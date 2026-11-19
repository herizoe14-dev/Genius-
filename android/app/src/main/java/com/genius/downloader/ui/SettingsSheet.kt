package com.genius.downloader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genius.downloader.BuildConfig
import com.genius.downloader.Prefs
import com.genius.downloader.ui.theme.Cyan
import com.genius.downloader.ui.theme.Surface1
import com.genius.downloader.ui.theme.TextDim

@Composable
fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val updating by vm.updating.collectAsState()
    var mp3 by remember { mutableStateOf(Prefs.audioMp3) }
    var auto by remember { mutableStateOf(Prefs.autoUpdate) }
    var paste by remember { mutableStateOf(Prefs.autoPaste) }
    val version = remember(updating) { vm.engineVersion() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Réglages", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SettingSwitch("Convertir l'audio en MP3", "Sinon : format d'origine (M4A/Opus), qualité max sans perte", mp3) {
                    mp3 = it; Prefs.audioMp3 = it
                }
                SettingSwitch("Coller automatiquement", "Récupère le lien copié à l'ouverture de l'app", paste) {
                    paste = it; Prefs.autoPaste = it
                }
                SettingSwitch("Mise à jour auto du moteur", "Met à jour yt-dlp une fois par jour", auto) {
                    auto = it; Prefs.autoUpdate = it
                }
                Spacer(Modifier.height(12.dp))
                Text("Moteur : yt-dlp $version", color = TextDim, fontSize = 13.sp)
                Text("Genius ${BuildConfig.VERSION_NAME}", color = TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.updateEngine() }, enabled = !updating) {
                    if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Cyan)
                    else androidx.compose.material3.Icon(Icons.Rounded.SystemUpdate, null, tint = Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(if (updating) "Mise à jour…" else "Mettre à jour yt-dlp maintenant", color = Cyan)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = Cyan) } },
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, color = TextDim, fontSize = 12.sp)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Cyan, checkedThumbColor = Color.Black),
        )
    }
}
