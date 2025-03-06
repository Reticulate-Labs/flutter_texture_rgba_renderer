package com.plugin.texture_rgba_renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Surface
import io.flutter.view.TextureRegistry
import java.nio.ByteBuffer

class TextureRenderer(private val textureRegistry: TextureRegistry) {
    val textureId: Long
    private val surfaceProducer: TextureRegistry.SurfaceProducer
    private val surface: Surface
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0
    private var bitmap: Bitmap? = null

    init {
        surfaceProducer = textureRegistry.createSurfaceProducer()
        surface = surfaceProducer.getSurface()
        textureId = surfaceProducer.id()
        println("TextureRenderer: Initialized with textureId=$textureId")
    }

    fun updateFrame(data: ByteArray, width: Int, height: Int, stride: Int) {
        if (!surface.isValid) {
            println("TextureRenderer: Surface is not valid")
            return
        }
        println("TextureRenderer: updateFrame called with width=$width, height=$height, dataSize=${data.size}, stride=$stride")

        // Check data size
        val expectedSize = stride * height
        if (data.size < expectedSize) {
            println("TextureRenderer: Data size too small: ${data.size} < $expectedSize")
            return
        }

        // Resize if necessary
        if (currentWidth != width || currentHeight != height) {
            surfaceProducer.setSize(width, height)
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            currentWidth = width
            currentHeight = height
            println("TextureRenderer: Resized surface and bitmap to $width x $height")
        }

        // Convert BGRA to ARGB and update bitmap
        val bitmapData = convertBgraToArgb(data, width, height, stride)
        bitmap?.setPixels(bitmapData, 0, width, 0, 0, width, height)
        println("TextureRenderer: Set bitmap pixels")

        // Draw to surface
        val canvas = try {
            surface.lockCanvas(null)
        } catch (e: Exception) {
            println("TextureRenderer: Failed to lock canvas: ${e.message}")
            return
        }
        try {
            canvas.drawBitmap(bitmap!!, 0f, 0f, null)
            println("TextureRenderer: Drew bitmap to canvas")
        } catch (e: Exception) {
            println("TextureRenderer: Failed to draw bitmap: ${e.message}")
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
                println("TextureRenderer: Unlocked and posted canvas")
            } catch (e: Exception) {
                println("TextureRenderer: Failed to unlock/post canvas: ${e.message}")
            }
        }
    }

    fun dispose() {
        bitmap?.recycle()
        surfaceProducer.release()
        surface.release()
        println("TextureRenderer: Disposed")
    }

    private fun convertBgraToArgb(data: ByteArray, width: Int, height: Int, stride: Int): IntArray {
        val pixels = IntArray(width * height)
        val buffer = ByteBuffer.wrap(data)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * stride + x * 4
                if (offset + 3 >= data.size) {
                    println("TextureRenderer: Buffer overflow at offset=$offset, dataSize=${data.size}")
                    continue
                }
                val b = data[offset].toInt() and 0xFF
                val g = data[offset + 1].toInt() and 0xFF
                val r = data[offset + 2].toInt() and 0xFF
                val a = data[offset + 3].toInt() and 0xFF
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        println("TextureRenderer: Converted BGRA to ARGB")
        return pixels
    }
}