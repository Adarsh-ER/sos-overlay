package com.sos.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme { Screen() } }
    }
}

private fun hasLocation(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun Screen() {
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var location by remember { mutableStateOf(hasLocation(ctx)) }
    var running by remember { mutableStateOf(SosService.running) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        location = hasLocation(ctx)
    }

    // Refresh state when the user returns from system settings.
    LaunchedEffect(Unit) {
        overlay = Settings.canDrawOverlays(ctx)
        location = hasLocation(ctx)
        running = SosService.running
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🆘 SOS Overlay", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "A floating red SOS button that stays on top of Google Maps (or any other app). " +
                "Tap it to capture your location and open or share it in Google Maps.",
            fontSize = 14.sp
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("Overlay", overlay)
            StatusChip("Location", location)
            StatusChip("Active", running)
        }

        if (!overlay) {
            Button(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("1 · Allow floating overlay") }
            Text("Choose SOS Overlay and turn on \"Allow display over other apps\".", fontSize = 12.sp, color = Color.Gray)
        }

        if (!location) {
            Button(
                onClick = {
                    val list = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    perms.launch(list.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("2 · Allow location") }
        }

        Button(
            enabled = overlay && location && !running,
            onClick = {
                ContextCompat.startForegroundService(ctx, Intent(ctx, SosService::class.java))
                running = true
                Toast.makeText(ctx, "Floating SOS button is on", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("3 · Turn on floating SOS") }

        OutlinedButton(
            enabled = running,
            onClick = {
                ctx.stopService(Intent(ctx, SosService::class.java))
                running = false
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Turn off") }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        Text("How to use", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "1. Turn the floating SOS on.\n" +
                "2. Open Google Maps and start navigating.\n" +
                "3. The red SOS button floats above the map — tap it at any time.\n" +
                "4. Choose \"Open in Google Maps\" to show your exact position, or \"Share location\" to send it to someone.",
            fontSize = 13.sp
        )
        Text(
            "The button stays above the map even while you use navigation or other apps.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    Surface(
        color = if (ok) Color(0xFF16A34A) else Color(0xFF6B7280),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            if (ok) "✓ $label" else "✗ $label",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
