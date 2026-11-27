package io.clipper.app

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import org.json.JSONArray
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.media.FaceDetector
import android.media.MediaMetadataRetriever
import android.view.View
import android.view.Gravity
import android.content.ContentValues
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var go: Button
    private lateinit var formView: ScrollView
    private lateinit var clipsView: ScrollView
    private lateinit var clipsBox: LinearLayout
    private lateinit var clipsSummary: TextView
    private var inClips = false
    private var lastSig = ""
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { if (inClips) { renderClips(); h.postDelayed(this, 1000) } } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val BG = 0xFF0A1128.toInt(); val CARD = 0xFF121C3D.toInt(); val FIELD = 0xFF1B2A55.toInt()
        val ACC = 0xFF4C8DFF.toInt(); val MUT = 0xFF8FA3C7.toInt(); val WHITE = Color.WHITE
        window.statusBarColor = BG; window.navigationBarColor = BG
        window.setBackgroundDrawable(ColorDrawable(BG))
        val dens = resources.displayMetrics.density
        fun dp(v: Int) = (v * dens).toInt()
        fun bg(c: Int, r: Int = 16) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
        fun tv(t: String, size: Float, c: Int = WHITE, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(c); if (bold) setTypeface(typeface, Typeface.BOLD) }
        fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
        fun field(h: String) = EditText(this).apply {
            hint = h; setHintTextColor(MUT); setTextColor(WHITE); setSingleLine(); textSize = 16f
            background = bg(FIELD, 14); setPadding(dp(16), dp(14), dp(16), dp(14)) }
        fun btn(t: String, fill: Int, txt: Int, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; textSize = 16f; setTextColor(txt); background = bg(fill, 16)
            stateListAnimator = null; setOnClickListener { onClick() } }
        fun card(title: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg(CARD, 22); setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(tv(title, 13f, ACC, true).apply { letterSpacing = 0.08f }) }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(36), dp(16), dp(24)) }

        // header: title + Follow
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv("Clipper", 32f, WHITE, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        head.addView(Button(this).apply {
            text = "Follow"; isAllCaps = false; textSize = 16f; setTextColor(WHITE); setTypeface(typeface, Typeface.BOLD)
            stateListAnimator = null
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFFFF7A18.toInt(), 0xFFE1306C.toInt(), 0xFF833AB4.toInt())).apply { cornerRadius = dp(26).toFloat() }
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/NaahNotMeDude"))) }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(48)))
        col.addView(head)

        // sections
        val link = field("Paste a YouTube or Twitch link")
        val wmField = field("@yourname  (leave empty = no watermark)")
        val prefs = getSharedPreferences("clipper", 0)
        val keyField = field("Groq API key (starts with gsk_)").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(prefs.getString("groq", "")) }
        val brainSw = Switch(this).apply { isChecked = prefs.getBoolean("brain", true) }
        wmField.setText(getSharedPreferences("clipper", 0).getString("wm", ""))
        var length = 30
        var count = 3
        var hq = 720
        var camMode = ""
        var lang = "en"
        var blur = false
        val mBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val cap = Switch(this).apply { isChecked = true }

        fun times(): List<Int> = (0 until mBox.childCount).mapNotNull {
            ((mBox.getChildAt(it) as LinearLayout).getChildAt(0) as EditText).text.toString().trim().ifEmpty { null } }.map { secs(it) }

        go = btn("Create", ACC, WHITE) {
            try { val t = times(); prefs.edit().putString("groq", keyField.text.toString().trim()).putBoolean("brain", brainSw.isChecked).apply(); enqueue(cleanLink(link.text.toString().trim()), t, length, t.isEmpty(), cap.isChecked, camMode, lang, blur, count, wmField.text.toString().trim(), hq, brainSw.isChecked) }
            catch (e: Exception) { say("Moments must look like 14742 or 1:47:42.") }
        }
        val top = LinearLayout(this)
        top.addView(go, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(8) })
        top.addView(btn("My clips", CARD, WHITE) {
            showClips()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        col.addView(top, lp(18))
        status = tv("", 14f, MUT).apply { setPadding(0, dp(12), 0, dp(4)) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(status); col.addView(list)

        val cAI = card("AI VIRAL BRAIN (ONLINE, FREE KEY)")
        val ar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        ar.addView(tv("Use the AI brain", 16f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); ar.addView(brainSw)
        cAI.addView(ar, lp(12))
        cAI.addView(tv("Exact word-by-word captions (Whisper) and smarter viral moment picking. Needs a free Groq key; the key stays on this phone.", 13f, MUT), lp(8))
        cAI.addView(keyField, lp(10))
        val brainStatus = tv(if (keyField.text.isNotBlank()) "Key saved on this phone." else "No key yet.", 13f, MUT)
        cAI.addView(btn("Test and save key", FIELD, WHITE) {
            val k = keyField.text.toString().trim(); prefs.edit().putString("groq", k).apply(); brainStatus.text = "Checking..."
            Thread {
                val ok = try {
                    val cn = java.net.URL("https://api.groq.com/openai/v1/models").openConnection() as java.net.HttpURLConnection
                    cn.setRequestProperty("Authorization", "Bearer $k"); cn.connectTimeout = 15000; cn.responseCode == 200
                } catch (e: Exception) { false }
                runOnUiThread { brainStatus.text = if (ok) "Connected. AI brain is ready." else "Key not accepted, or no internet." }
            }.start()
        }, lp(10))
        cAI.addView(brainStatus, lp(8))
        cAI.addView(btn("Get a free key: console.groq.com/keys", 0, ACC) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }, lp(4))
        col.addView(cAI, lp(10))

        val cYT = card("YOUTUBE BOT-CHECK FIX (OPTIONAL)")
        cYT.addView(tv("If YouTube keeps saying 'confirm you are not a bot', sign in once. Use a spare Google account, not your main one. The login stays only on this phone.", 13f, MUT), lp(8))
        val ytStatus = tv(if (File(getExternalFilesDir(null), "cookies.txt").exists()) "Signed in." else "Not signed in.", 13f, MUT)
        cYT.addView(btn("Sign in to YouTube", FIELD, WHITE) { loginYouTube { ytStatus.text = "Signed in." } }, lp(10))
        cYT.addView(btn("Remove login", 0, ACC) { File(getExternalFilesDir(null), "cookies.txt").delete(); ytStatus.text = "Not signed in." }, lp(4))
        cYT.addView(ytStatus, lp(4))
        col.addView(cYT, lp(10))

        val c1 = card("1 · STREAM LINK")
        val lr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        lr.addView(link, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        lr.addView(btn("Paste", FIELD, WHITE) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.let { link.setText(it) }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(48)).apply { leftMargin = dp(8) })
        c1.addView(lr, lp(12)); c1.addView(tv("Ended streams and normal videos work best.", 13f, MUT), lp(8))
        col.addView(c1, lp(10))

        val c2 = card("2 · MOMENTS")
        fun addM() {
            if (mBox.childCount >= 10) return
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(field("Moment time, like 14742 or 1:47:42"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            r.addView(btn("✕", 0, MUT) { mBox.removeView(r) }, LinearLayout.LayoutParams(dp(52), WRAP_CONTENT))
            mBox.addView(r, lp(8))
        }
        c2.addView(mBox, lp(4))
        c2.addView(btn("+ Add moment", 0, ACC) { addM() }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, lp(4))
        c2.addView(tv("Leave moments empty and Create will find the best ones for you.", 13f, MUT), lp(4))
        c2.addView(tv("Clip length", 16f, WHITE, true), lp(16))
        val lb = ArrayList<Button>(); val lens = listOf(15, 30, 60)
        fun paint() { lens.forEachIndexed { i, v -> lb[i].background = bg(if (v == length) ACC else FIELD, 14) } }
        val lrow = LinearLayout(this)
        lens.forEach { v ->
            val bt = btn("$v sec", FIELD, WHITE) { length = v; paint() }; lb.add(bt)
            lrow.addView(bt, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        }
        paint(); c2.addView(lrow, lp(8))
        c2.addView(tv("Number of viral clips (when Create finds the moments)", 16f, WHITE, true), lp(16))
        val cntBtns = ArrayList<Button>(); val crow = LinearLayout(this)
        fun paintCnt() { cntBtns.forEachIndexed { i, b -> b.background = bg(if (i + 1 == count) ACC else FIELD, 14) } }
        for (v in 1..5) {
            val bt = btn("$v", FIELD, WHITE) { count = v; paintCnt() }; cntBtns.add(bt)
            crow.addView(bt, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        }
        paintCnt(); c2.addView(crow, lp(8))
        c2.addView(tv("Video quality", 16f, WHITE, true), lp(16))
        val hqBtns = ArrayList<Button>(); val hrow = LinearLayout(this); val hqs = listOf(480, 720, 1080)
        fun paintHq() { hqBtns.forEachIndexed { i, b -> b.background = bg(if (hqs[i] == hq) ACC else FIELD, 14) } }
        hqs.forEach { v ->
            val bt = btn(if (v == 1080) "1080p Full HD" else "${v}p", FIELD, WHITE) { hq = v; paintHq() }; bt.textSize = 14f; hqBtns.add(bt)
            hrow.addView(bt, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        }
        paintHq(); c2.addView(hrow, lp(8)); c2.addView(tv("Higher quality takes longer and uses more storage.", 13f, MUT), lp(6))
        col.addView(c2, lp(14))

        val c3 = card("3 · CAPTIONS AND LAYOUT")
        val cr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        cr.addView(tv("Captions for English speech", 16f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); cr.addView(cap)
        c3.addView(cr, lp(12))
        c3.addView(tv("Word by word. Clips without English speech get no captions.", 13f, MUT), lp(8))
        val camBtns = ArrayList<Button>(); val camKeys = listOf("auto", "tl", "tr", "bl", "br"); val camNames = listOf("Auto", "↖", "↗", "↙", "↘")
        val camRow = LinearLayout(this).apply { visibility = View.GONE }
        fun paintCam() { camKeys.forEachIndexed { i, k -> camBtns[i].background = bg(if (k == camMode) ACC else FIELD, 12) } }
        camKeys.forEachIndexed { i, k ->
            val bt = btn(camNames[i], FIELD, WHITE) { camMode = k; paintCam() }; camBtns.add(bt)
            camRow.addView(bt, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) })
        }
        val camSw = Switch(this).apply { setOnCheckedChangeListener { _, on -> camMode = if (on) "auto" else ""; camRow.visibility = if (on) View.VISIBLE else View.GONE; paintCam() } }
        val cr2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        cr2.addView(tv("Webcam on top, gameplay below", 16f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); cr2.addView(camSw)
        c3.addView(cr2, lp(16)); c3.addView(tv("Finds the webcam by itself. If it misses, pick its corner:", 13f, MUT), lp(8)); c3.addView(camRow, lp(8))
        val langKeys = listOf("en", "in", "hi"); val langNames = listOf("English", "Indian English", "Hindi"); val langBtns = ArrayList<Button>()
        fun paintLang() { langKeys.forEachIndexed { i, k -> langBtns[i].background = bg(if (k == lang) ACC else FIELD, 12) } }
        val lrow2 = LinearLayout(this)
        langKeys.forEachIndexed { i, k ->
            val bt = btn(langNames[i], FIELD, WHITE) { lang = k; paintLang() }; bt.textSize = 13f; langBtns.add(bt)
            lrow2.addView(bt, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) })
        }
        paintLang()
        c3.addView(tv("Caption language (speech is recognised on your phone)", 13f, MUT), lp(16)); c3.addView(lrow2, lp(8))
        val blurSw = Switch(this).apply { setOnCheckedChangeListener { _, on -> blur = on } }
        val br = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        br.addView(tv("Blurred background (when no webcam)", 16f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); br.addView(blurSw)
        c3.addView(br, lp(16))
        c3.addView(tv("Watermark (optional)", 16f, WHITE, true), lp(18))
        c3.addView(tv("Shows at the bottom middle of every clip. Leave empty for none.", 13f, MUT), lp(4))
        c3.addView(wmField, lp(8))
        col.addView(c3, lp(14))

        col.addView(tv("Only clip streams you own or have permission to use.\nBuilt with yt-dlp, FFmpeg and Vosk (open source).", 12f, MUT), lp(18))
        formView = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(col) }
        setContentView(formView)
        addM()
        intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { link.setText(it) }

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun say(t: String) = runOnUiThread { status.text = t }
    private fun enqueue(link: String, times: List<Int>, length: Int, find: Boolean, caps: Boolean, cam: String, lang: String, blur: Boolean, count: Int, wm: String, hq: Int, brain: Boolean) {
        getSharedPreferences("clipper", 0).edit().putString("wm", wm).apply()
        if (!Regex("(youtube\\.com|youtu\\.be|twitch\\.tv)/").containsMatchIn(link)) { say("Paste a YouTube or Twitch link first."); return }
        val now = System.currentTimeMillis()
        fun entry(kind: String, start: Int, n: Int) = JSONObject().put("id", "$now-$n").put("kind", kind).put("link", link)
            .put("start", start).put("length", length).put("caps", caps).put("cam", cam).put("lang", lang).put("blur", blur).put("count", count).put("wm", wm).put("hq", hq).put("brain", brain).put("n", n + 1).put("batch", "$now").put("t0", now).put("title", "").put("state", "queued").put("note", "").put("path", "")
        if (find) Store.add(this, entry("auto", 0, 0)) else times.forEachIndexed { i, t -> Store.add(this, entry("clip", t, i)) }
        startForegroundService(Intent(this, ClipService::class.java))
        showClips()
    }

    private val cBG = 0xFF0A1128.toInt(); private val cCARD = 0xFF121C3D.toInt(); private val cFIELD = 0xFF1B2A55.toInt()
    private val cACC = 0xFF4C8DFF.toInt(); private val cMUT = 0xFF8FA3C7.toInt(); private val cOK = 0xFF3DDC97.toInt()
    private var tab = 0
    private lateinit var tabBtns: List<Button>
    private fun d2p(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rr(c: Int, r: Int = 16) = GradientDrawable().apply { setColor(c); cornerRadius = d2p(r).toFloat() }
    private fun txt(t: String, size: Float, c: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(c); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun lpm(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d2p(top) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rr(cCARD, 22); setPadding(d2p(16), d2p(16), d2p(16), d2p(16)) }
    private fun title(o: JSONObject) = o.optString("title").ifEmpty { o.optString("link") }
    private fun hasActive(): Boolean {
        val a = Store.all(this)
        for (i in 0 until a.length()) { val st = a.getJSONObject(i).getString("state"); if (st == "queued" || st == "working") return true }
        return false
    }
    private fun paintTabs() { tabBtns.forEachIndexed { i, b -> b.background = rr(if (i == tab) cACC else cCARD, 14) } }

    private fun showClips() {
        if (!::clipsView.isInitialized) {
            clipsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            clipsSummary = txt("Clips are made in the background, even if you close the app.", 13f, cMUT)
            tabBtns = listOf("Generation", "Clips").mapIndexed { i, n ->
                Button(this).apply { text = n; isAllCaps = false; setTextColor(Color.WHITE); setOnClickListener { tab = i; lastSig = ""; paintTabs(); renderClips() } } }
            val tabs = LinearLayout(this)
            tabBtns.forEach { tabs.addView(it, LinearLayout.LayoutParams(0, d2p(48), 1f).apply { rightMargin = d2p(6) }) }
            val back = Button(this).apply { text = "Back"; isAllCaps = false; setTextColor(Color.WHITE); background = rr(cCARD, 16); setOnClickListener { hideClips() } }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(d2p(16), d2p(36), d2p(16), d2p(24))
                addView(txt("My clips", 32f, Color.WHITE, true)); addView(clipsSummary, lpm(4)); addView(tabs, lpm(14)); addView(clipsBox); addView(back, lpm(18))
            }
            clipsView = ScrollView(this).apply { setBackgroundColor(cBG); addView(col) }
        }
        tab = if (hasActive()) 0 else 1; paintTabs()
        inClips = true; lastSig = ""
        setContentView(clipsView); renderClips()
        h.removeCallbacks(tick); h.post(tick)
    }

    private fun hideClips() { inClips = false; setContentView(formView) }
    override fun onBackPressed() { if (inClips) hideClips() else super.onBackPressed() }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }
    override fun onResume() { super.onResume(); if (inClips) { lastSig = ""; h.post(tick) } }

    private fun renderClips() { if (tab == 0) renderGen() else renderDone() }

    // live view of what the background service is doing
    private fun renderGen() {
        val a = Store.all(this); val now = System.currentTimeMillis()
        var batch = ""
        for (pass in listOf("working", "queued")) {
            if (batch.isNotEmpty()) break
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("state") == pass) { batch = o.optString("batch"); break } }
        }
        clipsBox.removeAllViews()
        if (batch.isNotEmpty()) {
            var total = 0; var done = 0; var curE: JSONObject? = null; var t0 = now; var qn = ""
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i); if (o.optString("batch") != batch) continue
                t0 = minOf(t0, o.optLong("t0", now))
                total += if (o.getString("kind") == "auto") o.optInt("count", 3) else 1
                if (o.getString("state") == "done") done++
                if (o.getString("state") == "working") curE = o
                if (o.getString("state") == "queued" && qn.isEmpty()) qn = o.optString("note")
            }
            val ce = curE
            val st = ce?.optInt("stage", 0) ?: 0
            val frac = if (ce == null) 0.0 else if (ce.getString("kind") == "auto") 0.05 else listOf(0.15, 0.55, 0.85)[st.coerceIn(0, 2)]
            val pct = (((done + frac) / maxOf(total, 1)) * 100).toInt().coerceIn(0, 99)
            val el = (now - t0) / 1000
            val note = ce?.optString("note") ?: qn.ifEmpty { "Waiting in line..." }
            val c = card(); c.gravity = Gravity.CENTER_HORIZONTAL
            c.addView(TextView(this).apply {
                text = "${el}s"; textSize = 26f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF4C6FFF.toInt()) } }, LinearLayout.LayoutParams(d2p(110), d2p(110)))
            c.addView(txt("Creating Viral Clips", 22f, Color.WHITE, true), lpm(14))
            c.addView(txt("Processing 100% on your phone", 14f, cMUT), lpm(4))
            c.addView(txt("Elapsed: ${el}s  -  ${note.take(36)}", 13f, cACC, true).apply { background = rr(cFIELD, 20); setPadding(d2p(16), d2p(8), d2p(16), d2p(8)) }, lpm(12))
            val pr = LinearLayout(this)
            pr.addView(txt("Processing Progress", 14f, cMUT), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); pr.addView(txt("$pct%", 14f, cACC, true))
            c.addView(pr, lpm(16))
            c.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = pct; progressTintList = ColorStateList.valueOf(cACC) }, lpm(6))
            c.addView(txt("Clip ${minOf(done + 1, total)} of $total: $note", 14f, cACC, true), lpm(10))
            clipsBox.addView(c, lpm(8))
            val sc = card(); sc.addView(txt("Pipeline Stages", 16f, Color.WHITE, true))
            val names = listOf("Import Video" to "Media stream and viral moments", "Transcribe Audio" to "Speech recognised on your phone", "Editing and Put Captions" to "9:16 layout and captions")
            names.forEachIndexed { i, pair ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                val mark = if (ce == null) "o" else if (i < st) "✓" else if (i == st) "..." else "o"
                row.addView(txt(mark, 20f, if (ce != null && i < st) cOK else cACC, true), LinearLayout.LayoutParams(d2p(40), WRAP_CONTENT))
                val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                mid.addView(txt(pair.first, 16f, if (ce != null && i == st) cACC else Color.WHITE, true)); mid.addView(txt(pair.second, 12f, cMUT))
                row.addView(mid, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                if (ce != null && i <= st) {
                    val a0 = ce.optLong("ts$i"); val a1 = if (i < st) ce.optLong("ts${i + 1}") else now
                    row.addView(txt("${maxOf(0L, a1 - a0) / 1000}s" + (if (i < st) " ✓" else ""), 13f, if (i < st) cOK else cACC, true)
                        .apply { background = rr(cFIELD, 14); setPadding(d2p(12), d2p(6), d2p(12), d2p(6)) })
                }
                sc.addView(row, lpm(14))
            }
            clipsBox.addView(sc, lpm(12))
        } else clipsBox.addView(txt("Nothing is being generated right now.", 15f, cMUT), lpm(16))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); if (o.getString("state") != "failed") continue
            val b = Button(this).apply {
                isAllCaps = false; setTextColor(Color.WHITE); gravity = Gravity.START or Gravity.CENTER_VERTICAL; background = rr(cFIELD, 16)
                text = "Failed: ${title(o)}\n${o.optString("note")}\nTap to retry"
                setOnClickListener { Store.update(this@MainActivity, o.getString("id"), "state" to "queued"); startForegroundService(Intent(this@MainActivity, ClipService::class.java)) }
            }
            clipsBox.addView(b, lpm(10))
        }
        var nf = 0
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("state") == "failed") nf++
        if (nf > 0) clipsBox.addView(Button(this).apply {
            text = "Clear failed ($nf)"; isAllCaps = false; setTextColor(Color.WHITE); background = rr(cCARD, 16)
            setOnClickListener {
                for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("state") == "failed") Store.remove(this@MainActivity, o.getString("id")) }
                renderClips()
            }
        }, lpm(10))
    }

    // finished clips, grouped by video
    private fun renderDone() {
        val a = Store.all(this)
        val sig = StringBuilder()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("state") == "done") sig.append(o.getString("id")).append(';') }
        if (sig.toString() == lastSig) return
        lastSig = sig.toString(); clipsBox.removeAllViews()
        val groups = LinkedHashMap<String, ArrayList<JSONObject>>()
        for (i in a.length() - 1 downTo 0) { val o = a.getJSONObject(i); if (o.getString("state") == "done") groups.getOrPut(o.optString("batch")) { ArrayList() }.add(o) }
        if (groups.isEmpty()) { clipsBox.addView(txt("No clips yet. Tap Create to make some.", 15f, cMUT), lpm(16)); return }
        val df = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
        for ((_, list) in groups) {
            list.sortBy { it.optInt("n") }
            val head = card()
            head.addView(txt(title(list[0]), 18f, Color.WHITE, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            head.addView(txt("${list.size} clip(s) from this video", 14f, cACC, true), lpm(4))
            clipsBox.addView(head, lpm(14))
            for (o in list) clipsBox.addView(clipCard(o, df), lpm(10))
        }
    }

    private fun uriFor(o: JSONObject): Uri {
        val u = o.optString("uri"); if (u.isNotEmpty()) return Uri.parse(u)
        val uri = save(File(o.getString("path")), "Clip_${o.getString("id")}.mp4")
        Store.update(this, o.getString("id"), "uri" to uri.toString()); return uri
    }

    private fun clipCard(o: JSONObject, df: java.text.SimpleDateFormat): View {
        val c = card(); val top = LinearLayout(this)
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = rr(cFIELD, 12)
            BitmapFactory.decodeFile(o.optString("thumb"))?.let { setImageBitmap(it) } }
        top.addView(iv, LinearLayout.LayoutParams(d2p(84), d2p(110)))
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d2p(14), 0, 0, 0) }
        val hd = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hd.addView(txt("Clip ${o.optInt("n", 1)}", 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        hd.addView(txt("9:16 Shorts", 11f, cACC, true).apply { background = rr(cFIELD, 12); setPadding(d2p(10), d2p(4), d2p(10), d2p(4)) })
        mid.addView(hd); mid.addView(txt("Duration: ${o.optInt("length")}s", 14f, cACC, true), lpm(6))
        mid.addView(txt(df.format(java.util.Date(o.optLong("finished"))), 13f, cMUT), lpm(4))
        top.addView(mid, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        c.addView(top)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(Button(this).apply { text = "▶ Play Clip"; isAllCaps = false; setTextColor(Color.WHITE); background = rr(cACC, 12)
            setOnClickListener { play(o.getString("path")) } }, LinearLayout.LayoutParams(0, d2p(44), 1f))
        fun ib(icon: Int, tint: Int, f: () -> Unit) = ImageButton(this).apply { setImageResource(icon); setColorFilter(tint); background = null; setOnClickListener { f() } }
        row.addView(ib(android.R.drawable.stat_sys_download, Color.WHITE) {
            try { uriFor(o); Toast.makeText(this, "Saved to Movies/Clipper", Toast.LENGTH_LONG).show() }
            catch (e: Exception) { Toast.makeText(this, "Could not save: ${e.message}", Toast.LENGTH_LONG).show() }
        }, LinearLayout.LayoutParams(d2p(52), d2p(48)).apply { leftMargin = d2p(6) })
        row.addView(ib(android.R.drawable.ic_menu_share, Color.WHITE) {
            try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uriFor(o)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share clip")) }
            catch (e: Exception) { Toast.makeText(this, "Could not share: ${e.message}", Toast.LENGTH_LONG).show() }
        }, LinearLayout.LayoutParams(d2p(52), d2p(48)))
        row.addView(ib(android.R.drawable.ic_menu_delete, 0xFFFF5A5A.toInt()) {
            AlertDialog.Builder(this).setTitle("Delete Clip ${o.optInt("n", 1)}?").setMessage(title(o))
                .setPositiveButton("Delete") { _, _ ->
                    File(o.optString("path")).delete(); File(o.optString("thumb")).delete(); Store.remove(this, o.getString("id")); lastSig = ""; renderClips() }
                .setNegativeButton("Cancel", null).show()
        }, LinearLayout.LayoutParams(d2p(52), d2p(48)))
        c.addView(row, lpm(12))
        return c
    }

    // youtu.be/..., /live/..., /shorts/... all become the plain watch link (YouTube checks these links less strictly)
    private fun cleanLink(l: String): String {
        val m = Regex("(?:v=|youtu\\.be/|/live/|/shorts/|/embed/)([A-Za-z0-9_-]{11})").find(l)
        return if (m != null && l.contains("youtu")) "https://www.youtube.com/watch?v=${m.groupValues[1]}" else l
    }

    private fun loginYouTube(onDone: () -> Unit) {
        val wv = android.webkit.WebView(this)
        wv.settings.javaScriptEnabled = true; wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        wv.webViewClient = android.webkit.WebViewClient()
        wv.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val bar = Button(this).apply { text = "Done: save my login"; setOnClickListener { saveCookies(); d.dismiss(); onDone() } }
        d.setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(bar); addView(wv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)) })
        d.show()
    }

    private fun saveCookies() {
        val cm = android.webkit.CookieManager.getInstance(); cm.flush()
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        for ((url, dom) in listOf("https://www.youtube.com" to ".youtube.com", "https://m.youtube.com" to ".youtube.com", "https://accounts.google.com" to ".google.com")) {
            val raw = cm.getCookie(url) ?: continue
            for (kv in raw.split(";")) {
                val i = kv.indexOf('='); if (i < 1) continue
                sb.append("$dom\tTRUE\t/\tTRUE\t2147483647\t${kv.substring(0, i).trim()}\t${kv.substring(i + 1).trim()}\n")
            }
        }
        File(getExternalFilesDir(null), "cookies.txt").writeText(sb.toString())
        Toast.makeText(this, "Login saved on this phone", Toast.LENGTH_LONG).show()
    }

    private fun play(path: String) {
        val vv = VideoView(this); vv.setVideoPath(path)
        vv.setMediaController(MediaController(this).also { it.setAnchorView(vv) })
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        d.setContentView(FrameLayout(this).apply { addView(vv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER)) })
        d.setOnDismissListener { vv.stopPlayback() }
        vv.setOnPreparedListener { it.isLooping = true; vv.start() }
        d.show()
    }

    private fun save(f: File, name: String): Uri {
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Clipper")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)!!
        contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        return uri
    }
}

fun secs(t: String): Int {
    val s = t.trim()
    if (!s.contains(":")) { val n = s.toInt(); return n / 10000 * 3600 + n / 100 % 100 * 60 + n % 100 }
    return s.split(":").fold(0) { a, p -> a * 60 + p.toInt() }
}
fun fmt(s: Int) = "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)

object Store {
    private fun f(c: Context) = File(c.filesDir, "clips.json")
    @Synchronized fun all(c: Context): JSONArray = try { JSONArray(f(c).readText()) } catch (e: Exception) { JSONArray() }
    @Synchronized private fun put(c: Context, a: JSONArray) { f(c).writeText(a.toString()) }
    @Synchronized fun add(c: Context, o: JSONObject) { val a = all(c); a.put(o); put(c, a) }
    @Synchronized fun update(c: Context, id: String, vararg kv: Pair<String, Any>) {
        val a = all(c)
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("id") == id) kv.forEach { o.put(it.first, it.second) } }
        put(c, a)
    }
    @Synchronized fun remove(c: Context, id: String) {
        val a = all(c); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) n.put(a.getJSONObject(i))
        put(c, n)
    }
    @Synchronized fun nextQueued(c: Context): JSONObject? {
        val a = all(c)
        val now = System.currentTimeMillis()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("state") == "queued" && o.optLong("wait", 0L) <= now) return o }
        return null
    }
    @Synchronized fun hasWaiting(c: Context): Boolean {
        val a = all(c)
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("state") == "queued") return true
        return false
    }
}

// Runs in the background (foreground service) so clips keep being made when the app is closed.
class ClipService : Service() {
    companion object { @Volatile var updated = false }
    @Volatile private var running = false
    private var cur = ""
    private var lastNote = ""
    private val subsCache = HashMap<String, JSONObject?>()

    private fun yt(link: String) = YoutubeDLRequest(link).apply {
        addOption("--force-ipv4"); addOption("--socket-timeout", "30"); addOption("--retries", "5")
        val ck = File(getExternalFilesDir(null), "cookies.txt")                      // saved by the "Sign in to YouTube" button
        if (ck.exists()) addOption("--cookies", ck.absolutePath)
        else addOption("--extractor-args", "youtube:player_client=${clients[clientIdx]}")   // another way to ask YouTube each time one gets the bot check
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("clips", "Clip progress", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notif("Starting..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!running) {
            running = true
            Thread {
                val wl = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clipper:work")
                wl.acquire(6 * 60 * 60 * 1000L)
                try { work() } catch (e: Exception) { } finally { wl.release(); running = false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }.start()
        }
        return START_STICKY
    }

    private fun notif(t: String): Notification = Notification.Builder(this, "clips")
        .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Clipper").setContentText(t).setOngoing(true).build()

    private fun say(t: String) {
        if (t == lastNote) return
        lastNote = t
        if (cur.isNotEmpty()) Store.update(this, cur, "note" to t)
        val a = Store.all(this); var done = 0; var left = 0
        for (i in 0 until a.length()) when (a.getJSONObject(i).getString("state")) { "done" -> done++; "failed" -> {}; else -> left++ }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notif("$done ready, $left left. $t"))
    }

    private fun work() {
        val a = Store.all(this)
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("state") == "working") Store.update(this, o.getString("id"), "state" to "queued") }
        YoutubeDL.getInstance().init(application); FFmpeg.getInstance().init(application)
        if (!updated) {
            say("Updating the downloader...")
            try { YoutubeDL.getInstance().updateYoutubeDL(application, YoutubeDL.UpdateChannel.NIGHTLY) } catch (e: Exception) { }
            updated = true
        }
        while (true) {
            val e = Store.nextQueued(this) ?: if (Store.hasWaiting(this)) { Thread.sleep(5000); continue } else break
            cur = e.getString("id"); lastNote = ""
            Store.update(this, cur, "state" to "working", "note" to "Starting"); curStage = -1; stage(0)
            val dir = File(getExternalFilesDir(null), "work/$cur").apply { deleteRecursively(); mkdirs() }
            var attempt = 0; val startClient = clientIdx
            while (true) {
            try {
                clientIdx = (startClient + attempt) % clients.size
                val link = e.getString("link"); val len = e.getInt("length")
                if (e.getString("kind") == "auto") {
                    val subs0 = if (e.getBoolean("caps")) { if (!subsCache.containsKey(link)) subsCache[link] = fetchSubs(link, dir); subsCache[link] } else null
                    val starts = snap(findBest(link, len, dir, subs0, e.optInt("count", 3), e.optBoolean("brain")), subs0)
                    if (starts.isEmpty()) Store.update(this, cur, "state" to "failed", "note" to lastNote.ifEmpty { "Nothing found" })
                    else {
                        val base = cur; cur = ""
                        Store.remove(this, base)
                        starts.forEachIndexed { i, s -> Store.add(this, JSONObject().put("id", "$base-$i").put("n", i + 1).put("batch", e.optString("batch")).put("t0", e.optLong("t0")).put("title", lastTitle).put("kind", "clip").put("link", link)
                            .put("start", s).put("length", len).put("caps", e.getBoolean("caps")).put("cam", e.optString("cam")).put("lang", e.optString("lang", "en")).put("blur", e.optBoolean("blur")).put("wm", e.optString("wm")).put("hq", e.optInt("hq", 720)).put("brain", e.optBoolean("brain")).put("state", "queued").put("note", "").put("path", "")) }
                    }
                } else {
                    val s = e.getInt("start")
                    val subs = if (e.getBoolean("caps")) { if (!subsCache.containsKey(link)) subsCache[link] = fetchSubs(link, dir); subsCache[link] } else null
                    val want = e.getBoolean("caps"); capWhy = ""
                    val (f, caps) = try { render2(link, s, len, dir, subs, e.optString("cam"), want, e.optString("lang", "en"), e.optBoolean("blur"), e.optString("wm"), e.optInt("hq", 720), e.optBoolean("brain")) }
                        catch (ex: Exception) { say("Trying the simple method..."); capWhy = " (simple method)"; render(link, 0, s, len, dir, subs) }
                    val out = File(getExternalFilesDir("clips"), "$cur.mp4")
                    f.copyTo(out, true)
                    val th = File(getExternalFilesDir("clips"), "$cur.jpg"); thumb(f, th)
                    Store.update(this, cur, "state" to "done", "path" to out.absolutePath, "thumb" to th.absolutePath, "finished" to System.currentTimeMillis(), "note" to ((if (caps) "with captions" else "no captions") + capWhy))
                }
                break
            } catch (ex: Exception) {
                val m = ex.message ?: ""
                if (isBot(m) && attempt < clients.size - 1) { attempt++; say("YouTube check: trying another way (${attempt + 1}/${clients.size})..."); continue }
                val tries = e.optInt("tries", 0)
                if (isNet(m)) Store.update(this, cur, "state" to "queued", "wait" to System.currentTimeMillis() + 20_000L, "note" to "Waiting for internet...")
                else if (isBot(m) && tries < 3) Store.update(this, cur, "state" to "queued", "tries" to tries + 1, "wait" to System.currentTimeMillis() + 90_000L, "note" to "YouTube check. Trying again in a minute...")
                else Store.update(this, cur, "state" to "failed", "note" to friendly(m))
                break
            }
            }
            dir.deleteRecursively()
        }
    }

    // ---- AI part: finds the most hyped moments ----
    private var capWhy = ""
    private var lastErr = ""
    private var lastTitle = ""
    private var clientIdx = 0
    private val clients = listOf("android_vr", "tv", "web_embedded", "mweb,web_safari", "default")
    private fun isBot(m: String) = m.contains("Sign in") || m.contains("not a bot") || m.contains("cookies")
    private fun isNet(m: String) = m.contains("Errno 7") || m.contains("Errno 101") || m.contains("No address") || m.contains("Unable to resolve") ||
        m.contains("UnknownHost") || m.contains("timed out") || m.contains("unreachable") || m.contains("Failed to resolve") || m.contains("Temporary failure")
    private var sf = 1f   // output size factor (1.0 = 720x1280, 1.5 = 1080x1920)

    private fun friendly(m: String): String = when {
        isBot(m) -> "YouTube kept asking for a bot check, even after trying every way. Use the Sign in to YouTube button once, or retry later."
        m.contains("Errno 7") || m.contains("No address") -> "Could not reach YouTube (network or DNS problem). Check your internet and retry."
        else -> m.takeLast(150)
    }

    // watermark text: light yellow-white letters with a black shadow, no background box
    private fun makeWm(text: String, dir: File): File {
        val W = (720 * sf).toInt(); val H = (90 * sf).toInt()
        val bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val c = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 38f * sf; textAlign = Paint.Align.CENTER }
        var sz = 38f * sf
        while (p.measureText(text) > W - 40 * sf && sz > 16f * sf) { sz -= 2f; p.textSize = sz }
        val y = H / 2f + p.textSize * 0.35f
        p.style = Paint.Style.STROKE; p.strokeWidth = 7f * sf; p.strokeJoin = Paint.Join.ROUND; p.color = 0xB3000000.toInt(); c.drawText(text, W / 2f, y, p)
        p.style = Paint.Style.FILL; p.setShadowLayer(8f * sf, 0f, 3f * sf, Color.BLACK); p.color = 0xFFFFF4B0.toInt(); c.drawText(text, W / 2f, y, p)
        val f = File(dir, "wm.png"); f.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }
    private var curStage = -1

    private fun stage(k: Int) {
        if (k <= curStage || cur.isEmpty()) return
        val now = System.currentTimeMillis(); val kv = ArrayList<Pair<String, Any>>()
        for (j in (curStage + 1)..k) kv.add("ts$j" to now)
        kv.add("stage" to k); curStage = k
        Store.update(this, cur, *kv.toTypedArray())
    }

    private fun thumb(f: File, out: File) {
        try {
            val r = MediaMetadataRetriever(); r.setDataSource(f.absolutePath)
            val b = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return
            out.outputStream().use { Bitmap.createScaledBitmap(b, 216, 384, true).compress(Bitmap.CompressFormat.JPEG, 80, it) }
        } catch (e: Exception) { }
    }

    private fun snap(starts: List<Int>, subs: JSONObject?): List<Int> {
        val ev = subs?.optJSONArray("events") ?: return starts
        val ts = ArrayList<Int>()
        for (i in 0 until ev.length()) { val o = ev.getJSONObject(i); if (o.has("segs")) ts.add((o.optLong("tStartMs") / 1000).toInt()) }
        return starts.map { s -> ts.filter { it in (s - 6)..(s + 2) }.minByOrNull { abs(it - s) }?.let { maxOf(0, it - 1) } ?: s }
    }

    private fun ffmpegBin(): File? = listOf(File(applicationInfo.nativeLibraryDir, "libffmpeg.so"),
        File(noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib/libffmpeg.so")).firstOrNull { it.exists() }

    private fun runFfmpeg(args: List<String>): Boolean {
        val bin = ffmpegBin() ?: throw RuntimeException("ffmpeg not found")
        val pb = ProcessBuilder(listOf(bin.absolutePath) + args).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = listOf(applicationInfo.nativeLibraryDir,
            File(noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").absolutePath).joinToString(":")
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        val ok = p.waitFor() == 0
        if (!ok) lastErr = out.takeLast(200)
        return ok
    }

    // crop box [w, h, x, y] of the webcam, found by face detection or by the corner the user picked
    private fun camCrop(raw: File, pre: Int, length: Int, mode: String): IntArray {
        val r = MediaMetadataRetriever(); r.setDataSource(raw.absolutePath)
        val W = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val H = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        fun ev(v: Int) = v / 2 * 2
        if (mode != "auto") {
            val cw = ev((W * 0.30).toInt()); val ch = ev(minOf(H, cw * 560 / 720))
            return intArrayOf(cw, ch, if (mode.endsWith("l")) 0 else W - cw, if (mode.startsWith("t")) 0 else H - ch)
        }
        val sc = 640f / W
        val pts = ArrayList<FloatArray>()
        for (k in 1..5) {
            val bm = r.getScaledFrameAtTime((pre + length * k / 6) * 1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, (H * sc).toInt() / 2 * 2) ?: continue
            val b = bm.copy(Bitmap.Config.RGB_565, false)
            val faces = arrayOfNulls<FaceDetector.Face>(4)
            val n = FaceDetector(b.width, b.height, 4).findFaces(b, faces)
            for (i in 0 until n) { val pt = PointF(); faces[i]!!.getMidPoint(pt); pts.add(floatArrayOf(pt.x, pt.y, faces[i]!!.eyesDistance())) }
        }
        fun support(a: FloatArray) = pts.count { abs(a[0] - it[0]) < 50 && abs(a[1] - it[1]) < 50 }
        val best = pts.maxByOrNull { support(it) * 1000 + it[2] }
        if (best == null || support(best) < 2) throw RuntimeException("webcam not found")
        val cw = ev(minOf(W, (best[2] / sc * 6.2f).toInt().coerceAtLeast((W * 0.18).toInt())))
        val ch = ev(minOf(H, cw * 560 / 720))
        return intArrayOf(cw, ch, ev((best[0] / sc - cw / 2).toInt().coerceIn(0, W - cw)), ev((best[1] / sc - ch * 0.45).toInt().coerceIn(0, H - ch)))
    }

    private fun drawCap(words: List<String>, act: Int, W: Int, H: Int, out: File) {
        val bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val c = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 66f * sf }
        val gap = 22f * sf
        fun total() = (words.sumOf { p.measureText(it).toDouble() } + gap * (words.size - 1)).toFloat()
        if (total() > W - 40 * sf) p.textSize = 66f * sf * (W - 40 * sf) / total()
        var x = (W - total()) / 2; val y = H / 2f + p.textSize * 0.35f
        words.forEachIndexed { i, w ->
            val wd = p.measureText(w)
            if (i == act) {
                p.style = Paint.Style.FILL; p.color = 0xFFFFD21F.toInt()
                c.drawRoundRect(RectF(x - 14 * sf, y - p.textSize * 0.95f, x + wd + 14 * sf, y + p.textSize * 0.28f), 18f * sf, 18f * sf, p)
                p.color = Color.BLACK; c.drawText(w, x, y, p)
            } else {
                p.style = Paint.Style.STROKE; p.strokeWidth = 9f * sf; p.strokeJoin = Paint.Join.ROUND; p.color = Color.BLACK; c.drawText(w, x, y, p)
                p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawText(w, x, y, p)
            }
            x += wd + gap
        }
        out.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // word-by-word caption pictures (3 words per line, current word in a yellow box), played back as an image list
    private fun ytWords(subs: JSONObject, s: Int, len: Int): List<CapWord> {
        val evs = subs.optJSONArray("events") ?: return emptyList()
        val ws = ArrayList<CapWord>()
        for (i in 0 until evs.length()) {
            val e = evs.getJSONObject(i); val segs = e.optJSONArray("segs") ?: continue; val st = e.optLong("tStartMs")
            for (j in 0 until segs.length()) {
                val g = segs.getJSONObject(j); val w = g.optString("utf8").trim(); if (w.isEmpty()) continue
                ws.add(CapWord((st + g.optLong("tOffsetMs")) / 1000.0 - s, 0.0, w.uppercase()))
            }
        }
        return ws
    }

    // offline speech recognition (Vosk): word times come from the real audio of the clip, so captions stay in sync
    private var voskModel: org.vosk.Model? = null
    private var voskName = ""
    private fun voskWords(raw: File, pre: Int, length: Int, dir: File, lang: String): List<CapWord> {
        val name = when (lang) { "hi" -> "vosk-model-small-hi-0.22"; "in" -> "vosk-model-small-en-in-0.4"; else -> "vosk-model-small-en-us-0.15" }
        val root = File(getExternalFilesDir(null), "vosk"); val md = File(root, name)
        if (!md.exists()) {
            root.mkdirs(); val zip = File(root, "$name.zip")
            val c = java.net.URL("https://alphacephei.com/vosk/models/$name.zip").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 30000
            val total = c.contentLength.toLong(); var got = 0L
            c.inputStream.use { i -> zip.outputStream().use { o ->
                val b = ByteArray(65536)
                while (true) { val n = i.read(b); if (n < 0) break; o.write(b, 0, n); got += n; if (total > 0) say("Downloading the speech model (one time): ${got * 100 / total}%") } } }
            java.util.zip.ZipInputStream(zip.inputStream()).use { z ->
                while (true) {
                    val en = z.nextEntry ?: break; val f = File(root, en.name)
                    if (!f.canonicalPath.startsWith(root.canonicalPath)) continue
                    if (en.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                }
            }
            zip.delete()
        }
        if (voskName != name) { voskModel = org.vosk.Model(md.absolutePath); voskName = name }
        say("Listening to the speech...")
        val pcm = File(dir, "a.pcm")
        if (!runFfmpeg(listOf("-y", "-ss", "$pre", "-i", raw.absolutePath, "-t", "$length", "-vn", "-ac", "1", "-ar", "16000", "-f", "s16le", pcm.absolutePath)))
            throw RuntimeException("audio extract failed")
        val rec = org.vosk.Recognizer(voskModel, 16000f); rec.setWords(true)
        val out = ArrayList<CapWord>()
        fun grab(js: String) {
            val a = JSONObject(js).optJSONArray("result") ?: return
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); out.add(CapWord(o.getDouble("start"), o.getDouble("end"), o.getString("word").uppercase())) }
        }
        pcm.inputStream().use { ins -> val b = ByteArray(8000); while (true) { val n = ins.read(b); if (n < 0) break; if (rec.acceptWaveForm(b, n)) grab(rec.result) } }
        grab(rec.finalResult); rec.close()
        return out
    }

    private fun makeCaps(words: List<CapWord>, len: Int, dir: File): File? {
        val ws = words.filter { it.a >= 0 && it.a < len }.sortedBy { it.a }
        if (ws.isEmpty()) return null
        for (i in ws.indices) ws[i].b = if (i + 1 < ws.size) minOf(ws[i + 1].a, ws[i].a + 1.2) else ws[i].a + 0.8
        val W = (720 * sf).toInt(); val H = (170 * sf).toInt()
        val blank = File(dir, "blank.png")
        Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).let { b -> blank.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val sb = StringBuilder(); var t = 0.0; var n = 0
        fun add(f: File, d: Double) { sb.append("file '${f.absolutePath}'\nduration ${String.format(java.util.Locale.US, "%.3f", d)}\n") }
        var i = 0
        while (i < ws.size) {
            var j = i + 1
            while (j < ws.size && j - i < 3 && ws[j].a - ws[j - 1].b < 0.6) j++
            val grp = ws.subList(i, j)
            for (k in grp.indices) {
                val img = File(dir, "cap${n++}.png"); drawCap(grp.map { it.t }, k, W, H, img)
                val st = maxOf(grp[k].a, t)
                if (st > t + 0.02) add(blank, st - t)
                val en = maxOf(st + 0.08, grp[k].b); add(img, en - st); t = en
            }
            i = j
        }
        if (t < len) add(blank, len - t)
        sb.append("file '${blank.absolutePath}'\n")
        return File(dir, "caps.txt").also { it.writeText(sb.toString()) }
    }

    // Gets the exact piece of video+audio. Preferred: our own FFmpeg seeks both streams to the same second (keeps sound in sync).
    // Fallback: yt-dlp section download with forced cuts. Returns how many seconds of lead-in the file has.
    private fun fetchRaw(link: String, s: Int, length: Int, dir: File, hq: Int): Int {
        val fmtSel = "bv*[height<=$hq]+ba/b[height<=$hq]/b"
        try {
            say("Getting the video address...")
            val g = yt(link); g.addOption("-g"); g.addOption("-e"); g.addOption("--no-playlist"); g.addOption("-f", fmtSel)
            val lines = YoutubeDL.getInstance().execute(g).out.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val urls = lines.filter { it.startsWith("http") }
            lines.firstOrNull { !it.startsWith("http") }?.let { t -> if (cur.isNotEmpty()) Store.update(this, cur, "title" to t) }
            if (urls.isEmpty()) throw RuntimeException("no address")
            say("Cutting the clip...")
            val raw = File(dir, "raw.mp4")
            val maps = if (urls.size > 1) listOf("-map", "0:v:0", "-map", "1:a:0") else listOf("-map", "0:v:0", "-map", "0:a:0?")
            val ok = runFfmpeg(listOf("-y") + urls.flatMap { listOf("-ss", s.toString(), "-i", it) } + listOf("-t", "$length") + maps +
                listOf("-c:v", "libx264", "-preset", "ultrafast", "-crf", "20", "-c:a", "aac", raw.absolutePath))
            if (ok && raw.length() > 10000) return 0
            raw.delete()
        } catch (e: Exception) { File(dir, "raw.mp4").delete() }
        val pre = minOf(20, s)
        say("Downloading the clip (slower method)...")
        val req = yt(link)
        req.addOption("-f", fmtSel); req.addOption("--merge-output-format", "mkv")
        req.addOption("--download-sections", "*${fmt(s - pre)}-${fmt(s + length)}"); req.addOption("--force-keyframes-at-cuts")
        req.addOption("-o", "${dir.absolutePath}/raw.%(ext)s")
        YoutubeDL.getInstance().execute(req) { p, _, _ -> say("Downloading the clip... ${p.toInt().coerceAtLeast(0)}%") }
        return pre
    }

    // main pipeline: download with a lead-in, then our own FFmpeg run (accurate cut, webcam layout, captions)
    private fun render2(link: String, s: Int, length: Int, dir: File, subs: JSONObject?, cam: String, want: Boolean, lang: String, blur: Boolean, wm: String, hq: Int, brain: Boolean): Pair<File, Boolean> {
        val k = if (hq >= 1080) 1.5 else 1.0; sf = k.toFloat()
        val OW = (720 * k).toInt(); val OH = (1280 * k).toInt(); val topH = (560 * k).toInt(); val sqH = (720 * k).toInt()
        val pre = fetchRaw(link, s, length, dir, hq)
        val raw = dir.listFiles { x -> x.name.startsWith("raw.") && !x.name.endsWith(".part") && !x.name.endsWith(".ytdl") }?.firstOrNull()
            ?: throw RuntimeException("download failed")
        var layout = "crop=ih*9/16:ih,scale=$OW:$OH"; var capY = (820 * k).toInt(); var camOn = false
        if (cam.isEmpty() && blur) layout = "split[a][b];[a]scale=$OW:$OH:force_original_aspect_ratio=increase,crop=$OW:$OH,boxblur=${(20 * k).toInt()}:2[bg];[b]scale=$OW:-2[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2"
        if (cam.isNotEmpty()) {
            say("Finding the webcam...")
            try {
                val c = camCrop(raw, pre, length, cam)
                layout = "split[a][b];[a]crop=${c[0]}:${c[1]}:${c[2]}:${c[3]},scale=$OW:$topH[c];[b]crop=ih:ih:(iw-ih)/2:0,scale=$OW:$sqH[g];[c][g]vstack"; capY = (475 * k).toInt(); camOn = true
            } catch (e: Exception) { capWhy += " (webcam not found, pick its corner)" }
        }
        var capList: File? = null
        if (want) {
            stage(1)
            var words: List<CapWord>? = null
            val key = getSharedPreferences("clipper", 0).getString("groq", "") ?: ""
            if (brain && key.isNotBlank()) {
                say("AI is writing the exact words...")
                words = try { groqWords(raw, pre, length, dir, lang, key) }
                    catch (e: Exception) { capWhy += " (AI transcription problem: ${(e.message ?: "").takeLast(50)})"; null }
            }
            if (words.isNullOrEmpty()) words = try { voskWords(raw, pre, length, dir, lang) }
                catch (e: Exception) { capWhy += " (speech model problem: ${(e.message ?: "").takeLast(60)})"; null }
            if (words.isNullOrEmpty() && subs != null) words = ytWords(subs, s, length)
            stage(2)
            if (!words.isNullOrEmpty()) { say("Drawing captions..."); capList = try { makeCaps(words, length, dir) } catch (e: Exception) { null } }
            if (capList == null) capWhy += " (no speech found)"
        }
        stage(2)
        say("Rendering the clip...")
        val wmFile = if (wm.isNotBlank()) try { makeWm(wm.trim().take(32), dir) } catch (e: Exception) { null } else null
        val wmY = ((if (camOn) 1190 else 1095) * k).toInt()   // bottom middle (a little higher on the blurred layout)
        val inputs = ArrayList<String>(listOf("-i", raw.absolutePath))
        var fc = "[0:v]trim=start=$pre,setpts=PTS-STARTPTS,$layout[base];"; var last = "base"; var idx = 1
        if (capList != null) {
            inputs.addAll(listOf("-f", "concat", "-safe", "0", "-i", capList.absolutePath))
            fc += "[$idx:v]format=rgba[cap];[$last][cap]overlay=0:$capY:eof_action=pass[c1];"; last = "c1"; idx++
        }
        if (wmFile != null) {
            inputs.addAll(listOf("-loop", "1", "-i", wmFile.absolutePath))
            fc += "[$idx:v]format=rgba[wm];[$last][wm]overlay=0:$wmY:shortest=1[c2];"; last = "c2"; idx++
        }
        fc += "[$last]format=yuv420p[v];[0:a]atrim=start=$pre,asetpts=PTS-STARTPTS[a]"
        val out = File(dir, "out.mp4"); var ok = false
        for (enc in listOf(listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", if (hq >= 1080) "23" else "26"), listOf("-c:v", "mpeg4", "-q:v", "5"))) {
            ok = runFfmpeg(listOf("-y") + inputs + listOf("-filter_complex", fc, "-map", "[v]", "-map", "[a]", "-t", "$length") + enc +
                listOf("-c:a", "aac", "-movflags", "+faststart", out.absolutePath))
            if (ok) break
        }
        if (!ok) throw RuntimeException(lastErr)
        return Pair(out, capList != null)
    }

    // ---- AI brain (Groq, free key): exact transcription + picking the most viral moments ----
    private fun groqWords(raw: File, pre: Int, length: Int, dir: File, lang: String, key: String): List<CapWord> {
        val a = File(dir, "a.m4a")
        if (!runFfmpeg(listOf("-y", "-ss", "$pre", "-i", raw.absolutePath, "-t", "$length", "-vn", "-ac", "1", "-ar", "16000", "-c:a", "aac", "-b:a", "48k", a.absolutePath)))
            throw RuntimeException("audio extract failed")
        val boundary = "----clipper${System.currentTimeMillis()}"
        val c = java.net.URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 120000
        c.setRequestProperty("Authorization", "Bearer $key"); c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        c.outputStream.use { o ->
            fun field(n: String, v: String) { o.write("--$boundary\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray()) }
            field("model", "whisper-large-v3-turbo"); field("response_format", "verbose_json"); field("timestamp_granularities[]", "word"); field("temperature", "0")
            when (lang) {
                "hi" -> field("language", "hi")
                "in" -> { field("language", "en"); field("prompt", "Namaste doston, aaj hum GTA khelenge, bahut mazaa aane wala hai.") }
                else -> field("language", "en")
            }
            o.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n".toByteArray())
            a.inputStream().use { it.copyTo(o) }
            o.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code !in 200..299) throw RuntimeException("Groq $code ${body.takeLast(80)}")
        val ws = JSONObject(body).optJSONArray("words") ?: return emptyList()
        return (0 until ws.length()).map { val o = ws.getJSONObject(it); CapWord(o.getDouble("start"), o.getDouble("end"), o.getString("word").trim().uppercase()) }.filter { it.t.isNotEmpty() }
    }

    private fun aiPick(cands: List<Int>, subs: JSONObject, len: Int, count: Int, key: String): List<Int>? {
        val evs = subs.optJSONArray("events") ?: return null
        val sb = StringBuilder(); var any = false
        cands.forEachIndexed { ci, s0 ->
            sb.append("CANDIDATE ${ci + 1} (video time ${fmt(s0)} to ${fmt(s0 + len)}):\n")
            for (i in 0 until evs.length()) {
                val e = evs.getJSONObject(i); val t = (e.optLong("tStartMs") / 1000).toInt()
                if (t < s0 || t > s0 + len) continue
                val sg = e.optJSONArray("segs") ?: continue
                val line = (0 until sg.length()).joinToString("") { sg.getJSONObject(it).optString("utf8") }.trim()
                if (line.isNotEmpty()) { sb.append("[$t] $line\n"); any = true }
            }
        }
        if (!any) return null
        val prompt = "You are an expert editor of viral short videos (Shorts, Reels, TikTok). Below are ${cands.size} candidate windows of $len seconds from one video. " +
            "Each line starts with [second] counted from the video start. Choose the best $count candidates for a viral clip: a strong hook in the first 3 seconds, emotion, humor, surprise, a complete mini-story. " +
            "For each pick give the start second where the clip should begin so the hook comes first (use a second inside that candidate). " +
            "Reply ONLY with JSON like {\"clips\":[{\"candidate\":1,\"start\":123}]}\n\n$sb"
        val body = JSONObject().put("model", "llama-3.3-70b-versatile").put("temperature", 0.3)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val c = java.net.URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 60000
        c.setRequestProperty("Authorization", "Bearer $key"); c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) return null
        val txt = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val arr = JSONObject(txt).getJSONArray("clips")
        val out = ArrayList<Int>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val ci = o.optInt("candidate", 0) - 1
            if (ci !in cands.indices) continue
            val st = o.optInt("start", cands[ci]).coerceIn(cands[ci], cands[ci] + len / 2)
            if (out.none { abs(it - st) < len }) out.add(st)
            if (out.size == count) break
        }
        return out.sorted()
    }

    private fun findBest(link: String, length: Int, dir: File, subs: JSONObject?, count: Int, brain: Boolean): List<Int> {
        say("Scanning the video for hyped moments")
        val r = yt(link)
        r.addOption("--dump-single-json"); r.addOption("--no-playlist"); r.addOption("--skip-download")
        val info = JSONObject(YoutubeDL.getInstance().execute(r).out)
        val dur = info.optDouble("duration", 0.0).toInt()
        lastTitle = info.optString("title")
        if (dur < length * 2) { say(if (info.optBoolean("is_live")) "This stream is still live. Auto-find works after it ends (or type your own moments)." else "Could not read this video's length, or it is too short."); return emptyList() }
        val score = DoubleArray(dur + 1)
        val hm = info.optJSONArray("heatmap")
        if (hm != null && hm.length() > 0) {            // YouTube "Most replayed" data
            for (i in 0 until hm.length()) {
                val h = hm.getJSONObject(i)
                for (t in h.optDouble("start_time").toInt() until minOf(h.optDouble("end_time").toInt() + 1, score.size)) score[t] = h.optDouble("value")
            }
        } else {                                         // fallback: loudest parts of the audio
            if (dur > 10800) { say("No 'most replayed' data and the video is over 3 hours. Type moments yourself."); return emptyList() }
            say("Listening to the audio (this can take a few minutes)")
            val a = yt(link)
            a.addOption("-f", "worstaudio[ext=m4a]/worstaudio"); a.addOption("-o", "${dir.absolutePath}/audio.%(ext)s")
            YoutubeDL.getInstance().execute(a) { p, _, _ -> say("Downloading audio to analyze: ${p.toInt().coerceAtLeast(0)}%") }
            val f = dir.listFiles { x -> x.name.startsWith("audio.") }?.firstOrNull() ?: throw RuntimeException("audio download failed")
            val loud = loudness(f)
            for (i in loud.indices) if (i < score.size) score[i] = loud[i]
        }
        val mx = score.maxOrNull() ?: 0.0
        if (mx > 0) for (i in score.indices) score[i] /= mx
        val evs = subs?.optJSONArray("events")
        if (evs != null) {   // excited words in the captions make that second hotter
            val hype = setOf("omg", "bro", "bruh", "insane", "crazy", "wtf", "wow", "holy", "yo", "no", "nah", "dude", "damn", "god", "what", "clutch", "lets", "let's", "goat", "dead", "cooked", "huh")
            for (i in 0 until evs.length()) {
                val o = evs.getJSONObject(i); val sg = o.optJSONArray("segs") ?: continue
                var c = 0
                for (j in 0 until sg.length()) if (sg.getJSONObject(j).optString("utf8").trim().lowercase().trim('.', ',', '!', '?') in hype) c++
                val sec = (o.optLong("tStartMs") / 1000).toInt()
                if (c > 0 && sec in score.indices) score[sec] += 0.35 * c
            }
        }
        val key = getSharedPreferences("clipper", 0).getString("groq", "") ?: ""
        if (brain && key.isNotBlank() && subs != null) {
            val cands = pick(score, length, minOf(10, count * 3))
            say("AI is judging the best moments...")
            val ai = try { aiPick(cands, subs, length, count, key) } catch (e: Exception) { null }
            if (!ai.isNullOrEmpty()) return ai
        }
        return pick(score, length, count)
    }

    private fun pick(score: DoubleArray, len: Int, k: Int): List<Int> {
        val med = score.sorted()[score.size / 2]
        val pre = DoubleArray(score.size + 1)
        for (i in score.indices) pre[i + 1] = pre[i] + (if (i < 15) 0.0 else maxOf(0.0, score[i] - med))
        val ws = (0..maxOf(0, score.size - len)).sortedByDescending { pre[minOf(it + len, score.size)] - pre[it] }
        val out = ArrayList<Int>()
        for (w in ws) { if (out.none { abs(it - w) < len * 2 }) { out.add(w); if (out.size == k) break } }
        return out.map { maxOf(0, it - len / 6) }.sorted()
    }

    private fun loudness(f: File): DoubleArray {
        val ex = MediaExtractor(); ex.setDataSource(f.absolutePath)
        val ti = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio") }
        ex.selectTrack(ti); val fmt = ex.getTrackFormat(ti)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        val perSec = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).toLong() * fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val sums = ArrayList<Double>(); var acc = 0.0; var n = 0L
        val info = MediaCodec.BufferInfo(); var inDone = false; var outDone = false
        while (!outDone) {
            if (!inDone) {
                val i = codec.dequeueInputBuffer(10000)
                if (i >= 0) {
                    val sz = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (sz < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                    else { codec.queueInputBuffer(i, 0, sz, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10000)
            if (o >= 0) {
                val sb = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                while (sb.hasRemaining()) { val v = sb.get().toDouble(); acc += v * v; n++; if (n == perSec) { sums.add(sqrt(acc / n)); if (sums.size % 120 == 0) say("Analyzing audio: ${sums.size / 60} min done"); acc = 0.0; n = 0 } }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
            }
        }
        codec.stop(); codec.release(); ex.release()
        return sums.toDoubleArray()
    }

    // ---- captions: YouTube's own auto-captions, word by word ----
    private fun fetchSubs(link: String, dir: File): JSONObject? = try {
        val r = yt(link)
        r.addOption("--skip-download"); r.addOption("--write-auto-subs"); r.addOption("--write-subs")
        r.addOption("--sub-langs", "en.*,en"); r.addOption("--sub-format", "json3"); r.addOption("-o", "${dir.absolutePath}/subs")
        YoutubeDL.getInstance().execute(r)
        dir.listFiles { x -> x.name.startsWith("subs") && x.name.endsWith(".json3") }?.firstOrNull()?.let { JSONObject(it.readText()) }
    } catch (e: Exception) { null }

    private fun writeAss(subs: JSONObject, s: Int, len: Int, out: File): Boolean {
        val evs = subs.optJSONArray("events") ?: return false
        fun t(ms: Long) = "%d:%02d:%02d.%02d".format(ms / 3600000, ms % 3600000 / 60000, ms % 60000 / 1000, ms % 1000 / 10)
        val sb = StringBuilder(); val max = len * 1000L
        for (i in 0 until evs.length()) {
            val e = evs.getJSONObject(i); val segs = e.optJSONArray("segs") ?: continue; val st = e.optLong("tStartMs")
            for (j in 0 until segs.length()) {
                val g = segs.getJSONObject(j); val w = g.optString("utf8").trim(); if (w.isEmpty()) continue
                val a = st + g.optLong("tOffsetMs") - s * 1000L
                var b = st + (if (j + 1 < segs.length()) segs.getJSONObject(j + 1).optLong("tOffsetMs") else e.optLong("dDurationMs")) - s * 1000L
                if (b <= a) b = a + 300
                if (b <= 0 || a >= max) continue
                sb.append("Dialogue: 0,${t(maxOf(a, 0))},${t(minOf(b, max))},W,,0,0,0,,${w.uppercase()}\n")
            }
        }
        if (sb.isEmpty()) return false
        out.writeText("[Script Info]\nPlayResX: 1080\nPlayResY: 1920\n[V4+ Styles]\n" +
            "Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding\n" +
            "Style: W,Arial,110,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,1,8,2,5,40,40,0,1\n" +
            "[Events]\nFormat: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n" + sb)
        return true
    }

    // ---- cut + vertical crop + burn captions (falls back to no captions if this phone's FFmpeg can't) ----
    private fun render(link: String, i: Int, s: Int, length: Int, dir: File, subs: JSONObject?): Pair<File, Boolean> {
        val pre = minOf(20, s)
        val assF = File(dir, "c$i.ass")
        val tries = if (subs != null && writeAss(subs, s, length, assF)) listOf(Pair(true, true), Pair(false, true), Pair(false, false)) else listOf(Pair(false, true), Pair(false, false))
        var last: Exception? = null
        for ((caps, fast) in tries) {
            try {
                val vf = "trim=start=$pre,setpts=PTS-STARTPTS,crop=ih*9/16:ih,scale=720:1280" + if (caps) ",ass=${assF.absolutePath}" else ""
                val req = yt(link)
                req.addOption("-f", "bv*[height<=720]+ba/b[height<=720]/b")
                req.addOption("--merge-output-format", "mkv")
                req.addOption("--download-sections", "*${fmt(s - pre)}-${fmt(s + length)}"); req.addOption("--force-keyframes-at-cuts")
                req.addOption("--recode-video", "mp4")
                req.addOption("--postprocessor-args", "VideoConvertor:-vf $vf -af atrim=start=$pre,asetpts=PTS-STARTPTS" + (if (fast) " -preset ultrafast -crf 28" else ""))
                req.addOption("-o", "${dir.absolutePath}/clip$i.%(ext)s")
                YoutubeDL.getInstance().execute(req) { p, _, _ -> say("Downloading and cutting the clip... ${p.toInt().coerceAtLeast(0)}%") }
                val f = File(dir, "clip$i.mp4")
                if (!f.exists() || f.length() == 0L) throw RuntimeException("empty file")
                return Pair(f, caps)
            } catch (e: Exception) { last = e; File(dir, "clip$i.mp4").delete() }
        }
        throw last ?: RuntimeException("failed")
    }
}

class CapWord(val a: Double, var b: Double, val t: String)
