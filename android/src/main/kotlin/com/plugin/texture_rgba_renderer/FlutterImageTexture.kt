package com.plugin.texture_rgba_renderer

import android.R.attr.scaleHeight
import android.R.attr.scaleWidth
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry.SurfaceTextureEntry
import java.io.File
import java.nio.ByteBuffer


@RequiresApi(Build.VERSION_CODES.KITKAT)
class FlutterImageTexture(var context: Context?, entry: SurfaceTextureEntry, result: MethodChannel.Result?) {
    private val logTag = "FlutterImageTexture"
    var mEntry: SurfaceTextureEntry?
    var surface: Surface?
    var result: MethodChannel.Result?
    var imgWidth: Int? = null
    var imgHeight: Int? = null
    var handler : Handler? = Handler(Looper.getMainLooper())
    var bitmap:Bitmap? = null

    //    Bitmap bitmap;
    init {
        mEntry = entry
        surface = Surface(entry.surfaceTexture())
        this.result = result
    }

    fun getTextureId(): Long {
        return mEntry?.id() ?: -1
    }

    private fun draw(bitmap: Bitmap, width: Int, height: Int) {
        if (surface != null && surface!!.isValid) {
            mEntry!!.surfaceTexture().setDefaultBufferSize(width!!, height!!)
            val canvas = surface!!.lockCanvas(null)
            canvas.drawBitmap(bitmap, 0f, 0f, Paint())
            surface!!.unlockCanvasAndPost(canvas)
        }
    }

    fun onRgba(data: ByteArray, width: Int, height: Int) {
        val buffer = ByteBuffer.wrap(data)
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap!!.copyPixelsFromBuffer(buffer)

        handler?.post(object :Runnable{
            override fun run() {
                draw(bitmap!!, width, height)
            }
        })
    }

    fun dispose() {
        surface!!.release()
        surface = null
        mEntry!!.release()
        mEntry = null
        result = null
        handler = null
        context = null
        if(bitmap != null && !bitmap!!.isRecycled()){
            bitmap!!.recycle();
            bitmap = null;
        }
    }

    companion object {
        fun dp2px(context: Context?, dpValue: Float?): Int {
            val scale = context!!.resources.displayMetrics.density
            if (dpValue != null) {
                return (dpValue * scale + 0.5f).toInt()
            }
            return 0
        }
        fun calculateInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
            var inSampleSize = 1
            if (outWidth > reqWidth || outHeight > reqHeight) {
                val halfWidth = outWidth / 2
                val halfHeight = outHeight / 2
                while (halfWidth / inSampleSize >= reqWidth && halfHeight / inSampleSize >= reqHeight) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }

}