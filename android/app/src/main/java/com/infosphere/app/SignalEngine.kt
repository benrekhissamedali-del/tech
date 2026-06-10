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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Collecte les vraies sources de signaux autour de l'appareil :
 *  - réseaux Wi-Fi (scan WifiManager, distance estimée par perte en espace libre)
 *  - antennes cellulaires (TelephonyManager.allCellInfo, fréquence via ARFCN)
 *  - satellites GNSS avec azimut/élévation/C-N0/porteuse réels (GnssStatus)
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
            val channel = wifiChannel(r.frequency)
            wifiSources[bssid] = SignalSource(
                type = SignalType.WIFI,
                azimuthDeg = hashedAzimuth(bssid),
                elevationDeg = hashedUnit(bssid) * 50f - 22f,
                radius = 12f + min(1f, distM / 80f) * 18f,
                title = ssid,
                detail = "Wi-Fi $band · canal $channel · ${r.frequency} MHz\n" +
                        "Signal ${r.level} dBm · distance ≈ ${distM.toInt()} m\n" +
                        "Ce routeur émet ~10 trames balises par seconde pour annoncer sa présence.",
                frequencyMhz = r.frequency.toFloat(),
                strength = r.level,
            )
        }
        publish()
    }

    /** Numéro de canal Wi-Fi à partir de la fréquence centrale. */
    private fun wifiChannel(freqMhz: Int): Int = when {
        freqMhz in 2412..2484 -> (freqMhz - 2407) / 5
        freqMhz in 5170..5825 -> (freqMhz - 5000) / 5
        freqMhz >= 5955 -> (freqMhz - 5950) / 5 // Wi-Fi 6E
        else -> 0
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

    private data class ParsedCell(
        val tech: String, val key: String, val dbm: Int, val freqMhz: Float,
    )

    /** Fréquence descendante approximative depuis l'EARFCN (bandes LTE courantes). */
    private fun lteFreqMhz(earfcn: Int): Float = when (earfcn) {
        in 0..599 -> 2110f + 0.1f * earfcn                  // B1 (2100)
        in 1200..1949 -> 1805f + 0.1f * (earfcn - 1200)     // B3 (1800)
        in 2750..3449 -> 2620f + 0.1f * (earfcn - 2750)     // B7 (2600)
        in 3450..3799 -> 925f + 0.1f * (earfcn - 3450)      // B8 (900)
        in 6150..6449 -> 791f + 0.1f * (earfcn - 6150)      // B20 (800)
        in 9210..9659 -> 758f + 0.1f * (earfcn - 9210)      // B28 (700)
        else -> 1800f
    }

    /** Fréquence depuis le NR-ARFCN (raster global 3GPP). */
    private fun nrFreqMhz(nrarfcn: Int): Float = when {
        nrarfcn in 1..599_999 -> nrarfcn * 0.005f
        nrarfcn in 600_000..2_016_666 -> 3000f + (nrarfcn - 600_000) * 0.015f
        else -> 3500f
    }

    private fun parseCell(info: CellInfo): ParsedCell? {
        if (info is CellInfoLte) {
            val earfcn = info.cellIdentity.earfcn
            val freq = if (earfcn in 0..70_000) lteFreqMhz(earfcn) else 1800f
            return ParsedCell(
                "4G LTE", "lte-${info.cellIdentity.ci}", info.cellSignalStrength.dbm, freq
            )
        }
        if (Build.VERSION.SDK_INT >= 29 && info is CellInfoNr) {
            val id = (info.cellIdentity as? CellIdentityNr) ?: return null
            return ParsedCell(
                "5G NR", "nr-${id.nci}", info.cellSignalStrength.dbm, nrFreqMhz(id.nrarfcn)
            )
        }
        if (info is CellInfoWcdma) {
            val uarfcn = info.cellIdentity.uarfcn
            val freq = if (uarfcn in 400..11_000) uarfcn * 0.2f else 2100f
            return ParsedCell(
                "3G UMTS", "wcdma-${info.cellIdentity.cid}", info.cellSignalStrength.dbm, freq
            )
        }
        if (info is CellInfoGsm) {
            val arfcn = info.cellIdentity.arfcn
            val freq = when (arfcn) {
                in 1..124 -> 935f + 0.2f * (arfcn - 1)        // GSM 900
                in 512..885 -> 1805.2f + 0.2f * (arfcn - 512) // DCS 1800
                else -> 900f
            }
            return ParsedCell(
                "2G GSM", "gsm-${info.cellIdentity.cid}", info.cellSignalStrength.dbm, freq
            )
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
            val served = if (info.isRegistered) "\n✓ Antenne de rattachement de votre téléphone" else ""
            cellSources[cell.key] = SignalSource(
                type = SignalType.CELL,
                azimuthDeg = hashedAzimuth(cell.key),
                elevationDeg = 1f + (1f - distM / 3000f) * 5f,
                radius = 30f + (distM / 3000f) * 28f,
                title = "Antenne $operator",
                detail = "${cell.tech} · ${cell.freqMhz.toInt()} MHz\n" +
                        "Signal ${cell.dbm} dBm · distance ≈ ${distM.toInt()} m$served\n" +
                        "Station de base du réseau mobile. Votre téléphone lui parle " +
                        "en permanence, même en veille.",
                frequencyMhz = cell.freqMhz,
                strength = cell.dbm,
            )
        }
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
                val type = status.getConstellationType(i)
                val cons = constellationName(type)
                val svid = status.getSvid(i)
                val az = status.getAzimuthDegrees(i)
                val cn0 = status.getCn0DbHz(i)
                val freqMhz = if (status.hasCarrierFrequencyHz(i)) {
                    status.getCarrierFrequencyHz(i) / 1e6f
                } else {
                    defaultCarrierMhz(type)
                }
                val eph = if (status.hasEphemerisData(i)) "oui" else "non"
                val alm = if (status.hasAlmanacData(i)) "oui" else "non"
                val used = if (status.usedInFix(i)) {
                    "✓ Utilisé pour calculer votre position"
                } else {
                    "Visible mais non utilisé dans le calcul"
                }
                satSources["$cons-$svid"] = SignalSource(
                    type = SignalType.SAT,
                    azimuthDeg = az,
                    elevationDeg = el,
                    radius = 62f,
                    title = "Satellite $cons · n° $svid",
                    detail = "Constellation $cons · identifiant SVID $svid\n" +
                            "Élévation ${el.toInt()}° · azimut ${az.toInt()}° (position réelle)\n" +
                            "Rapport signal/bruit C/N₀ : ${cn0.toInt()} dB-Hz\n" +
                            "Porteuse : ${"%.2f".format(freqMhz)} MHz (bande ${gnssBand(freqMhz)})\n" +
                            "Éphémérides : $eph · Almanach : $alm\n" +
                            "$used\n" +
                            "Orbite : ${orbitInfo(type)}\n" +
                            "Son signal transporte l'heure atomique et met ~70 ms à vous atteindre.",
                    frequencyMhz = freqMhz,
                    strength = cn0.toInt(),
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

    private fun defaultCarrierMhz(type: Int): Float = when (type) {
        GnssStatus.CONSTELLATION_GLONASS -> 1602f
        GnssStatus.CONSTELLATION_BEIDOU -> 1561.1f
        else -> 1575.42f // L1/E1
    }

    private fun gnssBand(mhz: Float): String = when {
        abs(mhz - 1575.42f) < 15f -> "L1 / E1"
        abs(mhz - 1176.45f) < 15f -> "L5 / E5a"
        abs(mhz - 1207.14f) < 10f -> "E5b / B2"
        abs(mhz - 1227.6f) < 10f -> "L2"
        abs(mhz - 1602f) < 10f -> "G1 (GLONASS)"
        abs(mhz - 1561.1f) < 8f -> "B1 (BeiDou)"
        else -> "GNSS"
    }

    private fun orbitInfo(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "altitude 20 180 km · période 11 h 58 min · ~3,9 km/s"
        GnssStatus.CONSTELLATION_GLONASS -> "altitude 19 130 km · période 11 h 16 min"
        GnssStatus.CONSTELLATION_GALILEO -> "altitude 23 222 km · période 14 h 05 min"
        GnssStatus.CONSTELLATION_BEIDOU -> "altitude 21 528 km · période 12 h 53 min"
        GnssStatus.CONSTELLATION_QZSS -> "orbite géosynchrone inclinée ~36 000 km"
        GnssStatus.CONSTELLATION_SBAS -> "orbite géostationnaire 35 786 km"
        GnssStatus.CONSTELLATION_IRNSS -> "orbite géosynchrone ~36 000 km"
        else -> "orbite moyenne ~20 000 km"
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
        val rand = Random(0xC0FFEE)
        val operators = listOf("Orange", "SFR", "Bouygues Telecom", "Free Mobile")
        val prefixes = listOf("Livebox-", "Freebox-", "SFR_", "Bbox-", "TP-Link_")
        val cellFreqs = listOf(811f, 945f, 1842f, 2655f, 3551f)
        repeat(10) { i ->
            val d = 150f + rand.nextFloat() * 2850f
            val freq = cellFreqs[rand.nextInt(cellFreqs.size)]
            val dbm = -65 - rand.nextInt(40)
            cellSources["demo-c$i"] = SignalSource(
                SignalType.CELL, rand.nextFloat() * 360f, 1f + rand.nextFloat() * 5f,
                30f + (d / 3000f) * 28f,
                "Antenne ${operators[rand.nextInt(operators.size)]}",
                "Mode démonstration — 4G · ${freq.toInt()} MHz · $dbm dBm · ≈ ${d.toInt()} m\n" +
                        "Accordez la permission de localisation pour voir les vraies antennes.",
                freq, dbm,
            )
        }
        repeat(28) { i ->
            val d = 5f + rand.nextFloat() * 70f
            val freq = if (rand.nextBoolean()) 2412f + rand.nextInt(13) * 5f
            else 5180f + rand.nextInt(24) * 20f
            val dbm = -40 - rand.nextInt(50)
            wifiSources["demo-w$i"] = SignalSource(
                SignalType.WIFI, rand.nextFloat() * 360f, rand.nextFloat() * 50f - 22f,
                12f + (d / 80f) * 18f,
                "${prefixes[rand.nextInt(prefixes.size)]}${rand.nextInt(0x10000).toString(16).uppercase()}",
                "Mode démonstration — Wi-Fi · ${freq.toInt()} MHz · $dbm dBm · ≈ ${d.toInt()} m\n" +
                        "Accordez la permission de localisation pour voir les vrais réseaux.",
                freq, dbm,
            )
        }
        if (satSources.isEmpty()) {
            repeat(11) { i ->
                val cn0 = 18 + rand.nextInt(30)
                satSources["demo-s$i"] = SignalSource(
                    SignalType.SAT, rand.nextFloat() * 360f, 22f + rand.nextFloat() * 60f, 62f,
                    "Satellite GPS · n° ${1 + rand.nextInt(32)}",
                    "Mode démonstration — L1 1575,42 MHz · C/N₀ $cn0 dB-Hz\n" +
                            "Sortez à ciel ouvert pour voir les vrais satellites.",
                    1575.42f, cn0,
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
