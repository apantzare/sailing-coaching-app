package se.pantzare.startstop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

private data class Measurement(
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

private sealed interface UiState {
    data object Idle : UiState
    data class Tracking(val start: Location, val startWallMs: Long) : UiState
    data class Done(val measurement: Measurement) : UiState
}

@Composable
private fun App() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasFineLocation(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    if (!hasPermission) {
        PermissionGate(
            onGrant = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            onSettings = { openAppSettings(context) },
        )
        return
    }

    val location = rememberLocationStream()
    var state by remember { mutableStateOf<UiState>(UiState.Idle) }

    Column(modifier = Modifier.fillMaxSize()) {
        GpsHeader(location.value)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is UiState.Idle -> IdleScreen(
                    fixAvailable = location.value != null,
                    onStart = {
                        val current = location.value ?: return@IdleScreen
                        state = UiState.Tracking(current, System.currentTimeMillis())
                    },
                )

                is UiState.Tracking -> TrackingScreen(
                    startWallMs = s.startWallMs,
                    onStop = {
                        val stop = location.value ?: return@TrackingScreen
                        state = UiState.Done(
                            computeMeasurement(s.start, s.startWallMs, stop, System.currentTimeMillis())
                        )
                    },
                )

                is UiState.Done -> DoneScreen(
                    measurement = s.measurement,
                    onReset = { state = UiState.Idle },
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Location permission required", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Grant permission") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSettings) { Text("Open settings") }
    }
}

@Composable
private fun GpsHeader(location: Location?) {
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

@Composable
private fun IdleScreen(fixAvailable: Boolean, onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
private fun TrackingScreen(startWallMs: Long, onStop: () -> Unit) {
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
    ) {
        Text("Tracking…", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("elapsed ${formatElapsed(elapsedS)}", fontSize = 16.sp)
        Button(onClick = onStop) { Text("Stop", fontSize = 22.sp) }
    }
}

@Composable
private fun DoneScreen(measurement: Measurement, onReset: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Direction", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "${cardinal(measurement.bearingDeg)}  ${measurement.bearingDeg.roundToInt()}° ± ${measurement.bearingSigmaDeg.roundToInt()}°",
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
                "${"%.1f".format(measurement.mPerMin)} ± ${"%.1f".format(measurement.mPerMinSigma)} m/min",
                fontSize = 22.sp,
            )
            Text(
                "${"%.2f".format(measurement.knots)} ± ${"%.2f".format(measurement.knotsSigma)} knots",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text("elapsed ${formatElapsed(measurement.elapsedS)}")
        Button(onClick = onReset) { Text("Reset", fontSize = 20.sp) }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun rememberLocationStream(): State<Location?> {
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

private fun computeMeasurement(
    start: Location,
    startWallMs: Long,
    stop: Location,
    stopWallMs: Long,
): Measurement {
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

    return Measurement(
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

private fun cardinal(bearingDeg: Double): String {
    val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = ((bearingDeg + 22.5) / 45).toInt().mod(8)
    return labels[idx]
}

private fun formatElapsed(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "${m}m ${s}s"
}

private fun hasFineLocation(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
