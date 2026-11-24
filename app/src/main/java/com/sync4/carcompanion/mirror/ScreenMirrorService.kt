package com.sync4.carcompanion.mirror

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.sync4.carcompanion.MainActivity
import com.sync4.carcompanion.R
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.MirrorStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Android 14/15/16+ (API 34-36+) uyumlu MediaProjection ön plan ekran yakalama servisi.
 * Fiat Egea 2020 (Uconnect 7" 800×480 / 1024×600) ve Ford SYNC 4 (720p / 1080p) profillerini destekler.
 */
class ScreenMirrorService : Service() {

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val frameCounter = AtomicLong(0L)
    private val isStopping = AtomicBoolean(false)

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection sistem veya kullanıcı tarafından sonlandırıldı.")
            stopMirrorSessionAndService()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            Log.d(TAG, "Yakalanan içerik boyutu değişti: ${width}x$height")
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            Log.d(TAG, "Yakalanan içerik görünürlüğü: $isVisible")
        }
    }

    override fun onCreate() {
        super.onCreate()
        CompanionStateRepository.init(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMirrorSessionAndService()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME)
                val profile = MirrorProfile.entries.find { it.name == profileName }
                    ?: CompanionStateRepository.selectedProfile.value

                ensureNotificationChannel()

                // Adım 1: Servisi MEDIA_PROJECTION türüyle ön plana yükselt (API 34+ zorunluluğu)
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildForegroundNotification(profile),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } catch (secEx: SecurityException) {
                    Log.e(TAG, "Ön plan MediaProjection servisi başlatılamadı", secEx)
                    CompanionStateRepository.setMirrorStatus(
                        MirrorStatus.Error("MediaProjection izni geçersiz veya süresi doldu.")
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = IntentCompat.getParcelableExtra(
                    intent,
                    EXTRA_RESULT_DATA,
                    Intent::class.java
                )

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    CompanionStateRepository.setMirrorStatus(
                        MirrorStatus.Error("Ekran yakalama izin verisi alınamadı.")
                    )
                    stopMirrorSessionAndService()
                    return START_NOT_STICKY
                }

                startCapturePipeline(resultCode, resultData, profile)
            }
        }

        return START_NOT_STICKY
    }

    private fun startCapturePipeline(
        resultCode: Int,
        resultData: Intent,
        profile: MirrorProfile
    ) {
        releaseCaptureResources(resetStateToIdle = false)
        isStopping.set(false)
        frameCounter.set(0L)

        try {
            val thread = HandlerThread("CarMirrorCaptureThread").apply { start() }
            val handler = Handler(thread.looper)
            captureThread = thread
            captureHandler = handler

            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection örneği oluşturulamadı.")

            // Adım 2 (API 34+ zorunlu): createVirtualDisplay öncesi Callback kaydı
            projection.registerCallback(projectionCallback, handler)
            mediaProjection = projection

            // Adım 3: Kare yakalama hattı
            val reader = ImageReader.newInstance(
                profile.width,
                profile.height,
                PixelFormat.RGBA_8888,
                MAX_IMAGE_BUFFERS
            )
            reader.setOnImageAvailableListener({ availableReader ->
                if (isStopping.get()) return@setOnImageAvailableListener
                try {
                    availableReader.acquireLatestImage()?.use { _ ->
                        val currentCount = frameCounter.incrementAndGet()
                        // Arayüz yeniden çizim yükünü düşük tutmak için her 15 karede (~0.5 sn) sayaç güncelle
                        if (currentCount == 1L || currentCount % 15L == 0L) {
                            CompanionStateRepository.updateCapturedFrames(currentCount)
                        }
                    }
                } catch (ex: IllegalStateException) {
                    Log.v(TAG, "ImageReader kapanırken kare atlandı: ${ex.message}")
                }
            }, handler)
            imageReader = reader

            // Adım 4: Sanal ekranı (VirtualDisplay) oluştur
            virtualDisplay = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                profile.width,
                profile.height,
                profile.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            CompanionStateRepository.setMirrorStatus(
                MirrorStatus.Active(
                    profile = profile,
                    startedAtMillis = System.currentTimeMillis(),
                    capturedFrames = 0L
                )
            )
            Log.i(TAG, "Ekran yansıtma başlatıldı: ${profile.width}x${profile.height} (${profile.densityDpi} dpi)")
        } catch (ex: Exception) {
            Log.e(TAG, "Yakalama hattı başlatılırken hata oluştu", ex)
            CompanionStateRepository.setMirrorStatus(
                MirrorStatus.Error(ex.localizedMessage ?: "Bilinmeyen yakalama hatası")
            )
            stopMirrorSessionAndService()
        }
    }

    private fun stopMirrorSessionAndService() {
        if (!isStopping.compareAndSet(false, true)) return
        releaseCaptureResources(resetStateToIdle = true)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseCaptureResources(resetStateToIdle: Boolean) {
        try {
            virtualDisplay?.release()
        } catch (ex: Exception) {
            Log.w(TAG, "VirtualDisplay serbest bırakılırken uyarı", ex)
        } finally {
            virtualDisplay = null
        }

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (ex: Exception) {
            Log.w(TAG, "ImageReader kapatılırken uyarı", ex)
        } finally {
            imageReader = null
        }

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (ex: Exception) {
            Log.w(TAG, "MediaProjection durdurulurken uyarı", ex)
        } finally {
            mediaProjection = null
        }

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        if (resetStateToIdle && CompanionStateRepository.mirrorStatus.value !is MirrorStatus.Error) {
            CompanionStateRepository.setMirrorStatus(MirrorStatus.Idle)
        }
    }

    override fun onDestroy() {
        isStopping.set(true)
        releaseCaptureResources(resetStateToIdle = true)
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.mirror_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.mirror_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(profile: MirrorProfile): Notification {
        val vehicle = CompanionStateRepository.selectedVehicle.value
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopServicePendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenMirrorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car_notification)
            .setContentTitle(getString(R.string.mirror_notif_title, vehicle.shortTitle))
            .setContentText("${profile.width}×${profile.height} (${profile.targetFps} FPS) yakalama sürüyor")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_car_notification,
                getString(R.string.mirror_notif_stop),
                stopServicePendingIntent
            )
            .build()
    }

    companion object {
        private const val TAG = "ScreenMirrorService"
        private const val CHANNEL_ID = "car_screen_mirror_channel"
        private const val NOTIFICATION_ID = 4004
        private const val VIRTUAL_DISPLAY_NAME = "CarCompanionVirtualDisplay"
        private const val MAX_IMAGE_BUFFERS = 2

        private const val ACTION_START = "com.sync4.carcompanion.mirror.ACTION_START"
        private const val ACTION_STOP = "com.sync4.carcompanion.mirror.ACTION_STOP"

        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val EXTRA_PROFILE_NAME = "extra_profile_name"

        fun start(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            profile: MirrorProfile
        ) {
            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_PROFILE_NAME, profile.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenMirrorService::class.java)
            val wasRunning = context.stopService(intent)
            if (!wasRunning) {
                CompanionStateRepository.setMirrorStatus(MirrorStatus.Idle)
            }
        }
    }
}
