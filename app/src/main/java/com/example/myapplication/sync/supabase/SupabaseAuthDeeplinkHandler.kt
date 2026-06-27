package com.example.myapplication.sync.supabase

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "SupabaseAuthDeeplink"

object SupabaseAuthDeeplinkHandler {

    private const val AUTH_SCHEME = "vetrocollection"
    private const val AUTH_HOST = "auth-callback"

    @Volatile
    private var lastHandledCode: String? = null

    fun isAuthCallback(uri: Uri): Boolean =
        uri.scheme == AUTH_SCHEME && uri.host == AUTH_HOST

    /**
     * Handles OAuth / PKCE auth callbacks safely.
     *
     * supabase-kt 3.1.x launches [exchangeCodeForSession] on an internal scope without catching
     * errors, and calling [handleDeeplinks] twice (e.g. from onNewIntent + onResume) consumes
     * the PKCE verifier and crashes the process.
     */
    fun handle(
        lifecycleOwner: LifecycleOwner,
        supabase: SupabaseClient,
        intent: Intent?,
        onConsumed: () -> Unit = {},
    ) {
        val uri = intent?.data ?: return
        if (!isAuthCallback(uri)) return

        if (supabase.auth.config.flowType != FlowType.PKCE) {
            supabase.handleDeeplinks(intent)
            onConsumed()
            return
        }

        uri.getQueryParameter("error")?.let { error ->
            Log.e(TAG, "OAuth error: $error (${uri.getQueryParameter("error_description")})")
            onConsumed()
            return
        }

        val code = uri.getQueryParameter("code") ?: return
        if (code == lastHandledCode) {
            onConsumed()
            return
        }
        lastHandledCode = code
        onConsumed()

        lifecycleOwner.lifecycleScope.launch {
            try {
                supabase.auth.exchangeCodeForSession(code)
                Log.d(TAG, "PKCE session established")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PKCE exchange failed", e)
                if (lastHandledCode == code) {
                    lastHandledCode = null
                }
            }
        }
    }
}
