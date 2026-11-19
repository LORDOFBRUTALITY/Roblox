package com.wexdraw.robloxtool

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.text.InputType
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private lateinit var panel: ScrollView
    private lateinit var scriptInput: EditText
    private lateinit var savedList: LinearLayout
    private lateinit var bp: WindowManager.LayoutParams
    private lateinit var pp: WindowManager.LayoutParams
    private val scripts = mutableListOf<String>()
    private val ui = Handler(Looper.getMainLooper())
    private var bubbleShown = false
    private var panelShown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        loadPrefs()
        buildBubble()
        buildPanel()
        showBubble()
    }

    override fun onDestroy() {
        if (bubbleShown) wm.removeView(bubble)
        if (panelShown) wm.removeView(panel)
        super.onDestroy()
    }

    // ---------- Foreground bildirimi ----------
    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("wex", "Wex Tool", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "wex")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Wex Tool çalışıyor")
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Balon ----------
    private fun buildBubble() {
        bubble = TextView(this).apply {
            text = "W"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#e63946"))
            setTypeface(null, Typeface.BOLD)
        }
        bp = WindowManager.LayoutParams(
            dp(48), dp(48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(200) }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = bp.x; sy = bp.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        bp.x = sx + dx; bp.y = sy + dy
                        wm.updateViewLayout(bubble, bp)
                    }
                    MotionEvent.ACTION_UP -> if (!moved) openPanel()
                }
                return true
            }
        })
    }

    private fun showBubble() {
        if (!bubbleShown) { wm.addView(bubble, bp); bubbleShown = true }
    }

    private fun openPanel() {
        if (bubbleShown) { wm.removeView(bubble); bubbleShown = false }
        if (!panelShown) { wm.addView(panel, pp); panelShown = true }
    }

    private fun minimize() {
        if (panelShown) { wm.removeView(panel); panelShown = false }
        showBubble()
    }

    // ---------- Panel ----------
    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTextColor(Color.parseColor("#ff6f3c"))
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 24, 0, 8)
    }

    private fun button(t: String, color: String) = Button(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor(color))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 }
    }

    private fun buildPanel() {
        val dm = resources.displayMetrics
        pp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.9).toInt(),
            (dm.heightPixels * 0.6).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // klavye için odaklanabilir
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        panel = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#e61b1f27"))
        }
        panel.addView(root)

        // Üst bar
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val minBtn = Button(this).apply { text = "— Küçült"; setOnClickListener { minimize() } }
        val closeBtn = Button(this).apply { text = "✕ Kapat"; setOnClickListener { stopSelf() } }
        bar.addView(minBtn); bar.addView(closeBtn)
        root.addView(bar)

        // Sunucu seçici
        root.addView(title("⚡ Sunucu Seçici"))
        val placeIdInput = EditText(this).apply {
            hint = "Place ID (örn: 2753915549)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(placeIdInput)

        val leastBtn = button("👥 En Az Kişili Sunucuya Katıl", "#2d6a4f")
        root.addView(leastBtn)
        leastBtn.setOnClickListener {
            val pid = placeIdInput.text.toString().trim()
            if (pid.isEmpty()) return@setOnClickListener toast("Place ID gir!")
            joinServer(pid, true)
        }

        val randomBtn = button("🎲 Rastgele Sunucuya Katıl", "#457b9d")
        root.addView(randomBtn)
        randomBtn.setOnClickListener {
            val pid = placeIdInput.text.toString().trim()
            if (pid.isEmpty()) return@setOnClickListener toast("Place ID gir!")
            joinServer(pid, false)
        }

        // Script notları
        root.addView(title("📜 Script Notları"))
        scriptInput = EditText(this).apply {
            hint = "Script'ini buraya yapıştır..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 5
            setBackgroundColor(Color.parseColor("#272c36"))
            setPadding(20, 20, 20, 20)
        }
        root.addView(scriptInput)

        val saveBtn = button("💾 Scripti Kaydet", "#6a4c93")
        root.addView(saveBtn)
        saveBtn.setOnClickListener {
            val s = scriptInput.text.toString().trim()
            if (s.isEmpty()) return@setOnClickListener toast("Boş script kaydedilmez!")
            scripts.add(s); savePrefs(); refreshList()
            toast("Script kaydedildi ✔")
        }

        val copyBtn = button("📋 Panoya Kopyala", "#e63946")
        root.addView(copyBtn)
        copyBtn.setOnClickListener {
            val s = scriptInput.text.toString().trim()
            if (s.isEmpty()) return@setOnClickListener toast("Script boş!")
            copy(s)
            toast("Panoya kopyalandı")
        }

        root.addView(title("🗂 Kayıtlı Scriptler"))
        savedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(savedList)
        refreshList()
    }

    private fun refreshList() {
        savedList.removeAllViews()
        scripts.forEachIndexed { i, s ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#272c36"))
                setPadding(20, 20, 20, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 12 }
            }
            row.addView(TextView(this).apply {
                text = "Script #${i + 1} — ${s.take(40)}..."
                setTextColor(Color.WHITE)
            })
            val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            btns.addView(Button(this).apply {
                text = "Yükle"
                setOnClickListener { scriptInput.setText(scripts[i]); toast("Editöre yüklendi") }
            })
            btns.addView(Button(this).apply {
                text = "📋"
                setOnClickListener { copy(scripts[i]); toast("Kopyalandı") }
            })
            btns.addView(Button(this).apply {
                text = "Sil"
                setOnClickListener { scripts.removeAt(i); savePrefs(); refreshList() }
            })
            row.addView(btns)
            savedList.addView(row)
        }
    }

    // ---------- Yardımcılar ----------
    private fun copy(s: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("script", s))
    }

    private fun toast(t: String) {
        ui.post { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }
    }

    private fun openUrl(u: String) {
        ui.post {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
        }
    }

    private fun joinServer(placeId: String, leastPlayers: Boolean) {
        toast("Sunucular taranıyor...")
        Thread {
            try {
                var bestJobId: String? = null
                var bestCount = Int.MAX_VALUE
                val all = mutableListOf<Pair<String, Int>>()
                var cursor = ""

                for (page in 0 until 5) {
                    val urlStr = "https://games.roblox.com/v1/games/$placeId/servers/Public?sortOrder=Asc&limit=100" +
                            if (cursor.isNotEmpty()) "&cursor=$cursor" else ""
                    val conn = URL(urlStr).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val data = json.optJSONArray("data") ?: break
                    for (i in 0 until data.length()) {
                        val sv = data.getJSONObject(i)
                        val id = sv.getString("id")
                        val cnt = sv.getInt("playerCount")
                        all.add(id to cnt)
                        if (cnt < bestCount) { bestCount = cnt; bestJobId = id }
                    }
                    cursor = json.optString("nextPageCursor", "")
                    if (cursor.isEmpty() || cursor == "null") break
                }

                val chosen = if (leastPlayers) bestJobId else all.randomOrNull()?.first
                if (chosen == null) {
                    toast("Sunucu bulunamadı, oyun sayfası açılıyor...")
                    openUrl("https://www.roblox.com/games/start?placeId=$placeId")
                    return@Thread
                }
                toast("Sunucu bulundu, Roblox açılıyor...")
                ui.post { minimize() }
                openUrl("roblox://experiences/start?placeId=$placeId&gameInstanceId=$chosen")
            } catch (e: Exception) {
                toast("Hata: ${e.message}")
            }
        }.start()
    }

    private fun savePrefs() {
        getSharedPreferences("wex", MODE_PRIVATE)
            .edit().putStringSet("scripts", scripts.toSet()).apply()
    }

    private fun loadPrefs() {
        scripts.clear()
        scripts.addAll(
            getSharedPreferences("wex", MODE_PRIVATE)
                .getStringSet("scripts", emptySet())?.sortedBy { it } ?: emptyList()
        )
    }
}
