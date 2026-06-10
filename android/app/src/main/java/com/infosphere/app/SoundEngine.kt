package com.infosphere.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ambiance sonore générative, dans l'esprit de l'application originale :
 *  - une nappe grave continue (le « bruit de fond » de l'infosphère)
 *  - un « ping » à chaque impulsion qui atteint le spectateur, dont la
 *    hauteur dépend de la fréquence radio du signal (grave = basses
 *    fréquences type 4G 800 MHz, aigu = Wi-Fi 5 GHz)
 *
 * Les sons sont synthétisés au premier lancement (aucun fichier embarqué).
 */
class SoundEngine(private val context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var pingId = 0
    private var droneId = 0
    private var droneStream = 0
    private val loaded = HashSet<Int>()
    private var lastPingMs = 0L

    var muted = false
        set(value) {
            field = value
            val vol = if (value) 0f else DRONE_VOL
            if (droneStream != 0) pool.setVolume(droneStream, vol, vol)
        }

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loaded.add(sampleId)
                if (sampleId == droneId) startDrone()
            }
        }
        pingId = pool.load(synthToFile("ping.wav", renderPing()), 1)
        droneId = pool.load(synthToFile("drone.wav", renderDrone()), 1)
    }

    private fun startDrone() {
        val vol = if (muted) 0f else DRONE_VOL
        droneStream = pool.play(droneId, vol, vol, 1, -1, 1f)
    }

    /** Joue un ping dont la hauteur dépend de la fréquence radio du signal. */
    fun ping(frequencyMhz: Float) {
        if (muted || pingId !in loaded) return
        val now = System.currentTimeMillis()
        if (now - lastPingMs < 140) return // limite la densité sonore
        lastPingMs = now
        // 0,6× (grave) → 1,9× (aigu) selon la position sur l'échelle des fréquences
        val rate = 0.6f + frequencyNorm(frequencyMhz) * 1.3f
        val vol = 0.10f + Random.nextFloat() * 0.16f
        pool.play(pingId, vol, vol, 0, 0, rate)
    }

    fun pause() = pool.autoPause()

    fun resume() = pool.autoResume()

    fun release() = pool.release()

    // ------------------------------------------------------------- Synthèse

    /** Ping : sinusoïde 880 Hz + harmonique, décroissance exponentielle. */
    private fun renderPing(): ShortArray {
        val n = (SAMPLE_RATE * 0.45f).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 9f)
            val v = sin(2.0 * PI * 880.0 * t) * 0.8 +
                    sin(2.0 * PI * 1760.0 * t) * 0.2
            out[i] = (v * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /**
     * Nappe : trois sinusoïdes graves légèrement battantes. Toutes les
     * composantes comptent un nombre entier de cycles sur la durée pour
     * que la boucle soit parfaitement silencieuse au raccord.
     */
    private fun renderDrone(): ShortArray {
        val seconds = 6
        val n = SAMPLE_RATE * seconds
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val trem = 0.75 + 0.25 * sin(2.0 * PI * t / seconds) // 1 cycle/boucle
            val v = (sin(2.0 * PI * 55.0 * t) * 0.45 +
                    sin(2.0 * PI * 82.5 * t) * 0.30 +
                    sin(2.0 * PI * 110.0 * t) * 0.25) * trem
            out[i] = (v * 0.5 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Écrit les échantillons en WAV PCM 16 bits mono dans le cache. */
    private fun synthToFile(name: String, samples: ShortArray): String {
        val file = File(context.cacheDir, name)
        val dataSize = samples.size * 2
        FileOutputStream(file).use { fos ->
            val header = ByteArray(44)
            fun putAscii(off: Int, s: String) {
                for (k in s.indices) header[off + k] = s[k].code.toByte()
            }
            fun putIntLE(off: Int, v: Int) {
                header[off] = (v and 0xFF).toByte()
                header[off + 1] = ((v shr 8) and 0xFF).toByte()
                header[off + 2] = ((v shr 16) and 0xFF).toByte()
                header[off + 3] = ((v shr 24) and 0xFF).toByte()
            }
            fun putShortLE(off: Int, v: Int) {
                header[off] = (v and 0xFF).toByte()
                header[off + 1] = ((v shr 8) and 0xFF).toByte()
            }
            putAscii(0, "RIFF"); putIntLE(4, 36 + dataSize); putAscii(8, "WAVE")
            putAscii(12, "fmt "); putIntLE(16, 16); putShortLE(20, 1) // PCM
            putShortLE(22, 1) // mono
            putIntLE(24, SAMPLE_RATE); putIntLE(28, SAMPLE_RATE * 2)
            putShortLE(32, 2); putShortLE(34, 16)
            putAscii(36, "data"); putIntLE(40, dataSize)
            fos.write(header)
            val pcm = ByteArray(dataSize)
            for (i in samples.indices) {
                val s = samples[i].toInt()
                pcm[i * 2] = (s and 0xFF).toByte()
                pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            fos.write(pcm)
        }
        return file.absolutePath
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val DRONE_VOL = 0.16f
    }
}
