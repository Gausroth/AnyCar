package dev.timothy.anycar.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.timothy.anycar.R
import dev.timothy.anycar.actions.SpokenResult
import dev.timothy.anycar.actions.UiChoice
import dev.timothy.anycar.ai.MetaError
import dev.timothy.anycar.car.DayNight
import dev.timothy.anycar.graph
import dev.timothy.anycar.ui.PermissionCopy
import dev.timothy.anycar.voice.RecognizerPath
import dev.timothy.anycar.voice.VoicePhase
import java.time.LocalTime
import kotlinx.coroutines.launch

private enum class Sheet { MORE, SYSTEMS }

/**
 * The car screen (M4.3): a status line, a 2×2 grid (Talk, Call, Navigate, Music), and a bottom row.
 * Big buttons, high contrast, and no text entry, scrolling feeds, web views, or video anywhere.
 * Fonts meet the minimums: status 32 sp or more, buttons and body 24 sp or more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarScreen(onQuit: () -> Unit) {
    val ctx = LocalContext.current
    val graph = ctx.graph
    val session = graph.session
    val voice = session.voice
    val vehicle by session.activeVehicle.collectAsStateWithLifecycle()
    val connected by session.connected.collectAsStateWithLifecycle()
    val state by voice.state.collectAsStateWithLifecycle()
    val metaAi by graph.settings.metaAiEnabled.collectAsStateWithLifecycle(initialValue = false)
    val dayNight by graph.settings.dayNightPref.collectAsStateWithLifecycle(initialValue = "auto")
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
        if (granted && choice != null) voice.pick(choice)
        else voice.say(SpokenResult("Contacts permission is off, so I can't look up names."))
    }

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var scan by remember { mutableStateOf<ScanState?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (!saved) { scan = null; return@rememberLauncherForActivityResult }
        scope.launch {
            val (photo, jpeg) = loadScanPhoto(ctx) ?: run { scan = ScanState.Failed("The camera didn't save a photo. Try again."); return@launch }
            scan = ScanState.Working(photo)
            scan = try {
                val report = graph.scanner.scan(jpeg, vehicle)
                voice.say(SpokenResult(report.spoken.ifBlank { "I couldn't tell. Try again closer to the dashboard." }))
                ScanState.Done(photo, report)
            } catch (e: MetaError) {
                Log.w("CarScreen", "Scan failed: ${e.message}")
                val msg = if (e is MetaError.Offline) "No connection. Try again when you have signal." else "The scan didn't work. Try again."
                voice.say(msg)
                ScanState.Failed(msg)
            }
        }
    }
    fun startScan() {
        voice.cancel()
        sheet = null
        scan = null
        takePhoto.launch(scanPhotoUri(ctx))
    }

    LaunchedEffect(Unit) { voice.refreshPath() }
    BackHandler(onBack = { if (scan != null) scan = null else onQuit() })

    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = scan
            if (current != null) {
                DashboardScanPanel(current, onScanAgain = ::startScan, onClose = { scan = null })
                return@Column
            }

            if ((ctx as? Activity)?.isInMultiWindowMode == true) {
                Text("Exit split-screen for car mode", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            }

            // The car's name can shorten; the connection state never does.
            Row {
                Text(
                    text = vehicle?.displayName ?: "AnyCar",
                    fontSize = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " · " + if (vehicle?.hasBluetooth == true) (if (connected) "Connected" else "Not connected") else "Phone speaker",
                    fontSize = 24.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Status line: what was heard while listening, the reply while speaking (M4.3).
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.message, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (state.heard.isNotBlank()) {
                    Text("Heard: ${state.heard}", fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            when {
                !micGranted -> {
                    Text(PermissionCopy.MICROPHONE, fontSize = 24.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Big("Allow microphone", Modifier.fillMaxWidth()) { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                }
                state.path == RecognizerPath.NEEDS_CLOUD_CONSENT -> {
                    Text(PermissionCopy.CLOUD_SPEECH, fontSize = 24.sp)
                    Big("Use Google speech", Modifier.fillMaxWidth()) {
                        scope.launch { graph.settings.setCloudSpeechAllowed(true); voice.refreshPath() }
                    }
                    if (graph.meta.configured) {
                        Big("Use Meta speech", Modifier.fillMaxWidth(), outlined = true) {
                            scope.launch { graph.settings.setMetaSpeechEnabled(true); voice.refreshPath() }
                        }
                    }
                }
                else -> Grid(state.phase) { action ->
                    when (action) {
                        GridAction.TALK -> voice.startListening()
                        GridAction.CALL -> voice.startListening(prompt = "Who should I call?", prefix = "call")
                        GridAction.NAVIGATE -> voice.startListening(prompt = "Where to?", prefix = "navigate to")
                        GridAction.HOME -> voice.command("go home")
                        GridAction.MUSIC -> voice.command("play")
                        GridAction.NEXT -> voice.command("next")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Big(systemsChip(vehicle), Modifier.weight(1f), height = 72, outlined = true, fontSize = 22) { sheet = Sheet.SYSTEMS }
                Big("More", Modifier.weight(1f), height = 72, outlined = true) { sheet = Sheet.MORE }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val night = DayNight.isNight(dayNight, LocalTime.now().hour)
                Big(if (night) "Day screen" else "Night screen", Modifier.weight(1f), height = 72, outlined = true, fontSize = 22) {
                    scope.launch { graph.settings.setDayNightPref(DayNight.next(dayNight, LocalTime.now().hour)) }
                }
                Big("Quit car mode", Modifier.weight(1f), height = 72, outlined = true, fontSize = 22, onClick = onQuit)
            }
        }
    }

    // Contact choices, at most 3 full-width buttons (M4.3).
    if (state.choices.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { voice.dismissChoices() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                state.choices.forEach { choice ->
                    Big(choice.label, Modifier.fillMaxWidth(), outlined = true, fontSize = 24) {
                        if (choice.needsContactsPermission) {
                            waitingForContacts = choice
                            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            voice.pick(choice)
                        }
                    }
                }
                if (state.choices.any { it.needsContactsPermission }) Text(PermissionCopy.CONTACTS, fontSize = 22.sp)
                Big("Cancel", Modifier.fillMaxWidth(), height = 72) { voice.dismissChoices(); voice.command("cancel") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    when (sheet) {
        Sheet.MORE -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (metaAi && graph.meta.configured) {
                    Big("Scan a warning light", Modifier.fillMaxWidth(), height = 80, onClick = ::startScan)
                } else {
                    Big("Diagnostics", Modifier.fillMaxWidth(), height = 80) { sheet = null; voice.command("check engine") }
                }
                Big("Systems", Modifier.fillMaxWidth(), height = 80, outlined = true) { sheet = Sheet.SYSTEMS }
                Big("Close", Modifier.fillMaxWidth(), height = 80, outlined = true) { sheet = null }
                Spacer(Modifier.height(16.dp))
            }
        }
        Sheet.SYSTEMS -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(16.dp)) {
                vehicle?.let { SystemsReport(it.id, large = true) { sheet = null } }
                Spacer(Modifier.height(16.dp))
            }
        }
        null -> Unit
    }
}

private enum class GridAction { TALK, CALL, NAVIGATE, HOME, MUSIC, NEXT }

/** Talk and Call on top; Navigate (with Go home) and Music (with Next) below. Every target is 72 dp or more. */
@Composable
private fun Grid(phase: VoicePhase, onAction: (GridAction) -> Unit) {
    val busy = phase == VoicePhase.LISTENING || phase == VoicePhase.PROCESSING
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onAction(GridAction.TALK) },
                enabled = !busy,
                modifier = Modifier.weight(1f).height(150.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (phase) {
                            VoicePhase.LISTENING -> "Listening"
                            VoicePhase.PROCESSING -> "Working"
                            else -> "Talk"
                        },
                        fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Big("Call", Modifier.weight(1f), height = 150, outlined = true, fontSize = 34, enabled = !busy) { onAction(GridAction.CALL) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Big("Navigate", Modifier.fillMaxWidth(), height = 110, outlined = true, fontSize = 30, enabled = !busy) { onAction(GridAction.NAVIGATE) }
                Big("Go home", Modifier.fillMaxWidth(), height = 72, outlined = true, enabled = !busy) { onAction(GridAction.HOME) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Big("Play / Pause", Modifier.fillMaxWidth(), height = 110, outlined = true, fontSize = 28) { onAction(GridAction.MUSIC) }
                Big("Next song", Modifier.fillMaxWidth(), height = 72, outlined = true) { onAction(GridAction.NEXT) }
            }
        }
    }
}

@Composable
private fun Big(
    label: String,
    modifier: Modifier,
    height: Int = 96,
    outlined: Boolean = false,
    fontSize: Int = 24,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val m = modifier.heightIn(min = height.dp)
    val shape = RoundedCornerShape(24.dp)
    val text: @Composable RowScope.() -> Unit = {
        Text(
            label, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, lineHeight = (fontSize + 4).sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
    if (outlined) OutlinedButton(onClick = onClick, enabled = enabled, modifier = m, shape = shape, content = text)
    else Button(onClick = onClick, enabled = enabled, modifier = m, shape = shape, colors = ButtonDefaults.buttonColors(), content = text)
}
