package com.mikael.launcherai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.regex.Pattern

/**
 * Reads the Minecraft F3 overlay from the user's screen-share session.
 * Captured frames are processed in memory and never saved. Values are only
 * recorded when OCR finds an explicit "NN fps" / "fps: NN" text line.
 */
class FpsCaptureService : Service() {
    companion object {
        const val ACTION_STOP = "com.mikael.launcherai.STOP_FPS_CAPTURE"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        private const val CHANNEL_ID = "launcher_ai_fps_capture"
        private const val NOTIFICATION_ID = 4201
        private const val PREFS = "launcher_ai_metrics"
        private const val MAX_SAMPLES = 1800
    }

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var startElapsedRealtime = 0L
    private var lastOcrElapsedRealtime = 0L
    private var finalized = false
    private var lastMatchedLine = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishCapture()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY

        createNotificationChannel()
        val notification = buildNotification("Aguardando a leitura real do FPS do Minecraft.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
            prefs.edit().putBoolean("capturing", false)
                .putString("state", "Captura de tela não autorizada.")
                .apply()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                prefs.edit().putBoolean("capturing", false)
                    .putString("state", "O Android não iniciou a captura de tela.")
                    .apply()
                stopSelf()
                return START_NOT_STICKY
            }
            startElapsedRealtime = SystemClock.elapsedRealtime()
            lastOcrElapsedRealtime = 0L
            finalized = false
            prefs.edit()
                .putBoolean("capturing", true)
                .putString("samples", "")
                .putInt("last_fps", -1)
                .putString("last_line", "")
                .putString("state", "Capturando a tela. Entre no mundo e deixe o F3 visível.")
                .putLong("test_start_wall", System.currentTimeMillis())
                .putLong("test_start_elapsed", startElapsedRealtime)
                .apply()

            workerThread = HandlerThread("LauncherAIFpsOCR").also { it.start() }
            worker = Handler(workerThread!!.looper)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val density = metrics.densityDpi
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader -> processNextImage(reader) }, worker)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post { finishCapture() }
                }
            }, mainHandler)
            virtualDisplay = projection?.createVirtualDisplay(
                "LauncherAI-FPS",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                worker
            )
            if (virtualDisplay == null) {
                prefs.edit().putString("state", "Não foi possível iniciar a captura virtual.").apply()
                finishCapture()
            }
        } catch (e: Exception) {
            prefs.edit().putString("state", "Erro ao iniciar captura: ${e.javaClass.simpleName}").apply()
            finishCapture()
        }
        return START_NOT_STICKY
    }

    private fun processNextImage(reader: ImageReader) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOcrElapsedRealtime < 900L) {
            val skipped = reader.acquireLatestImage()
            skipped?.close()
            return
        }
        val image = try { reader.acquireLatestImage() } catch (_: Exception) { null } ?: return
        lastOcrElapsedRealtime = now
        var bitmap: Bitmap? = null
        try {
            bitmap = imageToBitmap(image)
        } catch (_: Exception) {
            image.close()
            return
        }
        image.close()
        val frame = bitmap ?: return
        recognizer.process(InputImage.fromBitmap(frame, 0))
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                prefs.edit().putString("last_line", text.take(900)).apply()
                val fps = findFps(text)
                if (fps != null) recordSample(fps, matchingLine(text, fps))
            }
            .addOnFailureListener {
                prefs.edit().putString("state", "Captura ativa; o texto do F3 não pôde ser lido nesta amostra.").apply()
            }
            .addOnCompleteListener {
                if (!frame.isRecycled) frame.recycle()
            }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = rowStride / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == image.width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun findFps(text: String): Int? {
        val numberFirst = Pattern.compile("(?i)\\b(\\d{1,4})\\s*fps\\b")
        val fpsFirst = Pattern.compile("(?i)\\bfps\\s*[:=]\\s*(\\d{1,4})\\b")
        for (line in text.lines()) {
            val matcherA = numberFirst.matcher(line)
            val matcherB = fpsFirst.matcher(line)
            val value = when {
                matcherA.find() -> matcherA.group(1)?.toIntOrNull()
                matcherB.find() -> matcherB.group(1)?.toIntOrNull()
                else -> null
            }
            if (value != null && value in 1..1000) return value
        }
        return null
    }

    private fun matchingLine(text: String, fps: Int): String =
        text.lines().firstOrNull { it.contains("fps", ignoreCase = true) && it.contains(fps.toString()) }
            ?.trim()?.take(250) ?: "FPS lido do overlay: $fps"

    private fun recordSample(fps: Int, line: String) {
        val elapsed = (SystemClock.elapsedRealtime() - startElapsedRealtime).coerceAtLeast(0L)
        val current = prefs.getString("samples", "").orEmpty()
        val entries = current.split(';').filter { it.contains(':') }.toMutableList()
        entries.add("$elapsed:$fps")
        while (entries.size > MAX_SAMPLES) entries.removeAt(0)
        lastMatchedLine = line
        prefs.edit()
            .putString("samples", entries.joinToString(";"))
            .putInt("last_fps", fps)
            .putString("last_line", line)
            .putString("state", "Leitura real do F3: $fps FPS • ${entries.size} amostras")
            .apply()
        val elapsedSeconds = elapsed / 1000
        if (elapsedSeconds % 10L == 0L) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification("FPS lido do F3: $fps • ${entries.size} amostras"))
        }
    }

    private fun finishCapture() {
        if (finalized) {
            stopSelf()
            return
        }
        finalized = true
        val samples = prefs.getString("samples", "").orEmpty()
            .split(';')
            .mapNotNull { item ->
                val pieces = item.split(':')
                if (pieces.size == 2) pieces[1].toIntOrNull() else null
            }
        val history = prefs.getString("history", "").orEmpty()
        if (samples.isNotEmpty()) {
            val average = samples.average()
            val min = samples.minOrNull() ?: 0
            val max = samples.maxOrNull() ?: 0
            val launcher = prefs.getString("selected_launcher", "Launcher não identificado").orEmpty()
                .replace("|", "/").replace("\n", " ")
            val timestamp = prefs.getLong("test_start_wall", System.currentTimeMillis())
            val row = "$timestamp|$launcher|${String.format(Locale.US, "%.2f", average)}|$min|$max|${samples.size}"
            val rows = (history.lines().filter { it.isNotBlank() } + row).takeLast(100)
            prefs.edit().putString("history", rows.joinToString("\n")).apply()
            prefs.edit().putString("last_summary", row).apply()
            prefs.edit().putString(
                "state",
                "Teste encerrado • ${samples.size} leituras reais do F3 • média ${String.format(Locale.US, "%.1f", average)} FPS"
            ).apply()
        } else {
            prefs.edit().putString(
                "state",
                "Nenhuma leitura de FPS reconhecida. Entre no mundo e deixe o texto do F3 visível."
            ).apply()
        }
        prefs.edit().putBoolean("capturing", false).apply()
        releaseCapture()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseCapture() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        try { if (workerThread != null) workerThread?.quitSafely() } catch (_: Exception) {}
        workerThread = null
        worker = null
        try { recognizer.close() } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Medição real de FPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Captura consentida do texto do F3 durante o teste de Minecraft." }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Launcher AI — teste de FPS")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        if (!finalized && projection != null) finishCapture()
        else releaseCapture()
        super.onDestroy()
    }
}
