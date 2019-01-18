package com.zcc.mediarecorder;

import android.os.Handler;
import android.os.Looper;

import com.zcc.mediarecorder.common.ICommonFailListener;

import java.util.LinkedList;

public class EventManager {

    private static EventManager instance;
    private final Object MUTEX = new Object();
    private Handler mMainHandler;
    private LinkedList<ICommonFailListener> listeners = new LinkedList<>();

    private EventManager() {
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public static EventManager get() {
        if (instance == null) {
            synchronized (EventManager.class) {
                if (instance == null) {
                    instance = new EventManager();
                }
            }
        }
        return instance;
    }

    public void regist(ICommonFailListener iCommonFailListener) {
        listeners.add(iCommonFailListener);
    }

    public void destroy() {
        synchronized (MUTEX) {
            instance = null;
            listeners = null;
        }
    }

    public void sendMsg(final int code, final String msg) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (MUTEX) {
                    if (listeners != null) {
                        for (ICommonFailListener listener : listeners) {
                            listener.onFailed(code, msg);
                        }
                    }
                }
            }
        });
    }

}
