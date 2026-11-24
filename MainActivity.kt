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
    private val tick = object : Runnable { override fun run() { if (inClips) { renderClips(); h.postDelayed(this, 1500) } } }

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
        var length = 30
        val mBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val cap = Switch(this).apply { isChecked = true }

        fun times(): List<Int> = (0 until mBox.childCount).mapNotNull {
            ((mBox.getChildAt(it) as LinearLayout).getChildAt(0) as EditText).text.toString().trim().ifEmpty { null } }.map { secs(it) }

        go = btn("Create", ACC, WHITE) {
            try { val t = times(); enqueue(link.text.toString().trim(), t, length, t.isEmpty(), cap.isChecked) }
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
        col.addView(c2, lp(14))

        val c3 = card("3 · CAPTIONS")
        val cr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        cr.addView(tv("Captions for English speech", 16f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); cr.addView(cap)
        c3.addView(cr, lp(12))
        c3.addView(tv("Word by word. Clips without English speech get no captions.", 13f, MUT), lp(8))
        col.addView(c3, lp(14))

        col.addView(tv("Only clip streams you own or have permission to use.", 12f, MUT), lp(18))
        formView = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(col) }
        setContentView(formView)
        addM()
        intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { link.setText(it) }

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun say(t: String) = runOnUiThread { status.text = t }
    private fun enqueue(link: String, times: List<Int>, length: Int, find: Boolean, caps: Boolean) {
        if (!Regex("(youtube\\.com|youtu\\.be|twitch\\.tv)/").containsMatchIn(link)) { say("Paste a YouTube or Twitch link first."); return }
        val now = System.currentTimeMillis()
        fun entry(kind: String, start: Int, n: Int) = JSONObject().put("id", "$now-$n").put("kind", kind).put("link", link)
            .put("start", start).put("length", length).put("caps", caps).put("state", "queued").put("note", "").put("path", "")
        if (find) Store.add(this, entry("auto", 0, 0)) else times.forEachIndexed { i, t -> Store.add(this, entry("clip", t, i)) }
        startForegroundService(Intent(this, ClipService::class.java))
        showClips()
    }

    private fun showClips() {
        val dens = resources.displayMetrics.density
        fun dp(v: Int) = (v * dens).toInt()
        if (!::clipsView.isInitialized) {
            clipsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            clipsSummary = TextView(this).apply { textSize = 15f; setTextColor(0xFF8FA3C7.toInt()); setPadding(0, dp(8), 0, dp(12)) }
            val back = Button(this).apply {
                text = "Back"; isAllCaps = false; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor(0xFF121C3D.toInt()); cornerRadius = dp(16).toFloat() }
                setOnClickListener { hideClips() }
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(36), dp(16), dp(24))
                addView(TextView(this@MainActivity).apply { text = "My clips"; textSize = 32f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
                addView(clipsSummary); addView(clipsBox); addView(back)
            }
            clipsView = ScrollView(this).apply { setBackgroundColor(0xFF0A1128.toInt()); addView(col) }
        }
        inClips = true; lastSig = ""
        setContentView(clipsView)
        h.removeCallbacks(tick); h.post(tick)
    }

    private fun hideClips() { inClips = false; setContentView(formView) }
    override fun onBackPressed() { if (inClips) hideClips() else super.onBackPressed() }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }
    override fun onResume() { super.onResume(); if (inClips) { lastSig = ""; h.post(tick) } }

    private fun renderClips() {
        val a = Store.all(this)
        if (a.toString() == lastSig) return
        lastSig = a.toString()
        val dens = resources.displayMetrics.density
        var done = 0; var work = 0; var wait = 0
        clipsBox.removeAllViews()
        for (i in a.length() - 1 downTo 0) {
            val o = a.getJSONObject(i); val st = o.getString("state"); val id = o.getString("id")
            val title = if (o.getString("kind") == "auto") "Finding best moments" else "Clip at ${fmt(o.getInt("start"))}, ${o.getInt("length")} sec"
            val bt = Button(this).apply {
                isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.START or Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply { setColor(if (st == "done") 0xFF4C8DFF.toInt() else 0xFF1B2A55.toInt()); cornerRadius = 16 * dens }
            }
            when (st) {
                "done" -> { done++; bt.text = "Download: $title" + (if (o.optString("note").isNotEmpty()) "\n${o.getString("note")}" else "")
                    bt.setOnClickListener {
                        try { save(File(o.getString("path")), "Clip_${id}.mp4"); Toast.makeText(this, "Saved to Movies/Clipper", Toast.LENGTH_LONG).show() }
                        catch (e: Exception) { Toast.makeText(this, "Could not save: ${e.message}", Toast.LENGTH_LONG).show() } } }
                "working" -> { work++; bt.text = "In progress: $title\n${o.optString("note")}" }
                "failed" -> { bt.text = "Failed: $title\n${o.optString("note")}\nTap to retry"
                    bt.setOnClickListener { Store.update(this, id, "state" to "queued"); startForegroundService(Intent(this, ClipService::class.java)) } }
                else -> { wait++; bt.text = "Waiting: $title" }
            }
            clipsBox.addView(bt, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = (8 * dens).toInt() })
        }
        clipsSummary.text = "$done ready  |  $work in progress  |  $wait waiting\nClips keep being made even if you close the app."
    }

    private fun save(f: File, name: String) {
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Clipper")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)!!
        contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
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
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("state") == "queued") return o }
        return null
    }
}

// Runs in the background (foreground service) so clips keep being made when the app is closed.
class ClipService : Service() {
    companion object { @Volatile var updated = false }
    @Volatile private var running = false
    private var cur = ""
    private var lastNote = ""
    private val subsCache = HashMap<String, JSONObject?>()

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
            val e = Store.nextQueued(this) ?: break
            cur = e.getString("id"); lastNote = ""
            Store.update(this, cur, "state" to "working", "note" to "Starting")
            val dir = File(getExternalFilesDir(null), "work/$cur").apply { deleteRecursively(); mkdirs() }
            try {
                val link = e.getString("link"); val len = e.getInt("length")
                if (e.getString("kind") == "auto") {
                    val starts = findBest(link, len, dir)
                    if (starts.isEmpty()) Store.update(this, cur, "state" to "failed", "note" to lastNote.ifEmpty { "Nothing found" })
                    else {
                        val base = cur; cur = ""
                        Store.remove(this, base)
                        starts.forEachIndexed { i, s -> Store.add(this, JSONObject().put("id", "$base-$i").put("kind", "clip").put("link", link)
                            .put("start", s).put("length", len).put("caps", e.getBoolean("caps")).put("state", "queued").put("note", "").put("path", "")) }
                    }
                } else {
                    val s = e.getInt("start")
                    val subs = if (e.getBoolean("caps")) { if (!subsCache.containsKey(link)) subsCache[link] = fetchSubs(link, dir); subsCache[link] } else null
                    val (f, caps) = render(link, 0, s, len, dir, subs)
                    val out = File(getExternalFilesDir("clips"), "$cur.mp4")
                    f.copyTo(out, true)
                    Store.update(this, cur, "state" to "done", "path" to out.absolutePath, "note" to if (caps) "with captions" else "no captions")
                }
            } catch (ex: Exception) {
                Store.update(this, cur, "state" to "failed", "note" to (ex.message ?: "error").takeLast(150))
            } finally { dir.deleteRecursively() }
        }
    }

    // ---- AI part: finds the most hyped moments ----
    private fun findBest(link: String, length: Int, dir: File): List<Int> {
        say("Scanning the video for hyped moments")
        val r = YoutubeDLRequest(link)
        r.addOption("--dump-single-json"); r.addOption("--no-playlist"); r.addOption("--skip-download")
        val info = JSONObject(YoutubeDL.getInstance().execute(r).out)
        val dur = info.optDouble("duration", 0.0).toInt()
        if (dur < length * 2) { say("Can't scan this one (live right now, or too short). Try again after the stream ends."); return emptyList() }
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
            val a = YoutubeDLRequest(link)
            a.addOption("-f", "worstaudio[ext=m4a]/worstaudio"); a.addOption("-o", "${dir.absolutePath}/audio.%(ext)s")
            YoutubeDL.getInstance().execute(a) { p, _, _ -> say("Downloading audio to analyze: ${p.toInt().coerceAtLeast(0)}%") }
            val f = dir.listFiles { x -> x.name.startsWith("audio.") }?.firstOrNull() ?: throw RuntimeException("audio download failed")
            val loud = loudness(f)
            for (i in loud.indices) if (i < score.size) score[i] = loud[i]
        }
        return pick(score, length, 5)
    }

    private fun pick(score: DoubleArray, len: Int, k: Int): List<Int> {
        val pre = DoubleArray(score.size + 1)
        for (i in score.indices) pre[i + 1] = pre[i] + score[i]
        val ws = (0..maxOf(0, score.size - len)).sortedByDescending { pre[minOf(it + len, score.size)] - pre[it] }
        val out = ArrayList<Int>()
        for (w in ws) { if (out.none { abs(it - w) < len * 3 / 2 }) { out.add(w); if (out.size == k) break } }
        return out.sorted()
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
        val r = YoutubeDLRequest(link)
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
        val assF = File(dir, "c$i.ass")
        val tries = if (subs != null && writeAss(subs, s, length, assF)) listOf(Pair(true, true), Pair(false, true), Pair(false, false)) else listOf(Pair(false, true), Pair(false, false))
        var last: Exception? = null
        for ((caps, fast) in tries) {
            try {
                val vf = "crop=ih*9/16:ih,scale=720:1280" + if (caps) ",ass=${assF.absolutePath}" else ""
                val req = YoutubeDLRequest(link)
                req.addOption("-f", "bv*[height<=720]+ba/b[height<=720]/b")
                req.addOption("--merge-output-format", "mkv")
                req.addOption("--download-sections", "*${fmt(s)}-${fmt(s + length)}")
                req.addOption("--recode-video", "mp4")
                req.addOption("--postprocessor-args", "VideoConvertor:-vf $vf" + (if (fast) " -preset ultrafast -crf 28" else ""))
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
