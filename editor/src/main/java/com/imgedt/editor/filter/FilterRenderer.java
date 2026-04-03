package com.imgedt.editor.filter;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * OpenGL ES 2.0 renderer for photo filter adjustments.
 * Manages the EGL context, shader programs, and multi-pass rendering pipeline.
 */
public class FilterRenderer {

    private static final String TAG = "FilterRenderer";

    // EGL state
    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;

    // GL objects
    private int sourceTexture;
    private int[] renderTextures = new int[2];
    private int[] renderFramebuffers = new int[2];
    private int curvesTexture;

    // Shader programs
    private int toolsProgram;
    private int sharpenProgram;
    private int simpleProgram;

    // Uniform locations for tools shader
    private int toolsExposureLoc, toolsContrastLoc, toolsSaturationLoc;
    private int toolsWarmthLoc, toolsHighlightsLoc, toolsShadowsLoc;
    private int toolsVignetteLoc, toolsGrainLoc, toolsFadeLoc;
    private int toolsWidthLoc, toolsHeightLoc;
    private int toolsSkipToneLoc;
    private int toolsShadowsTintIntensityLoc, toolsHighlightsTintIntensityLoc;
    private int toolsShadowsTintColorLoc, toolsHighlightsTintColorLoc;
    private int toolsCurvesImageLoc;

    // Uniform locations for sharpen shader
    private int sharpenLoc, sharpenWidthLoc, sharpenHeightLoc;

    // Vertex data
    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;

    private int renderWidth, renderHeight;
    private boolean initialized = false;

    private HandlerThread glThread;
    private Handler glHandler;
    private TextureView textureView;
    private Bitmap sourceBitmap;
    private FilterParams params;
    private Runnable onFirstFrameReady;

    private static final float[] VERTEX_DATA = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
    };

    private static final float[] TEX_COORD_DATA = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };

    public FilterRenderer(TextureView textureView, FilterParams params) {
        this.textureView = textureView;
        this.params = params;

        vertexBuffer = createFloatBuffer(VERTEX_DATA);
        texCoordBuffer = createFloatBuffer(TEX_COORD_DATA);
    }

    public void setOnFirstFrameReady(Runnable callback) {
        this.onFirstFrameReady = callback;
    }

    public void setSourceBitmap(Bitmap bitmap) {
        this.sourceBitmap = bitmap;
        if (initialized && glHandler != null) {
            glHandler.post(this::uploadSourceTexture);
        }
    }

    public void start() {
        glThread = new HandlerThread("FilterGLThread");
        glThread.start();
        glHandler = new Handler(glThread.getLooper());
        glHandler.post(this::initGL);
    }

    public void requestRender() {
        if (glHandler != null) {
            glHandler.post(this::draw);
        }
    }

    public void release() {
        if (glHandler != null) {
            glHandler.post(this::cleanup);
            glThread.quitSafely();
        }
    }

    /**
     * Render the current state to a Bitmap for saving.
     * Posts GL work to the GL thread and blocks until complete.
     */
    public Bitmap renderToBitmap() {
        if (!initialized || sourceBitmap == null || glHandler == null) return null;

        final CountDownLatch latch = new CountDownLatch(1);
        final Bitmap[] result = new Bitmap[1];

        glHandler.post(() -> {
            result[0] = renderToBitmapInternal();
            latch.countDown();
        });

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.e(TAG, "renderToBitmap timed out");
                return null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "renderToBitmap interrupted", e);
            return null;
        }
        return result[0];
    }

    private Bitmap renderToBitmapInternal() {
        int w = sourceBitmap.getWidth();
        int h = sourceBitmap.getHeight();

        // Create offscreen framebuffer at full resolution
        int[] fullTextures = new int[2];
        int[] fullFramebuffers = new int[2];
        GLES20.glGenTextures(2, fullTextures, 0);
        GLES20.glGenFramebuffers(2, fullFramebuffers, 0);
        for (int i = 0; i < 2; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fullTextures[i]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fullFramebuffers[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fullTextures[i], 0);
        }

        // Upload full-res source
        int fullSourceTex = createTexture(sourceBitmap);

        GLES20.glViewport(0, 0, w, h);

        // Sharpen pass
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fullFramebuffers[0]);
        drawSharpenPass(fullSourceTex, w, h);

        // Tools pass
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fullFramebuffers[1]);
        drawToolsPass(fullTextures[0], w, h);

        // Read pixels
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);
        buffer.order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        buffer.rewind();

        Bitmap glBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        glBitmap.copyPixelsFromBuffer(buffer);

        // Flip vertically (GL origin is bottom-left, Android is top-left)
        android.graphics.Matrix flipMatrix = new android.graphics.Matrix();
        flipMatrix.postScale(1, -1, w / 2f, h / 2f);
        Bitmap result = Bitmap.createBitmap(glBitmap, 0, 0, w, h, flipMatrix, false);
        if (result != glBitmap) {
            glBitmap.recycle();
        }

        // Cleanup
        GLES20.glDeleteTextures(1, new int[]{fullSourceTex}, 0);
        GLES20.glDeleteTextures(2, fullTextures, 0);
        GLES20.glDeleteFramebuffers(2, fullFramebuffers, 0);

        // Restore preview viewport
        GLES20.glViewport(0, 0, renderWidth, renderHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        return result;
    }

    private void initGL() {
        egl = (EGL10) EGLContext.getEGL();
        eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        egl.eglInitialize(eglDisplay, version);

        int[] configAttribs = {
                EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        egl.eglChooseConfig(eglDisplay, configAttribs, configs, 1, numConfigs);

        int[] contextAttribs = {0x3098, 2, EGL10.EGL_NONE}; // EGL_CONTEXT_CLIENT_VERSION = 2
        eglContext = egl.eglCreateContext(eglDisplay, configs[0], EGL10.EGL_NO_CONTEXT, contextAttribs);

        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        if (surfaceTexture == null) {
            Log.e(TAG, "SurfaceTexture not available");
            return;
        }
        eglSurface = egl.eglCreateWindowSurface(eglDisplay, configs[0], surfaceTexture, null);
        egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        renderWidth = textureView.getWidth();
        renderHeight = textureView.getHeight();

        initShaders();
        initTextures();
        initialized = true;

        if (sourceBitmap != null) {
            uploadSourceTexture();
        }
    }

    private void initShaders() {
        simpleProgram = createProgram(FilterShaders.SIMPLE_VERTEX, FilterShaders.SIMPLE_FRAGMENT);
        toolsProgram = createProgram(FilterShaders.SIMPLE_VERTEX, FilterShaders.TOOLS_FRAGMENT);
        sharpenProgram = createProgram(FilterShaders.SHARPEN_VERTEX, FilterShaders.SHARPEN_FRAGMENT);

        // Tools uniforms
        toolsExposureLoc = GLES20.glGetUniformLocation(toolsProgram, "exposure");
        toolsContrastLoc = GLES20.glGetUniformLocation(toolsProgram, "contrast");
        toolsSaturationLoc = GLES20.glGetUniformLocation(toolsProgram, "saturation");
        toolsWarmthLoc = GLES20.glGetUniformLocation(toolsProgram, "warmth");
        toolsHighlightsLoc = GLES20.glGetUniformLocation(toolsProgram, "highlights");
        toolsShadowsLoc = GLES20.glGetUniformLocation(toolsProgram, "shadows");
        toolsVignetteLoc = GLES20.glGetUniformLocation(toolsProgram, "vignette");
        toolsGrainLoc = GLES20.glGetUniformLocation(toolsProgram, "grain");
        toolsFadeLoc = GLES20.glGetUniformLocation(toolsProgram, "fadeAmount");
        toolsWidthLoc = GLES20.glGetUniformLocation(toolsProgram, "width");
        toolsHeightLoc = GLES20.glGetUniformLocation(toolsProgram, "height");
        toolsSkipToneLoc = GLES20.glGetUniformLocation(toolsProgram, "skipTone");
        toolsShadowsTintIntensityLoc = GLES20.glGetUniformLocation(toolsProgram, "shadowsTintIntensity");
        toolsHighlightsTintIntensityLoc = GLES20.glGetUniformLocation(toolsProgram, "highlightsTintIntensity");
        toolsShadowsTintColorLoc = GLES20.glGetUniformLocation(toolsProgram, "shadowsTintColor");
        toolsHighlightsTintColorLoc = GLES20.glGetUniformLocation(toolsProgram, "highlightsTintColor");
        toolsCurvesImageLoc = GLES20.glGetUniformLocation(toolsProgram, "curvesImage");

        // Sharpen uniforms
        sharpenLoc = GLES20.glGetUniformLocation(sharpenProgram, "sharpen");
        sharpenWidthLoc = GLES20.glGetUniformLocation(sharpenProgram, "inputWidth");
        sharpenHeightLoc = GLES20.glGetUniformLocation(sharpenProgram, "inputHeight");
    }

    private void initTextures() {
        // Render textures (ping-pong buffers)
        GLES20.glGenTextures(2, renderTextures, 0);
        GLES20.glGenFramebuffers(2, renderFramebuffers, 0);
        for (int i = 0; i < 2; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTextures[i]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderWidth, renderHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFramebuffers[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTextures[i], 0);
        }

        // Identity curves texture (200x1 RGBA, all channels = identity mapping)
        ByteBuffer curvesData = ByteBuffer.allocateDirect(200 * 4);
        for (int i = 0; i < 200; i++) {
            byte v = (byte) (i * 255 / 199);
            curvesData.put(v); // R
            curvesData.put(v); // G
            curvesData.put(v); // B
            curvesData.put(v); // A (luminance)
        }
        curvesData.rewind();

        int[] texId = new int[1];
        GLES20.glGenTextures(1, texId, 0);
        curvesTexture = texId[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curvesTexture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 200, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, curvesData);
    }

    private void uploadSourceTexture() {
        if (sourceBitmap == null) return;
        if (sourceTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{sourceTexture}, 0);
        }
        sourceTexture = createTexture(sourceBitmap);
        draw();
    }

    private void draw() {
        if (!initialized || sourceTexture == 0) return;

        GLES20.glViewport(0, 0, renderWidth, renderHeight);

        // Pass 1: Sharpen (source -> renderTexture[0])
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFramebuffers[0]);
        drawSharpenPass(sourceTexture, renderWidth, renderHeight);

        // Pass 2: Tools (renderTexture[0] -> renderTexture[1])
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFramebuffers[1]);
        drawToolsPass(renderTextures[0], renderWidth, renderHeight);

        // Pass 3: Display (renderTexture[1] -> screen)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        drawSimplePass(renderTextures[1]);

        egl.eglSwapBuffers(eglDisplay, eglSurface);

        if (onFirstFrameReady != null) {
            Runnable r = onFirstFrameReady;
            onFirstFrameReady = null;
            textureView.post(r);
        }
    }

    private void drawSharpenPass(int inputTexture, int w, int h) {
        GLES20.glUseProgram(sharpenProgram);

        int posLoc = GLES20.glGetAttribLocation(sharpenProgram, "position");
        int texLoc = GLES20.glGetAttribLocation(sharpenProgram, "inputTexCoord");

        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(texLoc);
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(sharpenProgram, "sTexture"), 0);

        GLES20.glUniform1f(sharpenLoc, params.getSharpen());
        GLES20.glUniform1f(sharpenWidthLoc, (float) w);
        GLES20.glUniform1f(sharpenHeightLoc, (float) h);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    private void drawToolsPass(int inputTexture, int w, int h) {
        GLES20.glUseProgram(toolsProgram);

        int posLoc = GLES20.glGetAttribLocation(toolsProgram, "position");
        int texLoc = GLES20.glGetAttribLocation(toolsProgram, "inputTexCoord");

        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(texLoc);
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(toolsProgram, "sTexture"), 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curvesTexture);
        GLES20.glUniform1i(toolsCurvesImageLoc, 1);

        GLES20.glUniform1f(toolsExposureLoc, params.getExposure());
        GLES20.glUniform1f(toolsContrastLoc, params.getContrast());
        GLES20.glUniform1f(toolsSaturationLoc, params.getSaturation());
        GLES20.glUniform1f(toolsWarmthLoc, params.getWarmth());
        GLES20.glUniform1f(toolsHighlightsLoc, params.getHighlights());
        GLES20.glUniform1f(toolsShadowsLoc, params.getShadows());
        GLES20.glUniform1f(toolsVignetteLoc, params.getVignette());
        GLES20.glUniform1f(toolsGrainLoc, params.getGrain());
        GLES20.glUniform1f(toolsFadeLoc, params.getFade());
        GLES20.glUniform1f(toolsWidthLoc, (float) w);
        GLES20.glUniform1f(toolsHeightLoc, (float) h);
        GLES20.glUniform1f(toolsSkipToneLoc, 1.0f); // Skip curves for now (identity)
        GLES20.glUniform1f(toolsShadowsTintIntensityLoc, 0.0f);
        GLES20.glUniform1f(toolsHighlightsTintIntensityLoc, 0.0f);
        GLES20.glUniform3f(toolsShadowsTintColorLoc, 0.0f, 0.0f, 0.0f);
        GLES20.glUniform3f(toolsHighlightsTintColorLoc, 0.0f, 0.0f, 0.0f);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    private void drawSimplePass(int inputTexture) {
        GLES20.glUseProgram(simpleProgram);

        int posLoc = GLES20.glGetAttribLocation(simpleProgram, "position");
        int texLoc = GLES20.glGetAttribLocation(simpleProgram, "inputTexCoord");

        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(texLoc);
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(simpleProgram, "sTexture"), 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    private void cleanup() {
        if (sourceTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{sourceTexture}, 0);
        }
        GLES20.glDeleteTextures(2, renderTextures, 0);
        GLES20.glDeleteFramebuffers(2, renderFramebuffers, 0);
        GLES20.glDeleteTextures(1, new int[]{curvesTexture}, 0);
        GLES20.glDeleteProgram(toolsProgram);
        GLES20.glDeleteProgram(sharpenProgram);
        GLES20.glDeleteProgram(simpleProgram);

        egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        egl.eglDestroySurface(eglDisplay, eglSurface);
        egl.eglDestroyContext(eglDisplay, eglContext);
        egl.eglTerminate(eglDisplay);

        initialized = false;
    }

    // GL helpers

    private static int createTexture(Bitmap bitmap) {
        int[] texId = new int[1];
        GLES20.glGenTextures(1, texId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        return texId[0];
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Program link error: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Shader compile error: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static FloatBuffer createFloatBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data);
        fb.position(0);
        return fb;
    }
}
