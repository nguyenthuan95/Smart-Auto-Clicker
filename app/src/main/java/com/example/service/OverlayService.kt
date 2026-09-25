package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.example.SmartClickerApp
import com.example.data.model.RoiRegion
import com.example.data.model.TargetPoint
import com.example.engine.ExecutionManager
import com.example.engine.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "ACTION_SHOW"
        const val ACTION_HIDE = "ACTION_HIDE"

        @Volatile
        var isOverlayShowing = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var pointPickerOverlay: View? = null
    private var roiPickerOverlay: View? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private var statusCollectorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                hideOverlay()
                stopSelf()
            }
            else -> {
                showOverlay()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (floatingView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        // Tạo container UI thanh điều khiển nổi
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#EE1E1E2E")) // Giao diện tối mờ thanh lịch
            elevation = 16f
        }

        // Bo góc viền bằng drawable gradient/shape
        val shape = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 24f
            setColor(Color.parseColor("#EE1E1E2E"))
            setStroke(2, Color.parseColor("#4488EE"))
        }
        root.background = shape

        // Nút Start / Stop
        val btnPlayStop = TextView(this).apply {
            text = "▶ Bắt đầu"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(20, 14, 20, 14)
            val btnBg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#2E7D32"))
            }
            background = btnBg
            setOnClickListener {
                if (ExecutionManager.isRunning.value) {
                    ExecutionManager.stop()
                } else {
                    ExecutionManager.startActiveScript(this@OverlayService)
                }
            }
        }

        // Nút Chọn Điểm
        val btnPickPoint = TextView(this).apply {
            text = "📍 Điểm"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(16, 14, 16, 14)
            setOnClickListener {
                showPointPickerOverlay()
            }
        }

        // Nút Chọn Vùng ROI
        val btnPickRoi = TextView(this).apply {
            text = "🔲 Vùng OCR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(16, 14, 16, 14)
            setOnClickListener {
                showRoiPickerOverlay()
            }
        }

        // Nút Mở App chính
        val btnOpenApp = TextView(this).apply {
            text = "⚙ App"
            textSize = 12f
            setTextColor(Color.parseColor("#90CAF9"))
            setPadding(16, 14, 16, 14)
            setOnClickListener {
                val appIntent = Intent(this@OverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(appIntent)
            }
        }

        // Nút Thu nhỏ / Đóng
        val btnClose = TextView(this).apply {
            text = "✖"
            textSize = 13f
            setTextColor(Color.parseColor("#EF5350"))
            setPadding(16, 14, 16, 14)
            setOnClickListener {
                hideOverlay()
                stopSelf()
            }
        }

        root.addView(btnPlayStop)
        root.addView(btnPickPoint)
        root.addView(btnPickRoi)
        root.addView(btnOpenApp)
        root.addView(btnClose)

        // Hỗ trợ kéo di chuyển thanh công cụ trên màn hình
        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        return false // Cho phép click vào các nút con
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMoving = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(root, params)
                            return true
                        }
                    }
                }
                return false
            }
        })

        floatingView = root
        windowManager.addView(root, params)
        isOverlayShowing = true

        // Cập nhật giao diện nút Start/Stop theo trạng thái chạy
        statusCollectorJob = scope.launch {
            ExecutionManager.isRunning.collectLatest { running ->
                val bg = btnPlayStop.background as? android.graphics.drawable.GradientDrawable
                if (running) {
                    btnPlayStop.text = "⏹ Dừng"
                    bg?.setColor(Color.parseColor("#C62828")) // Đỏ
                } else {
                    btnPlayStop.text = "▶ Bắt đầu"
                    bg?.setColor(Color.parseColor("#2E7D32")) // Xanh lá
                }
            }
        }
    }

    /**
     * Màn hình toàn diện trong suốt để người dùng chạm và lưu tọa độ điểm (X, Y)
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showPointPickerOverlay() {
        if (pointPickerOverlay != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val picker = object : View(this) {
            var touchedX = -1f
            var touchedY = -1f
            val paint = Paint().apply {
                color = Color.parseColor("#00E5FF")
                strokeWidth = 4f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 36f
                isAntiAlias = true
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                // Lớp phủ tối mờ chỉ dẫn
                canvas.drawColor(Color.parseColor("#55000000"))
                canvas.drawText("Chạm vào bất kỳ điểm nào trên màn hình để chọn tọa độ", 50f, 150f, textPaint)

                if (touchedX >= 0 && touchedY >= 0) {
                    // Vẽ tâm ngắm
                    canvas.drawCircle(touchedX, touchedY, 40f, paint)
                    canvas.drawLine(touchedX - 60f, touchedY, touchedX + 60f, touchedY, paint)
                    canvas.drawLine(touchedX, touchedY - 60f, touchedX, touchedY + 60f, paint)
                    canvas.drawText("X: ${touchedX.toInt()}, Y: ${touchedY.toInt()}", touchedX + 50f, touchedY - 20f, textPaint)
                }
            }
        }

        picker.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.rawX
                val y = event.rawY
                picker.touchedX = x
                picker.touchedY = y
                picker.invalidate()

                // Lưu điểm vào database
                savePickedPoint(x, y)
                Toast.makeText(this, "Đã lưu điểm: (${x.toInt()}, ${y.toInt()})", Toast.LENGTH_SHORT).show()

                // Tự động đóng overlay sau 400ms
                Handler(Looper.getMainLooper()).postDelayed({
                    removePointPicker()
                }, 400)
                true
            } else {
                true
            }
        }

        pointPickerOverlay = picker
        windowManager.addView(picker, params)
    }

    private fun removePointPicker() {
        pointPickerOverlay?.let {
            windowManager.removeView(it)
            pointPickerOverlay = null
        }
    }

    /**
     * Màn hình kéo chọn hình chữ nhật để xác định vùng quan tâm OCR (ROI)
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showRoiPickerOverlay() {
        if (roiPickerOverlay != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val roiView = object : View(this) {
            var startX = 0f
            var startY = 0f
            var currentX = 0f
            var currentY = 0f
            var isDrawing = false

            val boxPaint = Paint().apply {
                color = Color.parseColor("#00E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 5f
                isAntiAlias = true
            }
            val fillPaint = Paint().apply {
                color = Color.parseColor("#3300E5FF")
                style = Paint.Style.FILL
            }
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 34f
                isAntiAlias = true
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawColor(Color.parseColor("#66000000"))
                canvas.drawText("Kéo ngón tay để vẽ khung vùng OCR (ROI) cần quét", 50f, 150f, textPaint)

                if (isDrawing || (startX != currentX && startY != currentY)) {
                    val rect = RectF(
                        Math.min(startX, currentX),
                        Math.min(startY, currentY),
                        Math.max(startX, currentX),
                        Math.max(startY, currentY)
                    )
                    canvas.drawRect(rect, fillPaint)
                    canvas.drawRect(rect, boxPaint)
                    val info = "W: ${rect.width().toInt()} x H: ${rect.height().toInt()}"
                    canvas.drawText(info, rect.left, rect.top - 15f, textPaint)
                }
            }
        }

        roiView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    roiView.startX = event.rawX
                    roiView.startY = event.rawY
                    roiView.currentX = event.rawX
                    roiView.currentY = event.rawY
                    roiView.isDrawing = true
                    roiView.invalidate()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    roiView.currentX = event.rawX
                    roiView.currentY = event.rawY
                    roiView.invalidate()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    roiView.currentX = event.rawX
                    roiView.currentY = event.rawY
                    roiView.isDrawing = false
                    roiView.invalidate()

                    val rx = Math.min(roiView.startX, roiView.currentX)
                    val ry = Math.min(roiView.startY, roiView.currentY)
                    val rw = Math.abs(roiView.currentX - roiView.startX)
                    val rh = Math.abs(roiView.currentY - roiView.startY)

                    if (rw > 30 && rh > 30) {
                        savePickedRoi(rx, ry, rw, rh)
                        Toast.makeText(this, "Đã lưu vùng OCR: ${rw.toInt()}x${rh.toInt()}", Toast.LENGTH_SHORT).show()
                    }

                    Handler(Looper.getMainLooper()).postDelayed({
                        removeRoiPicker()
                    }, 400)
                    true
                }
                else -> false
            }
        }

        roiPickerOverlay = roiView
        windowManager.addView(roiView, params)
    }

    private fun removeRoiPicker() {
        roiPickerOverlay?.let {
            windowManager.removeView(it)
            roiPickerOverlay = null
        }
    }

    private fun savePickedPoint(x: Float, y: Float) {
        val app = application as? SmartClickerApp ?: return
        val count = System.currentTimeMillis() % 10000
        val point = TargetPoint(
            name = "Điểm #$count",
            x = x,
            y = y,
            description = "Tọa độ: (${x.toInt()}, ${y.toInt()})"
        )
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.savePoint(point)
            LogRepository.info("Overlay", "Đã lưu điểm: ${point.name} tại ($x, $y)")
        }
    }

    private fun savePickedRoi(x: Float, y: Float, w: Float, h: Float) {
        val app = application as? SmartClickerApp ?: return
        val count = System.currentTimeMillis() % 10000
        val roi = RoiRegion(
            name = "Vùng OCR #$count",
            x = x,
            y = y,
            width = w,
            height = h
        )
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.saveRoi(roi)
            LogRepository.info("Overlay", "Đã lưu vùng ROI: ${roi.name} ($w x $h)")
        }
    }

    private fun hideOverlay() {
        removePointPicker()
        removeRoiPicker()
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
        }
        statusCollectorJob?.cancel()
        isOverlayShowing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
}
