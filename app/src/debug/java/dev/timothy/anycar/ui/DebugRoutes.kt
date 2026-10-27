package dev.timothy.anycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.timothy.anycar.car.CarConnectionHandler
import dev.timothy.anycar.graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TEST_DRIVE_S = 15

/** Debug builds only (§6.6): fake car connect/disconnect and fake speech. */
fun NavGraphBuilder.debugRoutes() {
    composable(Routes.DEBUG) { DebugScreen() }
}

@Composable
private fun DebugScreen() {
    val ctx = LocalContext.current
    val graph = ctx.graph
    val scope = rememberCoroutineScope()
    val vehicles by remember { graph.vehicles.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val active by graph.session.activeVehicle.collectAsStateWithLifecycle()
    val connected by graph.session.connected.collectAsStateWithLifecycle()
    val serviceRunning by graph.session.serviceRunning.collectAsStateWithLifecycle()
    var transcript by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Debug tools", style = MaterialTheme.typography.headlineMedium)
            Text("Session: ${active?.displayName ?: "none"} · connected=$connected · service=$serviceRunning")

            Text("Fake car", style = MaterialTheme.typography.titleMedium)
            vehicles.filter { it.hasBluetooth }.forEach { v ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${v.displayName} (${v.btName})")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { CarConnectionHandler.onAclConnected(ctx.applicationContext, v.btMac!!) } }) {
                                Text("Connect")
                            }
                            OutlinedButton(onClick = {
                                if (active?.id != v.id) note = "No drive to end: Back or Quit in car mode already ended it."
                                scope.launch { CarConnectionHandler.onAclDisconnected(ctx.applicationContext, v.btMac!!) }
                            }) {
                                Text("Disconnect")
                            }
                        }
                        // Car mode covers this screen, so this one disconnects by itself. Expect "Disconnected"
                        // and car mode closing TEST_DRIVE_S + 8 s after the tap.
                        Button(onClick = {
                            note = "Test drive: disconnecting in $TEST_DRIVE_S s, then car mode should close 8 s later."
                            graph.appScope.launch {
                                CarConnectionHandler.onAclConnected(ctx.applicationContext, v.btMac!!)
                                delay(TEST_DRIVE_S * 1000L)
                                CarConnectionHandler.onAclDisconnected(ctx.applicationContext, v.btMac)
                            }
                        }) {
                            Text("Test drive (${TEST_DRIVE_S} s)")
                        }
                    }
                }
            }
            if (vehicles.none { it.hasBluetooth }) Text("Add a Bluetooth car to simulate connects.")
            if (note.isNotBlank()) Text(note, color = MaterialTheme.colorScheme.primary)

            Text("Fake speech", style = MaterialTheme.typography.titleMedium)
            Text("Text entry is fine here: this isn't car mode.")
            OutlinedTextField(transcript, { transcript = it }, label = { Text("Transcript") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { graph.session.voice.handleTranscript(transcript) }, enabled = transcript.isNotBlank()) {
                Text("Run as if heard")
            }
            val voice by graph.session.voice.state.collectAsStateWithLifecycle()
            Text("Voice: ${voice.phase} · path=${voice.path} · \"${voice.message}\"")
        }
    }
}
