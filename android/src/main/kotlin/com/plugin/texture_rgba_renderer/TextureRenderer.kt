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
        surfaceProducer.setCallback(object : TextureRegistry.SurfaceProducer.Callback {
            override fun onSurfaceAvailable() {
                println("SurfaceProducer: Surface is available")
            }
            override fun onSurfaceDestroyed() {
                println("SurfaceProducer: Surface destroyed")
            }
        })
        println("SurfaceProducer initialized with textureId=$textureId")
    }

    fun updateFrame(data: ByteArray, width: Int, height: Int, stride: Int) {
        if (!surface.isValid) {
            println("Surface is not valid")
            return
        }
        println("updateFrame: width=$width, height=$height, dataSize=${data.size}")
        if (currentWidth != width || currentHeight != height) {
            surfaceProducer.setSize(width, height)
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            currentWidth = width
            currentHeight = height
            println("Resized surface and bitmap to $width x $height")
        }

        val bitmapData = convertBgraToArgb(data, width, height, stride)
        bitmap?.setPixels(bitmapData, 0, width, 0, 0, width, height)
        println("Set bitmap pixels")

        val canvas = surface.lockCanvas(null)
        try {
            canvas.drawBitmap(bitmap!!, 0f, 0f, null)
            println("Drew bitmap to canvas")
        } finally {
            surface.unlockCanvasAndPost(canvas)
            surfaceProducer.requestRender()
            println("Unlocked and posted canvas, requested render")
        }
    }

    fun dispose() {
        bitmap?.recycle()
        surfaceProducer.release()
        surface.release()
        println("TextureRenderer disposed")
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