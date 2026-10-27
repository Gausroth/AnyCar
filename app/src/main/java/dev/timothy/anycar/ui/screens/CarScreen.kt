package dev.timothy.anycar.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.timothy.anycar.actions.SpokenResult
import dev.timothy.anycar.actions.UiChoice
import dev.timothy.anycar.ai.MetaError
import dev.timothy.anycar.graph
import dev.timothy.anycar.ui.PermissionCopy
import dev.timothy.anycar.voice.RecognizerPath
import dev.timothy.anycar.voice.VoicePhase
import kotlinx.coroutines.launch

/**
 * The car screen: huge buttons, high contrast, no text entry anywhere (M4).
 * Fonts meet the minimums: status 32 sp or more, buttons and body 24 sp or more.
 */
@Composable
fun CarScreen(onQuit: () -> Unit) {
    val ctx = LocalContext.current
    val graph = ctx.graph
    val session = graph.session
    val vehicle by session.activeVehicle.collectAsStateWithLifecycle()
    val connected by session.connected.collectAsStateWithLifecycle()
    val voice by session.voice.state.collectAsStateWithLifecycle()
    val metaAi by graph.settings.metaAiEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

    // A choice that needs contacts runs only once the permission is granted.
    var waitingForContacts by remember { mutableStateOf<UiChoice?>(null) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val choice = waitingForContacts
        waitingForContacts = null
        if (granted && choice != null) session.voice.pick(choice)
        else session.voice.say(SpokenResult("Contacts permission is off, so I can't look up names."))
    }

    var scan by remember { mutableStateOf<ScanState?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (!saved) { scan = null; return@rememberLauncherForActivityResult }
        scope.launch {
            val (photo, jpeg) = loadScanPhoto(ctx) ?: run { scan = ScanState.Failed("The camera didn't save a photo. Try again."); return@launch }
            scan = ScanState.Working(photo)
            scan = try {
                val report = graph.scanner.scan(jpeg, vehicle)
                session.voice.say(SpokenResult(report.spoken.ifBlank { "I couldn't tell. Try again closer to the dashboard." }))
                ScanState.Done(photo, report)
            } catch (e: MetaError) {
                Log.w("CarScreen", "Scan failed: ${e.message}")
                val msg = if (e is MetaError.Offline) "No connection. Try again when you have signal." else "The scan didn't work. Try again."
                session.voice.say(msg)
                ScanState.Failed(msg)
            }
        }
    }
    fun startScan() {
        session.voice.cancel()
        scan = null
        takePhoto.launch(scanPhotoUri(ctx))
    }

    LaunchedEffect(Unit) { session.voice.refreshPath() }
    BackHandler(onBack = { if (scan != null) scan = null else onQuit() })

    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = scan
            if (current != null) {
                DashboardScanPanel(current, onScanAgain = ::startScan, onClose = { scan = null })
                return@Column
            }

            Text(
                text = vehicle?.displayName ?: "AnyCar",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Text(
                text = if (vehicle?.hasBluetooth == true) (if (connected) "Connected" else "Not connected") else "No Bluetooth: phone mic and speaker",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = voice.message, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp)
                if (voice.heard.isNotBlank()) Text(text = "Heard: ${voice.heard}", fontSize = 24.sp)
                voice.choices.forEach { choice ->
                    BigButton(choice.label, outlined = true) {
                        if (choice.needsContactsPermission) {
                            waitingForContacts = choice
                            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            session.voice.pick(choice)
                        }
                    }
                }
                if (voice.choices.any { it.needsContactsPermission }) Text(PermissionCopy.CONTACTS, fontSize = 24.sp)
            }

            when {
                !micGranted -> {
                    Text(PermissionCopy.MICROPHONE, fontSize = 24.sp)
                    BigButton("Allow microphone") { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                }
                voice.path == RecognizerPath.NEEDS_CLOUD_CONSENT -> {
                    Text(PermissionCopy.CLOUD_SPEECH, fontSize = 24.sp)
                    BigButton("Use Google speech") {
                        scope.launch {
                            graph.settings.setCloudSpeechAllowed(true)
                            session.voice.refreshPath()
                        }
                    }
                    if (graph.meta.configured) {
                        Text(PermissionCopy.META_SPEECH, fontSize = 24.sp)
                        BigButton("Use Meta speech") {
                            scope.launch {
                                graph.settings.setMetaSpeechEnabled(true)
                                session.voice.refreshPath()
                            }
                        }
                    }
                }
                else -> BigButton(
                    label = when (voice.phase) {
                        VoicePhase.LISTENING -> "Listening…"
                        VoicePhase.PROCESSING -> "Working…"
                        else -> "Talk"
                    },
                    height = 200,
                    enabled = voice.phase == VoicePhase.IDLE || voice.phase == VoicePhase.SPEAKING,
                ) { session.voice.startListening() }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (metaAi && graph.meta.configured) {
                    OutlinedButton(
                        onClick = ::startScan,
                        modifier = Modifier.weight(1f).height(80.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) { Text("Scan a light", fontSize = 24.sp) }
                }
                OutlinedButton(
                    onClick = onQuit,
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Quit", fontSize = 26.sp) }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, height: Int = 96, enabled: Boolean = true, outlined: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().height(height.dp)
    val shape = RoundedCornerShape(28.dp)
    val text: @Composable () -> Unit = {
        Text(label, fontSize = if (height > 120) 48.sp else 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    }
    if (outlined) OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = shape) { text() }
    else Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = shape, colors = ButtonDefaults.buttonColors()) { text() }
}
