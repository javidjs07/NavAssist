package com.example.navassist

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

/** Invisible activity that asks Android for screen-capture consent, then starts the recorder. */
class RecordActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RecordService.recording) {
            startService(Intent(this, RecordService::class.java).setAction(RecordService.ACTION_STOP))
            finish()
            return
        }
        val mgr = getSystemService(MediaProjectionManager::class.java)
        try {
            startActivityForResult(mgr.createScreenCaptureIntent(), 42)
        } catch (e: Exception) {
            Toast.makeText(this, "Screen recording is not available on this phone", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, RecordService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
            startForegroundService(i)
        } else {
            Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
