package net.kikin.nubecita

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.designsystem.NubecitaTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var oauthRedirectBroker: OAuthRedirectBroker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            NubecitaTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
        }
        // Cold-start case: the OAuth redirect arrived while the app was dead and Android
        // launched MainActivity with the redirect intent. The broker buffers until
        // LoginViewModel's init-time collector subscribes.
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm-start case: launchMode="singleTask" + the intent filter route the redirect
        // back to this existing instance instead of spawning a new task.
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        // Defense in depth: the manifest intent filter constrains scheme + path, but
        // also re-validate here so a misconfigured filter (or a future second deep link
        // sharing the scheme) can't leak unrelated URIs into the OAuth completeLogin
        // path. Path must match the redirect_uri declared in client-metadata.json.
        if (uri.scheme == OAUTH_REDIRECT_SCHEME && uri.path == OAUTH_REDIRECT_PATH) {
            val redirectUri = uri.toString()
            lifecycleScope.launch { oauthRedirectBroker.publish(redirectUri) }
            // Consume so configuration changes (rotation, theme switch, dark-mode flip)
            // don't re-fire the redirect handler and double-invoke completeLogin.
            intent.data = null
        }
    }

    private companion object {
        const val OAUTH_REDIRECT_SCHEME = "net.kikin.nubecita"
        const val OAUTH_REDIRECT_PATH = "/oauth-redirect"
    }
}
