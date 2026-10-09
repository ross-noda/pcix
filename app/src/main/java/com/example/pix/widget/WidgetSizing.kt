package com.example.pix.widget

/** Respect both dimensions; bound shrinkage to keep small widgets readable. */
internal fun widgetContentScale(width: Float, height: Float, referenceHeight: Float): Float =
    minOf(width / 320f, height / referenceHeight).coerceIn(.75f, 1.1f)
