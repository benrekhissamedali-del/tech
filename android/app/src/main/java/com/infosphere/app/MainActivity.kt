package com.infosphere.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var sky: SkyView
    private lateinit var engine: SignalEngine
    private var started = false

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

        engine = SignalEngine(this) { sources ->
            runOnUiThread {
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
                infoDetail.text = src.detail
                infoCard.visibility = View.VISIBLE
            }
        }
        findViewById<View>(R.id.infoClose).setOnClickListener {
            infoCard.visibility = View.GONE
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

    private fun startExperience() {
        if (started) return
        started = true
        findViewById<View>(R.id.splash).visibility = View.GONE
        engine.start()
        sky.start()
    }

    override fun onResume() {
        super.onResume()
        if (started) {
            engine.start()
            sky.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (started) {
            sky.stop()
            engine.stop()
        }
    }

    private companion object {
        const val PERMISSION_REQUEST = 1
    }
}
