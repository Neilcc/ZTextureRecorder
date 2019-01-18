package com.zcc.mediarecorder;

import android.util.Log;

public class ALog {

    public static boolean enabled = true;

    public static void v(String TAG, String msg) {
        if (!enabled) return;
        android.util.Log.v(TAG, msg);
    }

    public static void i(String TAG, String msg) {
        if (!enabled) return;
        android.util.Log.i(TAG, msg);
    }

    public static void d(String TAG, String msg) {
        if (!enabled) return;
        android.util.Log.d(TAG, msg);
    }

    public static void w(String TAG, String msg) {
        if (!enabled) return;
        android.util.Log.w(TAG, msg);
    }

    public static void e(String TAG, String msg) {
        if (!enabled) return;
        android.util.Log.e(TAG, msg);
    }

    public static void dd(String msg) {
        if (!enabled) return;
        Log.d("zcc", msg);
    }
}
