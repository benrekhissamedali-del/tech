package com.infosphere.app

import android.graphics.Color
import kotlin.math.log10

enum class SignalType { CELL, WIFI, SAT }

/**
 * Une source de signal placée sur le dôme céleste.
 *
 * @param azimuthDeg   azimut en degrés (0 = nord, 90 = est)
 * @param elevationDeg élévation en degrés au-dessus de l'horizon
 * @param radius       rayon visuel (les sources proches sont rendues plus près)
 * @param frequencyMhz fréquence radio en MHz (0 si inconnue)
 * @param strength     puissance : dBm (Wi-Fi/antennes) ou dB-Hz (satellites)
 */
data class SignalSource(
    val type: SignalType,
    var azimuthDeg: Float,
    var elevationDeg: Float,
    val radius: Float,
    val title: String,
    val detail: String,
    val frequencyMhz: Float = 0f,
    val strength: Int = 0,
)

/** Azimut pseudo-aléatoire mais stable, dérivé d'un identifiant (BSSID, cell id…). */
fun hashedAzimuth(key: String): Float {
    val h = key.hashCode()
    return ((h % 360) + 360) % 360f
}

/** Seconde valeur stable dans [0,1) dérivée du même identifiant. */
fun hashedUnit(key: String): Float {
    val h = key.hashCode() * -0x61c88647 // mélange (constante de Fibonacci)
    return ((h ushr 8) % 1000) / 1000f
}

private const val FREQ_LOW_MHZ = 700f
private const val FREQ_HIGH_MHZ = 6000f

/** Position 0..1 de la fréquence sur une échelle logarithmique 700 MHz → 6 GHz. */
fun frequencyNorm(mhz: Float): Float {
    if (mhz <= 0f) return 0.5f
    val t = (log10(mhz) - log10(FREQ_LOW_MHZ)) /
            (log10(FREQ_HIGH_MHZ) - log10(FREQ_LOW_MHZ))
    return t.coerceIn(0f, 1f)
}

/**
 * Couleur selon la fréquence : orange (basses fréquences, grandes ondes)
 * → vert → cyan → violet (hautes fréquences).
 */
fun frequencyColor(mhz: Float, fallback: SignalType): Int {
    if (mhz <= 0f) return when (fallback) {
        SignalType.CELL -> Color.rgb(255, 138, 92)
        SignalType.WIFI -> Color.rgb(126, 232, 162)
        SignalType.SAT -> Color.rgb(245, 215, 110)
    }
    val hue = 20f + frequencyNorm(mhz) * 250f
    return Color.HSVToColor(floatArrayOf(hue, 0.72f, 1f))
}
