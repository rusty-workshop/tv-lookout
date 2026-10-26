package com.rusty.lookout

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * A chessboard drawn on the TV itself (Lichess brown, cburnett pieces), so a
 * new position shows the moment it arrives. A move slides its piece from the
 * old square to the new one.
 */
class BoardView(context: Context) : View(context) {

    private val light = Paint().apply { color = Color.parseColor("#F0D9B5") }
    private val dark = Paint().apply { color = Color.parseColor("#B58863") }
    private val lastLight = Paint().apply { color = Color.parseColor("#CDD26A") }
    private val lastDark = Paint().apply { color = Color.parseColor("#AAA23A") }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()

    private val pieces: Map<Char, Bitmap> = "KQRBNPkqrbnp".associateWith { c ->
        val name = (if (c.isUpperCase()) "w" else "b") + c.lowercaseChar()
        val id = resources.getIdentifier("piece_$name", "drawable", context.packageName)
        BitmapFactory.decodeResource(resources, id)
    }

    private var board = Array(8) { CharArray(8) { ' ' } }  // [rank 0 = 8th rank][file]
    private var flipped = false
    private var last: Pair<Int, Int>? = null  // from, to as square index 0..63 (a1 = 0)

    // The sliding piece while a move animates.
    private var moving: Char = ' '
    private var moveFrom = 0
    private var moveTo = 0
    private var progress = 1f
    private var animator: ValueAnimator? = null

    fun setPosition(fen: String, lastMove: String?, orientationBlack: Boolean) {
        val next = parse(fen) ?: return
        flipped = orientationBlack
        val move = lastMove?.takeIf { it.length >= 4 }?.let { square(it.substring(0, 2)) to square(it.substring(2, 4)) }
        animator?.cancel()
        if (move != null && move != last && pieceAt(board, move.first) != ' ') {
            // Slide what was on the from-square; the new board already has it on the to-square.
            moving = pieceAt(next, move.second)
            moveFrom = move.first
            moveTo = move.second
            progress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 160
                interpolator = DecelerateInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            progress = 1f
        }
        board = next
        last = move
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val sq = width / 8f
        clip.reset()
        clip.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 22f, 22f, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        for (r in 0..7) for (f in 0..7) {
            val idx = (7 - r) * 8 + f
            val isLight = (r + f) % 2 == 0
            val highlighted = last?.let { idx == it.first || idx == it.second } == true
            val paint = when {
                highlighted && isLight -> lastLight
                highlighted -> lastDark
                isLight -> light
                else -> dark
            }
            val (x, y) = origin(idx, sq)
            c.drawRect(x, y, x + sq, y + sq, paint)
        }
        for (r in 0..7) for (f in 0..7) {
            val p = board[r][f]
            val idx = (7 - r) * 8 + f
            if (p == ' ' || (progress < 1f && idx == moveTo)) continue
            val (x, y) = origin(idx, sq)
            drawPiece(c, p, x, y, sq)
        }
        if (progress < 1f && moving != ' ') {
            val (fx, fy) = origin(moveFrom, sq)
            val (tx, ty) = origin(moveTo, sq)
            drawPiece(c, moving, fx + (tx - fx) * progress, fy + (ty - fy) * progress, sq)
        }
        c.restore()
    }

    private fun drawPiece(c: Canvas, p: Char, x: Float, y: Float, sq: Float) {
        val bmp = pieces[p] ?: return
        c.drawBitmap(bmp, null, RectF(x, y, x + sq, y + sq), bitmapPaint)
    }

    /** Top-left corner of a square on screen, honouring board orientation. */
    private fun origin(idx: Int, sq: Float): Pair<Float, Float> {
        val file = idx % 8
        val rank = idx / 8
        val col = if (flipped) 7 - file else file
        val row = if (flipped) rank else 7 - rank
        return col * sq to row * sq
    }

    private fun parse(fen: String): Array<CharArray>? {
        val rows = fen.substringBefore(' ').split('/')
        if (rows.size != 8) return null
        return Array(8) { r ->
            val out = CharArray(8) { ' ' }
            var f = 0
            for (ch in rows[r]) {
                if (ch.isDigit()) f += ch - '0' else if (f < 8) out[f++] = ch
            }
            out
        }
    }

    private fun square(s: String) = (s[1] - '1') * 8 + (s[0] - 'a')

    private fun pieceAt(b: Array<CharArray>, idx: Int) = b[7 - idx / 8][idx % 8]
}
