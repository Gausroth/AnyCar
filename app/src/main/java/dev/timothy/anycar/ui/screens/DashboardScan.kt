package dev.timothy.anycar.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.timothy.anycar.ai.DashLight
import dev.timothy.anycar.ai.DashReport
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data class Working(val photo: Bitmap) : ScanState
    data class Done(val photo: Bitmap, val report: DashReport) : ScanState
    data class Failed(val message: String) : ScanState
}

/** A fresh cache file the camera app writes the dashboard photo into. */
fun scanPhotoUri(ctx: Context): Uri {
    val file = File(ctx.cacheDir, "scan/dash.jpg").apply { parentFile?.mkdirs(); delete() }
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
}

/** Loads the photo upright and at most 1280 px, then deletes it. Returns the bitmap and the JPEG to send. */
suspend fun loadScanPhoto(ctx: Context): Pair<Bitmap, ByteArray>? = withContext(Dispatchers.IO) {
    val file = File(ctx.cacheDir, "scan/dash.jpg")
    if (!file.exists() || file.length() == 0L) return@withContext null
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
        val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        var photo = if (degrees == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
        val scale = 1280f / maxOf(photo.width, photo.height)
        if (scale < 1f) photo = Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true)
        val jpeg = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        photo to jpeg
    } finally {
        file.delete()
    }
}

private fun urgencyColor(urgency: String) = when (urgency) {
    "stop_now" -> Color(0xFFE53935)
    "soon" -> Color(0xFFFFB300)
    else -> Color(0xFF4FC3F7)
}

private fun urgencyLabel(urgency: String) = when (urgency) {
    "stop_now" -> "Stop safely now"
    "soon" -> "Get it checked soon"
    else -> "For your information"
}

/** The scan result: the photo with each light boxed by urgency, then the lights, most urgent first. */
@Composable
fun DashboardScanPanel(state: ScanState, onScanAgain: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Warning lights", fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        when (state) {
            is ScanState.Working -> {
                Photo(state.photo, emptyList())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(36.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Reading the dashboard…", fontSize = 26.sp)
                }
            }
            is ScanState.Done -> {
                val lights = state.report.lights.sortedBy { listOf("stop_now", "soon", "info").indexOf(it.urgency).let { i -> if (i < 0) 3 else i } }
                Photo(state.photo, lights)
                if (state.report.spoken.isNotBlank()) Text(state.report.spoken, fontSize = 24.sp, lineHeight = 30.sp)
                lights.forEach { light ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 8.dp).size(20.dp).background(urgencyColor(light.urgency), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(light.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(urgencyLabel(light.urgency), fontSize = 22.sp, color = urgencyColor(light.urgency))
                            if (light.meaning.isNotBlank()) Text(light.meaning, fontSize = 22.sp)
                        }
                    }
                }
            }
            is ScanState.Failed -> Text(state.message, fontSize = 26.sp)
        }
        if (state !is ScanState.Working) {
            Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(20.dp)) {
                Text("Scan again", fontSize = 26.sp)
            }
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(20.dp)) {
            Text("Done", fontSize = 26.sp)
        }
    }
}

@Composable
private fun Photo(photo: Bitmap, lights: List<DashLight>) {
    Box(Modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height)) {
        Image(photo.asImageBitmap(), contentDescription = "Dashboard photo", contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
            lights.filter { it.box.size == 4 }.forEach { light ->
                val (x0, y0, x1, y1) = light.box.map { it.coerceIn(0, 1000) / 1000f }
                drawRect(
                    color = urgencyColor(light.urgency),
                    topLeft = Offset(x0 * size.width, y0 * size.height),
                    size = Size((x1 - x0) * size.width, (y1 - y0) * size.height),
                    style = Stroke(width = 5.dp.toPx()),
                )
            }
        }
    }
}
