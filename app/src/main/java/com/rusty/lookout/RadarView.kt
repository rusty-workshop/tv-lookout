package com.rusty.lookout

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min

/**
 * A radar-style map of aircraft around home: north up, range rings, a slow
 * sweep, and each plane as an arrow pointing along its track, coloured by
 * altitude (low = warm, high = cool) with its callsign beside it.
 */
class RadarView(context: Context) : View(context) {

    class Plane(val lat: Double, val lon: Double, val track: Float, val alt: Int,
                val callsign: String, val highlight: Boolean)

    private var centerLat = 30.33218
    private var centerLon = -81.65565
    private var rangeNm = 40.0
    private var planes = listOf<Plane>()
    private var sweep = 0f

    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#33AA5CC3")
    }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f; color = Color.parseColor("#1FFFFFFF") }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A8C9C"); textSize = 22f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val callsign = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val plane = Paint(Paint.ANTI_ALIAS_FLAG)
    private val home = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C98BDB") }
    private val arrow = Path()

    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 6000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { sweep = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        spinner.start()
    }

    override fun onDetachedFromWindow() {
        spinner.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // Only spend frames on the sweep while the Sky page is on screen.
        when {
            !isVisible -> spinner.pause()
            spinner.isPaused -> spinner.resume()
            !spinner.isStarted -> spinner.start()
        }
    }

    fun setData(lat: Double, lon: Double, range: Double, list: List<Plane>) {
        centerLat = lat
        centerLon = lon
        rangeNm = range
        planes = list
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 4f
        disc.shader = RadialGradient(cx, cy, r, Color.parseColor("#1A2A1F33"), Color.parseColor("#0A0C12"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, disc)

        // Sweep: a soft wedge trailing the beam.
        c.save()
        c.rotate(sweep - 90f, cx, cy)
        sweepPaint.shader = SweepGradient(cx, cy,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.parseColor("#40AA5CC3")),
            floatArrayOf(0f, 0.85f, 1f))
        c.drawCircle(cx, cy, r, sweepPaint)
        c.restore()

        c.drawLine(cx - r, cy, cx + r, cy, axis)
        c.drawLine(cx, cy - r, cx, cy + r, axis)
        for (nm in listOf(10.0, 20.0, 40.0)) {
            val rr = (r * nm / rangeNm).toFloat()
            c.drawCircle(cx, cy, rr, ring)
            c.drawText("${nm.toInt()} nm", cx + 8f, cy - rr + 26f, label)
        }
        c.drawText("N", cx - 8f, cy - r + 28f, label)
        c.drawCircle(cx, cy, 9f, home)

        val nmPerDegLat = 60.0
        val nmPerDegLon = 60.0 * cos(Math.toRadians(centerLat))
        for (p in planes) {
            val dx = (p.lon - centerLon) * nmPerDegLon
            val dy = (p.lat - centerLat) * nmPerDegLat
            val x = cx + (r * dx / rangeNm).toFloat()
            val y = cy - (r * dy / rangeNm).toFloat()
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > r * r) continue
            val color = altitudeColor(p.alt)
            plane.color = color
            c.save()
            c.rotate(p.track, x, y)
            arrow.reset()
            val s = if (p.highlight) 15f else 11f
            arrow.moveTo(x, y - s)
            arrow.lineTo(x + s * 0.7f, y + s)
            arrow.lineTo(x, y + s * 0.45f)
            arrow.lineTo(x - s * 0.7f, y + s)
            arrow.close()
            c.drawPath(arrow, plane)
            c.restore()
            if (p.highlight) {
                callsign.color = color
                c.drawText(p.callsign, x + 18f, y + 8f, callsign)
            }
        }
    }

    private fun altitudeColor(ft: Int) = Color.parseColor(when {
        ft < 5000 -> "#FDBA74"
        ft < 15000 -> "#FDE047"
        ft < 30000 -> "#86EFAC"
        else -> "#7DD3FC"
    })
}
