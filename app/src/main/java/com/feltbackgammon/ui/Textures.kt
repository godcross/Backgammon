package com.feltbackgammon.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Procedurally generated, seamlessly tiling textures (no image assets needed). */
object Textures {

    /** Cached textures shared by every screen (generated once, on first use). */
    val feltImage: ImageBitmap by lazy { felt() }
    val woodImage: ImageBitmap by lazy { wood() }
    val barImage: ImageBitmap by lazy { wood(seed = 9L, dark = 0xFF2C170A.toInt(), light = 0xFF6B4221.toInt()) }

    private fun clamp(v: Float) = v.coerceIn(0f, 255f).roundToInt()

    /** Tileable value noise on a [g]x[g] lattice, sampled at pixel ([x],[y]) of a [size] tile. */
    private class Lattice(val g: Int, rnd: Random) {
        val v = FloatArray(g * g) { rnd.nextFloat() * 2f - 1f }
        fun at(x: Float, y: Float, size: Int): Float {
            val gx = x * g / size
            val gy = y * g / size
            val x0 = floor(gx).toInt()
            val y0 = floor(gy).toInt()
            val fx = smooth(gx - x0)
            val fy = smooth(gy - y0)
            fun c(ix: Int, iy: Int) = v[((iy % g + g) % g) * g + ((ix % g + g) % g)]
            val a = c(x0, y0) + (c(x0 + 1, y0) - c(x0, y0)) * fx
            val b = c(x0, y0 + 1) + (c(x0 + 1, y0 + 1) - c(x0, y0 + 1)) * fx
            return a + (b - a) * fy
        }

        private fun smooth(t: Float) = t * t * (3f - 2f * t)
    }

    /** Billiard-cloth style felt: fine speckle, soft mottling and short fibres. */
    fun felt(size: Int = 256, base: Int = 0xFF1E5A35.toInt(), seed: Long = 11L): ImageBitmap {
        val rnd = Random(seed)
        val n = size * size
        val lum = FloatArray(n)
        val low = Lattice(6, rnd)
        val mid = Lattice(24, rnd)
        for (y in 0 until size) for (x in 0 until size) {
            val i = y * size + x
            lum[i] = low.at(x.toFloat(), y.toFloat(), size) * 0.05f +
                mid.at(x.toFloat(), y.toFloat(), size) * 0.03f +
                (rnd.nextGaussian() * 0.035).toFloat()
        }
        // Fibres: short light/dark strokes in random directions, wrapped for tiling.
        repeat(n / 22) {
            val x0 = rnd.nextInt(size)
            val y0 = rnd.nextInt(size)
            val ang = rnd.nextFloat() * PI.toFloat()
            val len = 2 + rnd.nextInt(6)
            val delta = (rnd.nextFloat() - 0.5f) * 0.14f
            for (k in 0 until len) {
                val px = ((x0 + cos(ang) * k).roundToInt() % size + size) % size
                val py = ((y0 + sin(ang) * k).roundToInt() % size + size) % size
                lum[py * size + px] += delta
            }
        }
        val br = ((base shr 16) and 0xFF).toFloat()
        val bg = ((base shr 8) and 0xFF).toFloat()
        val bb = (base and 0xFF).toFloat()
        val px = IntArray(n) { i ->
            val f = 1f + lum[i]
            (0xFF shl 24) or (clamp(br * f) shl 16) or (clamp(bg * f) shl 8) or clamp(bb * f)
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Warm wood with flowing grain lines running along X. */
    fun wood(w: Int = 512, h: Int = 256, seed: Long = 5L, dark: Int = 0xFF3E2210.toInt(), light: Int = 0xFF8C5A2E.toInt()): ImageBitmap {
        val rnd = Random(seed)
        val warp = Lattice(5, rnd)
        val fine = Lattice(64, rnd)
        val rowJitter = FloatArray(h) { (rnd.nextGaussian() * 0.05).toFloat() }
        val dr = ((dark shr 16) and 0xFF).toFloat(); val dg = ((dark shr 8) and 0xFF).toFloat(); val db = (dark and 0xFF).toFloat()
        val lr = ((light shr 16) and 0xFF).toFloat(); val lg = ((light shr 8) and 0xFF).toFloat(); val lb = (light and 0xFF).toFloat()
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val wx = warp.at(x.toFloat(), y.toFloat() * 4f * w / h, w)
            val ring = y.toFloat() / h * 16f + wx * 0.9f + sin(x.toFloat() / w * 2f * PI.toFloat() * 2f) * 0.25f
            var t = ring - floor(ring)
            t = (0.5f + 0.5f * sin(t * 2f * PI.toFloat())).let { it * it }
            t = (t * 0.55f + 0.35f + rowJitter[y] + fine.at(x.toFloat(), y.toFloat() * 6f, w) * 0.05f).coerceIn(0f, 1f)
            val r = dr + (lr - dr) * t
            val g = dg + (lg - dg) * t
            val b = db + (lb - db) * t
            px[y * w + x] = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
