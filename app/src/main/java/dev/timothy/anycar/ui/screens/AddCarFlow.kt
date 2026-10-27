package dev.timothy.anycar.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.timothy.anycar.car.CompanionLink
import dev.timothy.anycar.car.MakeGuesser
import dev.timothy.anycar.car.SupportPolicy
import dev.timothy.anycar.car.SupportStatus
import dev.timothy.anycar.data.CheckResult
import dev.timothy.anycar.data.CheckStatus
import dev.timothy.anycar.data.ProbeKeys
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.graph
import dev.timothy.anycar.ui.PermissionCopy
import java.util.Locale
import kotlinx.coroutines.launch

private enum class Step { WELCOME, PERMISSIONS, HAS_BLUETOOTH, PICK_DEVICE, NAME_CAR, DETAILS, LINK, OVERLAY }

private data class BondedCar(val name: String, val mac: String)

/**
 * First run (§9.1) and "Add a car". [firstRun] adds the welcome and permission steps.
 * The capability report (step 6) arrives with the capability checker.
 */
@Composable
fun AddCarFlow(firstRun: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val graph = ctx.graph
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(if (firstRun) Step.WELCOME else Step.HAS_BLUETOOTH) }
    var btName by rememberSaveable { mutableStateOf("") }
    var btMac by rememberSaveable { mutableStateOf<String?>(null) }
    var savedId by rememberSaveable { mutableStateOf(-1L) }
    var linkMessage by remember { mutableStateOf("") }

    val firstRunPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val askFirstRunPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        step = Step.HAS_BLUETOOTH  // declines aren't fatal
    }
    // Compose state, so granting the permission redraws the picker with the paired list.
    var btGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)) }
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        btGranted = ok
        step = Step.PICK_DEVICE
    }
    val linkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Below API 33 the association is confirmed here; on 33+ the callback already reported it.
        val mac = btMac
        if (Build.VERSION.SDK_INT < 33 && result.resultCode == Activity.RESULT_OK && mac != null && CompanionLink.isLinked(ctx, mac)) {
            scope.launch { graph.vehicles.setCdmAssociationId(savedId, 0) }
            step = Step.OVERLAY
        }
    }

    fun finish() = scope.launch {
        graph.settings.setFirstRunDone(true)
        onDone()
    }

    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                Step.WELCOME -> {
                    Title("Welcome to AnyCar")
                    Body("AnyCar is a voice-first car mode for any car. It works best over Bluetooth, but your car doesn't need it. No extra hardware is needed.")
                    Primary("Set up my car") { step = Step.PERMISSIONS }
                }

                Step.PERMISSIONS -> {
                    Title("Two permissions")
                    Body(PermissionCopy.MICROPHONE)
                    if (Build.VERSION.SDK_INT >= 33) Body(PermissionCopy.NOTIFICATIONS)
                    Primary("Continue") { askFirstRunPermissions.launch(firstRunPermissions) }
                }

                Step.HAS_BLUETOOTH -> {
                    Title("Does your car have Bluetooth for calls or music?")
                    Body("A 12 V Bluetooth adapter counts.")
                    // Pick shows the Nearby devices disclosure first if the permission is still needed.
                    Primary("Yes") { step = Step.PICK_DEVICE }
                    Secondary("My car has no Bluetooth") { step = Step.NAME_CAR }
                }

                Step.PICK_DEVICE -> {
                    Title("Pick your car")
                    if (!btGranted) {
                        Body(PermissionCopy.NEARBY_DEVICES)
                        Primary("Continue") { askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                        Secondary("Back") { step = Step.HAS_BLUETOOTH }
                    } else {
                        val cars = remember(btGranted) { bondedCars(ctx) }
                        if (cars.isEmpty()) {
                            Body("No paired devices found. Pair your car in Android's Bluetooth settings, then come back.")
                        }
                        cars.forEach { car ->
                            OutlinedButton(onClick = {
                                btName = car.name
                                btMac = car.mac
                                step = Step.DETAILS
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(car.name, style = MaterialTheme.typography.titleMedium)
                                    Text(car.mac, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Secondary("Back") { step = Step.HAS_BLUETOOTH }
                    }
                }

                Step.NAME_CAR -> {
                    var name by rememberSaveable { mutableStateOf("") }
                    Title("Name this car")
                    Body("Car mode opens from the launcher in a car without Bluetooth.")
                    OutlinedTextField(name, { name = it }, label = { Text("Name, like \"F-350\"") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Primary("Next", enabled = name.isNotBlank()) {
                        btName = name.trim()
                        btMac = null
                        step = Step.DETAILS
                    }
                    Secondary("Back") { step = Step.HAS_BLUETOOTH }
                }

                Step.DETAILS -> {
                    val guess = remember(btName) { if (btMac != null) MakeGuesser.guess(btName) else "" }
                    var make by rememberSaveable { mutableStateOf(guess) }
                    var model by rememberSaveable { mutableStateOf("") }
                    var yearText by rememberSaveable { mutableStateOf("") }
                    var oldCarAcknowledged by rememberSaveable { mutableStateOf(false) }
                    val year = yearText.toIntOrNull() ?: 0
                    val notSupported = SupportPolicy.status(year, Locale.getDefault().country) == SupportStatus.NOT_SUPPORTED

                    fun save(confirmed: Boolean) = scope.launch {
                        val profile = graph.vehicles.add(
                            VehicleProfile(
                                btMac = btMac, btName = btName,
                                make = make.trim(), model = model.trim(), year = year, makeConfirmed = confirmed,
                            ),
                        )
                        savedId = profile.id
                        if (btMac == null) {
                            graph.vehicles.recordResult(
                                profile.id, ProbeKeys.AUTO_LAUNCH,
                                CheckResult(CheckStatus.FAIL, "This car has no Bluetooth, so car mode opens from the launcher.", System.currentTimeMillis()),
                            )
                            finish()
                        } else {
                            step = Step.LINK
                        }
                    }

                    Title(if (guess.isNotBlank()) "Is this a $guess?" else "About this car")
                    OutlinedTextField(make, { make = it }, label = { Text("Make") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        yearText, { yearText = it.filter(Char::isDigit).take(4); oldCarAcknowledged = false },
                        label = { Text("Year") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (notSupported && !oldCarAcknowledged) {
                        Body("This vehicle isn't supported. AnyCar works with cars from 1996 and newer.")
                        Primary("Continue anyway") { oldCarAcknowledged = true }
                    } else {
                        Primary("Save", enabled = make.isNotBlank()) { save(confirmed = true) }
                        Secondary("Skip for now") { save(confirmed = false) }
                    }
                }

                Step.LINK -> {
                    Title("Link your car")
                    Body(PermissionCopy.LINK_CAR)
                    Body("Do this while your phone is connected to the car, with Location turned on.")
                    if (linkMessage.isNotBlank()) Body(linkMessage)
                    Primary("Link car") {
                        val mac = btMac ?: return@Primary
                        CompanionLink.associate(
                            activity = ctx.findActivity(), mac = mac, launcher = linkLauncher,
                            onLinked = { id -> scope.launch { graph.vehicles.setCdmAssociationId(savedId, id) }; step = Step.OVERLAY },
                            onFailure = { error ->
                                linkMessage = "Couldn't link: $error. Car mode will use a notification instead."
                                scope.launch {
                                    graph.vehicles.recordResult(
                                        savedId, ProbeKeys.AUTO_LAUNCH,
                                        CheckResult(CheckStatus.WARN, "Needs linking: tap Link car while connected to the car.", System.currentTimeMillis()),
                                    )
                                }
                            },
                        )
                    }
                    Secondary("Skip") { step = Step.OVERLAY }
                }

                Step.OVERLAY -> {
                    Title("Open car mode automatically?")
                    Body("To open car mode by itself when you start the car, Android needs \"Display over other apps\". Without it, you'll get a notification to tap instead.")
                    Body(PermissionCopy.OVERLAY)
                    Primary("Allow") {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        finish()
                    }
                    Secondary("Use a notification") { finish() }
                }
            }
        }
    }
}

@Composable private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium)

@Composable private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

@Composable
private fun Primary(label: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }

@Composable
private fun Secondary(label: String, onClick: () -> Unit) =
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }

private fun granted(ctx: Context, permission: String) =
    ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

/** Bonded devices, minus headphones and headsets (§9.1 step 3). */
@SuppressLint("MissingPermission")  // callers check BLUETOOTH_CONNECT on 31+
private fun bondedCars(ctx: Context): List<BondedCar> {
    val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching {
        adapter.bondedDevices.orEmpty()
            .filterNot { d ->
                val c = d.bluetoothClass ?: return@filterNot false
                c.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO && c.deviceClass in setOf(
                    BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES, BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                )
            }
            .map { BondedCar(it.name ?: it.address, it.address.uppercase()) }
            .sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())
}

private fun Context.findActivity(): ComponentActivity {
    var c = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    error("No activity")
}
