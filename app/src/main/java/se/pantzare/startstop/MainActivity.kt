package se.pantzare.startstop

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private enum class Tab(val title: String, val icon: ImageVector) {
    Measure("Measure", Icons.Filled.LocationOn),
    History("History", Icons.AutoMirrored.Filled.List),
    Course("Course", Icons.Filled.Place),
    Wind("Wind", Icons.Filled.Refresh),
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

    val repository = remember { Repository.get(context) }
    val location = rememberLocationStream()
    var tab by remember { mutableStateOf(Tab.Measure) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GpsHeader(location.value)
            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.Measure -> MeasureScreen(repository, location)
                    Tab.History -> HistoryScreen(repository)
                    Tab.Course -> CourseScreen(repository, location)
                    Tab.Wind -> WindScreen(repository)
                }
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

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
