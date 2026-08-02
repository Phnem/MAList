package com.example.myapplication.media.player

import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.util.ArrayDeque
import java.util.IdentityHashMap
import kotlin.math.min

/** Correlates Analytics load ids with transfer sources without conflating identical requests. */
internal class LoadIdCorrelator<K>(
    private val maxPending: Int,
) {
    private val pending = LinkedHashMap<K, ArrayDeque<Long>>()
    private val waitingSources = LinkedHashMap<K, ArrayDeque<Any>>()
    private val active = IdentityHashMap<Any, Long>()

    fun register(loadId: Long, key: K) {
        val waiting = waitingSources[key]
        val source = waiting?.pollFirst()
        if (waiting?.isEmpty() == true) waitingSources.remove(key)
        if (source != null) {
            active[source] = loadId
        } else {
            pending.getOrPut(key, ::ArrayDeque).addLast(loadId)
            trimPending()
        }
    }

    fun begin(source: Any, key: K): Long? {
        val queue = pending[key]
        val loadId = queue?.pollFirst()
        if (queue?.isEmpty() == true) pending.remove(key)
        if (loadId != null) {
            active[source] = loadId
        } else {
            waitingSources.getOrPut(key, ::ArrayDeque).addLast(source)
        }
        return loadId
    }

    fun loadId(source: Any): Long? = active[source]

    fun bind(source: Any, loadId: Long, key: K) {
        val waiting = waitingSources[key]
        waiting?.remove(source)
        if (waiting?.isEmpty() == true) waitingSources.remove(key)
        active[source] = loadId
    }

    fun end(source: Any, key: K): Long? {
        active.remove(source)?.let { return it }
        val waiting = waitingSources[key] ?: return null
        waiting.remove(source)
        if (waiting.isEmpty()) waitingSources.remove(key)
        return null
    }

    fun discard(loadId: Long, key: K) {
        val pendingForKey = pending[key]
        pendingForKey?.remove(loadId)
        if (pendingForKey?.isEmpty() == true) pending.remove(key)
        val activeSource = active.entries.firstOrNull { it.value == loadId }?.key
        if (activeSource != null) active.remove(activeSource)
    }

    private fun trimPending() {
        while (pending.values.sumOf { it.size } > maxPending) {
            val first = pending.entries.firstOrNull() ?: break
            first.value.pollFirst()
            if (first.value.isEmpty()) pending.remove(first.key)
        }
    }
}

internal data class TransferProgressSnapshot(
    val requestStartMs: Long,
    val ttfbMs: Long?,
    val loadedBytes: Long,
    val expectedBytes: Long?,
    val rolling1sBps: Long,
    val rolling3sBps: Long,
    val noProgressMs: Long,
    val longestNoProgressMs: Long,
)

/** Pure byte/time accumulator. It deliberately knows nothing about Media3 or Android clocks. */
internal class TransferProgressAccumulator(
    expectedBytes: Long?,
) {
    private data class ByteSample(val atMs: Long, var bytes: Long)

    private val samples = ArrayDeque<ByteSample>()
    private var expectedBytes = expectedBytes?.takeIf { it >= 0L }
    private var startedAtMs: Long? = null
    private var firstByteAtMs: Long? = null
    private var lastProgressAtMs: Long? = null
    private var loadedBytes = 0L
    private var longestNoProgressMs = 0L

    fun start(nowMs: Long) {
        if (startedAtMs == null) startedAtMs = nowMs
    }

    fun updateExpectedBytes(bytes: Long?) {
        if (expectedBytes == null && bytes != null && bytes >= 0L) expectedBytes = bytes
    }

    fun addBytes(nowMs: Long, bytes: Int) {
        if (bytes <= 0) return
        val started = startedAtMs ?: nowMs.also { startedAtMs = it }
        val previousProgress = lastProgressAtMs ?: started
        longestNoProgressMs = maxOf(longestNoProgressMs, (nowMs - previousProgress).coerceAtLeast(0L))
        if (firstByteAtMs == null) firstByteAtMs = nowMs
        lastProgressAtMs = nowMs
        loadedBytes += bytes
        val bucketAtMs = nowMs - nowMs.mod(SAMPLE_BUCKET_MS)
        val latest = samples.peekLast()
        if (latest?.atMs == bucketAtMs) {
            latest.bytes += bytes.toLong()
        } else {
            samples.addLast(ByteSample(bucketAtMs, bytes.toLong()))
        }
        trim(nowMs)
    }

    internal val retainedSampleBucketCount: Int
        get() = samples.size

    fun snapshot(nowMs: Long): TransferProgressSnapshot {
        val started = startedAtMs ?: nowMs.also { startedAtMs = it }
        trim(nowMs)
        val noProgressMs = (nowMs - (lastProgressAtMs ?: started)).coerceAtLeast(0L)
        longestNoProgressMs = maxOf(longestNoProgressMs, noProgressMs)
        return TransferProgressSnapshot(
            requestStartMs = started,
            ttfbMs = firstByteAtMs?.let { (it - started).coerceAtLeast(0L) },
            loadedBytes = loadedBytes,
            expectedBytes = expectedBytes,
            rolling1sBps = bytesPerSecond(nowMs, ROLLING_1S_MS, started),
            rolling3sBps = bytesPerSecond(nowMs, ROLLING_3S_MS, started),
            noProgressMs = noProgressMs,
            longestNoProgressMs = longestNoProgressMs,
        )
    }

    private fun bytesPerSecond(nowMs: Long, windowMs: Long, startedAtMs: Long): Long {
        val cutoff = nowMs - windowMs
        val bytes = samples.asSequence().filter { it.atMs >= cutoff }.sumOf { it.bytes }
        if (bytes == 0L) return 0L
        val observedMs = min(windowMs, (nowMs - startedAtMs).coerceAtLeast(1L))
        return bytes * 1_000L / observedMs
    }

    private fun trim(nowMs: Long) {
        val cutoff = nowMs - ROLLING_3S_MS
        while (samples.isNotEmpty() && samples.first().atMs < cutoff) samples.removeFirst()
        while (samples.size > MAX_SAMPLE_BUCKETS) samples.removeFirst()
    }

    private companion object {
        const val ROLLING_1S_MS = 1_000L
        const val ROLLING_3S_MS = 3_000L
        const val SAMPLE_BUCKET_MS = 100L
        const val MAX_SAMPLE_BUCKETS = 64
    }
}

internal data class CompletedTransferProgress(
    val progress: TransferProgressSnapshot,
    val responseCode: Int?,
    val cancelReason: String?,
)

internal enum class TransferCancellationReason(val telemetryValue: String) {
    SLOW_CHUNK("slow_chunk"),
}

/**
 * Bounded, thread-safe adapter for Media3 transfer callbacks. Request keys may contain signed URLs,
 * but remain in memory and are never formatted or logged.
 */
internal class StreamingTransferMonitor(
    private val nowMs: () -> Long,
) : TransferListener {
    /** Media3 reuses the same DataSpec instance for Analytics and DataSource callbacks. */
    private class RequestKey(private val dataSpec: DataSpec) {
        override fun equals(other: Any?): Boolean =
            other is RequestKey && dataSpec === other.dataSpec

        override fun hashCode(): Int = System.identityHashCode(dataSpec)
    }

    /**
     * Bounded in-memory retry identity. A partial retry changes position and length, but keeps the
     * absolute range end. Exact URI equality avoids hash collisions; this value is never logged.
     */
    private data class RequestDescriptor(
        val uri: android.net.Uri,
        val httpMethod: Int,
        val absoluteRangeEnd: Long?,
        val customKey: String?,
    )

    private data class ActiveTransfer(
        val key: RequestKey,
        val descriptor: RequestDescriptor,
        val progress: TransferProgressAccumulator,
    )

    private val active = IdentityHashMap<DataSource, ActiveTransfer>()
    private val activeOrder = ArrayDeque<DataSource>()
    private val loadIds = LoadIdCorrelator<RequestKey>(MAX_COMPLETED_REQUESTS)
    private val completedByLoadId = LinkedHashMap<Long, CompletedTransferProgress>()
    private val uncorrelatedCompleted = LinkedHashMap<RequestKey, ArrayDeque<CompletedTransferProgress>>()
    private val cancellationReasons = LinkedHashMap<Long, TransferCancellationReason>()
    private var completedCount = 0

    @Synchronized
    fun registerLoad(loadId: Long, dataSpec: DataSpec) {
        loadIds.register(loadId, dataSpec.requestKey())
    }

    @Synchronized
    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (!isNetwork) return
        beginTransfer(source, dataSpec)
    }

    @Synchronized
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (!isNetwork) return
        if (active[source] == null) {
            beginTransfer(source, dataSpec)
        }
        val expectedFromResponse = (source as? HttpDataSource)
            ?.responseHeaders
            ?.headerLong("Content-Length")
        active[source]?.progress?.updateExpectedBytes(expectedFromResponse)
    }

    @Synchronized
    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int,
    ) {
        if (!isNetwork) return
        active[source]?.progress?.addBytes(nowMs(), bytesTransferred)
    }

    @Synchronized
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (!isNetwork) return
        val transfer = active.remove(source) ?: return
        activeOrder.remove(source)
        val loadId = loadIds.end(source, transfer.key)
        val expectedFromResponse = (source as? HttpDataSource)
            ?.responseHeaders
            ?.headerLong("Content-Length")
        transfer.progress.updateExpectedBytes(expectedFromResponse)
        val responseCode = (source as? HttpDataSource)
            ?.responseCode
            ?.takeIf { it > 0 }
        val result = CompletedTransferProgress(
            progress = transfer.progress.snapshot(nowMs()),
            responseCode = responseCode,
            cancelReason = loadId
                ?.let(cancellationReasons::remove)
                ?.telemetryValue,
        )
        if (loadId != null) {
            completedByLoadId[loadId] = result
        } else {
            uncorrelatedCompleted.getOrPut(transfer.key, ::ArrayDeque).addLast(result)
        }
        completedCount += 1
        trimCompleted()
    }

    @Synchronized
    fun finishInterrupted(loadId: Long, dataSpec: DataSpec, responseCode: Int? = null) {
        val key = dataSpec.requestKey()
        val entry = findActive(loadId, dataSpec)
        val transfer = entry?.value
        val source = entry?.key
        if (source != null) {
            active.remove(source)
            activeOrder.remove(source)
            loadIds.end(source, key)
        } else {
            loadIds.discard(loadId, key)
        }
        val progress = transfer?.progress?.snapshot(nowMs()) ?: return
        completedByLoadId[loadId] = CompletedTransferProgress(
            progress = progress,
            responseCode = responseCode,
            cancelReason = cancellationReasons.remove(loadId)?.telemetryValue,
        )
        completedCount += 1
        trimCompleted()
    }

    @Synchronized
    fun snapshot(loadId: Long, dataSpec: DataSpec): TransferProgressSnapshot? {
        return findActive(loadId, dataSpec)
            ?.value
            ?.progress
            ?.snapshot(nowMs())
    }

    @Synchronized
    fun consumeCompleted(loadId: Long, dataSpec: DataSpec): CompletedTransferProgress? {
        completedByLoadId.remove(loadId)?.let {
            completedCount -= 1
            return it
        }
        val key = dataSpec.requestKey()
        val queue = uncorrelatedCompleted[key] ?: return null
        val result = queue.pollFirst() ?: return null
        completedCount -= 1
        if (queue.isEmpty()) uncorrelatedCompleted.remove(key)
        return result
    }

    @Synchronized
    fun recordCancellationReason(
        loadId: Long,
        dataSpec: DataSpec,
        reason: TransferCancellationReason,
    ) {
        findActive(loadId, dataSpec)?.let { entry ->
            loadIds.bind(entry.key, loadId, entry.value.key)
        }
        cancellationReasons[loadId] = reason
        while (cancellationReasons.size > MAX_COMPLETED_REQUESTS) {
            cancellationReasons.remove(cancellationReasons.keys.first())
        }
    }

    private fun trimCompleted() {
        while (completedCount > MAX_COMPLETED_REQUESTS) {
            val correlatedKey = completedByLoadId.keys.firstOrNull()
            if (correlatedKey != null) {
                completedByLoadId.remove(correlatedKey)
                completedCount -= 1
                continue
            }
            val first = uncorrelatedCompleted.entries.firstOrNull() ?: break
            first.value.pollFirst()
            completedCount -= 1
            if (first.value.isEmpty()) uncorrelatedCompleted.remove(first.key)
        }
    }

    private fun beginTransfer(source: DataSource, dataSpec: DataSpec) {
        val key = dataSpec.requestKey()
        val progress = TransferProgressAccumulator(dataSpec.knownLength())
        progress.start(nowMs())
        loadIds.begin(source, key)
        active[source] = ActiveTransfer(key, dataSpec.requestDescriptor(), progress)
        activeOrder.remove(source)
        activeOrder.addLast(source)
        while (activeOrder.size > MAX_ACTIVE_REQUESTS) {
            val oldest = activeOrder.pollFirst() ?: break
            val removed = active.remove(oldest) ?: continue
            loadIds.end(oldest, removed.key)
        }
    }

    private fun DataSpec.requestKey() = RequestKey(this)

    private fun DataSpec.requestDescriptor() = RequestDescriptor(
        uri = uri,
        httpMethod = httpMethod,
        absoluteRangeEnd = length
            .takeUnless { it == C.LENGTH_UNSET.toLong() }
            ?.let { knownLength -> position + knownLength },
        customKey = key,
    )

    private fun findActive(loadId: Long, dataSpec: DataSpec): MutableMap.MutableEntry<DataSource, ActiveTransfer>? {
        val key = dataSpec.requestKey()
        active.entries.firstOrNull { loadIds.loadId(it.key) == loadId }?.let { return it }
        active.entries.firstOrNull { it.value.key == key }?.let { return it }
        val descriptor = dataSpec.requestDescriptor()
        return active.entries
            .filter { loadIds.loadId(it.key) == null && it.value.descriptor == descriptor }
            .singleOrNull()
    }

    private companion object {
        const val MAX_COMPLETED_REQUESTS = 64
        const val MAX_ACTIVE_REQUESTS = 64
    }
}

private fun DataSpec.knownLength(): Long? = length.takeUnless { it == C.LENGTH_UNSET.toLong() }

internal fun Map<String, List<String>>.headerLong(name: String): Long? = entries
    .firstOrNull { it.key.equals(name, ignoreCase = true) }
    ?.value
    ?.firstNotNullOfOrNull(String::toLongOrNull)
