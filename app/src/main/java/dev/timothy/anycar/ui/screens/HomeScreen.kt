package dev.timothy.anycar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.ui.PermissionCopy

data class MetaSettings(val configured: Boolean = false, val ai: Boolean = false, val speech: Boolean = false)

/**
 * Hackathon edition: Meta models, each off until turned on. The disclosure sits right above its switch,
 * so the driver reads what's sent before the first send (§8.3, §8.4).
 */
@Composable
private fun MetaCard(meta: MetaSettings, onMetaAi: (Boolean) -> Unit, onMetaSpeech: (Boolean) -> Unit) {
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Meta AI", style = MaterialTheme.typography.titleLarge)
            if (!meta.configured) {
                Text("This build has no Meta API key. Add META_API_KEY to local.properties and rebuild.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            SettingSwitch("Smart commands and light scan", PermissionCopy.META_AI, meta.ai, onMetaAi)
            SettingSwitch("Meta speech", PermissionCopy.META_SPEECH, meta.speech, onMetaSpeech)
            TextButton(onClick = { uri.openUri(PermissionCopy.META_PRIVACY_URL) }) { Text("Meta privacy policy") }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The launcher screen: your cars, "Enter car mode", and "Add a car". */
@Composable
fun HomeScreen(
    vehicles: List<VehicleProfile>,
    onEnterCarMode: (VehicleProfile) -> Unit,
    onAddCar: () -> Unit,
    onOpenDebug: (() -> Unit)?,
    meta: MetaSettings = MetaSettings(),
    onMetaAi: (Boolean) -> Unit = {},
    onMetaSpeech: (Boolean) -> Unit = {},
) {
    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("AnyCar", style = MaterialTheme.typography.displayMedium)
            Text("The universal vehicle assistant.", style = MaterialTheme.typography.titleMedium)

            val default = vehicles.firstOrNull { it.isDefaultVehicle } ?: vehicles.firstOrNull()
            if (default != null) {
                Button(onClick = { onEnterCarMode(default) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enter car mode · ${default.displayName}")
                }
            }

            vehicles.forEach { v ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(v.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                if (v.hasBluetooth) v.btName else "No Bluetooth",
                                if (v.isDefaultVehicle) "Default" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (vehicles.size > 1) {
                            OutlinedButton(onClick = { onEnterCarMode(v) }) { Text("Car mode in this car") }
                        }
                    }
                }
            }

            OutlinedButton(onClick = onAddCar, modifier = Modifier.fillMaxWidth()) { Text("Add a car") }

            MetaCard(meta, onMetaAi, onMetaSpeech)

            if (onOpenDebug != null) OutlinedButton(onClick = onOpenDebug, modifier = Modifier.fillMaxWidth()) { Text("Debug tools") }
        }
    }
}
