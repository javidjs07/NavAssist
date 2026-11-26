package com.example.navassist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.Manifest
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Screen recorder (MediaProjection + MediaRecorder). Saves to Movies/NavAssist. */
class RecordService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var paused = false
    private var withMic = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (recording) stopRecording(true) else stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                togglePause()
                return START_NOT_STICKY
            }
        }
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null || code == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        withMic = Prefs.recSound(this) == 1 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (withMic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(NOTIF_ID, buildNotification(), type)
        // give the consent dialog a moment to disappear so it is not in the video
        handler.postDelayed({ begin(code, data) }, 700)
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent) {
        try {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            val proj = mgr.getMediaProjection(code, data)
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { if (recording) stopRecording(true) }
                }
            }, handler)
            projection = proj

            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val shortSide = min(bounds.width(), bounds.height())
            val target = when (Prefs.recRes(this)) {
                1 -> 1080
                2 -> 720
                3 -> 480
                else -> shortSide
            }
            val scale = min(1f, target.toFloat() / shortSide)
            val w = (bounds.width() * scale).toInt() / 2 * 2
            val h = (bounds.height() * scale).toInt() / 2 * 2
            val fps = if (Prefs.recFps(this) == 1) 60 else 30
            val mbps = when (Prefs.recBitrate(this)) {
                0 -> 4
                2 -> 12
                else -> 8
            }

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME,
                    "NavAssist_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NavAssist")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val u = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create the video file")
            uri = u
            val fd = contentResolver.openFileDescriptor(u, "w")
                ?: throw IllegalStateException("Could not open the video file")
            pfd = fd

            val rec = MediaRecorder(this)
            if (withMic) rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withMic) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128000)
                rec.setAudioSamplingRate(44100)
            }
            rec.setVideoSize(w, h)
            rec.setVideoFrameRate(fps)
            rec.setVideoEncodingBitRate(mbps * 1_000_000)
            rec.setOutputFile(fd.fileDescriptor)
            rec.prepare()
            recorder = rec

            display = proj.createVirtualDisplay(
                "NavAssist", w, h, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, null
            )
            rec.start()
            recording = true
            OverlayService.instance?.onRecordingState(true)
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start recording: ${e.message}", Toast.LENGTH_LONG).show()
            cleanup(deleteFile = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun togglePause() {
        val r = recorder ?: return
        try {
            if (paused) r.resume() else r.pause()
            paused = !paused
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            Toast.makeText(this, "Pause is not supported here", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording(save: Boolean) {
        var ok = save
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            ok = false // recording was too short to produce a file
        }
        cleanup(deleteFile = !ok)
        Toast.makeText(
            this, if (ok) "Saved to Movies/NavAssist" else "Recording was too short to save",
            Toast.LENGTH_LONG
        ).show()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup(deleteFile: Boolean) {
        recording = false
        paused = false
        OverlayService.instance?.onRecordingState(false)
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        runCatching { display?.release() }
        runCatching { projection?.stop() }
        runCatching { pfd?.close() }
        recorder = null
        display = null
        projection = null
        pfd = null
        uri?.let {
            try {
                if (deleteFile) {
                    contentResolver.delete(it, null, null)
                } else {
                    val v = ContentValues()
                    v.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(it, v, null, null)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        uri = null
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 11, Intent(this, RecordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val pause = PendingIntent.getService(
            this, 12, Intent(this, RecordService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(if (paused) "Recording paused" else "Recording screen")
            .setContentText("Tap Stop when you are done")
            .setUsesChronometer(!paused)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_record), if (paused) "Resume" else "Pause", pause
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_close), "Stop", stop
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        if (recording) cleanup(deleteFile = false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.navassist.REC_STOP"
        const val ACTION_PAUSE = "com.example.navassist.REC_PAUSE"
        private const val CHANNEL_ID = "record_channel"
        private const val NOTIF_ID = 21

        @Volatile
        var recording = false
    }
}
