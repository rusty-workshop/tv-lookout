package com.rusty.lookout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A small line chart: a few series drawn over shared min/max, with three
 * light gridlines and their values on the left. Each series is spread across
 * the full width on its own, so series with different point counts (ratings
 * per speed) still read as trends side by side.
 */
class LineChart(context: Context) : View(context) {

    class Series(val points: List<Float>, val color: Int)

    private var series = listOf<Series>()
    private var unit = ""

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply { color = Color.parseColor("#1FFFFFFF"); strokeWidth = 2f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A8C9C")
        textSize = 22f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    fun setData(data: List<Series>, unit: String = "") {
        series = data.filter { it.points.isNotEmpty() }
        this.unit = unit
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (series.isEmpty()) return
        val all = series.flatMap { it.points }
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 1f) { lo -= 1f; hi += 1f }
        // Round the range outward to tidy gridline values.
        val step = niceStep((hi - lo) / 2)
        lo = floor(lo / step) * step
        hi = ceil(hi / step) * step

        val left = 80f
        val top = 14f
        val w = width - left - 14f
        val h = height - top - 14f
        for (i in 0..2) {
            val v = lo + (hi - lo) * i / 2
            val y = top + h - h * i / 2
            c.drawLine(left, y, left + w, y, grid)
            c.drawText("${v.toInt()}$unit", 0f, y + 8f, label)
        }
        for (s in series) {
            line.color = s.color
            dot.color = s.color
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val x = if (s.points.size == 1) left + w else left + w * i / (s.points.size - 1)
                val y = top + h - h * (p - lo) / (hi - lo)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (i == s.points.lastIndex) c.drawCircle(x, y, 9f, dot)
            }
            c.drawPath(path, line)
        }
    }

    private fun niceStep(raw: Float): Float {
        if (raw <= 0f) return 1f
        var mag = 1f
        while (mag * 10 <= raw) mag *= 10
        while (mag > raw) mag /= 10
        return listOf(1f, 2f, 5f, 10f).map { it * mag }.first { it >= raw }
    }
}
