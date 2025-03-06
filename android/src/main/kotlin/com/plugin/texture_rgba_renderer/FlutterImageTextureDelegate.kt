package com.plugin.texture_rgba_renderer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry
import java.util.HashMap

@SuppressLint("LongLogTag")
@RequiresApi(Build.VERSION_CODES.KITKAT)
class FlutterImageTextureDelegate(context: Context){
    internal var context:Context? = null
    private val fluttetrImageHashMap = HashMap<String, FlutterImageTexture>()
    private val logTag = "FlutterImageTextureDelegate"

    init {
        this.context = context;
    }

    fun createTexture(textures:TextureRegistry?, call:MethodCall, result:MethodChannel.Result){
        val key = call.argument<Int>("key") ?: return

        if (fluttetrImageHashMap.containsKey(key.toString())) {
            result.success(-1)
        } else {
            val entry = textures?.createSurfaceTexture()
            fluttetrImageHashMap[entry?.id().toString()] = FlutterImageTexture(context, entry!!,result)
            result.success(entry?.id())
        }
    }

    fun closeTexture(call:MethodCall, result:MethodChannel.Result){
        val textureId = call.argument<Int>("key")
        val fluttetrImage = fluttetrImageHashMap[textureId.toString()]
        fluttetrImage!!.dispose()
        fluttetrImageHashMap.remove(textureId.toString())
        result.success(true)
    }

    fun onRgba(call:MethodCall, result:MethodChannel.Result){

        val key = call.argument<Int>("key")
        val textureId = key.toString()

        val width = call.argument<Int>("width") ?: 0
        val height = call.argument<Int>("height") ?: 0


        val data = call.argument<ByteArray>("data") ?: ByteArray(0)
        val strideAlign = call.argument<Int>("stride_align")?: 0

        if (data == null || strideAlign == null) {
            result.success(false)
        }

        if (fluttetrImageHashMap.containsKey(key.toString())) {
            val fluttetrImage = fluttetrImageHashMap[key.toString()]
            fluttetrImage?.onRgba(data, width, height)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    fun getTexturePtr(call:MethodCall, result:MethodChannel.Result){
        val key = call.argument<Int>("key")
    }

    fun dispose(){
        context = null
    }
}