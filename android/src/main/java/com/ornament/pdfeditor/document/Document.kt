package com.ornament.pdfeditor.document

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Size
import android.util.SizeF
import androidx.core.net.toFile
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.drawing.BezierCurve
import java.nio.file.Files

abstract class Document {
    abstract val filename: String
    abstract val parcelFileDescriptor: ParcelFileDescriptor

    abstract var size: SizeF
        protected set

    abstract val pageCount: Int

    var minScale = 1f

    abstract fun save(outputDirectory: String, options: PDFEditorOptions, excludedPages: Set<Int>): String?

    abstract fun render(
        canvas: Canvas,
        scale: Float,
        offset: PointF,
        viewPortSize: Size,
        refresh: Boolean,
        interactive: Boolean,
        zoomingOut: Boolean
    )

    abstract fun contains(point: PointF): Boolean

    abstract fun addDrawing(point: PointF, drawing: BezierCurve)

    abstract fun renderDrawing(bitmap: Bitmap, scale: Float, viewPortSize: Size, lineColor: Int, lineWidth: Double)

    abstract fun addPointToDrawing(point: PointF, offset: PointF, scale: Float)

    abstract fun reset()

    abstract fun undo()

    abstract fun redo()

    abstract fun clear()

    // Snapshots the current drawing list as the committed baseline. Called when
    // the user accepts the current edit session via Done. Subsequent
    // restoreCommittedDrawings() calls (Cancel) revert to this snapshot.
    abstract fun commitDrawings()

    // Restores the drawing list to the most recently committed snapshot. Used
    // by the Cancel flow so drawings already accepted by a prior Done survive.
    abstract fun restoreCommittedDrawings()

    abstract fun pageBounds(): Map<Int, RectF>

    open fun generateThumbnail(maxWidth: Int, maxHeight: Int): Bitmap? = null

    open fun dispose() {
        try {
            parcelFileDescriptor.close()
        } catch (_: Exception) {
        }
    }

    companion object {

        fun create(documentUrl: String, contentResolver: ContentResolver) =
            Uri.parse(documentUrl)?.let { uri ->
                var filename = ""
                contentResolver.openFileDescriptor(uri, "r")?.let { parcelFileDescriptor ->
                    when (val scheme = uri.scheme) {
                        ContentResolver.SCHEME_FILE -> {
                            val file = uri.toFile()
                            filename = file.nameWithoutExtension
                            Files.probeContentType(file.toPath())
                        }

                        ContentResolver.SCHEME_CONTENT -> {
                            filename = contentResolver.query(uri, null, null, null, null)?.let { cursor ->
                                cursor.moveToFirst()
                                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).let { nameIndex ->
                                    val name = cursor.getString(nameIndex)
                                    cursor.close()
                                    name.substringBeforeLast(".")
                                }
                            } ?: ""
                            contentResolver.getType(uri)
                        }

                        else -> throw Exception("RNPDFEditor: Unexpected scheme '$scheme'")
                    }.let { mime ->
                        when (mime) {
                            "image/jpeg", "image/png" -> ImageDocument(filename, parcelFileDescriptor, uri)
                            "application/pdf" -> PdfDocument(filename, parcelFileDescriptor)
                            else -> throw Exception("RNPDFEditor: Unexpected mime type '$mime'")
                        }
                    }
                } ?: throw Exception("RNPDFEditor: Failed openFileDescriptor '$uri'")
            } ?: throw Exception("RNPDFEditor: Failed Uri.parse '$documentUrl'")
    }
}
