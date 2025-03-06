//import Cocoa
#if os(iOS)
    import Flutter
#elseif os(macOS)
    import FlutterMacOS
#endif

public class TextureRgbaRendererPlugin: NSObject, FlutterPlugin {

    private var renderer: [Int64: TextRgba] = [:]
    private var textureRegistry: FlutterTextureRegistry?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = TextureRgbaRendererPlugin()
        #if os(iOS)
            let channel = FlutterMethodChannel(
                name: "texture_rgba_renderer", binaryMessenger: registrar.messenger())
            instance.setTextureRegistry(registry: registrar.textures())
        #elseif os(macOS)
            let channel = FlutterMethodChannel(
                name: "texture_rgba_renderer", binaryMessenger: registrar.messenger)
            instance.setTextureRegistry(registry: registrar.textures)
        #endif

        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func setTextureRegistry(registry: FlutterTextureRegistry) {
        textureRegistry = registry
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getPlatformVersion":
            #if os(iOS)
                result("iOS " + UIDevice.current.systemVersion)
            #elseif os(macOS)
                result("macOS " + ProcessInfo.processInfo.operatingSystemVersionString)
            #endif
        case "createTexture":
            guard let args = call.arguments as? [String: Any?],
                let key = args["key"] as? Int64,
                renderer[key] == nil
            else {
                result(-1)
                return
            }

            let textRgba = TextRgba.new(registry: textureRegistry)
            renderer[key] = textRgba
            result(textRgba.textureId)
        case "closeTexture":
            guard let args = call.arguments as? [String: Any?],
                let key = args["key"] as? Int64,
                let textureRgba = renderer[key]
            else {
                result(false)
                return
            }

            if textureRgba.textureId != -1 {
                textureRegistry?.unregisterTexture(textureRgba.textureId)
            }
            renderer.removeValue(forKey: key)
            result(true)

        case "onRgba":
            guard let args = call.arguments as? [String: Any?],
                let key = args["key"] as? Int64,
                let data = args["data"] as? FlutterStandardTypedData,
                let height = args["height"] as? Int,
                let width = args["width"] as? Int,
                let strideAlign = args["stride_align"] as? Int,
                let textureRgba = renderer[key]
            else {
                result(false)
                return
            }

            result(
                textureRgba.markFrameAvaliable(
                    data: data.data, width: width, height: height, stride_align: strideAlign))
        case "getTexturePtr":
            guard let args = call.arguments as? [String: Any?],
                let key = args["key"] as? Int64,
                let textureRgba = renderer[key]
            else {
                result(0)
                return
            }

            let unmanaged = Unmanaged.passUnretained(textureRgba)
            let intAddr = UInt(bitPattern: unmanaged.toOpaque())
            result(intAddr)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
}
