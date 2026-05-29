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
    private var isManualModeSelected = MutableStateFlow(true)
    private var manualMultiplierInput = MutableStateFlow("")

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

    data class LiveMetrics(
        val nextPrediction: Double,
        val cashout: Double,
        val minOut: Double,
        val betFactor: Double,
        val trend: String,
        val streak: String,
        val description: String
    )

    private fun calculateLiveMetrics(history: List<Double>): LiveMetrics {
        if (history.isEmpty()) {
            return LiveMetrics(
                nextPrediction = 1.85,
                cashout = 1.50,
                minOut = 1.30,
                betFactor = 1.0,
                trend = "→ STEADY",
                streak = "MIXED",
                description = "Koyee outcomes history me nahi hai. Multipliers enter karke ya OCR start karke automatic analysis shuru karain!"
            )
        }

        val size = history.size
        val lastItem = history.first()

        // 1. Streak Analysis
        var consecutiveCrashes = 0
        for (valItem in history) {
            if (valItem < 1.45) consecutiveCrashes++ else break
        }
        var consecutiveRisers = 0
        for (valItem in history) {
            if (valItem >= 2.00) consecutiveRisers++ else break
        }

        val streak = when {
            consecutiveCrashes >= 3 -> "COLD"
            consecutiveRisers >= 3 -> "HOT"
            else -> "MIXED"
        }

        // 2. Trend Analysis
        val sliceCount = (size / 2).coerceAtLeast(1)
        val firstHalfAvg = history.take(sliceCount).average()
        val secondHalfAvg = history.drop(sliceCount).take(sliceCount).let { if (it.isEmpty()) lastItem else it.average() }

        val trend = when {
            firstHalfAvg > secondHalfAvg * 1.05 -> "↑ RISING"
            firstHalfAvg < secondHalfAvg * 0.95 -> "↓ FALLING"
            else -> "→ STEADY"
        }

        // 3. Next Prediction
        var nextPrediction = 1.50
        if (streak == "COLD") {
            nextPrediction = 1.80 + (consecutiveCrashes * 0.40)
        } else if (streak == "HOT") {
            nextPrediction = 1.12 + (0.08 * consecutiveRisers).coerceAtMost(0.25)
        } else {
            val weights = history.take(5).mapIndexed { idx, value -> value * (5 - idx) }
            val sumWeights = (1..weights.size).sum()
            val weightedMean = if (sumWeights > 0) weights.sum() / sumWeights else 1.80
            nextPrediction = weightedMean.coerceIn(1.30, 4.20)
        }
        
        // Add subtle natural multiplier variations
        val salt = (history.sum() * 73 % 29) / 100.0
        nextPrediction = (nextPrediction + salt).coerceIn(1.05, 12.5)

        // 4. Safe Cashout
        val cashout = when (streak) {
            "COLD" -> (nextPrediction * 0.75).coerceIn(1.30, 3.20)
            "HOT" -> (nextPrediction * 0.88).coerceIn(1.10, 1.35)
            else -> (nextPrediction * 0.78).coerceIn(1.20, 2.20)
        }

        // 5. Min out
        val minOut = (cashout * 0.85).coerceAtLeast(1.05)

        // 6. Bet multiplier Factor
        var betFactor = 1.0
        if (consecutiveCrashes > 0) {
            betFactor = when (consecutiveCrashes) {
                1 -> 1.5
                2 -> 2.5
                3 -> 4.5
                else -> 8.0
            }
        }

        val roundedNextStr = String.format("%.2f", nextPrediction)
        val roundedCashoutStr = String.format("%.2f", cashout)
        val description = when {
            streak == "COLD" -> "Frequent early crashes detected (${consecutiveCrashes} consecutive <1.45x). A strong rebound spike is predicted. Ride momentum to ${roundedCashoutStr}x."
            streak == "HOT" -> "Strong over-performing streak detected. High multiplier saturation. Auto Cashout set strictly at a safe ${roundedCashoutStr}x."
            trend == "↑ RISING" -> "Upward trend detected. Avg rising from ${String.format("%.2f", secondHalfAvg)}x to ${String.format("%.2f", firstHalfAvg)}x. Ride momentum to ${roundedCashoutStr}x."
            trend == "↓ FALLING" -> "Downward trend detected. Avg falling from ${String.format("%.2f", secondHalfAvg)}x to ${String.format("%.2f", firstHalfAvg)}x. Cash out early."
            else -> "Market stabilization detected. Target safe low-risk Cashout of ${roundedCashoutStr}x for high win-rate."
        }

        return LiveMetrics(
            nextPrediction = nextPrediction,
            cashout = cashout,
            minOut = minOut,
            betFactor = betFactor,
            trend = trend,
            streak = streak,
            description = description
        )
    }

    @Composable
    private fun BoxMetricItem(
        title: String,
        value: String,
        bgColor: Color,
        valueColor: Color,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .padding(vertical = 2.dp, horizontal = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = RangoTextWhite.copy(alpha = 0.85f),
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
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
        val isManualMode by isManualModeSelected.collectAsState()

        // Local Calculations
        val doubleBalance = balance.toDoubleOrNull() ?: 280.89
        val baseBet = (doubleBalance * 0.01).coerceAtLeast(1.0)
        
        // Advanced dynamic telemetry calculations
        val metrics = remember(historyList) { calculateLiveMetrics(historyList) }
        val df = DecimalFormat("#.##")
        val riskColor = when (metrics.streak) {
            "HOT" -> RangoDangerRed
            "COLD" -> RangoLimeGreen
            else -> RangoDesertGold
        }

        Column(
            modifier = Modifier
                .width(if (expanded) 250.dp else 100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RangoHorizon.copy(alpha = 0.96f))
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded.value = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(riskColor, CircleShape)
                    )
                    Text(
                        text = if (expanded) "🟢 RANGO PILOT" else "RANGO HUD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RangoTextWhite,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Minimize/Maximize toggle icon
                    Icon(
                        imageVector = if (expanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = "Resize Bubble",
                        tint = RangoLimeGreen,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { isExpanded.value = !expanded }
                    )
                    
                    if (expanded) {
                        // Close Overlay completely icon
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop Service",
                            tint = RangoDangerRed,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable {
                                    val stopIntent = Intent(this@OverlayService, OverlayService::class.java).apply {
                                        action = ACTION_STOP_OVERLAY
                                    }
                                    startService(stopIntent)
                                }
                        )
                    }
                }
            }

            if (!expanded) {
                // COLLAPSED COMPACT VIEW
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
                        text = metrics.trend,
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

                // Heading trend status banner dynamically styled
                val headingText = when {
                    metrics.streak == "HOT" -> "🚀 HOT MOMENTUM"
                    metrics.streak == "COLD" -> "🚀 COLD REBOUND"
                    else -> "🚀 STEADY TREND"
                }
                val headingColor = when {
                    metrics.streak == "HOT" -> RangoDesertGold
                    metrics.streak == "COLD" -> RangoLimeGreen
                    else -> Color(0xFF00B0FF)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(vertical = 5.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = headingText,
                        color = headingColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Grid of 6 Performance and strategy prediction boxes
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BoxMetricItem(
                            title = "NEXT",
                            value = "${String.format("%.2f", metrics.nextPrediction)}x",
                            bgColor = Color(0xFF1B5E20),
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        BoxMetricItem(
                            title = "CASHOUT",
                            value = "${String.format("%.2f", metrics.cashout)}x",
                            bgColor = Color(0xFF004D40),
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        BoxMetricItem(
                            title = "MIN OUT",
                            value = "${String.format("%.2f", metrics.minOut)}x",
                            bgColor = Color(0xFFBF360C),
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BoxMetricItem(
                            title = "BET",
                            value = "${String.format("%.1f", metrics.betFactor)}x base",
                            bgColor = Color(0xFF33691E),
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        BoxMetricItem(
                            title = "TREND",
                            value = metrics.trend,
                            bgColor = Color(0xFFE65100),
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        BoxMetricItem(
                            title = "STREAK",
                            value = metrics.streak,
                            bgColor = Color(0xFF37474F),
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Real time analysis text summary
                Text(
                    text = metrics.description,
                    color = RangoTextWhite,
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )

                HorizontalDivider(color = RangoTealSky.copy(alpha = 0.5f))

                // Wallet Balance & Adjustment Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "BALANCE:",
                            color = RangoTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "PKR ${String.format("%.2f", doubleBalance)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(-50.0, -10.0, 10.0, 50.0).forEach { amount ->
                            val label = if (amount > 0) "+${amount.toInt()}" else "${amount.toInt()}"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(RangoHorizon)
                                    .clickable {
                                        val current = walletBalanceInput.value.toDoubleOrNull() ?: 280.89
                                        val adjusted = (current + amount).coerceAtLeast(0.0)
                                        walletBalanceInput.value = String.format("%.2f", adjusted)
                                    }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (amount > 0) RangoLimeGreen else RangoDangerRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = RangoTealSky.copy(alpha = 0.5f))

                // Risk & Last Multipliers Info Summary
                val riskName = when (metrics.streak) {
                    "HOT" -> "HIGH RISK | Bet: Skip/Low"
                    "COLD" -> "LOW RISK | Bet: PKR ${df.format(baseBet * metrics.betFactor)}"
                    else -> "MEDIUM RISK | Bet: PKR ${df.format(baseBet * metrics.betFactor)}"
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RISK:", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(riskName, color = riskColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("LAST:", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (historyList.isEmpty()) "No rounds captured" else historyList.take(5).joinToString("x  ") { df.format(it) } + "x",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // LIVE SCANNER tabbed section (Auto OCR and Manual)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "⚡ LIVE SCANNER",
                        color = RangoLimeGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    // Tabs Row exactly like screenshot mockup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // AUTO OCR TAB
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isManualMode) RangoLimeGreen else RangoTealSky.copy(alpha = 0.2f))
                                .clickable { isManualModeSelected.value = false }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "⚡ AUTO OCR",
                                color = if (!isManualMode) Color.Black else RangoTextWhite,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // MANUAL TAB
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isManualMode) RangoLimeGreen else RangoTealSky.copy(alpha = 0.2f))
                                .clickable { isManualModeSelected.value = true }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✏️ MANUAL",
                                color = if (isManualMode) Color.Black else RangoTextWhite,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    if (isManualMode) {
                        // MANUAL INPUT SUBSECTION
                        Text(
                            "Game ka multiplier enter karo (e.g. 1.85):",
                            color = RangoTextMuted,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val inputText by manualMultiplierInput.collectAsState()
                        
                        TextField(
                            value = inputText,
                            onValueChange = { manualMultiplierInput.value = it },
                            placeholder = { Text("e.g. 1.85", fontSize = 10.sp, color = RangoTextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = RangoTextWhite,
                                unfocusedTextColor = RangoTextWhite,
                                focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                focusedIndicatorColor = RangoLimeGreen,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .onFocusChanged { focusState ->
                                    updateWindowFocus(focusState.isFocused)
                                },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        )
                        
                        Button(
                            onClick = {
                                val multiplierDouble = inputText.toDoubleOrNull()
                                if (multiplierDouble != null && multiplierDouble >= 1.0 && multiplierDouble <= 1000.0) {
                                    manualMultiplierInput.value = ""
                                    // Clear input focus to dismiss keyboard
                                    updateWindowFocus(false)
                                    // Add value as detected!
                                    checkAndCommitDetectedValue(multiplierDouble)
                                    captureLogs.value = "Manual added: ${multiplierDouble}x."
                                } else {
                                    captureLogs.value = "Krpya sahi multiplier enter karo (1.0 - 1000)!"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                        ) {
                            Text("✔️ SUBMIT & PREDICT", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        // AUTO OCR SCAN CONTROL SUBSECTION
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
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            putExtra("EXTRA_START_OCR_IMMEDIATELY", true)
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
                            "🔮 GEMINI AI RESPONSE",
                            color = RangoDesertGold,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black
                        )
                        
                        if (liveLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(color = RangoLimeGreen, modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                Text("AI analyzing live history...", color = RangoTextWhite, fontSize = 8.sp)
                            }
                        } else if (liveAdvice.isEmpty()) {
                            Text(
                                "Rounds add karo → auto analysis shuru ho",
                                color = RangoTextWhite,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 2.dp)
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

                                    // Crop from top of screen down to 48% height to scan both Lobby Ribbon and giant center text
                                    val x = (targetWidth * 0.03).toInt()
                                    val y = (targetHeight * 0.02).toInt()
                                    val w = (targetWidth * 0.94).toInt().coerceAtMost(bitmap.width - x)
                                    val h = (targetHeight * 0.46).toInt().coerceAtMost(bitmap.height - y)

                                    if (w > 0 && h > 0) {
                                        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, w, h)
                                        croppedBmp = croppedBitmap
                                        val image = InputImage.fromBitmap(croppedBitmap, 0)
                                        
                                        recognizer.process(image)
                                            .addOnSuccessListener { visionText ->
                                                val text = visionText.text
                                                val textLower = text.lowercase()
                                                val isCrashEnded = textLower.contains("flew") || textLower.contains("away")
                                                
                                                // Parse decimal multipliers like 1.07x, 2.14, 4.34x
                                                val regex = Regex("""\b(\d+[\.,]\d{1,2})\s*x?\b""", RegexOption.IGNORE_CASE)
                                                val matches = regex.findAll(text).toList()
                                                
                                                if (matches.isNotEmpty()) {
                                                    var matchedVal: Double? = null
                                                    
                                                    if (isCrashEnded) {
                                                        // When crashed, look for any multiplier in the text showing the crash value
                                                        val matchedStr = matches.first().groupValues[1].replace(',', '.')
                                                        matchedVal = matchedStr.toDoubleOrNull()
                                                    } else {
                                                        // Otherwise, take the first multiplier matching (usually leftmost lobby ribbon multiplier)
                                                        val matchedStr = matches.first().groupValues[1].replace(',', '.')
                                                        matchedVal = matchedStr.toDoubleOrNull()
                                                    }
                                                    
                                                    if (matchedVal != null && matchedVal >= 1.0 && matchedVal < 1000.0) {
                                                        serviceScope.launch(Dispatchers.Main) {
                                                            latestScannedMultiplier.value = String.format("%.2f", matchedVal)
                                                            captureLogs.value = "OCR match: ${matchedVal}x" + if (isCrashEnded) " (FLEW AWAY)" else ""
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
                Current Balance is PKR $currentBalance.
                Output Format Requirements (Use simple concise Hinglish/English similar to the mockup):
                - RISK LEVEL: [LOW / MEDIUM / HIGH]
                - SUGGESTED BET: [PKR X.XX or SKIP]
                - SAFE CASHOUT: [X.XXx]
                - ANALYSIS: [Provide a 1-sentence math explanation in very simple mixed English/Urdu/Hindi, e.g. "Cold streak chal raha hai, next 1.80x recovery chance high lag raha hai."]
                Do not include markdown intro header, backticks, asterisks, or long conversational filler. Keep response strictly under 4 lines.
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
