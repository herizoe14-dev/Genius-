package com.genius.downloader.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.genius.downloader.engine.Account
import com.genius.downloader.engine.Accounts
import com.genius.downloader.ui.theme.Bg
import com.genius.downloader.ui.theme.Cyan
import com.genius.downloader.ui.theme.GeniusTheme

/**
 * Connexion à un compte (Instagram, Facebook, TikTok) dans une WebView.
 * Le mot de passe est saisi directement sur le site ; l'app ne garde que les cookies, sur le téléphone.
 */
class LoginActivity : ComponentActivity() {
    private lateinit var account: Account
    private var loading by mutableStateOf(true)
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        account = runCatching { Account.valueOf(intent.getStringExtra(EXTRA_ACCOUNT)!!) }.getOrElse { finish(); return }

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Sans le marqueur "wv", les sites acceptent mieux la connexion.
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { loading = true }
                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    checkLoggedIn()
                }
            }
            loadUrl(account.loginUrl)
        }

        setContent {
            GeniusTheme {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(
                        title = { Text("Connexion ${account.label}") },
                        navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Rounded.Close, "Fermer") } },
                        actions = { TextButton(onClick = { checkLoggedIn(force = true) }) { Text("Terminé", color = Cyan) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                    )
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                    Text(
                        "Connectez-vous normalement. Vos identifiants restent sur le site ; Genius garde seulement la session sur ce téléphone.",
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    AndroidView({ web }, Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun checkLoggedIn(force: Boolean = false) {
        if (done) return
        CookieManager.getInstance().flush()
        if (Accounts.isLoggedIn(account)) {
            done = true
            Accounts.exportForYtDlp()
            Toast.makeText(this, "${account.label} connecté ✓", Toast.LENGTH_SHORT).show()
            finish()
        } else if (force) {
            Toast.makeText(this, "Pas encore connecté à ${account.label}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }
}
