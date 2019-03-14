package com.zcc.mediarecorder.demo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.zcc.mediarecorder.CapturingManager
import com.zcc.mediarecorder.encoder.TextureMovieEncoder2
import com.zcc.mediarecorder.frameproducer.gles.Texture2dProgram

class Camera1Activity : AppCompatActivity(), View.OnClickListener {
    private val recordButton: Button by lazy {
        findViewById<Button>(R.id.btn_record)
    }
    private val encodeButton: Button by lazy {
        findViewById<Button>(R.id.btn_change_codec)
    }
    private val tvVideoPath: TextView by lazy {
        findViewById<TextView>(R.id.tv_video_path)
    }
    private val cameraGLSurface: GLSurfaceView by lazy {
        findViewById<GLSurfaceView>(R.id.sv_camera)
    }
    private val camera1SurfaceRender: Camera1GLSurfaceRender by lazy {
        Camera1GLSurfaceRender(this, cameraGLSurface)
    }
    private val capturingManager: CapturingManager by lazy {
        CapturingManager()
    }

    private var currentEncoderType: TextureMovieEncoder2.EncoderType = TextureMovieEncoder2.EncoderType.MEDIA_RECORDER
    private var isRecordingNow = false

    override fun onStart() {
        super.onStart()
        Log.d("zcc", "onact start")
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        Log.d("zcc", "onact resume")
    }


    override fun onStop() {
        super.onStop()
        Log.d("zcc", "onact stop")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("zcc", "onact create")

        setContentView(R.layout.activity_main)
        recordButton.setOnClickListener(this)
        encodeButton.setOnClickListener(this)
        encodeButton.text = getString(R.string.media_recorder)
        initGLSurface()
        if (isPermissionNotGranted(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_PERMISSION)
        } else {
            initCameraSurface()
        }

    }

    private fun initGLSurface() {
        camera1SurfaceRender.setOnTextureRendListener { textureId ->
            if (capturingManager.isStarted) {
                capturingManager.captureFrame(textureId)
            }
        }
        cameraGLSurface.setEGLConfigChooser(8, 8, 8,
                8, 16, 0)
        cameraGLSurface.setZOrderMediaOverlay(true)
        cameraGLSurface.holder.setFormat(PixelFormat.RGBA_8888)
        cameraGLSurface.setZOrderOnTop(true)
        cameraGLSurface.setEGLContextClientVersion(2)
        cameraGLSurface.setRenderer(camera1SurfaceRender)
        cameraGLSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_record -> isRecordingNow = doRecordStuff(isRecordingNow)
            R.id.btn_change_codec -> {
                if (currentEncoderType == TextureMovieEncoder2.EncoderType.MEDIA_CODEC) {
                    currentEncoderType = TextureMovieEncoder2.EncoderType.MEDIA_RECORDER
                    encodeButton.text = getString(R.string.media_recorder)
                } else {
                    currentEncoderType = TextureMovieEncoder2.EncoderType.MEDIA_CODEC
                    encodeButton.text = getString(R.string.media_codec)
                }
                Toast.makeText(this@Camera1Activity,
                        "you should re record to make this change happen", Toast.LENGTH_LONG).show()
            }
            else -> Log.e(TAG, "no view id matched")
        }
    }

    private fun doRecordStuff(isRecordingNow: Boolean): Boolean {
        if (isRecordingNow) {
            recordButton.text = resources.getString(R.string.start_recording)
            camera1SurfaceRender.runOnGLThread { capturingManager.stopCapturing() }
            Toast.makeText(this,
                    "record succeed! file at " + CapturingManager.getDirectoryDCIM()
                            + TEST_FILE_NAME, Toast.LENGTH_LONG).show()
        } else {
            recordButton.setText(R.string.stop_recording)
            val path = CapturingManager.getDirectoryDCIM() + TEST_FILE_NAME
            tvVideoPath.text = path
            camera1SurfaceRender.runOnGLThread {
                capturingManager.initCapturing(camera1SurfaceRender.cameraW, camera1SurfaceRender.cameraH,
                        path,
                        Texture2dProgram.ProgramType.TEXTURE_EXT, currentEncoderType, null)
                capturingManager.startCapturing()
            }
        }
        return !isRecordingNow
    }

    private fun initCameraSurface() {
        cameraGLSurface.onResume()
        camera1SurfaceRender.initCamera()
    }

    override fun onResume() {
        super.onResume()
        cameraGLSurface.onResume()
        Log.d("zcc", "onact onreusme")
    }

    override fun onPause() {
        cameraGLSurface.onPause()
        Log.d("zcc", "onact onpause")
        super.onPause()
    }

    override fun onRestart() {
        super.onRestart()
        Log.d("zcc", "onact restat")
    }

    override fun onDestroy() {
        capturingManager.release()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            var count = 0
            for (r in permissions) {
                if (r == Manifest.permission.CAMERA) {
                    count++
                }
                if (r == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                    count++
                }
                if (r == Manifest.permission.RECORD_AUDIO) {
                    count++
                }
                if (count == permissions.size) {
                    initCameraSurface()
                }
            }
        }
    }

    companion object {
        private const val TAG = "Camera1Act"
        private const val REQUEST_CODE_PERMISSION = 1
        private val PERMISSIONS = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val TEST_FILE_NAME = "test.mp4"

        fun isPermissionNotGranted(context: Context, permissions: Array<String>): Boolean {
            for (permission in permissions) {
                val result = ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                if (!result) {
                    return false
                }
            }
            return true
        }
    }
}

