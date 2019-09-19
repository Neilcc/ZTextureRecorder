package com.zcc.lib.camera;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.Surface;

import java.util.List;

/**
 * Created by cc on 2019-09-19.
 */
public class Camera1Utils {
    private static final double MIN_RATIO_DIFF = 0.2;

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

    public static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        double targetRatio = w * 1.0 / h;
        if (sizes == null) {
            return null;
        }
        Camera.Size optimalSize = null;
        double minRatioDiff = MIN_RATIO_DIFF;
        for (Camera.Size size : sizes) {
            double ratio = size.width * 1.0 / size.height;
            if (Math.abs(targetRatio - ratio) < minRatioDiff) {
                optimalSize = size;
                minRatioDiff = Math.abs(targetRatio - ratio);
            }
        }

        double minDiff = Double.MAX_VALUE;
        if (optimalSize == null) {
            // we find closed height
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
    }

    public static Camera.Size setCameraPreviewSizeAndCodec(int width, int height, Camera camera) {
        List<Camera.Size> mSupportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
        Camera.Size optimalPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        // get Camera parameters
        Camera.Parameters params = camera.getParameters();
        params.setPreviewSize(optimalPreviewSize.width, optimalPreviewSize.height);
        params.setPreviewFormat(ImageFormat.NV21);

        // set Camera parameters
        camera.setParameters(params);
        params = camera.getParameters();
        optimalPreviewSize = params.getPreviewSize();
        return optimalPreviewSize;
    }
}
