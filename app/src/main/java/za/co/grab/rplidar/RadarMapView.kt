package za.co.grab.rplidar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class RadarMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Storage for the raw points
    private var angles: FloatArray = floatArrayOf()
    private var distances: FloatArray = floatArrayOf()

    // Dynamic scale bounds (Default: 12 meters)
    private var maxRangeMm = 12000f
    private val minAllowedRangeMm = 1500f   // Zoomed all the way in (1.5m)
    private val maxAllowedRangeMm = 15000f  // Zoomed all the way out (15m)

    // Paintbrushes for drawing
    private val pointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    // Paintbrush for the radar background grid rings
    private val gridPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // Set up the Pinch-to-Zoom detector
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // detector.scaleFactor is > 1.0 when fingers spread apart, and < 1.0 when pinching together
            // Because increasing maxRangeMm zooms OUT, we divide by the factor to invert the action naturally
            maxRangeMm /= detector.scaleFactor

            // Clamp the value so the user can't zoom infinitely far in or out
            if (maxRangeMm < minAllowedRangeMm) maxRangeMm = minAllowedRangeMm
            if (maxRangeMm > maxAllowedRangeMm) maxRangeMm = maxAllowedRangeMm

            invalidate() // Redraw immediately during the gesture
            return true
        }
    })

    // Route touch events from the screen into our scale detector
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        return true
    }

    // This function updates the data and forces the screen to redraw
    fun updateData(newAngles: FloatArray, newDistances: FloatArray) {
        this.angles = newAngles
        this.distances = newDistances
        invalidate() // Tells Android to trigger onDraw() immediately
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Find the center of your screen
        val centerX = width / 2f
        val centerY = height / 2f

        // 2. Set a scale factor for the maximum 12-meter range (12000mm)
        //val maxRangeMm = 12000f
        val scale = width / (maxRangeMm * 2f)

        // Draw reference grid rings so you know where 3m, 6m, 9m, and 12m boundaries are
        canvas.drawCircle(centerX, centerY, 3000f * scale, gridPaint)  // 3 Meters
        canvas.drawCircle(centerX, centerY, 6000f * scale, gridPaint)  // 6 Meters
        canvas.drawCircle(centerX, centerY, 9000f * scale, gridPaint)  // 9 Meters
        canvas.drawCircle(centerX, centerY, 12000f * scale, gridPaint) // 12 Meters

        // Draw a small center point representing your physical LiDAR position
        canvas.drawCircle(centerX, centerY, 10f, pointPaint)

        // 3. Loop through and draw each point
        if (angles.isNotEmpty()) {
            for (i in angles.indices) {
                val angleDeg = angles[i]
                val distanceMm = distances[i]

                // Validate distance bounds up to 12 meters
                if (distanceMm <= 0 || distanceMm > maxRangeMm) continue

                val angleRad = Math.toRadians(angleDeg.toDouble())
                val xOffset = distanceMm * cos(angleRad) * scale
                val yOffset = distanceMm * sin(angleRad) * scale

                val pixelX = centerX - xOffset.toFloat()
                val pixelY = centerY - yOffset.toFloat()

                // Draw the point on the map
                canvas.drawPoint(pixelX, pixelY, pointPaint)
            }
        }
    }
}