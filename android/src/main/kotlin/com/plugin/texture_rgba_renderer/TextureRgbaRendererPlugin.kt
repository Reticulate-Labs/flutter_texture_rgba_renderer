package com.plugin.flutter_texture_rgba_renderer

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry

class TextureRgbaRendererPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
  private lateinit var channel: MethodChannel
  private lateinit var textureRegistry: TextureRegistry
  private lateinit var context: Context
  private val renderers = mutableMapOf<Int, TextureRenderer>()

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(binding.binaryMessenger, "texture_rgba_renderer")
    channel.setMethodCallHandler(this)
    textureRegistry = binding.textureRegistry
    context = binding.applicationContext
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "createTexture" -> {
        val key = call.argument<Int>("key") ?: return result.error(
          "INVALID_KEY", "Key is required", null
        )
        if (renderers.containsKey(key)) {
          result.success(renderers[key]!!.textureId.toInt())
        } else {
          try {
            val renderer = TextureRenderer(textureRegistry)
            renderers[key] = renderer
            result.success(renderer.textureId.toInt())
          } catch (e: Exception) {
            result.error("CREATE_TEXTURE_FAILED", e.message, null)
          }
        }
      }
      "closeTexture" -> {
        val key = call.argument<Int>("key") ?: return result.error(
          "INVALID_KEY", "Key is required", null
        )
        val renderer = renderers.remove(key)
        if (renderer != null) {
          renderer.dispose()
          result.success(true)
        } else {
          result.success(false)
        }
      }
      "onRgba" -> {
        val key = call.argument<Int>("key") ?: return result.error(
          "INVALID_KEY", "Key is required", null
        )
        val data = call.argument<ByteArray>("data") ?: return result.error(
          "INVALID_DATA", "Data is required", null
        )
        val height = call.argument<Int>("height") ?: 0
        val width = call.argument<Int>("width") ?: 0
        val strideAlign = call.argument<Int>("stride_align") ?: (width * 4)
        val renderer = renderers[key]
        if (renderer != null) {
          renderer.updateFrame(data, width, height, strideAlign)
          result.success(true)
        } else {
          result.error("RENDERER_NOT_FOUND", "No renderer for key $key", null)
        }
      }
      "getTexturePtr" -> {
        val key = call.argument<Int>("key") ?: return result.error(
          "INVALID_KEY", "Key is required", null
        )
        val renderer = renderers[key]
        if (renderer != null) {
          result.success(renderer.textureId.toInt())
        } else {
          result.error("RENDERER_NOT_FOUND", "No renderer for key $key", null)
        }
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    renderers.values.forEach { it.dispose() }
    renderers.clear()
  }
}