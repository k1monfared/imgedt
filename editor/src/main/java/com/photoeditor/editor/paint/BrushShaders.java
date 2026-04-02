package com.photoeditor.editor.paint;

/**
 * GLSL shader source code for the brush rendering pipeline.
 * Ported from Telegram's ShaderSet.java (GPL v2).
 */
public final class BrushShaders {

    private BrushShaders() {}

    public static final String BRUSH_VERTEX = ""
            + "precision highp float;\n"
            + "uniform mat4 mvpMatrix;\n"
            + "attribute vec4 inPosition;\n"
            + "attribute vec2 inTexcoord;\n"
            + "attribute float alpha;\n"
            + "varying vec2 varTexcoord;\n"
            + "varying float varIntensity;\n"
            + "void main (void) {\n"
            + "    gl_Position = mvpMatrix * inPosition;\n"
            + "    varTexcoord = inTexcoord;\n"
            + "    varIntensity = alpha;\n"
            + "}\n";

    public static final String BRUSH_FRAGMENT = ""
            + "precision highp float;\n"
            + "varying vec2 varTexcoord;\n"
            + "varying float varIntensity;\n"
            + "uniform sampler2D texture;\n"
            + "void main (void) {\n"
            + "    gl_FragColor = vec4(1, 1, 1, varIntensity * texture2D(texture, varTexcoord.st, 0.0).r);\n"
            + "}\n";

    public static final String BLIT_VERTEX = ""
            + "precision highp float;\n"
            + "uniform mat4 mvpMatrix;\n"
            + "attribute vec4 inPosition;\n"
            + "attribute vec2 inTexcoord;\n"
            + "varying vec2 varTexcoord;\n"
            + "void main (void) {\n"
            + "    gl_Position = mvpMatrix * inPosition;\n"
            + "    varTexcoord = inTexcoord;\n"
            + "}\n";

    public static final String BLIT_WITH_MASK_FRAGMENT = ""
            + "precision highp float;\n"
            + "varying vec2 varTexcoord;\n"
            + "uniform sampler2D texture;\n"
            + "uniform sampler2D mask;\n"
            + "uniform vec4 color;\n"
            + "void main (void) {\n"
            + "    vec4 dst = texture2D(texture, varTexcoord.st, 0.0);\n"
            + "    float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;\n"
            + "    float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);\n"
            + "    gl_FragColor.rgb = (color.rgb * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha));\n"
            + "    gl_FragColor.a = outAlpha;\n"
            + "}\n";

    public static final String COMPOSITE_WITH_MASK_FRAGMENT = ""
            + "precision highp float;\n"
            + "varying vec2 varTexcoord;\n"
            + "uniform sampler2D texture;\n"
            + "uniform sampler2D mask;\n"
            + "uniform vec4 color;\n"
            + "void main(void) {\n"
            + "    vec4 dst = texture2D(texture, varTexcoord.st, 0.0);\n"
            + "    float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;\n"
            + "    float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);\n"
            + "    gl_FragColor.rgb = (color.rgb * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha)) / outAlpha;\n"
            + "    gl_FragColor.a = outAlpha;\n"
            + "}\n";

    public static final String BLIT_WITH_MASK_ERASER_FRAGMENT = ""
            + "precision highp float;\n"
            + "varying vec2 varTexcoord;\n"
            + "uniform sampler2D texture;\n"
            + "uniform sampler2D mask;\n"
            + "uniform vec4 color;\n"
            + "void main (void) {\n"
            + "    vec4 dst = texture2D(texture, varTexcoord.st, 0.0);\n"
            + "    float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;\n"
            + "    float outAlpha = dst.a * (1.0 - srcAlpha);\n"
            + "    gl_FragColor.rgb = dst.rgb;\n"
            + "    gl_FragColor.a = outAlpha;\n"
            + "    gl_FragColor.rgb *= gl_FragColor.a;\n"
            + "}\n";

    public static final String SIMPLE_BLIT_FRAGMENT = ""
            + "precision highp float;\n"
            + "varying vec2 varTexcoord;\n"
            + "uniform sampler2D texture;\n"
            + "void main (void) {\n"
            + "    gl_FragColor = texture2D(texture, varTexcoord.st, 0.0);\n"
            + "    gl_FragColor.rgb *= gl_FragColor.a;\n"
            + "}\n";
}
