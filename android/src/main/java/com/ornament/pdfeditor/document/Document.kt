package com.ornament.pdfeditor.document

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
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

    var minScale = 1f

    abstract fun save(outputDirectory: String, options: PDFEditorOptions): String

    abstract fun render(canvas: Canvas, scale: Float, offset: PointF, viewPortSize: Size, refresh: Boolean)

    abstract fun contains(point: PointF): Boolean

    abstract fun addDrawing(point: PointF, drawing: BezierCurve)

    abstract fun renderDrawing(bitmap: Bitmap, scale: Float, viewPortSize: Size, lineColor: Int, lineWidth: Double)

    abstract fun addPointToDrawing(point: PointF, offset: PointF, scale: Float)

    abstract fun reset()

    abstract fun undo()

    abstract fun clear()

    companion object {

        fun create(documentUrl: String, contentResolver: ContentResolver) =
            Uri.parse(documentUrl)?.let { uri ->
                var filename = ""
                contentResolver.openFileDescriptor(uri, "r")?.let { parcelFileDescriptor ->
                    when (val scheme = uri.scheme) {
                        ContentResolver.SCHEME_FILE -> {
                            val file = uri.toFile()
                            filename = file.name
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
                            "image/jpeg", "image/png" -> ImageDocument(filename, parcelFileDescriptor)
                            "application/pdf" -> PdfDocument(filename, parcelFileDescriptor)
                            else -> throw Exception("RNPDFEditor: Unexpected mime type '$mime'")
                        }
                    }
                } ?: throw Exception("RNPDFEditor: Failed openFileDescriptor '$uri'")
            } ?: throw Exception("RNPDFEditor: Failed Uri.parse '$documentUrl'")
    }
}
