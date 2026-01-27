package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.DecimalFormat
import java.util.UUID
import kotlin.math.min

// ==========================================
// NETWORK & DATA LAYER
// ==========================================

data class AnimeUpdate(
    val animeId: String,
    val title: String,
    val currentEpisodes: Int,
    val newEpisodes: Int,
    val source: String
)

object ApiRateLimiter {
    private val mutex = Mutex()
    suspend fun <T> executeSafe(block: suspend () -> T): T {
        mutex.withLock {
            delay(1200)
            return block()
        }
    }
}

class AnimeRepository {
    private val tmdbKey = "4f4dc3cd35d58a551162eefe92ff549c"

    suspend fun findTotalEpisodes(title: String): Pair<Int, String>? = withContext(Dispatchers.IO) {
        val shikiResult = checkShikimori(title)
        if (shikiResult != null && shikiResult > 0) return@withContext shikiResult to "Shikimori"

        val jikanResult = checkJikan(title)
        if (jikanResult != null && jikanResult > 0) return@withContext jikanResult to "Jikan"

        val tmdbResult = checkTmdb(title)
        if (tmdbResult != null && tmdbResult > 0) return@withContext tmdbResult to "TMDB"

        return@withContext null
    }

    private suspend fun checkShikimori(query: String): Int? {
        return try {
            ApiRateLimiter.executeSafe {
                val searchUrl = "https://shikimori.one/api/animes?search=${enc(query)}&limit=1"
                val json = getJson(searchUrl).asJsonArray
                if (json.size() == 0) return@executeSafe null
                val id = json[0].asJsonObject.get("id").asInt
                json[0].asJsonObject.get("episodes").let { if(it.isJsonNull) 0 else it.asInt }
            }
        } catch (e: Exception) { null }
    }

    private suspend fun checkJikan(query: String): Int? {
        return try {
            ApiRateLimiter.executeSafe {
                val searchUrl = "https://api.jikan.moe/v4/anime?q=${enc(query)}&limit=1"
                val resp = getJson(searchUrl).asJsonObject
                val data = resp.getAsJsonArray("data")
                if (data == null || data.size() == 0) return@executeSafe null
                val anime = data[0].asJsonObject
                if (anime.get("episodes").isJsonNull) 0 else anime.get("episodes").asInt
            }
        } catch (e: Exception) { null }
    }

    private suspend fun checkTmdb(query: String): Int? {
        return try {
            ApiRateLimiter.executeSafe {
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$tmdbKey&query=${enc(query)}"
                val results = getJson(searchUrl).asJsonObject.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    val id = results[0].asJsonObject.get("id").asInt
                    val detailsUrl = "https://api.themoviedb.org/3/tv/$id?api_key=$tmdbKey"
                    val details = getJson(detailsUrl).asJsonObject
                    val seasons = details.getAsJsonArray("seasons")
                    var total = 0
                    seasons.forEach { s ->
                        val seasonObj = s.asJsonObject
                        val sNum = seasonObj.get("season_number").asInt
                        if (sNum > 0) total += seasonObj.get("episode_count").asInt
                    }
                    return@executeSafe total
                }
                null
            }
        } catch (e: Exception) { null }
    }

    private fun getJson(urlString: String): com.google.gson.JsonElement {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (conn.responseCode in 200..299) {
            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            return JsonParser.parseString(jsonStr)
        } else {
            throw Exception("HTTP ${conn.responseCode}")
        }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

// ==========================================
// THEME & COLORS
// ==========================================

val BrandBlue = Color(0xFF3E82F7)
val BrandBlueSoft = Color(0xFF5E92F3)
val BrandRed = Color(0xFFFF453A)

val RateColor1 = Color(0xFFFF453A)
val RateColor2 = Color(0xFFFF9F0A)
val RateColor3 = Color(0xFFFFD60A)
val RateColor4 = Color(0xFF32D74B)
val RateColor5 = Color(0xFF30D158)
val RateColorEmpty = Color(0xFF8E8E93)

val DarkBackground = Color(0xFF111318)
val DarkSurface = Color(0xFF1F222B)
val DarkSurfaceVariant = Color(0xFF262A35)
val DarkTextPrimary = Color(0xFFF2F2F7)
val DarkTextSecondary = Color(0xFF9898A0)
val DarkBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)

val LightBackground = Color(0xFFF0F2F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF9F9FB)
val LightTextPrimary = Color(0xFF1C1C1E)
val LightTextSecondary = Color(0xFF8E8E93)
val LightBorder = Color(0xFF000000).copy(alpha = 0.04f)

val RatingChipBg = Color.Black.copy(alpha = 0.4f)

fun getRatingColor(rating: Int): Color {
    return when (rating) {
        1 -> RateColor1
        2 -> RateColor2
        3 -> RateColor3
        4 -> RateColor4
        5 -> RateColor5
        else -> Color.Gray
    }
}

@Composable
fun OneUiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            primary = BrandBlueSoft,
            onBackground = DarkTextPrimary,
            onSurface = DarkTextPrimary,
            secondary = DarkTextSecondary,
            outline = DarkBorder,
            error = BrandRed,
            surfaceContainer = Color(0xFF2C2C2E)
        )
    } else {
        lightColorScheme(
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            primary = BrandBlue,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary,
            secondary = LightTextSecondary,
            outline = LightBorder,
            error = BrandRed,
            surfaceContainer = Color(0xFFFFFFFF)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as ComponentActivity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val wic = androidx.core.view.WindowCompat.getInsetsController(window, view)
            wic.isAppearanceLightStatusBars = !darkTheme
            wic.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            titleMedium = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                letterSpacing = 0.2.sp
            ),
            bodyMedium = TextStyle(
                fontSize = 14.sp,
                color = colors.secondary,
                letterSpacing = 0.1.sp
            ),
            headlineMedium = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                letterSpacing = (-0.5).sp
            )
        ),
        content = content
    )
}

// ==========================================
// DATA & VIEWMODEL
// ==========================================

data class Anime(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val episodes: Int,
    val rating: Int,
    val imageFileName: String?,
    val orderIndex: Int,
    val dateAdded: Long = System.currentTimeMillis()
)

data class RankedAnime(
    val anime: Anime,
    val score: Int
)

class AnimeViewModel : ViewModel() {
    private val _animeList = mutableStateListOf<Anime>()
    val animeList: List<Anime> get() = _animeList
    var searchQuery by mutableStateOf("")
    var sortOption by mutableStateOf(SortOption.DATE_NEWEST)

    private val _updates = mutableStateListOf<AnimeUpdate>()
    val updates: List<AnimeUpdate> get() = _updates
    var isCheckingUpdates by mutableStateOf(false)
    var needsUpdateCheck by mutableStateOf(true)

    private val repository = AnimeRepository()
    private val ROOT = "MyAnimeList"
    private val IMG_DIR = "collection"
    private val FILE_NAME = "list.json"
    private val IGNORED_FILE_NAME = "ignored.json"

    private var ignoredUpdatesMap = mutableMapOf<String, Int>()

    private fun getRoot(): File {
        val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val f = File(d, ROOT); if (!f.exists()) f.mkdirs(); return f
    }
    private fun getImgDir(): File { val f = File(getRoot(), IMG_DIR); if(!f.exists()) f.mkdirs(); return f }
    private fun getDataFile(): File = File(getRoot(), FILE_NAME)
    private fun getIgnoredFile(): File = File(getRoot(), IGNORED_FILE_NAME)

    fun loadAnime() {
        val f = getDataFile()
        if (f.exists()) {
            try {
                val json = f.readText()
                val list: List<Anime> = Gson().fromJson(json, object : TypeToken<List<Anime>>() {}.type) ?: emptyList()
                _animeList.clear(); _animeList.addAll(list.sortedBy { it.orderIndex })
            } catch (e: Exception) { e.printStackTrace() }
        }

        val fIgnored = getIgnoredFile()
        if (fIgnored.exists()) {
            try {
                val json = fIgnored.readText()
                val map: Map<String, Int> = Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
                ignoredUpdatesMap.putAll(map)
            } catch (e: Exception) { e.printStackTrace() }
        }

        needsUpdateCheck = true
        checkForUpdates()
    }

    fun checkForUpdates(force: Boolean = false) {
        if (!force && !needsUpdateCheck && _updates.isEmpty()) return
        if (isCheckingUpdates) return
        isCheckingUpdates = true

        viewModelScope.launch {
            try {
                val newUpdates = mutableListOf<AnimeUpdate>()
                val currentList = _animeList.toList()

                currentList.forEach { anime ->
                    val result = repository.findTotalEpisodes(anime.title)
                    if (result != null) {
                        val (remoteEps, source) = result
                        val isIgnored = ignoredUpdatesMap[anime.id] == remoteEps
                        if (remoteEps > anime.episodes && !isIgnored) {
                            newUpdates.add(AnimeUpdate(anime.id, anime.title, anime.episodes, remoteEps, source))
                        }
                    }
                }
                _updates.clear()
                _updates.addAll(newUpdates)
                if (_updates.isEmpty()) needsUpdateCheck = false
            } catch (e: Exception) { e.printStackTrace() }
            finally { isCheckingUpdates = false }
        }
    }

    fun acceptUpdate(update: AnimeUpdate, ctx: Context) {
        val anime = getAnimeById(update.animeId) ?: return
        updateAnime(ctx, anime.id, anime.title, update.newEpisodes, anime.rating, null)
        _updates.remove(update)
    }

    fun dismissUpdate(update: AnimeUpdate) {
        ignoredUpdatesMap[update.animeId] = update.newEpisodes
        saveIgnored()
        _updates.remove(update)
        if (_updates.isEmpty()) needsUpdateCheck = false
    }

    fun getAnimeById(id: String): Anime? = _animeList.find { it.id == id }

    fun addAnime(ctx: Context, title: String, ep: Int, rate: Int, uri: Uri?) {
        val id = UUID.randomUUID().toString()
        val img = if (uri != null) saveImg(ctx, uri, id) else null
        _animeList.add(0, Anime(id, title, ep, rate, img, (_animeList.maxOfOrNull { it.orderIndex }?:0)+1))
        save()
        needsUpdateCheck = true
    }

    fun updateAnime(ctx: Context, id: String, title: String, ep: Int, rate: Int, uri: Uri?) {
        val idx = _animeList.indexOfFirst { it.id == id }
        if (idx == -1) return
        var img = _animeList[idx].imageFileName
        if (uri != null) {
            val newImg = saveImg(ctx, uri, id)
            if (newImg != null) {
                _animeList[idx].imageFileName?.let { File(getImgDir(), it).delete() }
                img = newImg
            }
        }
        _animeList[idx] = _animeList[idx].copy(title=title, episodes=ep, rating=rate, imageFileName=img)
        save()
        needsUpdateCheck = true
        if (ignoredUpdatesMap.containsKey(id)) {
            ignoredUpdatesMap.remove(id)
            saveIgnored()
        }
    }

    fun deleteAnime(id: String) {
        val anime = _animeList.find { it.id == id } ?: return
        anime.imageFileName?.let { File(getImgDir(), it).delete() }
        _animeList.remove(anime)
        save()
        needsUpdateCheck = true
    }

    private fun save() { try { getDataFile().writeText(Gson().toJson(_animeList)) } catch(e:Exception){e.printStackTrace()} }
    private fun saveIgnored() { try { getIgnoredFile().writeText(Gson().toJson(ignoredUpdatesMap)) } catch(e:Exception){e.printStackTrace()} }

    private fun saveImg(ctx: Context, uri: Uri, id: String): String? {
        return try {
            val name = "img_${id}_${System.currentTimeMillis()}.jpg"
            ctx.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(File(getImgDir(), name)).use { o -> i.copyTo(o) } }
            name
        } catch(e: Exception) { null }
    }

    fun getImgPath(name: String?): File? = if(name!=null) File(getImgDir(), name).let { if(it.exists()) it else null } else null

    fun getDisplayList(): List<Anime> {
        val rawQuery = searchQuery.trim()
        if (rawQuery.isBlank()) return sortList(_animeList)
        val normalizedQuery = rawQuery.lowercase()
        val rankedList = _animeList.mapNotNull { anime ->
            val score = calculateRelevanceScore(normalizedQuery, anime.title.lowercase())
            if (score > 0) RankedAnime(anime, score) else null
        }
        return rankedList.sortedWith(
            compareByDescending<RankedAnime> { it.score }
                .thenBy { it.anime.title }
        ).map { it.anime }
    }

    private fun calculateRelevanceScore(query: String, title: String): Int {
        if (title == query) return 100
        if (title.startsWith(query)) return 90
        val words = title.split(" ", "-", ":")
        if (words.any { it.startsWith(query) }) return 80
        if (title.contains(query)) return 60
        if (query.length > 2) {
            val dist = levenshtein(query, title)
            val maxEdits = if (query.length < 6) 1 else 2
            val prefix = title.take(query.length + 1)
            val distPrefix = levenshtein(query, prefix)
            if (dist <= maxEdits) return 50
            if (distPrefix <= maxEdits) return 45
        }
        return 0
    }

    private fun sortList(list: List<Anime>): List<Anime> {
        return when(sortOption) {
            SortOption.DATE_NEWEST -> list.sortedByDescending { it.dateAdded }
            SortOption.RATING_HIGH -> list.sortedByDescending { it.rating }
            SortOption.AZ -> list.sortedBy { it.title }
        }
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost; cost = newCost; newCost = swap
        }
        return cost[lhsLength]
    }
}

enum class SortOption(val label: String) {
    DATE_NEWEST("Newest First"),
    RATING_HIGH("Highest Rated"),
    AZ("Name (A-Z)")
}

// ==========================================
// UI COMPONENTS
// ==========================================

fun performHaptic(view: View, type: String) {
    when (type) {
        "success" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        "warning" -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        "light" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
}

fun performHaptic(view: View, isStrong: Boolean) {
    performHaptic(view, if (isStrong) "warning" else "light")
}

@Composable
fun GlassActionDock(
    hazeState: HazeState,
    isFloating: Boolean,
    sortOption: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topPadding by animateDpAsState(
        targetValue = if (isFloating) 16.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "dockPadding"
    )

    val isDark = isSystemInDarkTheme()
    val targetTint = if (isDark) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
    val tintColor by animateColorAsState(targetValue = if (isFloating) targetTint else Color.Transparent, label = "tint")
    val blurRadius by animateDpAsState(targetValue = if (isFloating) 20.dp else 0.dp, label = "blur")
    val shineColorBase = if (isDark) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.7f)
    val shineAlpha by animateFloatAsState(targetValue = if (isFloating) 1f else 0f, label = "shineAlpha")
    val borderStrokeBase = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.2f)
    val borderColor by animateColorAsState(targetValue = if (isFloating) borderStrokeBase else Color.Transparent, label = "border")
    val buttonBgColor by animateColorAsState(targetValue = if (isFloating) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), label = "btnBg")
    val dividerAlpha by animateFloatAsState(targetValue = if (isFloating) 1f else 0f, label = "divAlpha")
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(top = topPadding)
            .statusBarsPadding()
            .clip(RoundedCornerShape(32.dp))
            .hazeChild(state = hazeState, shape = RoundedCornerShape(32.dp), style = HazeStyle(blurRadius = blurRadius, tint = tintColor))
            .border(0.5.dp, borderColor, RoundedCornerShape(32.dp))
    ) {
        if (shineAlpha > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val rect = Rect(offset = Offset.Zero, size = size)
                val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(32.dp.toPx()))) }
                drawPath(path, brush = Brush.verticalGradient(colors = listOf(shineColorBase.copy(alpha = shineColorBase.alpha * shineAlpha), Color.Transparent, Color.Transparent, shineColorBase.copy(alpha = 0.1f * shineAlpha))), style = Stroke(width = 2.dp.toPx()))
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(44.dp).clip(CircleShape).background(buttonBgColor)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurface)
                }
                MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, offset = DpOffset(x = 0.dp, y = 8.dp)) {
                        SortOption.values().forEach { option ->
                            val isSelected = sortOption == option
                            DropdownMenuItem(
                                text = { Text(text = option.label, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                trailingIcon = {
                                    val icon = when (option) { SortOption.DATE_NEWEST -> Icons.Default.DateRange; SortOption.RATING_HIGH -> Icons.Default.Star; SortOption.AZ -> Icons.AutoMirrored.Filled.Sort }
                                    Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                },
                                onClick = { onSortSelected(option); expanded = false }
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.height(20.dp).width(1.dp).alpha(dividerAlpha).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(44.dp).clip(CircleShape).background(buttonBgColor)) {
                Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

val StarIconVector: ImageVector get() = ImageVector.Builder(name = "Star", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null, strokeAlpha = 1f, pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero) { moveTo(8.243f, 7.34f); lineTo(1.863f, 8.265f); lineTo(1.75f, 8.288f); arcTo(1f, 1f, 0f, false, false, 1.31f, 9.972f); lineTo(5.932f, 14.471f); lineTo(4.842f, 20.826f); lineTo(4.829f, 20.936f); arcTo(1f, 1f, 0f, false, false, 6.293f, 21.88f); lineTo(12.0f, 18.88f); lineTo(17.693f, 21.88f); lineTo(17.793f, 21.926f); arcTo(1f, 1f, 0f, false, false, 19.145f, 20.826f); lineTo(18.054f, 14.471f); lineTo(22.678f, 9.971f); lineTo(22.756f, 9.886f); arcTo(1f, 1f, 0f, false, false, 22.123f, 8.266f); lineTo(15.743f, 7.34f); lineTo(12.891f, 1.56f); arcTo(1f, 1f, 0f, false, false, 11.097f, 1.56f); lineTo(8.243f, 7.34f); close() }.build()

@Composable
fun StarRatingBar(rating: Int, onRatingChanged: (Int) -> Unit) {
    val colors = listOf(RateColor1, RateColor2, RateColor3, RateColor4, RateColor5)
    val activeColor = if (rating > 0) colors[rating - 1] else RateColorEmpty
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val isSelected = i <= rating
            val animatedColor by animateColorAsState(targetValue = if (isSelected) activeColor else RateColorEmpty, animationSpec = tween(300), label = "color")
            val scale by animateFloatAsState(targetValue = if (isSelected) 1.2f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")
            Icon(imageVector = StarIconVector, contentDescription = "Rate $i", tint = animatedColor, modifier = Modifier.size(54.dp).scale(scale).padding(4.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onRatingChanged(i) })
        }
    }
}

@Composable
fun EpisodeSuggestions(onSelect: (String) -> Unit) {
    val suggestions = listOf("12", "13", "24", "36", "48", "100")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        suggestions.forEach { num ->
            Box(modifier = Modifier.weight(1f).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { onSelect(num) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(text = num, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AnimatedSaveFab(isEnabled: Boolean, onClick: () -> Unit) {
    var isSaved by remember { mutableStateOf(false) }
    val view = LocalView.current
    FloatingActionButton(
        onClick = {
            if (isEnabled && !isSaved) {
                isSaved = true
                performHaptic(view, "success")
                onClick()
            }
        },
        containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else Color(0xFF424242),
        contentColor = if (isEnabled) Color.White else Color.Gray,
        shape = CircleShape,
        modifier = Modifier.size(64.dp)
    ) {
        AnimatedContent(targetState = isSaved, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "save_animation") { saved ->
            if (saved) Icon(imageVector = Icons.Default.Check, contentDescription = "Saved", modifier = Modifier.size(28.dp))
            else Icon(imageVector = Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun AnimatedCopyButton(textToCopy: String) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    val view = LocalView.current
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(visible = isCopied, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp).zIndex(10f)) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Copied!", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        IconButton(onClick = { if (textToCopy.isNotEmpty()) { performHaptic(view, "light"); clipboardManager.setText(AnnotatedString(textToCopy)); isCopied = true } }) {
            AnimatedContent(targetState = isCopied, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "copy_icon") { copied ->
                if (copied) { LaunchedEffect(Unit) { delay(2000); isCopied = false }; Icon(Icons.Default.Check, null, tint = BrandBlue, modifier = Modifier.size(24.dp)) }
                else Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// --- НОВЫЕ АНИМИРОВАННЫЕ КОМПОНЕНТЫ ВВОДА ---

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.TextAsIndividualLetters(
    animatedContentScope: AnimatedContentScope,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    textColor: Color
) {
    Row(modifier) {
        text.forEachIndexed { index, letter ->
            Text(
                text = "$letter",
                modifier = Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "hint_$index"),
                    animatedVisibilityScope = animatedContentScope,
                    boundsTransform = { _, _ ->
                        // УСКОРЕННАЯ анимация (stiffness = 1000f)
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = 1000f
                        )
                    }
                ),
                style = style,
                color = textColor
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AnimatedOneUiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Логика: если фокус ИЛИ есть текст -> подсказка наверху
    val showHintAbove = isFocused || value.isNotEmpty()

    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.secondary
    val textStyle = TextStyle(fontSize = 18.sp, color = textColor)
    val hintStyleInner = TextStyle(fontSize = 18.sp)
    val hintStyleOuter = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)

    // ВАЖНО: BasicTextField теперь СНАРУЖИ. Он стабилен и не пересоздается.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        interactionSource = interactionSource,
        textStyle = textStyle,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        decorationBox = { innerTextField ->
            // SharedTransitionLayout внутри decorationBox
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = showHintAbove,
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    label = "hintAnimation"
                ) { targetShowAbove ->
                    if (targetShowAbove) {
                        // STATE: ПОДСКАЗКА СВЕРХУ (Hint Above)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 1. Текст подсказки (снаружи)
                            Box(
                                modifier = Modifier
                                    .padding(start = 24.dp, bottom = 4.dp)
                                    .height(16.dp)
                            ) {
                                TextAsIndividualLetters(
                                    animatedContentScope = this@AnimatedContent,
                                    text = placeholder,
                                    style = hintStyleOuter,
                                    textColor = MaterialTheme.colorScheme.primary
                                )
                            }

                            // 2. Контейнер ввода
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sharedElement(
                                        rememberSharedContentState(key = "container"),
                                        animatedVisibilityScope = this@AnimatedContent
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(containerColor)
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                innerTextField()
                            }
                        }
                    } else {
                        // STATE: ПОДСКАЗКА ВНУТРИ (Hint Inside)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Пустой Box наверху, чтобы сохранить структуру (опционально) или просто сразу контейнер
                            // Используем sharedElement для контейнера
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sharedElement(
                                        rememberSharedContentState(key = "container"),
                                        animatedVisibilityScope = this@AnimatedContent
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(containerColor)
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Подсказка внутри, рядом с текстом
                                TextAsIndividualLetters(
                                    animatedContentScope = this@AnimatedContent,
                                    text = placeholder,
                                    style = hintStyleInner,
                                    textColor = hintColor
                                )
                                innerTextField()
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun RatingChip(rating: Int) {
    val starTint = getRatingColor(rating)
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RatingChipBg).padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = starTint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = rating.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun ThemeSwitch(darkTheme: Boolean, onToggle: () -> Unit) {
    val view = LocalView.current
    val offsetAnim by animateDpAsState(targetValue = if (darkTheme) 28.dp else 4.dp, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "switchOffset")
    val containerColor by animateColorAsState(targetValue = if (darkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), label = "switchContainer")
    Box(modifier = Modifier.width(60.dp).height(34.dp).clip(CircleShape).background(containerColor).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { performHaptic(view, "light"); onToggle() }) {
        Box(modifier = Modifier.size(26.dp).align(Alignment.CenterStart).offset(x = offsetAnim).shadow(4.dp, CircleShape).clip(CircleShape).background(if (darkTheme) Color(0xFF48484A) else Color.White), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = darkTheme, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "iconAnim") { isDark ->
                if (isDark) Icon(Icons.Default.DarkMode, null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                else Icon(Icons.Default.LightMode, null, tint = Color(0xFFFF9500), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun OneUiAnimeCard(
    anime: Anime,
    viewModel: AnimeViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val imageFile = remember(anime.imageFileName) { viewModel.getImgPath(anime.imageFileName) }
    val view = LocalView.current
    val cardShape = RoundedCornerShape(24.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp)
                .sharedBounds(rememberSharedContentState(key = "container_${anime.id}"), animatedVisibilityScope = animatedVisibilityScope, resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds, placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize)
                .shadow(elevation = 12.dp, shape = cardShape, spotColor = Color.Black.copy(alpha = 0.08f), ambientColor = Color.Black.copy(alpha = 0.04f))
                .background(brush = Brush.verticalGradient(colors = listOf(surfaceColor, surfaceVariant)), shape = cardShape)
                .border(width = 1.dp, color = borderColor, shape = cardShape)
                .clip(cardShape)
                .combinedClickable(onClick = { performHaptic(view, "light"); onClick() }, onLongClick = { performHaptic(view, "light"); onLongClick() })
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.aspectRatio(1f).fillMaxHeight().sharedElement(rememberSharedContentState(key = "image_${anime.id}"), animatedVisibilityScope = animatedVisibilityScope).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                    if (imageFile != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageFile).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color(0xFF2C2C2E))) { Text(text = anime.title.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Gray) }
                }
                Spacer(Modifier.width(16.dp))
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.Center) {
                        Text(text = anime.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(text = "${anime.episodes} episodes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (anime.rating > 0) RatingChip(rating = anime.rating)
                }
            }
        }
    }
}

@Composable
fun SimpleAnimePreviewOverlay(anime: Anime, viewModel: AnimeViewModel, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val imageFile = remember(anime.imageFileName) { viewModel.getImgPath(anime.imageFileName) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val view = LocalView.current
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!showConfirmDialog) { performHaptic(view, "light"); onDismiss() } }.zIndex(50f), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = !showConfirmDialog, enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(24.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(0.7f).clip(RoundedCornerShape(32.dp)).background(DarkSurface)) {
                    if (imageFile != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageFile).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(anime.title.take(1), fontSize = 60.sp, color = Color.Gray) }
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 300f)))
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(text = anime.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = BrandBlue.copy(alpha = 0.8f), shape = CircleShape) { Text(text = "${anime.episodes} EP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                            Spacer(Modifier.width(12.dp))
                            if (anime.rating > 0) RatingChip(rating = anime.rating)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                AnimatedTrashButton(onClick = { performHaptic(view, "warning"); showConfirmDialog = true })
            }
        }
        if (showConfirmDialog) DeleteConfirmationDialog(onConfirm = { performHaptic(view, "warning"); onDelete() }, onCancel = { performHaptic(view, "light"); showConfirmDialog = false })
    }
}

@Composable
fun AnimatedTrashButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.8f else 1f, label = "scale")
    Box(modifier = Modifier.size(70.dp).scale(scale).shadow(12.dp, CircleShape, spotColor = BrandRed).clip(CircleShape).background(BrandRed).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isPressed = true; onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun DeleteConfirmationDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = onCancel) {
        AnimatedVisibility(visible = visible, enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(modifier = Modifier.clip(RoundedCornerShape(32.dp)).background(DarkSurface).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)).padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(BrandRed.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.DeleteForever, null, tint = BrandRed, modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.height(16.dp))
                    Text("Delete title?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("This action cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = DarkTextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { onCancel() }, contentAlignment = Alignment.Center) { Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(BrandRed).clickable { onConfirm() }, contentAlignment = Alignment.Center) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun MalistWorkspaceTopBar(isDarkTheme: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("M", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "MAList", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(16.dp))
                ThemeSwitch(darkTheme = isDarkTheme, onToggle = onToggleTheme)
            }
        }
    }
}

@Composable
fun StatsCard(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onClick: () -> Unit,
    rankColor: Color? = null,
    content: @Composable () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val contentColor = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape, spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(brush = Brush.verticalGradient(colors = listOf(surfaceColor, surfaceVariant)))
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable { onClick() }
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = contentColor, fontWeight = FontWeight.SemiBold)
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
fun WatchStatsContent(animeList: List<Anime>) {
    val totalEpisodes = remember(animeList) { animeList.sumOf { it.episodes } }
    val totalMinutes = totalEpisodes * 22L
    val formatter = DecimalFormat("#,###")
    val formattedMinutes = formatter.format(totalMinutes).replace(",", " ")
    val formattedHours = formatter.format(totalMinutes / 60).replace(",", " ")
    val formattedDays = formatter.format(totalMinutes / (60 * 24)).replace(",", " ")
    val avgRating = remember(animeList) { if (animeList.isEmpty()) 0.0 else animeList.map { it.rating }.average() }
    val (rankName, rankColor) = remember(totalEpisodes) { when { totalEpisodes >= 1200 -> "Legend" to RateColor5; totalEpisodes >= 800 -> "Veteran" to RateColor4; totalEpisodes >= 500 -> "Dedicated" to RateColor3; totalEpisodes >= 300 -> "Casual" to RateColor2; else -> "Rookie" to RateColor1 } }
    var expandedIndex by remember { mutableIntStateOf(-1) }
    val textColor = MaterialTheme.colorScheme.onBackground
    val secColor = MaterialTheme.colorScheme.secondary

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Watch Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = textColor); Spacer(modifier = Modifier.height(4.dp)); Text("Everything you’ve watched so far", style = MaterialTheme.typography.bodyMedium, color = secColor); Spacer(modifier = Modifier.height(24.dp))
        StatsCard(title = "Watched episodes", icon = Icons.Default.Visibility, isExpanded = expandedIndex == 0, onClick = { expandedIndex = if (expandedIndex == 0) -1 else 0 }) { Text(text = "$totalEpisodes Episodes watched", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textColor) }
        Spacer(modifier = Modifier.height(12.dp))
        StatsCard(title = "Time spent watching", icon = Icons.Default.Schedule, isExpanded = expandedIndex == 1, onClick = { expandedIndex = if (expandedIndex == 1) -1 else 1 }) { Column { Text(text = "$formattedMinutes min", style = MaterialTheme.typography.titleLarge, color = textColor); Text(text = "$formattedHours h", style = MaterialTheme.typography.titleLarge, color = secColor); Text(text = "$formattedDays days", style = MaterialTheme.typography.titleLarge, color = BrandBlue.copy(alpha = 0.6f)) } }
        Spacer(modifier = Modifier.height(12.dp))
        StatsCard(title = "Average rating", icon = Icons.Default.Star, isExpanded = expandedIndex == 2, onClick = { expandedIndex = if (expandedIndex == 2) -1 else 2 }) { Text(text = String.format("%.1f / 5⭐", avgRating), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textColor) }
        Spacer(modifier = Modifier.height(12.dp))
        StatsCard(title = "Your rank:", icon = Icons.Default.MilitaryTech, isExpanded = expandedIndex == 3, onClick = { expandedIndex = if (expandedIndex == 3) -1 else 3 }) { Text(text = rankName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = rankColor) }
    }
}

@Composable
fun NotificationContent(updates: List<AnimeUpdate>, isChecking: Boolean, onAccept: (AnimeUpdate) -> Unit, onDismiss: (AnimeUpdate) -> Unit) {
    val ctx = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val cardBg = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Updates", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = textColor)
        if (isChecking) {
            Spacer(Modifier.height(24.dp)); CircularProgressIndicator(color = BrandBlue); Spacer(Modifier.height(8.dp))
            Text(text = "Checking APIs (Shikimori, Jikan, TMDB)...\nIt will take about 10 minutes.", color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
        } else if (updates.isEmpty()) {
            Spacer(Modifier.height(24.dp)); Icon(Icons.Default.CheckCircle, null, tint = BrandBlue.copy(alpha = 0.5f), modifier = Modifier.size(64.dp)); Spacer(Modifier.height(8.dp))
            Text("You are up to date!", color = textColor, fontSize = 18.sp)
        } else {
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(updates) { update ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(cardBg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(update.title, color = textColor, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
                            Text("Found ${update.newEpisodes} eps (You: ${update.currentEpisodes})", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            Text("Source: ${update.source}", color = Color.Gray, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDismiss(update) }) { Icon(Icons.Default.Close, null, tint = BrandRed) }
                        IconButton(onClick = { onAccept(update); Toast.makeText(ctx, "Updated ${update.title}!", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.Check, null, tint = BrandBlue) }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    nav: NavController,
    vm: AnimeViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val kbd = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val hazeState = remember { HazeState() }
    var showCSheet by remember { mutableStateOf(false) }
    var showNotifSheet by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var selectedAnimeForPreview by remember { mutableStateOf<Anime?>(null) }
    var currentPreviewAnime by remember { mutableStateOf<Anime?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.checkForUpdates() }
    if (selectedAnimeForPreview != null) currentPreviewAnime = selectedAnimeForPreview
    BackHandler(enabled = isSearchVisible || vm.searchQuery.isNotEmpty() || selectedAnimeForPreview != null) {
        if (selectedAnimeForPreview != null) { performHaptic(view, "light"); selectedAnimeForPreview = null }
        else if (isSearchVisible) { performHaptic(view, "light"); isSearchVisible = false; vm.searchQuery = ""; focusManager.clearFocus(); kbd?.hide() }
    }
    val listState = rememberLazyListState()
    val isHeaderFloating by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 10 } }
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
    val bgColor = MaterialTheme.colorScheme.background

    Scaffold(containerColor = Color.Transparent, bottomBar = {}, floatingActionButton = {}) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(bgColor))
            Box(modifier = Modifier.zIndex(6f).align(Alignment.TopEnd).padding(end = 16.dp)) {
                GlassActionDock(
                    hazeState = hazeState,
                    isFloating = isHeaderFloating,
                    sortOption = vm.sortOption,
                    onSortSelected = { performHaptic(view, "light"); vm.sortOption = it },
                    onSettingsClick = { performHaptic(view, "light") },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            val shouldBlur = (isSearchVisible && vm.searchQuery.isBlank()) || selectedAnimeForPreview != null || showCSheet || showNotifSheet
            val blurAmount by animateDpAsState(targetValue = if (shouldBlur) 10.dp else 0.dp, label = "blur")

            Column(modifier = Modifier.fillMaxSize().blur(blurAmount)) {
                Box(modifier = Modifier.fillMaxSize().weight(1f).background(bgColor)) {
                    val list = vm.getDisplayList()
                    if (list.isEmpty()) {
                        Column { MalistWorkspaceTopBar(isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme); Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("List is empty (or no matches)", color = MaterialTheme.colorScheme.secondary) } }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 0.dp, bottom = 120.dp, start = 0.dp, end = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.haze(state = hazeState)
                        ) {
                            item { MalistWorkspaceTopBar(isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme) }
                            items(list, key = { it.id }) { anime ->
                                Box(modifier = Modifier.animateItem().padding(horizontal = 16.dp)) {
                                    OneUiAnimeCard(anime = anime, viewModel = vm, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope, onClick = { nav.navigate("add_anime?animeId=${anime.id}") }, onLongClick = { performHaptic(view, "light"); selectedAnimeForPreview = anime })
                                }
                            }
                        }
                    }
                }
            }

            if (selectedAnimeForPreview == null && !isSearchVisible) {
                GlassBottomNavigation(
                    hazeState = hazeState,
                    nav = nav,
                    viewModel = vm,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onShowStats = { showCSheet = true },
                    onShowNotifs = { showNotifSheet = true },
                    onSearchClick = {
                        performHaptic(view, "light")
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) {
                            vm.searchQuery = ""
                            focusManager.clearFocus()
                            kbd?.hide()
                        }
                    },
                    isSearchActive = isSearchVisible,
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(3f).navigationBarsPadding()
                )
            }

            AnimatedVisibility(
                visible = isSearchVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).windowInsetsPadding(WindowInsets.ime).padding(bottom = 16.dp).zIndex(10f)
            ) {
                SimpGlassCard(
                    hazeState = hazeState,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    BasicTextField(
                        value = vm.searchQuery,
                        onValueChange = { vm.searchQuery = it; if (it.isNotEmpty()) performHaptic(view, "light") },
                        modifier = Modifier.fillMaxSize().focusRequester(searchFocusRequester).padding(horizontal = 20.dp),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(BrandBlue),
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, tint = BrandBlue)
                                Spacer(Modifier.width(12.dp))
                                Box {
                                    if (vm.searchQuery.isEmpty()) { Text("Search collection...", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp) }
                                    innerTextField()
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { kbd?.hide() })
                    )
                }
                LaunchedEffect(Unit) { searchFocusRequester.requestFocus(); kbd?.show() }
            }
            if (shouldBlur && selectedAnimeForPreview == null) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusManager.clearFocus(); isSearchVisible = false; kbd?.hide() }.zIndex(2f)) }
            AnimatedVisibility(visible = selectedAnimeForPreview != null, enter = fadeIn() + scaleIn(initialScale = 0.9f) + slideInVertically { it / 6 }, exit = fadeOut() + scaleOut(targetScale = 0.9f) + slideOutVertically { it / 6 }, modifier = Modifier.zIndex(50f)) {
                currentPreviewAnime?.let { anime -> SimpleAnimePreviewOverlay(anime = anime, viewModel = vm, onDismiss = { selectedAnimeForPreview = null }, onDelete = { scope.launch { selectedAnimeForPreview = null; delay(250); vm.deleteAnime(anime.id) } }) }
            }
            AnimatedVisibility(visible = showScrollToTop && !isSearchVisible && selectedAnimeForPreview == null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 160.dp, end = 24.dp).zIndex(1f)) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF333333).copy(alpha = 0.8f)).clickable { performHaptic(view, "light"); scope.launch { listState.animateScrollToItem(0) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.White) }
            }
        }
        if (showCSheet) {
            ModalBottomSheet(onDismissRequest = { showCSheet = false }, containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f), scrimColor = Color.Transparent) { WatchStatsContent(animeList = vm.animeList) }
        }
        if (showNotifSheet) {
            ModalBottomSheet(onDismissRequest = { showNotifSheet = false }, containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f), scrimColor = Color.Transparent) {
                NotificationContent(updates = vm.updates, isChecking = vm.isCheckingUpdates, onAccept = { vm.acceptUpdate(it, view.context) }, onDismiss = { vm.dismissUpdate(it) })
            }
        }
    }
}
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AddEditScreen(nav: NavController, vm: AnimeViewModel, id: String?, sharedTransitionScope: SharedTransitionScope, animatedVisibilityScope: AnimatedVisibilityScope) {
    val anime = remember(id) { id?.let { vm.getAnimeById(it) } }
    var title by remember { mutableStateOf(anime?.title ?: "") }
    var ep by remember { mutableStateOf(anime?.episodes?.toString() ?: "") }
    var rate by remember { mutableIntStateOf(anime?.rating ?: 0) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val hasChanges by remember(title, ep, rate, uri) { derivedStateOf { if (id == null) { title.isNotBlank() } else { (anime != null) && (title != anime.title || ep != anime.episodes.toString() || rate != anime.rating || uri != null) } } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    val ctx = LocalContext.current
    val view = LocalView.current
    val bg = MaterialTheme.colorScheme.background
    val textC = MaterialTheme.colorScheme.onBackground

    with(sharedTransitionScope) {
        val sharedModifier = if (id == null) { Modifier.sharedBounds(rememberSharedContentState(key = "fab_container"), animatedVisibilityScope = animatedVisibilityScope, resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds) } else { Modifier.sharedBounds(rememberSharedContentState(key = "card_${id}"), animatedVisibilityScope = animatedVisibilityScope, resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds) }
        Scaffold(modifier = Modifier.fillMaxSize().then(sharedModifier), containerColor = Color.Transparent, topBar = { Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textC, modifier = if (id == null) Modifier.sharedElement(rememberSharedContentState(key = "fab_icon"), animatedVisibilityScope = animatedVisibilityScope) else Modifier) }; Spacer(Modifier.width(16.dp)); Text(text = if (id == null) "Add title" else "Edit title", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = textC) } }, floatingActionButton = { AnimatedSaveFab(isEnabled = hasChanges, onClick = { performHaptic(view, "success"); if (title.isNotEmpty()) { scope.launch { delay(600); if (id != null) vm.updateAnime(ctx, id, title, ep.toIntOrNull()?:0, rate, uri) else vm.addAnime(ctx, title, ep.toIntOrNull()?:0, rate, uri); nav.popBackStack() } } else { Toast.makeText(ctx, "Enter title", Toast.LENGTH_SHORT).show() } }) }) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().background(bg))
                // ИСПРАВЛЕНИЕ: Добавил .imePadding() к скролл-контейнеру.
                // Теперь клавиатура не будет перекрывать поле ввода.
                Column(modifier = Modifier
                    .padding(innerPadding)
                    .imePadding() // <--- ВАЖНО: Учитываем клавиатуру
                    .padding(horizontal = 24.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.width(180.dp).aspectRatio(0.7f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp)).clickable { performHaptic(view, "light"); launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
                        val imageModifier = if (id != null) Modifier.sharedElement(rememberSharedContentState(key = "image_${id}"), animatedVisibilityScope = animatedVisibilityScope) else Modifier
                        Box(modifier = Modifier.fillMaxSize().then(imageModifier).clip(RoundedCornerShape(32.dp))) {
                            if (uri != null) AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else if (anime?.imageFileName != null) AsyncImage(vm.getImgPath(anime.imageFileName), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary); Text("Add Photo", color = MaterialTheme.colorScheme.secondary) }
                        }
                    }
                    Spacer(Modifier.height(32.dp))

                    // --- ИСПРАВЛЕННЫЙ БЛОК ВВОДА ---
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedOneUiTextField(
                                value = title,
                                onValueChange = { title = it },
                                placeholder = "Anime Title",
                                singleLine = false,
                                maxLines = 4
                            )
                        }
                        if (title.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            AnimatedCopyButton(textToCopy = title)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    AnimatedOneUiTextField(
                        value = ep,
                        onValueChange = { if (it.all { c -> c.isDigit() }) ep = it },
                        placeholder = "Episodes Watched",
                        keyboardType = KeyboardType.Number
                    )
                    // ----------------------------------------

                    Spacer(Modifier.height(12.dp))
                    EpisodeSuggestions { selectedEp -> performHaptic(view, "light"); ep = selectedEp }
                    Spacer(Modifier.height(24.dp))
                    StarRatingBar(rating = rate) { newRate -> performHaptic(view, "light"); rate = newRate }
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { window.attributes.preferredDisplayModeId = 0 }
        checkPerms()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            OneUiTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                val viewModel: AnimeViewModel = viewModel()
                LaunchedEffect(Unit) { viewModel.loadAnime() }
                SharedTransitionLayout {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") { HomeScreen(nav = navController, vm = viewModel, sharedTransitionScope = this@SharedTransitionLayout, animatedVisibilityScope = this, isDarkTheme = isDarkTheme, onToggleTheme = { isDarkTheme = !isDarkTheme }) }
                        composable("add_anime?animeId={animeId}", arguments = listOf(navArgument("animeId") { nullable = true })) { AddEditScreen(navController, viewModel, it.arguments?.getString("animeId"), this@SharedTransitionLayout, this) }
                    }
                }
            }
        }
    }
    private fun checkPerms() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")); startActivity(intent); Toast.makeText(this, "Need file access", Toast.LENGTH_LONG).show()
        }
    }
}