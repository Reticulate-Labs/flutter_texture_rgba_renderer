package com.plugin.texture_rgba_renderer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import io.flutter.view.TextureRegistry
import java.nio.ByteBuffer

class TextRgba(private var textureRegistry: TextureRegistry?) {
    var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var bitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        fun new(textureRegistry: TextureRegistry?): TextRgba {
            val textRgba = TextRgba(textureRegistry)
            textRgba.textureEntry = textureRegistry?.createSurfaceTexture()
            return textRgba
        }
    }

    val textureId: Long
        get() = textureEntry?.id() ?: -1

}
