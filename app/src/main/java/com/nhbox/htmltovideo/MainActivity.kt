package com.nhbox.htmltovideo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

class MainActivity : Activity() {

    // ---------- colors ----------
    private val cBg0 = Color.parseColor("#0A0F1F")
    private val cBg1 = Color.parseColor("#111A38")
    private val cCard = Color.parseColor("#141C3A")
    private val cCard2 = Color.parseColor("#1B2548")
    private val cStroke = Color.parseColor("#2A3563")
    private val cTxt = Color.parseColor("#F2F5FF")
    private val cSub = Color.parseColor("#98A5CC")
    private val cP1 = Color.parseColor("#7C5CFF")
    private val cP2 = Color.parseColor("#22D3EE")
    private val cOk = Color.parseColor("#34D399")
    private val cErr = Color.parseColor("#F87171")

    // ---------- views ----------
    private lateinit var web: WebView
    private lateinit var playerBox: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabPlayer: TextView
    private lateinit var tabEditor: TextView
    private lateinit var fileLabel: TextView
    private lateinit var btnPick: TextView
    private lateinit var btnSample: TextView
    private lateinit var btnStudio: TextView
    private lateinit var btnLast: TextView
    private lateinit var btnPreview: TextView
    private lateinit var btnExport: TextView
    private lateinit var btnCancel: TextView
    private lateinit var btnOpen: TextView
    private lateinit var btnShare: TextView
    private lateinit var btnTts: TextView
    private lateinit var resultRow: LinearLayout
    private lateinit var engInfo: TextView
    private lateinit var etKey: EditText
    private lateinit var etVoice: EditText
    private lateinit var keyRow: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var bar: ProgressBar
    private val engChips = ArrayList<TextView>()
    private val fpsChips = ArrayList<TextView>()
    private val aspectChips = ArrayList<TextView>()
    private val fitChips = ArrayList<TextView>()
    private lateinit var fitRow: LinearLayout
    private lateinit var exportTitle: TextView

    // ---------- state ----------
    private val engines = listOf("sarvam", "google", "phone", "myvoice", "off")
    private val engLabels = listOf("Sarvam AI", "Google", "Phone", "My Voice", "Off")
    private var curEng = "sarvam"
    private var fps = 60
    private var portrait = false   // false = 16:9, true = 9:16
    private var fillMode = false   // sirf 9:16: false = Fit (blur background), true = Fill (crop)
    private var editorMode = false
    private var htmlReady = false
    private var keyVisible = false
    @Volatile private var exporting = false
    @Volatile private var cancel = false
    @Volatile private var exporter: VideoExporter? = null
    private var savedUri: Uri? = null
    private var startMs = 0L
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var pendingPerm: PermissionRequest? = null

    // ---------- v3.2 My Voice ----------
    private lateinit var myCard: LinearLayout
    private lateinit var myInfo: TextView
    private lateinit var btnMyRec: TextView
    private lateinit var btnMyImport: TextView
    private lateinit var btnMyExport: TextView
    private lateinit var btnMyClear: TextView
    @Volatile private var myLines: List<String> = emptyList()
    private lateinit var etCloudUrl: EditText
    private lateinit var etCloudToken: EditText
    private lateinit var btnMySample: TextView
    private lateinit var btnMyTest: TextView
    private lateinit var btnMyCloud: TextView
    @Volatile private var cloudRunning = false
    @Volatile private var cloudStop = false
    private var pendingRecorder = false

    // ---------- v3 state ----------
    private var domMode = false            // scene/DOM wali HTML (jaise MovieBox promo): adapter NH API deta hai
    private var exportScaled = false       // export ke dauran WebView 1920x1080 px par rehta hai
    private val vols = intArrayOf(100, 100, 0, 100)     // master, voice, music(0=off), sfx (percent)
    private var duck = true
    private var autoSfx = true              // v3.4: page ka SFX/music na mile (ya fail ho) to built-in SFX + music
    private lateinit var btnAuto: TextView
    private val audioInfo = HashMap<String, Double>()
    @Volatile private var audioNote = ""
    private val volBars = ArrayList<SeekBar>()
    private lateinit var btnDuck: TextView
    private lateinit var btnVolReset: TextView
    private lateinit var btnTutorial: TextView
    private lateinit var btnMovie: TextView
    private lateinit var btnUiUx: TextView
    // v3.2: stems seedha ShortArray mein (kam memory); rate alag (music 12k, sfx 24k) taaki lambi video par bhi memory safe rahe
    private class Stem(val data: ShortArray, val rate: Int) { var pos = 0 }
    private val stemBufs = HashMap<String, Stem>()
    @Volatile private var audioLatch: CountDownLatch? = null
    @Volatile private var audioErr: String? = null
    private var capBmp: Bitmap? = null
    private var blankFrames = 0
    private var contentSeen = false
    private val domAdapterJs: String by lazy { assets.open("h2v_dom.js").bufferedReader().use { it.readText() } }
    private val sfxEngineJs: String by lazy { assets.open("h2v_sfx.js").bufferedReader().use { it.readText() } }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Int) = v * resources.displayMetrics.density

    // ---------- drawable helpers ----------
    private fun rounded(color: Int, r: Float, stroke: Int = 0, sw: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(color)
            if (sw > 0) setStroke(sw, stroke)
        }

    private fun grad(c1: Int, c2: Int, r: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply { cornerRadius = r }

    private fun ripple(bg: Drawable): Drawable =
        RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), bg, null)

    private fun enable(v: View, on: Boolean) {
        v.isEnabled = on
        v.alpha = if (on) 1f else 0.4f
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun button(text: String, primary: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary) Color.WHITE else cTxt)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = ripple(if (primary) grad(cP1, cP2, dpf(14)) else rounded(cCard2, dpf(14), cStroke, dp(1)))
            isClickable = true
            isFocusable = true
            setOnClickListener { if (isEnabled) onClick() }
        }

    private fun card(title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = rounded(cCard, dpf(20), cStroke, dp(1))
        }
        c.addView(label(title, 11f, cSub, true).apply { letterSpacing = 0.12f })
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12)
        c.layoutParams = lp
        return c
    }

    private fun rowOf(vararg views: View): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, v) in views.withIndex()) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = dp(10)
            r.addView(v, lp)
        }
        val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = dp(12)
        r.layoutParams = rlp
        return r
    }

    private fun chip(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(10), dp(8), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { if (isEnabled) onClick() }
        }

    private fun styleChip(c: TextView, selected: Boolean) {
        c.setTextColor(if (selected) Color.WHITE else cSub)
        c.background = ripple(if (selected) grad(cP1, cP2, dpf(12)) else rounded(cCard2, dpf(12), cStroke, dp(1)))
    }

    private fun input(hint: String): EditText =
        EditText(this).apply {
            this.hint = hint
            setHintTextColor(cSub)
            setTextColor(cTxt)
            textSize = 14f
            isSingleLine = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.parseColor("#0E1530"), dpf(12), cStroke, dp(1))
        }

    // ---------- lifecycle ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = cBg0
        window.navigationBarColor = cBg1
        getSharedPreferences("h2v", MODE_PRIVATE).let {
            portrait = it.getBoolean("portrait", false)
            fillMode = it.getBoolean("fill", false)
        }
        getSharedPreferences("h2v", MODE_PRIVATE).let {
            val d = intArrayOf(100, 100, 0, 100)
            for (i in 0 until 4) vols[i] = it.getInt("vol_$i", d[i]).coerceIn(0, 200)
            vols[2] = 0 // v3.6: Music completely disabled
            duck = it.getBoolean("duck", true)
            autoSfx = it.getBoolean("autosfx", true)
            if (it.getInt("vol_ver", 1) < 3) {
                vols[2] = 0
                vols[3] = 100
                it.edit().putInt("vol_2", 0).putInt("vol_3", 100).putInt("vol_ver", 3).apply()
            }
        }
        buildUi()
        loadEngineFields(curEng)
        refreshEngineUi()
        refreshFpsUi()
        refreshAspectUi()
        applyPlayerLayout()
        refreshLastButton()
        setStatus("Pehle HTML file chuno, Sample kholo ya Screenshot Studio shuru karo.", 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        exporter?.abort()
        try { web.destroy() } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (editorMode) setMode(false) else super.onBackPressed()
    }

    // ---------- UI ----------
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(cBg0, cBg1))
            setPadding(dp(14), dp(14), dp(14), 0)
        }

        // header
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("HTML2Video", 24f, cTxt, true))
        titles.addView(label("v3.6  \u2022  1080p  \u2022  Voice + All SFX (No Music)  \u2022  MP4", 11f, cSub))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(cCard, dpf(16), cStroke, dp(1))
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        tabPlayer = chip("\uD83C\uDFAC Player") { if (!exporting) setMode(false) }
        tabEditor = chip("\u270F Editor") { if (!exporting) setMode(true) }
        seg.addView(tabPlayer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        seg.addView(tabEditor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(seg)
        root.addView(header)

        // player box (WebView)
        playerBox = FrameLayout(this).apply {
            background = rounded(Color.BLACK, dpf(18))
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        web = WebView(this)
        setupWebView()
        playerBox.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playerBox.addOnLayoutChangeListener { _, l, tp, r, b, ol, ot, orr, ob ->
            if (exportScaled && (r - l != orr - ol || b - tp != ob - ot)) layoutWeb()
        }
        val pw = resources.displayMetrics.widthPixels - dp(28)
        val plp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pw * 9 / 16)
        plp.topMargin = dp(14)
        root.addView(playerBox, plp)

        // scrolling controls
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }

        // project card
        val cProject = card("PROJECT")
        fileLabel = label("Koi HTML load nahi hui", 14f, cTxt)
        fileLabel.setPadding(0, dp(10), 0, 0)
        cProject.addView(fileLabel)
        btnPick = button("\uD83D\uDCC2  HTML file", false) { pickHtml() }
        btnSample = button("\u2728  Sample", false) { loadAsset("sample_firebase.html", "Sample: Firebase tutorial") }
        cProject.addView(rowOf(btnPick, btnSample))
        btnTutorial = button("\uD83C\uDF93  Build tutorial", false) { loadAsset("build_tutorial_video.html", "Sample: GitHub Uploader build tutorial") }
        btnMovie = button("\uD83C\uDFAC  MovieBox promo", false) { loadAsset("moviebox_promo.html", "Sample: MovieBox promo (SFX + Voice)") }
        cProject.addView(rowOf(btnTutorial, btnMovie))
        btnUiUx = button("\uD83C\uDFA8  UI/UX Name Explorer (All SFX, No Music)", false) { loadAsset("uiux_showcase_video.html", "UI/UX Name Explorer Showcase") }
        cProject.addView(rowOf(btnUiUx))
        btnStudio = button("\uD83D\uDDBC  Screenshot Studio (screenshots se video)", true) {
            if (!exporting) {
                setMode(true)
                loadAsset("studio.html", "Screenshot Studio")
            }
        }
        cProject.addView(rowOf(btnStudio))
        btnLast = button("\u21BA  Pichla project kholo", false) { loadLast() }
        cProject.addView(rowOf(btnLast))
        col.addView(cProject)

        // voice card
        val cVoice = card("AWAAZ (VOICE-OVER)")
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, name) in engLabels.withIndex()) {
            val ch = chip(name) {
                if (!exporting) {
                    saveEngineFields(curEng)
                    curEng = engines[i]
                    loadEngineFields(curEng)
                    refreshEngineUi()
                }
            }
            engChips.add(ch)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            chipRow.addView(ch, lp)
        }
        val crlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        crlp.topMargin = dp(12)
        cVoice.addView(chipRow, crlp)
        engInfo = label("", 12f, cSub)
        engInfo.setPadding(0, dp(10), 0, 0)
        cVoice.addView(engInfo)
        etKey = input("API key")
        etKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val eye = label("\uD83D\uDC41", 20f, cSub).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(4), 0)
            isClickable = true
            setOnClickListener {
                keyVisible = !keyVisible
                etKey.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (keyVisible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                etKey.setSelection(etKey.text.length)
            }
        }
        keyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        keyRow.addView(etKey, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        keyRow.addView(eye, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val krlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        krlp.topMargin = dp(12)
        cVoice.addView(keyRow, krlp)
        etVoice = input("Voice / speaker")
        val vlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        vlp.topMargin = dp(10)
        cVoice.addView(etVoice, vlp)
        col.addView(cVoice)

        // my voice card (v3.2)
        myCard = card("MY VOICE  \u2022  APNI AWAAZ")
        myInfo = label("", 13f, cSub)
        myInfo.setPadding(0, dp(8), 0, 0)
        myCard.addView(myInfo)
        btnMyRec = button("\uD83C\uDF99  Record lines", true) { openRecorder() }
        btnMyImport = button("\uD83D\uDCE6  Voice pack import", false) { pickVoicePack() }
        myCard.addView(rowOf(btnMyRec, btnMyImport))
        btnMyExport = button("\uD83D\uDCE4  Script export", false) { exportMyScript() }
        btnMyClear = button("\uD83D\uDDD1  Clips delete", false) { clearMyVoice() }
        myCard.addView(rowOf(btnMyExport, btnMyClear))
        val cloudHead = label("\u2601  CLOUD AI VOICE (free Hugging Face Space)", 12f, cSub, true)
        cloudHead.setPadding(0, dp(14), 0, dp(4))
        myCard.addView(cloudHead)
        val cprefs = getSharedPreferences("h2v", MODE_PRIVATE)
        etCloudUrl = input("Server URL (https://....hf.space)")
        etCloudUrl.setText(cprefs.getString("cloud_url", ""))
        etCloudToken = input("API Token (Space secret)")
        etCloudToken.setText(cprefs.getString("cloud_token", ""))
        val ulp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ulp.topMargin = dp(6)
        myCard.addView(etCloudUrl, ulp)
        myCard.addView(etCloudToken, ulp)
        btnMySample = button("\uD83C\uDFA4  Sample chuno", false) { pickCloudSample() }
        btnMyTest = button("\uD83D\uDD0C  Server test", false) { testCloud() }
        myCard.addView(rowOf(btnMySample, btnMyTest))
        btnMyCloud = button("\u2601  Cloud se awaaz banao", true) { cloudGenerate() }
        myCard.addView(rowOf(btnMyCloud))
        val myHelp = label("Record: har line ka text dikhta hai, aap bolkar record karo. Import: zip ya audio files jinke naam line number se shuru hon (001.wav, 002.wav ...). Script export: AI/Colab ke liye script (JSON + TXT) Download/HTML2Video mein save hoti hai.", 12f, cSub)
        myHelp.setPadding(0, dp(10), 0, 0)
        myCard.addView(myHelp)
        myCard.visibility = View.GONE
        col.addView(myCard)

        // volume card (v3.6): master / voice / sfx (music completely removed)
        val cVol = card("VOLUME  \u2022  MASTER / VOICE / SOUND EFFECTS")
        val volNames = listOf("Master", "Voice-over", "Music (Off)", "Sound effects (SFX)")
        for (i in 0 until 4) {
            val nameTv = label(volNames[i], 13f, cTxt, true)
            val pctTv = label("${vols[i]}%", 13f, cSub, true)
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(nameTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(pctTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val hlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            hlp.topMargin = dp(12)
            cVol.addView(head, hlp)
            val sb = SeekBar(this).apply {
                max = 200
                progress = vols[i]
                progressTintList = ColorStateList.valueOf(cP2)
                thumbTintList = ColorStateList.valueOf(cP1)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        vols[i] = if (i == 2) 0 else p
                        pctTv.text = "${vols[i]}%"
                        if (fromUser) pushVolumes()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) { saveVolumes() }
                })
            }
            if (i == 2) {
                head.visibility = View.GONE
                sb.visibility = View.GONE
            }
            volBars.add(sb)
            cVol.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        btnDuck = button("", false) { duck = !duck; refreshDuckUi(); saveVolumes() }
        btnVolReset = button("\u21BA  Reset volume", false) { resetVolumes() }
        refreshDuckUi()
        cVol.addView(rowOf(btnDuck, btnVolReset))
        btnAuto = button("", false) { autoSfx = !autoSfx; refreshAutoUi(); saveVolumes() }
        refreshAutoUi()
        cVol.addView(rowOf(btnAuto))
        val volInfo = label("Background music poori tarah hata diya gaya hai. Ab har HTML mein Voice-over + saare Sound Effects (typing, popup, slide, tap, click, copy, lock, chime, screen in-out whoosh) Preview aur Export MP4 dono mein kaam karte hain.", 12f, cSub)
        volInfo.setPadding(0, dp(10), 0, 0)
        cVol.addView(volInfo)
        col.addView(cVol)

        // export card
        val cExport = card("EXPORT  \u2022  1920x1080")
        exportTitle = cExport.getChildAt(0) as TextView
        val aspectRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, nm) in listOf("16:9  Landscape", "9:16  Portrait").withIndex()) {
            val ch = chip(nm) {
                if (!exporting) {
                    portrait = (i == 1)
                    getSharedPreferences("h2v", MODE_PRIVATE).edit().putBoolean("portrait", portrait).apply()
                    refreshAspectUi()
                    applyPlayerLayout()
                }
            }
            aspectChips.add(ch)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            aspectRow.addView(ch, lp)
        }
        val arlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        arlp.topMargin = dp(12)
        cExport.addView(aspectRow, arlp)
        fitRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, nm) in listOf("Fit (blur background)", "Fill (crop)").withIndex()) {
            val ch = chip(nm) {
                if (!exporting) {
                    fillMode = (i == 1)
                    getSharedPreferences("h2v", MODE_PRIVATE).edit().putBoolean("fill", fillMode).apply()
                    refreshAspectUi()
                    applyPlayerLayout()
                }
            }
            fitChips.add(ch)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            fitRow.addView(ch, lp)
        }
        val frlp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        frlp2.topMargin = dp(8)
        cExport.addView(fitRow, frlp2)
        val fpsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, f) in listOf(30, 60).withIndex()) {
            val ch = chip("$f fps") {
                if (!exporting) {
                    fps = f
                    refreshFpsUi()
                    getSharedPreferences("h2v", MODE_PRIVATE).edit().putInt("fps", fps).apply()
                }
            }
            fpsChips.add(ch)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            fpsRow.addView(ch, lp)
        }
        val frlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        frlp.topMargin = dp(12)
        cExport.addView(fpsRow, frlp)
        btnPreview = button("\u25B6  Preview", false) { togglePreview() }
        btnExport = button("\u2B07  Export MP4", true) { startExport() }
        cExport.addView(rowOf(btnPreview, btnExport))
        btnCancel = button("Cancel", false) { cancel = true }
        cExport.addView(rowOf(btnCancel))
        col.addView(cExport)

        // status card
        val cStatus = card("STATUS")
        statusText = label("", 14f, cTxt)
        statusText.setPadding(0, dp(10), 0, dp(10))
        cStatus.addView(statusText)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            val ld = LayerDrawable(arrayOf<Drawable>(
                rounded(cCard2, dpf(6)),
                ClipDrawable(grad(cP1, cP2, dpf(6)), Gravity.START, ClipDrawable.HORIZONTAL)
            ))
            ld.setId(0, android.R.id.background)
            ld.setId(1, android.R.id.progress)
            progressDrawable = ld
        }
        cStatus.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        statsText = label("", 12f, cSub)
        statsText.setPadding(0, dp(8), 0, 0)
        cStatus.addView(statsText)
        btnOpen = button("\u25B6  Video kholo", true) { openVideo() }
        btnShare = button("\u2934  Share", false) { shareVideo() }
        resultRow = rowOf(btnOpen, btnShare)
        resultRow.visibility = View.GONE
        cStatus.addView(resultRow)
        btnTts = button("TTS settings kholo (Hindi voice download)", false) { openTtsSettings() }
        val ttsRow = rowOf(btnTts)
        ttsRow.visibility = View.GONE
        btnTts.tag = ttsRow
        cStatus.addView(ttsRow)
        col.addView(cStatus)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(col)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        enable(btnPreview, false)
        enable(btnExport, false)
        enable(btnCancel, false)
        styleTabs()
    }

    private fun styleTabs() {
        styleChip(tabPlayer, !editorMode)
        styleChip(tabEditor, editorMode)
        tabPlayer.background = ripple(if (!editorMode) grad(cP1, cP2, dpf(12)) else rounded(Color.TRANSPARENT, dpf(12)))
        tabEditor.background = ripple(if (editorMode) grad(cP1, cP2, dpf(12)) else rounded(Color.TRANSPARENT, dpf(12)))
    }

    private fun refreshEngineUi() {
        for ((i, ch) in engChips.withIndex()) styleChip(ch, engines[i] == curEng)
        engInfo.text = when (curEng) {
            "sarvam" -> "Natural Hindi voice. Free credits milte hain. Internet aur API key chahiye."
            "google" -> "Google Cloud Text-to-Speech. Internet aur API key chahiye."
            "phone" -> "Phone ki offline awaaz. Thodi robotic ho sakti hai."
            "myvoice" -> "Aapki apni awaaz: lines khud record karo, ya Colab/AI se bani voice pack import karo. Internet nahi chahiye."
            else -> "Bina awaaz ka video banega."
        }
        if (::myCard.isInitialized) {
            myCard.visibility = if (curEng == "myvoice") View.VISIBLE else View.GONE
            if (curEng == "myvoice") refreshMyStatus()
        }
    }

    private fun refreshFpsUi() {
        for ((i, ch) in fpsChips.withIndex()) styleChip(ch, listOf(30, 60)[i] == fps)
    }

    private fun refreshAspectUi() {
        for ((i, ch) in aspectChips.withIndex()) styleChip(ch, (i == 1) == portrait)
        for ((i, ch) in fitChips.withIndex()) styleChip(ch, (i == 1) == fillMode)
        fitRow.visibility = if (portrait) View.VISIBLE else View.GONE
        exportTitle.text = if (portrait) "EXPORT  \u2022  1080x1920" else "EXPORT  \u2022  1920x1080"
    }

    private fun setStatus(msg: String, kind: Int) {
        statusText.text = msg
        statusText.setTextColor(if (kind == 1) cOk else if (kind == 2) cErr else cTxt)
    }

    private fun ui(msg: String) = runOnUiThread { setStatus(msg, 0) }

    // ---------- WebView ----------
    private fun setupWebView() {
        web.setBackgroundColor(Color.BLACK)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        web.addJavascriptInterface(Bridge(), "Android")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = filePathCallback
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
                return try {
                    startActivityForResult(i, 12)
                    true
                } catch (e: Exception) {
                    fileCb?.onReceiveValue(null)
                    fileCb = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val r = request ?: return
                runOnUiThread {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        r.grant(r.resources)
                    } else {
                        pendingPerm = r
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 13)
                    }
                }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(H2V_JS) { _ ->
                    // v3.6: DOM adapter + Universal Canvas/DOM SFX engine (No Music)
                    view.evaluateJavascript(domAdapterJs) { dr ->
                        val d = (dr ?: "").trim('"')
                        domMode = (d == "dom" || d == "dom-exists")
                        view.evaluateJavascript(sfxEngineJs) { _ ->
                            layoutWeb()
                            view.evaluateJavascript("(typeof NH!=='undefined'&&typeof NH.run==='function')") { r ->
                                htmlReady = (r == "true")
                                refreshButtons()
                                if (htmlReady) {
                                    if (domMode) {
                                        setStatus("Scene-based HTML mili. Voice-over + All SFX (No Music) export mein judenge. Preview ya Export dabao.", 1)
                                    } else {
                                        setStatus("HTML taiyar hai (Voice + All SFX: typing, popup, slide, tap, whoosh • No Music). Preview ya Export dabao.", 1)
                                    }
                                    pushVolumes()
                                    refreshMyStatus()
                                } else if (d.startsWith("err")) {
                                    setStatus("DOM adapter error: $d", 2)
                                } else {
                                    setStatus("Is HTML mein NH API nahi mili. Master prompt ke template se HTML banao.", 2)
                                }
                                applyMode()
                                web.postDelayed({ if (!editorMode) applyMode() }, 900)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 14) {
            val go = pendingRecorder
            pendingRecorder = false
            if (go && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) openRecorder()
            else if (go) setStatus("Mic permission chahiye (record karne ke liye).", 2)
        }
        if (requestCode == 13) {
            val r = pendingPerm
            pendingPerm = null
            if (r != null) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) r.grant(r.resources) else r.deny()
            }
        }
    }

    private fun refreshButtons() {
        enable(btnExport, htmlReady && !exporting)
        enable(btnPreview, htmlReady && !exporting)
        enable(btnCancel, exporting)
        enable(btnPick, !exporting)
        enable(btnSample, !exporting)
        enable(btnStudio, !exporting)
        enable(btnLast, !exporting)
        enable(btnTutorial, !exporting)
        enable(btnMovie, !exporting)
        if (::btnUiUx.isInitialized) enable(btnUiUx, !exporting)
        enable(btnMyRec, !exporting)
        enable(btnMyImport, !exporting)
        enable(btnMyExport, !exporting)
        enable(btnMyClear, !exporting)
        enable(btnMySample, !exporting)
        enable(btnMyTest, !exporting)
        enable(btnMyCloud, !exporting)
        for (b in volBars) enable(b, !exporting)
        enable(btnDuck, !exporting)
        enable(btnAuto, !exporting)
        enable(btnVolReset, !exporting)
        for (c in engChips) enable(c, !exporting)
        for (c in fpsChips) enable(c, !exporting)
        for (c in aspectChips) enable(c, !exporting)
        for (c in fitChips) enable(c, !exporting)
        enable(etKey, !exporting)
        enable(etVoice, !exporting)
        enable(tabPlayer, !exporting)
        enable(tabEditor, !exporting)
    }

    // ---------- Player / Editor mode ----------
    private fun setMode(editor: Boolean) {
        editorMode = editor
        val lp = playerBox.layoutParams as LinearLayout.LayoutParams
        if (editor) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.NO_GRAVITY
            lp.height = 0
            lp.weight = 1f
            scroll.visibility = View.GONE
        } else {
            val fullW = resources.displayMetrics.widthPixels - dp(28)
            if (portrait) {
                // 9:16 screen: height screen ke ~58% tak, width uske hisaab se
                val maxH = resources.displayMetrics.heightPixels * 58 / 100
                var h = fullW * 16 / 9
                if (h > maxH) h = maxH
                lp.width = h * 9 / 16
                lp.height = h
                lp.gravity = Gravity.CENTER_HORIZONTAL
            } else {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.gravity = Gravity.NO_GRAVITY
                lp.height = fullW * 9 / 16
            }
            lp.weight = 0f
            scroll.visibility = View.VISIBLE
        }
        playerBox.layoutParams = lp
        styleTabs()
        applyMode()
    }

    /** 16:9 / 9:16 badalne par player screen ka shape dobara set karta hai. */
    private fun applyPlayerLayout() {
        setMode(editorMode)
    }

    private fun applyMode() {
        val js = if (editorMode || domMode) "(window.__h2v?__h2v.off():'nohelper')"
        else "(window.__h2v?__h2v.on($portrait,$fillMode):'nohelper')"
        web.evaluateJavascript(js, null)
    }

    private fun togglePreview() {
        if (!htmlReady || exporting) return
        if (editorMode) setMode(false)
        pushVolumes()
        if (domMode) {
            web.evaluateJavascript("(window.NH&&NH.preview?NH.preview():'x')") { r ->
                setStatus(if (r == "\"playing\"") "Preview chal raha hai (SFX only, No Music). Voice-over export mein judegi. Dobara dabao to ruk jayega." else "Preview ruk gaya.", 0)
            }
            return
        }
        web.evaluateJavascript("(function(){var b=document.getElementById('pv');if(b){b.click();return 1}return 0})()") { r ->
            if (r == "0") setStatus("Is HTML mein preview button nahi mila.", 2)
        }
    }

    // ---------- HTML load ----------
    private fun pickHtml() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, 11)
    }

    private fun nameOf(u: Uri): String {
        var n = "file.html"
        try {
            contentResolver.query(u, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) n = c.getString(idx) ?: n
                }
            }
        } catch (_: Exception) {}
        return n
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11 && resultCode == RESULT_OK) {
            val u = data?.data ?: return
            try {
                val html = contentResolver.openInputStream(u)!!.use { it.readBytes() }.toString(Charsets.UTF_8)
                loadHtml(html, nameOf(u))
            } catch (e: Exception) {
                setStatus("File padh nahi paya: ${e.message}", 2)
            }
        } else if (requestCode == 22 && resultCode == RESULT_OK && data?.data != null) {
            val u = data.data!!
            Thread {
                try {
                    var size = 0L
                    contentResolver.openInputStream(u)!!.use { ins ->
                        FileOutputStream(File(filesDir, "cloud_sample.bin")).use { o ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                size += n
                            }
                        }
                    }
                    getSharedPreferences("h2v", MODE_PRIVATE).edit().putString("cloud_sample_name", nameOf(u)).remove("cloud_voice_id").apply()
                    runOnUiThread {
                        setStatus("Sample set: ${nameOf(u)} (" + String.format(Locale.US, "%.1f", size / 1048576.0) + " MB)." +
                            (if (size < 200_000) " Ye chhota lagta hai, 1-3 minute ka saaf sample behtar hai." else ""), if (size < 200_000) 2 else 1)
                    }
                } catch (e: Exception) {
                    runOnUiThread { setStatus("Sample copy fail: ${e.message}", 2) }
                }
            }.start()
        } else if (requestCode == 21 && resultCode == RESULT_OK && data != null) {
            val uris = ArrayList<Uri>()
            val cd = data.clipData
            if (cd != null) {
                for (k in 0 until cd.itemCount) uris.add(cd.getItemAt(k).uri)
            } else if (data.data != null) {
                uris.add(data.data!!)
            }
            importVoiceFiles(uris)
        } else if (requestCode == 12) {
            val res = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            fileCb?.onReceiveValue(res)
            fileCb = null
        }
    }

    private fun loadAsset(name: String, title: String) {
        try {
            val html = assets.open(name).bufferedReader().use { it.readText() }
            loadHtml(html, title)
        } catch (e: Exception) {
            setStatus("Asset nahi khula: ${e.message}", 2)
        }
    }

    private fun lastFile() = File(filesDir, "last.html")
    private fun lastNameFile() = File(filesDir, "last.txt")

    private fun refreshLastButton() {
        val has = lastFile().exists()
        (btnLast.parent as View).visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun loadLast() {
        try {
            val html = lastFile().readText(Charsets.UTF_8)
            val n = if (lastNameFile().exists()) lastNameFile().readText() else "Pichla project"
            loadHtml(html, n, false)
        } catch (e: Exception) {
            setStatus("Pichla project nahi khula: ${e.message}", 2)
        }
    }

    private fun loadHtml(html: String, name: String, remember: Boolean = true) {
        if (exporting) return
        htmlReady = false
        domMode = false
        exportScaled = false
        layoutWeb()
        refreshButtons()
        resultRow.visibility = View.GONE
        fileLabel.text = name
        setStatus("HTML load ho raha hai...", 0)
        bar.progress = 0
        statsText.text = ""
        if (remember) {
            Thread {
                try {
                    lastFile().writeText(html, Charsets.UTF_8)
                    lastNameFile().writeText(name, Charsets.UTF_8)
                    runOnUiThread { refreshLastButton() }
                } catch (_: Exception) {}
            }.start()
        }
        web.loadDataWithBaseURL("https://html2video.local/", html, "text/html", "UTF-8", null)
    }

    // ---------- voice fields ----------
    private fun defaultVoice(eng: String): String = when (eng) {
        "sarvam" -> "ritu"
        "google" -> "hi-IN-Neural2-A"
        else -> ""
    }

    private fun saveEngineFields(eng: String) {
        if (eng != "sarvam" && eng != "google") return
        getSharedPreferences("h2v", MODE_PRIVATE).edit()
            .putString("key_$eng", etKey.text.toString().trim())
            .putString("voice_$eng", etVoice.text.toString().trim())
            .apply()
    }

    private fun loadEngineFields(eng: String) {
        val p = getSharedPreferences("h2v", MODE_PRIVATE)
        fps = p.getInt("fps", 60)
        val online = eng == "sarvam" || eng == "google"
        val vis = if (online) View.VISIBLE else View.GONE
        keyRow.visibility = vis
        etVoice.visibility = vis
        etKey.hint = if (eng == "sarvam") "Sarvam API key (dashboard.sarvam.ai)" else "Google Cloud API key"
        etVoice.hint = if (eng == "sarvam") "Speaker (ritu, shubh, priya, kavya ...)" else "Voice name (hi-IN-Neural2-A)"
        etKey.setText(p.getString("key_$eng", "") ?: "")
        etVoice.setText(p.getString("voice_$eng", defaultVoice(eng)) ?: defaultVoice(eng))
    }

    // ---------- Export ----------
    private fun setBusy(b: Boolean) {
        exporting = b
        refreshButtons()
    }

    private fun startExport() {
        if (!htmlReady || exporting) return
        if (editorMode) setMode(false)
        cancel = false
        blankFrames = 0
        contentSeen = false
        audioNote = ""
        savedUri = null
        resultRow.visibility = View.GONE
        (btnTts.tag as View).visibility = View.GONE
        bar.progress = 0
        statsText.text = ""
        saveEngineFields(curEng)
        val mode = curEng
        val key = etKey.text.toString().trim()
        val voiceName = etVoice.text.toString().trim().ifEmpty { defaultVoice(mode) }
        val useFps = fps
        val usePortrait = portrait
        val useFill = fillMode
        setBusy(true)
        Thread { runExport(mode, key, voiceName, useFps, usePortrait, useFill) }.start()
    }

    private fun evalSync(js: String, timeoutS: Long = 60): String? {
        val l = CountDownLatch(1)
        var res: String? = null
        runOnUiThread {
            web.evaluateJavascript(js) { r ->
                res = r
                l.countDown()
            }
        }
        l.await(timeoutS, TimeUnit.SECONDS)
        return res
    }

    private fun unquote(s: String?): String? {
        if (s == null || s == "null") return null
        return try {
            JSONTokener(s).nextValue() as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun runExport(mode: String, key: String, voiceName: String, useFps: Int, usePortrait: Boolean = false, useFill: Boolean = false) {
        var tts: TtsRenderer? = null
        try {
            ui("Script padh raha hai...")
            val narr = unquote(evalSync("NH.narration()")) ?: throw Exception("NH.narration() nahi mila")
            val steps = JSONArray(narr)
            val lang = unquote(evalSync("NH.lang")) ?: "hi-IN"
            val clips = HashMap<String, ShortArray>()
            val durs = JSONArray()

            if (mode != "off") {
                var online: ((String) -> ShortArray)? = null
                if (mode == "sarvam") {
                    if (key.isBlank()) throw Exception("Sarvam API key daalo (dashboard.sarvam.ai se free). Ya 'Phone' awaaz chuno.")
                    val sv = SarvamTts(key, lang, voiceName, 1.0)
                    online = { txt -> sv.synth(txt) }
                } else if (mode == "google") {
                    if (key.isBlank()) throw Exception("Google Cloud API key daalo (ya doosra voice option chuno).")
                    val g = CloudTts(key, lang, voiceName, 0.95)
                    online = { txt -> g.synth(txt) }
                } else if (mode == "myvoice") {
                    val missing = ArrayList<Int>()
                    var cnt = 0
                    for (i in 0 until steps.length()) {
                        for (k in listOf("a", "b")) {
                            val tx = steps.getJSONObject(i).optString(k)
                            if (tx.isNotBlank()) {
                                cnt++
                                if (!MyVoice.has(applicationContext, tx)) missing.add(cnt)
                            }
                        }
                    }
                    if (missing.isNotEmpty()) {
                        throw Exception("My Voice: ${missing.size} line(s) ki awaaz nahi mili (line ${missing.take(6).joinToString(", ")}${if (missing.size > 6) " ..." else ""}). 'Record lines' ya 'Voice pack import' se poori karo.")
                    }
                } else {
                    val t = TtsRenderer(applicationContext)
                    tts = t
                    ui("Hindi awaaz engine shuru ho raha hai...")
                    t.init(lang, 0.95f)
                }
                for (i in 0 until steps.length()) {
                    val o = steps.getJSONObject(i)
                    val d = JSONObject()
                    for (k in listOf("a", "b")) {
                        if (cancel) throw Exception("Cancel ho gaya")
                        val text = o.optString(k)
                        var sec = 0.0
                        if (text.isNotBlank()) {
                            ui("Awaaz ban rahi hai: step ${i + 1}/${steps.length()} ($k)")
                            val pcm = if (mode == "myvoice") (MyVoice.load(applicationContext, text)
                                ?: throw Exception("My Voice clip nahi mili: step ${i + 1}"))
                            else if (online != null) online(text)
                            else tts!!.synth("$i$k", text)
                                ?: throw TtsMissingException("Step ${i + 1} ki awaaz nahi bani. Hindi TTS voice check karo.")
                            clips["$i$k"] = pcm
                            sec = pcm.size / AudioUtils.SR.toDouble()
                        }
                        d.put(k, sec)
                    }
                    durs.put(d)
                }
            } else {
                for (i in 0 until steps.length()) durs.put(JSONObject().put("a", 0).put("b", 0))
            }

            ui("Timeline set ho rahi hai...")
            val planJson = unquote(evalSync("NH.setDurations(" + JSONObject.quote(durs.toString()) + ")"))
                ?: throw Exception("NH.setDurations() fail hua")
            val plan = JSONObject(planJson)
            val total = plan.getDouble("total")
            // v3.6: Extract all SFX cues (typing, popup, slide, tap, click, copy, lock, chime, whoosh) & synthesize without Music
            var sfxPcm: Stem? = null
            val evAll = plan.getJSONArray("events")
            val cuesJson = unquote(evalSync("(window.__h2vExtractSfxCues?window.__h2vExtractSfxCues():(window.__h2vExtractDomCues?window.__h2vExtractDomCues():'[]'))", 90))
            val cuesArr = try { JSONArray(cuesJson ?: "[]") } catch (_: Exception) { JSONArray() }

            if (plan.optBoolean("stems", false)) {
                ui("Sound effects ban rahe hain (No Music)...")
                val st = renderStems()
                sfxPcm = st.second
                val err = audioErr
                val sfxPeak = synchronized(audioInfo) { audioInfo["sfx"] ?: 0.0 }
                val sfxNodes = synchronized(audioInfo) { (audioInfo["sfx_n"] ?: 0.0).toInt() }
                val sfxOk = sfxPcm != null && sfxPeak >= 0.02
                var note = "SFX " + (if (sfxOk) "\u2713 ($sfxNodes sounds)" else "\u2717")
                if (!sfxOk && autoSfx) {
                    sfxPcm = Stem(AutoSfx.sfxFromCues(total, cuesArr, evAll, false), AutoSfx.SFX_RATE)
                    note = "SFX \u2713 (${cuesArr.length()} sounds)"
                }
                if (err != null) note += " [note: $err]"
                audioNote = "$note \u2022 No Music"
            } else if (autoSfx) {
                ui("Sound effects (typing, popup, slide, tap, whoosh...) ban rahe hain...")
                sfxPcm = Stem(AutoSfx.sfxFromCues(total, cuesArr, evAll, true), AutoSfx.SFX_RATE)
                audioNote = "SFX \u2713 (${cuesArr.length()} sounds: typing, popup, slide, tap, whoosh) \u2022 No Music"
            }
            val audio = if (clips.isNotEmpty() || sfxPcm != null) {
                AudioUtils.mixStems(
                    total, plan.getJSONArray("events"), clips,
                    null, 12000, sfxPcm?.data, sfxPcm?.rate ?: 48000,
                    vols[0] / 100f, vols[1] / 100f, 0f, vols[3] / 100f, duck
                )
            } else null

            val out = File(cacheDir, "export_${System.currentTimeMillis()}.mp4")
            val e = VideoExporter(out, useFps, audio, usePortrait, useFill)
            ui("Encoder taiyar ho raha hai...")
            e.prepare()
            exporter = e
            if (domMode) {
                // WebView ko asli 1920x1080 px par laate hain taaki frame poore 1080p mein capture ho
                runOnUiThread { exportScaled = true; layoutWeb() }
                var waited = 0
                while ((web.width != 1920 || web.height != 1080) && waited < 6000) { Thread.sleep(100); waited += 100 }
                if (web.width != 1920 || web.height != 1080) {
                    throw Exception("WebView 1920x1080 size nahi le paya (${web.width}x${web.height}).")
                }
                Thread.sleep(800)   // page ko resize ke baad settle hone do
            }
            startMs = SystemClock.elapsedRealtime()
            ui("Video ban raha hai (" + (if (usePortrait) "9:16 1080x1920" else "1080p") + ", $useFps fps)...")
            runOnUiThread { web.evaluateJavascript("NH.run($useFps)", null) }
        } catch (ex: Exception) {
            fail(ex.message ?: ex.toString(), ex is TtsMissingException)
        } finally {
            tts?.shutdown()
        }
    }

    private fun fail(msg: String, showTts: Boolean = false) {
        try { exporter?.abort() } catch (_: Exception) {}
        exporter = null
        runOnUiThread {
            if (exportScaled) { exportScaled = false; layoutWeb() }
            if (domMode) web.evaluateJavascript("(window.NH&&NH.endExport?NH.endExport():0)", null)
            setStatus("Error: $msg", 2)
            setBusy(false)
            if (showTts) (btnTts.tag as View).visibility = View.VISIBLE
        }
    }

    // ---------- v3.2: My Voice ----------
    /** Script ki saari lines (a, b ke non-blank texts) ek list mein. Background thread se bulao. */
    private fun fetchLines(): List<String> {
        val narr = unquote(evalSync("NH.narration()")) ?: return emptyList()
        val steps = JSONArray(narr)
        val out = ArrayList<String>()
        for (i in 0 until steps.length()) {
            val o = steps.getJSONObject(i)
            for (k in listOf("a", "b")) {
                val tx = o.optString(k)
                if (tx.isNotBlank()) out.add(tx)
            }
        }
        return out
    }

    private fun refreshMyStatus() {
        if (!::myInfo.isInitialized || curEng != "myvoice") return
        if (!htmlReady) {
            myInfo.text = "Pehle HTML load karo. Lines us HTML ke script se aati hain."
            return
        }
        Thread {
            val lines = try { fetchLines() } catch (e: Exception) { emptyList() }
            myLines = lines
            val ready = MyVoice.countReady(applicationContext, lines)
            runOnUiThread {
                myInfo.text = "Is video ki ${lines.size} lines mein se $ready ready hain." +
                    (if (ready < lines.size) " Baaki record ya import karo." else " Export ke liye taiyar!")
            }
        }.start()
    }

    private fun openRecorder() {
        if (!htmlReady) {
            setStatus("Pehle HTML load karo.", 2)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingRecorder = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 14)
            return
        }
        setStatus("Lines load ho rahi hain...", 0)
        Thread {
            val lines = try { fetchLines() } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (lines.isEmpty()) {
                    setStatus("Is HTML se lines nahi mili.", 2)
                } else {
                    myLines = lines
                    setStatus("Recorder khula hai.", 0)
                    showRecorder(lines)
                }
            }
        }.start()
    }

    private fun showRecorder(lines: List<String>) {
        val ctx = applicationContext
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        var idx = lines.indexOfFirst { !MyVoice.has(ctx, it) }
        if (idx < 0) idx = 0
        val recFlag = AtomicBoolean(false)
        val chunks = ArrayList<ShortArray>()
        var rec: AudioRecord? = null
        var recThread: Thread? = null
        var recRate = 48000
        var track: AudioTrack? = null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg0)
            setPadding(dp(18), dp(36), dp(18), dp(18))
        }
        val head = label("", 14f, cSub, true)
        root.addView(head)
        val lineTv = label("", 22f, cTxt, true)
        lineTv.setPadding(0, dp(14), 0, dp(14))
        val sv = ScrollView(this)
        sv.addView(lineTv)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val stat = label("", 14f, cSub, true)
        stat.setPadding(0, dp(8), 0, dp(4))
        root.addView(stat)

        fun stopPlay() {
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            track = null
        }

        fun refresh() {
            val ready = MyVoice.countReady(ctx, lines)
            head.text = "Line ${idx + 1} / ${lines.size}   \u2022   ready: $ready / ${lines.size}"
            lineTv.text = lines[idx]
            val has = MyVoice.has(ctx, lines[idx])
            val sec = if (has) (MyVoice.load(ctx, lines[idx])?.size ?: 0) / AudioUtils.SR.toDouble() else 0.0
            if (recFlag.get()) {
                stat.text = "\u25CF Recording... bolo, phir Stop dabao"
                stat.setTextColor(cErr)
            } else if (has) {
                stat.text = "\u2713 Record ho chuka (" + String.format(Locale.US, "%.1f", sec) + " sec)"
                stat.setTextColor(cOk)
            } else {
                stat.text = "Abhi record nahi hua"
                stat.setTextColor(cSub)
            }
        }

        fun stopRec(save: Boolean) {
            if (!recFlag.get()) return
            recFlag.set(false)
            try { recThread?.join(1500) } catch (_: Exception) {}
            try { rec?.stop() } catch (_: Exception) {}
            try { rec?.release() } catch (_: Exception) {}
            rec = null
            if (!save) {
                synchronized(chunks) { chunks.clear() }
                return
            }
            val all: ShortArray
            synchronized(chunks) {
                var total = 0
                for (c in chunks) total += c.size
                all = ShortArray(total)
                var p = 0
                for (c in chunks) {
                    System.arraycopy(c, 0, all, p, c.size)
                    p += c.size
                }
                chunks.clear()
            }
            if (all.size < recRate / 4) {
                Toast.makeText(this, "Recording bahut chhoti thi", Toast.LENGTH_SHORT).show()
                return
            }
            val pcm = MyVoice.normalize(AudioUtils.trimSilence(AudioUtils.resample(all, recRate, AudioUtils.SR)))
            MyVoice.save(ctx, lines[idx], pcm)
        }

        fun startRec() {
            stopPlay()
            var ar: AudioRecord? = null
            for (r in intArrayOf(48000, 44100, 16000)) {
                val min = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (min <= 0) continue
                val a = try {
                    AudioRecord(MediaRecorder.AudioSource.MIC, r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
                } catch (e: Exception) { null }
                if (a != null && a.state == AudioRecord.STATE_INITIALIZED) {
                    ar = a
                    recRate = r
                    break
                }
                a?.release()
            }
            if (ar == null) {
                Toast.makeText(this, "Mic shuru nahi ho paya", Toast.LENGTH_LONG).show()
                return
            }
            rec = ar
            synchronized(chunks) { chunks.clear() }
            recFlag.set(true)
            ar.startRecording()
            val rr = ar
            val t = Thread {
                val buf = ShortArray(4096)
                while (recFlag.get()) {
                    val n = rr.read(buf, 0, buf.size)
                    if (n > 0) synchronized(chunks) { chunks.add(buf.copyOf(n)) } else if (n < 0) break
                }
            }
            recThread = t
            t.start()
        }

        fun play() {
            stopPlay()
            val pcm = MyVoice.load(ctx, lines[idx]) ?: return
            try {
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(AudioUtils.SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                t.write(pcm, 0, pcm.size)
                t.play()
                track = t
            } catch (e: Exception) {
                Toast.makeText(this, "Play nahi ho paya: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        lateinit var btnRecord: TextView
        fun go(newIdx: Int) {
            stopRec(true)
            stopPlay()
            idx = newIdx.coerceIn(0, lines.size - 1)
            btnRecord.text = "\u25CF  Record"
            refresh()
        }

        btnRecord = button("\u25CF  Record", true) {
            if (recFlag.get()) {
                stopRec(true)
                btnRecord.text = "\u25CF  Record"
            } else {
                startRec()
                if (recFlag.get()) btnRecord.text = "\u25A0  Stop"
            }
            refresh()
        }
        val btnPlay = button("\u25B6  Play", false) { if (!recFlag.get()) play() }
        val btnDel = button("\uD83D\uDDD1  Delete", false) {
            if (!recFlag.get()) {
                MyVoice.delete(ctx, lines[idx])
                refresh()
            }
        }
        root.addView(rowOf(btnRecord, btnPlay, btnDel))
        val btnPrev = button("\u25C0  Pichhli", false) { go(idx - 1) }
        val btnNext = button("Agli  \u25B6", false) { go(idx + 1) }
        root.addView(rowOf(btnPrev, btnNext))
        val btnMiss = button("\u23ED  Agli baaki line", false) {
            var j = -1
            for (k in 1..lines.size) {
                val c = (idx + k) % lines.size
                if (!MyVoice.has(ctx, lines[c])) { j = c; break }
            }
            if (j >= 0) go(j) else Toast.makeText(this, "Saari lines ready hain!", Toast.LENGTH_SHORT).show()
        }
        val btnClose = button("\u2713  Done", true) { dlg.dismiss() }
        root.addView(rowOf(btnMiss, btnClose))

        dlg.setContentView(root)
        dlg.setOnDismissListener {
            stopRec(true)
            stopPlay()
            refreshMyStatus()
        }
        refresh()
        dlg.show()
    }

    private fun pickVoicePack() {
        if (!htmlReady) {
            setStatus("Pehle HTML load karo.", 2)
            return
        }
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "audio/*", "application/octet-stream"))
        }
        try {
            startActivityForResult(i, 21)
        } catch (e: Exception) {
            setStatus("File picker nahi khula: ${e.message}", 2)
        }
    }

    /** Zip ya audio files import: file ke naam ka pehla number = line number (001.wav -> pehli line). */
    private fun importVoiceFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        setStatus("Voice pack import ho raha hai...", 0)
        Thread {
            var imported = 0
            var skipped = 0
            val failed = ArrayList<String>()
            try {
                val lines = fetchLines()
                if (lines.isEmpty()) throw Exception("Is HTML se lines nahi mili")
                fun handle(fname: String, bytes: ByteArray) {
                    val base = fname.substringAfterLast('/')
                    if (base.startsWith(".")) return
                    val n = Regex("(\\d+)").find(base)?.value?.toIntOrNull()
                    if (n == null || n < 1 || n > lines.size) {
                        skipped++
                        return
                    }
                    val ext = base.substringAfterLast('.', "wav").lowercase(Locale.US).ifEmpty { "wav" }
                    val tmp = File(cacheDir, "imp_${System.nanoTime()}.$ext")
                    try {
                        tmp.writeBytes(bytes)
                        val pcm = MyVoice.decodeToPcm(tmp)
                        if (pcm == null || pcm.size < AudioUtils.SR / 10) {
                            failed.add(base)
                        } else {
                            MyVoice.save(applicationContext, lines[n - 1], pcm)
                            imported++
                            if (imported % 5 == 0) ui("Import ho raha hai: $imported lines...")
                        }
                    } finally {
                        tmp.delete()
                    }
                }
                for (u in uris) {
                    val name = nameOf(u)
                    if (name.lowercase(Locale.US).endsWith(".zip")) {
                        ZipInputStream(contentResolver.openInputStream(u)!!).use { zis ->
                            while (true) {
                                val e = zis.nextEntry ?: break
                                if (e.isDirectory) continue
                                handle(e.name, zis.readBytes())
                            }
                        }
                    } else {
                        handle(name, contentResolver.openInputStream(u)!!.use { it.readBytes() })
                    }
                }
                runOnUiThread {
                    val msg = "Voice pack import: $imported line(s) ho gayi" +
                        (if (skipped > 0) ", $skipped skip (naam mein line number nahi)" else "") +
                        (if (failed.isNotEmpty()) ", ${failed.size} decode fail (${failed.take(3).joinToString(", ")})" else "") + "."
                    setStatus(msg, if (imported > 0) 1 else 2)
                    refreshMyStatus()
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Import fail: ${e.message}", 2) }
            }
        }.start()
    }

    /** AI/Colab ke liye script: JSON + TXT, Download/HTML2Video mein. */
    private fun exportMyScript() {
        if (!htmlReady) {
            setStatus("Pehle HTML load karo.", 2)
            return
        }
        setStatus("Script export ho rahi hai...", 0)
        Thread {
            try {
                val lines = fetchLines()
                if (lines.isEmpty()) throw Exception("Is HTML se lines nahi mili")
                val arr = JSONArray()
                val sb = StringBuilder()
                for ((i, tx) in lines.withIndex()) {
                    arr.put(JSONObject().put("n", i + 1).put("text", tx))
                    sb.append(String.format(Locale.US, "%03d. %s\n\n", i + 1, tx))
                }
                val p1 = saveDownload("my_voice_script.json", "application/json", arr.toString(2).toByteArray(Charsets.UTF_8))
                val p2 = saveDownload("my_voice_script.txt", "text/plain", sb.toString().toByteArray(Charsets.UTF_8))
                runOnUiThread { setStatus("Script save ho gayi (${lines.size} lines): $p1 aur $p2", 1) }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Script export fail: ${e.message}", 2) }
            }
        }.start()
    }

    // ---------- v3.3: cloud AI voice ----------
    private fun saveCloudPrefs() {
        getSharedPreferences("h2v", MODE_PRIVATE).edit()
            .putString("cloud_url", etCloudUrl.text.toString().trim())
            .putString("cloud_token", etCloudToken.text.toString().trim()).apply()
    }

    private fun cloudClient(): CloudVoice? {
        val u = etCloudUrl.text.toString().trim()
        if (!u.startsWith("http")) {
            setStatus("Server URL daalo (https://...hf.space).", 2)
            return null
        }
        saveCloudPrefs()
        return CloudVoice(u, etCloudToken.text.toString().trim())
    }

    private fun pickCloudSample() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        try {
            startActivityForResult(i, 22)
        } catch (e: Exception) {
            setStatus("File picker nahi khula: ${e.message}", 2)
        }
    }

    private fun testCloud() {
        val cv = cloudClient() ?: return
        setStatus("Server se connect ho raha hai (soya hua Space jagne mein 1-2 minute lagte hain)...", 0)
        Thread {
            try {
                val r = cv.health()
                runOnUiThread { setStatus("Server OK \u2713  $r", 1) }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Server test fail: ${e.message}", 2) }
            }
        }.start()
    }

    /** Baaki (record/import nahi hui) lines ko cloud se aapki awaaz mein banwata hai. Dobara dabao to rok deta hai. */
    private fun cloudGenerate() {
        if (cloudRunning) {
            cloudStop = true
            setStatus("Rok raha hoon...", 0)
            return
        }
        if (!htmlReady) {
            setStatus("Pehle HTML load karo.", 2)
            return
        }
        val sample = File(filesDir, "cloud_sample.bin")
        if (!sample.exists()) {
            setStatus("Pehle 'Sample chuno' se apni awaaz ka 1-3 minute ka sample do.", 2)
            return
        }
        val cv = cloudClient() ?: return
        cloudRunning = true
        cloudStop = false
        btnMyCloud.text = "\u25A0  Stop cloud"
        val ctx = applicationContext
        Thread {
            var done = 0
            var skipped = 0
            var failed = 0
            var consec = 0
            var total = 0
            var error: String? = null
            try {
                val lines = fetchLines()
                total = lines.size
                if (lines.isEmpty()) throw Exception("Is HTML se lines nahi mili")
                ui("Server jag raha hai (pehli baar 1-2 minute)...")
                var woke = false
                var wakeErr = ""
                for (a in 1..6) {
                    if (cloudStop) break
                    try {
                        cv.health()
                        woke = true
                        break
                    } catch (e: CloudVoice.HttpFail) {
                        wakeErr = e.message ?: ""
                        if (e.code == 401) throw e
                        Thread.sleep(10_000)
                    } catch (e: Exception) {
                        wakeErr = e.message ?: ""
                        Thread.sleep(10_000)
                    }
                }
                if (!woke && !cloudStop) throw Exception("Server nahi mila: $wakeErr")
                val prefs = getSharedPreferences("h2v", MODE_PRIVATE)
                var vid = prefs.getString("cloud_voice_id", "") ?: ""
                fun reg(): String {
                    ui("Aapki awaaz server par register ho rahi hai...")
                    val id = cv.register(sample.readBytes(), prefs.getString("cloud_sample_name", "sample") ?: "sample")
                    prefs.edit().putString("cloud_voice_id", id).apply()
                    return id
                }
                if (vid.isEmpty() && !cloudStop) vid = reg()
                for ((i, tx) in lines.withIndex()) {
                    if (cloudStop) break
                    if (MyVoice.has(ctx, tx)) {
                        skipped++
                        continue
                    }
                    ui("Cloud: line ${i + 1} / ${lines.size}  (ban gayi: $done)")
                    var ok = false
                    var attempt = 0
                    var lastErr = ""
                    while (!ok && attempt < 3 && !cloudStop) {
                        attempt++
                        try {
                            val wav = try {
                                cv.synth(vid, tx)
                            } catch (e: CloudVoice.UnknownVoice) {
                                vid = reg()
                                cv.synth(vid, tx)
                            }
                            val tmp = File(cacheDir, "cloud_${System.nanoTime()}.wav")
                            try {
                                tmp.writeBytes(wav)
                                val pcm = MyVoice.decodeToPcm(tmp)
                                if (pcm == null || pcm.size < AudioUtils.SR / 10) throw java.io.IOException("audio decode nahi hua")
                                MyVoice.save(ctx, tx, pcm)
                                done++
                                ok = true
                            } finally {
                                tmp.delete()
                            }
                        } catch (e: CloudVoice.HttpFail) {
                            lastErr = e.message ?: ""
                            if (e.code == 401 || e.code == 400) break
                            Thread.sleep(attempt * 4000L)
                        } catch (e: Exception) {
                            lastErr = e.message ?: e.javaClass.simpleName
                            Thread.sleep(attempt * 4000L)
                        }
                    }
                    if (ok) {
                        consec = 0
                    } else if (!cloudStop) {
                        failed++
                        consec++
                        error = "line ${i + 1}: $lastErr"
                        if (consec >= 3) throw Exception("Lagatar fail ($lastErr). Server logs check karo.")
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                cloudRunning = false
                val stopped = cloudStop
                runOnUiThread {
                    btnMyCloud.text = "\u2601  Cloud se awaaz banao"
                    val msg = "Cloud: $done line(s) bani" + (if (skipped > 0) ", $skipped pehle se ready" else "") +
                        (if (failed > 0) ", $failed fail" else "") + (if (stopped) " (roka gaya)" else "") +
                        (if (error != null) ". Error: $error" else ".")
                    setStatus(msg, if (error != null) 2 else 1)
                    refreshMyStatus()
                }
            }
        }.start()
    }

    private fun clearMyVoice() {
        AlertDialog.Builder(this)
            .setMessage("Saari My Voice recordings/clips delete karni hain? Ye wapas nahi aayengi.")
            .setPositiveButton("Delete") { _, _ ->
                val n = MyVoice.clearAll(applicationContext)
                setStatus("$n clip(s) delete ho gayi.", 1)
                refreshMyStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- v3: volume ----------
    private fun volStr(i: Int): String = String.format(Locale.US, "%.2f", vols[i] / 100.0)

    /** Slider ki volume page ko bhejta hai (preview mein music + SFX par turant asar). */
    private fun pushVolumes() {
        if (!::web.isInitialized || !htmlReady) return
        web.evaluateJavascript("(window.NH&&NH.setVolumes?NH.setVolumes(${volStr(0)},${volStr(1)},0.0,${volStr(3)}):0)", null)
    }

    private fun saveVolumes() {
        val ed = getSharedPreferences("h2v", MODE_PRIVATE).edit()
        for (i in 0 until 4) ed.putInt("vol_$i", vols[i])
        ed.putBoolean("duck", duck)
        ed.putBoolean("autosfx", autoSfx)
        ed.apply()
    }

    private fun refreshAutoUi() {
        btnAuto.text = if (autoSfx) "Sound Effects (SFX): ON" else "Sound Effects (SFX): OFF"
    }

    private fun refreshDuckUi() {
        btnDuck.text = if (duck) "Auto-duck: ON" else "Auto-duck: OFF"
    }

    private fun resetVolumes() {
        val d = intArrayOf(100, 100, 0, 100)
        for (i in 0 until 4) volBars[i].progress = d[i]
        duck = true
        autoSfx = true
        refreshDuckUi()
        refreshAutoUi()
        saveVolumes()
        pushVolumes()
    }

    // ---------- v3: DOM HTML ka frame capture + audio stems ----------
    private fun captureWeb(): Bitmap? {
        val latch = CountDownLatch(1)
        var okDraw = false
        runOnUiThread {
            try {
                val w = web.width
                val h = web.height
                if (w > 0 && h > 0) {
                    var b = capBmp
                    if (b == null || b.width != w || b.height != h) {
                        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        capBmp = b
                    }
                    val c = Canvas(b!!)
                    c.drawColor(Color.BLACK)
                    web.draw(c)
                    okDraw = true
                }
            } catch (_: Exception) {
            }
            latch.countDown()
        }
        latch.await(15, TimeUnit.SECONDS)
        return if (okDraw) capBmp else null
    }

    /** Kuch sample points par check karta hai ki poora frame ek hi rang ka to nahi. */
    private fun looksUniform(b: Bitmap): Boolean {
        val first = b.getPixel(b.width / 2, b.height / 2)
        var y = 40
        while (y < b.height) {
            var x = 40
            while (x < b.width) {
                if (b.getPixel(x, y) != first) return false
                x += 160
            }
            y += 120
        }
        return true
    }

    /** Page se music aur SFX ka PCM mangta hai (OfflineAudioContext). Background thread se bulao. */
    private fun renderStems(): Pair<Stem?, Stem?> {
        synchronized(stemBufs) { stemBufs.clear() }
        synchronized(audioInfo) { audioInfo.clear() }
        audioErr = null
        val latch = CountDownLatch(1)
        audioLatch = latch
        runOnUiThread { web.evaluateJavascript("NH.renderAudio()", null) }
        var waitedMs = 0L
        while (!latch.await(500, TimeUnit.MILLISECONDS)) {
            if (cancel) throw Exception("Cancel ho gaya")
            waitedMs += 500
            if (waitedMs > 20 * 60 * 1000L) throw Exception("Music/SFX render timeout (20 min)")
        }
        audioLatch = null
        val m = synchronized(stemBufs) { stemBufs["music"] }
        val s = synchronized(stemBufs) { stemBufs["sfx"] }
        synchronized(stemBufs) { stemBufs.clear() }
        return Pair(m, s)
    }

    /** DOM mode mein export ke waqt WebView 1920x1080 px par; baaki time player box mein normal. */
    private fun layoutWeb() {
        if (!::web.isInitialized || !::playerBox.isInitialized) return
        val lp = web.layoutParams as? FrameLayout.LayoutParams ?: return
        val big = domMode && exportScaled
        val wantW = if (big) 1920 else ViewGroup.LayoutParams.MATCH_PARENT
        val wantH = if (big) 1080 else ViewGroup.LayoutParams.MATCH_PARENT
        if (lp.width != wantW || lp.height != wantH) {
            lp.width = wantW
            lp.height = wantH
            web.layoutParams = lp
        }
        if (big) {
            val bw = playerBox.width
            val bh = playerBox.height
            if (bw > 0 && bh > 0) {
                val sc = minOf(bw / 1920f, bh / 1080f)
                web.pivotX = 0f
                web.pivotY = 0f
                web.scaleX = sc
                web.scaleY = sc
                web.translationX = (bw - 1920f * sc) / 2f
                web.translationY = (bh - 1080f * sc) / 2f
            }
        } else {
            web.scaleX = 1f
            web.scaleY = 1f
            web.translationX = 0f
            web.translationY = 0f
        }
    }

    // ---------- save / share ----------
    private fun saveToGallery(f: File): Uri {
        val name = "HTML2Video_${System.currentTimeMillis()}.mp4"
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HTML2Video")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
            ?: throw Exception("Gallery mein file nahi bana paya")
        contentResolver.openOutputStream(uri)!!.use { os -> f.inputStream().use { it.copyTo(os) } }
        v.clear()
        v.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(uri, v, null, null)
        return uri
    }

    private fun saveDownload(name: String, mime: String, bytes: ByteArray): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "file" }
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safe)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/HTML2Video")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            ?: throw Exception("Downloads mein file nahi bana paya")
        contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        v.clear()
        v.put(MediaStore.Downloads.IS_PENDING, 0)
        contentResolver.update(uri, v, null, null)
        return "Download/HTML2Video/$safe"
    }

    private fun openVideo() {
        val u = savedUri ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(u, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) {
            setStatus("Video player nahi mila: ${e.message}", 2)
        }
    }

    private fun shareVideo() {
        val u = savedUri ?: return
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, u)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Video share karo"))
        } catch (e: Exception) {
            setStatus("Share nahi ho paya: ${e.message}", 2)
        }
    }

    private fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    // ---------- JS bridge ----------
    inner class Bridge {
        @JavascriptInterface
        fun pushFrame(dataUrl: String): Boolean {
            if (cancel) return false
            val e = exporter ?: return false
            return try {
                e.addFrame(dataUrl)
                true
            } catch (ex: Exception) {
                fail("Frame encode fail: ${ex.message}")
                false
            }
        }

        @JavascriptInterface
        fun progress(n: Int, total: Int) {
            val sec = (SystemClock.elapsedRealtime() - startMs) / 1000
            val eta = if (n > 0) sec * (total - n) / n else 0
            runOnUiThread {
                bar.progress = if (total > 0) (n.toLong() * 1000 / total).toInt() else 0
                setStatus("Video ban raha hai... ${n * 100 / maxOf(total, 1)}%", 0)
                statsText.text = "Frame $n / $total   \u2022   ${sec}s gaye   \u2022   lagbhag ${eta}s baaki"
            }
        }

        @JavascriptInterface
        fun done() {
            Thread {
                try {
                    val e = exporter ?: return@Thread
                    ui("MP4 band ho raha hai...")
                    val f = e.finish()
                    exporter = null
                    val uri = saveToGallery(f)
                    f.delete()
                    savedUri = uri
                    runOnUiThread {
                        if (exportScaled) { exportScaled = false; layoutWeb() }
                        bar.progress = 1000
                        setStatus("Ho gaya! Video Movies/HTML2Video mein save hua (" + (if (portrait) "9:16 1080x1920" else "1080p") + ", $fps fps)." +
                            (if (audioNote.isNotEmpty()) " Audio: $audioNote" else ""), 1)
                        resultRow.visibility = View.VISIBLE
                        setBusy(false)
                    }
                } catch (ex: Exception) {
                    fail("MP4 save fail: ${ex.message}")
                }
            }.start()
        }

        @JavascriptInterface
        fun fail(msg: String) {
            this@MainActivity.fail(msg)
        }

        /** v3: DOM HTML ke liye. WebView ko seedha 1920x1080 Bitmap mein draw karke encoder ko deta hai. */
        @JavascriptInterface
        fun captureFrame(n: Int): Boolean {
            if (cancel) return false
            val e = exporter ?: return false
            val bmp = captureWeb()
            if (bmp == null) {
                fail("WebView se frame capture nahi hua")
                return false
            }
            if (!contentSeen) {
                if (looksUniform(bmp)) {
                    blankFrames++
                    if (blankFrames >= 90) {
                        fail("Capture ke frame khali (ek hi rang) aa rahe hain. Is phone ka WebView draw() support nahi kar raha ya page render nahi hua.")
                        return false
                    }
                } else {
                    contentSeen = true
                }
            }
            return try {
                e.addBitmap(bmp)
                true
            } catch (ex: Exception) {
                fail("Frame encode fail: ${ex.message}")
                false
            }
        }

        @JavascriptInterface
        fun audioBegin(stem: String, samples: Int, rate: Int) {
            synchronized(stemBufs) { stemBufs[stem] = Stem(ShortArray(samples), rate) }
        }

        @JavascriptInterface
        fun audioChunk(stem: String, b64: String) {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val st = synchronized(stemBufs) { stemBufs[stem] } ?: return
            val n = bytes.size / 2
            for (i in 0 until n) {
                if (st.pos >= st.data.size) break
                st.data[st.pos++] = ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
            }
        }

        @JavascriptInterface
        fun audioInfo(stem: String, v: Double, count: Int) {
            synchronized(this@MainActivity.audioInfo) {
                this@MainActivity.audioInfo[stem] = v
                this@MainActivity.audioInfo[stem + "_n"] = count.toDouble()
            }
        }

        @JavascriptInterface
        fun audioDone() {
            audioLatch?.countDown()
        }

        @JavascriptInterface
        fun audioFail(msg: String) {
            audioErr = msg
            audioLatch?.countDown()
        }

        @JavascriptInterface
        fun saveFile(name: String, mime: String, b64: String) {
            Thread {
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val where = saveDownload(name, if (mime.isBlank()) "application/octet-stream" else mime, bytes)
                    runOnUiThread { Toast.makeText(this@MainActivity, "Saved: $where", Toast.LENGTH_LONG).show() }
                } catch (ex: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Save fail: ${ex.message}", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
    }

    companion object {
        /** Page ke andar helper: sirf video canvas dikhana (Player mode) aur blob download ko app tak lana. */
        private val H2V_JS = """(function(){
if(window.__h2v)return 'exists';
window.__h2v={
 on:function(pt,fl){
  try{
   var st=document.getElementById('h2v-style');
   if(!st){st=document.createElement('style');st.id='h2v-style';document.head.appendChild(st)}
   st.textContent='html,body{margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important;height:100%!important}.h2v-hide{display:none!important}.h2v-path{display:block!important;margin:0!important;padding:0!important;max-width:none!important;width:100%!important;border:0!important;background:transparent!important}.h2v-cv{display:block!important;width:100vw!important;height:56.25vw!important;max-height:100vh!important;border-radius:0!important;margin:0!important;aspect-ratio:auto!important}';
   if(pt){st.textContent+='.h2v-cv{width:100vw!important;height:100vh!important;object-fit:'+(fl?'cover':'contain')+'!important}'}
   var c=document.getElementById('cv')||document.querySelector('canvas');
   if(!c)return 'nocanvas';
   var p=c;
   while(p&&p!==document.body){
    if(p.dataset&&p.dataset.h2vh===undefined)p.dataset.h2vh=p.hidden?'1':'0';
    p.hidden=false;p.classList.add('h2v-path');
    var par=p.parentElement;
    if(par){for(var i=0;i<par.children.length;i++){var s=par.children[i];if(s!==p&&!/^(SCRIPT|STYLE|LINK|META)${'$'}/.test(s.tagName))s.classList.add('h2v-hide')}}
    p=par;
   }
   c.classList.add('h2v-cv');
   window.scrollTo(0,0);
   if(typeof buildPlan==='function'&&typeof frameAt==='function'&&window.S&&window.S.length&&typeof TOTAL!=='undefined'){buildPlan();frameAt(Math.min(2.5,Math.max(0,TOTAL-0.05)))}
   return 'ok';
  }catch(e){return 'err:'+e.message}
 },
 off:function(){
  try{
   var st=document.getElementById('h2v-style');if(st)st.textContent='';
   document.querySelectorAll('.h2v-hide').forEach(function(e){e.classList.remove('h2v-hide')});
   document.querySelectorAll('.h2v-path').forEach(function(e){e.classList.remove('h2v-path');if(e.dataset&&e.dataset.h2vh!==undefined){e.hidden=(e.dataset.h2vh==='1');delete e.dataset.h2vh}});
   var c=document.getElementById('cv')||document.querySelector('canvas');if(c)c.classList.remove('h2v-cv');
   return 'ok';
  }catch(e){return 'err:'+e.message}
 }
};
if(!window.__h2vDl){window.__h2vDl=1;
 var oc=HTMLAnchorElement.prototype.click;
 HTMLAnchorElement.prototype.click=function(){
  var a=this;
  if(a.download&&a.href&&a.href.indexOf('blob:')===0&&window.Android&&Android.saveFile){
   fetch(a.href).then(function(r){return r.blob()}).then(function(b){
    var fr=new FileReader();
    fr.onload=function(){Android.saveFile(a.download,b.type||'application/octet-stream',String(fr.result).split(',')[1])};
    fr.readAsDataURL(b);
   });
   return;
  }
  return oc.apply(a,arguments);
 };
}
return 'defined';
})()"""
    }
}
