package io.legado.app.utils

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Size
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import com.caverock.androidsvg.SVG
import kotlin.math.max

@Suppress("WeakerAccess", "MemberVisibilityCanBePrivate")
object SvgUtils {

    fun createBitmap(filePath: String, width: Int, height: Int? = null): Bitmap? {
        return kotlin.runCatching {
            val inputStream = FileInputStream(filePath)
            createBitmap(inputStream, width, height)
        }.getOrNull()
    }

    /**
     * 从Svg中解码bitmap
     */
    fun createBitmap(inputStream: InputStream, width: Int, height: Int? = null): Bitmap? {
        return kotlin.runCatching {
            val svg = SVG.getFromInputStream(inputStream)
            createBitmap(svg, width, height)
        }.getOrNull()
    }

    fun createBitmapFromSvgText(svgText: String, width: Int, height: Int? = null): Bitmap? {
        return kotlin.runCatching {
            ByteArrayInputStream(svgText.toByteArray(Charsets.UTF_8)).use { inputStream ->
                val svg = SVG.getFromInputStream(inputStream)
                createBitmap(svg, width, height)
            }
        }.getOrNull()
    }

    fun getAspectRatioFromSvgText(svgText: String): Float? {
        return kotlin.runCatching {
            ByteArrayInputStream(svgText.toByteArray(Charsets.UTF_8)).use { inputStream ->
                val svg = SVG.getFromInputStream(inputStream)
                val size = getSize(svg)
                if (size.width > 0 && size.height > 0) {
                    size.width.toFloat() / size.height.toFloat()
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    //获取svg图片大小
    fun getSize(filePath: String): Size? {
        return kotlin.runCatching {
            val inputStream = FileInputStream(filePath)
            getSize(inputStream)
        }.getOrNull()
    }

    fun getSize(inputStream: InputStream): Size? {
        return kotlin.runCatching {
            val svg = SVG.getFromInputStream(inputStream)
            getSize(svg)
        }.getOrNull()
    }

    /////// private method
    private fun createBitmap(svg: SVG, width: Int? = null, height: Int? = null): Bitmap {
        val size = getSize(svg)
        val wRatio = width?.let { size.width / it } ?: -1
        val hRatio = height?.let { size.height / it } ?: -1
        //如果超出指定大小，则缩小相应的比例
        val ratio = when {
            wRatio > 1 && hRatio > 1 -> max(wRatio, hRatio)
            wRatio > 1 -> wRatio
            hRatio > 1 -> hRatio
            else -> 1
        }

        val viewBox: RectF? = svg.documentViewBox
        if (viewBox == null && size.width > 0 && size.height > 0) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }

        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")

        val bitmapWidth = size.width / ratio
        val bitmapHeight = size.height / ratio
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
    }

    private fun getSize(svg: SVG): Size {
        val width = svg.documentWidth.toInt().takeIf { it > 0 }
            ?: (svg.documentViewBox.right - svg.documentViewBox.left).toInt()
        val height = svg.documentHeight.toInt().takeIf { it > 0 }
            ?: (svg.documentViewBox.bottom - svg.documentViewBox.top).toInt()
        return Size(width, height)
    }
}
