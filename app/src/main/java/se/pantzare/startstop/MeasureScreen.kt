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

sealed interface MeasureUiState {
    data object Idle : MeasureUiState
    data class Tracking(val start: Location, val startWallMs: Long) : MeasureUiState
    data class Done(val savedId: String) : MeasureUiState
}

@Composable
fun MeasureScreen(
    repository: Repository,
    location: State<Location?>,
    state: MeasureUiState,
    onStateChange: (MeasureUiState) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (val s = state) {
            is MeasureUiState.Idle -> IdleView(
                fixAvailable = location.value != null,
                onStart = {
                    val current = location.value ?: return@IdleView
                    onStateChange(MeasureUiState.Tracking(current, System.currentTimeMillis()))
                },
            )

            is MeasureUiState.Tracking -> TrackingView(
                startWallMs = s.startWallMs,
                onStop = {
                    val stop = location.value ?: return@TrackingView
                    val saved = computeResult(s.start, s.startWallMs, stop, System.currentTimeMillis())
                    repository.addMeasurement(saved)
                    onStateChange(MeasureUiState.Done(savedId = saved.id))
                },
            )

            is MeasureUiState.Done -> {
                val saved = repository.data.measurements.firstOrNull { it.id == s.savedId }
                if (saved == null) {
                    LaunchedEffect(s.savedId) { onStateChange(MeasureUiState.Idle) }
                } else {
                    DoneView(
                        saved = saved,
                        repository = repository,
                        onCleared = { onStateChange(MeasureUiState.Idle) },
                    )
                }
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
    saved: SavedMeasurement,
    repository: Repository,
    onCleared: () -> Unit,
) {
    var showFreeTextDialog by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }

    val customLabels = repository.data.customLabels
    val currentLabel = saved.positionLabel

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Saved", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Direction", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "${cardinal(saved.bearingDeg)}  ${saved.bearingDeg.roundToInt()}° ± ${saved.bearingSigmaDeg.roundToInt()}°",
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
                "${"%.1f".format(saved.mPerMin)} ± ${"%.1f".format(saved.mPerMinSigma)} m/min",
                fontSize = 22.sp,
            )
            Text(
                "${"%.2f".format(saved.knots)} ± ${"%.2f".format(saved.knotsSigma)} knots",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text("elapsed ${formatElapsed(saved.elapsedS)}")

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (currentLabel.isEmpty()) "Pick a label (or set later from History)"
            else "Label: $currentLabel",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DEFAULT_LABELS.forEach { label ->
                FilterChip(
                    selected = currentLabel == label,
                    onClick = { repository.updateMeasurementLabel(saved.id, label) },
                    label = { Text(label) },
                )
            }
            customLabels.forEach { label ->
                FilterChip(
                    selected = currentLabel == label,
                    onClick = { repository.updateMeasurementLabel(saved.id, label) },
                    label = { Text(label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showFreeTextDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("Free text")
            }
            OutlinedButton(
                onClick = { showAddLabelDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("+ New label")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCleared,
                modifier = Modifier.weight(1f),
            ) {
                Text("Done")
            }
            OutlinedButton(
                onClick = {
                    repository.deleteMeasurement(saved.id)
                    onCleared()
                },
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
            initialValue = "",
            onDismiss = { showFreeTextDialog = false },
            onConfirm = { value ->
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    repository.updateMeasurementLabel(saved.id, trimmed)
                }
                showFreeTextDialog = false
            },
        )
    }

    if (showAddLabelDialog) {
        TextEntryDialog(
            title = "Add new label",
            description = "Will be saved for next time.",
            initialValue = "",
            onDismiss = { showAddLabelDialog = false },
            onConfirm = { value ->
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    repository.addCustomLabel(trimmed)
                    repository.updateMeasurementLabel(saved.id, trimmed)
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
): SavedMeasurement {
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

    return SavedMeasurement(
        timestampMs = stopWallMs,
        positionLabel = "",
        startLat = start.latitude,
        startLng = start.longitude,
        startAccuracyM = start.accuracy.toDouble(),
        stopLat = stop.latitude,
        stopLng = stop.longitude,
        stopAccuracyM = stop.accuracy.toDouble(),
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

