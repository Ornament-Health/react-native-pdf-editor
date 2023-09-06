package com.ornament.pdfeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView
import com.ornament.pdfeditor.extenstions.div
import com.ornament.pdfeditor.extenstions.minus
import com.ornament.pdfeditor.extenstions.plus

class PDFRecyclerView : RecyclerView {
    private lateinit var mScaleDetector: ScaleGestureDetector
    private var scaleFactor = 1f
    private var startPoint = PointF(0f, 0f)
    private var canvasPosition = PointF(0f, 0f)
    private var width = 0f
    private var height = 0f

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!,
        attrs,
        defStyleAttr
    )

    init {
        if (!isInEditMode) {
            mScaleDetector = ScaleGestureDetector(context, ScaleListener())
        }
    }

    interface Listener {
        fun onScaleChanged(newScale: Float)
    }

    private var listener: Listener? = null
    fun setListener(listener: Listener) {
        this.listener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        width = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        height = MeasureSpec.getSize(heightMeasureSpec).toFloat()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent) = true


    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isTwoFingers(ev)) super.onTouchEvent(ev)
        mScaleDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isTwoFingers(ev)) {
                    startPoint.x = (ev.getX(0) + ev.getX(1)) / 2
                    startPoint.y = (ev.getY(0) + ev.getY(1)) / 2
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTwoFingers(ev)) {
                    val currentPoint = PointF(
                        (ev.getX(0) + ev.getX(1)) / 2,
                        (ev.getY(0) + ev.getY(1)) / 2
                    )
                    val movementDifference = currentPoint - startPoint
                    //val scaleDifference = (currentPoint - startPoint) / scaleFactor
                    canvasPosition += movementDifference
                    closeCanvasPosition()
                    startPoint = currentPoint
                    invalidate()
                    Log.i("POSITION", "${canvasPosition.x} x ${canvasPosition.y}")
                } else drawOnPage(ev)
            }

            MotionEvent.ACTION_CANCEL -> {}

            MotionEvent.ACTION_POINTER_UP -> {}

            MotionEvent.ACTION_UP -> {
                listener?.onScaleChanged(scaleFactor)
            }
        }
        return true
    }

    private fun drawOnPage(event: MotionEvent) {
        val pageView = findChildViewUnder(event.x, event.y) as? DrawableImageView
        pageView?.let {
            val marginInset = with(it.layoutParams as LayoutParams) {
                PointF(
                    leftMargin.toFloat(),
                    topMargin.toFloat()
                )
            }
            it.draw(
                (PointF(event.x, event.y + scrollY) + canvasPosition) / scaleFactor - marginInset,
                scaleFactor
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(canvasPosition.x, canvasPosition.y)
        canvas.scale(scaleFactor, scaleFactor)

        super.dispatchDraw(canvas)
        canvas.restore()
        invalidate()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceAtLeast(1f).coerceAtMost(5.0f)
            invalidate()
            return true
        }
    }


    private fun isTwoFingers(event: MotionEvent) = event.pointerCount == 2

    private fun closeCanvasPosition() {
        canvasPosition.x =
            canvasPosition.x.coerceAtLeast((1f - scaleFactor) * width).coerceAtMost(0f)
        canvasPosition.y =
            canvasPosition.y.coerceAtLeast((1f - scaleFactor) * height).coerceAtMost(0f)
    }

}