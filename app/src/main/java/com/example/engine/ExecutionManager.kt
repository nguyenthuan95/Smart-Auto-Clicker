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
        val rect = floatingBubbleRect ?: return false
        return x >= (rect.left - 10) && x <= (rect.right + 10) &&
               y >= (rect.top - 10) && y <= (rect.bottom + 10)
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

    fun stopButton(buttonId: Int) {
        if (buttonId == 2) {
            if (_statusButton2.value != ExecutionStatus.IDLE) {
                _statusButton2.value = ExecutionStatus.IDLE
                runningThread2?.interrupt()
                runningThread2 = null
                activeJob2?.cancel()
                activeJob2 = null
                updateGlobalStatus()
                LogRepository.info("Manager", "Nút 2: Đã dừng tác vụ.")
            }
        } else {
            if (_statusButton1.value != ExecutionStatus.IDLE) {
                _statusButton1.value = ExecutionStatus.IDLE
                runningThread1?.interrupt()
                runningThread1 = null
                activeJob1?.cancel()
                activeJob1 = null
                updateGlobalStatus()
                LogRepository.info("Manager", "Nút 1: Đã dừng tác vụ.")
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
        stopButton(1)
        stopButton(2)
        updateGlobalStatus()
        _statusText.value = "Đã dừng lại"
        LogRepository.info("Manager", "Đã dừng ngay lập tức toàn bộ tác vụ (Failsafe).")
    }

    fun togglePlayPauseForButton(context: Context, buttonId: Int) {
        triggerHaptic(context)
        val status = if (buttonId == 2) _statusButton2.value else _statusButton1.value
        when (status) {
            ExecutionStatus.IDLE -> {
                val script = if (buttonId == 2) _activeScript2.value else _activeScript.value
                if (script == null) {
                    mainHandler.post {
                        Toast.makeText(context, "Nút $buttonId chưa được gán script nào!", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                startScriptForButton(context, script, buttonId)
            }
            ExecutionStatus.RUNNING -> {
                stopButton(buttonId)
            }
            ExecutionStatus.PAUSED -> {
                resumeButton(buttonId)
            }
        }
    }

    fun togglePlayPause(context: Context) {
        togglePlayPauseForButton(context, 1)
    }

    fun startActiveScript(context: Context) {
        val script = _activeScript.value ?: lastScript
        if (script == null) {
            mainHandler.post {
                Toast.makeText(context, "Vui lòng chọn một script để chạy!", Toast.LENGTH_SHORT).show()
            }
            return
        }
        startScriptForButton(context, script, 1)
    }

    fun startScript(context: Context, script: ScriptEntity) {
        startScriptForButton(context, script, 1)
    }

    fun startScriptForButton(context: Context, script: ScriptEntity, buttonId: Int) {
        stopButton(buttonId)

        if (buttonId == 2) {
            _statusButton2.value = ExecutionStatus.RUNNING
        } else {
            _statusButton1.value = ExecutionStatus.RUNNING
        }
        updateGlobalStatus()
        _loopCount.value = 1
        _statusText.value = "[Nút $buttonId] Đang chạy: ${script.name}"

        LogRepository.startNewSession("[Nút $buttonId] ${script.name}")
        if (timerJob == null || !timerJob!!.isActive) {
            startTimer()
        }

        val job = scope.launch(Dispatchers.Default) {
            if (buttonId == 2) {
                runningThread2 = Thread.currentThread()
            } else {
                runningThread1 = Thread.currentThread()
            }
            var exitMessage: String? = null
            var isError = false
            val startTimeMs = System.currentTimeMillis()

            try {
                val runner = ScriptRunner(context, buttonId)
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
                    LogRepository.error("Manager", "[Nút $buttonId] Lỗi thực thi script '${script.name}': $errDetail")
                }
            } finally {
                if (buttonId == 2) {
                    runningThread2 = null
                    _statusButton2.value = ExecutionStatus.IDLE
                } else {
                    runningThread1 = null
                    _statusButton1.value = ExecutionStatus.IDLE
                }
                updateGlobalStatus()

                val totalRuntimeMs = System.currentTimeMillis() - startTimeMs
                val summary = "${exitMessage ?: "Script đã kết thúc"}. Tổng thời gian thực thi: ${totalRuntimeMs}ms"
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

    suspend fun checkPauseState(buttonId: Int = 1) {
        while (isButtonPaused(buttonId)) {
            delay(200L)
        }
    }
}
