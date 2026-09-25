package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.engine.ExecutionManager
import com.example.engine.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SmartAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SmartAccessibilityService? = null
            private set

        @Volatile
        var currentPackageName: String = ""
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        LogRepository.info("Accessibility", "Đã kết nối dịch vụ Accessibility thành công.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()?.trim()
            if (!pkg.isNullOrEmpty() && pkg != "com.android.systemui") {
                currentPackageName = pkg
            }
        }
    }

    override fun onInterrupt() {
        LogRepository.warn("Accessibility", "Dịch vụ Accessibility bị gián đoạn.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceConnected.value = false
        LogRepository.warn("Accessibility", "Dịch vụ Accessibility đã bị hủy.")
    }

    /**
     * Lắng nghe phím âm lượng để làm Hotkey Start / Stop nhanh
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    // Hotkey bật / tắt nhanh
                    if (ExecutionManager.isRunning.value) {
                        LogRepository.action("Hotkey", "Nhận phím âm lượng -> DỪNG tác vụ ngay lập tức.")
                        ExecutionManager.stop()
                        return true // nuốt sự kiện để không đổi âm lượng khi khẩn cấp
                    }
                }
            }
        }
        return super.onKeyEvent(event)
    }

    /**
     * Chạm đơn tại tọa độ (x, y)
     */
    suspend fun click(x: Float, y: Float, durationMs: Long = 60L): Boolean {
        return suspendCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(10L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }, null)

            if (!dispatched) {
                continuation.resume(false)
            }
        }
    }

    /**
     * Chạm đúp (2 lần liên tiếp cách nhau intervalMs ~50-150ms)
     */
    suspend fun doubleClick(x: Float, y: Float, intervalMs: Long = 80L): Boolean {
        val first = click(x, y, 50L)
        if (!first) return false
        kotlinx.coroutines.delay(intervalMs.coerceIn(50L, 200L))
        return click(x, y, 50L)
    }

    /**
     * Nhấn giữ tại tọa độ (x, y) với thời lượng durationMs (mặc định 600ms)
     */
    suspend fun longPress(x: Float, y: Float, durationMs: Long = 600L): Boolean {
        return click(x, y, durationMs)
    }

    /**
     * Vuốt / Kéo thả từ (x1, y1) đến (x2, y2)
     */
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350L): Boolean {
        return suspendCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }, null)

            if (!dispatched) {
                continuation.resume(false)
            }
        }
    }

    /**
     * Dán văn bản: Chạm vào ô nhập để focus rồi thực hiện ACTION_PASTE hoặc ACTION_SET_TEXT
     */
    suspend fun pasteText(targetX: Float? = null, targetY: Float? = null, customText: String? = null): Boolean {
        // Nếu có tọa độ, click trước để kích hoạt focus vào ô nhập
        if (targetX != null && targetY != null) {
            click(targetX, targetY)
            kotlinx.coroutines.delay(350)
        }

        // Tìm node đang được focus
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        val textToInsert = if (!customText.isNullOrEmpty()) {
            customText
        } else {
            // Lấy từ ClipboardManager
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            item?.text?.toString() ?: ""
        }

        if (focusedNode != null) {
            // Thử ACTION_SET_TEXT trước
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToInsert)
            }
            val setTextSuccess = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (setTextSuccess) {
                LogRepository.action("Paste", "Đã dán văn bản thành công bằng ACTION_SET_TEXT.")
                return true
            }

            // Fallback: Nếu không được, đặt lại clipboard rồi thực hiện ACTION_PASTE
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("smart_clicker_text", textToInsert)
            clipboard.setPrimaryClip(clip)

            val pasteSuccess = focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasteSuccess) {
                LogRepository.action("Paste", "Đã dán văn bản thành công bằng ACTION_PASTE.")
                return true
            }
        }

        LogRepository.warn("Paste", "Không tìm thấy ô nhập đang focus hoặc ứng dụng chặn dán tự động.")
        return false
    }

    /**
     * Thực hiện điều hướng về màn hình chính (Home)
     */
    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Thực hiện điều hướng quay lại (Back)
     */
    fun back(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Lấy package name của ứng dụng đang hiển thị ở foreground.
     *
     * 1. Ưu tiên rootInActiveWindow (đọc đồng bộ, phản ánh trạng thái window hiện tại).
     * 2. Nếu rootInActiveWindow null (đang chuyển cảnh), duyệt danh sách windows tìm window TYPE_APPLICATION.
     * 3. Fallback về currentPackageName (cập nhật từ event và các lần đọc hợp lệ gần nhất).
     */
    fun getForegroundPackage(): String {
        // 1. Đọc rootInActiveWindow trước — nguồn đồng bộ
        try {
            val rootPkg = rootInActiveWindow?.packageName?.toString()?.trim()
            if (!rootPkg.isNullOrEmpty() && rootPkg != "com.android.systemui") {
                currentPackageName = rootPkg
                return rootPkg
            }
        } catch (e: Exception) {
            // ignore
        }

        // 2. Duyệt danh sách interactive windows để tìm cửa sổ TYPE_APPLICATION
        try {
            val appWindow = windows?.firstOrNull {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
                        (it.isFocused || it.isActive)
            } ?: windows?.firstOrNull {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
            }
            val winPkg = appWindow?.root?.packageName?.toString()?.trim()
            if (!winPkg.isNullOrEmpty() && winPkg != "com.android.systemui") {
                currentPackageName = winPkg
                return winPkg
            }
        } catch (e: Exception) {
            // ignore
        }

        // 3. Fallback: giá trị gần nhất được ghi nhận
        return currentPackageName.trim()
    }
}
