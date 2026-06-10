package com.infosphere.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Collecte les vraies sources de signaux autour de l'appareil :
 *  - réseaux Wi-Fi (scan WifiManager, distance estimée par perte en espace libre)
 *  - antennes cellulaires (TelephonyManager.allCellInfo)
 *  - satellites GNSS avec leur azimut/élévation réels (GnssStatus)
 *
 * Si aucune donnée réelle n'est disponible (permissions refusées, émulateur…),
 * un monde de démonstration est généré après quelques secondes.
 */
class SignalEngine(
    private val context: Context,
    private val onSources: (List<SignalSource>) -> Unit,
) {
    var onLocation: ((Double, Double) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val wifiSources = LinkedHashMap<String, SignalSource>()
    private val cellSources = LinkedHashMap<String, SignalSource>()
    private val satSources = LinkedHashMap<String, SignalSource>()
    private var demoMode = false
    private var running = false

    // ------------------------------------------------------------------ Wi-Fi

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) = readScanResults()
    }

    @SuppressLint("MissingPermission")
    private fun readScanResults() {
        val results = try {
            wifiManager.scanResults
        } catch (e: SecurityException) {
            return
        } ?: return
        if (results.isEmpty()) return

        wifiSources.clear()
        for (r in results) {
            val bssid = r.BSSID ?: continue
            @Suppress("DEPRECATION")
            val ssid = if (r.SSID.isNullOrBlank()) "(réseau masqué)" else r.SSID
            val distM = wifiDistanceMeters(r.level, r.frequency)
            val band = if (r.frequency > 5000) "5 GHz" else "2,4 GHz"
            wifiSources[bssid] = SignalSource(
                type = SignalType.WIFI,
                azimuthDeg = hashedAzimuth(bssid),
                elevationDeg = hashedUnit(bssid) * 50f - 22f,
                radius = 12f + min(1f, distM / 80f) * 18f,
                title = ssid,
                detail = "Wi-Fi $band · signal ${r.level} dBm · ≈ ${distM.toInt()} m\n" +
                        "Ce routeur émet ~10 trames balises par seconde pour annoncer sa présence.",
            )
        }
        demoMode = false
        publish()
    }

    /** Distance estimée par la formule de perte en espace libre (FSPL). */
    private fun wifiDistanceMeters(levelDbm: Int, freqMhz: Int): Float {
        val exp = (27.55 - 20.0 * log10(freqMhz.toDouble()) - levelDbm) / 20.0
        return 10.0.pow(exp).toFloat().coerceIn(1f, 120f)
    }

    private val wifiScanLoop = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
            } catch (e: SecurityException) {
                // permission refusée : on reste en mode démo
            }
            handler.postDelayed(this, 30_000)
        }
    }

    // ---------------------------------------------------------------- Antennes

    private data class ParsedCell(val tech: String, val key: String, val dbm: Int)

    private fun parseCell(info: CellInfo): ParsedCell? {
        if (info is CellInfoLte) {
            return ParsedCell("4G LTE", "lte-${info.cellIdentity.ci}", info.cellSignalStrength.dbm)
        }
        if (Build.VERSION.SDK_INT >= 29 && info is CellInfoNr) {
            val id = (info.cellIdentity as? android.telephony.CellIdentityNr)?.nci ?: return null
            return ParsedCell("5G NR", "nr-$id", info.cellSignalStrength.dbm)
        }
        if (info is CellInfoWcdma) {
            return ParsedCell("3G UMTS", "wcdma-${info.cellIdentity.cid}", info.cellSignalStrength.dbm)
        }
        if (info is CellInfoGsm) {
            return ParsedCell("2G GSM", "gsm-${info.cellIdentity.cid}", info.cellSignalStrength.dbm)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun readCells() {
        val infos = try {
            telephonyManager.allCellInfo
        } catch (e: SecurityException) {
            null
        } ?: return
        if (infos.isEmpty()) return

        val operator = telephonyManager.networkOperatorName.ifBlank { "opérateur inconnu" }
        cellSources.clear()
        for (info in infos) {
            val cell = parseCell(info) ?: continue
            if (cell.dbm == Int.MAX_VALUE || cell.dbm >= 0) continue
            val distM = (10.0.pow((-cell.dbm - 60) / 20.0) * 40.0)
                .toFloat().coerceIn(100f, 3000f)
            val served = if (info.isRegistered) " · connectée à votre téléphone" else ""
            cellSources[cell.key] = SignalSource(
                type = SignalType.CELL,
                azimuthDeg = hashedAzimuth(cell.key),
                elevationDeg = 1f + (1f - distM / 3000f) * 5f,
                radius = 30f + (distM / 3000f) * 28f,
                title = "Antenne $operator",
                detail = "${cell.tech} · signal ${cell.dbm} dBm · ≈ ${distM.toInt()} m$served\n" +
                        "Station de base du réseau mobile. Votre téléphone lui parle " +
                        "en permanence, même en veille.",
            )
        }
        demoMode = false
        publish()
    }

    private val cellLoop = object : Runnable {
        override fun run() {
            if (!running) return
            readCells()
            handler.postDelayed(this, 10_000)
        }
    }

    // -------------------------------------------------------------- Satellites

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satSources.clear()
            for (i in 0 until status.satelliteCount) {
                val el = status.getElevationDegrees(i)
                if (el <= 2f) continue
                val cons = constellationName(status.getConstellationType(i))
                val svid = status.getSvid(i)
                val used = if (status.usedInFix(i)) "utilisé pour votre position" else "visible"
                val cn0 = status.getCn0DbHz(i).toInt()
                satSources["$cons-$svid"] = SignalSource(
                    type = SignalType.SAT,
                    azimuthDeg = status.getAzimuthDegrees(i),
                    elevationDeg = el,
                    radius = 62f,
                    title = "Satellite $cons · n° $svid",
                    detail = "Élévation ${el.toInt()}° · signal $cn0 dB-Hz · $used\n" +
                            "Son signal met ~70 ms pour vous atteindre depuis ~20 000 km. " +
                            "Il transporte l'heure atomique qui permet de vous localiser.",
                )
            }
            publish()
        }
    }

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> "GNSS"
    }

    // Interface complète : sur Android 8 à 10, les méthodes ci-dessous sont
    // abstraites côté système et une simple lambda provoquerait un crash.
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            onLocation?.invoke(loc.latitude, loc.longitude)
        }

        @Deprecated("Conservé pour les anciennes versions d'Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    // ------------------------------------------------------------------- Démo

    /** Monde plausible si aucune donnée réelle n'arrive (permissions, émulateur…). */
    private fun buildDemoWorld() {
        if (wifiSources.isNotEmpty() || cellSources.isNotEmpty()) return
        demoMode = true
        val rand = Random(0xC0FFEE)
        val operators = listOf("Orange", "SFR", "Bouygues Telecom", "Free Mobile")
        val prefixes = listOf("Livebox-", "Freebox-", "SFR_", "Bbox-", "TP-Link_")
        repeat(10) { i ->
            val d = 150f + rand.nextFloat() * 2850f
            cellSources["demo-c$i"] = SignalSource(
                SignalType.CELL, rand.nextFloat() * 360f, 1f + rand.nextFloat() * 5f,
                30f + (d / 3000f) * 28f,
                "Antenne ${operators[rand.nextInt(operators.size)]}",
                "Mode démonstration — 4G · ≈ ${d.toInt()} m\n" +
                        "Accordez la permission de localisation pour voir les vraies antennes.",
            )
        }
        repeat(28) { i ->
            val d = 5f + rand.nextFloat() * 70f
            wifiSources["demo-w$i"] = SignalSource(
                SignalType.WIFI, rand.nextFloat() * 360f, rand.nextFloat() * 50f - 22f,
                12f + (d / 80f) * 18f,
                "${prefixes[rand.nextInt(prefixes.size)]}${rand.nextInt(0x10000).toString(16).uppercase()}",
                "Mode démonstration — Wi-Fi · ≈ ${d.toInt()} m\n" +
                        "Accordez la permission de localisation pour voir les vrais réseaux.",
            )
        }
        if (satSources.isEmpty()) {
            repeat(11) { i ->
                satSources["demo-s$i"] = SignalSource(
                    SignalType.SAT, rand.nextFloat() * 360f, 22f + rand.nextFloat() * 60f, 62f,
                    "Satellite GPS · n° ${1 + rand.nextInt(32)}",
                    "Mode démonstration — sortez à ciel ouvert pour voir les vrais satellites.",
                )
            }
        }
        publish()
    }

    // -------------------------------------------------------------- Cycle de vie

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true

        context.registerReceiver(
            wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        handler.post(wifiScanLoop)
        handler.post(cellLoop)

        try {
            @Suppress("DEPRECATION")
            locationManager.registerGnssStatusCallback(gnssCallback, handler)
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
            provider?.let {
                locationManager.requestLocationUpdates(it, 30_000L, 50f, locationListener)
                locationManager.getLastKnownLocation(it)?.let { loc ->
                    onLocation?.invoke(loc.latitude, loc.longitude)
                }
            }
        } catch (e: SecurityException) {
            // pas de permission : le mode démo prendra le relais
        }

        readScanResults()
        readCells()
        handler.postDelayed({ if (running) buildDemoWorld() }, 12_000)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        try {
            context.unregisterReceiver(wifiReceiver)
        } catch (e: IllegalArgumentException) {
            // déjà désinscrit
        }
        locationManager.unregisterGnssStatusCallback(gnssCallback)
        locationManager.removeUpdates(locationListener)
    }

    private fun publish() {
        onSources(cellSources.values + wifiSources.values + satSources.values)
    }
}
