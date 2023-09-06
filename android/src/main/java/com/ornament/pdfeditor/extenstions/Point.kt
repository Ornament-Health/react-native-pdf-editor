package com.ornament.pdfeditor.extenstions

import android.graphics.PointF
import kotlin.math.sqrt

internal operator fun PointF.minus(other: PointF) = PointF(
    this.x - other.x,
    this.y - other.y
)

internal operator fun PointF.plus(other: PointF) = PointF(
    this.x + other.x,
    this.y + other.y
)

internal operator fun PointF.times(factor: Float) = PointF(
    this.x * factor,
    this.y * factor
)

internal operator fun PointF.div(factor: Float) = PointF(
    this.x / factor,
    this.y / factor
)