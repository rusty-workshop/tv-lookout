package com.rusty.lookout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The whole Lookout display: clock + weather, homelab status, and chess pages,
 * fed by latitude-lookout's /api/feed. Shared by the app (MainActivity) and the
 * screensaver (LookoutDream).
 *
 * Everything is laid out on a fixed 1920x1080 canvas that's scaled to the
 * screen, so positions match the Roku version one for one.
 */
class LookoutView(context: Context, private val screensaver: Boolean) : FrameLayout(context) {

    private val canvas = FrameLayout(context)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private val images = HashMap<String, Bitmap>()

    private val pages: List<FrameLayout>
    private val dots = mutableListOf<View>()
    private var index = 0
    private var pausedUntil = 0L
    private var running = false

    // clock page
    private val bigTime: TextView
    private val bigDate: TextView
    private val issText: TextView
    private val temp: TextView
    private val summary: TextView
    private val details: TextView
    private val hours = FrameLayout(context)
    // status page
    private val statusSummary: TextView
    private val serviceGrid = FrameLayout(context)
    private val backupText: TextView
    private val ripperText: TextView
    private val ripperBarBg: View
    private val ripperBar: View
    // chess page
    private val chessSummary: TextView
    private val boards = mutableListOf<ImageView>()
    private val gameLabels = mutableListOf<TextView>()
    private val noGames: TextView
    private val moreGames: TextView
    private val puzzleBoard: ImageView
    private val puzzleText: TextView
    // over everything
    private val alertBar = FrameLayout(context)
    private val alertText: TextView
    private val cornerTime: TextView

    init {
        setBackgroundColor(BG)
        addView(canvas, LayoutParams(W, H))
        canvas.pivotX = 0f
        canvas.pivotY = 0f

        val clockPage = page()
        bigTime = label(clockPage, 120, 130, 230, WHITE, bold = true)
        bigDate = label(clockPage, 130, 420, 56, MUTED)
        issText = label(clockPage, 130, 545, 36, MUTED, width = 980)
        temp = label(clockPage, 1180, 130, 170, WHITE, bold = true)
        summary = label(clockPage, 1190, 365, 52, ACCENT, bold = true)
        details = label(clockPage, 1190, 440, 36, MUTED, width = 680).apply { setLineSpacing(10f, 1f) }
        rect(clockPage, 120, 700, 1680, 2, LINE)
        place(clockPage, hours, 120, 740, 1680, 260)

        val statusPage = page()
        label(statusPage, 120, 70, 76, WHITE, bold = true).text = "Homelab"
        statusSummary = label(statusPage, 120, 180, 38, MUTED, width = 1200)
        place(statusPage, serviceGrid, 120, 270, 1200, 680)
        label(statusPage, 1360, 270, 30, ACCENT, bold = true).text = "BACKUP"
        backupText = label(statusPage, 1360, 315, 40, WHITE, width = 460)
        label(statusPage, 1360, 500, 30, ACCENT, bold = true).text = "DVD RIPPER"
        ripperText = label(statusPage, 1360, 545, 40, WHITE, width = 460)
        ripperBarBg = rect(statusPage, 1360, 720, 460, 16, LINE)
        ripperBar = rect(statusPage, 1360, 720, 0, 16, ACCENT)

        val chessPage = page()
        label(chessPage, 120, 70, 76, WHITE, bold = true).text = "Chess"
        chessSummary = label(chessPage, 120, 180, 38, MUTED, width = 1700)
        for (x in listOf(120, 780)) {
            boards += image(chessPage, x, 270, 560)
            gameLabels += label(chessPage, x, 850, 38, WHITE, width = 560)
        }
        noGames = label(chessPage, 120, 420, 56, MUTED, width = 1220).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = "No games in progress"
        }
        moreGames = label(chessPage, 1420, 920, 34, MUTED, width = 400)
        label(chessPage, 1420, 240, 30, ACCENT, bold = true).text = "DAILY PUZZLE"
        puzzleBoard = image(chessPage, 1420, 290, 400)
        puzzleText = label(chessPage, 1420, 710, 34, WHITE, width = 400)

        pages = listOf(clockPage, statusPage, chessPage)

        place(canvas, alertBar, 0, 0, W, 60)
        alertText = label(alertBar, 120, 6, 34, WHITE, bold = true, width = 1680).apply { maxLines = 1 }
        alertBar.visibility = GONE
        cornerTime = label(canvas, 1500, 80, 56, MUTED, bold = true, width = 300).apply {
            gravity = Gravity.END
        }
        for (i in 0..2) {
            dots += View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(DIM) }
            }
            place(canvas, dots[i], 915 + i * 34, 1030, 18, 18)
        }
        showPage(0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val scale = minOf(w / W.toFloat(), h / H.toFloat())
        canvas.scaleX = scale
        canvas.scaleY = scale
        canvas.translationX = (w - W * scale) / 2
        canvas.translationY = (h - H * scale) / 2
    }

    // ---- lifecycle --------------------------------------------------------

    fun start() {
        if (running) return
        running = true
        tickClock()
        refresh()
        main.postDelayed(rotate, ROTATE_MS)
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
    }

    fun shutdown() {
        stop()
        worker.shutdownNow()
    }

    // ---- navigation -------------------------------------------------------

    fun step(delta: Int) {
        showPage((index + delta + 3) % 3)
        pausedUntil = System.currentTimeMillis() + PAUSE_MS
    }

    private val rotate = object : Runnable {
        override fun run() {
            if (screensaver || System.currentTimeMillis() >= pausedUntil) showPage((index + 1) % 3)
            main.postDelayed(this, ROTATE_MS)
        }
    }

    private fun showPage(i: Int) {
        index = i
        pages.forEachIndexed { p, page -> page.visibility = if (p == i) VISIBLE else GONE }
        dots.forEachIndexed { p, d -> (d.background as GradientDrawable).setColor(if (p == i) ACCENT else DIM) }
        cornerTime.visibility = if (i == 0) GONE else VISIBLE
    }

    // ---- clock ------------------------------------------------------------

    private fun tickClock() {
        val now = Date()
        val clock = SimpleDateFormat("h:mm", Locale.US).format(now)
        bigTime.text = clock
        cornerTime.text = clock
        bigDate.text = SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now)
        if (running) main.postDelayed({ tickClock() }, 1000L - System.currentTimeMillis() % 1000)
    }

    // ---- feed -------------------------------------------------------------

    private fun refresh() {
        worker.execute {
            val feed = runCatching { JSONObject(String(fetch("$SERVER/api/feed"))) }
                .onFailure { Log.w(TAG, "feed: $it") }
                .getOrNull()
            main.post {
                if (feed != null) show(feed)  // otherwise keep the last good one
                if (running) main.postDelayed({ refresh() }, FEED_MS)
            }
        }
    }

    private fun show(f: JSONObject) {
        f.optJSONObject("weather")?.let { showWeather(it, f.optJSONObject("air")) }
        showIss(f.optJSONObject("iss"))
        showAlerts(f.optJSONArray("alerts"))
        showStatus(f)
        f.optJSONObject("chess")?.let { showChess(it) }
        showPuzzle(f.optJSONObject("puzzle"))
    }

    private fun showWeather(w: JSONObject, air: JSONObject?) {
        temp.text = "${w.getInt("temp")}°"
        summary.text = w.getString("summary")
        val lines = mutableListOf(
            "High ${w.getInt("high")}°  ·  Low ${w.getInt("low")}°",
            "Feels like ${w.getInt("feels")}°  ·  Humidity ${w.getInt("humidity")}%",
            "Wind ${w.getInt("wind")} mph  ·  Rain ${w.getInt("rain")}%",
            "Sunrise ${time12(w.getString("sunrise"))}  ·  Sunset ${time12(w.getString("sunset"))}",
        )
        if (air != null) {
            lines += "Air ${air.getInt("aqi")} (${air.getString("aqiLabel")})  ·  " +
                "UV ${air.getInt("uv")} ${air.getString("uvLabel")}, max ${w.optInt("uvMax")}"
        }
        details.text = lines.joinToString("\n")

        hours.removeAllViews()
        val hrs = w.getJSONArray("hours")
        for (i in 0 until hrs.length()) {
            val h = hrs.getJSONObject(i)
            val x = i * 280
            label(hours, x, 0, 34, MUTED, width = 260).text = hour12(h.getString("time"))
            label(hours, x, 50, 64, WHITE, bold = true, width = 260).text = "${h.getInt("temp")}°"
            label(hours, x, 140, 30, MUTED, width = 260).text = h.getString("summary")
        }
    }

    private fun showIss(iss: JSONObject?) {
        issText.text = iss?.let {
            "Space station overhead ${it.getString("when")}  ·  ${it.getInt("maxElevation")}° up  ·  ${it.getInt("minutes")} min"
        } ?: ""
    }

    /** Most severe first; red for Extreme/Severe, amber for Moderate, grey otherwise. */
    private fun showAlerts(alerts: JSONArray?) {
        if (alerts == null || alerts.length() == 0) {
            alertBar.visibility = GONE
            return
        }
        val a = alerts.getJSONObject(0)
        var text = "WEATHER ALERT  ·  ${a.getString("event")}"
        if (!a.isNull("until")) text += " until ${a.getString("until")}"
        if (alerts.length() > 1) text += "  ·  +${alerts.length() - 1} more"
        alertText.text = text
        alertBar.setBackgroundColor(when (a.optString("severity")) {
            "Extreme", "Severe" -> RED
            "Moderate" -> AMBER
            else -> DIM
        })
        alertBar.visibility = VISIBLE
    }

    private fun showStatus(f: JSONObject) {
        f.optJSONArray("services")?.let { services ->
            serviceGrid.removeAllViews()
            val down = mutableListOf<String>()
            for (i in 0 until services.length()) {
                val s = services.getJSONObject(i)
                val ok = s.getBoolean("ok")
                val x = (i / 8) * 600
                val y = (i % 8) * 82
                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (ok) GREEN else RED)
                    }
                }
                place(serviceGrid, dot, x, y + 14, 26, 26)
                label(serviceGrid, x + 50, y, 44, WHITE, width = 520).text = s.getString("name")
                if (!ok) down += s.getString("name")
            }
            statusSummary.text = if (down.isEmpty()) "Everything's running"
            else "${down.size} down: ${down.joinToString(", ")}"
        }

        f.optJSONObject("backup")?.let { b ->
            backupText.text = (if (b.getBoolean("ok")) "OK" else "Overdue") + "\nLast: " + b.getString("last")
        }

        ripperBar.visibility = GONE
        ripperBarBg.visibility = GONE
        f.optJSONObject("ripper")?.let { r ->
            ripperText.text = if (r.getString("state") == "idle") {
                "Idle" + when {
                    r.isNull("last") -> ""
                    r.optBoolean("lastOk") -> "\nLast: " + r.getString("last")
                    else -> "\nLast failed: " + r.getString("last")
                }
            } else {
                val verb = if (r.getString("state") == "copying") "Copying" else "Compressing"
                var text = verb + "\n" + r.getString("title")
                if (!r.isNull("progress")) {
                    val pct = r.getDouble("progress")
                    text += "\n${pct.toInt()}%"
                    ripperBarBg.visibility = VISIBLE
                    ripperBar.visibility = VISIBLE
                    ripperBar.layoutParams = (ripperBar.layoutParams as LayoutParams)
                        .apply { width = (460 * pct / 100).roundToInt() }
                }
                text
            }
        }
    }

    private fun showChess(c: JSONObject) {
        var line = "${c.getString("me")}  ·  Correspondence ${c.optInt("rating")}"
        line += if (c.optBoolean("puzzleSolved")) "  ·  Daily puzzle done" else "  ·  Daily puzzle waiting"
        val challenges = c.optInt("challenges")
        if (challenges > 0) line += "  ·  $challenges challenge(s)"
        chessSummary.text = line

        val games = c.getJSONArray("games")
        noGames.visibility = if (games.length() == 0) VISIBLE else GONE
        for (i in 0..1) {
            if (i < games.length()) {
                val g = games.getJSONObject(i)
                loadImage(boards[i], SERVER + g.getString("board"))
                boards[i].visibility = VISIBLE
                gameLabels[i].text = (if (g.getBoolean("myTurn")) "Your move" else "Their move") +
                    " vs " + g.getString("opponent")
                gameLabels[i].visibility = VISIBLE
            } else {
                boards[i].visibility = GONE
                gameLabels[i].visibility = GONE
            }
        }
        moreGames.text = if (games.length() > 2) "+ ${games.length() - 2} more game(s)" else ""
    }

    private fun showPuzzle(p: JSONObject?) {
        if (p == null) {
            puzzleBoard.visibility = GONE
            puzzleText.text = ""
            return
        }
        loadImage(puzzleBoard, SERVER + p.getString("board"))
        puzzleBoard.visibility = VISIBLE
        val side = if (p.getString("color") == "white") "White" else "Black"
        puzzleText.text = "$side to move\nRated ${p.getInt("rating")}"
    }

    // ---- helpers ----------------------------------------------------------

    /** Board images change only when a move is made (the URL carries it), so cache by URL. */
    private fun loadImage(view: ImageView, url: String) {
        images[url]?.let { view.setImageBitmap(it); return }
        worker.execute {
            val bmp = runCatching { fetch(url).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                .onFailure { Log.w(TAG, "image $url: $it") }
                .getOrNull() ?: return@execute
            main.post {
                if (images.size > 8) images.clear()
                images[url] = bmp
                view.setImageBitmap(bmp)
            }
        }
    }

    private fun fetch(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun page() = FrameLayout(context).also { place(canvas, it, 0, 0, W, H) }

    private fun place(parent: FrameLayout, child: View, x: Int, y: Int, w: Int, h: Int) {
        parent.addView(child, LayoutParams(w, h).apply { leftMargin = x; topMargin = y })
    }

    private fun label(parent: FrameLayout, x: Int, y: Int, size: Int, color: Int,
                      bold: Boolean = false, width: Int = LayoutParams.WRAP_CONTENT) =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat())
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            includeFontPadding = false
            parent.addView(this, LayoutParams(width, LayoutParams.WRAP_CONTENT)
                .apply { leftMargin = x; topMargin = y })
        }

    private fun rect(parent: FrameLayout, x: Int, y: Int, w: Int, h: Int, color: Int) =
        View(context).apply { setBackgroundColor(color) }.also { place(parent, it, x, y, w, h) }

    private fun image(parent: FrameLayout, x: Int, y: Int, size: Int) =
        ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            .also { place(parent, it, x, y, size, size) }

    private fun hour12(hhmm: String): String {
        val h = hhmm.take(2).toInt()
        return "${if (h % 12 == 0) 12 else h % 12} ${if (h >= 12) "PM" else "AM"}"
    }

    private fun time12(hhmm: String): String {
        val h = hhmm.take(2).toInt()
        return "${if (h % 12 == 0) 12 else h % 12}:${hhmm.substring(3, 5)} ${if (h >= 12) "PM" else "AM"}"
    }

    private companion object {
        const val TAG = "Lookout"
        const val SERVER = "http://192.168.1.158:8473"
        const val W = 1920
        const val H = 1080
        const val ROTATE_MS = 30_000L
        const val PAUSE_MS = 300_000L
        const val FEED_MS = 60_000L
        val BG = Color.parseColor("#0F1115")
        val WHITE = Color.WHITE
        val MUTED = Color.parseColor("#9A9CAB")
        val ACCENT = Color.parseColor("#AA5CC3")
        val LINE = Color.parseColor("#262A33")
        val DIM = Color.parseColor("#3A3E4A")
        val GREEN = Color.parseColor("#22C55E")
        val RED = Color.parseColor("#B91C1C")
        val AMBER = Color.parseColor("#B45309")
    }
}
