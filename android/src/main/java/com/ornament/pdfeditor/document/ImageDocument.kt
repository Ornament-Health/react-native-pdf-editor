package com.ornament.pdfeditor.document

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Size
import android.util.SizeF
import androidx.core.net.toFile
import androidx.exifinterface.media.ExifInterface
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.drawing.BezierCurve
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.collections.ArrayDeque
import kotlin.math.max
import kotlin.math.min

class ImageDocument(
    override val filename: String,
    override val parcelFileDescriptor: ParcelFileDescriptor,
    private val sourceUri: Uri
) : Document() {

    private lateinit var imageBitmap: Bitmap
    override lateinit var size: SizeF
    override val pageCount: Int = 1
    private var bounds: RectF? = null
    private val imageDrawing = mutableListOf<BezierCurve>()
    private val redoStack = ArrayDeque<BezierCurve>()

    override fun save(outputDirectory: String, options: PDFEditorOptions, excludedPages: Set<Int>): String? {
        if (excludedPages.contains(0)) return null
        val pagePaint = Paint().apply {
            color = options.lineColor
            strokeWidth = options.lineWidth.toFloat()
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val drawingBitmap = Bitmap.createBitmap(
            imageBitmap.width,
            imageBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        Canvas(drawingBitmap).let { canvas ->
            imageDrawing.forEach {
                it.drawOnCanvas(
                    canvas,
                    pagePaint,
                    RectF(
                        0f,
                        0f,
                        drawingBitmap.width.toFloat(),
                        drawingBitmap.height.toFloat()
                    ),
                    1f / minScale
                )
            }
        }
        val copy = imageBitmap.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(copy).drawBitmap(drawingBitmap, 0f, 0f, Paint())
        val outputStream = ByteArrayOutputStream()
        copy.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val outputPath = "$outputDirectory/$filename-edited.jpg"
        FileOutputStream(outputPath).also { outputFileStream ->
            outputStream.writeTo(outputFileStream)
            outputFileStream.close()
            outputStream.close()
        }
        return "file://$outputPath"
    }

    init {
        // Read EXIF orientation BEFORE decoding. BitmapFactory.decodeFileDescriptor
        // advances the FD to EOF and ParcelFileDescriptor.dup shares the offset,
        // so reading EXIF afterwards returns ORIENTATION_UNDEFINED and gallery
        // photos render rotated 90° left.
        val orientation = readExifOrientation()
        decodeImage()?.let { imageBitmap = applyExifOrientation(it, orientation) }
        size = with(imageBitmap) { SizeF(width.toFloat(), height.toFloat()) }
    }

    private fun decodeImage(): Bitmap? {
        rewindFileDescriptor(parcelFileDescriptor.fileDescriptor)
        return BitmapFactory.decodeFileDescriptor(parcelFileDescriptor.fileDescriptor)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> rotateBitmap(bitmap, flipX = true)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> rotateBitmap(bitmap, flipY = true)
            ExifInterface.ORIENTATION_TRANSPOSE -> rotateBitmap(bitmap, 90f, flipX = true)
            ExifInterface.ORIENTATION_TRANSVERSE -> rotateBitmap(bitmap, 270f, flipX = true)
            else -> bitmap
        }
    }

    private fun readExifOrientation(): Int {
        // Prefer reading EXIF directly from the file path — the most reliable
        // method, immune to file-descriptor offset issues that affect
        // ExifInterface(FileDescriptor) after BitmapFactory has consumed the FD.
        if (sourceUri.scheme == ContentResolver.SCHEME_FILE) {
            try {
                return ExifInterface(sourceUri.toFile().absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
            } catch (_: Exception) {
                // Fall through to the FD-based reader.
            }
        }
        return try {
            ParcelFileDescriptor.dup(parcelFileDescriptor.fileDescriptor).use { descriptor ->
                rewindFileDescriptor(descriptor.fileDescriptor)
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED
                    )
                }
            }
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_UNDEFINED
        }
    }

    private fun rewindFileDescriptor(fd: java.io.FileDescriptor) {
        try {
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
        } catch (_: Exception) {
            // ignore — non-seekable FDs will still work with the JPEG decoder/ExifInterface
        }
    }

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotationDegrees: Float = 0f,
        flipX: Boolean = false,
        flipY: Boolean = false
    ): Bitmap {
        if (rotationDegrees == 0f && !flipX && !flipY) return bitmap
        val matrix = Matrix().apply {
            if (rotationDegrees != 0f) postRotate(rotationDegrees)
            if (flipX || flipY) {
                postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
            }
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return transformed
    }

    override fun render(
        canvas: Canvas,
        scale: Float,
        offset: PointF,
        viewPortSize: Size,
        refresh: Boolean,
        interactive: Boolean,
        zoomingOut: Boolean
    ) {
        val imageRect = findPdfPageRect(scale * minScale, offset)
        canvas.drawBitmap(imageBitmap, null, imageRect, Paint())
        //bitmapPages[index] = imagesBitmaps[index]
    }

    private fun findPdfPageRect(scale: Float, movementOffset: PointF): RectF {
        return RectF(
            movementOffset.x,
            movementOffset.y,
            size.width * scale + movementOffset.x,
            size.height * scale + movementOffset.y
        ).also {
            bounds = it
        }
    }

    override fun contains(point: PointF) = bounds?.contains(point.x, point.y) == true

    override fun addDrawing(point: PointF, drawing: BezierCurve) {
        if (contains(point)) {
            imageDrawing.add(drawing)
            redoStack.clear()
        }
    }

    override fun renderDrawing(
        bitmap: Bitmap,
        scale: Float,
        viewPortSize: Size,
        lineColor: Int,
        lineWidth: Double
    ) {
        val imageRect = bounds ?: return
        val drawClip = RectF(
            max(imageRect.left, 0f),
            max(imageRect.top, 0f),
            min(imageRect.right, viewPortSize.width.toFloat()),
            min(imageRect.bottom, viewPortSize.height.toFloat()),
        )
        if (drawClip.height() < 0 || drawClip.width() < 0) return
        val pagePaint = Paint().apply {
            color = lineColor
            strokeWidth = lineWidth.toFloat()
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val bitmapCanvas = Canvas(bitmap)
        val saveCount = bitmapCanvas.save()
        bitmapCanvas.clipRect(drawClip)
        imageDrawing.forEach {
            it.drawOnCanvas(
                bitmapCanvas,
                pagePaint,
                imageRect,
                scale,
                if (it.isClosed) 255 else 128
            )
        }
        bitmapCanvas.restoreToCount(saveCount)
    }

    override fun reset() {
        bounds = null
        imageDrawing.clear()
        redoStack.clear()
    }

    override fun undo() {
        imageDrawing.removeLastOrNull()?.let { redoStack.addLast(it) }
    }

    override fun redo() {
        redoStack.removeLastOrNull()?.let { imageDrawing.add(it) }
    }

    override fun clear() {
        imageDrawing.clear()
        redoStack.clear()
    }

    override fun pageBounds(): Map<Int, RectF> = bounds?.let { mapOf(0 to it) } ?: emptyMap()

    override fun generateThumbnail(maxWidth: Int, maxHeight: Int): Bitmap? {
        if (!::imageBitmap.isInitialized || maxWidth <= 0 || maxHeight <= 0) return null
        val aspectRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
        var targetWidth = maxWidth
        var targetHeight = (targetWidth / aspectRatio).toInt()
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight
            targetWidth = (targetHeight * aspectRatio).toInt()
        }
        if (targetWidth <= 0 || targetHeight <= 0) return null
        return Bitmap.createScaledBitmap(imageBitmap, targetWidth, targetHeight, true)
    }

    override fun addPointToDrawing(point: PointF, offset: PointF, scale: Float) {
        imageDrawing.lastOrNull()?.takeIf { !it.isClosed }?.addPoint(point, offset, scale)
    }

    override fun dispose() {
        if (::imageBitmap.isInitialized && !imageBitmap.isRecycled) {
            imageBitmap.recycle()
        }
        super.dispose()
    }
}
