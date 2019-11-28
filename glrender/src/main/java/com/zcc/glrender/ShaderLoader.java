package com.zcc.glrender;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by cc on 2019-11-28.
 */
public class ShaderLoader {
    private static final String VERTEXT_SHADER_ASSET_NAME = "fade_vertex_shader.glsl";
    private static final String FRAGMENT_SHADER_EXT_NAME = "fade_fragment_shader.glsl";
    private static String VERTEXT_SHADER = "";
    private static String FRAGMENT_SHADER_EXT = "";
    private static Context sAppContext;

    public static void init(Context appContext) {
        sAppContext = appContext.getApplicationContext();
        VERTEXT_SHADER = loadShader(VERTEXT_SHADER_ASSET_NAME);
        FRAGMENT_SHADER_EXT = loadShader(FRAGMENT_SHADER_EXT_NAME);
    }

    private static String loadShader(String path) {
        StringBuilder sb = new StringBuilder();
        try {
            InputStream in = sAppContext.getAssets().open(path);
            InputStreamReader inr = new InputStreamReader(in);
            char[] buf = new char[1024];
            int size;
            while ((size = inr.read(buf)) > 0) {
                sb.append(buf, 0, size);
            }
            inr.close();
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }


    public static String getVertextShader() {
        return VERTEXT_SHADER;
    }


    public static String getFragmentShaderExt() {
        return FRAGMENT_SHADER_EXT;
    }
}
