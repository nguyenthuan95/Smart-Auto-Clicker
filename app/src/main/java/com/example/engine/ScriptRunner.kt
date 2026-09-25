package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.service.ScreenCaptureService
import com.example.service.SmartAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import kotlin.random.Random

/**
 * ContextFactory tùy biến quan sát số lệnh thực thi trong Rhino để ngắt vòng lặp while ngay lập tức khi dừng
 */
class CancellableContextFactory : ContextFactory() {
    override fun makeContext(): RhinoContext {
        val cx = super.makeContext()
        cx.instructionObserverThreshold = 50 // Giám sát mỗi 50 lệnh bytecode
        return cx
    }

    override fun observeInstructionCount(cx: RhinoContext, instructionCount: Int) {
        if (ExecutionManager.executionStatus.value == ExecutionStatus.IDLE) {
            throw RuntimeException("Script bị dừng bởi người dùng.")
        }
        super.observeInstructionCount(cx, instructionCount)
    }
}

class ScriptRunner(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun execute(scriptSource: String) {
        val factory = CancellableContextFactory()
        val rhino = factory.enterContext()
        var currentApi: ScriptApi? = null
        try {
            rhino.optimizationLevel = -1 // Thông dịch trực tiếp trên Android VM
            val scope: Scriptable = rhino.initStandardObjects()

            // Tạo Host API object
            val api = ScriptApi(context, rhino, scope)
            currentApi = api
            ScriptableObject.putProperty(scope, "__api", api)

            // Khởi tạo các hàm API toàn cục cho JS
            val setupJs = """
                function ocr(region) { return __api.ocr(region || null); }
                function scanText(region) { return ocr(region); }
                function findText(pattern, region) { return __api.findText(pattern, region || null); }
                function click(x, y, ms) { return __api.click(x, y, ms === undefined ? 80 : ms); }
                function doubleClick(x, y) { return __api.doubleClick(x, y); }
                function longPress(x, y, ms) { return __api.longPress(x, y, ms || 600); }
                function swipe(x1, y1, x2, y2, ms) { return __api.swipe(x1, y1, x2, y2, ms || 350); }
                function pasteClipboard(x, y, retries, retryDelayMs) { return __api.pasteClipboard(x || null, y || null, retries === undefined ? 3 : retries, retryDelayMs === undefined ? 150 : retryDelayMs); }
                function setClipboard(text) { return __api.setClipboard(text); }
                function getClipboard() { return __api.getClipboard(); }
                function sleep(ms) { return __api.sleep(ms); }
                function log(msg) { return __api.log(msg); }
                function toast(msg) { return __api.toast(msg); }
                function stop() { return __api.stop(); }
                function pause() { return __api.pause(); }
                function resume() { return __api.resume(); }
                function screenSize() { return __api.screenSize(); }
                function getPixelColor(x, y) { return __api.getPixelColor(x, y); }
                function openApp(packageName, waitMs) { return __api.openApp(packageName, waitMs === undefined ? 3000 : waitMs); }
                function openUrl(uriString) { return __api.openUrl(uriString); }
                function goHome(ms) { return __api.goHome(ms || 250); }
                function back(ms) { return __api.back(ms || 200); }
                function getForegroundPackage() { return String(__api.getForegroundPackage() || "").trim(); }
                function tapText(pattern, region, ms) { return __api.tapText(pattern, region || null, ms === undefined ? 80 : ms); }
                function markStep(stepName) { return __api.markStep(stepName); }
                function waitUntil(conditionFn, timeoutMs, intervalMs) { return __api.waitUntil(conditionFn, timeoutMs || 10000, intervalMs || 1000); }
                function waitForText(pattern, timeoutMs, region) { return __api.waitForText(pattern, timeoutMs || 10000, region || null); }
            """.trimIndent()

            rhino.evaluateString(scope, setupJs, "init_api.js", 1, null)

            // Chạy code người dùng
            rhino.evaluateString(scope, scriptSource, "user_script.js", 1, null)

        } catch (e: Exception) {
            LogRepository.error("Script", "Lỗi thực thi Script: ${e.message}")
            throw e
        } finally {
            try {
                // In bảng tổng kết mốc thời gian các bước
                currentApi?.printStepSummary()
            } catch (e: Exception) {
                // ignore
            }
            RhinoContext.exit()
        }
    }

    /**
     * Lớp cầu nối các hàm Android sang JavaScript Runtime
     */
    inner class ScriptApi(
        private val ctx: Context,
        private val rhino: RhinoContext,
        private val scope: Scriptable
    ) {

        private fun checkCancelled() {
            if (ExecutionManager.executionStatus.value == ExecutionStatus.IDLE) {
                throw RuntimeException("Script bị dừng bởi người dùng.")
            }
            while (ExecutionManager.executionStatus.value == ExecutionStatus.PAUSED) {
                try {
                    Thread.sleep(100L)
                } catch (e: InterruptedException) {
                    throw RuntimeException("Script bị dừng bởi người dùng.")
                }
                if (ExecutionManager.executionStatus.value == ExecutionStatus.IDLE) {
                    throw RuntimeException("Script bị dừng bởi người dùng.")
                }
            }
        }

        fun log(msg: Any?) {
            LogRepository.info("Script", msg?.toString() ?: "null")
        }

        fun toast(msg: Any?) {
            val text = msg?.toString() ?: ""
            mainHandler.post {
                Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
            }
        }

        fun stop() {
            ExecutionManager.stop()
            throw RuntimeException("Script gọi lệnh stop().")
        }

        fun pause() {
            ExecutionManager.pause()
        }

        fun resume() {
            ExecutionManager.resume()
        }

        fun sleep(ms: Any?) {
            val millis = when (val res = msgToLong(ms)) {
                is Long -> res
                else -> 500L
            }
            val step = 50L
            var elapsed = 0L
            while (elapsed < millis) {
                checkCancelled()
                try {
                    Thread.sleep(step.coerceAtMost(millis - elapsed))
                } catch (e: InterruptedException) {
                    throw RuntimeException("Script bị dừng bởi người dùng.")
                }
                elapsed += step
            }
        }

        private fun msgToLong(obj: Any?): Long {
            return when (obj) {
                is Number -> obj.toLong()
                is String -> obj.toLongOrNull() ?: 500L
                else -> 500L
            }
        }

        fun screenSize(): NativeObject {
            val dims = ScreenCaptureService.instance?.getScreenDimensions() ?: Pair(1080, 2400)
            val obj = rhino.newObject(scope) as NativeObject
            obj.put("width", obj, dims.first)
            obj.put("height", obj, dims.second)
            return obj
        }

        fun getPixelColor(x: Any?, y: Any?): String {
            checkCancelled()
            val px = (x as? Number)?.toInt() ?: 0
            val py = (y as? Number)?.toInt() ?: 0

            val bitmap = ScreenCaptureService.instance?.captureCurrentFrame()
                ?: return "#000000"

            val colorHex = try {
                if (px in 0 until bitmap.width && py in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(px, py)
                    String.format("#%06X", (0xFFFFFF and pixel))
                } else {
                    "#000000"
                }
            } finally {
                bitmap.recycle()
            }
            return colorHex
        }

        fun ocr(regionObj: Any?): NativeArray {
            checkCancelled()
            val service = ScreenCaptureService.instance
            if (service == null) {
                LogRepository.warn("OCR", "Chưa bật chụp màn hình MediaProjection!")
                return NativeArray(0)
            }

            val bitmap = service.captureCurrentFrame()
            if (bitmap == null) {
                LogRepository.warn("OCR", "Không lấy được ảnh màn hình.")
                return NativeArray(0)
            }

            val rect = parseRegion(regionObj)

            val elements = runBlocking {
                try {
                    OcrEngine.recognizeText(bitmap, rect)
                } finally {
                    bitmap.recycle()
                }
            }

            val list = elements.map { el ->
                val obj = rhino.newObject(scope) as NativeObject
                obj.put("text", obj, el.text)
                obj.put("x", obj, el.x)
                obj.put("y", obj, el.y)
                obj.put("w", obj, el.width)
                obj.put("h", obj, el.height)
                obj.put("conf", obj, el.confidence)
                obj.put("centerX", obj, el.centerX)
                obj.put("centerY", obj, el.centerY)
                obj
            }.toTypedArray()

            return NativeArray(list)
        }

        fun findText(pattern: Any?, regionObj: Any?): NativeObject? {
            checkCancelled()
            if (pattern == null) return null
            val patternStr = pattern.toString()
            if (patternStr.isBlank()) return null

            val service = ScreenCaptureService.instance
            if (service == null) {
                LogRepository.warn("OCR", "MediaProjection chưa hoạt động.")
                return null
            }

            val bitmap = service.captureCurrentFrame() ?: return null
            val rect = parseRegion(regionObj)

            val elements = runBlocking {
                try {
                    OcrEngine.recognizeText(bitmap, rect)
                } finally {
                    bitmap.recycle()
                }
            }

            // Hỗ trợ cả String (chứa một phần, không phân biệt hoa thường) và RegExp
            val regexMatch = Regex("^/(.+)/([a-z]*)$").matchEntire(patternStr)
            val matched = if (regexMatch != null || pattern.javaClass.name.contains("RegExp")) {
                val regexPattern = regexMatch?.groupValues?.get(1) ?: patternStr
                val flags = regexMatch?.groupValues?.get(2) ?: ""
                val caseSensitive = !flags.contains("i")
                OcrEngine.findMatchingElement(
                    elements = elements,
                    patternString = regexPattern,
                    matchType = MatchType.REGEX,
                    caseSensitive = caseSensitive
                )
            } else {
                OcrEngine.findMatchingElement(
                    elements = elements,
                    patternString = patternStr,
                    matchType = MatchType.CONTAINS,
                    caseSensitive = false
                )
            } ?: return null

            val obj = rhino.newObject(scope) as NativeObject
            obj.put("text", obj, matched.text)
            obj.put("x", obj, matched.x)
            obj.put("y", obj, matched.y)
            obj.put("w", obj, matched.width)
            obj.put("h", obj, matched.height)
            obj.put("centerX", obj, matched.centerX)
            obj.put("centerY", obj, matched.centerY)
            obj.put("conf", obj, matched.confidence)
            return obj
        }

        fun click(x: Any?, y: Any?, ms: Any? = null): Boolean {
            checkCancelled()
            val service = SmartAccessibilityService.instance
            if (service == null) {
                LogRepository.error("Gesture", "Dịch vụ Accessibility chưa được bật!")
                return false
            }

            val rawX = (x as? Number)?.toFloat() ?: 0f
            val rawY = (y as? Number)?.toFloat() ?: 0f

            if (ExecutionManager.isPointInsideFloatingBubble(rawX, rawY)) {
                LogRepository.warn("Click", "Tọa độ ($rawX, $rawY) trùng với nút nổi. Đã tự động chặn để tránh click nhầm vào nút nổi!")
                return false
            }

            // Thêm độ lệch jitter ngẫu nhiên nhỏ +/- 2px
            val jitterX = rawX + Random.nextFloat() * 4f - 2f
            val jitterY = rawY + Random.nextFloat() * 4f - 2f

            LogRepository.action("Click", "Chạm tại ($rawX, $rawY)")
            val result = runBlocking {
                service.click(jitterX, jitterY)
            }
            // Delay sau gesture để UI ổn định (mặc định 80ms, tắt bằng click(x,y,0))
            val delayMs = (ms as? Number)?.toLong() ?: 80L
            if (delayMs > 0) sleep(delayMs)
            return result
        }

        fun doubleClick(x: Any?, y: Any?): Boolean {
            checkCancelled()
            val service = SmartAccessibilityService.instance ?: return false
            val fx = (x as? Number)?.toFloat() ?: 0f
            val fy = (y as? Number)?.toFloat() ?: 0f
            LogRepository.action("Click", "Chạm đúp tại ($fx, $fy)")
            return runBlocking {
                service.doubleClick(fx, fy)
            }
        }

        fun longPress(x: Any?, y: Any?, ms: Any?): Boolean {
            checkCancelled()
            val service = SmartAccessibilityService.instance ?: return false
            val fx = (x as? Number)?.toFloat() ?: 0f
            val fy = (y as? Number)?.toFloat() ?: 0f
            val dur = (ms as? Number)?.toLong() ?: 600L
            LogRepository.action("Click", "Nhấn giữ tại ($fx, $fy) trong ${dur}ms")
            return runBlocking {
                service.longPress(fx, fy, dur)
            }
        }

        fun swipe(x1: Any?, y1: Any?, x2: Any?, y2: Any?, ms: Any?): Boolean {
            checkCancelled()
            val service = SmartAccessibilityService.instance ?: return false
            val sx1 = (x1 as? Number)?.toFloat() ?: 0f
            val sy1 = (y1 as? Number)?.toFloat() ?: 0f
            val sx2 = (x2 as? Number)?.toFloat() ?: 0f
            val sy2 = (y2 as? Number)?.toFloat() ?: 0f
            val dur = (ms as? Number)?.toLong() ?: 350L
            LogRepository.action("Gesture", "Vuốt từ ($sx1, $sy1) đến ($sx2, $sy2) trong ${dur}ms")
            return runBlocking {
                service.swipe(sx1, sy1, sx2, sy2, dur)
            }
        }

        fun pasteClipboard(x: Any?, y: Any?, retries: Any? = null, retryDelayMs: Any? = null): Boolean {
            checkCancelled()
            val service = SmartAccessibilityService.instance ?: return false
            val fx = (x as? Number)?.toFloat()
            val fy = (y as? Number)?.toFloat()
            val maxRetries = (retries as? Number)?.toInt()?.coerceAtLeast(1) ?: 3
            val retryDelay = (retryDelayMs as? Number)?.toLong()?.coerceAtLeast(50L) ?: 150L
            LogRepository.action("Clipboard", "Dán clipboard tại vị trí: $fx, $fy")
            // Thử nhiều lần: click có thể chưa kịp đưa focus vào ô nhập
            for (attempt in 1..maxRetries) {
                val ok = runBlocking { service.pasteText(fx, fy, null) }
                if (ok) return true
                if (attempt < maxRetries) {
                    LogRepository.warn("Clipboard", "Lần thử $attempt/$maxRetries thất bại, thử lại sau ${retryDelay}ms...")
                    sleep(retryDelay)
                }
            }
            LogRepository.error("Clipboard", "pasteClipboard thất bại sau $maxRetries lần thử.")
            return false
        }

        fun setClipboard(text: Any?) {
            checkCancelled()
            val str = text?.toString() ?: ""
            mainHandler.post {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("smart_clicker_clip", str)
                clipboard.setPrimaryClip(clip)
            }
        }

        fun getClipboard(): String {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            return item?.text?.toString() ?: ""
        }

        fun openApp(packageName: Any?, waitMs: Any? = null): Boolean {
            checkCancelled()
            val pkg = packageName?.toString()?.trim() ?: return false
            if (pkg.isEmpty()) return false
            val maxWaitMs = (waitMs as? Number)?.toLong()?.coerceAtLeast(0L) ?: 3000L
            return try {
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    ctx.startActivity(intent)
                    LogRepository.action("App", "Mở ứng dụng: $pkg")

                    // Đợi app thực sự lên foreground (polling)
                    if (maxWaitMs > 0) {
                        val intervalMs = 300L
                        val startTime = System.currentTimeMillis()
                        var appeared = false
                        while (System.currentTimeMillis() - startTime < maxWaitMs) {
                            checkCancelled()
                            Thread.sleep(intervalMs)
                            val fg = SmartAccessibilityService.instance?.getForegroundPackage() ?: ""
                            if (fg == pkg) {
                                val elapsed = System.currentTimeMillis() - startTime
                                LogRepository.info("App", "✓ '$pkg' đã lên foreground sau ${elapsed}ms")
                                appeared = true
                                break
                            }
                        }
                        if (!appeared) {
                            LogRepository.warn("App", "⚠ '$pkg' chưa lên foreground sau ${maxWaitMs}ms (startActivity đã gọi).")
                        }
                    }
                    true
                } else {
                    LogRepository.warn("App", "Không tìm thấy ứng dụng với package: $pkg")
                    false
                }
            } catch (e: Exception) {
                LogRepository.error("App", "Lỗi mở ứng dụng $pkg: ${e.message}")
                false
            }
        }

        fun openUrl(uriString: Any?): Boolean {
            checkCancelled()
            val raw = uriString?.toString()?.trim() ?: return false
            if (raw.isEmpty()) return false
            return try {
                val uri = Uri.parse(raw)
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                LogRepository.action("App", "Mở URL/URI: $raw")
                true
            } catch (e: android.content.ActivityNotFoundException) {
                LogRepository.warn("App", "Không có ứng dụng nào xử lý được URI: $raw")
                false
            } catch (e: Exception) {
                LogRepository.error("App", "Lỗi mở URI '$raw': ${e.message}")
                false
            }
        }

        fun goHome(ms: Any? = null): Boolean {
            checkCancelled()
            val service = SmartAccessibilityService.instance
            if (service == null) {
                LogRepository.error("Action", "Dịch vụ Accessibility chưa được bật!")
                return false
            }
            LogRepository.action("Action", "Thực hiện Trang chủ (Home)")
            val res = service.goHome()
            val delayMs = (ms as? Number)?.toLong() ?: 250L
            if (delayMs > 0) {
                sleep(delayMs)
            }
            return res
        }

        fun back(ms: Any? = null): Boolean {
            checkCancelled()
            val service = SmartAccessibilityService.instance
            if (service == null) {
                LogRepository.error("Action", "Dịch vụ Accessibility chưa được bật!")
                return false
            }
            LogRepository.action("Action", "Thực hiện Quay lại (Back)")
            val res = service.back()
            val delayMs = (ms as? Number)?.toLong() ?: 200L
            if (delayMs > 0) {
                sleep(delayMs)
            }
            return res
        }

        fun getForegroundPackage(): String {
            checkCancelled()
            val service = SmartAccessibilityService.instance
            if (service == null) {
                LogRepository.warn("App", "AccessibilityService chưa kết nối.")
                return ""
            }
            val pkg = service.getForegroundPackage()
            LogRepository.info("App", "Ứng dụng đang mở ở Foreground: $pkg")
            return pkg
        }

        private val stepMarks = mutableListOf<Pair<String, Long>>()
        private var lastStepTimeMs: Long = System.currentTimeMillis()

        fun markStep(stepName: Any?) {
            checkCancelled()
            val name = stepName?.toString()?.trim() ?: "Bước không tên"
            val now = System.currentTimeMillis()
            val duration = now - lastStepTimeMs
            stepMarks.add(Pair(name, duration))
            lastStepTimeMs = now
            LogRepository.info("StepMark", "► Đánh dấu bước: '$name' (${duration}ms)")
        }

        fun printStepSummary() {
            if (stepMarks.isEmpty()) return
            val sb = StringBuilder()
            sb.append("\n=== TỔNG KẾT THỜI GIAN CÁC BƯỚC (markStep) ===\n")
            var maxStep: Pair<String, Long>? = null
            stepMarks.forEachIndexed { idx, pair ->
                sb.append("${idx + 1}. [${pair.first}]: ${pair.second}ms\n")
                if (maxStep == null || pair.second > maxStep!!.second) {
                    maxStep = pair
                }
            }
            if (maxStep != null) {
                sb.append("⚡ Bước chậm nhất: '${maxStep!!.first}' với ${maxStep!!.second}ms\n")
            }
            sb.append("==============================================")
            LogRepository.info("StepSummary", sb.toString())
        }

        fun tapText(pattern: Any?, regionObj: Any?): Boolean {
            checkCancelled()
            val textPattern = pattern?.toString() ?: return false
            if (textPattern.isBlank()) return false

            val matched = findText(pattern, regionObj) ?: return false
            val cx = (matched.get("centerX") as? Number)?.toFloat()
                ?: (matched.get("x") as? Number)?.toFloat() ?: return false
            val cy = (matched.get("centerY") as? Number)?.toFloat()
                ?: (matched.get("y") as? Number)?.toFloat() ?: return false

            LogRepository.action("OCR", "tapText: Tìm thấy '$textPattern' tại ($cx, $cy), tự động chạm...")
            return click(cx, cy)
        }

        fun waitForText(pattern: Any?, timeoutMsObj: Any?, regionObj: Any?): NativeObject? {
            checkCancelled()
            val textPattern = pattern?.toString() ?: return null
            val timeoutMs = (timeoutMsObj as? Number)?.toLong() ?: 10000L
            val intervalMs = 1000L
            val startTime = System.currentTimeMillis()
            var attempts = 0

            LogRepository.info("OCR", "Đang chờ chữ xuất hiện: '$textPattern' (tối đa ${timeoutMs}ms)...")

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                checkCancelled()
                attempts++
                val result = findText(textPattern, regionObj)
                if (result != null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    LogRepository.info("OCR", "✓ Đã tìm thấy '$textPattern' sau ${attempts} lần thử (${elapsed}ms)")
                    return result
                }
                sleep(intervalMs)
            }

            // THẤT BẠI (Hết timeout): Tự động log OCR content đọc được + package foreground + số lần thử
            logFailureDetails("waitForText('$textPattern')", attempts, timeoutMs, regionObj)
            return null
        }

        fun waitUntil(conditionObj: Any?, timeoutMsObj: Any?, intervalMsObj: Any?): Boolean {
            checkCancelled()
            val timeoutMs = (timeoutMsObj as? Number)?.toLong() ?: 10000L
            val intervalMs = (intervalMsObj as? Number)?.toLong() ?: 1000L
            val startTime = System.currentTimeMillis()
            var attempts = 0

            LogRepository.info("Script", "Bắt đầu waitUntil (tối đa ${timeoutMs}ms)...")

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                checkCancelled()
                attempts++
                var conditionMet = false
                if (conditionObj is Function) {
                    val result = conditionObj.call(rhino, scope, scope, emptyArray())
                    conditionMet = org.mozilla.javascript.Context.toBoolean(result)
                }
                if (conditionMet) {
                    val elapsed = System.currentTimeMillis() - startTime
                    LogRepository.info("Script", "✓ waitUntil thỏa mãn sau ${attempts} lần thử (${elapsed}ms)")
                    return true
                }
                sleep(intervalMs)
            }

            // THẤT BẠI (Hết timeout)
            logFailureDetails("waitUntil", attempts, timeoutMs, null)
            return false
        }

        private fun logFailureDetails(actionName: String, attempts: Int, timeoutMs: Long, regionObj: Any?) {
            val fgPkg = getForegroundPackage().ifEmpty { "Không lấy được (Null/Home)" }

            // Chụp OCR tại thời điểm thất bại
            val ocrList = try {
                ocr(regionObj)
            } catch (e: Exception) {
                NativeArray(0)
            }

            val sb = StringBuilder()
            for (i in 0 until ocrList.size) {
                val item = ocrList.get(i) as? NativeObject
                val txt = item?.get("text")?.toString() ?: ""
                if (txt.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(" | ")
                    sb.append(txt)
                }
            }
            val ocrTextContent = if (sb.isNotEmpty()) sb.toString() else "(Màn hình không có chữ hoặc bị che/FLAG_SECURE)"

            LogRepository.error(
                "TimeoutFailure",
                "❌ [$actionName] THẤT BẠI sau $attempts lần thử (${timeoutMs}ms)!\n" +
                        "• Package Foreground: $fgPkg\n" +
                        "• Nội dung OCR đọc được tại thời điểm đó: [$ocrTextContent]"
            )
        }

        private fun parseRegion(regionObj: Any?): Rect? {
            if (regionObj is Scriptable) {
                val rx = (ScriptableObject.getProperty(regionObj, "x") as? Number)?.toInt() ?: 0
                val ry = (ScriptableObject.getProperty(regionObj, "y") as? Number)?.toInt() ?: 0
                val rw = (ScriptableObject.getProperty(regionObj, "w") as? Number)?.toInt()
                    ?: (ScriptableObject.getProperty(regionObj, "width") as? Number)?.toInt() ?: 0
                val rh = (ScriptableObject.getProperty(regionObj, "h") as? Number)?.toInt()
                    ?: (ScriptableObject.getProperty(regionObj, "height") as? Number)?.toInt() ?: 0
                if (rw > 0 && rh > 0) {
                    return Rect(rx, ry, rx + rw, ry + rh)
                }
            }
            return null
        }
    }
}
