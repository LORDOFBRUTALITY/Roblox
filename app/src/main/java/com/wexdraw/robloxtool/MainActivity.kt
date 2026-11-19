package com.wexdraw.robloxtool

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var scriptInput: EditText
    private lateinit var savedList: LinearLayout
    private val scripts = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(40, 60, 40, 60)
        root.setBackgroundColor(Color.parseColor("#1b1f27"))
        scroll.addView(root)
        setContentView(scroll)

        fun title(t: String): TextView {
            val tv = TextView(this)
            tv.text = t
            tv.textSize = 20f
            tv.setTextColor(Color.parseColor("#ff6f3c"))
            tv.setTypeface(null, Typeface.BOLD)
            tv.setPadding(0, 30, 0, 10)
            return tv
        }

        fun button(t: String, color: String): Button {
            val b = Button(this)
            b.text = t
            b.setTextColor(Color.WHITE)
            b.setBackgroundColor(Color.parseColor(color))
            b.setPadding(0, 16, 0, 16)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 12
            b.layoutParams = p
            return b
        }

        // ---- EXTRALAR ----
        root.addView(title("⚡ Ek Düğmeler (Sunucu Seçici)"))

        val placeIdInput = EditText(this)
        placeIdInput.hint = "Place ID (örn: 2753915549)"
        placeIdInput.setTextColor(Color.WHITE)
        placeIdInput.setHintTextColor(Color.GRAY)
        placeIdInput.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(placeIdInput)

        val leastBtn = button("👥 En Az Kişili Sunucuya Katıl", "#2d6a4f")
        root.addView(leastBtn)
        leastBtn.setOnClickListener {
            val pid = placeIdInput.text.toString().trim()
            if (pid.isEmpty()) return@setOnClickListener toast("Place ID gir!")
            joinServer(pid, leastPlayers = true)
        }

        val randomBtn = button("🎲 Rastgele Sunucuya Katıl", "#457b9d")
        root.addView(randomBtn)
        randomBtn.setOnClickListener {
            val pid = placeIdInput.text.toString().trim()
            if (pid.isEmpty()) return@setOnClickListener toast("Place ID gir!")
            joinServer(pid, leastPlayers = false)
        }

        // ---- SCRIPT BÖLÜMÜ ----
        root.addView(title("📜 Script Editörü"))

        scriptInput = EditText(this)
        scriptInput.hint = "Script'ini buraya yapıştır..."
        scriptInput.setTextColor(Color.WHITE)
        scriptInput.setHintTextColor(Color.GRAY)
        scriptInput.minLines = 6
        scriptInput.setBackgroundColor(Color.parseColor("#272c36"))
        scriptInput.setPadding(20, 20, 20, 20)
        root.addView(scriptInput)

        val saveBtn = button("💾 Scripti Kaydet", "#6a4c93")
        root.addView(saveBtn)
        saveBtn.setOnClickListener {
            val s = scriptInput.text.toString().trim()
            if (s.isEmpty()) return@setOnClickListener toast("Boş script kaydedilmez!")
            scripts.add(s)
            savePrefs()
            refreshList()
            toast("Script kaydedildi! ✔")
        }

        val execBtn = button("▶ EXECUTE", "#e63946")
        root.addView(execBtn)
        execBtn.setOnClickListener {
            val s = scriptInput.text.toString().trim()
            if (s.isEmpty()) return@setOnClickListener toast("Script boş!")
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("script", s))
            openRoblox()
            toast("Script çalıştırıldı! 🚀")
        }

        root.addView(title("🗂 Kayıtlı Scriptler"))
        savedList = LinearLayout(this)
        savedList.orientation = LinearLayout.VERTICAL
        root.addView(savedList)

        loadPrefs()
        refreshList()
    }

    private fun refreshList() {
        savedList.removeAllViews()
        scripts.forEachIndexed { i, s ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setBackgroundColor(Color.parseColor("#272c36"))
            row.setPadding(20, 20, 20, 20)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 12
            row.layoutParams = p

            val name = TextView(this)
            name.text = "Script #${i + 1} — ${s.take(40)}..."
            name.setTextColor(Color.WHITE)
            row.addView(name)

            val btns = LinearLayout(this)
            btns.orientation = LinearLayout.HORIZONTAL

            val use = Button(this)
            use.text = "Yükle"
            use.setOnClickListener {
                scriptInput.setText(scripts[i])
                toast("Editöre yüklendi!")
            }
            val del = Button(this)
            del.text = "Sil"
            del.setOnClickListener {
                scripts.removeAt(i)
                savePrefs()
                refreshList()
            }
            val run = Button(this)
            run.text = "▶"
            run.setOnClickListener {
                scriptInput.setText(scripts[i])
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("script", scripts[i]))
                openRoblox()
                toast("Çalıştırıldı! 🚀")
            }

            btns.addView(use)
            btns.addView(run)
            btns.addView(del)
            row.addView(btns)
            savedList.addView(row)
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
                    val body = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: break
                    for (i in 0 until data.length()) {
                        val sv = data.getJSONObject(i)
                        val id = sv.getString("id")
                        val cnt = sv.getInt("playerCount")
                        all.add(id to cnt)
                        if (cnt < bestCount) { bestCount = cnt; bestJobId = id }
                    }
                    cursor = json.optString("nextPageCursor", "")
                    if (cursor.isNullOrEmpty()) break
                }

                val chosen = if (leastPlayers) bestJobId
                             else all.randomOrNull()?.first

                if (chosen == null) {
                    runOnUiThread { toast("Sunucu bulunamadı, oyun ana sayfası açılıyor...") }
                    openUrl("https://www.roblox.com/games/start?placeId=$placeId")
                    return@Thread
                }

                runOnUiThread { toast("Sunucu bulundu! ($bestCount kişi) Roblox açılıyor...") }
                openUrl("roblox://navigation/game_details?placeId=$placeId&gameInstanceId=$chosen")
            } catch (e: Exception) {
                runOnUiThread { toast("Hata: ${e.message}") }
            }
        }.start()
    }

    private fun openRoblox() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("roblox://")))
        } catch (e: Exception) {
            openUrl("https://www.roblox.com/home")
        }
    }

    private fun openUrl(u: String) {
        runOnUiThread {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
            } catch (_: Exception) {}
        }
    }

    private fun toast(t: String) = runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }

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
