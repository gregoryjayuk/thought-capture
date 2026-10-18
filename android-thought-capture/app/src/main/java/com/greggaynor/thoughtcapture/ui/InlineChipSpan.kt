package com.greggaynor.thoughtcapture.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

class InlineChipSpan(
    private val backgroundColor: Int,
    private val textColor: Int,
    private val horizontalPaddingPx: Float,
    private val verticalPaddingPx: Float,
    private val cornerRadiusPx: Float,
    private val trailingGapPx: Float
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val textWidth = paint.measureText(text, start, end)
        return (textWidth + (horizontalPaddingPx * 2) + trailingGapPx).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val originalColor = paint.color
        val originalStyle = paint.style
        val textWidth = paint.measureText(text, start, end)
        val fontMetrics = paint.fontMetrics
        val chipTop = y + fontMetrics.ascent - verticalPaddingPx
        val chipBottom = y + fontMetrics.descent + verticalPaddingPx
        val rect = RectF(
            x,
            chipTop,
            x + textWidth + (horizontalPaddingPx * 2),
            chipBottom
        )

        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)

        paint.color = textColor
        canvas.drawText(
            text,
            start,
            end,
            x + horizontalPaddingPx,
            y.toFloat(),
            paint
        )

        paint.color = originalColor
        paint.style = originalStyle
    }
}
