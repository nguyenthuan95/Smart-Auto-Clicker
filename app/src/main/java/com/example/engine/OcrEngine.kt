package com.example.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.data.model.MatchType
import com.example.data.model.OcrElement
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object OcrEngine {

    // Khởi tạo ML Kit TextRecognizer on-device
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Nhận diện toàn bộ văn bản trong Bitmap (hoặc trong vùng ROI chỉ định)
     */
    suspend fun recognizeText(
        sourceBitmap: Bitmap,
        roi: Rect? = null,
        minConfidence: Float = 0.5f
    ): List<OcrElement> = withContext(Dispatchers.Default) {
        val bitmapToProcess: Bitmap
        val offsetX: Int
        val offsetY: Int

        if (roi != null && roi.width() > 0 && roi.height() > 0) {
            val validLeft = roi.left.coerceIn(0, sourceBitmap.width - 1)
            val validTop = roi.top.coerceIn(0, sourceBitmap.height - 1)
            val validWidth = roi.width().coerceAtMost(sourceBitmap.width - validLeft)
            val validHeight = roi.height().coerceAtMost(sourceBitmap.height - validTop)

            bitmapToProcess = Bitmap.createBitmap(sourceBitmap, validLeft, validTop, validWidth, validHeight)
            offsetX = validLeft
            offsetY = validTop
        } else {
            bitmapToProcess = sourceBitmap
            offsetX = 0
            offsetY = 0
        }

        val elements = mutableListOf<OcrElement>()

        try {
            val inputImage = InputImage.fromBitmap(bitmapToProcess, 0)
            val visionText = suspendCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { text -> continuation.resume(text) }
                    .addOnFailureListener { e ->
                        LogRepository.error("OCR", "Lỗi nhận diện văn bản: ${e.message}")
                        continuation.resume(null)
                    }
            }

            if (visionText != null) {
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        if (box != null) {
                            val confidence = line.confidence ?: 1.0f
                            val elemX = box.left + offsetX
                            val elemY = box.top + offsetY
                            val elemW = box.width()
                            val elemH = box.height()

                            val bubble = ExecutionManager.floatingBubbleRect
                            val intersectsBubble = if (bubble != null) {
                                Rect.intersects(bubble, Rect(elemX, elemY, elemX + elemW, elemY + elemH))
                            } else false

                            if (!intersectsBubble && confidence >= minConfidence) {
                                elements.add(
                                    OcrElement(
                                        text = line.text.trim(),
                                        x = elemX,
                                        y = elemY,
                                        width = elemW,
                                        height = elemH,
                                        confidence = confidence
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            if (bitmapToProcess != sourceBitmap) {
                bitmapToProcess.recycle()
            }
        }

        elements
    }

    /**
     * Tìm kiếm văn bản theo chuỗi / regex trong các phần tử đã nhận diện
     */
    fun findMatchingElement(
        elements: List<OcrElement>,
        patternString: String,
        matchType: MatchType = MatchType.CONTAINS,
        caseSensitive: Boolean = false
    ): OcrElement? {
        val target = if (caseSensitive) patternString else patternString.lowercase()

        val compiledRegex = if (matchType == MatchType.REGEX) {
            try {
                if (caseSensitive) Pattern.compile(patternString)
                else Pattern.compile(patternString, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                null
            }
        } else null

        for (el in elements) {
            val textToCheck = if (caseSensitive) el.text else el.text.lowercase()

            val isMatched = when (matchType) {
                MatchType.EXACT -> textToCheck == target
                MatchType.CONTAINS -> textToCheck.contains(target)
                MatchType.REGEX -> compiledRegex?.matcher(el.text)?.find() == true
            }

            if (isMatched) {
                return el
            }
        }
        return null
    }
}
