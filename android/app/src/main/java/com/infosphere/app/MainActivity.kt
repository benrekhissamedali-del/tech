package com.infosphere.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var sky: SkyView
    private lateinit var engine: SignalEngine
    private var sound: SoundEngine? = null
    private var started = false
    private var latestSources: List<SignalSource> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sky = findViewById(R.id.sky)
        val countCell = findViewById<TextView>(R.id.countCell)
        val countWifi = findViewById<TextView>(R.id.countWifi)
        val countSat = findViewById<TextView>(R.id.countSat)
        val coords = findViewById<TextView>(R.id.coords)
        val compass = findViewById<TextView>(R.id.compass)
        val infoCard = findViewById<View>(R.id.infoCard)
        val infoTitle = findViewById<TextView>(R.id.infoTitle)
        val infoDetail = findViewById<TextView>(R.id.infoDetail)
        val hint = findViewById<TextView>(R.id.hint)
        val listPanel = findViewById<View>(R.id.listPanel)
        val btnMute = findViewById<TextView>(R.id.btnMute)

        engine = SignalEngine(this) { sources ->
            runOnUiThread {
                latestSources = sources
                sky.setSources(sources)
                countCell.text = getString(
                    R.string.count_cell, sources.count { it.type == SignalType.CELL })
                countWifi.text = getString(
                    R.string.count_wifi, sources.count { it.type == SignalType.WIFI })
                countSat.text = getString(
                    R.string.count_sat, sources.count { it.type == SignalType.SAT })
            }
        }
        engine.onLocation = { lat, lon ->
            runOnUiThread {
                coords.text = String.format(Locale.FRANCE, "%.4f°, %.4f°", lat, lon)
            }
        }

        sky.onCompass = { deg ->
            val dirs = resources.getStringArray(R.array.compass_dirs)
            compass.text = getString(R.string.compass_fmt, deg, dirs[((deg + 22) / 45) % 8])
        }
        sky.onTap = { src ->
            if (src == null) {
                infoCard.visibility = View.GONE
            } else {
                infoTitle.text = src.title
                infoTitle.setTextColor(frequencyColor(src.frequencyMhz, src.type))
                infoDetail.text = src.detail
                infoCard.visibility = View.VISIBLE
            }
        }
        sky.onPulseArrived = { src -> sound?.ping(src.frequencyMhz) }

        findViewById<View>(R.id.infoClose).setOnClickListener {
            infoCard.visibility = View.GONE
        }

        findViewById<View>(R.id.btnList).setOnClickListener {
            buildListPanel()
            listPanel.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.listClose).setOnClickListener {
            listPanel.visibility = View.GONE
        }

        btnMute.setOnClickListener {
            val s = sound ?: return@setOnClickListener
            s.muted = !s.muted
            btnMute.text = getString(if (s.muted) R.string.sound_off else R.string.sound_on)
        }

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            if (hasLocationPermission()) {
                startExperience()
            } else {
                requestPermissions(requiredPermissions(), PERMISSION_REQUEST)
            }
        }
        hint.postDelayed({ hint.animate().alpha(0f).setDuration(1200).start() }, 9000)
    }

    // -------------------------------------------------------- Liste détaillée

    private fun buildListPanel() {
        val content = findViewById<LinearLayout>(R.id.listContent)
        content.removeAllViews()
        val density = resources.displayMetrics.density

        fun addHeader(text: String) {
            val tv = TextView(this)
            tv.text = text
            tv.setTextColor(getColor(R.color.accent))
            tv.textSize = 15f
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.setPadding(0, (18 * density).toInt(), 0, (6 * density).toInt())
            content.addView(tv)
        }

        fun addRow(src: SignalSource) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setBackgroundResource(R.drawable.info_card_bg)
            row.setPadding(
                (14 * density).toInt(), (10 * density).toInt(),
                (14 * density).toInt(), (10 * density).toInt(),
            )
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = (8 * density).toInt()
            row.layoutParams = lp

            val title = TextView(this)
            title.text = src.title
            title.setTextColor(frequencyColor(src.frequencyMhz, src.type))
            title.textSize = 15f
            title.typeface = Typeface.DEFAULT_BOLD
            row.addView(title)

            val detail = TextView(this)
            detail.text = src.detail
            detail.setTextColor(getColor(R.color.ink))
            detail.alpha = 0.8f
            detail.textSize = 13f
            detail.setLineSpacing(0f, 1.25f)
            row.addView(detail)

            content.addView(row)
        }

        val cells = latestSources.filter { it.type == SignalType.CELL }
            .sortedByDescending { it.strength }
        val wifis = latestSources.filter { it.type == SignalType.WIFI }
            .sortedByDescending { it.strength }
        val sats = latestSources.filter { it.type == SignalType.SAT }
            .sortedByDescending { it.strength }

        addHeader(getString(R.string.header_cell, cells.size))
        cells.forEach(::addRow)
        addHeader(getString(R.string.header_wifi, wifis.size))
        wifis.forEach(::addRow)
        addHeader(getString(R.string.header_sat, sats.size))
        sats.forEach(::addRow)
    }

    // ------------------------------------------------------------ Permissions

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return perms.toTypedArray()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        // On démarre même si la permission est refusée : le mode démo prend le relais.
        if (requestCode == PERMISSION_REQUEST) startExperience()
    }

    // ----------------------------------------------------------- Cycle de vie

    private fun startExperience() {
        if (started) return
        started = true
        findViewById<View>(R.id.splash).visibility = View.GONE
        if (sound == null) sound = SoundEngine(this)
        engine.start()
        sky.start()
    }

    override fun onResume() {
        super.onResume()
        if (started) {
            engine.start()
            sky.start()
            sound?.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (started) {
            sky.stop()
            engine.stop()
            sound?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sound?.release()
        sound = null
    }

    private companion object {
        const val PERMISSION_REQUEST = 1
    }
}
