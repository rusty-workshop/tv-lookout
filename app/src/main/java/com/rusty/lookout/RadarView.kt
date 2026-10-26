package com.rusty.lookout

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
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
    private val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f; color = Color.parseColor("#66C98BDB") }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC0C0E14") }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val placed = mutableListOf<RectF>()
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
        // A narrow, faint trail behind a thin beam line.
        sweepPaint.shader = SweepGradient(cx, cy,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.parseColor("#22AA5CC3")),
            floatArrayOf(0f, 0.93f, 1f))
        c.drawCircle(cx, cy, r, sweepPaint)
        c.drawLine(cx, cy, cx + r, cy, beam)
        c.restore()

        c.drawLine(cx - r, cy, cx + r, cy, axis)
        c.drawLine(cx, cy - r, cx, cy + r, axis)
        placed.clear()
        for (nm in listOf(10.0, 20.0, 40.0)) {
            val rr = (r * nm / rangeNm).toFloat()
            c.drawCircle(cx, cy, rr, ring)
            // Ring labels sit on the lower-right diagonal, away from "N"; plane labels avoid them.
            val d = rr * 0.7071f
            val text = "${nm.toInt()} nm"
            c.drawText(text, cx + d + 6f, cy + d + 20f, label)
            placed += RectF(cx + d + 2f, cy + d - 2f, cx + d + 10f + label.measureText(text), cy + d + 26f)
        }
        c.drawText("N", cx - 8f, cy - r + 30f, label)
        c.drawCircle(cx, cy, 9f, home)

        val nmPerDegLat = 60.0
        val nmPerDegLon = 60.0 * cos(Math.toRadians(centerLat))
        // Background traffic first, small and dimmed; the labelled nearest flights on top.
        for (p in planes.sortedBy { it.highlight }) {
            val dx = (p.lon - centerLon) * nmPerDegLon
            val dy = (p.lat - centerLat) * nmPerDegLat
            val x = cx + (r * dx / rangeNm).toFloat()
            val y = cy - (r * dy / rangeNm).toFloat()
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > r * r) continue
            val color = altitudeColor(p.alt)
            plane.color = color
            plane.alpha = if (p.highlight) 255 else 140
            if (p.highlight) {
                halo.color = color
                halo.alpha = 110
                c.drawCircle(x, y, 22f, halo)
            }
            c.save()
            c.rotate(p.track, x, y)
            arrow.reset()
            val s = if (p.highlight) 14f else 8f
            arrow.moveTo(x, y - s)
            arrow.lineTo(x + s * 0.7f, y + s)
            arrow.lineTo(x, y + s * 0.45f)
            arrow.lineTo(x - s * 0.7f, y + s)
            arrow.close()
            c.drawPath(arrow, plane)
            c.restore()
            if (p.highlight) label(c, p.callsign, x, y, color)
        }
    }

    /**
     * Callsign in a small dark pill beside the plane. Tries right, left, below
     * and above, and skips the label if all four would overlap one already drawn.
     */
    private fun label(c: Canvas, text: String, x: Float, y: Float, color: Int) {
        val w = callsign.measureText(text) + 16f
        val h = 32f
        val spots = listOf(x + 26f to y - h / 2, x - 26f - w to y - h / 2, x - w / 2 to y + 24f, x - w / 2 to y - 24f - h)
        for ((lx, ly) in spots) {
            val box = RectF(lx, ly, lx + w, ly + h)
            if (placed.any { RectF.intersects(it, box) }) continue
            placed += box
            c.drawRoundRect(box, 10f, 10f, pill)
            callsign.color = color
            c.drawText(text, lx + 8f, ly + 23f, callsign)
            return
        }
    }

    private fun altitudeColor(ft: Int) = Color.parseColor(when {
        ft < 5000 -> "#FDBA74"
        ft < 15000 -> "#FDE047"
        ft < 30000 -> "#86EFAC"
        else -> "#7DD3FC"
    })
}
