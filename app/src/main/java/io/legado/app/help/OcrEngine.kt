package io.legado.app.help

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import io.legado.app.exception.NoStackTraceException
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.min

object OcrEngine {

    enum class OcrMode(val value: String) {
        RAW("raw"),
        LINE("line"),
        CAPTCHA("captcha");

        companion object {
            fun from(raw: String?): OcrMode? {
                if (raw.isNullOrBlank()) return RAW
                return entries.firstOrNull { it.value.equals(raw.trim(), ignoreCase = true) }
            }
        }
    }

    /** 单段最大高度，超过则分段识别 */
    private const val MAX_SEGMENT_HEIGHT = 2000
    /** 分段之间的重叠像素，避免文字恰好在切割线上被截断 */
    private const val SEGMENT_OVERLAP = 150

    fun recognize(bytes: ByteArray, mode: OcrMode = OcrMode.RAW): String {
        if (bytes.isEmpty()) return ""
        val originalBitmap = decodeBitmap(bytes) ?: throw NoStackTraceException("OCR图片解码失败")
        val rotationDegrees = readRotationDegrees(bytes)
        val bitmap = rotateBitmapIfNeeded(originalBitmap, rotationDegrees)
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        return try {
            if (bitmap.height > MAX_SEGMENT_HEIGHT) {
                recognizeSegmented(recognizer, bitmap, mode)
            } else {
                recognizeSingle(recognizer, bitmap, mode)
            }
        } finally {
            recognizer.close()
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            if (bitmap !== originalBitmap && !originalBitmap.isRecycled) {
                originalBitmap.recycle()
            }
        }
    }

    /**
     * 单张图片识别
     */
    private fun recognizeSingle(
        recognizer: TextRecognizer,
        bitmap: Bitmap,
        mode: OcrMode
    ): String {
        val rawResult = recognizeResultByBitmap(recognizer, bitmap)
        return when (mode) {
            OcrMode.RAW -> rawResult.text.trim()
            OcrMode.LINE -> recognizeByLineSegmentation(
                recognizer, bitmap, rawResult
            ).orEmpty().trim()
            OcrMode.CAPTCHA -> normalizeCaptcha(rawResult.text)
        }
    }

    /**
     * 长图分段识别：将图片按固定高度切割，逐段识别后拼接
     */
    private fun recognizeSegmented(
        recognizer: TextRecognizer,
        bitmap: Bitmap,
        mode: OcrMode
    ): String {
        val segments = buildSegments(bitmap.height)
        val results = arrayListOf<String>()

        for (segment in segments) {
            val segHeight = segment.second - segment.first
            val segBitmap = Bitmap.createBitmap(
                bitmap, 0, segment.first, bitmap.width, segHeight
            )
            try {
                val text = recognizeSingle(recognizer, segBitmap, mode)
                if (text.isNotBlank()) {
                    results += text
                }
            } finally {
                if (!segBitmap.isRecycled) {
                    segBitmap.recycle()
                }
            }
        }

        if (results.isEmpty()) return ""
        // 去除重叠区域带来的重复行
        return deduplicateSegmentResults(results)
    }

    /**
     * 计算分段的 (startY, endY) 列表
     */
    private fun buildSegments(imageHeight: Int): List<Pair<Int, Int>> {
        val segments = mutableListOf<Pair<Int, Int>>()
        val step = MAX_SEGMENT_HEIGHT - SEGMENT_OVERLAP
        var y = 0
        while (y < imageHeight) {
            val end = min(y + MAX_SEGMENT_HEIGHT, imageHeight)
            segments += y to end
            if (end >= imageHeight) break
            y += step
        }
        return segments
    }

    /**
     * 对分段结果去重：相邻段的重叠区可能产生重复行，通过比较尾部/头部行来去除
     */
    private fun deduplicateSegmentResults(results: List<String>): String {
        if (results.size <= 1) return results.firstOrNull().orEmpty()

        val merged = StringBuilder(results[0])
        for (i in 1 until results.size) {
            val prevLines = merged.toString().lines()
            val currLines = results[i].lines()

            // 在当前段的前几行中查找与上一段末尾行重复的内容
            val overlapIndex = findOverlapIndex(prevLines, currLines)
            val newLines = if (overlapIndex > 0) {
                currLines.drop(overlapIndex)
            } else {
                currLines
            }
            if (newLines.isNotEmpty()) {
                merged.append("\n").append(newLines.joinToString("\n"))
            }
        }
        return merged.toString().trim()
    }

    /**
     * 查找 currLines 开头与 prevLines 末尾的重叠行数
     */
    private fun findOverlapIndex(prevLines: List<String>, currLines: List<String>): Int {
        // 最多检查尾部 5 行
        val checkCount = min(5, min(prevLines.size, currLines.size))
        for (overlapLen in checkCount downTo 1) {
            val prevTail = prevLines.takeLast(overlapLen).map { it.trim() }
            val currHead = currLines.take(overlapLen).map { it.trim() }
            if (prevTail == currHead && prevTail.all { it.isNotBlank() }) {
                return overlapLen
            }
        }
        return 0
    }

    // ==================== 行分割二次识别（LINE模式） ====================

    /**
     * 行分割二次识别：先用整图识别拿到每行坐标，裁切后逐行重新识别
     */
    private fun recognizeByLineSegmentation(
        recognizer: TextRecognizer,
        sourceBitmap: Bitmap,
        rawResult: Text
    ): String? {
        val rects = buildLineRects(rawResult, sourceBitmap.width, sourceBitmap.height)
        if (rects.isEmpty()) return null

        val lineTexts = arrayListOf<String>()
        for (rect in rects.take(120)) {
            val safeRect = clampRect(rect, sourceBitmap.width, sourceBitmap.height)
                ?: continue
            val lineBitmap = Bitmap.createBitmap(
                sourceBitmap,
                safeRect.left,
                safeRect.top,
                safeRect.width(),
                safeRect.height()
            )
            try {
                val lineText = recognizeByBitmap(recognizer, lineBitmap).trim()
                if (lineText.isNotBlank()) {
                    lineTexts += lineText
                }
            } finally {
                if (!lineBitmap.isRecycled) {
                    lineBitmap.recycle()
                }
            }
        }
        if (lineTexts.isEmpty()) return null
        return lineTexts.joinToString("\n")
    }

    private fun buildLineRects(
        rawResult: Text,
        imageWidth: Int,
        imageHeight: Int
    ): List<Rect> {
        val rawRects = rawResult.textBlocks
            .flatMap { it.lines }
            .mapNotNull { it.boundingBox }
            .filter { it.width() > 2 && it.height() > 2 }
        if (rawRects.isEmpty()) return emptyList()

        val horizontalPad = (imageWidth * 0.02f).toInt().coerceAtLeast(2)
        val verticalPad = (imageHeight * 0.002f).toInt().coerceAtLeast(1)
        val expanded = rawRects.sortedBy { it.top }.map {
            Rect(
                (it.left - horizontalPad).coerceAtLeast(0),
                (it.top - verticalPad).coerceAtLeast(0),
                (it.right + horizontalPad).coerceAtMost(imageWidth),
                (it.bottom + verticalPad).coerceAtMost(imageHeight)
            )
        }

        val merged = mutableListOf<Rect>()
        for (rect in expanded) {
            if (merged.isEmpty()) {
                merged += Rect(rect)
                continue
            }
            val last = merged.last()
            val gap = rect.top - last.bottom
            val mergeGap = max(6, minOf(last.height(), rect.height()) / 3)
            if (gap <= mergeGap) {
                last.union(rect)
            } else {
                merged += Rect(rect)
            }
        }
        return merged.filter { it.height() >= 32 && it.width() >= 32 }
    }

    private fun clampRect(rect: Rect, width: Int, height: Int): Rect? {
        val left = rect.left.coerceIn(0, width - 1)
        val top = rect.top.coerceIn(0, height - 1)
        val right = rect.right.coerceIn(left + 1, width)
        val bottom = rect.bottom.coerceIn(top + 1, height)
        if (right - left <= 1 || bottom - top <= 1) return null
        return Rect(left, top, right, bottom)
    }

    // ==================== ML Kit 基础调用 ====================

    private fun recognizeResultByBitmap(recognizer: TextRecognizer, bitmap: Bitmap): Text {
        val image = InputImage.fromBitmap(bitmap, 0)
        return Tasks.await(recognizer.process(image))
    }

    private fun recognizeByBitmap(recognizer: TextRecognizer, bitmap: Bitmap): String {
        return recognizeResultByBitmap(recognizer, bitmap).text
    }

    // ==================== 图片工具方法 ====================

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        return runCatching {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
        }.getOrElse { bitmap }
    }

    private fun normalizeCaptcha(text: String): String {
        return text.filter { it.isLetterOrDigit() }
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun readRotationDegrees(bytes: ByteArray): Int {
        return runCatching {
            ByteArrayInputStream(bytes).use { inputStream ->
                when (
                    ExifInterface(inputStream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90,
                    ExifInterface.ORIENTATION_TRANSPOSE -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270,
                    ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
            }
        }.getOrDefault(0)
    }
}
