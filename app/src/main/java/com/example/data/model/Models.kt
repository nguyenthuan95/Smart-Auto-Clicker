package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * Điểm chạm đã lưu với tọa độ (X, Y)
 */
@Entity(tableName = "target_points")
data class TargetPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val x: Float,
    val y: Float,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Vùng quan tâm (ROI - Region of Interest) dùng để quét OCR giới hạn
 */
@Entity(tableName = "roi_regions")
data class RoiRegion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Kiểu bước trong Macro chuỗi hành động
 */
enum class StepType {
    CLICK_POINT,          // Chạm vào tọa độ cố định
    DOUBLE_CLICK,         // Chạm đúp (2 lần liên tiếp)
    LONG_PRESS,           // Nhấn giữ (ms)
    SWIPE,                // Vuốt từ điểm A đến điểm B
    WAIT_TEXT_APPEAR,     // Chờ chữ xuất hiện (OCR)
    WAIT_TEXT_DISAPPEAR,  // Chờ chữ biến mất (OCR)
    CLICK_TEXT,           // Tìm chữ bằng OCR và bấm vào tâm
    PASTE_CLIPBOARD,      // Chạm focus rồi dán văn bản / clipboard
    DELAY                 // Nghỉ (ms)
}

/**
 * Kiểu khớp chữ trong OCR
 */
enum class MatchType {
    EXACT,      // Khớp chính xác
    CONTAINS,   // Chứa chuỗi
    REGEX       // Biểu thức chính quy
}

/**
 * Bước hành động cụ thể trong chuỗi Macro
 */
data class MacroStep(
    val id: String = java.util.UUID.randomUUID().toString(),
    val stepType: StepType = StepType.CLICK_POINT,
    val title: String = "Bước hành động",
    // Tọa độ
    val x: Float = 500f,
    val y: Float = 1000f,
    val endX: Float = 500f,
    val endY: Float = 500f,
    val durationMs: Long = 100L,
    // OCR
    val searchText: String = "",
    val matchType: MatchType = MatchType.CONTAINS,
    val caseSensitive: Boolean = false,
    val timeoutMs: Long = 5000L,
    val roiX: Float = 0f,
    val roiY: Float = 0f,
    val roiWidth: Float = 0f,
    val roiHeight: Float = 0f,
    // Dán chữ
    val customTextToPaste: String = "", // rỗng = lấy từ clipboard
    // Lặp / Retry
    val delayAfterMs: Long = 500L,
    val jitterRadius: Float = 5f,
    val maxRetries: Int = 3
) : Serializable

/**
 * Kịch bản chuỗi hành động Macro
 */
@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val stepsJson: String = "[]", // Lưu danh sách List<MacroStep> dạng JSON
    val repeatCount: Int = 1, // 0 = lặp vô hạn
    val intervalBetweenLoopsMs: Long = 1000L,
    val stopOnError: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Script JavaScript thực thi linh hoạt
 */
@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val code: String,
    val isPreset: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * Mức độ ghi log
 */
enum class LogLevel {
    INFO,
    ACTION,
    OCR,
    WARN,
    ERROR
}

/**
 * Phiên thực thi chạy (Session)
 */
data class LogSession(
    val id: Long,
    val name: String,
    val startTimeMs: Long
)

/**
 * Bản ghi nhật ký chạy
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "SmartClicker",
    val message: String,
    val sessionId: Long = 0L,
    val elapsedMs: Long = -1L
)

/**
 * Kết quả nhận diện OCR cho từng phần tử chữ
 */
data class OcrElement(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float = 1.0f
) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
}
