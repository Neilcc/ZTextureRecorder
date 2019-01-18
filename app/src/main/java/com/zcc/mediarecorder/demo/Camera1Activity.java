package com.zcc.mediarecorder.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.zcc.mediarecorder.CapturingManager;
import com.zcc.mediarecorder.encoder.TextureMovieEncoder2;
import com.zcc.mediarecorder.gles.Texture2dProgram;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Camera1Activity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "Camera1Act";
    private static final int REQUEST_CODE_PERMISSION = 1;
    private static final String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };
    private static final String TEST_FILE_NAME = "test.mp4";
    private Button recordButton;
    private Button encodeButton;
    private GLSurfaceView cameraGLSurface;

    private boolean isRecordingNow = false;
    private Camera1GLHelper camera1Helper;
    private CapturingManager capturingManager;
    private TextureMovieEncoder2.Encoder currentEncoder = TextureMovieEncoder2.Encoder.MEDIA_RECORDER;

    public static boolean isPermissionNotGranted(Context context, String[] permissions) {
        for (String permission : permissions) {
            boolean result = ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED;
            if (!result) {
                return false;
            }
        }
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraGLSurface = findViewById(R.id.sv_camera);
        recordButton = findViewById(R.id.btn_record);
        recordButton.setOnClickListener(this);
        encodeButton = findViewById(R.id.btn_change_codec);
        encodeButton.setOnClickListener(this);
        encodeButton.setText(getString(R.string.media_recorder));
        initGLSurface();
        if (isPermissionNotGranted(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_PERMISSION);
        } else {
            initCameraSurface();
        }
    }

    private void initGLSurface() {
        camera1Helper = new Camera1GLHelper(this, cameraGLSurface);
        camera1Helper.setOnTextureRendListener(new Camera1GLHelper.OnTextureRendListener() {
            @Override
            public void onFrame(int textureId) {
                if (capturingManager != null && capturingManager.isStarted()) {
                    capturingManager.captureFrame(textureId);
                }
            }
        });
        cameraGLSurface.setEGLConfigChooser(8, 8, 8,
                8, 16, 0);
        cameraGLSurface.setZOrderMediaOverlay(true);
        cameraGLSurface.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraGLSurface.setZOrderOnTop(true);
        cameraGLSurface.setEGLContextClientVersion(2);
        cameraGLSurface.setRenderer(camera1Helper);
        cameraGLSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_record:
                isRecordingNow = doRecordStuff(isRecordingNow);
                break;
            case R.id.btn_change_codec:
                if (currentEncoder == TextureMovieEncoder2.Encoder.MEDIA_CODEC) {
                    currentEncoder = TextureMovieEncoder2.Encoder.MEDIA_RECORDER;
                    encodeButton.setText(getString(R.string.media_recorder));
                } else {
                    currentEncoder = TextureMovieEncoder2.Encoder.MEDIA_CODEC;
                    encodeButton.setText(getString(R.string.media_codec));
                }
                Toast.makeText(Camera1Activity.this,
                        "you shuld rerecord to make this change happen", Toast.LENGTH_LONG).show();
                break;
            default:
                Log.e(TAG, "no view id matched");
                break;
        }
    }

    private boolean doRecordStuff(boolean isRecordingNow) {
        if (isRecordingNow) {
            recordButton.setText(getResources().getString(R.string.start_recording));
            camera1Helper.runOnGLThread(new Runnable() {
                @Override
                public void run() {
                    capturingManager.stopCapturing();
                }
            });
            Toast.makeText(this,
                    "record successed! file at " + CapturingManager.getDirectoryDCIM()
                            + TEST_FILE_NAME, Toast.LENGTH_LONG).show();
        } else {
            recordButton.setText(R.string.stop_recording);
            capturingManager = new CapturingManager();
            final String path = CapturingManager.getDirectoryDCIM() + TEST_FILE_NAME;
            camera1Helper.runOnGLThread(new Runnable() {
                @Override
                public void run() {
                    capturingManager.initCapturing(camera1Helper.getCameraW(), camera1Helper.getCameraH(),
                            path,
                            Texture2dProgram.ProgramType.TEXTURE_EXT, currentEncoder,
                            null);
                    capturingManager.startCapturing();
                }
            });
        }
        return !isRecordingNow;
    }

    private void initCameraSurface() {
        cameraGLSurface.onResume();
        camera1Helper.initCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraGLSurface.onResume();
    }

    @Override
    protected void onPause() {
        cameraGLSurface.onPause();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION) {
            int count = 0;
            for (String r : permissions) {
                if (r.equals(Manifest.permission.CAMERA)) {
                    count++;
                }
                if (r.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    count++;
                }
                if (r.equals(Manifest.permission.RECORD_AUDIO)) {
                    count++;
                }
                if (count == permissions.length) {
                    initCameraSurface();
                }
            }
        }
    }
}
