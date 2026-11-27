package com.example.navassist

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File
import java.text.DecimalFormat

/** Storage helpers: free space, NavAssist cache, NavAssist recordings, and (with permission) big media files. */
object Cleaner {

    data class Media(
        val id: Long, val uri: Uri, val name: String, val size: Long,
        val durationMs: Long, val dateMs: Long, val isVideo: Boolean
    )

    fun totalFree(): Pair<Long, Long> {
        val st = StatFs(Environment.getDataDirectory().path)
        return st.totalBytes to st.availableBytes
    }

    fun fmt(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val df = DecimalFormat("#.#")
        val kb = bytes / 1024.0
        if (kb < 1024) return df.format(kb) + " KB"
        val mb = kb / 1024.0
        if (mb < 1024) return df.format(mb) + " MB"
        return df.format(mb / 1024.0) + " GB"
    }

    // ---- cache
    private fun dirSize(f: File): Long =
        if (f.isFile) f.length() else (f.listFiles()?.sumOf { dirSize(it) } ?: 0L)

    fun cacheBytes(c: Context): Long = dirSize(c.cacheDir) + (c.externalCacheDir?.let { dirSize(it) } ?: 0L)

    fun clearCache(c: Context): Long {
        val before = cacheBytes(c)
        c.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        c.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        return before
    }

    // ---- permissions for scanning other apps' media
    fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun hasMediaAccess(c: Context): Boolean =
        mediaPermissions().all { c.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    // ---- queries
    /** NavAssist's own recordings: readable and deletable without any permission. */
    fun recordings(c: Context): List<Media> = query(
        c, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true,
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("Movies/NavAssist/%")
    )

    fun largeVideos(c: Context, minBytes: Long = 100L * 1024 * 1024): List<Media> = query(
        c, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true,
        "${MediaStore.MediaColumns.SIZE} >= ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?",
        arrayOf(minBytes.toString(), "Movies/NavAssist/%")
    )

    fun screenshots(c: Context): List<Media> = query(
        c, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false,
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("%Screenshots%")
    )

    private fun query(c: Context, base: Uri, video: Boolean, sel: String, args: Array<String>, limit: Int = 200): List<Media> {
        val out = ArrayList<Media>()
        val proj = if (video) arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.Video.VideoColumns.DURATION
        ) else arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        try {
            c.contentResolver.query(base, proj, sel, args, "${MediaStore.MediaColumns.SIZE} DESC")?.use { cur ->
                while (cur.moveToNext() && out.size < limit) {
                    val id = cur.getLong(0)
                    out.add(
                        Media(
                            id, ContentUris.withAppendedId(base, id), cur.getString(1) ?: "file", cur.getLong(2),
                            if (video) cur.getLong(4) else 0L, cur.getLong(3) * 1000L, video
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // no permission yet
        } catch (e: Exception) {
            // ignore unreadable rows
        }
        return out
    }

    /** Deletes a file NavAssist created itself (no confirmation needed). */
    fun deleteOwn(cr: ContentResolver, uri: Uri): Boolean = try {
        cr.delete(uri, null, null) > 0
    } catch (e: Exception) {
        false
    }
}
