package com.zcc.lib.camera;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;

import com.zcc.lib.camera.Camera1Utils;

import java.io.IOException;

public class Camera1Manager implements Camera.PreviewCallback {
    private static final String TAG = "Camera1Manager";
    private Camera mCamera;
    private byte[] previewBuffer = null;
    private Camera.Size mPreviewSize;
    private Camera.PreviewCallback mOuterPreviewCallback;

    public Camera.Size getPreviewSize() {
        return mPreviewSize;
    }

    public void initCameraWithTargetSize(Activity currentActivity,
                                         int targetPreviewWidth, int targetPreviewHeight,
                                         SurfaceTexture cameraSurfaceTexture,
                                         Camera.PreviewCallback previewCallback) throws IOException {
        this.mOuterPreviewCallback = previewCallback;
        int numCams = Camera.getNumberOfCameras();
        if (numCams > 0) {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            if (null == mCamera) {
                Log.e(TAG, "No front camera found, return");
                return;
            }
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, info);
            // orientation
            int correctOrientation = Camera1Utils.getCorrectCameraOrientation(currentActivity, info);
            mCamera.setDisplayOrientation(correctOrientation);
            mCamera.getParameters().setRotation(correctOrientation);
            // texture size
            mPreviewSize = Camera1Utils.setCameraPreviewSizeAndCodec(targetPreviewWidth, targetPreviewHeight, mCamera);
            previewBuffer = new byte[mPreviewSize.width * mPreviewSize.height * 3 / 2];

            mCamera.addCallbackBuffer(previewBuffer);
            mCamera.setPreviewTexture(cameraSurfaceTexture);
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.startPreview();
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mCamera.addCallbackBuffer(previewBuffer);
        if (mOuterPreviewCallback != null) {
            synchronized (this) {
                mOuterPreviewCallback.onPreviewFrame(data, camera);
            }
        }
    }

    public void onResume() {

    }

    public void onPause() {

    }

    public void release() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }
}
