package com.fithall.app.platform

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.fithall.app.R
import com.fithall.app.auth.AuthException
import com.fithall.app.auth.SignInCancelled
import com.fithall.app.auth.SocialProvider
import com.fithall.app.auth.SocialSignIn
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CompletableDeferred

/**
 * Google through Credential Manager (needs Google sign-in enabled in Firebase, which adds
 * `default_web_client_id` to google-services.json) and Facebook through its Login SDK (needs
 * FACEBOOK_APP_ID and FACEBOOK_CLIENT_TOKEN). Either is left out when it isn't set up.
 */
@Composable
actual fun rememberSocialSignIn(): SocialSignIn {
    val context = LocalContext.current
    val googleClientId = remember { googleWebClientId(context) }
    val facebookReady = remember { startFacebook(context) }
    val callbackManager = remember { CallbackManager.Factory.create() }
    // The Facebook login in flight, completed by the callback below.
    val pending = remember { arrayOfNulls<CompletableDeferred<String>>(1) }

    DisposableEffect(facebookReady) {
        if (!facebookReady) return@DisposableEffect onDispose { }
        val login = LoginManager.getInstance()
        login.registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                pending[0]?.complete(result.accessToken.token)
                // FitHall keeps only its Firebase session, not a Facebook one.
                login.logOut()
            }
            override fun onCancel() {
                pending[0]?.completeExceptionally(SignInCancelled())
            }
            override fun onError(error: FacebookException) {
                pending[0]?.completeExceptionally(AuthException("Couldn't sign in with Facebook. Please try again.", error))
            }
        })
        onDispose { login.unregisterCallback(callbackManager) }
    }
    val facebookLauncher = rememberLauncherForActivityResult(
        LoginManager.getInstance().createLogInActivityResultContract(callbackManager, null),
    ) { /* Delivered to the callback registered above. */ }

    return remember(googleClientId, facebookReady) {
        object : SocialSignIn {
            override val available = buildList {
                if (googleClientId != null) add(SocialProvider.GOOGLE)
                if (facebookReady) add(SocialProvider.FACEBOOK)
            }

            override suspend fun token(provider: SocialProvider): String = when (provider) {
                SocialProvider.GOOGLE -> googleIdToken(context, googleClientId ?: throw notAvailable(provider))
                SocialProvider.FACEBOOK -> {
                    if (!facebookReady) throw notAvailable(provider)
                    val result = CompletableDeferred<String>()
                    pending[0] = result
                    facebookLauncher.launch(listOf("email", "public_profile"))
                    result.await()
                }
            }
        }
    }
}

private fun notAvailable(provider: SocialProvider) = AuthException("${provider.label} sign-in isn't set up in this build.")

private suspend fun googleIdToken(context: Context, clientId: String): String {
    // Credential Manager shows its account picker over the current activity.
    val activity = context as? Activity ?: throw AuthException("Couldn't open Google sign-in.")
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
        .build()
    val credential = try {
        CredentialManager.create(activity).getCredential(activity, request).credential
    } catch (e: GetCredentialCancellationException) {
        throw SignInCancelled()
    } catch (e: NoCredentialException) {
        throw AuthException("Add a Google account to this phone to continue with Google.", e)
    } catch (e: GetCredentialException) {
        throw AuthException("Couldn't sign in with Google. Please try again.", e)
    }
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw AuthException("Couldn't sign in with Google. Please try again.")
}

/** Generated by the google-services plugin once Google sign-in is enabled in Firebase. */
private fun googleWebClientId(context: Context): String? {
    @Suppress("DiscouragedApi")
    val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (id == 0) null else context.getString(id).ifBlank { null }
}

/** Starts the Facebook SDK on first use; false when this build has no Facebook keys. */
private fun startFacebook(context: Context): Boolean {
    if (context.getString(R.string.facebook_app_id).isBlank() || context.getString(R.string.facebook_client_token).isBlank()) return false
    if (!FacebookSdk.isInitialized()) {
        @Suppress("DEPRECATION")
        FacebookSdk.sdkInitialize(context.applicationContext)
        // Also off in the manifest; set again in case the SDK's defaults change.
        FacebookSdk.setAutoLogAppEventsEnabled(false)
        FacebookSdk.setAdvertiserIDCollectionEnabled(false)
    }
    return true
}
