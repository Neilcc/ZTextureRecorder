package com.zcc.lib;

/**
 * Created by cc on 2019-09-19.
 */
public interface IGLRender {

    int createTexture();

    void rend(int textureId, float[] matrix);
}
