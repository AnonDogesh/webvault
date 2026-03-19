package com.webvault.browser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: "download_${System.currentTimeMillis()}"
        val id = inputData.getString(KEY_ID) ?: UUID.randomUUID().toString()

        val dao = WebvaultDatabase.get(applicationContext).downloadDao()
        val existing = dao.getById(id)
        val targetFile = File(applicationContext.filesDir, filename)
        val downloaded = existing?.downloadedBytes ?: if (targetFile.exists()) targetFile.length() else 0L

        createNotificationChannel()
        setForeground(createForegroundInfo(filename, downloaded, existing?.totalBytes ?: 0L))

        return withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url(url)
            if (downloaded > 0L) requestBuilder.addHeader("Range", "bytes=$downloaded-")

            val call = OkHttpClient().newCall(requestBuilder.build())
            val response = call.execute()
            if (!response.isSuccessful) return@withContext Result.retry()

            val body = response.body ?: return@withContext Result.retry()
            val totalBytes = (response.header("Content-Length")?.toLongOrNull() ?: 0L) + downloaded

            dao.upsert(
                DownloadEntity(
                    id = id,
                    url = url,
                    filename = filename,
                    totalBytes = totalBytes,
                    downloadedBytes = downloaded,
                    status = "DOWNLOADING",
                    filePath = targetFile.absolutePath
                )
            )

            val stream = body.byteStream()
            val raf = RandomAccessFile(targetFile, "rw")
            raf.seek(downloaded)

            var bytesDownloaded = downloaded
            val buffer = ByteArray(8 * 1024)
            var read = stream.read(buffer)
            while (read != -1) {
                if (isStopped) {
                    dao.upsert(
                        DownloadEntity(
                            id = id,
                            url = url,
                            filename = filename,
                            totalBytes = totalBytes,
                            downloadedBytes = bytesDownloaded,
                            status = "PAUSED",
                            filePath = targetFile.absolutePath
                        )
                    )
                    raf.close()
                    return@withContext Result.retry()
                }

                raf.write(buffer, 0, read.toInt())
                bytesDownloaded += read.toLong()
                dao.upsert(
                    DownloadEntity(
                        id = id,
                        url = url,
                        filename = filename,
                        totalBytes = totalBytes,
                        downloadedBytes = bytesDownloaded,
                        status = "DOWNLOADING",
                        filePath = targetFile.absolutePath
                    )
                )
                setForeground(createForegroundInfo(filename, bytesDownloaded, totalBytes))
                read = stream.read(buffer)
            }

            stream.close()
            raf.close()

            dao.upsert(
                DownloadEntity(
                    id = id,
                    url = url,
                    filename = filename,
                    totalBytes = totalBytes,
                    downloadedBytes = bytesDownloaded,
                    status = "COMPLETE",
                    filePath = targetFile.absolutePath
                )
            )
            Result.success()
        }
    }

    private fun createForegroundInfo(filename: String, done: Long, total: Long): ForegroundInfo {
        val progress = if (total > 0) ((done * 100) / total).toInt() else 0
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $filename")
            .setContentText("$progress%")
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, total <= 0)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1337, notif)
        return ForegroundInfo(1337, notif)
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILENAME = "filename"
        const val KEY_ID = "id"
        private const val CHANNEL_ID = "Downloads"
    }
}
