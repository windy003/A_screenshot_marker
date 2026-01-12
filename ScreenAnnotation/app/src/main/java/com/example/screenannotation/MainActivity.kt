package com.example.screenannotation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        permissionButton = findViewById(R.id.permissionButton)

        permissionButton.setOnClickListener {
            requestOverlayPermission()
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        if (Settings.canDrawOverlays(this)) {
            statusText.text = getString(R.string.permission_granted)
            permissionButton.text = getString(R.string.permission_granted_button)
            permissionButton.isEnabled = false
        } else {
            statusText.text = getString(R.string.permission_required)
            permissionButton.text = getString(R.string.grant_permission)
            permissionButton.isEnabled = true
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.permission_granted_toast, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.permission_denied_toast, Toast.LENGTH_SHORT).show()
            }
            updateUI()
        }
    }
}
