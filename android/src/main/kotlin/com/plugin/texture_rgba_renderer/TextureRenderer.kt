package com.example.flutter_texture_rgba_renderer

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
    }

    fun updateFrame(data: ByteArray, width: Int, height: Int, stride: Int) {
        // Resize surface and bitmap if dimensions change
        if (currentWidth != width || currentHeight != height) {
            surfaceProducer.setSize(width, height)
            bitmap?.recycle() // Clean up old bitmap
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            currentWidth = width
            currentHeight = height
        }

        // Convert BGRA data to ARGB for Android Bitmap
        val bitmapData = convertBgraToArgb(data, width, height, stride)
        bitmap?.setPixels(bitmapData, 0, width, 0, 0, width, height)

        // Draw to the surface
        val canvas = surface.lockCanvas(null)
        try {
            canvas.drawBitmap(bitmap!!, 0f, 0f, null)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    fun dispose() {
        bitmap?.recycle()
        surfaceProducer.release()
        surface.release()
    }

    private fun convertBgraToArgb(data: ByteArray, width: Int, height: Int, stride: Int): IntArray {
        val pixels = IntArray(width * height)
        val buffer = ByteBuffer.wrap(data)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * stride + x * 4
                val b = data[offset].toInt() and 0xFF
                val g = data[offset + 1].toInt() and 0xFF
                val r = data[offset + 2].toInt() and 0xFF
                val a = data[offset + 3].toInt() and 0xFF
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }
}