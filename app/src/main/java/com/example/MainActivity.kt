package com.example

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.OverlayService
import com.example.ui.CompanionDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CompanionViewModel
import com.example.viewmodel.CompanionViewModelFactory

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted! Enjoy Rango HUD Cockpit.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultData = result.data
        if (result.resultCode == RESULT_OK && resultData != null) {
            // Save parameters into process-level global statics to solve OS-dependent intent serialization bottlenecks
            OverlayService.savedProjectionResultCode = result.resultCode
            OverlayService.savedProjectionIntent = resultData

            // Fast, direct same-process active instance connection to trigger OCR scanning immediately
            val activeService = OverlayService.activeInstance
            if (activeService != null) {
                activeService.startOcrFromActivity(result.resultCode, resultData)
            } else {
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START_PROJECTION
                    putExtra(OverlayService.EXTRA_PROJECTION_RESULT_CODE, result.resultCode)
                    putExtra(OverlayService.EXTRA_PROJECTION_INTENT_DATA, resultData)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        } else {
            Toast.makeText(this, "Screen capture authorization declined. OCR scanner offline.", Toast.LENGTH_SHORT).show()
        }
        // Send our activity to the background instantly so we return straight to the game/underlying screen
        moveTaskToBack(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val companionViewModel: CompanionViewModel = viewModel(
                    factory = CompanionViewModelFactory(application)
                )
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CompanionDashboard(
                        viewModel = companionViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        checkAndTriggerOcrConsent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAndTriggerOcrConsent(intent)
    }

    private fun checkAndTriggerOcrConsent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra("EXTRA_START_OCR_IMMEDIATELY", false)) {
            // Clear the extra so it doesn't trigger again on subsequent activity lifecycle resume loops
            intent.removeExtra("EXTRA_START_OCR_IMMEDIATELY")
            startOcrScreenCapture()
        }
    }

    fun startFloatingCockpit(game: String = "RANGO", mode: String = "HORIZONTAL") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra("EXTRA_GAME", game)
                putExtra("EXTRA_MODE", mode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            val displayName = if (game == "DRAGON_TIGER") "Dragon Tiger" else if (game == "AVIATOR") "Aviator" else "Rango"
            Toast.makeText(this, "$displayName Floating HUD Activated!", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopFloatingCockpit() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "Rango Floating Bubble Cleared.", Toast.LENGTH_SHORT).show()
    }

    fun startOcrScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(intent)
    }

    fun updateHudMode(mode: String, game: String) {
        OverlayService.activeInstance?.updateHudModeAndGame(mode, game)
    }
}
