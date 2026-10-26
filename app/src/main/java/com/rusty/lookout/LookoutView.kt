package com.rusty.lookout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The whole Lookout display: weather, homelab and chess pages, fed by
 * latitude-lookout's /api/feed. Shared by the app (MainActivity) and the
 * screensaver (LookoutDream).
 *
 * Everything is laid out on a fixed 1920x1080 canvas that's scaled to the
 * screen. Pages are built from rounded "cards" on a dark gradient.
 */
class LookoutView(context: Context, private val screensaver: Boolean) : FrameLayout(context) {

    private val canvas = FrameLayout(context)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private val images = HashMap<String, Bitmap>()

    private val pages = mutableListOf<FrameLayout>()
    private val tabs = mutableListOf<TextView>()
    private var index = 0
    private var pausedUntil = 0L
    private var running = false
    private var sun: Pair<Int, Int>? = null  // today's sunrise/sunset, minutes after midnight

    // header + alert
    private val headerTime: TextView
    private val alertCard: FrameLayout
    private val alertText: TextView

    // weather page
    private val bigTime: TextView
    private val bigDate: TextView
    private val issCard: FrameLayout
    private val issText: TextView
    private val weatherIcon: TextView
    private val temp: TextView
    private val summary: TextView
    private val stats = mutableListOf<Pair<TextView, TextView>>()
    private val hourCards = mutableListOf<List<TextView>>()

    // homelab page
    private val statusSummary: TextView
    private val serviceGrid = FrameLayout(context)
    private val backupBadge: TextView
    private val backupText: TextView
    private val ripperState: TextView
    private val ripperTitle: TextView
    private val ripperBar: View
    private val ripperTrack: View
    private val ripperPct: TextView

    // chess page
    private val chips = FrameLayout(context)
    private val gameCards = mutableListOf<FrameLayout>()
    private val boards = mutableListOf<ImageView>()
    private val turnBadges = mutableListOf<TextView>()
    private val opponents = mutableListOf<TextView>()
    private val noGames: FrameLayout
    private val moreGames: TextView
    private val puzzleBoard: ImageView
    private val puzzleText: TextView

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#171C2E"), Color.parseColor("#0C0E14")))
        addView(canvas, LayoutParams(W, H))
        canvas.pivotX = 0f
        canvas.pivotY = 0f
        // A faint purple glow in the top right, behind everything.
        place(canvas, View(context).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = 700f  // half the view, so it fades out before the edges
                colors = intArrayOf(Color.parseColor("#33AA5CC3"), Color.TRANSPARENT)
            }
        }, 1000, -500, 1400, 1400)

        // ---- header: tabs on the left, time on the right ----
        listOf("Weather", "Homelab", "Chess").forEachIndexed { i, name ->
            tabs += text(canvas, 96 + i * 230, 52, 30, MUTED, MEDIUM, width = 210, height = 60).apply {
                this.text = name
                gravity = Gravity.CENTER
            }
        }
        headerTime = text(canvas, 1500, 50, 46, WHITE, LIGHT, width = 324).apply { gravity = Gravity.END }

        alertCard = card(canvas, 96, 132, 1728, 64, radius = 32f, fill = "#33B91C1C", stroke = "#99EF4444")
        alertText = text(alertCard, 32, 0, 30, WHITE, MEDIUM, width = 1664, height = 64).apply {
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }
        alertCard.visibility = GONE

        // ---- weather ----
        val weather = page()
        bigTime = text(weather, 88, 200, 250, WHITE, THIN)
        bigDate = text(weather, 100, 480, 46, SOFT, LIGHT)
        issCard = card(weather, 96, 580, 800, 84, radius = 42f)
        issText = text(issCard, 36, 0, 30, SOFT, LIGHT, width = 740, height = 84).apply {
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }

        val wcard = card(weather, 980, 220, 844, 470)
        weatherIcon = text(wcard, 40, 30, 120, WHITE, LIGHT)
        temp = text(wcard, 230, 6, 150, WHITE, THIN)
        summary = text(wcard, 236, 172, 40, ACCENT_LIGHT, MEDIUM, width = 580).apply { maxLines = 1 }
        for (i in 0 until 8) {
            val tile = card(wcard, 30 + (i % 4) * 200, 250 + (i / 4) * 106, 184, 92,
                radius = 18f, fill = "#0DFFFFFF", stroke = null)
            val label = text(tile, 18, 14, 19, MUTED, MEDIUM).apply { letterSpacing = 0.12f }
            val value = text(tile, 18, 42, 32, WHITE, REGULAR, width = 160).apply { maxLines = 1 }
            stats += label to value
        }

        for (i in 0 until 6) {
            val c = card(weather, 96 + i * 292, 740, 268, 280)
            hourCards += listOf(
                text(c, 0, 26, 26, MUTED, MEDIUM, width = 268).apply { gravity = Gravity.CENTER },
                text(c, 0, 72, 64, WHITE, REGULAR, width = 268).apply { gravity = Gravity.CENTER },
                text(c, 0, 160, 50, WHITE, LIGHT, width = 268).apply { gravity = Gravity.CENTER },
                text(c, 12, 228, 23, MUTED, REGULAR, width = 244).apply { gravity = Gravity.CENTER; maxLines = 1 },
            )
        }

        // ---- homelab ----
        val lab = page()
        statusSummary = text(lab, 100, 226, 40, WHITE, LIGHT, width = 1160)
        place(lab, serviceGrid, 96, 300, 1164, 720)

        val bcard = card(lab, 1300, 226, 524, 300)
        eyebrow(bcard, 36, 32, "BACKUP")
        backupBadge = text(bcard, 36, 84, 60, GREEN, LIGHT)
        backupText = text(bcard, 36, 180, 30, SOFT, LIGHT, width = 460)

        val rcard = card(lab, 1300, 556, 524, 464)
        eyebrow(rcard, 36, 32, "DVD RIPPER")
        ripperState = text(rcard, 36, 84, 60, WHITE, LIGHT)
        ripperTitle = text(rcard, 36, 180, 30, SOFT, LIGHT, width = 452).apply { maxLines = 3 }
        ripperTrack = card(rcard, 36, 370, 452, 14, radius = 7f, fill = "#1FFFFFFF", stroke = null)
        ripperBar = card(rcard, 36, 370, 0, 14, radius = 7f, fill = ACCENT_HEX, stroke = null)
        ripperPct = text(rcard, 36, 398, 26, MUTED, MEDIUM, width = 452)

        // ---- chess ----
        val chess = page()
        place(chess, chips, 96, 220, 1728, 56)
        for (x in listOf(96, 760)) {
            val c = card(chess, x, 300, 620, 720)
            gameCards += c
            boards += image(c, 30, 30, 560)
            turnBadges += text(c, 30, 616, 26, WHITE, MEDIUM, height = 52).apply {
                gravity = Gravity.CENTER
                setPadding(26, 0, 26, 0)
            }
            opponents += text(c, 30, 678, 30, SOFT, LIGHT, width = 560).apply { maxLines = 1 }
        }
        noGames = card(chess, 96, 300, 1284, 720)
        text(noGames, 0, 0, 44, MUTED, LIGHT, width = 1284, height = 720).apply {
            gravity = Gravity.CENTER
            text = "No games in progress"
        }
        val pcard = card(chess, 1424, 300, 400, 720)
        eyebrow(pcard, 30, 30, "DAILY PUZZLE")
        puzzleBoard = image(pcard, 30, 84, 340)
        puzzleText = text(pcard, 30, 452, 32, WHITE, LIGHT, width = 340).apply { setLineSpacing(8f, 1f) }
        moreGames = text(pcard, 30, 640, 26, MUTED, REGULAR, width = 340)

        showPage(0, animate = false)
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

    private fun showPage(i: Int, animate: Boolean = true) {
        val old = pages[index]
        val new = pages[i]
        index = i
        if (animate && old !== new) {
            old.animate().alpha(0f).translationX(-40f).setDuration(FADE_MS)
                .withEndAction { old.visibility = GONE; old.translationX = 0f }
            new.alpha = 0f
            new.translationX = 40f
            new.visibility = VISIBLE
            new.animate().alpha(1f).translationX(0f).setDuration(FADE_MS).startDelay = FADE_MS / 2
        } else {
            pages.forEach { it.visibility = if (it === new) VISIBLE else GONE; it.alpha = 1f }
        }
        tabs.forEachIndexed { t, tab ->
            val on = t == i
            tab.setTextColor(if (on) WHITE else MUTED)
            tab.background = if (on) GradientDrawable().apply {
                cornerRadius = 30f
                setColor(Color.parseColor("#40AA5CC3"))
                setStroke(2, Color.parseColor("#80AA5CC3"))
            } else null
        }
        headerTime.animate().alpha(if (i == 0) 0f else 1f).setDuration(FADE_MS)
    }

    // ---- clock ------------------------------------------------------------

    private fun tickClock() {
        val now = Date()
        val clock = SimpleDateFormat("h:mm", Locale.US).format(now)
        bigTime.text = clock
        headerTime.text = clock
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
        sun = minutes(w.getString("sunrise")) to minutes(w.getString("sunset"))
        val nowMin = Calendar.getInstance().run { get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE) }
        weatherIcon.text = icon(w.getString("summary"), nowMin)
        temp.text = "${w.getInt("temp")}°"
        summary.text = w.getString("summary")

        val tiles = mutableListOf(
            "HIGH / LOW" to "${w.getInt("high")}° / ${w.getInt("low")}°",
            "FEELS LIKE" to "${w.getInt("feels")}°",
            "HUMIDITY" to "${w.getInt("humidity")}%",
            "WIND" to "${w.getInt("wind")} mph",
            "RAIN" to "${w.getInt("rain")}%",
            "UV" to (air?.let { "${it.getInt("uv")}" } ?: "max ${w.optInt("uvMax")}"),
            "AIR" to (air?.let { "${it.getInt("aqi")}" } ?: "—"),
            "SUNSET" to time12(w.getString("sunset")),
        )
        tiles.forEachIndexed { i, (label, value) ->
            stats[i].first.text = label
            stats[i].second.text = value
        }
        if (air != null) {
            stats[5].second.text = withNote(air.getInt("uv").toString(), air.getString("uvLabel"))
            stats[6].second.text = withNote(air.getInt("aqi").toString(), short(air.getString("aqiLabel")))
        }
        stats[6].second.setTextColor(air?.let { aqiColor(it.getInt("aqi")) } ?: WHITE)

        val hrs = w.getJSONArray("hours")
        hourCards.forEachIndexed { i, views ->
            if (i >= hrs.length()) return@forEachIndexed
            val h = hrs.getJSONObject(i)
            views[0].text = hour12(h.getString("time"))
            views[1].text = icon(h.getString("summary"), minutes(h.getString("time")))
            views[2].text = "${h.getInt("temp")}°"
            views[3].text = h.getString("summary")
        }
    }

    private fun showIss(iss: JSONObject?) {
        issCard.visibility = if (iss == null) GONE else VISIBLE
        iss ?: return
        issText.text = "🛰   Space station  ·  ${iss.getString("when")}  ·  " +
            "${iss.getInt("maxElevation")}° up  ·  ${iss.getInt("minutes")} min"
    }

    /** Most severe first; red for Extreme/Severe, amber for Moderate, grey otherwise. */
    private fun showAlerts(alerts: JSONArray?) {
        if (alerts == null || alerts.length() == 0) {
            alertCard.visibility = GONE
            return
        }
        val a = alerts.getJSONObject(0)
        var text = "⚠   ${a.getString("event")}"
        if (!a.isNull("until")) text += "  ·  until ${a.getString("until")}"
        if (alerts.length() > 1) text += "  ·  +${alerts.length() - 1} more"
        alertText.text = text
        val (fill, stroke) = when (a.optString("severity")) {
            "Extreme", "Severe" -> "#40B91C1C" to "#B3EF4444"
            "Moderate" -> "#40B45309" to "#B3F59E0B"
            else -> "#1FFFFFFF" to "#40FFFFFF"
        }
        (alertCard.background as GradientDrawable).apply {
            setColor(Color.parseColor(fill))
            setStroke(2, Color.parseColor(stroke))
        }
        alertCard.visibility = VISIBLE
    }

    private fun showStatus(f: JSONObject) {
        f.optJSONArray("services")?.let { services ->
            serviceGrid.removeAllViews()
            val down = mutableListOf<String>()
            for (i in 0 until services.length()) {
                val s = services.getJSONObject(i)
                val ok = s.getBoolean("ok")
                val tile = card(serviceGrid, (i % 3) * 394, (i / 3) * 112, 370, 92, radius = 22f,
                    fill = if (ok) "#0DFFFFFF" else "#33B91C1C",
                    stroke = if (ok) "#14FFFFFF" else "#99EF4444")
                place(tile, View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (ok) GREEN else RED)
                        setStroke(6, if (ok) Color.parseColor("#3322C55E") else Color.parseColor("#33EF4444"))
                    }
                }, 26, 31, 30, 30)
                text(tile, 76, 0, 32, WHITE, REGULAR, width = 280, height = 92).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 1
                    text = s.getString("name")
                }
                if (!ok) down += s.getString("name")
            }
            statusSummary.text = if (down.isEmpty()) "All ${services.length()} services running"
            else "${down.size} down: ${down.joinToString(", ")}"
            statusSummary.setTextColor(if (down.isEmpty()) WHITE else Color.parseColor("#FCA5A5"))
        }

        f.optJSONObject("backup")?.let { b ->
            val ok = b.getBoolean("ok")
            backupBadge.text = if (ok) "✓ OK" else "Overdue"
            backupBadge.setTextColor(if (ok) GREEN else Color.parseColor("#FCA5A5"))
            backupText.text = "Last run ${b.getString("last")}"
        }

        f.optJSONObject("ripper")?.let { r ->
            val idle = r.getString("state") == "idle"
            ripperTrack.visibility = if (idle) GONE else VISIBLE
            ripperBar.visibility = if (idle) GONE else VISIBLE
            ripperPct.text = ""
            if (idle) {
                ripperState.text = "Idle"
                ripperTitle.text = when {
                    r.isNull("last") -> "Waiting for a disc"
                    r.optBoolean("lastOk") -> "Last: " + r.getString("last")
                    else -> "Last failed: " + r.getString("last")
                }
            } else {
                ripperState.text = if (r.getString("state") == "copying") "Copying" else "Compressing"
                ripperTitle.text = r.getString("title")
                val pct = if (r.isNull("progress")) 0.0 else r.getDouble("progress")
                ripperBar.layoutParams = (ripperBar.layoutParams as LayoutParams)
                    .apply { width = (452 * pct / 100).roundToInt() }
                ripperBar.requestLayout()
                if (!r.isNull("progress")) ripperPct.text = "${pct.toInt()}% done"
            }
        }
    }

    private fun showChess(c: JSONObject) {
        chips.removeAllViews()
        var x = 0
        val labels = mutableListOf(c.getString("me"), "Correspondence ${c.optInt("rating")}")
        if (c.has("puzzleRating")) labels += "Puzzles ${c.optInt("puzzleRating")}"
        labels += if (c.optBoolean("puzzleSolved")) "✓ Daily puzzle done" else "Daily puzzle waiting"
        val challenges = c.optInt("challenges")
        if (challenges > 0) labels += "$challenges challenge(s)"
        for (label in labels) {
            val chip = text(chips, x, 0, 26, SOFT, MEDIUM, height = 52).apply {
                text = label
                gravity = Gravity.CENTER
                setPadding(26, 0, 26, 0)
                background = GradientDrawable().apply {
                    cornerRadius = 26f
                    setColor(Color.parseColor("#14FFFFFF"))
                    setStroke(2, Color.parseColor("#1FFFFFFF"))
                }
            }
            chip.measure(0, 0)
            x += chip.measuredWidth + 14
        }

        val games = c.getJSONArray("games")
        noGames.visibility = if (games.length() == 0) VISIBLE else GONE
        for (i in 0..1) {
            if (i >= games.length()) {
                gameCards[i].visibility = GONE
                continue
            }
            val g = games.getJSONObject(i)
            gameCards[i].visibility = VISIBLE
            loadImage(boards[i], SERVER + g.getString("board"))
            val mine = g.getBoolean("myTurn")
            turnBadges[i].text = if (mine) "Your move" else "Their move"
            turnBadges[i].setTextColor(if (mine) WHITE else SOFT)
            turnBadges[i].background = GradientDrawable().apply {
                cornerRadius = 26f
                setColor(Color.parseColor(if (mine) ACCENT_HEX else "#14FFFFFF"))
                if (!mine) setStroke(2, Color.parseColor("#33FFFFFF"))
            }
            turnBadges[i].measure(0, 0)
            val rating = if (g.isNull("opponentRating")) "" else " (${g.getInt("opponentRating")})"
            opponents[i].text = "vs ${g.getString("opponent")}$rating"
            (opponents[i].layoutParams as LayoutParams).apply {
                leftMargin = 30 + turnBadges[i].measuredWidth + 18
                topMargin = 626
                width = 560 - turnBadges[i].measuredWidth - 18
            }
            opponents[i].requestLayout()
        }
        moreGames.text = if (games.length() > 2) "+ ${games.length() - 2} more game(s) on Lichess" else ""
    }

    private fun showPuzzle(p: JSONObject?) {
        if (p == null) {
            puzzleBoard.visibility = GONE
            puzzleText.text = "No puzzle today"
            return
        }
        loadImage(puzzleBoard, SERVER + p.getString("board"))
        puzzleBoard.visibility = VISIBLE
        val side = if (p.getString("color") == "white") "White" else "Black"
        puzzleText.text = "$side to move\nRated ${p.getInt("rating")}"
    }

    // ---- weather helpers ----------------------------------------------------

    private fun icon(summary: String, minuteOfDay: Int): String {
        val night = sun?.let { (rise, set) -> minuteOfDay < rise || minuteOfDay >= set } ?: false
        val s = summary.lowercase()
        return when {
            "thunder" in s -> "⛈️"
            "snow" in s -> "🌨️"
            "freezing" in s -> "🌧️"
            "heavy" in s || s == "rain" || "showers" in s -> "🌧️"
            "drizzle" in s || "light rain" in s -> if (night) "🌧️" else "🌦️"
            "fog" in s -> "🌫️"
            "overcast" in s -> "☁️"
            "partly" in s -> if (night) "☁️" else "⛅"
            "mostly clear" in s -> if (night) "🌙" else "🌤️"
            "clear" in s -> if (night) "🌙" else "☀️"
            else -> "🌡️"
        }
    }

    private fun aqiColor(aqi: Int) = Color.parseColor(when {
        aqi <= 50 -> "#86EFAC"
        aqi <= 100 -> "#FDE047"
        aqi <= 150 -> "#FDBA74"
        else -> "#FCA5A5"
    })

    /** "53" at full size with a smaller, muted "Moderate" after it. */
    private fun withNote(value: String, note: String) = SpannableString("$value  $note").apply {
        val start = value.length + 2
        setSpan(RelativeSizeSpan(0.6f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(MUTED), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun short(label: String) = if (label.startsWith("Unhealthy for")) "Sensitive" else label

    private fun minutes(hhmm: String) = hhmm.take(2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    private fun hour12(hhmm: String): String {
        val h = hhmm.take(2).toInt()
        return "${if (h % 12 == 0) 12 else h % 12} ${if (h >= 12) "PM" else "AM"}"
    }

    private fun time12(hhmm: String): String {
        val h = hhmm.take(2).toInt()
        return "${if (h % 12 == 0) 12 else h % 12}:${hhmm.substring(3, 5)} ${if (h >= 12) "PM" else "AM"}"
    }

    // ---- view helpers -------------------------------------------------------

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

    private fun page() = FrameLayout(context).also { place(canvas, it, 0, 0, W, H) }.also { pages += it }

    private fun place(parent: FrameLayout, child: View, x: Int, y: Int, w: Int, h: Int) {
        parent.addView(child, LayoutParams(w, h).apply { leftMargin = x; topMargin = y })
    }

    private fun card(parent: FrameLayout, x: Int, y: Int, w: Int, h: Int, radius: Float = 32f,
                     fill: String = "#0FFFFFFF", stroke: String? = "#1AFFFFFF") =
        FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(Color.parseColor(fill))
                if (stroke != null) setStroke(2, Color.parseColor(stroke))
            }
        }.also { place(parent, it, x, y, w, h) }

    private fun eyebrow(parent: FrameLayout, x: Int, y: Int, label: String) =
        text(parent, x, y, 22, ACCENT_LIGHT, MEDIUM).apply {
            text = label
            letterSpacing = 0.16f
        }

    private fun text(parent: FrameLayout, x: Int, y: Int, size: Int, color: Int, face: Typeface,
                     width: Int = LayoutParams.WRAP_CONTENT, height: Int = LayoutParams.WRAP_CONTENT) =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat())
            setTextColor(color)
            typeface = face
            includeFontPadding = false
            parent.addView(this, LayoutParams(width, height).apply { leftMargin = x; topMargin = y })
        }

    private fun image(parent: FrameLayout, x: Int, y: Int, size: Int) =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, 18f)
            }
        }.also { place(parent, it, x, y, size, size) }

    private companion object {
        const val TAG = "Lookout"
        const val SERVER = "http://192.168.1.158:8473"
        const val W = 1920
        const val H = 1080
        const val ROTATE_MS = 30_000L
        const val PAUSE_MS = 300_000L
        const val FEED_MS = 60_000L
        const val FADE_MS = 450L
        const val ACCENT_HEX = "#AA5CC3"
        val WHITE = Color.parseColor("#F4F4F7")
        val SOFT = Color.parseColor("#C9CAD6")
        val MUTED = Color.parseColor("#8A8C9C")
        val ACCENT_LIGHT = Color.parseColor("#C98BDB")
        val GREEN = Color.parseColor("#4ADE80")
        val RED = Color.parseColor("#EF4444")
        val THIN: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}
