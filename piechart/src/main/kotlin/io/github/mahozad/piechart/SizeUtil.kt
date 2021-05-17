package io.github.mahozad.piechart

import android.util.Log
import android.view.View
import kotlin.math.min

class SizeUtil {

    /**
     * Extracted the calculation logics to a separate class and function to be testable.
     */
    fun calculateWidthAndHeight(widthMeasureSpec: Int, heightMeasureSpec: Int): Pair<Int, Int> {
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
        val min = min(widthSize, heightSize)
        Log.d("SizeUtil", "width: ${View.MeasureSpec.toString(widthMeasureSpec)}")
        Log.d("SizeUtil", "height: ${View.MeasureSpec.toString(heightMeasureSpec)}")
        Log.d("SizeUtil", "min: $min")
        return Pair(min, min)
    }

    internal data class Coordinate(val x: Float, val y: Float)

    /**
     * Note that this function should get paddingLeft and PaddingRight
     *  not paddingStart and paddingEnd.
     *
     */
    internal fun calculateCenterCoordinate(
        width: Int,
        height: Int,
        paddingLeft: Int,
        paddingRight: Int,
        paddingTop: Int,
        paddingBottom: Int
    ): Coordinate {
        val centerX = (width + paddingLeft - paddingRight) / 2f
        val centerY = (height + paddingTop - paddingBottom) / 2f
        return Coordinate(centerX, centerY)
    }
}
