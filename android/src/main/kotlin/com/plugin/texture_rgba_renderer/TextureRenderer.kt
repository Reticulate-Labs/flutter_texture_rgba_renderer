package com.plugin.texture_rgba_renderer

import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Log
import android.view.Surface
import io.flutter.view.TextureRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class TextureRenderer(private val textureRegistry: TextureRegistry) {
    val textureId: Long
    private val surfaceTexture: SurfaceTexture
    private var surface: Surface
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var program: Int = 0
    private var textureIdGL: Int = 0
    private var vao: Int = 0
    private var vertexVBO: Int = 0
    private var texCoordVBO: Int = 0
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0

    // Reusable buffer for frame data to avoid allocation on every frame
    private var frameBuffer: ByteBuffer? = null
    private var frameBufferCapacity: Int = 0

    // EGL configuration defaults
    private val EGL_OPENGL_ES3_BIT = 0x40

    // Vertex and texture coordinates for a full-screen quad
    private val vertices = floatArrayOf(
        -1f, -1f,  // Bottom left
        1f, -1f,   // Bottom right
        -1f, 1f,   // Top left
        1f, 1f     // Top right
    )
    private val texCoords = floatArrayOf(
        0f, 1f,    // Bottom left
        1f, 1f,    // Bottom right
        0f, 0f,    // Top left
        1f, 0f     // Top right
    )
    private lateinit var verticesBuffer: FloatBuffer
    private lateinit var texCoordsBuffer: FloatBuffer

    init {
        // Get the SurfaceTextureEntry from Flutter TextureRegistry
        val textureEntry = textureRegistry.createSurfaceTexture()
        surfaceTexture = textureEntry.surfaceTexture()
        textureId = textureEntry.id()
        surface = Surface(surfaceTexture)
        Log.d(TAG, "Initialized with textureId=$textureId")
        initOpenGL()
    }

    private fun initOpenGL() {
        // Initialize EGL
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        // Choose EGL config
        val configAttributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, 1, numConfigs, 0)

        // Create EGL context for OpenGL ES 3.0
        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0)

        // Create EGL surface
        createEGLSurface(configs[0])

        // Compile shaders
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource)
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        // Create VBOs
        val vbos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        vertexVBO = vbos[0]
        texCoordVBO = vbos[1]

        // Upload vertex data
        verticesBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVBO)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, verticesBuffer, GLES30.GL_STATIC_DRAW)

        // Upload texture coordinate data
        texCoordsBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVBO)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, texCoords.size * 4, texCoordsBuffer, GLES30.GL_STATIC_DRAW)

        // Create VAO
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vao = vaos[0]
        GLES30.glBindVertexArray(vao)

        // Set up vertex attributes
        val positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVBO)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, 0)

        val texCoordHandle = GLES30.glGetAttribLocation(program, "aTexCoord")
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVBO)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindVertexArray(0)
    }

    // Create or recreate the EGL surface
    private fun createEGLSurface(config: EGLConfig?) {
        // Destroy existing surface if there is one
        if (eglSurface != null) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
        }

        // Create new EGL surface
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, intArrayOf(EGL14.EGL_NONE), 0)

        // Make the context current
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    // Check if surface is valid and recreate if necessary
    private fun ensureValidSurface(): Boolean {
        if (!surface.isValid) {
            Log.e(TAG, "Surface is not valid, attempting to recreate")
            try {
                // Release old surface
                surface.release()

                // Create new surface
                surface = Surface(surfaceTexture)

                // Get current EGL config
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                val configAttributes = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE
                )
                EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, 1, numConfigs, 0)

                // Recreate EGL surface
                createEGLSurface(configs[0])

                if (currentWidth > 0 && currentHeight > 0) {
                    // Restore buffer size
                    surfaceTexture.setDefaultBufferSize(currentWidth, currentHeight)
                }

                Log.d(TAG, "Surface recreated successfully")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recreate surface", e)
                return false
            }
        }
        return true
    }

    fun updateFrame(data: ByteArray, width: Int, height: Int, stride: Int) {
        // Check if surface is valid, attempt to recreate if not
        if (!ensureValidSurface()) {
            Log.e(TAG, "Cannot update frame, surface is invalid")
            return
        }

        //Log.v(TAG, "updateFrame called with width=$width, height=$height, dataSize=${data.size}, stride=$stride")

        // Check data size
        val expectedSize = stride * height
        if (data.size < expectedSize) {
            Log.e(TAG, "Data size too small: ${data.size} < $expectedSize")
            return
        }

        // Resize if necessary
        if (currentWidth != width || currentHeight != height) {
            surfaceTexture.setDefaultBufferSize(width, height)
            currentWidth = width
            currentHeight = height

            if (textureIdGL != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(textureIdGL), 0)
            }

            val textures = IntArray(1)
            GLES30.glGenTextures(1, textures, 0)
            textureIdGL = textures[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIdGL)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            Log.d(TAG, "Resized texture to $width x $height")
        }

        // Reuse or create ByteBuffer for frame data
        if (frameBuffer == null || frameBufferCapacity < data.size) {
            frameBuffer = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
            frameBufferCapacity = data.size
            Log.d(TAG, "Created new frame buffer with capacity $frameBufferCapacity")
        }

        // Clear and fill the buffer
        frameBuffer?.clear()
        frameBuffer?.put(data)
        frameBuffer?.rewind()

        // Upload to OpenGL texture with stride
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIdGL)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, stride / 4)
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, frameBuffer
        )
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, 0)

        // Render with OpenGL
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        GLES30.glViewport(0, 0, width, height)

        GLES30.glUseProgram(program)
        val textureHandle = GLES30.glGetUniformLocation(program, "uTexture")

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIdGL)
        GLES30.glUniform1i(textureHandle, 0)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        //Log.v(TAG, "Rendered frame")
    }

    fun dispose() {
        if (textureIdGL != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureIdGL), 0)
        }
        GLES30.glDeleteBuffers(2, intArrayOf(vertexVBO, texCoordVBO), 0)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
        surface.release()
        surfaceTexture.release()
        frameBuffer = null
        Log.d(TAG, "Disposed")
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        // Check compilation status
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation failed: $error")
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $error")
        }

        return shader
    }

    companion object {
        private const val TAG = "TextureRenderer"
        private const val vertexShaderSource = "#version 300 es\nin vec4 aPosition;\nin vec2 aTexCoord;\nout vec2 vTexCoord;\nvoid main() {\n    gl_Position = aPosition;\n    vTexCoord = aTexCoord;\n}"
        private const val fragmentShaderSource = "#version 300 es\nprecision mediump float;\nuniform sampler2D uTexture;\nin vec2 vTexCoord;\nout vec4 fragColor;\nvoid main() {\n    vec4 color = texture(uTexture, vTexCoord);\n    fragColor = vec4(color.b, color.g, color.r, color.a); // BGRA to RGBA\n}"
    }
}