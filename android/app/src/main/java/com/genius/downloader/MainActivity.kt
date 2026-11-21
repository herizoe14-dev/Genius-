package com.genius.downloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import com.genius.downloader.ui.SnifferActivity
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.genius.downloader.download.DownloadManager
import com.genius.downloader.engine.Analyzer
import com.genius.downloader.ui.DownloadsScreen
import com.genius.downloader.ui.HomeScreen
import com.genius.downloader.ui.MainViewModel
import com.genius.downloader.ui.SettingsDialog
import com.genius.downloader.ui.theme.Bg
import com.genius.downloader.ui.theme.Cyan
import com.genius.downloader.ui.theme.GeniusTheme
import com.genius.downloader.ui.theme.Magenta
import com.genius.downloader.ui.theme.Surface1
import com.genius.downloader.ui.theme.TextDim
import com.genius.downloader.ui.theme.Violet

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val tab = mutableIntStateOf(0)
    private var lastClip: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        askPermissions()
        handleIntent(intent)
        setContent {
            GeniusTheme { Root(vm, tab.intValue, { tab.intValue = it }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.hasExtra(EXTRA_TAB)) tab.intValue = intent.getIntExtra(EXTRA_TAB, 0)
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            Analyzer.extractUrl(text)?.let {
                lastClip = it
                tab.intValue = 0
                vm.analyze(it)
            }
        }
    }

    /** Colle automatiquement un lien copié (lu au retour dans l'app). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !Prefs.autoPaste) return
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull() ?: return
        val url = Analyzer.extractUrl(text) ?: return
        if (url != lastClip && url != vm.input.value) {
            lastClip = url
            vm.input.value = url
        }
    }

    private fun askPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1)
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(vm: MainViewModel, tab: Int, onTab: (Int) -> Unit) {
    var settings by remember { mutableStateOf(false) }
    val tasks by DownloadManager.tasks.collectAsState()
    val toast by vm.toast.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(toast) {
        toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.consumeToast() }
    }
    val active = tasks.count { it.active }

    Scaffold(
        containerColor = Bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "GENIUS",
                        style = TextStyle(
                            brush = Brush.horizontalGradient(listOf(Cyan, Violet, Magenta)),
                            fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                        ),
                    )
                },
                actions = {
                    IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Settings, "Réglages", tint = TextDim) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Bg),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface1, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black, indicatorColor = Cyan,
                    selectedTextColor = Cyan, unselectedIconColor = TextDim, unselectedTextColor = TextDim,
                )
                NavigationBarItem(
                    selected = tab == 0, onClick = { onTab(0) },
                    icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("Accueil") }, colors = colors,
                )
                // Accès direct au navigateur Genius (page d'accueil Google)
                NavigationBarItem(
                    selected = false,
                    onClick = { ctx.startActivity(Intent(ctx, SnifferActivity::class.java)) },
                    icon = { Icon(Icons.Rounded.TravelExplore, null) }, label = { Text("Navigateur") }, colors = colors,
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { onTab(1) },
                    icon = {
                        BadgedBox(badge = { if (active > 0) Badge(containerColor = Magenta) { Text("$active") } }) {
                            Icon(Icons.Rounded.Download, null)
                        }
                    },
                    label = { Text("Téléchargements") },
                    colors = colors,
                )
            }
        },
    ) { padding ->
        if (tab == 0) HomeScreen(vm, padding) else DownloadsScreen(padding)
    }
    if (settings) SettingsDialog(vm) { settings = false }
}
