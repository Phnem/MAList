package com.example.myapplication.sync.supabase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.phnem.vetro.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

val supabaseModule = org.koin.dsl.module {
    single<SupabaseClient> {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                scheme = "vetrocollection"
                host = "auth-callback"
                defaultRedirectUrl = "vetrocollection://auth-callback"
                // Implicit flow puts tokens in URL fragment (#...) — Android drops it on deeplink.
                flowType = FlowType.PKCE
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }

    single {
        SupabaseSyncCoordinator(
            context = androidContext(),
            syncRepository = get(),
            authRepository = get(),
            supabase = get(),
            collectionImageRestoreCoordinator = get(),
        )
    }

    single { AuthRepository(get(), get()) }
    single { SyncRepository(get(), get(), get(), get()) }
    single { CollectionImageRestoreCoordinator(get(), get(), get(), get(), get()) }
    single { AttachmentSyncManager(androidContext(), get(), get(), get()) }
}

class AuthRepository(
    private val supabase: SupabaseClient,
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    val isUserSignedIn: Flow<Boolean> = supabase.auth.sessionStatus.map {
        it is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
    }

    val linkedProvidersFlow: Flow<Set<String>> = supabase.auth.sessionStatus.map {
        linkedProviders()
    }
    
    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    var isGuest: Boolean
        get() = prefs.getBoolean("is_guest", false)
        set(value) = prefs.edit().putBoolean("is_guest", value).apply()

    val currentUserEmail: String?
        get() = supabase.auth.currentUserOrNull()?.email

    fun linkedProviders(): Set<String> {
        return supabase.auth.currentIdentitiesOrNull()
            ?.map { it.provider }
            ?.toSet()
            ?: emptySet()
    }

    fun hasToken(): Boolean {
        return supabase.auth.sessionStatus.value is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
    }

    fun signInAsGuest() {
        isGuest = true
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is androidx.credentials.CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                supabase.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    this.provider = Google
                    this.nonce = rawNonce
                }
                isGuest = false
                Result.success(Unit)
            } else {
                android.util.Log.e("AuthRepository", "Unsupported credential type")
                Result.failure(Exception("Unsupported credential type"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            android.util.Log.e("AuthRepository", "Google Sign in cancelled")
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "signInWithGoogle failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGithub(): Result<Unit> {
        return try {
            supabase.auth.signInWith(Github, redirectUrl = "vetrocollection://auth-callback") {}
            isGuest = false
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "signInWithGithub failed", e)
            Result.failure(e)
        }
    }

    suspend fun linkGoogle(): Result<Unit> {
        return try {
            supabase.auth.linkIdentity(Google, redirectUrl = "vetrocollection://auth-callback") {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "linkGoogle failed", e)
            Result.failure(e)
        }
    }

    suspend fun linkGithub(): Result<Unit> {
        return try {
            supabase.auth.linkIdentity(Github, redirectUrl = "vetrocollection://auth-callback") {}
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "linkGithub failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            isGuest = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            isGuest = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPasswordForEmail(email: String): Result<Unit> {
        return try {
            supabase.auth.resetPasswordForEmail(
                email = email.trim(),
                redirectUrl = "vetrocollection://auth-callback",
                captchaToken = "",
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPassword(newPassword: String): Result<Unit> {
        return try {
            supabase.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            isGuest = false
            supabase.auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
