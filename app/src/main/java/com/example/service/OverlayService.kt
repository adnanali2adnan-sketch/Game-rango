package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.CrashRound
import com.example.data.CrashRepository
import com.example.ui.theme.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.DecimalFormat

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "RangoOverlayChannel"
        const val NOTIFICATION_ID = 8871
        
        const val EXTRA_PROJECTION_RESULT_CODE = "PROJECTION_RESULT_CODE"
        const val EXTRA_PROJECTION_INTENT_DATA = "PROJECTION_INTENT_DATA"
        
        const val ACTION_START_PROJECTION = "START_PROJECTION"
        const val ACTION_STOP_OVERLAY = "STOP_OVERLAY"

        // Global state for sharing with Activity if needed
        val isServiceRunning = MutableStateFlow(false)
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    
    // DB Repository
    private lateinit var repository: CrashRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Floating View States
    private var isExpanded = MutableStateFlow(false)
    private var walletBalanceInput = MutableStateFlow("280.89")
    private var latestScannedMultiplier = MutableStateFlow("1.00")
    private var recentMultipliersList = MutableStateFlow<List<Double>>(emptyList())
    private var captureLogs = MutableStateFlow("OCR Scanner offline. Press START.")
    private var isOcrScanning = MutableStateFlow(false)

    // Dynamic Live Gemini HUD advice
    private var geminiAiAdvice = MutableStateFlow("")
    private var isGeminiLoading = MutableStateFlow(false)
    private var geminiApiKey = MutableStateFlow("")

    // Media Projection & OCR components
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var ocrJob: Job? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var lastRecordedValue = 0.0
    private var isServiceDestroying = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val database = AppDatabase.getDatabase(this)
        repository = CrashRepository(database.crashDao())
        
        // Listen to Room updates to keep our recent items list in sync!
        serviceScope.launch {
            repository.allRounds.collect { list ->
                recentMultipliersList.value = list.map { it.multiplier }.take(10)
                if (list.isNotEmpty() && lastRecordedValue == 0.0) {
                    lastRecordedValue = list.first().multiplier
                    latestScannedMultiplier.value = String.format("%.2f", lastRecordedValue)
                }
            }
        }

        // Securely pre-load Gemini API profile Key if set by the user
        geminiApiKey.value = com.example.util.SecurePrefs.getGeminiApiKey(this)
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        
        setupFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_START_PROJECTION -> {
                    val resultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, -1)
                    val data: Intent? = intent.getParcelableExtra(EXTRA_PROJECTION_INTENT_DATA)
                    if (resultCode != -1 && data != null) {
                        startMediaProjection(resultCode, data)
                    }
                }
                ACTION_STOP_OVERLAY -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceDestroying = true
        super.onDestroy()
        isServiceRunning.value = false
        stopOcrScanning()
        serviceScope.cancel()
        
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Rango Cockpit Live Scanner"
            val descriptionText = "Handles floating live cockpit HUD overlay and screen OCR multiplier scanning"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Rango Cockpit HUD Active")
            .setContentText("The floating strategic overlay card is currently analyzing game patterns.")
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun setupFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val localLifecycleOwner = ServiceLifecycleOwner().apply {
            performRestore(null)
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        lifecycleOwner = localLifecycleOwner

        overlayView = object : FrameLayout(this) {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private val touchSlop = 15 // px

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val lp = layoutParams as? WindowManager.LayoutParams
                        if (lp != null) {
                            initialX = lp.x
                            initialY = lp.y
                            initialTouchX = ev.rawX
                            initialTouchY = ev.rawY
                            isDragging = false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - initialTouchX
                        val dy = ev.rawY - initialTouchY
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            isDragging = true
                            return true // Intercept! Cancel goes to ComposeView.
                        }
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (isDragging) {
                    when (event.action) {
                        MotionEvent.ACTION_MOVE -> {
                            val lp = layoutParams as? WindowManager.LayoutParams
                            if (lp != null) {
                                val dx = event.rawX - initialTouchX
                                val dy = event.rawY - initialTouchY
                                lp.x = initialX + dx.toInt()
                                lp.y = initialY + dy.toInt()
                                try {
                                    windowManager.updateViewLayout(this, lp)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isDragging = false
                        }
                    }
                    return true
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(localLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(localLifecycleOwner)
            setViewTreeViewModelStoreOwner(localLifecycleOwner)
        }
        
        // Setup Compose View inside frame
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(localLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(localLifecycleOwner)
            setViewTreeViewModelStoreOwner(localLifecycleOwner)
            
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = RangoLimeGreen,
                        secondary = RangoDesertGold,
                        background = RangoHorizon,
                        surface = RangoCardBg
                    )
                ) {
                    FloatingOverlayCockpit()
                }
            }
        }
        
        overlayView?.addView(composeView)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 200
        }

        // Allow click focus when keyboard text inputs are tapped in overlay!
        composeView.setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                // Dummy listener to trace overlay hierarchies
            }
            override fun onChildViewRemoved(parent: View?, child: View?) {
                // Dummy listener
            }
        })

        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWindowFocus(focusable: Boolean) {
        val root = overlayView ?: return
        val lp = root.layoutParams as WindowManager.LayoutParams
        if (focusable) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            windowManager.updateViewLayout(root, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Compose Overlay View UI Cockpit
     */
    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun FloatingOverlayCockpit() {
        val expanded by isExpanded.collectAsState()
        val balance by walletBalanceInput.collectAsState()
        val latestScan by latestScannedMultiplier.collectAsState()
        val historyList by recentMultipliersList.collectAsState()
        val logInfo by captureLogs.collectAsState()
        val isScanning by isOcrScanning.collectAsState()

        // Local Calculations
        val doubleBalance = balance.toDoubleOrNull() ?: 280.89
        val baseBet = (doubleBalance * 0.01).coerceAtLeast(1.0)
        
        // Simple local streak algorithm matching viewmodel
        val last4 = historyList.take(4)
        val isColdStreak = last4.size >= 4 && last4.all { it < 1.20 }
        val last10 = historyList.take(10)
        val isHotStreak = last10.count { it >= 3.0 } >= 3

        val riskLevel: String
        val riskColor: Color
        val targetCash: Double
        val nextBet: Double

        if (isHotStreak) {
            riskLevel = "HIGH RISK / SKIP!"
            riskColor = RangoDangerRed
            targetCash = 1.15
            nextBet = baseBet
        } else if (isColdStreak) {
            riskLevel = "MEDIUM (Rebound Overdue)"
            riskColor = RangoDesertGold
            targetCash = 1.30
            // Double up recovery bet
            nextBet = baseBet * 2.5
        } else {
            riskLevel = "LOW RISK (Steady)"
            riskColor = RangoLimeGreen
            targetCash = 1.50
            nextBet = baseBet
        }

        val df = DecimalFormat("#.##")

        Column(
            modifier = Modifier
                .width(if (expanded) 310.dp else 125.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RangoHorizon.copy(alpha = 0.94f))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded.value = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(riskColor, CircleShape)
                    )
                    Text(
                        if (expanded) "RANGO PILOT HUD" else "RANGO HUD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RangoTextWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                    contentDescription = "Resize Bubble",
                    tint = RangoLimeGreen,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (!expanded) {
                // COLLAPSED COMPACT VIEW (Displays risk status, latest multiplier log and clickable click focus option)
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { isExpanded.value = true },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Last: ${historyList.firstOrNull() ?: 1.00}x",
                        color = RangoTextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isColdStreak) "COLD" else if (isHotStreak) "HOT RISK" else "STEADY",
                        color = riskColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Tap to view",
                        color = RangoTextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // EXPANDED INTERACTIVE CONTROL PANEL
                HorizontalDivider(color = RangoTealSky.copy(alpha = 0.5f))

                // Wallet Balance Dynamic Money Manager Configuration
                Column {
                    Text(
                        "WALLET BALANCE ($):",
                        color = RangoTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(RangoTealSky)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "$ ${String.format("%.2f", doubleBalance)}",
                                color = RangoLimeGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                        
                        Spacer(Modifier.width(6.dp))
                        
                        Row(
                            modifier = Modifier.weight(2f),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(-50.0, -10.0, 10.0, 50.0).forEach { amount ->
                                val label = if (amount > 0) "+${amount.toInt()}" else "${amount.toInt()}"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (amount > 0) RangoLimeGreen.copy(alpha = 0.85f) else RangoDangerRed.copy(alpha = 0.85f))
                                        .clickable {
                                            val current = walletBalanceInput.value.toDoubleOrNull() ?: 280.89
                                            val adjusted = (current + amount).coerceAtLeast(0.0)
                                            walletBalanceInput.value = String.format("%.2f", adjusted)
                                        }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (amount > 0) Color.Black else RangoTextWhite,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic calculations card
                Card(
                    colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RISK SCORE", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(riskLevel, color = riskColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SUGGESTED BASE BET", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$${df.format(baseBet)}", color = RangoLimeGreen, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("NEXT STRATEGIC BET", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$${df.format(nextBet)}", color = RangoDesertGold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SAFE TARGET CASHOUT", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${df.format(targetCash)}x", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // OCR live screen capture system controllers
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "LIVE OCR SCANNER CONTROLS",
                        color = RangoLimeGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { 
                                if (isScanning) {
                                    stopOcrScanning()
                                } else {
                                    captureLogs.value = "Launching Dashboard for Screen Auth..."
                                    val launchIntent = Intent(this@OverlayService, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(launchIntent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) RangoDangerRed else RangoLimeGreen
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                        ) {
                            Text(
                                if (isScanning) "STOP SCAN" else "START AUTO OCR",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Scan Logs UI Box
                    Text(
                        text = logInfo,
                        color = RangoTextMuted,
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(4.dp)
                    )
                }

                // Dynamic Gemini Cockpit Live Performance Panel
                val liveAdvice by geminiAiAdvice.collectAsState()
                val liveLoading by isGeminiLoading.collectAsState()
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = RangoHorizon.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "⚡ LIVE GEMINI AI COCKPIT",
                            color = RangoDesertGold,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (liveLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(color = RangoLimeGreen, modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                Text("Dynamic processing...", color = RangoTextWhite, fontSize = 8.sp)
                            }
                        } else if (liveAdvice.isEmpty()) {
                            Text(
                                "Waiting for real OCR scans to analyze...",
                                color = RangoTextMuted,
                                fontSize = 8.sp
                            )
                        } else {
                            Text(
                                text = liveAdvice,
                                color = RangoTextWhite,
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // Ribbon of recent rounds in overlay
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("LOBBY RIBBON", color = RangoTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        historyList.take(5).forEach { mult ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            mult >= 3.0 -> Color(0xFF8C34FF)
                                            mult >= 1.5 -> RangoLimeGreen
                                            else -> RangoTealSky
                                        }
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "${mult}x",
                                    color = Color.White,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        val targetWidth = 480
        val targetHeight = 800
        try {
            // Dynamically upgrade FGS run-type to mediaProjection BEFORE requesting projection session to satisfy AndroidQ+ / Android14 requirements!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }

            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            
            // Register MediaProjection callback to comply with Android 14 requirements
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    stopOcrScanning()
                }
            }, Handler(Looper.getMainLooper()))
            
            isOcrScanning.value = true
            captureLogs.value = "Media Screen stream authorized! Scanning flight decimals..."
            
            // Setup Virtual Display mirroring to read pixels
            val density = 1
            
            imageReader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
            mediaProjection?.createVirtualDisplay(
                "RangoOcrMirror",
                targetWidth, targetHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            captureLogs.value = "Auth/Projection Error: ${e.localizedMessage}"
            isOcrScanning.value = false
            try {
                // Return FGS type to dataSync on failure
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return
        }

        // Launch real image-scanning job matching Rango flight numbers periodically
        ocrJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1500) // Scan screen pixels every 1.5 seconds under strict real-time requirement
                if (!isOcrScanning.value) {
                    break
                }
                var parentBitmap: Bitmap? = null
                var croppedBmp: Bitmap? = null
                try {
                    val img = imageReader?.acquireLatestImage()
                    if (img != null) {
                        try {
                            val planes = img.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride

                            if (pixelStride > 0) {
                                val rowPadding = rowStride - pixelStride * targetWidth
                                val widthToUse = targetWidth + rowPadding / pixelStride
                                if (widthToUse > 0) {
                                    val bitmap = Bitmap.createBitmap(
                                        widthToUse,
                                        targetHeight,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    bitmap.copyPixelsFromBuffer(buffer)
                                    parentBitmap = bitmap

                                    // Strictly crop to top coordinate Lobby Ribbon area (top 1% to 14% of the screen)
                                    val x = (targetWidth * 0.05).toInt()
                                    val y = (targetHeight * 0.02).toInt()
                                    val w = (targetWidth * 0.90).toInt().coerceAtMost(bitmap.width - x)
                                    val h = (targetHeight * 0.12).toInt().coerceAtMost(bitmap.height - y)

                                    if (w > 0 && h > 0) {
                                        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, w, h)
                                        croppedBmp = croppedBitmap
                                        val image = InputImage.fromBitmap(croppedBitmap, 0)
                                        
                                        recognizer.process(image)
                                            .addOnSuccessListener { visionText ->
                                                val text = visionText.text
                                                // Parse decimal multipliers followed by 'x' or standalone decimals (e.g., 1.07x, 2.14, 4.34x)
                                                val regex = Regex("""\b(\d+[\.,]\d{1,2})\s*x?\b""", RegexOption.IGNORE_CASE)
                                                val matches = regex.findAll(text).toList()
                                                if (matches.isNotEmpty()) {
                                                    // Take oldest text segment parsed - left to right matching represents the newest
                                                    val matchedStr = matches.first().groupValues[1].replace(',', '.')
                                                    val matchedVal = matchedStr.toDoubleOrNull()
                                                    if (matchedVal != null && matchedVal >= 1.0 && matchedVal < 1000.0) {
                                                        serviceScope.launch(Dispatchers.Main) {
                                                            latestScannedMultiplier.value = String.format("%.2f", matchedVal)
                                                            captureLogs.value = "Real OCR scan match: ${matchedVal}x"
                                                            checkAndCommitDetectedValue(matchedVal)
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                try {
                                                    bitmap.recycle()
                                                    croppedBitmap.recycle()
                                                } catch (ex: Exception) {
                                                    ex.printStackTrace()
                                                }
                                            }
                                    } else {
                                        try {
                                            bitmap.recycle()
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                        }
                                    }
                                }
                            }
                        } finally {
                            img.close()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        parentBitmap?.recycle()
                    } catch (ex: Exception) {
                        // ignore
                    }
                    try {
                        croppedBmp?.recycle()
                    } catch (ex: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    private fun checkAndCommitDetectedValue(value: Double) {
        if (value != lastRecordedValue) {
            lastRecordedValue = value
            serviceScope.launch {
                // Instantly update database
                val doubleBalance = walletBalanceInput.value.toDoubleOrNull() ?: 280.89
                val baseBet = (doubleBalance * 0.01).coerceAtLeast(1.0)
                val target = if (value < 1.20) 1.5 else 1.30
                addCapturedMultiplierToDatabase(value, baseBet, target)

                // Instantly trigger dynamic real-time Gemini pipeline!
                triggerRealtimeGeminiPipeline()
            }
        }
    }

    private fun triggerRealtimeGeminiPipeline() {
        serviceScope.launch {
            val key = geminiApiKey.value
            if (key.isBlank()) {
                geminiAiAdvice.value = "Please add Gemini API Key on Dashboard first."
                return@launch
            }
            
            isGeminiLoading.value = true
            val recentList = recentMultipliersList.value.take(10)
            if (recentList.isEmpty()) {
                isGeminiLoading.value = false
                return@launch
            }
            
            val multipliersStr = recentList.joinToString(", ") { "${it}x" }
            val currentBalance = walletBalanceInput.value
            
            val prompt = """
                Act as a lightning-fast, high-accuracy math analytics processor for a crash game. Your target is a low-end display system, so your response must be extremely concise, direct, and stripped of unnecessary prose.
                Analyze the sequence of incoming multipliers provided: $multipliersStr
                Output Format Requirements:
                - RISK LEVEL: [LOW / MEDIUM / HIGH] (Based on streak analysis)
                - NEXT STRATEGIC BET: [${'$'}X.XX / SKIP] (Based on Martingale parameters against current balance of ${'$'}$currentBalance)
                - SAFE CASHOUT: [X.XXx / PASS]
                - SHORT RATIONALE: [Provide a 1-sentence mathematical explanation of why this target was generated, e.g., "3 consecutive crashes under 1.2x suggest an imminent correction phase."]
                Do not include markdown intros, greetings, or long conversational filler.
            """.trimIndent()
            
            try {
                val adviceResult = com.example.api.GeminiClient.getStrategyAdvice(prompt, key)
                geminiAiAdvice.value = adviceResult
            } catch (e: Exception) {
                geminiAiAdvice.value = "Advice Fail: ${e.localizedMessage}"
            } finally {
                isGeminiLoading.value = false
            }
        }
    }

    private fun stopOcrScanning() {
        isOcrScanning.value = false
        captureLogs.value = "OCR Scanner offline. Press START."
        ocrJob?.cancel()
        ocrJob = null
        
        // Downgrade/restores FGS to lower level dataSync type when not capture-scanning (only if not destroying!)
        if (!isServiceDestroying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addCapturedMultiplierToDatabase(multiplier: Double, betSize: Double, cashOutVal: Double) {
        serviceScope.launch(Dispatchers.IO) {
            var profit = 0.0
            if (betSize > 0.0) {
                if (multiplier >= cashOutVal) {
                    profit = betSize * (cashOutVal - 1.0)
                } else {
                    profit = -betSize
                }
            }

            val round = CrashRound(
                multiplier = multiplier,
                betAmount = betSize,
                cashOutMultiplier = if (multiplier >= cashOutVal) cashOutVal else 0.0,
                profitLoss = profit
            )
            repository.insert(round)
        }
    }
}

/**
 * Standard utility class mapping system lifecycle events into Service scope WindowManager layouts.
 */
class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performAttach()
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }
}
