package com.plugin.texture_rgba_renderer

import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry

class TextureRgbaRendererPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
  private lateinit var channel: MethodChannel
  private lateinit var textureRegistry: TextureRegistry
  private lateinit var context: Context
  private val renderers = mutableMapOf<Int, TextureRenderer>()

  companion object {
    private const val TAG = "TextureRgbaPlugin"
    private const val CHANNEL_NAME = "texture_rgba_renderer"
  }

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME)
    channel.setMethodCallHandler(this)
    textureRegistry = binding.textureRegistry
    context = binding.applicationContext
    Log.d(TAG, "Plugin attached to engine")
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    try {
      when (call.method) {
        "getPlatformVersion" -> {
          result.success("Android ${android.os.Build.VERSION.RELEASE}")
        }
        "createTexture" -> handleCreateTexture(call, result)
        "closeTexture" -> handleCloseTexture(call, result)
        "onRgba" -> handleOnRgba(call, result)
        "getTexturePtr" -> handleGetTexturePtr(call, result)
        else -> result.notImplemented()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling method ${call.method}", e)
      result.error("UNEXPECTED_ERROR", "Error in ${call.method}: ${e.message}", null)
    }
  }

  private fun handleCreateTexture(call: MethodCall, result: MethodChannel.Result) {
    val key = call.argument<Int>("key") ?: return result.error(
      "INVALID_KEY", "Key is required", null
    )

    if (renderers.containsKey(key)) {
      Log.d(TAG, "Renderer for key $key already exists, reusing texture ID")
      result.success(renderers[key]!!.textureId.toInt())
    } else {
      try {
        val renderer = TextureRenderer(textureRegistry)
        renderers[key] = renderer
        Log.d(TAG, "Created new texture with ID ${renderer.textureId} for key $key")
        result.success(renderer.textureId.toInt())
      } catch (e: Exception) {
        Log.e(TAG, "Failed to create texture", e)
        result.error("CREATE_TEXTURE_FAILED", e.message, null)
      }
    }
  }

  private fun handleCloseTexture(call: MethodCall, result: MethodChannel.Result) {
    val key = call.argument<Int>("key") ?: return result.error(
      "INVALID_KEY", "Key is required", null
    )

    val renderer = renderers.remove(key)
    if (renderer != null) {
      renderer.dispose()
      Log.d(TAG, "Disposed texture for key $key")
      result.success(true)
    } else {
      Log.w(TAG, "Attempted to close non-existent texture with key $key")
      result.success(false)
    }
  }

  private fun handleOnRgba(call: MethodCall, result: MethodChannel.Result) {
    val key = call.argument<Int>("key") ?: return result.error(
      "INVALID_KEY", "Key is required", null
    )
    val data = call.argument<ByteArray>("data") ?: return result.error(
      "INVALID_DATA", "Data is required", null
    )
    val height = call.argument<Int>("height") ?: 0
    val width = call.argument<Int>("width") ?: 0
    val strideAlign = call.argument<Int>("stride_align") ?: (width * 4)

    if (width <= 0 || height <= 0) {
      result.error("INVALID_DIMENSIONS", "Width and height must be positive", null)
      return
    }

    val renderer = renderers[key]
    if (renderer != null) {
      // Use a try-catch block to handle potential errors in updateFrame
      try {
        renderer.updateFrame(data, width, height, strideAlign)
        result.success(true)
      } catch (e: Exception) {
        Log.e(TAG, "Error updating frame", e)
        result.error("UPDATE_FRAME_FAILED", e.message, null)
      }
    } else {
      Log.e(TAG, "No renderer found for key $key")
      result.error("RENDERER_NOT_FOUND", "No renderer for key $key", null)
    }
  }

  private fun handleGetTexturePtr(call: MethodCall, result: MethodChannel.Result) {
    val key = call.argument<Int>("key") ?: return result.error(
      "INVALID_KEY", "Key is required", null
    )

    val renderer = renderers[key]
    if (renderer != null) {
      result.error("NOT_SUPPORTED", "Use onRgba method instead", null)
    } else {
      result.error("RENDERER_NOT_FOUND", "No renderer for key $key", null)
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    Log.d(TAG, "Plugin detached from engine, disposing ${renderers.size} textures")
    channel.setMethodCallHandler(null)

    renderers.forEach { (key, renderer) ->
      try {
        renderer.dispose()
        Log.d(TAG, "Disposed texture for key $key")
      } catch (e: Exception) {
        Log.e(TAG, "Error disposing renderer for key $key", e)
      }
    }
    renderers.clear()
  }
}