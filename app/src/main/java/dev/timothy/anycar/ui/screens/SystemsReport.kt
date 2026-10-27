package dev.timothy.anycar.ui.screens

import android.app.Activity
import android.content.ClipData
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.timothy.anycar.car.CompanionLink
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.timothy.anycar.car.SupportPolicy
import dev.timothy.anycar.car.SupportStatus
import dev.timothy.anycar.car.WheelButton
import dev.timothy.anycar.data.CheckResult
import dev.timothy.anycar.data.CheckStatus
import dev.timothy.anycar.data.ProbeKeys
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.graph
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ROWS = listOf(
    ProbeKeys.VOICE to "Voice",
    ProbeKeys.AUTO_LAUNCH to "Auto-launch",
    ProbeKeys.AUDIO_ROUTE to "Car audio",
    ProbeKeys.WHEEL_BUTTON to "Wheel button",
    ProbeKeys.OBD_LINK to "Diagnostics adapter",
    ProbeKeys.EXTENDED_PIDS to "Extended data",
)

private val NOT_CHECKED = CheckResult(CheckStatus.NOT_TESTED, "Not checked yet. Tap Re-check.", 0)
private val WHEEL_NOT_TESTED = CheckResult(CheckStatus.NOT_TESTED, "Not tested yet. Press Test while connected to the car.", 0)

/** "Systems ✓", or how many rows need the driver (M3.4). FAIL rows are phone or car limits, so they don't count. */
fun systemsChip(v: VehicleProfile?): String {
    val warn = ROWS.count { (key, _) -> v?.capabilityCheckResults?.get(key)?.status == CheckStatus.WARN }
    return when (warn) {
        0 -> "Systems ✓"
        1 -> "1 item needs attention"
        else -> "$warn items need attention"
    }
}

/** The compatibility report (§9.3), shown from the car screen and from the home screen. [large] uses car-screen sizes. */
@Composable
fun SystemsReport(vehicleId: Long, large: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val graph = ctx.graph
    val scope = rememberCoroutineScope()
    val vehicles by remember { graph.vehicles.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val v = vehicles.firstOrNull { it.id == vehicleId } ?: return
    var checking by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    val title = if (large) 30.sp else 22.sp
    val body = if (large) 24.sp else 16.sp

    fun recheck() = scope.launch {
        checking = true
        graph.checker.full(v, connected = graph.session.activeVehicle.value?.id == v.id && graph.session.connected.value)
        checking = false
    }

    // Linking the car from the report, with the same system dialog as setup (M1.3).
    var linkMessage by remember { mutableStateOf("") }
    val linkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val mac = v.btMac
        if (Build.VERSION.SDK_INT < 33 && result.resultCode == Activity.RESULT_OK && mac != null && CompanionLink.isLinked(ctx, mac)) {
            scope.launch { graph.vehicles.setCdmAssociationId(v.id, 0); recheck() }
        }
    }
    fun link() {
        val mac = v.btMac ?: return
        val activity = runCatching { ctx.findActivity() }.getOrNull() ?: return
        linkMessage = "Looking for your car…"
        CompanionLink.associate(
            activity, mac, linkLauncher,
            onLinked = { id -> linkMessage = ""; scope.launch { graph.vehicles.setCdmAssociationId(v.id, id); recheck() } },
            onFailure = { linkMessage = "Couldn't link. Try again while your phone is connected to the car, with Location on." },
        )
    }

    // On open, and on coming back from Android's settings, so a permission just granted shows at once.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheck() }

    fun wheelTest() = scope.launch {
        countdown = WheelButton.TEST_SECONDS
        val tick = launch { while (countdown > 0) { delay(1_000); countdown-- } }
        val result = WheelButton.test(ctx, v, graph.vehicles)
        tick.cancel()
        countdown = 0
        if (result.status == CheckStatus.PASS) graph.session.voice.say("Steering-wheel button linked.")
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${v.displayName} · ${v.btName}", fontSize = title, fontWeight = FontWeight.SemiBold)
        if (SupportPolicy.status(v.year, Locale.getDefault().country) == SupportStatus.NOT_SUPPORTED) {
            Text("This vehicle isn't supported. AnyCar works with cars from 1996 and newer.", fontSize = body, color = statusColor(CheckStatus.FAIL))
        }
        if (countdown > 0) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Press your steering-wheel voice button now. $countdown",
                    fontSize = title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp),
                )
            }
        }

        ROWS.forEach { (key, name) ->
            val r = v.capabilityCheckResults[key] ?: if (key == ProbeKeys.WHEEL_BUTTON) WHEEL_NOT_TESTED else NOT_CHECKED
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 8.dp).size(if (large) 20.dp else 14.dp).background(statusColor(r.status), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$name · ${label(r.status)}", fontSize = body, fontWeight = FontWeight.Bold)
                    Text(r.note, fontSize = body)
                    Text(
                        when {
                            r.checkedAt == 0L -> "never checked"
                            System.currentTimeMillis() - r.checkedAt < 60_000 -> "checked just now"
                            else -> "checked ${DateUtils.getRelativeTimeSpanString(r.checkedAt)}".lowercase()
                        },
                        fontSize = if (large) 20.sp else 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    rowActions(ctx, key, r, v, onWheelTest = ::wheelTest, onLink = ::link, countdownRunning = countdown > 0).forEach { (label, action) ->
                        OutlinedButton(onClick = action, modifier = Modifier.heightIn(min = if (large) 72.dp else 48.dp)) {
                            Text(label, fontSize = body)
                        }
                    }
                    if (key == ProbeKeys.AUTO_LAUNCH && linkMessage.isNotBlank()) Text(linkMessage, fontSize = body)
                    if (key == ProbeKeys.WHEEL_BUTTON && v.wheelButtonSupported) {
                        OutlinedButton(
                            onClick = { scope.launch { graph.vehicles.setWheelButtonEnabled(v.id, !v.wheelButtonEnabled); WheelButton.sync(ctx, graph.vehicles.all()) } },
                            modifier = Modifier.heightIn(min = if (large) 72.dp else 48.dp),
                        ) { Text(if (v.wheelButtonEnabled) "Turn off" else "Turn on", fontSize = body) }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { recheck() }, enabled = !checking, modifier = Modifier.weight(1f).heightIn(min = if (large) 80.dp else 48.dp)) {
                Text(if (checking) "Checking…" else "Re-check", fontSize = body)
            }
            OutlinedButton(onClick = { copySummary(ctx, v) }, modifier = Modifier.weight(1f).heightIn(min = if (large) 80.dp else 48.dp)) {
                Text("Copy summary", fontSize = body)
            }
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = if (large) 80.dp else 48.dp)) {
            Text("Close", fontSize = body)
        }
    }
}

/** The buttons for a row. Auto-launch reads the live state, so every missing piece gets its own button. */
private fun rowActions(
    ctx: Context, key: String, r: CheckResult, v: VehicleProfile,
    onWheelTest: () -> Unit, onLink: () -> Unit, countdownRunning: Boolean,
): List<Pair<String, () -> Unit>> = when (key) {
    ProbeKeys.WHEEL_BUTTON -> if (v.hasBluetooth && !countdownRunning) {
        listOf((if (r.status == CheckStatus.NOT_TESTED) "Test" else "Re-verify") to onWheelTest)
    } else {
        emptyList()
    }
    ProbeKeys.VOICE -> if (r.status == CheckStatus.WARN) listOf("Fix" to { openAppSettings(ctx) }) else emptyList()
    ProbeKeys.AUTO_LAUNCH -> {
        val mac = v.btMac
        buildList {
            if (mac != null && Build.VERSION.SDK_INT >= 31 && !CompanionLink.isLinked(ctx, mac)) add("Link car" to onLink)
            if (mac != null && Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(ctx)) {
                add("Allow" to {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                })
            }
            if (r.note.startsWith("Turn on notifications")) add("Open settings" to { openAppSettings(ctx) })
        }
    }
    else -> emptyList()
}

private fun openAppSettings(ctx: Context) = ctx.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

private fun copySummary(ctx: Context, v: VehicleProfile) {
    val text = buildString {
        appendLine("AnyCar systems report: ${v.displayName} (${v.btName})")
        ROWS.forEach { (key, name) ->
            val r = v.capabilityCheckResults[key] ?: NOT_CHECKED
            appendLine("$name: ${label(r.status)}. ${r.note}")
        }
    }
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AnyCar systems report", text))
}

private fun label(s: CheckStatus) = when (s) {
    CheckStatus.PASS -> "Works"
    CheckStatus.WARN -> "Needs attention"
    CheckStatus.FAIL -> "Not available"
    CheckStatus.NOT_TESTED -> "Not tested"
}

private fun statusColor(s: CheckStatus) = when (s) {
    CheckStatus.PASS -> Color(0xFF43A047)
    CheckStatus.WARN -> Color(0xFFFFB300)
    CheckStatus.FAIL -> Color(0xFFE53935)
    CheckStatus.NOT_TESTED -> Color(0xFF9E9E9E)
}
