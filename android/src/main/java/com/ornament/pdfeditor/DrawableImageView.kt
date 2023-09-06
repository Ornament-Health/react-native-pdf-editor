package com.ornament.pdfeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class DrawableImageView : AppCompatImageView {

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!,
        attrs,
        defStyleAttr
    )
    private var position: PointF? = null
    private var scale: Float = 1f

    fun draw(position: PointF, scale: Float) {
        this.position = position
        this.scale = scale
        invalidate()
    }

    private val paint = Paint().apply {
        color = Color.RED
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        position?.let {
            canvas.drawCircle(it.x, it.y, 20f / scale, paint)
            canvas
        }
    }
}