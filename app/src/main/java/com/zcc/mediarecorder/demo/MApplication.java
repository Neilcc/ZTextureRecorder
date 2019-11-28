package com.zcc.mediarecorder.demo;

import android.app.Application;

import com.zcc.glrender.ShaderLoader;

/**
 * Created by cc on 2019-11-28.
 */
public class MApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ShaderLoader.init(this);
    }
}

