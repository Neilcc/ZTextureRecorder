package com.zcc.mediarecorder.demo.utils;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.util.List;

public class Camera1Manager implements Camera.PreviewCallback {
    private static final String TAG = "Camera1Manager";
    private static final double ASPECT_TOLERANCE = 0.1;
    private static final int TEXTURE_NAME = 10;
    private static final int PORTRAIT_PREVIEW_ORIENTATION = 180;
    private static final String RESULT_FACE_NUM = "faceNum";
    private static final String RESULT_FACES = "faces";
    private static final String RESULT_LIP = "lip";
    private static final String DETECTED_STATUS = "status";
    private static final int FRAME_INTERVAL = 60;
    private Camera mCamera;
    private byte[] previewBuffer = null;
    private Camera.Size mPreviewSize;
    private Camera.PreviewCallback mOuterPreviewCallback;
    private float mFov;

    public static int getCorrectCameraOrientation(Activity currentActivity, Camera.CameraInfo info) {
        int rotation = currentActivity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 90;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }

    public Camera.Size getPreviewSize() {
        return mPreviewSize;
    }

    public static byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }

    public void initCamera(Activity currentActivity, int targetPreviewWidth,
                           int targetPreviewHeight, SurfaceTexture cameraSurfaceTexture,
                           Camera.PreviewCallback previewCallback) {
        this.mOuterPreviewCallback = previewCallback;
        int numCams = Camera.getNumberOfCameras();
        if (numCams > 0) {
            try {
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
                if (null == mCamera) {
                    Log.e(TAG, "No front camera found, return");
                    return;
                }
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, info);
                mCamera.setDisplayOrientation(getCorrectCameraOrientation(currentActivity, info));
                mCamera.getParameters().setRotation(getCorrectCameraOrientation(currentActivity, info));
                setCamera(targetPreviewWidth, targetPreviewHeight);
                mCamera.setPreviewTexture(cameraSurfaceTexture);
                mCamera.setPreviewCallbackWithBuffer(this);
                mFov = mCamera.getParameters().getHorizontalViewAngle();
                mCamera.startPreview();
            } catch (RuntimeException | IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void setCamera(int width, int height) {
        if (mCamera != null) {
            List<Camera.Size> mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);

            // get Camera parameters
            Camera.Parameters params = mCamera.getParameters();
            params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            params.setPreviewFormat(ImageFormat.NV21);

            // set Camera parameters
            mCamera.setParameters(params);
            params = mCamera.getParameters();
            mPreviewSize = params.getPreviewSize();
            previewBuffer = new byte[mPreviewSize.width * mPreviewSize.height * 3 / 2];
            mCamera.addCallbackBuffer(previewBuffer);
        }
    }

    /**
     * 获取最佳预览尺寸
     *
     * @param sizes 相机提供支持的系列预览尺寸
     * @param w     外部传入限定宽度
     * @param h     外部传入限定高度
     * @return 最佳预览尺寸
     */
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        double targetRatio = (double) w / h;
        if (sizes == null) {
            return null;
        }

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
                continue;
            }
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
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

    void release() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }
}
