package com.example.engine

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import com.example.data.database.AppRepository
import com.example.data.model.ScriptEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ExecutionStatus {
    IDLE,       // Sẵn sàng / Đã dừng
    RUNNING,    // Đang thực thi
    PAUSED      // Tạm dừng
}

object ExecutionManager {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var activeJob1: Job? = null
    private var activeJob2: Job? = null
    private var timerJob: Job? = null
    private var initJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var runningThread1: Thread? = null
    @Volatile
    private var runningThread2: Thread? = null

    private val button1Lock = Any()
    private val button2Lock = Any()
    @Volatile
    private var isStartingButton1 = false
    @Volatile
    private var isStartingButton2 = false
    private val runCounter = java.util.concurrent.atomic.AtomicLong(0)

    private const val PREFS_NAME = "smart_clicker_prefs"
    private const val PREF_ACTIVE_SCRIPT_ID = "active_script_id"
    private const val PREF_ACTIVE_SCRIPT2_ID = "active_script2_id"
    private const val PREF_BUTTON1_LABEL = "button1_label"
    private const val PREF_BUTTON2_LABEL = "button2_label"

    private val _statusButton1 = MutableStateFlow(ExecutionStatus.IDLE)
    val statusButton1: StateFlow<ExecutionStatus> = _statusButton1.asStateFlow()

    private val _statusButton2 = MutableStateFlow(ExecutionStatus.IDLE)
    val statusButton2: StateFlow<ExecutionStatus> = _statusButton2.asStateFlow()

    private val _executionStatus = MutableStateFlow(ExecutionStatus.IDLE)
    val executionStatus: StateFlow<ExecutionStatus> = _executionStatus.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _statusText = MutableStateFlow("Sẵn sàng")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    // Script 1 hoạt động (Gắn Nút nổi 1)
    private val _activeScript = MutableStateFlow<ScriptEntity?>(null)
    val activeScript: StateFlow<ScriptEntity?> = _activeScript.asStateFlow()

    // Script 2 hoạt động (Gắn Nút nổi 2)
    private val _activeScript2 = MutableStateFlow<ScriptEntity?>(null)
    val activeScript2: StateFlow<ScriptEntity?> = _activeScript2.asStateFlow()

    // Nhãn hiển thị phân biệt trên nút nổi (1-2 ký tự)
    private val _button1Label = MutableStateFlow("1")
    val button1Label: StateFlow<String> = _button1Label.asStateFlow()

    private val _button2Label = MutableStateFlow("2")
    val button2Label: StateFlow<String> = _button2Label.asStateFlow()

    // Bộ đếm số lần lặp và thời gian đã chạy
    private val _loopCount = MutableStateFlow(0)
    val loopCount: StateFlow<Int> = _loopCount.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    // Tọa độ bounding box của các bong bóng nổi trên màn hình để loại trừ OCR và auto-click
    @Volatile
    var floatingBubbleRect: Rect? = null
    @Volatile
    var floatingBubbleRect1: Rect? = null
    @Volatile
    var floatingBubbleRect2: Rect? = null

    // Callback ẩn tạm thời bong bóng khi chụp màn hình OCR
    var onTemporarilyHideBubble: ((hide: Boolean) -> Unit)? = null

    var lastScript: ScriptEntity? = null

    fun init(context: Context, repository: AppRepository) {
        initJob?.cancel()
        initJob = scope.launch {
            // Đảm bảo các script mẫu mới luôn được nạp vào cơ sở dữ liệu nếu chưa có hoặc cập nhật nếu có thay đổi
            try {
                val existing = repository.getAllScriptsList()
                val existingMap = existing.associateBy { it.name }
                for (preset in SampleScripts.allPresets) {
                    val current = existingMap[preset.name]
                    if (current == null) {
                        repository.saveScript(preset)
                    } else if (current.isPreset && current.code != preset.code) {
                        repository.saveScript(current.copy(code = preset.code, description = preset.description))
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedScriptId1 = prefs.getLong(PREF_ACTIVE_SCRIPT_ID, -1L)
            val savedScriptId2 = prefs.getLong(PREF_ACTIVE_SCRIPT2_ID, -1L)

            _button1Label.value = prefs.getString(PREF_BUTTON1_LABEL, "1") ?: "1"
            _button2Label.value = prefs.getString(PREF_BUTTON2_LABEL, "2") ?: "2"

            repository.allScripts.collectLatest { list ->
                if (list.isEmpty()) {
                    _activeScript.value = null
                    _activeScript2.value = null
                    return@collectLatest
                }

                // Script 1
                val matched1 = if (_activeScript.value != null) {
                    list.find { it.id == _activeScript.value!!.id }
                } else if (savedScriptId1 != -1L) {
                    list.find { it.id == savedScriptId1 }
                } else {
                    null
                }
                _activeScript.value = matched1 ?: list.first()

                // Script 2
                val matched2 = if (_activeScript2.value != null) {
                    list.find { it.id == _activeScript2.value!!.id }
                } else if (savedScriptId2 != -1L) {
                    list.find { it.id == savedScriptId2 }
                } else {
                    null
                }
                _activeScript2.value = matched2 ?: if (list.size > 1) list[1] else list.first()

                lastScript = _activeScript.value
            }
        }
    }

    fun setActiveScript(script: ScriptEntity, context: Context) {
        setActiveScript1(script, context)
    }

    fun setActiveScript1(script: ScriptEntity, context: Context) {
        _activeScript.value = script
        lastScript = script
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(PREF_ACTIVE_SCRIPT_ID, script.id).apply()
        } catch (e: Exception) {
            // ignore
        }
        _statusText.value = "Nút 1: ${script.name}"
        LogRepository.info("Manager", "Đã chọn '${script.name}' cho Nút nổi 1.")
    }

    fun setActiveScript2(script: ScriptEntity, context: Context) {
        _activeScript2.value = script
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(PREF_ACTIVE_SCRIPT2_ID, script.id).apply()
        } catch (e: Exception) {
            // ignore
        }
        _statusText.value = "Nút 2: ${script.name}"
        LogRepository.info("Manager", "Đã chọn '${script.name}' cho Nút nổi 2.")
    }

    fun setButton1Label(label: String, context: Context) {
        val trimmed = label.trim().take(3).ifEmpty { "1" }
        _button1Label.value = trimmed
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_BUTTON1_LABEL, trimmed).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun setButton2Label(label: String, context: Context) {
        val trimmed = label.trim().take(3).ifEmpty { "2" }
        _button2Label.value = trimmed
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_BUTTON2_LABEL, trimmed).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun isPointInsideFloatingBubble(x: Float, y: Float): Boolean {
        val r1 = floatingBubbleRect1 ?: floatingBubbleRect
        if (r1 != null && x >= (r1.left - 15) && x <= (r1.right + 15) &&
            y >= (r1.top - 15) && y <= (r1.bottom + 15)
        ) {
            return true
        }
        val r2 = floatingBubbleRect2
        if (r2 != null && x >= (r2.left - 15) && x <= (r2.right + 15) &&
            y >= (r2.top - 15) && y <= (r2.bottom + 15)
        ) {
            return true
        }
        return false
    }

    fun setLoop(count: Int) {
        _loopCount.value = count
    }

    fun incrementLoop() {
        _loopCount.value += 1
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _executionStatus.value != ExecutionStatus.IDLE) {
                delay(1000L)
                if (_executionStatus.value == ExecutionStatus.RUNNING) {
                    _elapsedSeconds.value += 1
                }
            }
        }
    }

    fun triggerHaptic(context: Context, longVibrate: Boolean = false) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vm?.defaultVibrator
                val effect = if (longVibrate) {
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(if (longVibrate) 80L else 35L)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun isButtonRunning(buttonId: Int): Boolean {
        val s = if (buttonId == 2) _statusButton2.value else _statusButton1.value
        return s != ExecutionStatus.IDLE
    }

    fun isButtonPaused(buttonId: Int): Boolean {
        val s = if (buttonId == 2) _statusButton2.value else _statusButton1.value
        return s == ExecutionStatus.PAUSED
    }

    fun getStatusForButton(buttonId: Int): StateFlow<ExecutionStatus> {
        return if (buttonId == 2) statusButton2 else statusButton1
    }

    private fun updateGlobalStatus() {
        val s1 = _statusButton1.value
        val s2 = _statusButton2.value
        val combined = when {
            s1 == ExecutionStatus.RUNNING || s2 == ExecutionStatus.RUNNING -> ExecutionStatus.RUNNING
            s1 == ExecutionStatus.PAUSED || s2 == ExecutionStatus.PAUSED -> ExecutionStatus.PAUSED
            else -> ExecutionStatus.IDLE
        }
        _executionStatus.value = combined
        _isRunning.value = (combined == ExecutionStatus.RUNNING)
        _isPaused.value = (combined == ExecutionStatus.PAUSED)
        if (combined == ExecutionStatus.IDLE) {
            _elapsedSeconds.value = 0L
            _loopCount.value = 0
            timerJob?.cancel()
            timerJob = null
        }
    }

    fun pauseButton(buttonId: Int) {
        if (buttonId == 2) {
            if (_statusButton2.value == ExecutionStatus.RUNNING) {
                _statusButton2.value = ExecutionStatus.PAUSED
                updateGlobalStatus()
                LogRepository.info("Manager", "Nút 2: Đã tạm dừng.")
            }
        } else {
            if (_statusButton1.value == ExecutionStatus.RUNNING) {
                _statusButton1.value = ExecutionStatus.PAUSED
                updateGlobalStatus()
                LogRepository.info("Manager", "Nút 1: Đã tạm dừng.")
            }
        }
    }

    fun resumeButton(buttonId: Int) {
        if (buttonId == 2) {
            if (_statusButton2.value == ExecutionStatus.PAUSED) {
                _statusButton2.value = ExecutionStatus.RUNNING
                updateGlobalStatus()
                LogRepository.info("Manager", "Nút 2: Đang tiếp tục chạy...")
            }
        } else {
            if (_statusButton1.value == ExecutionStatus.PAUSED) {
                _statusButton1.value = ExecutionStatus.RUNNING
                updateGlobalStatus()
                LogRepository.info("Manager", "Nút 1: Đang tiếp tục chạy...")
            }
        }
    }

    fun stopButton(buttonId: Int, source: String = "Manual") {
        val lock = if (buttonId == 2) button2Lock else button1Lock
        synchronized(lock) {
            if (buttonId == 2) {
                if (_statusButton2.value != ExecutionStatus.IDLE) {
                    _statusButton2.value = ExecutionStatus.IDLE
                    runningThread2?.interrupt()
                    runningThread2 = null
                    activeJob2?.cancel()
                    activeJob2 = null
                    isStartingButton2 = false
                    updateGlobalStatus()
                    LogRepository.info("Manager", "[Nút 2] Đã dừng tác vụ (Nguồn: $source).")
                }
            } else {
                if (_statusButton1.value != ExecutionStatus.IDLE) {
                    _statusButton1.value = ExecutionStatus.IDLE
                    runningThread1?.interrupt()
                    runningThread1 = null
                    activeJob1?.cancel()
                    activeJob1 = null
                    isStartingButton1 = false
                    updateGlobalStatus()
                    LogRepository.info("Manager", "[Nút 1] Đã dừng tác vụ (Nguồn: $source).")
                }
            }
        }
    }

    fun pause() {
        pauseButton(1)
        pauseButton(2)
    }

    fun resume() {
        resumeButton(1)
        resumeButton(2)
    }

    fun stop() {
        stopButton(1, "Failsafe Stop All")
        stopButton(2, "Failsafe Stop All")
        updateGlobalStatus()
        _statusText.value = "Đã dừng lại"
        LogRepository.info("Manager", "Đã dừng ngay lập tức toàn bộ tác vụ (Failsafe).")
    }

    fun togglePlayPauseForButton(context: Context, buttonId: Int, triggerSource: String = "Touch") {
        triggerHaptic(context)
        val lock = if (buttonId == 2) button2Lock else button1Lock
        synchronized(lock) {
            val isStarting = if (buttonId == 2) isStartingButton2 else isStartingButton1
            if (isStarting) {
                LogRepository.warn("Manager", "[Nút $buttonId] BỎ QUA togglePlayPause từ [$triggerSource] vì job đang trong quá trình khởi động (State Lock).")
                return
            }
            val status = if (buttonId == 2) _statusButton2.value else _statusButton1.value
            LogRepository.info("Manager", ">>> [Nút $buttonId] togglePlayPause nhận từ [$triggerSource] lúc ${System.currentTimeMillis()}, Trạng thái hiện tại: $status")
            when (status) {
                ExecutionStatus.IDLE -> {
                    val script = if (buttonId == 2) _activeScript2.value else _activeScript.value
                    if (script == null) {
                        mainHandler.post {
                            Toast.makeText(context, "Nút $buttonId chưa được gán script nào!", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                    startScriptForButton(context, script, buttonId, triggerSource)
                }
                ExecutionStatus.RUNNING -> {
                    stopButton(buttonId, triggerSource)
                }
                ExecutionStatus.PAUSED -> {
                    resumeButton(buttonId)
                }
            }
        }
    }

    fun togglePlayPause(context: Context) {
        togglePlayPauseForButton(context, 1, "Tile/Shortcut")
    }

    fun startActiveScript(context: Context) {
        val script = _activeScript.value ?: lastScript
        if (script == null) {
            mainHandler.post {
                Toast.makeText(context, "Vui lòng chọn một script để chạy!", Toast.LENGTH_SHORT).show()
            }
            return
        }
        startScriptForButton(context, script, 1, "startActiveScript")
    }

    fun startScript(context: Context, script: ScriptEntity) {
        startScriptForButton(context, script, 1, "startScript")
    }

    fun startScriptForButton(context: Context, script: ScriptEntity, buttonId: Int, triggerSource: String = "Manual") {
        val lock = if (buttonId == 2) button2Lock else button1Lock
        val runId = runCounter.incrementAndGet()
        val now = System.currentTimeMillis()

        synchronized(lock) {
            val isStarting = if (buttonId == 2) isStartingButton2 else isStartingButton1
            if (isStarting) {
                LogRepository.warn("Manager", "[Nút $buttonId] BỎ QUA startScriptForButton vì đang có job khởi động (Run #$runId, Nguồn: $triggerSource)")
                return
            }
            if (buttonId == 2) isStartingButton2 = true else isStartingButton1 = true

            // Đảm bảo thread cũ được ngắt và kết thúc sạch sẽ trước khi bắt đầu job mới
            val oldThread = if (buttonId == 2) runningThread2 else runningThread1
            val oldJob = if (buttonId == 2) activeJob2 else activeJob1
            if (oldThread != null && oldThread.isAlive) {
                LogRepository.warn("Manager", "[Nút $buttonId] Phát hiện thread cũ (${oldThread.name}) vẫn đang chạy, tiến hành ngắt và chờ dừng...")
                oldThread.interrupt()
                try {
                    oldThread.join(250L)
                } catch (e: Exception) {
                    // ignore
                }
            }
            oldJob?.cancel()

            if (buttonId == 2) {
                _statusButton2.value = ExecutionStatus.RUNNING
            } else {
                _statusButton1.value = ExecutionStatus.RUNNING
            }
            updateGlobalStatus()
            _loopCount.value = 1
            _statusText.value = "[Nút $buttonId] Đang chạy: ${script.name}"

            LogRepository.info("Manager", ">>> [Nút $buttonId] KHỞI ĐỘNG RUN #$runId lúc $now | Nguồn gọi: [$triggerSource] | Script: '${script.name}' (ID: ${script.id})")
            LogRepository.startNewSession("[Nút $buttonId] ${script.name} (#$runId)")
            if (timerJob == null || !timerJob!!.isActive) {
                startTimer()
            }

            val job = scope.launch(Dispatchers.Default) {
                if (buttonId == 2) {
                    runningThread2 = Thread.currentThread()
                    isStartingButton2 = false
                } else {
                    runningThread1 = Thread.currentThread()
                    isStartingButton1 = false
                }
                var exitMessage: String? = null
                var isError = false
                val startTimeMs = System.currentTimeMillis()

                try {
                    LogRepository.info("Manager", ">>> [Nút $buttonId][Run #$runId] Thread '${Thread.currentThread().name}' bắt đầu thông dịch ScriptRunner")
                    val runner = ScriptRunner(context, buttonId, runId)
                    runner.execute(script.code)
                    exitMessage = "[Nút $buttonId] '${script.name}' đã hoàn thành"
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("Script bị dừng bởi người dùng") || msg.contains("stop()")) {
                        exitMessage = "[Nút $buttonId] Script đã dừng"
                    } else {
                        isError = true
                        val errDetail = e.localizedMessage ?: e.message ?: "Lỗi không xác định"
                        exitMessage = "[Nút $buttonId] Lỗi script: $errDetail"
                        LogRepository.error("Manager", "[Nút $buttonId][Run #$runId] Lỗi thực thi script '${script.name}': $errDetail")
                    }
                } finally {
                    synchronized(lock) {
                        if (buttonId == 2) {
                            runningThread2 = null
                            isStartingButton2 = false
                            _statusButton2.value = ExecutionStatus.IDLE
                            activeJob2 = null
                        } else {
                            runningThread1 = null
                            isStartingButton1 = false
                            _statusButton1.value = ExecutionStatus.IDLE
                            activeJob1 = null
                        }
                        updateGlobalStatus()
                    }

                    val totalRuntimeMs = System.currentTimeMillis() - startTimeMs
                    val summary = "${exitMessage ?: "Script đã kết thúc"}. Tổng thời gian thực thi: ${totalRuntimeMs}ms"
                    LogRepository.info("Manager", ">>> [Nút $buttonId][Run #$runId] Kết thúc Job sau ${totalRuntimeMs}ms")
                    LogRepository.endSession(summary)

                    val notifyText = exitMessage ?: "Script đã xong"
                    mainHandler.post {
                        if (isError) {
                            Toast.makeText(context, notifyText, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, notifyText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            if (buttonId == 2) {
                activeJob2 = job
            } else {
                activeJob1 = job
            }
        }
    }

    suspend fun checkPauseState(buttonId: Int = 1) {
        while (isButtonPaused(buttonId)) {
            delay(200L)
        }
    }
}
