package se.pantzare.startstop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class TransientResult(
    val start: Location,
    val stop: Location,
    val startWallMs: Long,
    val stopWallMs: Long,
    val distanceM: Double,
    val distanceSigmaM: Double,
    val bearingDeg: Double,
    val bearingSigmaDeg: Double,
    val mPerMin: Double,
    val mPerMinSigma: Double,
    val knots: Double,
    val knotsSigma: Double,
    val elapsedS: Double,
)

private sealed interface MeasureUiState {
    data object Idle : MeasureUiState
    data class Tracking(val start: Location, val startWallMs: Long) : MeasureUiState
    data class Done(val result: TransientResult) : MeasureUiState
}

@Composable
fun MeasureScreen(repository: Repository) {
    val location = rememberLocationStream()
    var state by remember { mutableStateOf<MeasureUiState>(MeasureUiState.Idle) }

    Column(modifier = Modifier.fillMaxSize()) {
        GpsHeader(location.value)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                is MeasureUiState.Idle -> IdleView(
                    fixAvailable = location.value != null,
                    onStart = {
                        val current = location.value ?: return@IdleView
                        state = MeasureUiState.Tracking(current, System.currentTimeMillis())
                    },
                )

                is MeasureUiState.Tracking -> TrackingView(
                    startWallMs = s.startWallMs,
                    onStop = {
                        val stop = location.value ?: return@TrackingView
                        state = MeasureUiState.Done(
                            computeResult(s.start, s.startWallMs, stop, System.currentTimeMillis())
                        )
                    },
                )

                is MeasureUiState.Done -> DoneView(
                    result = s.result,
                    repository = repository,
                    onCleared = { state = MeasureUiState.Idle },
                )
            }
        }
    }
}

@Composable
private fun IdleView(fixAvailable: Boolean, onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 80.dp),
    ) {
        Button(onClick = onStart, enabled = fixAvailable) {
            Text("Start", fontSize = 22.sp)
        }
        if (!fixAvailable) {
            Text("Waiting for GPS fix…")
        }
    }
}

@Composable
private fun TrackingView(startWallMs: Long, onStop: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsedS = (nowMs - startWallMs) / 1000.0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 60.dp),
    ) {
        Text("Tracking…", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("elapsed ${formatElapsed(elapsedS)}", fontSize = 16.sp)
        Button(onClick = onStop) { Text("Stop", fontSize = 22.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneView(
    result: TransientResult,
    repository: Repository,
    onCleared: () -> Unit,
) {
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var showFreeTextDialog by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var freeTextValue by remember { mutableStateOf("") }

    val customLabels = repository.data.customLabels

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Direction", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "${cardinal(result.bearingDeg)}  ${result.bearingDeg.roundToInt()}° ± ${result.bearingSigmaDeg.roundToInt()}°",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Velocity", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "${"%.1f".format(result.mPerMin)} ± ${"%.1f".format(result.mPerMinSigma)} m/min",
                fontSize = 22.sp,
            )
            Text(
                "${"%.2f".format(result.knots)} ± ${"%.2f".format(result.knotsSigma)} knots",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text("elapsed ${formatElapsed(result.elapsedS)}")

        Spacer(Modifier.height(8.dp))
        Text("Label this measurement", fontSize = 16.sp, fontWeight = FontWeight.Medium)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DEFAULT_LABELS.forEach { label ->
                FilterChip(
                    selected = selectedLabel == label,
                    onClick = { selectedLabel = label },
                    label = { Text(label) },
                )
            }
            customLabels.forEach { label ->
                FilterChip(
                    selected = selectedLabel == label,
                    onClick = { selectedLabel = label },
                    label = { Text(label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    freeTextValue = ""
                    showFreeTextDialog = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Free text")
            }
            OutlinedButton(
                onClick = {
                    freeTextValue = ""
                    showAddLabelDialog = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("+ New label")
            }
        }

        selectedLabel?.takeIf { it !in DEFAULT_LABELS && it !in customLabels }?.let {
            Text("Selected: $it", fontWeight = FontWeight.Medium)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    val label = selectedLabel ?: return@Button
                    repository.addMeasurement(toSaved(result, label))
                    onCleared()
                },
                enabled = selectedLabel != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = onCleared,
                modifier = Modifier.weight(1f),
            ) {
                Text("Discard")
            }
        }
    }

    if (showFreeTextDialog) {
        TextEntryDialog(
            title = "Free text label",
            description = "Used for this measurement only.",
            initialValue = freeTextValue,
            onDismiss = { showFreeTextDialog = false },
            onConfirm = { value ->
                if (value.isNotBlank()) selectedLabel = value.trim()
                showFreeTextDialog = false
            },
        )
    }

    if (showAddLabelDialog) {
        TextEntryDialog(
            title = "Add new label",
            description = "Will be saved for next time.",
            initialValue = freeTextValue,
            onDismiss = { showAddLabelDialog = false },
            onConfirm = { value ->
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    repository.addCustomLabel(trimmed)
                    selectedLabel = trimmed
                }
                showAddLabelDialog = false
            },
        )
    }
}

@Composable
private fun TextEntryDialog(
    title: String,
    description: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, fontSize = 14.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun GpsHeader(location: Location?) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val ageS = if (location == null) Long.MAX_VALUE else (nowMs - location.time) / 1000
    val (color, label, text) = describeGpsQuality(location, ageS)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(label)
    }
}

private fun describeGpsQuality(location: Location?, ageS: Long): Triple<Color, String, String> {
    if (location == null) return Triple(Color(0xFFD32F2F), "Bad", "GPS: no fix")
    if (ageS > 5) return Triple(Color(0xFFD32F2F), "Bad", "GPS: stale (${ageS}s)")
    val acc = location.accuracy
    return when {
        acc <= 5f -> Triple(Color(0xFF2E7D32), "Excellent", "GPS ±${acc.roundToInt()} m")
        acc <= 10f -> Triple(Color(0xFF66BB6A), "Good", "GPS ±${acc.roundToInt()} m")
        acc <= 20f -> Triple(Color(0xFFFBC02D), "Fair", "GPS ±${acc.roundToInt()} m")
        acc <= 50f -> Triple(Color(0xFFEF6C00), "Poor", "GPS ±${acc.roundToInt()} m")
        else -> Triple(Color(0xFFD32F2F), "Bad", "GPS ±${acc.roundToInt()} m")
    }
}

@SuppressLint("MissingPermission")
@Composable
fun rememberLocationStream(): State<Location?> {
    val context = LocalContext.current
    val location = remember { mutableStateOf<Location?>(null) }
    DisposableEffect(Unit) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location.value = it }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        if (hasFineLocation(context)) {
            try {
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (_: SecurityException) {
                // Permission revoked between check and call; the stream stays null.
            }
        }
        onDispose { client.removeLocationUpdates(callback) }
    }
    return location
}

fun hasFineLocation(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun computeResult(
    start: Location,
    startWallMs: Long,
    stop: Location,
    stopWallMs: Long,
): TransientResult {
    val results = FloatArray(2)
    Location.distanceBetween(start.latitude, start.longitude, stop.latitude, stop.longitude, results)
    val distance = results[0].toDouble()
    val bearing = ((results[1] + 360f) % 360f).toDouble()

    val acc = sqrt(
        start.accuracy.toDouble() * start.accuracy + stop.accuracy.toDouble() * stop.accuracy
    )

    val bearingSigma = if (distance > 0.0) {
        Math.toDegrees(acc / distance).coerceAtMost(180.0)
    } else 180.0

    val gpsElapsedMs = stop.time - start.time
    val elapsedS = if (gpsElapsedMs > 0) gpsElapsedMs / 1000.0
    else ((stopWallMs - startWallMs) / 1000.0).coerceAtLeast(0.001)

    val mPerMin = distance / (elapsedS / 60.0)
    val knots = (distance / elapsedS) * 1.94384
    val mPerMinSig = acc / (elapsedS / 60.0)
    val knotsSig = (acc / elapsedS) * 1.94384

    return TransientResult(
        start = start,
        stop = stop,
        startWallMs = startWallMs,
        stopWallMs = stopWallMs,
        distanceM = distance,
        distanceSigmaM = acc,
        bearingDeg = bearing,
        bearingSigmaDeg = bearingSigma,
        mPerMin = mPerMin,
        mPerMinSigma = mPerMinSig,
        knots = knots,
        knotsSigma = knotsSig,
        elapsedS = elapsedS,
    )
}

private fun toSaved(result: TransientResult, label: String): SavedMeasurement {
    return SavedMeasurement(
        timestampMs = result.stopWallMs,
        positionLabel = label,
        startLat = result.start.latitude,
        startLng = result.start.longitude,
        startAccuracyM = result.start.accuracy.toDouble(),
        stopLat = result.stop.latitude,
        stopLng = result.stop.longitude,
        stopAccuracyM = result.stop.accuracy.toDouble(),
        distanceM = result.distanceM,
        distanceSigmaM = result.distanceSigmaM,
        bearingDeg = result.bearingDeg,
        bearingSigmaDeg = result.bearingSigmaDeg,
        mPerMin = result.mPerMin,
        mPerMinSigma = result.mPerMinSigma,
        knots = result.knots,
        knotsSigma = result.knotsSigma,
        elapsedS = result.elapsedS,
    )
}
