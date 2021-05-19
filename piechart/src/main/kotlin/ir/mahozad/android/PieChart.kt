package ir.mahozad.android

import android.content.Context
import android.graphics.*
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.core.content.ContextCompat
import androidx.core.graphics.minus
import ir.mahozad.android.PieChart.DrawDirection.CLOCKWISE
import ir.mahozad.android.PieChart.GapPosition.MIDDLE
import ir.mahozad.android.PieChart.GradientType.RADIAL
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val DEFAULT_SIZE = 256 /* dp */
const val DEFAULT_START_ANGLE = -90
const val DEFAULT_HOLE_RATIO = 0.25f
const val DEFAULT_OVERLAY_RATIO = 0.55f
const val DEFAULT_OVERLAY_ALPHA = 0.25f
const val DEFAULT_GAP = 8f /* px */
const val DEFAULT_LABEL_SIZE = 24f /* sp */
const val DEFAULT_LABEL_OFFSET = 0.75f
const val DEFAULT_CENTER_LABEL = ""
val defaultGapPosition = MIDDLE
val defaultGradientType = RADIAL
val defaultDrawDirection = CLOCKWISE

/**
 * This is the order that these commonly used view methods are run:
 * 1. Constructor    // choose your desired size
 * 2. onMeasure      // parent will determine if your desired size is acceptable
 * 3. onSizeChanged
 * 4. onLayout
 * 5. onDraw         // draw your view content at the size specified by the parent
 *
 * Any time that you make a change to your view that affects the appearance but not the size,
 * then call invalidate(). This will cause onDraw to be called again (but not all of those other previous methods).
 *
 * Any time that you make a change to your view that would affect the size, then call requestLayout().
 * This will start the process of measuring and drawing all over again from onMeasure.
 * This call is usually accompanied (preceded) by a call to invalidate().
 *
 * See [this helpful post](https://stackoverflow.com/a/42430834) for more information.
 */
class PieChart(context: Context, attrs: AttributeSet) : View(context, attrs) {

    data class Slice(
        @FloatRange(from = 0.0, to = 1.0) val fraction: Float,
        @ColorInt val color: Int,
        @ColorInt val colorEnd: Int = color,
        val label: String = NumberFormat.getPercentInstance().format(fraction),
        /**
         * Can also set the default value to the slice fraction.
         *
         * Scale the slice like this:
         * ```kotlin
         * val scaleMatrix = Matrix()
         * scaleMatrix.setScale(slice.scale, slice.scale, centerX, centerY)
         * piePath.transform(scaleMatrix)
         * ```
         * Or with the canvas which seems to scale the whole drawing:
         * ```kotlin
         * canvas.scale(slice.scale, slice.scale, centerX, centerY)
         * ```
         */
        @FloatRange(from = 0.0, to = 1.0) val scale: Float = 1f
    )

    enum class DrawDirection { CLOCKWISE, COUNTER_CLOCKWISE }
    enum class GradientType { RADIAL, SWEEP }
    enum class GapPosition { MIDDLE, PRECEDING_SLICE, SUCCEEDING_SLICE }

    var startAngle = DEFAULT_START_ANGLE
        set(angle) {
            field = angle.coerceIn(-360, 360)
            invalidate()
        }
    var holeRatio = DEFAULT_HOLE_RATIO
        set(ratio) {
            field = ratio.coerceIn(0f, 1f)
            invalidate()
        }
    var overlayRatio = DEFAULT_OVERLAY_RATIO
        set(ratio) {
            field = ratio.coerceIn(0f, 1f)
            invalidate()
        }
    var overlayAlpha = DEFAULT_OVERLAY_ALPHA
        set(alpha) {
            field = alpha.coerceIn(0f, 1f)
            invalidate()
        }
    var gap = DEFAULT_GAP
        set(width) {
            field = width
            invalidate()
        }
    var labelSize = spToPx(DEFAULT_LABEL_SIZE)
        set(size /* px */) {
            field = size
            invalidate()
        }
    var labelOffset = DEFAULT_LABEL_OFFSET
        set(offset) {
            field = offset
            invalidate()
        }
    var centerLabel = DEFAULT_CENTER_LABEL
    var gapPosition = MIDDLE
    var gradientType = defaultGradientType
    var drawDirection = defaultDrawDirection
    val slices = mutableListOf(
        Slice(0.43f, ContextCompat.getColor(context, android.R.color.holo_green_dark)),
        Slice(0.21f, ContextCompat.getColor(context, android.R.color.holo_orange_dark)),
        Slice(0.19f, ContextCompat.getColor(context, android.R.color.holo_blue_dark)),
        Slice(0.14f, ContextCompat.getColor(context, android.R.color.holo_red_light)),
        Slice(0.03f, ContextCompat.getColor(context, android.R.color.holo_purple))
    )
    private val pie = Path()
    private val clip = Path()
    private val overlay = Path()
    private val mainPaint: Paint = Paint(ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val enclosingRect = RectF()
    private var pieRadius = 0f
    private var centerX = 0f
    private var centerY = 0f

    /**
     * Attributes are a powerful way of controlling the behavior and appearance of views,
     * but they can only be read when the view is initialized. To provide dynamic behavior,
     * expose a property getter and setter pair for each custom attribute.
     *
     * A good rule to follow is to always expose any property that affects the
     * visible appearance or behavior of your custom view.
     */
    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.PieChart, 0, 0).apply {
            try {
                startAngle = getInt(R.styleable.PieChart_startAngle, DEFAULT_START_ANGLE)
                holeRatio = getFloat(R.styleable.PieChart_holeRatio, DEFAULT_HOLE_RATIO)
                overlayRatio = getFloat(R.styleable.PieChart_overlayRatio, DEFAULT_OVERLAY_RATIO)
                overlayAlpha = getFloat(R.styleable.PieChart_overlayAlpha, DEFAULT_OVERLAY_ALPHA)
                gap = getDimension(R.styleable.PieChart_gap, DEFAULT_GAP)
                labelSize = getDimension(R.styleable.PieChart_labelSize, spToPx(DEFAULT_LABEL_SIZE))
                labelOffset = getFloat(R.styleable.PieChart_labelOffset, DEFAULT_LABEL_OFFSET)
                centerLabel = getString(R.styleable.PieChart_centerLabel) ?: DEFAULT_CENTER_LABEL
                gapPosition = GapPosition.values()[
                        getInt(R.styleable.PieChart_gapPosition, defaultGapPosition.ordinal)
                ]
                gradientType = GradientType.values()[
                        getInt(R.styleable.PieChart_gradientType, defaultGradientType.ordinal)
                ]
                drawDirection = DrawDirection.values()[
                        getInt(R.styleable.PieChart_drawDirection, defaultDrawDirection.ordinal)
                ]
            } finally {
                // TypedArray objects are a shared resource and must be recycled after use
                recycle()
            }
        }
    }

    /**
     * This method is called when your view is first assigned a size, and again
     * if the size of your view changes for any reason.
     *
     * Calculate positions, dimensions, and any other values related to your
     * view's size in onSizeChanged(), instead of recalculating them every time you draw.
     *
     * When your view is assigned a size, the layout manager assumes that
     * the size includes all of the view's padding.
     * You must handle the padding values when you calculate your view's size.
     */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        pieRadius = calculateRadius(width, height, paddingLeft, paddingRight, paddingTop, paddingBottom)
        val (x, y) = calculateCenter(width, height, paddingLeft, paddingRight, paddingTop, paddingBottom)
        centerX = x
        centerY = y
        val (top, left, right, bottom) = calculateBoundaries(centerX, centerY, pieRadius)
        enclosingRect.set(RectF(left, top, right, bottom))
        pie.reset()
        val holeRadius = holeRatio * pieRadius
        val overlayRadius = overlayRatio * pieRadius
        overlay.set(Path().apply { addCircle(centerX, centerY, overlayRadius, Path.Direction.CW) })
        val rect = Path().apply { addRect(enclosingRect, Path.Direction.CW) }
        val hole = Path().apply { addCircle(centerX, centerY, holeRadius, Path.Direction.CW) }
        val gaps = makeGaps()
        // Could also have set the fillType to EVEN_ODD and just add the other paths to the clip
        // Or could abandon using clip path and do the operations on the pie itself
        clip.set(rect - hole - gaps)
    }

    private fun makeGaps(): Path {
        val gaps = Path()
        var angle = startAngle.toFloat()
        for (slice in slices) {
            angle += slice.fraction * 360
            val (c1, c2, c3, c4) = calculateGapCoordinates(centerX, centerY, angle, gap, pieRadius, gapPosition)
            gaps.moveTo(c1.x, c1.y)
            gaps.lineTo(c2.x, c2.y)
            gaps.lineTo(c3.x, c3.y)
            gaps.lineTo(c4.x, c4.y)
            gaps.close()
        }
        return gaps
    }

    private fun spToPx(sp: Float) = sp * resources.displayMetrics.scaledDensity

    private fun pxToSp(px: Float) = px / resources.displayMetrics.scaledDensity

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density

    private fun pxToDp(px: Float) = px / resources.displayMetrics.density

    private fun Float.toRadian() = (this / 180) * PI.toFloat()

    /**
     * The clip path (and maybe some other features) do not work on
     * emulators with hardware acceleration enabled.
     *
     * Try to disable hardware acceleration of device
     * or disable hardware acceleration for activity or whole application
     * or call `setLayerType(LAYER_TYPE_SOFTWARE, null)` here to use software rendering.
     * See the following posts:
     * [1](https://stackoverflow.com/q/16889815),
     * [2](https://stackoverflow.com/q/16432565),
     * [3](https://stackoverflow.com/q/8895677),
     * [4](https://stackoverflow.com/a/23517980),
     * [5](https://stackoverflow.com/q/13672802)
     *
     * Another solution would be to not use clip path and instead use the
     * operations on the paths themselves
     * (differencing the gaps and the hole from the pie and the overlay path).
     */
    override fun onDraw(canvas: Canvas) {
        /**
         * The android.graphics framework divides drawing into two areas:
         * - What to draw, handled by Canvas
         * - How to draw, handled by Paint.
         * Simply put, Canvas defines shapes that you can draw on the screen,
         * while Paint defines the color, style, font, and so forth of each shape you draw.
         *
         * So, before you can call any drawing methods, it's necessary to create a Paint object.
         *
         * Creating objects ahead of time is an important optimization. Views are redrawn very frequently,
         * and many drawing objects require expensive initialization. Creating drawing objects within your
         * onDraw() method significantly reduces performance and can make your UI appear sluggish.
         */
        // clipping should be applied before drawing other things
        canvas.clipPath(clip)

        var currentAngle = startAngle.toFloat()
        for (slice in slices) {

            val gradient = if (gradientType == RADIAL) {
                RadialGradient(centerX, centerY, pieRadius, slice.color, slice.colorEnd, Shader.TileMode.MIRROR)
            } else {
                val colors = slices.map {  listOf(it.color, it.colorEnd) }.flatten().toIntArray()
                val positions = slices.map { it.fraction }
                    .scan(listOf(0f)) { acc, value -> listOf(acc.first() + value, acc.first() + value) }
                    .flatten()
                    .dropLast(1)
                    .toFloatArray()
                val sweepGradient = SweepGradient(centerX, centerY, colors, positions)
                // Fix the rotation start
                val sweepGradientDefaultStartAngle = 0f
                val rotate = startAngle - sweepGradientDefaultStartAngle
                val gradientMatrix = Matrix()
                gradientMatrix.preRotate(rotate, centerX, centerY)
                sweepGradient.setLocalMatrix(gradientMatrix)
                sweepGradient
            }

            mainPaint.shader = gradient
            val sliceSweep = slice.fraction * 360
            pie.reset()
            pie.moveTo(centerX, centerY)
            pie.arcTo(enclosingRect, currentAngle, sliceSweep)
            canvas.drawPath(pie, mainPaint)

            mainPaint.shader = null // Clear the gradient
            mainPaint.color = ContextCompat.getColor(context, android.R.color.black)
            mainPaint.textSize = labelSize
            mainPaint.textAlign = Paint.Align.CENTER
            val label = slice.label

            // For help on text dimensions see https://stackoverflow.com/a/42091739
            val bounds = Rect()
            mainPaint.getTextBounds(label, 0, label.length, bounds)
            val textHeight = bounds.height()

            val endAngle = (currentAngle + sliceSweep) % 360
            val middleAngle = ((currentAngle + endAngle) / 2 % 360).toRadian()
            val x = centerX + cos(middleAngle) * pieRadius * labelOffset
            val y = centerY + sin((middleAngle)) * pieRadius * labelOffset
            canvas.drawText(label, x, y + (textHeight / 2), mainPaint)

            currentAngle += sliceSweep
        }

        // The center label gets clipped by the clip path and is not shown
        // mainPaint.color = ContextCompat.getColor(context, android.R.color.black)
        // mainPaint.textSize = labelSize
        // mainPaint.textAlign = Paint.Align.CENTER
        // val bounds = Rect()
        // mainPaint.getTextBounds(centerLabel, 0, centerLabel.length, bounds)
        // val textHeight = bounds.height()
        // canvas.drawText(centerLabel, centerX, centerY + (textHeight / 2), mainPaint)

        mainPaint.color = ContextCompat.getColor(context, android.R.color.black) // or better Color.BLACK
        mainPaint.alpha = (overlayAlpha * 255).toInt()
        canvas.drawPath(overlay, mainPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val (width, height) = calculateWidthAndHeight(widthMeasureSpec, heightMeasureSpec)
        // This MUST be called
        setMeasuredDimension(width, height)
    }
}