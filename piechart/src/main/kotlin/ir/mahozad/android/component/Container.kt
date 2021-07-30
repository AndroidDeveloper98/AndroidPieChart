package ir.mahozad.android.component

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.RectF
import androidx.core.graphics.withClip
import ir.mahozad.android.Coordinates
import ir.mahozad.android.PieChart
import ir.mahozad.android.component.Wrapping.WRAP
import kotlin.math.max
import kotlin.math.min

internal class Container(
    children: List<Box>,
    private val maxAvailableWidth: Float,
    private val maxAvailableHeight: Float,
    layoutDirection: LayoutDirection,
    private val childrenAlignment: Alignment,
    private val wrapping: Wrapping = WRAP,
    override val margins: Margins? = null,
    override val paddings: Paddings? = null,
    private val background: Background? = null,
    private val border: Border? = null,
    legendLinesMargin: Float = 0f
) : Box {

    private var internalChildren = children
    private var internalLayoutDirection = layoutDirection

    init {
        if (wrapping == WRAP) {
            if (children.size > 1 && layoutDirection == LayoutDirection.HORIZONTAL) {
                val width = calculateRowWidth(children, border, paddings)
                if (width > maxAvailableWidth) {
                    internalLayoutDirection = LayoutDirection.VERTICAL
                    val rows = mutableListOf<Box>()
                    val row = mutableListOf<Box>()
                    for (child in children) {
                        row.add(child)
                        val rowWidth = calculateRowWidth(row, border, paddings)
                        if (rowWidth > maxAvailableWidth) {
                            if (row.size > 1) {
                                val removed = row.removeLast()
                                val container = Container(row.toList()/*clone*/, maxAvailableWidth, maxAvailableHeight, layoutDirection, childrenAlignment, wrapping, Margins(top = legendLinesMargin), paddings, background)
                                rows.add(container)

                                row.clear()
                                row.add(removed)
                            } else {
                                val container = Container(row.toList()/*clone*/, maxAvailableWidth, maxAvailableHeight, layoutDirection, childrenAlignment, wrapping, Margins(top = legendLinesMargin), paddings, background)
                                rows.add(container)
                                row.clear()
                            }
                        }
                    }
                    if (row.isNotEmpty()) {
                        val container = Container(row, maxAvailableWidth, maxAvailableHeight, layoutDirection, childrenAlignment, wrapping, Margins(top = legendLinesMargin), paddings, background)
                        rows.add(container)
                    }
                    internalChildren = rows
                }
            } else if (children.size > 1 && layoutDirection == LayoutDirection.VERTICAL) {
                val height = calculateColumnHeight(children, border, paddings)
                if (height > maxAvailableHeight) {
                    internalLayoutDirection = LayoutDirection.HORIZONTAL
                    val columns = mutableListOf<Box>()
                    val column = mutableListOf<Box>()
                    for (child in children) {
                        column.add(child)
                        val columnHeight = calculateColumnHeight(column, border, paddings)
                        if (columnHeight > maxAvailableHeight) {
                            if (column.size > 1) {
                                val removed = column.removeLast()
                                val container = Container(column.toList()/*clone*/, maxAvailableWidth, maxAvailableHeight, layoutDirection, childrenAlignment, wrapping, Margins(top = legendLinesMargin), paddings, background)
                                columns.add(container)

                                column.clear()
                                column.add(removed)
                            } else {
                                val container = Container(column.toList()/*clone*/, maxAvailableWidth, maxAvailableHeight, layoutDirection, childrenAlignment, wrapping, Margins(top = legendLinesMargin), paddings, background)
                                columns.add(container)
                                column.clear()
                            }
                        }
                    }
                    if (column.isNotEmpty()) {
                        val container = Container(column, maxAvailableWidth, maxAvailableHeight, layoutDirection, childrenAlignment, wrapping, Margins(top = legendLinesMargin), paddings, background)
                        columns.add(container)
                    }
                    internalChildren = columns
                }
            }

        }
    }

    override val width by lazy { min(calculateWidth(), maxAvailableWidth) }
    override val height by lazy { min(calculateHeight(), maxAvailableHeight) }
    private val bounds = RectF(0f, 0f, 0f, 0f)
    private val borderBounds = RectF(0f, 0f, 0f, 0f)
    private val paint = Paint(ANTI_ALIAS_FLAG)

    private fun calculateWidth(): Float {
        if (internalChildren.isEmpty()) {
            return (border?.thickness ?: 0f) * 2 + (paddings?.horizontal ?: 0f)
        }
        return if (internalLayoutDirection == LayoutDirection.HORIZONTAL) {
            internalChildren.sumOf { it.width.toDouble() }.toFloat() +
                    (border?.thickness ?: 0f) * 2 +
                    // Sum of all the collapsing margins between each pair of children
                    internalChildren.zipWithNext { a, b -> max(a.margins?.end ?: 0f, b.margins?.start ?: 0f) }.sum() +
                    max(paddings?.start ?: 0f, internalChildren.first().margins?.start ?: 0f) +
                    max(paddings?.end ?: 0f, internalChildren.last().margins?.end ?: 0f)
        } else {
            internalChildren.maxOf {
                it.width +
                        (border?.thickness ?: 0f) * 2 +
                        max(paddings?.start ?: 0f, it.margins?.start ?: 0f) +
                        max(paddings?.end ?: 0f, it.margins?.end ?: 0f)
            }
        }
    }

    private fun calculateRowWidth(row: List<Box>, border: Border?, paddings: Paddings?): Float {
        return row.sumOf { it.width.toDouble() }.toFloat() +
                (border?.thickness ?: 0f) * 2 +
                // Sum of all the collapsing margins between each pair of children
                row.zipWithNext { a, b -> max(a.margins?.end ?: 0f, b.margins?.start ?: 0f) }.sum() +
                max(paddings?.start ?: 0f, row.first().margins?.start ?: 0f) +
                max(paddings?.end ?: 0f, row.last().margins?.end ?: 0f)
    }

    private fun calculateColumnHeight(column: List<Box>, border: Border?, paddings: Paddings?): Float {
        return column.sumOf { it.height.toDouble() }.toFloat() +
                (border?.thickness ?: 0f) * 2 +
                // Sum of all the collapsing margins between each pair of children
                column.zipWithNext { a, b -> max(a.margins?.bottom ?: 0f, b.margins?.top ?: 0f) }.sum() +
                max(paddings?.top ?: 0f, column.first().margins?.top ?: 0f) +
                max(paddings?.bottom ?: 0f, column.last().margins?.bottom ?: 0f)
    }

    private fun calculateHeight(): Float {
        if (internalChildren.isEmpty()) {
            return ((border?.thickness ?: 0f) * 2) + (paddings?.vertical ?: 0f)
        }
        return if (internalLayoutDirection == LayoutDirection.HORIZONTAL) {
            internalChildren.maxOf {
                it.height +
                        (border?.thickness ?: 0f) * 2 +
                        max(paddings?.top ?: 0f, it.margins?.top ?: 0f) +
                        max(paddings?.bottom ?: 0f, it.margins?.bottom ?: 0f)
            }
        } else {
            internalChildren.sumOf { it.height.toDouble() }.toFloat() +
            (border?.thickness ?: 0f) * 2 +
            // Sum of all the collapsing margins between each pair of children
                    internalChildren.zipWithNext { a, b -> max(a.margins?.bottom ?: 0f, b.margins?.top ?: 0f) }.sum() +
            max(paddings?.top ?: 0f, internalChildren.first().margins?.top ?: 0f) +
            max(paddings?.bottom ?: 0f, internalChildren.last().margins?.bottom ?: 0f)
        }
    }

    override fun layOut(top: Float, start: Float, drawDirection: DrawDirection) {
        bounds.set(start, top, start + width, top + height) // Used to draw the background
        val borderOffset = (border?.thickness?: 0f) / 2f
        borderBounds.set(bounds.left + borderOffset, bounds.top + borderOffset, bounds.right - borderOffset, bounds.bottom - borderOffset)
        val positions = arrangeChildren(internalChildren, internalLayoutDirection, drawDirection, childrenAlignment, Coordinates(start, top), wrapping, paddings, border, maxAvailableWidth, maxAvailableHeight,)
        for ((i, child) in internalChildren.withIndex()) {
            child.layOut(positions[i].y, positions[i].x, drawDirection)
        }
    }

    /**
     * Border is drawn on top of the background. So call [drawBorder] after [drawBackground].
     */
    override fun draw(canvas: Canvas) {
        canvas.withClip(bounds) {
            drawBackground(canvas)
            drawBorder(canvas)
            for (child in internalChildren) {
                child.draw(canvas)
            }
        }
    }

    private fun drawBorder(canvas: Canvas) {
        border?.let { border ->
            paint.strokeWidth = border.thickness
            paint.style = Paint.Style.STROKE
            paint.color = border.color
            paint.alpha = (border.alpha * 255).toInt() // Setting alpha should be *AFTER* setting the color to override the color alpha
            if (border.type == PieChart.BorderType.DASHED) {
                border.dashArray?.let { paint.pathEffect = DashPathEffect(it.toFloatArray(), 0f) }
            }
            canvas.drawRoundRect(borderBounds, border.cornerRadius, border.cornerRadius, paint)
        }
    }

    // FIXME: background corner radius is not implemented (its xml and kotlin property)
    private fun drawBackground(canvas: Canvas) {
        background?.let { background ->
            paint.style = Paint.Style.FILL
            paint.color = background.color
            paint.alpha = (background.alpha * 255).toInt() // Setting alpha should be *AFTER* setting the color to override the color alpha
            canvas.drawRoundRect(bounds, background.cornerRadius, background.cornerRadius, paint)
        }
    }
}
