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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import android.content.res.Configuration
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.example.data.DragonTigerRound
import com.example.data.DragonTigerDao
import com.example.data.DragonTigerAnalyzer
import com.example.ui.theme.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        // Static variables for passing MediaProjection parameters safely without bundle serialization bugs
        @JvmStatic var savedProjectionResultCode: Int = -1
        @JvmStatic var savedProjectionIntent: Intent? = null

        // Static active instance listener for high-speed same-process callbacks
        @Volatile @JvmStatic var activeInstance: OverlayService? = null
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    
    // DB Repository
    private lateinit var repository: CrashRepository
    private lateinit var dtDao: DragonTigerDao
    private lateinit var abDao: com.example.data.AndarBaharDao
    private lateinit var sevenDao: com.example.data.SevenUpDownDao
    private lateinit var baccaratDao: com.example.data.BaccaratDao
    private lateinit var rouletteDao: com.example.data.RouletteDao
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // HUD setup
    enum class HudMode { HORIZONTAL, VERTICAL }
    private val _currentHudMode = MutableStateFlow(HudMode.HORIZONTAL)
    val currentHudMode: StateFlow<HudMode> = _currentHudMode.asStateFlow()
    
    private val overlayGame = MutableStateFlow("RANGO")
    private var autoHudMode = true
    private var recentDtRoundsList = MutableStateFlow<List<DragonTigerRound>>(emptyList())
    private var recentAbRoundsList = MutableStateFlow<List<com.example.data.AndarBaharRound>>(emptyList())
    private var recentSevenRoundsList = MutableStateFlow<List<com.example.data.SevenUpDownRound>>(emptyList())
    private var recentBaccaratRoundsList = MutableStateFlow<List<com.example.data.BaccaratRound>>(emptyList())
    private var recentRouletteRoundsList = MutableStateFlow<List<com.example.data.RouletteRound>>(emptyList())
    
    // Floating View States
    private var isExpanded = MutableStateFlow(false)
    private var walletBalanceInput = MutableStateFlow("280.89")
    private var latestScannedMultiplier = MutableStateFlow("1.00")
    private var recentMultipliersList = MutableStateFlow<List<Double>>(emptyList())
    private var captureLogs = MutableStateFlow("OCR Scanner offline. Press START.")
    private var isOcrScanning = MutableStateFlow(false)
    private var isManualModeSelected = MutableStateFlow(true)
    private var isBottomSectionExpanded = MutableStateFlow(false)
    private var manualMultiplierInput = MutableStateFlow("")

    // Dynamic Live Gemini HUD advice
    private var geminiAiAdvice = MutableStateFlow("")
    private var isGeminiLoading = MutableStateFlow(false)
    private var geminiApiKey = MutableStateFlow("")
    private var shareBalanceWithAi = MutableStateFlow(false)
    private var onBackPressCallback: (() -> Unit)? = null

    // Media Projection & OCR components
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var ocrJob: Job? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var lastRecordedValue = 0.0
    private var lastGeminiCallTime = 0L
    private var isServiceDestroying = false

    private var livePeakMultiplier = 1.00
    private var isInsideFlight = false
    private var lastDtOcrLogTime = 0L

    // Orientation dynamic viewport sizing
    private var currentWidth = 480
    private var currentHeight = 800

    private fun setupVirtualDisplay() {
        val mp = mediaProjection ?: return
        
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            currentWidth = 800
            currentHeight = 480
        } else {
            currentWidth = 480
            currentHeight = 800
        }
        
        val density = 1
        imageReader = ImageReader.newInstance(currentWidth, currentHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mp.createVirtualDisplay(
            "RangoOcrMirror",
            currentWidth, currentHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isOcrScanning.value && mediaProjection != null) {
            setupVirtualDisplay()
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        isServiceRunning.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val database = AppDatabase.getDatabase(this)
        repository = CrashRepository(database.crashDao())
        dtDao = database.dragonTigerDao()
        abDao = database.andarBaharDao()
        sevenDao = database.sevenUpDownDao()
        baccaratDao = database.baccaratDao()
        rouletteDao = database.rouletteDao()
        
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

        // Listen to Dragon Tiger updates
        serviceScope.launch {
            dtDao.getAllRounds().collect { list ->
                recentDtRoundsList.value = list.take(30)
            }
        }

        // Listen to Andar Bahar updates
        serviceScope.launch {
            abDao.getAllRounds().collect { list ->
                recentAbRoundsList.value = list.take(30)
            }
        }

        // Listen to 7 Up Down updates
        serviceScope.launch {
            sevenDao.getAllRounds().collect { list ->
                recentSevenRoundsList.value = list.take(30)
            }
        }

        // Listen to Baccarat updates
        serviceScope.launch {
            baccaratDao.getAllRounds().collect { list ->
                recentBaccaratRoundsList.value = list.take(30)
            }
        }

        // Listen to Roulette updates
        serviceScope.launch {
            rouletteDao.getAllRounds().collect { list ->
                recentRouletteRoundsList.value = list.take(30)
            }
        }

        // Keep window focus state synced with manual/auto mode switches
        serviceScope.launch {
            isManualModeSelected.collect { isManual ->
                updateWindowFocus(isManual)
            }
        }

        // Securely pre-load Gemini API profile Key if set by the user
        geminiApiKey.value = com.example.util.SecurePrefs.getGeminiApiKey(this)
        shareBalanceWithAi.value = com.example.util.SecurePrefs.isShareBalanceWithAi(this)
        
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
            val game = intent.getStringExtra("EXTRA_GAME")
            val mode = intent.getStringExtra("EXTRA_MODE")
            if (game != null && mode != null) {
                updateHudModeAndGame(mode, game)
            }
            when (intent.action) {
                ACTION_START_PROJECTION -> {
                    val resultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, -1)
                    val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_PROJECTION_INTENT_DATA, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_PROJECTION_INTENT_DATA) as? Intent
                    }
                    
                    // Fallback using companion static fields in case of parcelable serialization bottlenecks
                    val finalCode = if (resultCode == android.app.Activity.RESULT_OK) resultCode else savedProjectionResultCode
                    val finalIntent = data ?: savedProjectionIntent
                    
                    if (finalCode == android.app.Activity.RESULT_OK && finalIntent != null) {
                        startMediaProjection(finalCode, finalIntent)
                    } else {
                        captureLogs.value = "Capture init error: code=$resultCode, nullData=${data == null}"
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
        if (activeInstance == this) {
            activeInstance = null
        }
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
            private var isTouchInHeader = false

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    if (isManualModeSelected.value) {
                        updateWindowFocus(true)
                    }
                    val density = resources.displayMetrics.density
                    val headerHeightPx = 40f * density
                    isTouchInHeader = ev.y <= headerHeightPx
                }
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
                        if (isTouchInHeader) {
                            val dx = ev.rawX - initialTouchX
                            val dy = ev.rawY - initialTouchY
                            if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                                isDragging = true
                                return true // Intercept! Cancel goes to ComposeView.
                            }
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
                                
                                val hasEndGravity = (lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.END || 
                                                    (lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT
                                val signX = if (hasEndGravity) -1 else 1

                                lp.x = initialX + signX * dx.toInt()
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

            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    if (event.action == android.view.KeyEvent.ACTION_UP) {
                        onBackPressCallback?.invoke()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
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

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun updateWindowFocus(focusable: Boolean) {
        mainHandler.post {
            val root = overlayView ?: return@post
            val lp = root.layoutParams as? WindowManager.LayoutParams ?: return@post
            val oldFlags = lp.flags
            if (focusable) {
                lp.flags = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            } else {
                lp.flags = (lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
            }
            if (oldFlags != lp.flags) {
                try {
                    windowManager.updateViewLayout(root, lp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updatePillGravity(gravityVal: Int) {
        mainHandler.post {
            val root = overlayView ?: return@post
            val lp = root.layoutParams as? WindowManager.LayoutParams ?: return@post
            lp.gravity = gravityVal
            try {
                windowManager.updateViewLayout(root, lp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateHudModeAndGame(modeStr: String, gameStr: String) {
        serviceScope.launch {
            overlayGame.value = gameStr
            when (modeStr) {
                "VERTICAL" -> {
                    autoHudMode = false
                    _currentHudMode.value = HudMode.VERTICAL
                    updatePillGravity(Gravity.TOP or Gravity.END)
                }
                "HORIZONTAL" -> {
                    autoHudMode = false
                    _currentHudMode.value = HudMode.HORIZONTAL
                    updatePillGravity(Gravity.TOP or Gravity.START)
                }
                "AUTO" -> {
                    autoHudMode = true
                    val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    _currentHudMode.value = if (isPortrait) HudMode.HORIZONTAL else HudMode.VERTICAL
                    updatePillGravity(if (isPortrait) (Gravity.TOP or Gravity.START) else (Gravity.TOP or Gravity.START))
                }
            }
            // Trigger automatic AI strategy advice refresh for the newly switched game
            triggerRealtimeGeminiPipeline(force = true)
        }
    }

    private fun parseAiRecommendation(adviceText: String): String {
        if (adviceText.contains("RECOMMENDATION:", ignoreCase = true)) {
            val rec = adviceText.substringAfter("RECOMMENDATION:")
                .substringBefore("\n")
                .trim()
                .uppercase()
            val clean = rec.replace("[", "").replace("]", "").trim()
            if (clean.contains("DRAGON") || clean == "D") return "DRAGON"
            if (clean.contains("TIGER") || clean == "T") return "TIGER"
            if (clean.contains("ANDAR") || clean == "A") return "ANDAR"
            if (clean.contains("BAHAR") || clean == "B") return "BAHAR"
            if (clean.contains("UP") || clean == "U") return "UP"
            if (clean.contains("DOWN") || clean == "D") return "DOWN"
            if (clean.contains("SEVEN") || clean == "7") return "SEVEN"
            if (clean.contains("PLAYER") || clean == "P") return "PLAYER"
            if (clean.contains("BANKER") || clean == "B") return "BANKER"
            if (clean.contains("TIE") || clean == "T") return "TIE"
            if (clean.contains("RED")) return "RED"
            if (clean.contains("BLACK")) return "BLACK"
            if (clean.contains("EVEN")) return "EVEN"
            if (clean.contains("ODD")) return "ODD"
            if (clean.contains("HIGH")) return "HIGH"
            if (clean.contains("LOW")) return "LOW"
            if (clean.contains("1ST")) return "1ST DOZEN"
            if (clean.contains("2ND")) return "2ND DOZEN"
            if (clean.contains("3RD")) return "3RD DOZEN"
        }
        return "UNCERTAIN"
    }

    private fun parseCrashAiTarget(adviceText: String): Double? {
        if (adviceText.contains("RECOMMENDATION:", ignoreCase = true)) {
            val rec = adviceText.substringAfter("RECOMMENDATION:")
                .substringBefore("\n")
                .trim()
                .lowercase()
            val regex = """(\d+\.?\d*)""".toRegex()
            val match = regex.find(rec)
            if (match != null) {
                return match.value.toDoubleOrNull()
            }
        }
        return null
    }

    private fun addDragonTigerRoundResult(result: String) {
        serviceScope.launch {
            val freshHistory = dtDao.getRecentRounds(30)
            val localResult = com.example.data.DragonTigerAnalyzer.analyze(freshHistory)
            val pred = localResult.predictedNext
            val hasGemini = geminiApiKey.value.isNotBlank() && geminiAiAdvice.value.isNotBlank()
            val source = if (hasGemini) "AI" else "LOCAL"
            val finalPred = if (source == "AI") {
                val parsed = parseAiRecommendation(geminiAiAdvice.value)
                if (parsed != "UNCERTAIN") parsed else pred
            } else {
                pred
            }
            val win = if (finalPred == "DRAGON" && result == "D") true 
                      else if (finalPred == "TIGER" && result == "T") true 
                      else if (finalPred == "UNCERTAIN" || result == "X") null 
                      else false

            dtDao.insertRound(com.example.data.DragonTigerRound(
                result = result,
                prediction = finalPred,
                predictionSource = source,
                isWin = win
            ))
            val updatedHistory = dtDao.getRecentRounds(30)
            recentDtRoundsList.value = updatedHistory
            captureLogs.value = "Result logged: $result (${if (win == true) "WIN" else if (win == false) "LOSS" else "WAIT"})"
            triggerRealtimeGeminiPipeline(force = true)
        }
    }

    private fun deleteLastDragonTigerRound() {
        serviceScope.launch {
            dtDao.deleteLastRound()
            captureLogs.value = "Last round undone"
            val remaining = dtDao.getRecentRounds(30)
            recentDtRoundsList.value = remaining
            if (remaining.isEmpty()) {
                geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            } else {
                triggerRealtimeGeminiPipeline(force = true)
            }
        }
    }

    private fun clearAllDragonTigerRounds() {
        serviceScope.launch {
            dtDao.clearAll()
            recentDtRoundsList.value = emptyList()
            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            captureLogs.value = "All rounds cleared"
        }
    }

    private fun addAndarBaharRoundResult(result: String) {
        serviceScope.launch {
            val freshHistory = abDao.getRecentRounds(30)
            val localResult = com.example.data.AndarBaharAnalyzer.analyze(freshHistory)
            val pred = localResult.predictedNext
            val hasGemini = geminiApiKey.value.isNotBlank() && geminiAiAdvice.value.isNotBlank()
            val source = if (hasGemini) "AI" else "LOCAL"
            val finalPred = if (source == "AI") {
                val parsed = parseAiRecommendation(geminiAiAdvice.value)
                if (parsed != "UNCERTAIN") parsed else pred
            } else {
                pred
            }
            val win = if (finalPred == "ANDAR" && result == "A") true 
                      else if (finalPred == "BAHAR" && result == "B") true 
                      else if (finalPred == "UNCERTAIN") null 
                      else false

            abDao.insertRound(com.example.data.AndarBaharRound(
                result = result,
                prediction = finalPred,
                predictionSource = source,
                isWin = win
            ))
            val updatedHistory = abDao.getRecentRounds(30)
            recentAbRoundsList.value = updatedHistory
            captureLogs.value = "Result logged: $result (${if (win == true) "WIN" else if (win == false) "LOSS" else "WAIT"})"
            triggerRealtimeGeminiPipeline(force = true)
        }
    }

    private fun deleteLastAndarBaharRound() {
        serviceScope.launch {
            abDao.deleteLastRound()
            captureLogs.value = "Last round undone"
            val remaining = abDao.getRecentRounds(30)
            recentAbRoundsList.value = remaining
            if (remaining.isEmpty()) {
                geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            } else {
                triggerRealtimeGeminiPipeline(force = true)
            }
        }
    }

    private fun clearAllAndarBaharRounds() {
        serviceScope.launch {
            abDao.clearAll()
            recentAbRoundsList.value = emptyList()
            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            captureLogs.value = "All rounds cleared"
        }
    }

    private fun addSevenUpDownRoundResult(result: String) {
        serviceScope.launch {
            val freshHistory = sevenDao.getRecentRounds(30)
            val localResult = com.example.data.SevenUpDownAnalyzer.analyze(freshHistory)
            val pred = localResult.predictedNext
            val hasGemini = geminiApiKey.value.isNotBlank() && geminiAiAdvice.value.isNotBlank()
            val source = if (hasGemini) "AI" else "LOCAL"
            val finalPred = if (source == "AI") {
                val parsed = parseAiRecommendation(geminiAiAdvice.value)
                if (parsed != "UNCERTAIN") parsed else pred
            } else {
                pred
            }
            val win = if (finalPred == "UP" && result == "U") true 
                      else if (finalPred == "DOWN" && result == "D") true 
                      else if (finalPred == "SEVEN" && result == "7") true 
                      else if (finalPred == "UNCERTAIN") null 
                      else false

            sevenDao.insertRound(com.example.data.SevenUpDownRound(
                result = result,
                prediction = finalPred,
                predictionSource = source,
                isWin = win
            ))
            val updatedHistory = sevenDao.getRecentRounds(30)
            recentSevenRoundsList.value = updatedHistory
            captureLogs.value = "Result logged: $result (${if (win == true) "WIN" else if (win == false) "LOSS" else "WAIT"})"
            triggerRealtimeGeminiPipeline(force = true)
        }
    }

    private fun deleteLastSevenUpDownRound() {
        serviceScope.launch {
            sevenDao.deleteLastRound()
            captureLogs.value = "Last round undone"
            val remaining = sevenDao.getRecentRounds(30)
            recentSevenRoundsList.value = remaining
            if (remaining.isEmpty()) {
                geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            } else {
                triggerRealtimeGeminiPipeline(force = true)
            }
        }
    }

    private fun clearAllSevenUpDownRounds() {
        serviceScope.launch {
            sevenDao.clearAll()
            recentSevenRoundsList.value = emptyList()
            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            captureLogs.value = "All rounds cleared"
        }
    }

    private fun addBaccaratRoundResult(result: String) {
        serviceScope.launch {
            val freshHistory = baccaratDao.getRecentRounds(30)
            val localResult = com.example.data.BaccaratAnalyzer.analyze(freshHistory)
            val pred = localResult.predictedNext
            val hasGemini = geminiApiKey.value.isNotBlank() && geminiAiAdvice.value.isNotBlank()
            val source = if (hasGemini) "AI" else "LOCAL"
            val finalPred = if (source == "AI") {
                val parsed = parseAiRecommendation(geminiAiAdvice.value)
                if (parsed != "UNCERTAIN") parsed else pred
            } else {
                pred
            }
            val win = if (finalPred == "PLAYER" && result == "P") true 
                      else if (finalPred == "BANKER" && result == "B") true 
                      else if (finalPred == "TIE" && result == "T") true 
                      else if (finalPred == "UNCERTAIN") null 
                      else false

            baccaratDao.insertRound(com.example.data.BaccaratRound(
                result = result,
                prediction = finalPred,
                predictionSource = source,
                isWin = win
            ))
            val updatedHistory = baccaratDao.getRecentRounds(30)
            recentBaccaratRoundsList.value = updatedHistory
            captureLogs.value = "Result logged: $result (${if (win == true) "WIN" else if (win == false) "LOSS" else "WAIT"})"
            triggerRealtimeGeminiPipeline(force = true)
        }
    }

    private fun deleteLastBaccaratRound() {
        serviceScope.launch {
            baccaratDao.deleteLastRound()
            captureLogs.value = "Last round undone"
            val remaining = baccaratDao.getRecentRounds(30)
            recentBaccaratRoundsList.value = remaining
            if (remaining.isEmpty()) {
                geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            } else {
                triggerRealtimeGeminiPipeline(force = true)
            }
        }
    }

    private fun clearAllBaccaratRounds() {
        serviceScope.launch {
            baccaratDao.clearAll()
            recentBaccaratRoundsList.value = emptyList()
            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            captureLogs.value = "All rounds cleared"
        }
    }

    private fun addRouletteRoundResult(result: String) {
        serviceScope.launch {
            val freshHistory = rouletteDao.getRecentRounds(30)
            val localResult = com.example.data.RouletteAnalyzer.analyze(freshHistory)
            val pred = localResult.suggestedBet
            val hasGemini = geminiApiKey.value.isNotBlank() && geminiAiAdvice.value.isNotBlank()
            val source = if (hasGemini) "AI" else "LOCAL"
            val finalPred = if (source == "AI") {
                val parsed = parseAiRecommendation(geminiAiAdvice.value)
                if (parsed != "UNCERTAIN") parsed else pred
            } else {
                pred
            }

            val num = result.toIntOrNull()
            val color = if (num != null) {
                com.example.data.RouletteAnalyzer.getColorForNumber(num)
            } else {
                when {
                    result.contains("RED", ignoreCase = true) -> "RED"
                    result.contains("BLACK", ignoreCase = true) -> "BLACK"
                    result.contains("GREEN", ignoreCase = true) || result == "0" -> "GREEN"
                    else -> "RED"
                }
            }

            val win: Boolean? = when {
                finalPred.contains("RED") && color == "RED" -> true
                finalPred.contains("BLACK") && color == "BLACK" -> true
                finalPred.contains("EVEN") && num != null && num != 0 && num % 2 == 0 -> true
                finalPred.contains("ODD") && num != null && num % 2 != 0 -> true
                finalPred.contains("HIGH") && num != null && num in 19..36 -> true
                finalPred.contains("LOW") && num != null && num in 1..18 -> true
                finalPred.contains("1ST") && num != null && num in 1..12 -> true
                finalPred.contains("2ND") && num != null && num in 13..24 -> true
                finalPred.contains("3RD") && num != null && num in 25..36 -> true
                finalPred == "STANDBY" || finalPred == "UNCERTAIN" -> null
                else -> false
            }

            rouletteDao.insertRound(com.example.data.RouletteRound(
                result = result,
                number = num,
                color = color,
                prediction = finalPred,
                predictionSource = source,
                isWin = win
            ))
            val updatedHistory = rouletteDao.getRecentRounds(30)
            recentRouletteRoundsList.value = updatedHistory
            captureLogs.value = "Result logged: $result (${if (win == true) "WIN" else if (win == false) "LOSS" else "WAIT"})"
            triggerRealtimeGeminiPipeline(force = true)
        }
    }

    private fun deleteLastRouletteRound() {
        serviceScope.launch {
            rouletteDao.deleteLastRound()
            captureLogs.value = "Last round undone"
            val remaining = rouletteDao.getRecentRounds(30)
            recentRouletteRoundsList.value = remaining
            if (remaining.isEmpty()) {
                geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            } else {
                triggerRealtimeGeminiPipeline(force = true)
            }
        }
    }

    private fun clearAllRouletteRounds() {
        serviceScope.launch {
            rouletteDao.clearAll()
            recentRouletteRoundsList.value = emptyList()
            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
            captureLogs.value = "All rounds cleared"
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
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        val verticalPadding = if (isLandscape) 1.dp else 2.dp
        val horizontalPadding = if (isLandscape) 1.5.dp else 2.dp
        val titleSize = if (isLandscape) 4.5.sp else 5.8.sp
        val valueSize = if (isLandscape) 7.5.sp else 8.5.sp

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(3.dp))
                .background(bgColor)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = RangoTextWhite.copy(alpha = 0.85f),
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = valueSize,
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
        
        val overlayMode by currentHudMode.collectAsState()
        val currentGameVal by overlayGame.collectAsState()
        val recentDtList by recentDtRoundsList.collectAsState()
        val recentAbList by recentAbRoundsList.collectAsState()
        val recentSevenList by recentSevenRoundsList.collectAsState()
        val recentBaccaratList by recentBaccaratRoundsList.collectAsState()
        val recentRouletteList by recentRouletteRoundsList.collectAsState()
        
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        LaunchedEffect(focusManager) {
            onBackPressCallback = {
                focusManager.clearFocus()
                updateWindowFocus(false)
            }
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val expandedWidthDp = if (isLandscape) 190.dp else 150.dp
        val expandedMaxHeightDp = if (isLandscape) {
            (configuration.screenHeightDp * 0.95f).dp
        } else {
            (configuration.screenHeightDp * 0.65f).dp
        }

        var isGeminiExpanded by remember { mutableStateOf(false) }

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

        val overlayWidth = if (expanded) {
            expandedWidthDp
        } else {
            if (overlayMode == HudMode.VERTICAL) 36.dp else 46.dp
        }

        Column(
            modifier = Modifier
                .width(overlayWidth)
                .clip(RoundedCornerShape(6.dp))
                .background(RangoHorizon.copy(alpha = 0.96f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                    updateWindowFocus(false)
                }
                .padding(
                    if (expanded) PaddingValues(horizontal = 4.dp, vertical = 3.dp)
                    else {
                        if (overlayMode == HudMode.VERTICAL) PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                        else PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (expanded) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(RangoTextMuted.copy(alpha = 0.5f))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(1.dp))
            }

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { 
                            focusManager.clearFocus()
                            updateWindowFocus(false)
                            isExpanded.value = !expanded 
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(riskColor, CircleShape)
                    )
                    Text(
                        text = if (expanded) {
                            if (currentGameVal == "DRAGON_TIGER") "🐉 PILOT" else "🟢 PILOT"
                        } else {
                            if (currentGameVal == "DRAGON_TIGER") "🐉 DT" else "HUD"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RangoTextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (expanded) 8.5.sp else 7.sp
                        )
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Minimize/Maximize toggle icon
                    Icon(
                        imageVector = if (expanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = "Resize Bubble",
                        tint = RangoLimeGreen,
                        modifier = Modifier
                            .size(12.dp)
                            .clickable { 
                                focusManager.clearFocus()
                                updateWindowFocus(false)
                                isExpanded.value = !expanded 
                            }
                    )
                    
                    if (expanded) {
                        // Close Overlay completely icon
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop Service",
                            tint = RangoDangerRed,
                            modifier = Modifier
                                .size(13.dp)
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
                if (overlayMode == HudMode.VERTICAL) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { isExpanded.value = true },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val emoji = when (currentGameVal) {
                            "BACCARAT" -> "👑"
                            "ROULETTE" -> "🎡"
                            "DRAGON_TIGER" -> "🐉"
                            "ANDAR_BAHAR" -> "🚪"
                            "SEVEN_UP_DOWN" -> "🎲"
                            else -> "✈️"
                        }
                        Text(emoji, fontSize = 14.sp)
                        
                        val lastR = when (currentGameVal) {
                            "BACCARAT" -> recentBaccaratList.firstOrNull()?.result ?: "P"
                            "ROULETTE" -> recentRouletteList.firstOrNull()?.result ?: "RED"
                            "DRAGON_TIGER" -> recentDtList.firstOrNull()?.result ?: "D"
                            "ANDAR_BAHAR" -> recentAbList.firstOrNull()?.result ?: "A"
                            "SEVEN_UP_DOWN" -> recentSevenList.firstOrNull()?.result ?: "U"
                            else -> if (historyList.isNotEmpty()) "${historyList.first().toInt()}x" else "1x"
                        }
                        Text(
                            text = lastR,
                            color = RangoTextWhite,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Text(
                            text = "↑",
                            color = RangoLimeGreen,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val predChar = when (currentGameVal) {
                            "BACCARAT" -> {
                                val bacAns = com.example.data.BaccaratAnalyzer.analyze(recentBaccaratList)
                                if (bacAns.predictedNext == "PLAYER" || bacAns.predictedNext == "BANKER" || bacAns.predictedNext == "TIE") bacAns.predictedNext.take(2) else "BC"
                            }
                            "ROULETTE" -> {
                                val roulAns = com.example.data.RouletteAnalyzer.analyze(recentRouletteList)
                                roulAns.suggestedBet.take(2)
                            }
                            "DRAGON_TIGER" -> {
                                val dtAns = DragonTigerAnalyzer.analyze(recentDtList)
                                if (dtAns.predictedNext == "DRAGON" || dtAns.predictedNext == "TIGER") dtAns.predictedNext.take(2) else "DR"
                            }
                            "ANDAR_BAHAR" -> {
                                val abAns = com.example.data.AndarBaharAnalyzer.analyze(recentAbList)
                                if (abAns.predictedNext == "ANDAR" || abAns.predictedNext == "BAHAR") abAns.predictedNext.take(2) else "AB"
                            }
                            "SEVEN_UP_DOWN" -> {
                                val sevenAns = com.example.data.SevenUpDownAnalyzer.analyze(recentSevenList)
                                if (sevenAns.predictedNext == "UP" || sevenAns.predictedNext == "DOWN" || sevenAns.predictedNext == "SEVEN") sevenAns.predictedNext.take(2) else "7U"
                            }
                            else -> {
                                "CR"
                            }
                        }
                        Text(
                            text = predChar,
                            color = RangoDesertGold,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { isExpanded.value = true },
                        verticalArrangement = Arrangement.spacedBy(0.5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (currentGameVal) {
                                "BACCARAT" -> "Last: ${recentBaccaratList.firstOrNull()?.result ?: "P"}"
                                "ROULETTE" -> "Last: ${recentRouletteList.firstOrNull()?.result ?: "RED"}"
                                "DRAGON_TIGER" -> "Last: ${recentDtList.firstOrNull()?.result ?: "D"}"
                                "ANDAR_BAHAR" -> "Last: ${recentAbList.firstOrNull()?.result ?: "A"}"
                                "SEVEN_UP_DOWN" -> "Last: ${recentSevenList.firstOrNull()?.result ?: "U"}"
                                else -> "Last: ${historyList.firstOrNull() ?: 1.00}x"
                            },
                            color = RangoTextWhite,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = when (currentGameVal) {
                                "BACCARAT" -> com.example.data.BaccaratAnalyzer.analyze(recentBaccaratList).trendLabel.take(12)
                                "ROULETTE" -> com.example.data.RouletteAnalyzer.analyze(recentRouletteList).trendLabel.take(12)
                                "DRAGON_TIGER" -> DragonTigerAnalyzer.analyze(recentDtList).trendLabel.take(12)
                                "ANDAR_BAHAR" -> com.example.data.AndarBaharAnalyzer.analyze(recentAbList).trendLabel.take(12)
                                "SEVEN_UP_DOWN" -> com.example.data.SevenUpDownAnalyzer.analyze(recentSevenList).trendLabel.take(12)
                                else -> metrics.trend
                            },
                            color = riskColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tap",
                            color = RangoTextMuted,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // EXPANDED INTERACTIVE CONTROL PANEL (With Max Height & Scrolling Column wrapper)
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = expandedMaxHeightDp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 1.dp))

                    if (currentGameVal == "BACCARAT") {
                        BaccaratHudView(
                            recentBaccaratList = recentBaccaratList,
                            isLandscape = isLandscape,
                            onAddResult = { addBaccaratRoundResult(it) },
                            onUndo = { deleteLastBaccaratRound() },
                            onReset = { clearAllBaccaratRounds() }
                        )
                    } else if (currentGameVal == "ROULETTE") {
                        RouletteHudView(
                            recentRouletteList = recentRouletteList,
                            isLandscape = isLandscape,
                            onAddResult = { addRouletteRoundResult(it) },
                            onUndo = { deleteLastRouletteRound() },
                            onReset = { clearAllRouletteRounds() }
                        )
                    } else if (currentGameVal == "DRAGON_TIGER") {
                        DragonTigerHudView(
                            recentDtList = recentDtList,
                            isLandscape = isLandscape,
                            isScanning = isScanning,
                            onAddResult = { addDragonTigerRoundResult(it) },
                            onUndo = { deleteLastDragonTigerRound() },
                            onReset = { clearAllDragonTigerRounds() },
                            onToggleOcr = {
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
                            }
                        )
                    } else if (currentGameVal == "ANDAR_BAHAR") {
                        AndarBaharHudView(
                            recentAbList = recentAbList,
                            isLandscape = isLandscape,
                            onAddResult = { addAndarBaharRoundResult(it) },
                            onUndo = { deleteLastAndarBaharRound() },
                            onReset = { clearAllAndarBaharRounds() }
                        )
                    } else if (currentGameVal == "SEVEN_UP_DOWN") {
                        SevenUpDownHudView(
                            recentSevenList = recentSevenList,
                            isLandscape = isLandscape,
                            onAddResult = { addSevenUpDownRoundResult(it) },
                            onUndo = { deleteLastSevenUpDownRound() },
                            onReset = { clearAllSevenUpDownRounds() }
                        )
                    } else {
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
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(vertical = 1.dp, horizontal = 3.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = headingText,
                                color = headingColor,
                                fontSize = if (isLandscape) 7.5.sp else 9.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Grid of 6 Performance and strategy prediction boxes
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                BoxMetricItem(
                                    title = "BET",
                                    value = "${String.format("%.1f", metrics.betFactor)}x base",
                                    bgColor = Color(0xFF33691E),
                                    valueColor = Color.White,
                                    modifier = Modifier.weight(1.3f)
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
                            fontSize = if (isLandscape) 5.sp else 6.sp,
                            lineHeight = if (isLandscape) 6.5.sp else 7.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 1.dp)
                        )

                        HorizontalDivider(color = RangoTealSky.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 1.dp))

                        // Wallet Balance Section (Without balance buttons row)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "BALANCE:",
                                color = RangoTextMuted,
                                fontSize = if (isLandscape) 6.sp else 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "PKR ${String.format("%.2f", doubleBalance)}",
                                color = Color.White,
                                fontSize = if (isLandscape) 7.5.sp else 8.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        HorizontalDivider(color = RangoTealSky.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 1.dp))

                        // Risk & Last Multipliers Info Summary
                        val riskName = when (metrics.streak) {
                            "HOT" -> "HIGH RISK | Skip"
                            "COLD" -> "LOW RISK | Bet PKR ${df.format(baseBet * metrics.betFactor)}"
                            else -> "MED RISK | Bet PKR ${df.format(baseBet * metrics.betFactor)}"
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(0.5.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("RISK:", color = RangoTextMuted, fontSize = if (isLandscape) 6.sp else 7.sp, fontWeight = FontWeight.Bold)
                                Text(riskName, color = riskColor, fontSize = if (isLandscape) 6.sp else 7.sp, fontWeight = FontWeight.Black)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("LAST:", color = RangoTextMuted, fontSize = if (isLandscape) 6.sp else 7.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (historyList.isEmpty()) "No rounds captured" else historyList.take(4).joinToString("  ") { df.format(it) },
                                    color = Color.White,
                                    fontSize = if (isLandscape) 6.sp else 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        HorizontalDivider(color = RangoTealSky.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 1.dp))

                        // LIVE SCANNER tabbed section (Auto OCR and Manual)
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                "⚡ LIVE SCANNER",
                                color = RangoLimeGreen,
                                fontSize = if (isLandscape) 7.sp else 8.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            
                            // Tabs Row exactly like screenshot mockup
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // AUTO OCR TAB
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (!isManualMode) RangoLimeGreen else RangoTealSky.copy(alpha = 0.2f))
                                        .clickable { 
                                            focusManager.clearFocus()
                                            isManualModeSelected.value = false 
                                        }
                                        .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "⚡ AUTO OCR",
                                        color = if (!isManualMode) Color.Black else RangoTextWhite,
                                        fontSize = if (isLandscape) 6.sp else 7.5.sp,
                                        fontWeight = FontWeight.Black
                                      )
                                }
 
                                // MANUAL TAB
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isManualMode) RangoLimeGreen else RangoTealSky.copy(alpha = 0.2f))
                                        .clickable { 
                                            focusManager.clearFocus()
                                            isManualModeSelected.value = true 
                                        }
                                        .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "✏️ MANUAL",
                                        color = if (isManualMode) Color.Black else RangoTextWhite,
                                        fontSize = if (isLandscape) 6.sp else 7.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
 
                            if (isManualMode) {
                                // MANUAL INPUT SUBSECTION
                                Text(
                                    "Enter multiplier:",
                                    color = RangoTextMuted,
                                    fontSize = if (isLandscape) 6.sp else 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                                
                                val inputText by manualMultiplierInput.collectAsState()
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            focusRequester.requestFocus()
                                        }
                                        .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { manualMultiplierInput.value = it },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = LocalTextStyle.current.copy(
                                            color = RangoTextWhite,
                                            fontSize = if (isLandscape) 9.sp else 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                            .onFocusChanged { focusState ->
                                                updateWindowFocus(focusState.isFocused)
                                            }
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                                                    focusManager.clearFocus()
                                                    updateWindowFocus(false)
                                                    true
                                                } else {
                                                    false
                                                }
                                            },
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (inputText.isEmpty()) {
                                                    Text(
                                                        "e.g. 1.85",
                                                        color = RangoTextMuted,
                                                        fontSize = if (isLandscape) 9.sp else 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E88E5))
                                        .clickable {
                                            val multiplierDouble = inputText.toDoubleOrNull()
                                            if (multiplierDouble != null && multiplierDouble >= 1.0 && multiplierDouble <= 1000.0) {
                                                manualMultiplierInput.value = ""
                                                focusManager.clearFocus()
                                                updateWindowFocus(false)
                                                checkAndCommitDetectedValue(multiplierDouble, forceGemini = true)
                                                captureLogs.value = "Manual added: ${multiplierDouble}x."
                                            } else {
                                                captureLogs.value = "Error: (1.0 - 1000)!"
                                            }
                                        }
                                        .padding(vertical = if (isLandscape) 4.dp else 5.5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✔️ SUBMIT & PREDICT",
                                        color = Color.White,
                                        fontSize = if (isLandscape) 6.sp else 7.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else {
                                // AUTO OCR SCAN CONTROL SUBSECTION
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isScanning) RangoDangerRed else RangoLimeGreen)
                                            .clickable { 
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
                                            }
                                            .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isScanning) "STOP SCAN" else "START AUTO OCR",
                                            color = Color.Black,
                                            fontSize = if (isLandscape) 6.sp else 7.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
 
                                // Scan Logs UI Box
                                Text(
                                    text = logInfo,
                                    color = RangoTextMuted,
                                    fontSize = if (isLandscape) 6.sp else 7.sp,
                                    lineHeight = if (isLandscape) 7.5.sp else 9.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .padding(2.5.dp)
                                )
                            }
                        }
                    } // Ends Rango/Aviator else block
 
                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 1.6.dp))
 
                    // Dynamic Gemini Cockpit Live Performance Panel
                    val liveAdvice by geminiAiAdvice.collectAsState()
                    val liveLoading by isGeminiLoading.collectAsState()
                    val isSharingBalance by shareBalanceWithAi.collectAsState()
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RangoHorizon.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable { isGeminiExpanded = !isGeminiExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (isGeminiExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle Gemini Advice Expansion",
                                        tint = RangoDesertGold,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (isLandscape) "🔮 GEMINI AI" else "🔮 GEMINI AI",
                                        color = RangoDesertGold,
                                        fontSize = if (isLandscape) 7.sp else 7.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    // Mini toggle chip for balance sharing in HUD
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isSharingBalance) RangoLimeGreen.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.4f))
                                            .clickable {
                                                val newSetting = !isSharingBalance
                                                shareBalanceWithAi.value = newSetting
                                                com.example.util.SecurePrefs.setShareBalanceWithAi(this@OverlayService, newSetting)
                                                com.example.api.GeminiClient.setShareBalanceWithAi(newSetting)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isSharingBalance) "💰 BAL: ON" else "💰 BAL: OFF",
                                            color = if (isSharingBalance) RangoLimeGreen else RangoTextMuted,
                                            fontSize = if (isLandscape) 6.sp else 6.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Manual Refresh Gemini Strategy Advice",
                                        tint = RangoLimeGreen,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { triggerRealtimeGeminiPipeline(force = true) }
                                    )
                                }
                            }
                            
                            if (liveLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    CircularProgressIndicator(color = RangoLimeGreen, modifier = Modifier.size(10.dp), strokeWidth = 1.2.dp)
                                    Text("Analyzing live...", color = RangoTextWhite, fontSize = if (isLandscape) 6.sp else 7.5.sp)
                                }
                            } else if (liveAdvice.isEmpty()) {
                                Text(
                                    "Rounds add karo → auto analysis shuru ho",
                                    color = RangoTextWhite,
                                    fontSize = if (isLandscape) 6.sp else 7.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 1.5.dp)
                                )
                            } else {
                                val advicePair = remember(liveAdvice, overlayGame.value) { 
                                    getSimplifiedAdviceDisplay(liveAdvice, overlayGame.value) 
                                }
                                val (recText, expText) = advicePair
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    if (recText.isNotEmpty()) {
                                        val badgeBg = when {
                                            recText.contains("DRAGON") -> Color(0xFF1E88E5).copy(alpha = 0.25f)
                                            recText.contains("TIGER") -> RangoDangerRed.copy(alpha = 0.25f)
                                            recText.contains("PLAYER") -> Color(0xFF1E88E5).copy(alpha = 0.25f)
                                            recText.contains("BANKER") -> RangoDangerRed.copy(alpha = 0.25f)
                                            recText.contains("TIE") -> Color(0xFF8E24AA).copy(alpha = 0.25f)
                                            recText.contains("CASHOUT") -> RangoLimeGreen.copy(alpha = 0.25f)
                                            recText.contains("BET") -> RangoLimeGreen.copy(alpha = 0.25f)
                                            else -> Color.Gray.copy(alpha = 0.2f)
                                        }
                                        val badgeTextCol = when {
                                            recText.contains("DRAGON") -> Color(0xFF64B5F6)
                                            recText.contains("TIGER") -> Color(0xFFFF8A80)
                                            recText.contains("PLAYER") -> Color(0xFF64B5F6)
                                            recText.contains("BANKER") -> Color(0xFFFF8A80)
                                            recText.contains("TIE") -> Color(0xFFE040FB)
                                            recText.contains("CASHOUT") -> RangoLimeGreen
                                            recText.contains("BET") -> RangoLimeGreen
                                            else -> Color.White
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(badgeBg)
                                                .padding(vertical = 3.dp, horizontal = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = recText,
                                                color = badgeTextCol,
                                                fontSize = if (isLandscape) 6.5.sp else 8.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                    
                                    if (expText.isNotEmpty()) {
                                        Text(
                                            text = expText,
                                            color = RangoTextWhite,
                                            fontSize = if (isLandscape) 5.5.sp else 7.sp,
                                            lineHeight = if (isLandscape) 7.sp else 9.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.fillMaxWidth().padding(top = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun startOcrFromActivity(resultCode: Int, data: Intent) {
        startMediaProjection(resultCode, data)
    }

    private fun startMediaProjection(resultCode: Int, data: Intent) {
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
            
            setupVirtualDisplay()
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
                                val wUse = currentWidth
                                val hUse = currentHeight
                                val rowPadding = rowStride - pixelStride * wUse
                                val widthToUse = wUse + rowPadding / pixelStride
                                if (widthToUse > 0) {
                                    val bitmap = Bitmap.createBitmap(
                                        widthToUse,
                                        hUse,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    bitmap.copyPixelsFromBuffer(buffer)
                                    parentBitmap = bitmap

                                    // Blank out overlay window in screenshot to prevent OCR on overlay values itself!
                                    val overlayV = overlayView
                                    if (overlayV != null && overlayV.isAttachedToWindow) {
                                        val lp = overlayV.layoutParams as? WindowManager.LayoutParams
                                        if (lp != null) {
                                            // Get real screen bounds safely
                                            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                                            val realW: Int
                                            val realH: Int
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                val bounds = wm.currentWindowMetrics.bounds
                                                realW = bounds.width().coerceAtLeast(1)
                                                realH = bounds.height().coerceAtLeast(1)
                                            } else {
                                                val display = wm.defaultDisplay
                                                val size = android.graphics.Point()
                                                display.getRealSize(size)
                                                realW = size.x.coerceAtLeast(1)
                                                realH = size.y.coerceAtLeast(1)
                                            }

                                            // Scale factors
                                            val scaleX = wUse.toFloat() / realW.toFloat()
                                            val scaleY = hUse.toFloat() / realH.toFloat()

                                            // Coordinates in virtual display dimensions
                                            val overlayL = (lp.x * scaleX).toInt().coerceIn(0, widthToUse)
                                            val overlayT = (lp.y * scaleY).toInt().coerceIn(0, hUse)
                                            val overlayR = ((lp.x + overlayV.width) * scaleX).toInt().coerceIn(0, widthToUse)
                                            val overlayB = ((lp.y + overlayV.height) * scaleY).toInt().coerceIn(0, hUse)

                                            if (overlayR > overlayL && overlayB > overlayT) {
                                                val canvas = android.graphics.Canvas(bitmap)
                                                val paint = android.graphics.Paint().apply {
                                                    color = android.graphics.Color.BLACK
                                                    style = android.graphics.Paint.Style.FILL
                                                }
                                                canvas.drawRect(
                                                    overlayL.toFloat(),
                                                    overlayT.toFloat(),
                                                    overlayR.toFloat(),
                                                    overlayB.toFloat(),
                                                    paint
                                                )
                                            }
                                        }
                                    }

                                    // Crop from top of screen down to 48% height to scan both Lobby Ribbon and giant center text
                                    val x = (wUse * 0.03).toInt()
                                    val y = (hUse * 0.02).toInt()
                                    val w = (wUse * 0.94).toInt().coerceAtMost(bitmap.width - x)
                                    val h = (hUse * 0.46).toInt().coerceAtMost(bitmap.height - y)

                                    if (w > 0 && h > 0) {
                                        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, w, h)
                                        croppedBmp = croppedBitmap
                                        val image = InputImage.fromBitmap(croppedBitmap, 0)
                                        
                                        recognizer.process(image)
                                            .addOnSuccessListener { visionText ->
                                                processOcrSuccess(visionText)
                                                if (true) return@addOnSuccessListener
                                                val text = visionText.text
                                                val textLower = text.lowercase()
                                                val isCrashEnded = textLower.contains("flew") || 
                                                        textLower.contains("away") || 
                                                        textLower.contains("burst") || 
                                                        textLower.contains("crash") || 
                                                        textLower.contains("crashed")
                                                
                                                // Parse decimal multipliers like 1.07x, 2.14, 4.34x
                                                val regex = Regex("""\b(\d+[\.,]\d{1,2})\s*x?\b""", RegexOption.IGNORE_CASE)
                                                val matches = regex.findAll(text).toList()
                                                
                                                if (matches.isNotEmpty()) {
                                                    val matchedStr = matches.first().groupValues[1].replace(',', '.')
                                                    val matchedVal = matchedStr.toDoubleOrNull()
                                                    
                                                    if (matchedVal != null && matchedVal >= 1.0 && matchedVal < 1000.0) {
                                                        serviceScope.launch(Dispatchers.Main) {
                                                            latestScannedMultiplier.value = String.format("%.2f", matchedVal)
                                                            if (isCrashEnded) {
                                                                captureLogs.value = "CRASH DETECTED: ${matchedVal}x"
                                                                checkAndCommitDetectedValue(matchedVal)
                                                            } else {
                                                                captureLogs.value = "Reading live... Current: ${matchedVal}x"
                                                            }
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

    private fun checkAndCommitDetectedValue(value: Double, forceGemini: Boolean = false) {
        if (value != lastRecordedValue) {
            lastRecordedValue = value
            serviceScope.launch {
                // Instantly update database
                val doubleBalance = walletBalanceInput.value.toDoubleOrNull() ?: 280.89
                val baseBet = (doubleBalance * 0.01).coerceAtLeast(1.0)
                val target = if (value < 1.20) 1.5 else 1.30
                addCapturedMultiplierToDatabase(value, baseBet, target)

                // Instantly trigger dynamic real-time Gemini pipeline!
                triggerRealtimeGeminiPipeline(force = forceGemini)
            }
        }
    }

    private fun generateLocalFallbackAdvice(gameType: String, currentBalance: Double): String {
        return if (gameType == "DRAGON_TIGER") {
            val localDtResult = DragonTigerAnalyzer.analyze(recentDtRoundsList.value)
            if (localDtResult.trendLabel == "COLLECTING") {
                return "🤖 [Local Engine]\nAdd at least 6 rounds to begin offline road matrix diagnostics."
            }
            "🤖 [Offline Local Road Analyser]\n" +
            "• Next Recommendation: ${if (localDtResult.predictedNext == "UNCERTAIN") "STANDBY (UNCERTAIN)" else localDtResult.predictedNext}\n" +
            "• Road Decision: ${localDtResult.finalRoadDecision}\n" +
            "• Signals: BEB = ${localDtResult.bigEyeBoySignal}, SR = ${localDtResult.smallRoadSignal}, CR = ${localDtResult.cockroachRoadSignal}\n" +
            "• Analysis: ${localDtResult.advice}"
        } else {
            val metrics = calculateLiveMetrics(recentMultipliersList.value)
            "🤖 [Local Core Analysis]\nBased on current ${metrics.trend} trend, recommend cashout safely around ${String.format("%.2f", metrics.cashout)}x. Suggested Bet: ${metrics.betFactor}x base."
        }
    }

    private fun getSimplifiedAdviceDisplay(advice: String, game: String): Pair<String, String> {
        if (advice.isEmpty()) return Pair("", "")
        if (advice.contains("Gemini API Key is missing") || advice.contains("missing") || advice.contains("Please add")) {
            val cleanMsg = advice.replace("⚠️", "").trim()
            return Pair("NO API KEY ⚠️", cleanMsg)
        }
        if (advice.startsWith("⚠️") || advice.contains("API Error") || advice.contains("Server connection issue")) {
            val cleanMsg = advice.replace("⚠️", "").trim()
            return Pair("API ERROR ❌", cleanMsg)
        }
        if (advice.startsWith("Rate Limit") || advice.contains("Cooldown") || advice.contains("Wait")) {
            return Pair("COOLDOWN ⏳", advice)
        }
        if (advice.contains("Connection error") || advice.contains("limit reached")) {
            return Pair("ERROR ⚠️", advice)
        }
        if (advice.lowercase().contains("offline") || advice.lowercase().contains("local")) {
            return Pair("RECOMMENDATION: STANDBY ⏳", "Waiting for online Gemini AI strategy advice...")
        }
        
        val lower = advice.lowercase()
        val lines = advice.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        val recommendationLine = lines.find { it.lowercase().startsWith("recommendation:") }
        val reasonLine = lines.find { it.lowercase().startsWith("reason:") }
        
        if (game == "DRAGON_TIGER") {
            var rec = "STANDBY ⏳"
            var explanation = ""
            
            if (recommendationLine != null) {
                val rVal = recommendationLine.substringAfter(":").trim().replace(Regex("\\*\\*|\\*"), "").uppercase()
                rec = when {
                    rVal.contains("DRAGON") -> "RECOMMENDATION: DRAGON 🐉"
                    rVal.contains("TIGER") -> "RECOMMENDATION: TIGER 🐯"
                    rVal.contains("TIE") -> "RECOMMENDATION: TIE 👔"
                    rVal.contains("SKIP") || rVal.contains("STANDBY") -> "RECOMMENDATION: SKIP / STANDBY ⏳"
                    else -> "RECOMMENDATION: $rVal"
                }
            } else {
                if (lower.contains("bet **dragon**") || lower.contains("bet dragon") || lower.contains("opinion**: dragon") || lower.contains("opinion: dragon")) {
                    rec = "RECOMMENDATION: DRAGON 🐉"
                } else if (lower.contains("bet **tiger**") || lower.contains("bet tiger") || lower.contains("opinion**: tiger") || lower.contains("opinion: tiger")) {
                    rec = "RECOMMENDATION: TIGER 🐯"
                } else if (lower.contains("bet **tie**") || lower.contains("bet tie") || lower.contains("opinion**: tie") || lower.contains("opinion: tie")) {
                    rec = "RECOMMENDATION: TIE 👔"
                } else if (lower.contains("skip") || lower.contains("standby") || lower.contains("uncertain")) {
                    rec = "RECOMMENDATION: SKIP / STANDBY ⏳"
                } else if (lower.contains("dragon")) {
                    rec = "RECOMMENDATION: DRAGON 🐉"
                } else if (lower.contains("tiger")) {
                    rec = "RECOMMENDATION: TIGER 🐯"
                } else {
                    rec = "RECOMMENDATION: STANDBY ⏳"
                }
            }
            
            if (reasonLine != null) {
                explanation = reasonLine.substringAfter(":").trim().replace(Regex("\\*\\*|\\*"), "")
            } else {
                val opinionLine = lines.find { it.lowercase().contains("opinion") }?.replace(Regex("\\*\\*|\\*"), "") ?: ""
                val reasoningLine = lines.find { it.lowercase().contains("reasoning") }?.replace(Regex("\\*\\*|\\*"), "") ?: ""
                
                explanation = when {
                    opinionLine.isNotEmpty() && reasoningLine.isNotEmpty() -> "$opinionLine. $reasoningLine"
                    reasoningLine.isNotEmpty() -> reasoningLine
                    opinionLine.isNotEmpty() -> opinionLine
                    lines.isNotEmpty() -> lines.take(2).joinToString(" ").replace(Regex("\\*\\*|\\*"), "")
                    else -> ""
                }
            }
            
            val cleanExplanation = explanation
                .replace(Regex("(?i)opinion:"), "")
                .replace(Regex("(?i)reasoning:"), "")
                .trim()
                
            return Pair(rec, if (cleanExplanation.length > 90) cleanExplanation.take(87) + "..." else cleanExplanation)
        } else {
            var rec = "RECOMMENDATION: STANDBY ⏳"
            var explanation = ""
            
            if (recommendationLine != null) {
                val rVal = recommendationLine.substringAfter(":").trim().replace(Regex("\\*\\*|\\*"), "").uppercase()
                rec = "RECOMMENDATION: $rVal"
            } else {
                if (lower.contains("cashout")) {
                    val regex = Regex("(\\d+\\.\\d+x|\\d+x)")
                    val match = regex.find(advice)
                    rec = if (match != null) "RECOMMENDATION: CASHOUT @ ${match.value} 🚀" else "RECOMMENDATION: CASHOUT ADVICE 🚀"
                } else if (lower.contains("skip") || lower.contains("wait")) {
                    rec = "RECOMMENDATION: SKIP ROUND ❌"
                } else if (lower.contains("bet")) {
                    rec = "RECOMMENDATION: BET ROUND 🎯"
                }
            }
            
            if (reasonLine != null) {
                explanation = reasonLine.substringAfter(":").trim().replace(Regex("\\*\\*|\\*"), "")
            } else {
                explanation = lines.take(2).joinToString(" ").replace(Regex("\\*\\*|\\*"), "")
            }
            return Pair(rec, if (explanation.length > 90) explanation.take(87) + "..." else explanation)
        }
    }

    private fun triggerRealtimeGeminiPipeline(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastGeminiCallTime
        val cooldownMs = 20_000L // 20s safety cooldown to prevent 429
        if (!force && elapsed < cooldownMs) {
            val remainingSecs = ((cooldownMs - elapsed) / 1000) + 1
            if (geminiAiAdvice.value.isEmpty() || geminiAiAdvice.value.contains("Analyzing") || geminiAiAdvice.value.contains("Cooldown") || geminiAiAdvice.value.contains("Wait")) {
                geminiAiAdvice.value = "Rate Limit Protection:\nWait ${remainingSecs}s or tap 🔄 Refresh to run manually."
            }
            return
        }

        lastGeminiCallTime = now
        serviceScope.launch {
            val key = com.example.util.SecurePrefs.getGeminiApiKey(this@OverlayService)
            geminiApiKey.value = key
            val game = overlayGame.value
            val currentBalance = walletBalanceInput.value.toDoubleOrNull() ?: 280.89
            val sendBalance = shareBalanceWithAi.value

            if (key.isBlank()) {
                geminiAiAdvice.value = "⚠️ Please add Gemini API Key on Dashboard to unlock real-time Gemini AI Model!"
                return@launch
            }
            
            isGeminiLoading.value = true
            
            try {
                val adviceResult = when (game) {
                    "BACCARAT" -> {
                        val recentList = baccaratDao.getRecentRounds(30)
                        recentBaccaratRoundsList.value = recentList
                        if (recentList.isEmpty()) {
                            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
                            isGeminiLoading.value = false
                            return@launch
                        }
                        val recent20 = recentList.take(20)
                        val bacResult = com.example.data.BaccaratAnalyzer.analyze(recentList)
                        val dataStr = recent20.joinToString(", ") { it.result }
                        val flowDesc = "Current Streak: ${bacResult.currentStreak} (${bacResult.streakCount} in a row), Table Momentum: ${bacResult.trendLabel}"
                        com.example.api.GeminiClient.analyzeGame(key, "BACCARAT", dataStr, currentBalance, flowDesc, sendBalance)
                    }
                    "DRAGON_TIGER" -> {
                        val recentList = dtDao.getRecentRounds(30)
                        recentDtRoundsList.value = recentList
                        if (recentList.isEmpty()) {
                            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
                            isGeminiLoading.value = false
                            return@launch
                        }
                        val recent20 = recentList.take(20)
                        val dtResult = DragonTigerAnalyzer.analyze(recentList)
                        val dataStr = recent20.joinToString(", ") { it.result }
                        val flowDesc = "Current Streak: ${dtResult.currentStreak} (${dtResult.streakCount} in a row), Table Momentum: ${dtResult.trendLabel}"
                        com.example.api.GeminiClient.analyzeGame(key, "DRAGON_TIGER", dataStr, currentBalance, flowDesc, sendBalance)
                    }
                    "ANDAR_BAHAR" -> {
                        val recentList = abDao.getRecentRounds(30)
                        recentAbRoundsList.value = recentList
                        if (recentList.isEmpty()) {
                            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
                            isGeminiLoading.value = false
                            return@launch
                        }
                        val dataStr = recentList.take(20).joinToString(", ") { it.result }
                        val abResult = com.example.data.AndarBaharAnalyzer.analyze(recentList)
                        com.example.api.GeminiClient.analyzeGame(key, "ANDAR_BAHAR", dataStr, currentBalance, abResult.trendLabel, sendBalance)
                    }
                    "SEVEN_UP_DOWN" -> {
                        val recentList = sevenDao.getRecentRounds(30)
                        recentSevenRoundsList.value = recentList
                        if (recentList.isEmpty()) {
                            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
                            isGeminiLoading.value = false
                            return@launch
                        }
                        val dataStr = recentList.take(20).joinToString(", ") { it.result }
                        val sevenResult = com.example.data.SevenUpDownAnalyzer.analyze(recentList)
                        com.example.api.GeminiClient.analyzeGame(key, "SEVEN_UP_DOWN", dataStr, currentBalance, sevenResult.trendLabel, sendBalance)
                    }
                    "ROULETTE" -> {
                        val recentList = rouletteDao.getRecentRounds(30)
                        recentRouletteRoundsList.value = recentList
                        if (recentList.isEmpty()) {
                            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
                            isGeminiLoading.value = false
                            return@launch
                        }
                        val roulResult = com.example.data.RouletteAnalyzer.analyze(recentList)
                        val dataStr = recentList.take(20).joinToString(", ") { "${it.result}(${it.color})" }
                        val flowDesc = "Red: ${roulResult.redPct}%, Black: ${roulResult.blackPct}%, Even: ${roulResult.evenPct}%, Odd: ${roulResult.oddPct}%, Streak: ${roulResult.currentStreak}"
                        com.example.api.GeminiClient.analyzeGame(key, "ROULETTE", dataStr, currentBalance, flowDesc, sendBalance)
                    }
                    else -> {
                        val recentList = repository.getRecentLimit(15).map { it.multiplier }
                        recentMultipliersList.value = recentList
                        if (recentList.isEmpty()) {
                            geminiAiAdvice.value = "🔄 Session Reset!\nAI has forgotten previous history. Please log new rounds to build a fresh trend pattern."
                            isGeminiLoading.value = false
                            return@launch
                        }
                        val multipliersStr = recentList.take(10).joinToString(", ") { "${it}x" }
                        val trend = calculateLiveMetrics(recentList).trend
                        com.example.api.GeminiClient.analyzeGame(key, game, multipliersStr, currentBalance, trend, sendBalance)
                    }
                }

                if (adviceResult.contains("Gemini API Key is missing")) {
                    geminiAiAdvice.value = "⚠️ Gemini API Key is missing. Please enter your Gemini API Key in the dashboard."
                } else if (adviceResult.contains("API Server connection issue")) {
                    val errorDetail = adviceResult.replace("API Server connection issue:", "").trim()
                    geminiAiAdvice.value = "⚠️ API Error: $errorDetail"
                } else {
                    geminiAiAdvice.value = adviceResult
                }
            } catch (e: Exception) {
                geminiAiAdvice.value = "⚠️ Connection error or invalid API key. Please check dashboard."
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
            val hasGemini = geminiApiKey.value.isNotBlank() && geminiAiAdvice.value.isNotBlank()
            val source = if (hasGemini) "AI" else "LOCAL"
            
            val localTarget = calculateLiveMetrics(recentMultipliersList.value).cashout
            val target = if (source == "AI") {
                parseCrashAiTarget(geminiAiAdvice.value) ?: localTarget
            } else {
                localTarget
            }

            val isWinVal = multiplier >= target

            var profit = 0.0
            if (betSize > 0.0) {
                if (isWinVal) {
                    profit = betSize * (target - 1.0)
                } else {
                    profit = -betSize
                }
            }

            val round = CrashRound(
                multiplier = multiplier,
                betAmount = betSize,
                cashOutMultiplier = if (isWinVal) target else 0.0,
                profitLoss = profit,
                prediction = "${String.format("%.2f", target)}x",
                predictionSource = source,
                isWin = isWinVal
            )
            repository.insert(round)
        }
    }

    private fun processOcrSuccess(visionText: com.google.mlkit.vision.text.Text) {
        if (overlayGame.value == "DRAGON_TIGER") {
            val textLower = visionText.text.lowercase()
            var dtResultFound: String? = null
            when {
                textLower.contains("dragon wins") || textLower.contains("dragon winner") || textLower.contains("dragon wins!") || textLower.contains("dragon won") -> {
                    dtResultFound = "D"
                }
                textLower.contains("tiger wins") || textLower.contains("tiger winner") || textLower.contains("tiger wins!") || textLower.contains("tiger won") -> {
                    dtResultFound = "T"
                }
                textLower.contains("tie wins") || textLower.contains("tie winner") || textLower.contains("tie wins!") -> {
                    dtResultFound = "X"
                }
                textLower.contains("dragon") && (textLower.contains("win") || textLower.contains("won")) -> {
                    dtResultFound = "D"
                }
                textLower.contains("tiger") && (textLower.contains("win") || textLower.contains("won")) -> {
                    dtResultFound = "T"
                }
            }
            
            if (dtResultFound != null) {
                serviceScope.launch(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    if (now - lastDtOcrLogTime > 6000L) {
                        lastDtOcrLogTime = now
                        addDragonTigerRoundResult(dtResultFound)
                    }
                }
            }
        } else {
            // Parse Crash/Aviator Game decimal multipliers
            val decimalRegex = Regex("""\b(\d+([\.,]\d{1,2})?)\s*x?\b""", RegexOption.IGNORE_CASE)
            var bestBlock: com.google.mlkit.vision.text.Text.TextBlock? = null
            var maxArea = 0
            for (block in visionText.textBlocks) {
                val blockText = block.text
                if (decimalRegex.containsMatchIn(blockText)) {
                    val box = block.boundingBox
                    if (box != null) {
                        val area = box.width() * box.height()
                        if (area > maxArea) {
                            maxArea = area
                            bestBlock = block
                        }
                    }
                }
            }

            val targetText = bestBlock?.text ?: visionText.text
            val matches = decimalRegex.findAll(targetText).toList()

            val textLower = visionText.text.lowercase()
            val isCrashEnded = textLower.contains("flew") || 
                    textLower.contains("away") || 
                    textLower.contains("burst") || 
                    textLower.contains("crash") || 
                    textLower.contains("crashed")

            if (matches.isNotEmpty()) {
                val matchedStr = matches.first().groupValues[1].replace(',', '.')
                val matchedVal = matchedStr.toDoubleOrNull()

                if (matchedVal != null && matchedVal >= 1.0 && matchedVal < 1000.0) {
                    serviceScope.launch(Dispatchers.Main) {
                        latestScannedMultiplier.value = String.format("%.2f", matchedVal)
                        val previousPeak = livePeakMultiplier

                        if (matchedVal > previousPeak) {
                            livePeakMultiplier = matchedVal
                            isInsideFlight = true
                            captureLogs.value = "Reading live... Current: ${matchedVal}x"
                        } else if (isInsideFlight && (matchedVal <= 1.05 || matchedVal < previousPeak - 0.15)) {
                            // Sudden drop detected -> previous peak committed safely!
                            captureLogs.value = "CRASH DETECTED (Drop): ${previousPeak}x"
                            checkAndCommitDetectedValue(previousPeak)
                            livePeakMultiplier = matchedVal
                            isInsideFlight = (matchedVal > 1.02)
                        } else if (isCrashEnded) {
                            val commitVal = if (previousPeak > matchedVal) previousPeak else matchedVal
                            captureLogs.value = "CRASH DETECTED (Keyword): ${commitVal}x"
                            checkAndCommitDetectedValue(commitVal)
                            livePeakMultiplier = 1.00
                            isInsideFlight = false
                        } else {
                            captureLogs.value = "Reading live... Current: ${matchedVal}x"
                        }
                    }
                }
            }
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
