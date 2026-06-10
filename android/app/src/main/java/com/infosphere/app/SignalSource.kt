package com.infosphere.app

enum class SignalType { CELL, WIFI, SAT }

/**
 * Une source de signal placée sur le dôme céleste.
 *
 * @param azimuthDeg   azimut en degrés (0 = nord, 90 = est)
 * @param elevationDeg élévation en degrés au-dessus de l'horizon
 * @param radius       rayon visuel (les sources proches sont rendues plus près)
 */
data class SignalSource(
    val type: SignalType,
    var azimuthDeg: Float,
    var elevationDeg: Float,
    val radius: Float,
    val title: String,
    val detail: String,
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
