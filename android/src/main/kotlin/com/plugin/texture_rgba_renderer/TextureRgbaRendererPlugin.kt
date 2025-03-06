package com.plugin.texture_rgba_renderer

import androidx.annotation.NonNull
import android.util.Log

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import io.flutter.view.TextureRegistry
import java.util.HashMap

/** TextureRgbaRendererPlugin */
class TextureRgbaRendererPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var flutterImageTextureDelegate: FlutterImageTextureDelegate
  private lateinit var textureRegistry: TextureRegistry
  private val logTag = "TextureRgbaRendererPlugin"

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "texture_rgba_renderer")
    textureRegistry = flutterPluginBinding.textureRegistry
    flutterImageTextureDelegate = FlutterImageTextureDelegate(flutterPluginBinding.applicationContext)
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {

    when(call.method){
      "getPlatformVersion"-> result.success("Android ${android.os.Build.VERSION.RELEASE}")
      "createTexture" -> {
        flutterImageTextureDelegate.createTexture(textureRegistry,call,result)
      }
      "closeTexture" -> {
        flutterImageTextureDelegate.closeTexture(call,result)
      }
      "onRgba" -> {
        flutterImageTextureDelegate.onRgba(call,result)
      }
      "getTexturePtr" -> {
        result.success(0)
      }

      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }
}