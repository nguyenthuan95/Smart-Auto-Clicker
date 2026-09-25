package com.example.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ScriptEntity
import com.example.engine.ExecutionManager
import com.example.engine.ExecutionStatus
import com.example.engine.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FloatingControlService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_FLOATING_CONTROL"
        const val ACTION_STOP = "ACTION_STOP_FLOATING_CONTROL"
        const val ACTION_TOGGLE = "ACTION_TOGGLE_FLOATING_CONTROL"

        const val ACTION_START_BUTTON_1 = "ACTION_START_BUTTON_1"
        const val ACTION_STOP_BUTTON_1 = "ACTION_STOP_BUTTON_1"
        const val ACTION_START_BUTTON_2 = "ACTION_START_BUTTON_2"
        const val ACTION_STOP_BUTTON_2 = "ACTION_STOP_BUTTON_2"

        private const val PREFS_NAME = "floating_bubble_prefs"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "smart_clicker_floating_channel"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isRunningButton1 = false
            private set

        @Volatile
        var isRunningButton2 = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences

    private var buttonController1: FloatingButtonController? = null
    private var buttonController2: FloatingButtonController? = null

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var touchSlop = 16

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        updateScreenMetrics()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Vui lòng cấp quyền Cửa sổ nổi cho Smart Auto Clicker", Toast.LENGTH_LONG).show()
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(overlayIntent)
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_STOP -> {
                removeButton(1)
                removeButton(2)
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                showButton(1)
            }
            ACTION_START_BUTTON_1 -> {
                showButton(1)
            }
            ACTION_STOP_BUTTON_1 -> {
                removeButton(1)
            }
            ACTION_START_BUTTON_2 -> {
                showButton(2)
            }
            ACTION_STOP_BUTTON_2 -> {
                removeButton(2)
            }
            ACTION_TOGGLE -> {
                ExecutionManager.togglePlayPauseForButton(this, 1)
            }
        }

        if (!isRunningButton1 && !isRunningButton2) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        isRunning = true
        return START_STICKY
    }

    private fun showButton(buttonId: Int) {
        if (buttonId == 1) {
            if (buttonController1 == null) {
                buttonController1 = FloatingButtonController(
                    service = this,
                    buttonId = 1,
                    defaultYOffsetRatio = 0.3f,
                    scriptFlow = ExecutionManager.activeScript,
                    labelFlow = ExecutionManager.button1Label,
                    onTogglePlayPause = { ExecutionManager.togglePlayPauseForButton(this, 1) }
                ).apply { show() }
            }
            isRunningButton1 = true
        } else if (buttonId == 2) {
            if (buttonController2 == null) {
                buttonController2 = FloatingButtonController(
                    service = this,
                    buttonId = 2,
                    defaultYOffsetRatio = 0.45f,
                    scriptFlow = ExecutionManager.activeScript2,
                    labelFlow = ExecutionManager.button2Label,
                    onTogglePlayPause = { ExecutionManager.togglePlayPauseForButton(this, 2) }
                ).apply { show() }
            }
            isRunningButton2 = true
        }
    }

    private fun removeButton(buttonId: Int) {
        if (buttonId == 1) {
            buttonController1?.remove()
            buttonController1 = null
            isRunningButton1 = false
        } else if (buttonId == 2) {
            buttonController2?.remove()
            buttonController2 = null
            isRunningButton2 = false
        }
    }

    private fun updateScreenMetrics() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Auto Clicker Nút Nổi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Điều khiển chạy/dừng và theo dõi trạng thái auto click"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val appIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, FloatingControlService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingControlService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nút nổi Smart Auto Clicker đang hoạt động")
            .setContentText("Bấm để mở ứng dụng hoặc chạm nút nổi trên màn hình")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Chạy/Dừng", togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tắt nút nổi", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeButton(1)
        removeButton(2)
        isRunning = false
        isRunningButton1 = false
        isRunningButton2 = false
    }

    /**
     * Controller quản lý cấu trúc giao diện và tương tác tái sử dụng cho từng Nút Nổi độc lập
     */
    inner class FloatingButtonController(
        private val service: FloatingControlService,
        val buttonId: Int,
        private val defaultYOffsetRatio: Float,
        val scriptFlow: StateFlow<ScriptEntity?>,
        val labelFlow: StateFlow<String>,
        val onTogglePlayPause: () -> Unit
    ) {
        private var rootContainer: FrameLayout? = null
        private var bubbleView: FrameLayout? = null
        private var expandedToolbar: LinearLayout? = null
        private var statusBadge: TextView? = null
        private var nameBadge: TextView? = null
        private var playStopIcon: TextView? = null
        private var pauseIcon: TextView? = null
        private var activeScriptLabel: TextView? = null

        private lateinit var windowParams: WindowManager.LayoutParams
        private var syncJob: Job? = null
        private var isExpanded = false
        private var showTimeInsteadOfLoop = false

        @SuppressLint("ClickableViewAccessibility")
        fun show() {
            if (rootContainer != null) return

            service.updateScreenMetrics()
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = service.resources.displayMetrics.density
            val bubbleSizePx = (54 * density).toInt()

            val savedX = prefs.getInt("bubble_x_$buttonId", screenWidth - bubbleSizePx - 20)
            val defaultY = (screenHeight * defaultYOffsetRatio).toInt()
            val savedY = prefs.getInt("bubble_y_$buttonId", defaultY)

            windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX.coerceIn(0, screenWidth - bubbleSizePx)
                y = savedY.coerceIn(50, screenHeight - bubbleSizePx - 100)
            }

            val root = FrameLayout(service)

            // Bong bóng tròn chính
            val bubble = FrameLayout(service).apply {
                layoutParams = FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx)
                background = createBubbleBackground(ExecutionStatus.IDLE)
                elevation = 16f
            }

            // Icon Play / Stop ở giữa
            val iconText = TextView(service).apply {
                text = "▶"
                textSize = 20f
                setTextColor(if (buttonId == 1) Color.parseColor("#00E676") else Color.parseColor("#00B0FF"))
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            bubble.addView(iconText)
            playStopIcon = iconText

            // Huy hiệu Nhãn tên nút nổi (Góc trên bên trái) để phân biệt Nút 1 và Nút 2
            val nameTag = TextView(service).apply {
                text = labelFlow.value
                textSize = 9f
                setTextColor(Color.WHITE)
                setPadding(6, 2, 6, 2)
                val bg = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(if (buttonId == 1) Color.parseColor("#DD0288D1") else Color.parseColor("#DD7B1FA2"))
                    setStroke(1, Color.parseColor("#88FFFFFF"))
                }
                background = bg
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    topMargin = 2
                    leftMargin = 2
                }
                layoutParams = lp
            }
            bubble.addView(nameTag)
            nameBadge = nameTag

            // Huy hiệu đếm số vòng/thời gian ở góc trên bên phải
            val badge = TextView(service).apply {
                text = "0"
                textSize = 9f
                setTextColor(Color.WHITE)
                setPadding(6, 2, 6, 2)
                val badgeBg = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(Color.parseColor("#E6000000"))
                    setStroke(1, Color.parseColor("#55FFFFFF"))
                }
                background = badgeBg
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = 2
                    rightMargin = 2
                }
                layoutParams = lp
                setOnClickListener {
                    showTimeInsteadOfLoop = !showTimeInsteadOfLoop
                    updateBadgeDisplay(ExecutionManager.loopCount.value, ExecutionManager.elapsedSeconds.value)
                }
            }
            bubble.addView(badge)
            statusBadge = badge

            // Thanh mở rộng
            val toolbar = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(10, 6, 10, 6)
                visibility = View.GONE
                val bg = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(Color.parseColor("#EE12141F"))
                    setStroke(2, if (buttonId == 1) Color.parseColor("#0288D1") else Color.parseColor("#7B1FA2"))
                }
                background = bg
                elevation = 18f
            }

            // 1. Nút Tạm dừng
            val btnPause = createToolbarButton("⏸", "#FFD600") {
                ExecutionManager.triggerHaptic(service)
                if (ExecutionManager.executionStatus.value == ExecutionStatus.PAUSED) {
                    ExecutionManager.resume()
                } else if (ExecutionManager.executionStatus.value == ExecutionStatus.RUNNING) {
                    ExecutionManager.pause()
                }
            }
            pauseIcon = btnPause
            toolbar.addView(btnPause)

            // 2. Nhãn hiển thị Script của nút này
            val scriptChip = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
                val chipBg = GradientDrawable().apply {
                    cornerRadius = 12f
                    setColor(Color.parseColor("#331E88E5"))
                    setStroke(1, Color.parseColor("#64B5F6"))
                }
                background = chipBg
            }
            val scriptLabel = TextView(service).apply {
                val cur = scriptFlow.value?.name ?: "Chưa chọn script"
                text = "📜 $cur"
                textSize = 11f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
                maxLines = 1
                maxWidth = (130 * density).toInt()
            }
            activeScriptLabel = scriptLabel
            scriptChip.addView(scriptLabel)
            toolbar.addView(scriptChip)

            // 3. Nút Mở app chính
            toolbar.addView(createToolbarButton("⚙", "#90CAF9") {
                ExecutionManager.triggerHaptic(service)
                val appIntent = Intent(service, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                service.startActivity(appIntent)
                toggleExpandedToolbar(false)
            })

            // 4. Nút Đóng nút nổi này
            toolbar.addView(createToolbarButton("✖", "#EF5350") {
                ExecutionManager.triggerHaptic(service)
                service.removeButton(buttonId)
            })

            val rootLayout = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(bubble)
                addView(toolbar)
            }
            root.addView(rootLayout)

            setupDragAndTouch(bubble, root)

            rootContainer = root
            bubbleView = bubble
            expandedToolbar = toolbar

            service.windowManager.addView(root, windowParams)
            updateFloatingRectBounds()

            ExecutionManager.onTemporarilyHideBubble = { hide ->
                mainHandler.post {
                    rootContainer?.alpha = if (hide) 0f else 1f
                }
            }

            startSync()
        }

        fun remove() {
            syncJob?.cancel()
            rootContainer?.let {
                try {
                    service.windowManager.removeView(it)
                } catch (e: Exception) {
                    // ignore
                }
                rootContainer = null
            }
        }

        private fun createBubbleBackground(status: ExecutionStatus): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E612141F"))
                val strokeColor = when (status) {
                    ExecutionStatus.IDLE -> if (buttonId == 1) Color.parseColor("#00E676") else Color.parseColor("#00B0FF")
                    ExecutionStatus.RUNNING -> Color.parseColor("#FF1744")
                    ExecutionStatus.PAUSED -> Color.parseColor("#FFD600")
                }
                setStroke(5, strokeColor)
            }
        }

        private fun createToolbarButton(symbol: String, colorHex: String, onClick: () -> Unit): TextView {
            return TextView(service).apply {
                text = symbol
                textSize = 14f
                setTextColor(Color.parseColor(colorHex))
                setPadding(12, 10, 12, 10)
                setOnClickListener { onClick() }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setupDragAndTouch(bubble: View, root: View) {
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false
            var lastDownTime = 0L

            bubble.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = windowParams.x
                        initialY = windowParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        lastDownTime = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            isDragging = true
                            windowParams.x = initialX + dx
                            windowParams.y = initialY + dy
                            service.windowManager.updateViewLayout(root, windowParams)
                            updateFloatingRectBounds()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - lastDownTime
                        if (!isDragging && duration < 350) {
                            onTogglePlayPause()
                        } else if (!isDragging && duration >= 350) {
                            ExecutionManager.triggerHaptic(service, true)
                            toggleExpandedToolbar(!isExpanded)
                        } else if (isDragging) {
                            snapToEdge()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        private fun toggleExpandedToolbar(expand: Boolean) {
            isExpanded = expand
            expandedToolbar?.visibility = if (isExpanded) View.VISIBLE else View.GONE
            updateFloatingRectBounds()
        }

        private fun snapToEdge() {
            val root = rootContainer ?: return
            val currentX = windowParams.x
            val bubbleWidth = bubbleView?.width ?: 120
            val targetX = if (currentX + bubbleWidth / 2 < screenWidth / 2) {
                10
            } else {
                screenWidth - bubbleWidth - 10
            }

            val animator = ValueAnimator.ofInt(currentX, targetX).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    windowParams.x = va.animatedValue as Int
                    service.windowManager.updateViewLayout(root, windowParams)
                    updateFloatingRectBounds()
                }
            }
            animator.start()

            prefs.edit()
                .putInt("bubble_x_$buttonId", targetX)
                .putInt("bubble_y_$buttonId", windowParams.y)
                .apply()
        }

        private fun updateFloatingRectBounds() {
            val root = rootContainer ?: return
            val width = root.width.coerceAtLeast(120)
            val height = root.height.coerceAtLeast(120)
            ExecutionManager.floatingBubbleRect = Rect(
                windowParams.x,
                windowParams.y,
                windowParams.x + width,
                windowParams.y + height
            )
        }

        private fun startSync() {
            syncJob?.cancel()
            syncJob = scope.launch {
                launch {
                    ExecutionManager.executionStatus.collectLatest { status ->
                        bubbleView?.background = createBubbleBackground(status)
                        when (status) {
                            ExecutionStatus.IDLE -> {
                                playStopIcon?.text = "▶"
                                playStopIcon?.setTextColor(if (buttonId == 1) Color.parseColor("#00E676") else Color.parseColor("#00B0FF"))
                                pauseIcon?.text = "⏸"
                            }
                            ExecutionStatus.RUNNING -> {
                                playStopIcon?.text = "⏹"
                                playStopIcon?.setTextColor(Color.parseColor("#FF1744"))
                                pauseIcon?.text = "⏸"
                            }
                            ExecutionStatus.PAUSED -> {
                                playStopIcon?.text = "▶"
                                playStopIcon?.setTextColor(Color.parseColor("#FFD600"))
                                pauseIcon?.text = "▶"
                            }
                        }
                    }
                }

                launch {
                    ExecutionManager.loopCount.collectLatest { loops ->
                        updateBadgeDisplay(loops, ExecutionManager.elapsedSeconds.value)
                    }
                }

                launch {
                    ExecutionManager.elapsedSeconds.collectLatest { seconds ->
                        updateBadgeDisplay(ExecutionManager.loopCount.value, seconds)
                    }
                }

                launch {
                    scriptFlow.collectLatest { script ->
                        val name = script?.name ?: "Chưa chọn script"
                        activeScriptLabel?.text = "📜 $name"
                    }
                }

                launch {
                    labelFlow.collectLatest { lbl ->
                        nameBadge?.text = lbl
                    }
                }
            }
        }

        private fun updateBadgeDisplay(loops: Int, seconds: Long) {
            if (showTimeInsteadOfLoop) {
                val min = seconds / 60
                val sec = seconds % 60
                statusBadge?.text = String.format("%02d:%02d", min, sec)
            } else {
                statusBadge?.text = if (loops <= 0) "0" else "x$loops"
            }
        }
    }
}
