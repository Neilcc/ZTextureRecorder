package com.zcc.mediarecorder.demo;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.util.Comparator;
import java.util.PriorityQueue;

import androidx.annotation.Nullable;

public class MyService extends Service {
    public MyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("zcc", "onBInd");
        return new Binder() {

            PriorityQueue<Integer> priorityQueue = new PriorityQueue<>(new Comparator<Integer>() {
                @Override
                public int compare(Integer integer, Integer t1) {
                    return integer - t1;
                }
            });
        };
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("zcc", "onCreate");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.d("zcc", "onStart");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("zcc", "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d("zcc", "onDestroy");
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d("zcc", "onUnBind");
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d("zcc", "onReBind");
        super.onRebind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d("zcc", "onTashRemoved");
        super.onTaskRemoved(rootIntent);
    }
}
