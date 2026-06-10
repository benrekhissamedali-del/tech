package com.infosphere.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Vue 360° du dôme de signaux, pilotée par le capteur de rotation
 * (gyroscope + boussole) avec glissement tactile en secours.
 */
class SkyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), SensorEventListener {

    var onCompass: ((Int) -> Unit)? = null
    var onTap: ((SignalSource?) -> Unit)? = null

    private var sources: List<SignalSource> = emptyList()

    fun setSources(list: List<SignalSource>) {
        sources = list
        worldDirty = true
    }

    // ------------------------------------------------------------ Caméra

    private val fov = (70.0 * PI / 180.0).toFloat()
    private var yaw = 0f          // cap en radians (0 = nord)
    private var pitch = 0f
    private var targetYaw = 0f
    private var targetPitch = 0f
    private var dragYawOffset = 0f
    private var dragPitchOffset = 0f
    private var hasSensor = false

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        postInvalidateOnAnimation()
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
        // Téléphone tenu verticalement : l'axe de visée est -Z (dos de l'appareil).
        SensorManager.remapCoordinateSystem(
            rotMatrix, SensorManager.AXIS_X, SensorManager.AXIS_MINUS_Z, remapped
        )
        SensorManager.getOrientation(remapped, orientation)
        hasSensor = true
        targetYaw = orientation[0] + dragYawOffset
        targetPitch = (-orientation[1] + dragPitchOffset).coerceIn(-1.45f, 1.45f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ------------------------------------------------------------ Tactile

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                downX = e.x; downY = e.y
                downTime = e.eventTime
            }
            MotionEvent.ACTION_MOVE -> {
                val k = fov / height
                val dYaw = -(e.x - lastX) * k
                val dPitch = (e.y - lastY) * k
                if (hasSensor) {
                    dragYawOffset += dYaw
                    dragPitchOffset += dPitch
                } else {
                    targetYaw += dYaw
                    targetPitch = (targetPitch + dPitch).coerceIn(-1.45f, 1.45f)
                }
            }
            MotionEvent.ACTION_UP -> {
                val isTap = e.eventTime - downTime < 350 &&
                        hypot(e.x - downX, e.y - downY) < 24f
                if (isTap) handleTap(e.x, e.y)
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        if (worldDirty || projectedVisible.size != sources.size) return
        var best: SignalSource? = null
        var bestDist = 56f * resources.displayMetrics.density
        for (i in sources.indices) {
            if (projectedVisible[i] != 1) continue
            val d = hypot(projectedX[i] - x, projectedY[i] - y)
            if (d < bestDist) {
                bestDist = d
                best = sources[i]
            }
        }
        onTap?.invoke(best)
    }

    // ------------------------------------------------------------ Monde

    private class Pulse(var srcIndex: Int, var t: Float, var speed: Float)

    private val domeDirs: FloatArray = buildDome()
    private var worldX = FloatArray(0)
    private var worldY = FloatArray(0)
    private var worldZ = FloatArray(0)
    private var projectedX = FloatArray(0)
    private var projectedY = FloatArray(0)
    private var projectedDepth = FloatArray(0)
    private var projectedVisible = IntArray(0)
    private var worldDirty = true
    private val pulses = ArrayList<Pulse>()
    private var lastFrameNanos = 0L
    private var lastCompassDeg = -1

    private fun buildDome(): FloatArray {
        val pts = ArrayList<Float>()
        var el = -30
        while (el <= 80) {
            val n = max(8, (64 * cos(el * DEG)).roundToInt())
            for (i in 0 until n) {
                val az = (i.toFloat() / n) * 360f + el * 1.7f
                pts.add(cos(el * DEG) * sin(az * DEG) * 70f)
                pts.add(sin(el * DEG) * 70f)
                pts.add(cos(el * DEG) * cos(az * DEG) * 70f)
            }
            el += 9
        }
        return pts.toFloatArray()
    }

    private fun rebuildWorld() {
        val n = sources.size
        worldX = FloatArray(n); worldY = FloatArray(n); worldZ = FloatArray(n)
        projectedX = FloatArray(n); projectedY = FloatArray(n)
        projectedDepth = FloatArray(n); projectedVisible = IntArray(n)
        for (i in 0 until n) {
            val s = sources[i]
            val az = s.azimuthDeg * DEG
            val el = s.elevationDeg * DEG
            worldX[i] = s.radius * cos(el) * sin(az)
            worldY[i] = s.radius * sin(el)
            worldZ[i] = s.radius * cos(el) * cos(az)
        }
        pulses.removeAll { it.srcIndex >= n }
        worldDirty = false
    }

    // ------------------------------------------------------------ Rendu

    private val bgColor = Color.rgb(16, 16, 107)
    private val dotPaint = Paint().apply { color = Color.WHITE }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shardPath = Path()
    private val proj = FloatArray(3)

    private fun colorFor(type: SignalType): Int = when (type) {
        SignalType.CELL -> Color.rgb(255, 138, 92)
        SignalType.WIFI -> Color.rgb(126, 232, 162)
        SignalType.SAT -> Color.rgb(245, 215, 110)
    }

    private fun sizeFor(type: SignalType): Float = when (type) {
        SignalType.CELL -> 5f
        SignalType.WIFI -> 3.5f
        SignalType.SAT -> 4f
    }

    /**
     * Projette un point monde sur l'écran. Résultat dans [proj] :
     * x, y, profondeur. Renvoie false si le point est derrière la caméra.
     */
    private fun project(px: Float, py: Float, pz: Float, w: Float, h: Float): Boolean {
        val cy = cos(-yaw); val sy = sin(-yaw)
        val x1 = px * cy + pz * sy
        val z1 = -px * sy + pz * cy
        val cp = cos(-pitch); val sp = sin(-pitch)
        val y2 = py * cp - z1 * sp
        val z2 = py * sp + z1 * cp
        if (z2 <= 0.1f) return false
        val f = (h / 2f) / tan(fov / 2f)
        proj[0] = w / 2f + (x1 / z2) * f
        proj[1] = h / 2f - (y2 / z2) * f
        proj[2] = z2
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.016f
        else min(0.05f, (now - lastFrameNanos) / 1e9f)
        lastFrameNanos = now
        val time = now / 1e9f

        if (worldDirty) rebuildWorld()

        canvas.drawColor(bgColor)

        // Lissage de la caméra.
        var dYaw = targetYaw - yaw
        while (dYaw > PI) dYaw -= (2 * PI).toFloat()
        while (dYaw < -PI) dYaw += (2 * PI).toFloat()
        yaw += dYaw * min(1f, dt * 8f)
        pitch += (targetPitch - pitch) * min(1f, dt * 8f)

        // Dôme de points.
        var i = 0
        while (i < domeDirs.size) {
            if (project(domeDirs[i], domeDirs[i + 1], domeDirs[i + 2], w, h)) {
                val depth = proj[2]
                val r = max(1f, 90f / depth)
                dotPaint.alpha = (min(0.5f, 28f / depth) * 255).toInt()
                canvas.drawRect(proj[0], proj[1], proj[0] + r, proj[1] + r, dotPaint)
            }
            i += 3
        }

        // Sources.
        val density = resources.displayMetrics.density
        for (s in sources.indices) {
            if (!project(worldX[s], worldY[s], worldZ[s], w, h)) {
                projectedVisible[s] = 0
                continue
            }
            projectedVisible[s] = 1
            projectedX[s] = proj[0]
            projectedY[s] = proj[1]
            projectedDepth[s] = proj[2]

            val src = sources[s]
            val color = colorFor(src.type)
            val scale = 40f / proj[2] * density
            val pulse = 0.6f + 0.4f * sin(time * 2f + src.azimuthDeg)

            haloPaint.color = color
            haloPaint.alpha = 34
            canvas.drawCircle(
                proj[0], proj[1], sizeFor(src.type) * (1.6f + pulse) * scale, haloPaint
            )
            nodePaint.color = color
            canvas.drawCircle(
                proj[0], proj[1], max(1.5f, sizeFor(src.type) * scale), nodePaint
            )
        }

        // Impulsions : éclats blancs voyageant des sources vers le spectateur.
        if (sources.isNotEmpty() && Math.random() < dt * 14) {
            pulses.add(
                Pulse(
                    (Math.random() * sources.size).toInt(),
                    0f,
                    0.25f + Math.random().toFloat() * 0.5f,
                )
            )
        }
        var p = pulses.size - 1
        while (p >= 0) {
            val pu = pulses[p]
            pu.t += dt * pu.speed
            if (pu.t >= 1f || pu.srcIndex >= sources.size) {
                pulses.removeAt(p); p--; continue
            }
            val k = pu.t
            val ok = project(
                worldX[pu.srcIndex] * (1 - k),
                worldY[pu.srcIndex] * (1 - k) - 2f * k,
                worldZ[pu.srcIndex] * (1 - k),
                w, h,
            )
            if (ok) {
                val ang = atan2(h / 2f - proj[1], w / 2f - proj[0])
                val len = min(46f, 260f / proj[2]) * (0.5f + k) * density
                shardPaint.alpha = (216 * sin(PI * k).toFloat()).toInt().coerceIn(0, 255)
                shardPath.reset()
                val c = cos(ang); val sn = sin(ang)
                shardPath.moveTo(proj[0] + len * c, proj[1] + len * sn)
                shardPath.lineTo(
                    proj[0] - len * 0.4f * c + len * 0.16f * sn,
                    proj[1] - len * 0.4f * sn - len * 0.16f * c,
                )
                shardPath.lineTo(
                    proj[0] - len * 0.4f * c - len * 0.16f * sn,
                    proj[1] - len * 0.4f * sn + len * 0.16f * c,
                )
                shardPath.close()
                canvas.drawPath(shardPath, shardPaint)
            }
            p--
        }

        // Boussole (notification seulement quand le degré affiché change).
        val deg = ((yaw / DEG).roundToInt() % 360 + 360) % 360
        if (deg != lastCompassDeg) {
            lastCompassDeg = deg
            onCompass?.invoke(deg)
        }

        postInvalidateOnAnimation()
    }

    private companion object {
        val DEG = (PI / 180.0).toFloat()
    }
}
