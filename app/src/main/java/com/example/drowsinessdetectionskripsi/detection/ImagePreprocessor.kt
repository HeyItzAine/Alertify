package com.example.drowsinessdetectionskripsi.detection

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Ultra-optimized image preprocessing algorithms.
 * Key optimizations:
 * - Histogram sampling (4x fewer pixels analyzed)
 * - Reusable buffers (zero allocations per frame)
 * - Integer-only math (no floats in hot paths)
 * - Simplified BPDHE (no Gaussian smoothing)
 * - Single-pass LUT application
 */
enum class PreprocessingType(val displayName: String, val description: String, val icon: String) {
    NONE("None", "Raw camera input", "\uD83D\uDD33"),
    GAMMA("Gamma Correction", "\u03B3 = 0.45", "\u2600\uFE0F"),
    HISTOGRAM_EQ("Histogram Equalization", "CDF redistribution", "\uD83D\uDCCA"),
    CLAHE("CLAHE", "clip 2.0, tile 2\u00D72", "\uD83D\uDD32"),
    BPDHE("BPDHE", "Brightness-preserving HE", "\uD83C\uDF17");
}

object ImagePreprocessor {

    // Pre-computed gamma LUT (γ = 0.45)
    private val gammaLut = IntArray(256) { i ->
        ((i / 255.0).pow(0.45) * 255 + 0.5).toInt().coerceIn(0, 255)
    }

    // Reusable per-frame pixel buffers
    private var pixels: IntArray? = null
    private var channel1: IntArray? = null
    private var channel2: IntArray? = null
    private var channel3: IntArray? = null
    // Double-buffered output: the bitmap Compose is displaying is never the
    // one being overwritten by the next frame's setPixels
    private var outBitmapA: Bitmap? = null
    private var outBitmapB: Bitmap? = null
    private var outFlip = false
    private var bufferSize = 0

    // Reusable fixed-size work arrays (never reallocated after first use)
    private val hist   = IntArray(256)
    private val lut    = IntArray(256)
    private val lut2   = IntArray(256)   // second LUT slot (CLAHE tiles)
    private val lut3   = IntArray(256)
    private val lut4   = IntArray(256)
    private val cdf    = IntArray(256)
    private val peaks  = IntArray(8)
    private val bounds = IntArray(10)

    private fun ensureBuffers(size: Int) {
        if (size != bufferSize) {
            pixels = IntArray(size)
            channel1 = IntArray(size)
            channel2 = IntArray(size)
            channel3 = IntArray(size)
            bufferSize = size
        }
    }

    private fun getOutBitmap(w: Int, h: Int): Bitmap {
        outFlip = !outFlip
        val bmp = if (outFlip) outBitmapA else outBitmapB
        if (bmp != null && bmp.width == w && bmp.height == h && !bmp.isRecycled) return bmp
        val fresh = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (outFlip) outBitmapA = fresh else outBitmapB = fresh
        return fresh
    }

    fun process(bitmap: Bitmap, type: PreprocessingType): Bitmap {
        return when (type) {
            PreprocessingType.NONE -> bitmap
            PreprocessingType.GAMMA -> applyGamma(bitmap)
            PreprocessingType.HISTOGRAM_EQ -> applyHistogramEq(bitmap)
            PreprocessingType.CLAHE -> applyClahe(bitmap)
            PreprocessingType.BPDHE -> applyBpdhe(bitmap)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GAMMA - Simple LUT, very fast
    // ═══════════════════════════════════════════════════════════════
    private fun applyGamma(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val size = w * h
        ensureBuffers(size)
        val px = pixels!!

        src.getPixels(px, 0, w, 0, 0, w, h)

        for (i in 0 until size) {
            val p = px[i]
            px[i] = -0x1000000 or
                    (gammaLut[(p shr 16) and 0xFF] shl 16) or
                    (gammaLut[(p shr 8) and 0xFF] shl 8) or
                    gammaLut[p and 0xFF]
        }

        val out = getOutBitmap(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ═══════════════════════════════════════════════════════════════
    // HISTOGRAM EQ - Sampled histogram for speed
    // ═══════════════════════════════════════════════════════════════
    private fun applyHistogramEq(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val size = w * h
        ensureBuffers(size)
        val px = pixels!!
        val y = channel1!!
        val cb = channel2!!
        val cr = channel3!!

        src.getPixels(px, 0, w, 0, 0, w, h)

        // Extract Y and build sampled histogram (every 4th pixel)
        hist.fill(0)
        var sampleCount = 0
        for (i in 0 until size) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val yVal = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
            y[i] = yVal
            cb[i] = (128 + ((-43 * r - 85 * g + 128 * b) shr 8)).coerceIn(0, 255)
            cr[i] = (128 + ((128 * r - 107 * g - 21 * b) shr 8)).coerceIn(0, 255)

            // Sample every 4th pixel for histogram
            if ((i and 3) == 0) {
                hist[yVal]++
                sampleCount++
            }
        }

        // Build equalization LUT
        var sum = 0
        var cdfMin = 0
        for (i in 0..255) {
            sum += hist[i]
            if (cdfMin == 0 && sum > 0) cdfMin = sum
            lut[i] = sum
        }

        if (sampleCount > cdfMin) {
            val scale = 255f / (sampleCount - cdfMin)
            for (i in 0..255) {
                lut[i] = ((lut[i] - cdfMin) * scale).toInt().coerceIn(0, 255)
            }
        }

        // Apply LUT and convert to RGB
        for (i in 0 until size) {
            val yNew = lut[y[i]]
            val cbV = cb[i] - 128
            val crV = cr[i] - 128

            val r = (yNew + ((359 * crV) shr 8)).coerceIn(0, 255)
            val g = (yNew - ((88 * cbV + 183 * crV) shr 8)).coerceIn(0, 255)
            val b = (yNew + ((454 * cbV) shr 8)).coerceIn(0, 255)

            px[i] = -0x1000000 or (r shl 16) or (g shl 8) or b
        }

        val out = getOutBitmap(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ═══════════════════════════════════════════════════════════════
    // CLAHE - 2x2 tiles for speed, sampled histograms
    // ═══════════════════════════════════════════════════════════════
    private fun applyClahe(src: Bitmap, clipLimit: Float = 2.0f): Bitmap {
        val w = src.width
        val h = src.height
        val size = w * h
        ensureBuffers(size)
        val px = pixels!!
        val y = channel1!!
        val cb = channel2!!
        val cr = channel3!!

        src.getPixels(px, 0, w, 0, 0, w, h)

        // Extract Y channel
        for (i in 0 until size) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            y[i] = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
            cb[i] = (128 + ((-43 * r - 85 * g + 128 * b) shr 8)).coerceIn(0, 255)
            cr[i] = (128 + ((128 * r - 107 * g - 21 * b) shr 8)).coerceIn(0, 255)
        }

        // 2x2 tiles for speed
        val gridW = 2
        val gridH = 2
        val tileW = w / gridW
        val tileH = h / gridH

        // Build LUT for each tile using reusable singleton arrays
        // 2x2 grid → lut=tile(0,0), lut2=tile(0,1), lut3=tile(1,0), lut4=tile(1,1)
        val tileLuts = arrayOf(lut, lut2, lut3, lut4)
        for (ty in 0 until gridH) {
            for (tx in 0 until gridW) {
                val x0 = tx * tileW
                val y0 = ty * tileH
                val x1 = if (tx == gridW - 1) w else (tx + 1) * tileW
                val y1 = if (ty == gridH - 1) h else (ty + 1) * tileH
                val tileLut = tileLuts[ty * gridW + tx]

                hist.fill(0)
                var samples = 0
                for (row in y0 until y1 step 2) {
                    val rowOff = row * w
                    for (col in x0 until x1 step 2) {
                        hist[y[rowOff + col]]++
                        samples++
                    }
                }

                val limit = max(1, (clipLimit * samples / 256).toInt())
                var excess = 0
                for (i in 0..255) {
                    if (hist[i] > limit) { excess += hist[i] - limit; hist[i] = limit }
                }
                val bonus = excess / 256
                for (i in 0..255) hist[i] += bonus

                var cumSum = 0
                var cdfMin = 0
                for (i in 0..255) {
                    cumSum += hist[i]
                    if (cdfMin == 0 && cumSum > 0) cdfMin = cumSum
                    tileLut[i] = cumSum
                }
                val denom = samples - cdfMin
                if (denom > 0) {
                    for (i in 0..255) {
                        tileLut[i] = ((tileLut[i] - cdfMin) * 255 / denom).coerceIn(0, 255)
                    }
                }
            }
        }

        // Simple bilinear interpolation with integer math
        val halfW = tileW / 2
        val halfH = tileH / 2

        for (py in 0 until h) {
            val rowOff = py * w
            for (pxCoord in 0 until w) {
                val v = y[rowOff + pxCoord]

                // Determine tile and blend factors
                val tx0 = if (pxCoord < halfW) 0 else if (pxCoord >= w - halfW) gridW - 1 else (pxCoord - halfW) / tileW
                val ty0 = if (py < halfH) 0 else if (py >= h - halfH) gridH - 1 else (py - halfH) / tileH
                val tx1 = (tx0 + 1).coerceAtMost(gridW - 1)
                val ty1 = (ty0 + 1).coerceAtMost(gridH - 1)

                val fx = ((pxCoord - halfW - tx0 * tileW) * 256 / tileW).coerceIn(0, 256)
                val fy = ((py - halfH - ty0 * tileH) * 256 / tileH).coerceIn(0, 256)

                val v00 = tileLuts[ty0 * gridW + tx0][v]
                val v10 = tileLuts[ty0 * gridW + tx1][v]
                val v01 = tileLuts[ty1 * gridW + tx0][v]
                val v11 = tileLuts[ty1 * gridW + tx1][v]

                val top = v00 * (256 - fx) + v10 * fx
                val bot = v01 * (256 - fx) + v11 * fx
                y[rowOff + pxCoord] = ((top * (256 - fy) + bot * fy) shr 16).coerceIn(0, 255)
            }
        }

        // Convert back to RGB
        for (i in 0 until size) {
            val yNew = y[i]
            val cbV = cb[i] - 128
            val crV = cr[i] - 128

            val r = (yNew + ((359 * crV) shr 8)).coerceIn(0, 255)
            val g = (yNew - ((88 * cbV + 183 * crV) shr 8)).coerceIn(0, 255)
            val b = (yNew + ((454 * cbV) shr 8)).coerceIn(0, 255)

            px[i] = -0x1000000 or (r shl 16) or (g shl 8) or b
        }

        val out = getOutBitmap(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ═══════════════════════════════════════════════════════════════
    // BPDHE - Simplified: no Gaussian, sampled histogram, fast HSV
    // ═══════════════════════════════════════════════════════════════
    private fun applyBpdhe(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val size = w * h
        ensureBuffers(size)
        val px = pixels!!
        val vChan = channel1!!
        val hue = channel2!!
        val sat = channel3!!

        src.getPixels(px, 0, w, 0, 0, w, h)

        // Extract V channel (brightness) and compute mean
        var vSum = 0L
        for (i in 0 until size) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC

            vChan[i] = maxC
            vSum += maxC

            sat[i] = if (maxC > 0) (delta * 255 / maxC) else 0

            hue[i] = if (delta == 0) 0
            else if (maxC == r) ((g - b) * 60 / delta + 360) % 360
            else if (maxC == g) (b - r) * 60 / delta + 120
            else (r - g) * 60 / delta + 240
        }

        val origMean = (vSum / size).toInt()

        // Build sampled histogram (every 4th pixel)
        hist.fill(0)
        for (i in 0 until size step 4) {
            hist[vChan[i]]++
        }

        // Find peaks directly (no Gaussian smoothing)
        var histTotal = 0
        for (v in hist) histTotal += v
        val threshold = histTotal / 256 / 4
        var peakCount = 0
        for (i in 2..253) {
            if (peakCount < 8 && hist[i] > threshold &&
                hist[i] >= hist[i-1] && hist[i] >= hist[i+1] &&
                hist[i] >= hist[i-2] && hist[i] >= hist[i+2]) {
                peaks[peakCount++] = i
            }
        }

        // Build partition boundaries
        var boundCount = 0
        bounds[boundCount++] = 0
        if (peakCount < 2) {
            bounds[boundCount++] = 128
        } else {
            for (i in 0 until peakCount - 1) {
                bounds[boundCount++] = (peaks[i] + peaks[i + 1]) / 2
            }
        }
        bounds[boundCount++] = 256

        // Build global LUT from partitions (using sampled histogram, reusing singleton arrays)
        for (i in 0..255) lut[i] = i
        for (p in 0 until boundCount - 1) {
            val lo = bounds[p]
            val hi = bounds[p + 1]
            if (hi <= lo + 1) continue

            // Build CDF for this partition into reusable cdf array (offset by lo)
            var cumSum = 0
            var cdfMin = 0
            for (i in lo until hi) {
                cumSum += hist[i]
                if (cdfMin == 0 && cumSum > 0) cdfMin = cumSum
                cdf[i - lo] = cumSum
            }

            val total = cumSum
            if (total > cdfMin) {
                val range = hi - lo - 1
                for (i in lo until hi) {
                    lut[i] = ((cdf[i - lo] - cdfMin) * range / (total - cdfMin) + lo).coerceIn(0, 255)
                }
            }
        }

        // Apply LUT and compute new mean
        var newSum = 0L
        for (i in 0 until size) {
            vChan[i] = lut[vChan[i]]
            newSum += vChan[i]
        }

        // Brightness preservation scaling
        val newMean = (newSum / size).toInt()
        if (newMean > 0) {
            val scale = (origMean * 256) / newMean
            for (i in 0 until size) {
                vChan[i] = ((vChan[i] * scale) shr 8).coerceIn(0, 255)
            }
        }

        // Convert HSV back to RGB
        for (i in 0 until size) {
            val hVal = hue[i]
            val s = sat[i]
            val v = vChan[i]

            if (s == 0) {
                px[i] = -0x1000000 or (v shl 16) or (v shl 8) or v
            } else {
                val sector = hVal / 60
                val f = hVal % 60

                val pv = (v * (255 - s)) / 255
                val qv = (v * (255 - s * f / 60)) / 255
                val tv = (v * (255 - s * (60 - f) / 60)) / 255

                val r: Int; val g: Int; val b: Int
                when (sector) {
                    0 -> { r = v;  g = tv; b = pv }
                    1 -> { r = qv; g = v;  b = pv }
                    2 -> { r = pv; g = v;  b = tv }
                    3 -> { r = pv; g = qv; b = v  }
                    4 -> { r = tv; g = pv; b = v  }
                    else -> { r = v; g = pv; b = qv }
                }
                px[i] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }

        val out = getOutBitmap(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
