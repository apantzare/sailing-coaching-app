package se.pantzare.startstop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

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

private sealed interface UiState {
    data object Idle : UiState
    data object Acquiring : UiState
    data class Tracking(val start: Location) : UiState
    data class Done(val distanceMeters: Double, val bearingDeg: Double) : UiState
    data class Error(val message: String) : UiState
}

@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState>(UiState.Idle) }
    var hasPermission by remember { mutableStateOf(hasFineLocation(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!hasPermission) {
                Text(
                    text = "Location permission required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Button(onClick = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) { Text("Grant permission") }
                TextButton(onClick = { openAppSettings(context) }) {
                    Text("Open settings")
                }
                return@Column
            }

            when (val s = state) {
                is UiState.Idle -> {
                    Button(onClick = {
                        state = UiState.Acquiring
                        scope.launch {
                            val loc = getOneShotLocation(context)
                            state = if (loc == null) {
                                UiState.Error("Could not get location — is location enabled?")
                            } else {
                                UiState.Tracking(loc)
                            }
                        }
                    }) { Text("Start", fontSize = 22.sp) }
                }

                is UiState.Acquiring -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text("Acquiring GPS fix…")
                }

                is UiState.Tracking -> {
                    Text("Tracking started", fontWeight = FontWeight.SemiBold)
                    Button(onClick = {
                        val start = s.start
                        state = UiState.Acquiring
                        scope.launch {
                            val stop = getOneShotLocation(context)
                            state = if (stop == null) {
                                UiState.Error("Could not get location — is location enabled?")
                            } else {
                                val results = FloatArray(2)
                                Location.distanceBetween(
                                    start.latitude, start.longitude,
                                    stop.latitude, stop.longitude,
                                    results
                                )
                                val bearing = ((results[1] % 360) + 360) % 360
                                UiState.Done(results[0].toDouble(), bearing.toDouble())
                            }
                        }
                    }) { Text("Stop", fontSize = 22.sp) }
                }

                is UiState.Done -> {
                    Text(
                        text = "${s.distanceMeters.roundToInt()} m",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${cardinal(s.bearingDeg)} ${s.bearingDeg.roundToInt()}°",
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { state = UiState.Idle }) {
                        Text("Reset", fontSize = 20.sp)
                    }
                }

                is UiState.Error -> {
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = { state = UiState.Idle }) { Text("OK") }
                }
            }
        }
    }
}

private fun cardinal(bearingDeg: Double): String {
    val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = ((bearingDeg + 22.5) / 45).toInt().mod(8)
    return labels[idx]
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

@SuppressLint("MissingPermission")
private suspend fun getOneShotLocation(context: Context): Location? {
    if (!hasFineLocation(context)) return null
    val client = LocationServices.getFusedLocationProviderClient(context)
    return suspendCancellableCoroutine { cont ->
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
}
