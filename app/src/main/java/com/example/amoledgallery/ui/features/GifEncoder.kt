package com.example.amoledgallery.ui.features

import android.graphics.Bitmap
import java.io.OutputStream

/**
 * A small animated GIF writer (GIF89a, looping). Every frame uses one fixed 252-colour palette (6 red ×
 * 7 green × 6 blue levels) with ordered dithering, which keeps the encoder simple and fast and looks good for
 * photos at GIF sizes. Frames are LZW-compressed as the format requires.
 */
class GifEncoder(private val out: OutputStream, private val width: Int, private val height: Int, private val delayMs: Int) {
    private val palette = ByteArray(256 * 3).also { p ->
        var i = 0
        for (r in 0 until 6) for (g in 0 until 7) for (b in 0 until 6) {
            p[i * 3] = (r * 255 / 5).toByte()
            p[i * 3 + 1] = (g * 255 / 6).toByte()
            p[i * 3 + 2] = (b * 255 / 5).toByte()
            i++
        }
    }
    private var started = false

    private fun writeShort(v: Int) {
        out.write(v and 0xff)
        out.write((v shr 8) and 0xff)
    }

    private fun header() {
        out.write("GIF89a".toByteArray())
        writeShort(width)
        writeShort(height)
        out.write(0xF7) // global colour table, 8 bits per colour, 256 entries
        out.write(0)
        out.write(0)
        out.write(palette)
        // Netscape extension: loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
        started = true
    }

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Adds [frame], which must be [width] × [height]. */
    fun addFrame(frame: Bitmap) {
        if (!started) header()
        val pixels = IntArray(width * height)
        frame.getPixels(pixels, 0, width, 0, 0, width, height)
        val indices = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                val t = (bayer[(y and 3) * 4 + (x and 3)] - 7.5f) / 16f
                val r = quant((c shr 16) and 0xff, 5, t)
                val g = quant((c shr 8) and 0xff, 6, t)
                val b = quant(c and 0xff, 5, t)
                indices[y * width + x] = (r * 42 + g * 6 + b).toByte()
            }
        }
        // Graphic control extension: the frame's delay.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x00))
        writeShort((delayMs / 10).coerceAtLeast(2))
        out.write(byteArrayOf(0x00, 0x00))
        // Image descriptor, no local colour table.
        out.write(0x2C)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
        out.write(0)
        lzw(indices)
    }

    /** The level (0..[levels]) of a channel value, nudged by the dither [threshold] (-0.5..0.5). */
    private fun quant(v: Int, levels: Int, threshold: Float): Int =
        Math.round(v / 255f * levels + threshold).coerceIn(0, levels)

    fun finish() {
        if (!started) header()
        out.write(0x3B)
        out.flush()
    }

    // ---- LZW --------------------------------------------------------------------------------------------

    private fun lzw(data: ByteArray) {
        val minCodeSize = 8
        out.write(minCodeSize)
        val clear = 1 shl minCodeSize
        val end = clear + 1
        val block = ByteArray(255)
        var blockLen = 0
        var bitBuffer = 0
        var bitCount = 0
        var codeSize = minCodeSize + 1
        var nextCode = end + 1
        val table = HashMap<Int, Int>(8192)

        fun flushBlock() {
            if (blockLen > 0) {
                out.write(blockLen)
                out.write(block, 0, blockLen)
                blockLen = 0
            }
        }
        fun emit(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                block[blockLen++] = (bitBuffer and 0xff).toByte()
                if (blockLen == 255) flushBlock()
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        emit(clear)
        var prefix = data[0].toInt() and 0xff
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xff
            val key = (prefix shl 8) or k
            val existing = table[key]
            if (existing != null) {
                prefix = existing
            } else {
                emit(prefix)
                if (nextCode < 4096) {
                    table[key] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
                } else {
                    emit(clear)
                    table.clear()
                    codeSize = minCodeSize + 1
                    nextCode = end + 1
                }
                prefix = k
            }
        }
        emit(prefix)
        emit(end)
        if (bitCount > 0) {
            block[blockLen++] = (bitBuffer and 0xff).toByte()
            if (blockLen == 255) flushBlock()
        }
        flushBlock()
        out.write(0)
    }
}
