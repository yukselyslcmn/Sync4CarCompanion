package com.sync4.carcompanion.mirror

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.Image
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import android.webkit.WebView
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.core.graphics.createBitmap
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorStatus
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Telefon ekranındaki aktif YouTube / WebView görüntüsünü (donanım hızlandırmalı HTML5 video
 * yüzeyi dahil) veya MediaProjection sanal ekran karelerini doğrudan Android Auto
 * (Fiat Egea Uconnect 7" / Ford SYNC 4 / DHU) [Surface] yüzeyine gerçek zamanlı çizen
 * video-yansıtma motoru.
 *
 * Önemli Mimari Detay:
 * Standart `webView.draw(canvas)` yalnızca yazılımsal DOM katmanını çizer ve donanım
 * hızlandırmalı HTML5 `<video>` katmanını siyah bırakır. Bu yüzden [CarSurfaceRenderer]:
 * 1. [PixelCopy.request] ile `Activity.window` GPU kompozitör tamponunu doğrudan yakalar
 *    (böylece YouTube videosunun oynayan kareleri birebir yakalanır).
 * 2. Eğer [ScreenMirrorService] (`MediaProjection`) aktifse, sistem sanal ekranından gelen
 *    `RGBA_8888` kareleri doğrudan çizer.
 * 3. Yedek (fallback) olarak `webView.draw(canvas)` kullanır.
 */
object CarSurfaceRenderer : SurfaceCallback {

    private const val TAG = "CarSurfaceRenderer"
    private const val CAPTURE_INTERVAL_MS = 45L // ~22 FPS akıcı video yakalama

    private val surfaceLock = Any()

    @Volatile
    private var activeSurface: Surface? = null

    @Volatile
    private var surfaceWidth: Int = 0

    @Volatile
    private var surfaceHeight: Int = 0

    @Volatile
    private var visibleRect: Rect = Rect()

    @Volatile
    private var latestFrameBitmap: Bitmap? = null

    @Volatile
    private var webViewRef: WeakReference<WebView>? = null

    @Volatile
    private var captureRootRef: WeakReference<View>? = null

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isLoopRunning = AtomicBoolean(false)
    private val isPixelCopyInFlight = AtomicBoolean(false)
    private val renderedFrameCount = AtomicLong(0L)

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 15, 23, 42)
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        isFakeBoldText = true
    }
    private val hudSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(148, 163, 184)
        textSize = 15f
    }

    /**
     * Telefon tarafındaki aktif [WebView] ve kapsayıcı görünümü kaydeder; böylece MediaProjection
     * izni beklenmeden dahi YouTube videosu anında araç ekranında canlı oynatılır.
     */
    fun attachPhoneViews(webView: WebView, rootContainer: View? = null) {
        webViewRef = WeakReference(webView)
        captureRootRef = if (rootContainer != null) WeakReference(rootContainer) else null
        ensureRenderLoop()
    }

    fun detachPhoneViews(webView: WebView) {
        if (webViewRef?.get() === webView) {
            webViewRef = null
            captureRootRef = null
        }
    }

    /**
     * Araç ekranındaki "Oynat / Duraklat" butonundan telefonun aktif YouTube / HTML5 `<video>`
     * öğesini doğrudan oynatır veya duraklatır.
     */
    fun togglePlayPauseOnPhone() {
        val wv = webViewRef?.get() ?: return
        wv.post {
            wv.evaluateJavascript(
                """
                (function() {
                    var v = document.querySelector('video');
                    if (v) {
                        if (v.paused) { v.play(); } else { v.pause(); }
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    /**
     * Araç ekranından yapılan tıklamayı telefonun WebView koordinatlarına dönüştürerek iletir.
     */
    override fun onClick(x: Float, y: Float) {
        val wv = webViewRef?.get() ?: return
        val sw = surfaceWidth
        val sh = surfaceHeight
        if (sw <= 0 || sh <= 0) return

        wv.post {
            if (wv.width <= 0 || wv.height <= 0) return@post
            val targetX = (x / sw.toFloat()) * wv.width
            val targetY = (y / sh.toFloat()) * wv.height
            val now = android.os.SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(
                now,
                now,
                android.view.MotionEvent.ACTION_DOWN,
                targetX,
                targetY,
                0
            )
            val up = android.view.MotionEvent.obtain(
                now,
                now + 40,
                android.view.MotionEvent.ACTION_UP,
                targetX,
                targetY,
                0
            )
            wv.dispatchTouchEvent(down)
            wv.dispatchTouchEvent(up)
            down.recycle()
            up.recycle()
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        val wv = webViewRef?.get() ?: return
        wv.post {
            wv.scrollBy(distanceX.toInt(), distanceY.toInt())
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface
        val width = surfaceContainer.width
        val height = surfaceContainer.height
        if (surface == null || width <= 0 || height <= 0) {
            Log.w(TAG, "Araç yüzeyi henüz hazır değil: ${width}x$height")
            return
        }

        synchronized(surfaceLock) {
            activeSurface = surface
            surfaceWidth = width
            surfaceHeight = height
            visibleRect = Rect(0, 0, width, height)
        }
        Log.i(TAG, "Araç video yüzeyi bağlandı: ${width}x$height (${surfaceContainer.dpi} dpi)")
        ensureRenderLoop()
        drawCurrentFrameToSurface()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        if (!visibleArea.isEmpty) {
            synchronized(surfaceLock) {
                visibleRect = Rect(visibleArea)
            }
            drawCurrentFrameToSurface()
        }
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        // Tam ekran video deneyimi için tüm görünür alanı kullanıyoruz
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        synchronized(surfaceLock) {
            activeSurface = null
            surfaceWidth = 0
            surfaceHeight = 0
        }
        stopRenderLoop()
        Log.i(TAG, "Araç video yüzeyi ayrıldı.")
    }

    /**
     * [ScreenMirrorService] içindeki `ImageReader`'dan gelen `RGBA_8888` donanım ekran karesini
     * [Bitmap]'e dönüştürüp anında araç ekranındaki [Surface] üzerine çizer.
     */
    fun onMediaProjectionImage(image: Image): Boolean {
        val plane = image.planes.firstOrNull() ?: return false
        val buffer: ByteBuffer = plane.buffer ?: return false
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return false

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + if (pixelStride > 0) rowPadding / pixelStride else 0

        return try {
            buffer.rewind()
            val rawBitmap = createBitmap(paddedWidth.coerceAtLeast(width), height)
            rawBitmap.copyPixelsFromBuffer(buffer)
            val cleanBitmap = if (paddedWidth > width) {
                Bitmap.createBitmap(rawBitmap, 0, 0, width, height).also {
                    rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }
            latestFrameBitmap = cleanBitmap
            drawBitmapToCarSurface(cleanBitmap)
            true
        } catch (ex: Exception) {
            Log.w(TAG, "MediaProjection karesi işlenemedi: ${ex.message}")
            false
        }
    }

    /**
     * Doğrudan bir [Bitmap] karesini araç ekranına gönderir.
     */
    fun submitBitmapFrame(bitmap: Bitmap) {
        latestFrameBitmap = bitmap
        drawBitmapToCarSurface(bitmap)
    }

    private fun ensureRenderLoop() {
        if (activeSurface == null) return
        if (!isLoopRunning.compareAndSet(false, true)) return

        val thread = HandlerThread("CarVideoSurfaceThread").apply { start() }
        val handler = Handler(thread.looper)
        renderThread = thread
        renderHandler = handler

        val tick = object : Runnable {
            override fun run() {
                if (!isLoopRunning.get() || activeSurface == null) return
                val mirrorStatus = CompanionStateRepository.mirrorStatus.value
                if (mirrorStatus !is MirrorStatus.Active) {
                    captureFromAttachedViewAndDraw()
                } else {
                    drawCurrentFrameToSurface()
                }
                handler.postDelayed(this, CAPTURE_INTERVAL_MS)
            }
        }
        handler.post(tick)
    }

    private fun stopRenderLoop() {
        isLoopRunning.set(false)
        isPixelCopyInFlight.set(false)
        renderHandler?.removeCallbacksAndMessages(null)
        renderThread?.quitSafely()
        renderThread = null
        renderHandler = null
    }

    private fun findActivity(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun captureFromAttachedViewAndDraw() {
        val targetView = captureRootRef?.get()?.takeIf { it.width > 0 && it.height > 0 }
            ?: webViewRef?.get()?.takeIf { it.width > 0 && it.height > 0 }

        if (targetView == null) {
            drawCurrentFrameToSurface()
            return
        }

        val targetW = (surfaceWidth.takeIf { it > 0 } ?: 800).coerceAtMost(1280)
        val targetH = (surfaceHeight.takeIf { it > 0 } ?: 480).coerceAtMost(720)
        val bgHandler = renderHandler ?: mainHandler

        // Donanım hızlandırmalı HTML5 YouTube video yüzeyini yakalamak için PixelCopy kullan
        val activity = findActivity(targetView.context)
        val window = activity?.window
        if (window != null &&
            targetView.isAttachedToWindow &&
            isPixelCopyInFlight.compareAndSet(false, true)
        ) {
            targetView.post {
                try {
                    val vw = targetView.width
                    val vh = targetView.height
                    if (vw <= 0 || vh <= 0 || !targetView.isAttachedToWindow) {
                        isPixelCopyInFlight.set(false)
                        return@post
                    }

                    val loc = IntArray(2)
                    targetView.getLocationInWindow(loc)
                    val srcRect = Rect(loc[0], loc[1], loc[0] + vw, loc[1] + vh)

                    val scale = minOf(
                        targetW.toFloat() / vw.toFloat(),
                        targetH.toFloat() / vh.toFloat()
                    ).coerceIn(0.25f, 1.0f)
                    val bmpW = (vw * scale).toInt().coerceAtLeast(1)
                    val bmpH = (vh * scale).toInt().coerceAtLeast(1)
                    val destBitmap = createBitmap(bmpW, bmpH)

                    PixelCopy.request(
                        window,
                        srcRect,
                        destBitmap,
                        { copyResult ->
                            isPixelCopyInFlight.set(false)
                            if (copyResult == PixelCopy.SUCCESS) {
                                latestFrameBitmap = destBitmap
                                drawBitmapToCarSurface(destBitmap)
                            } else {
                                fallbackSoftwareCapture(targetView, bmpW, bmpH, scale)
                            }
                        },
                        bgHandler
                    )
                } catch (ex: Exception) {
                    isPixelCopyInFlight.set(false)
                    Log.v(TAG, "PixelCopy yakalama hatası, yazılım moduna geçiliyor: ${ex.message}")
                    val vw = targetView.width.coerceAtLeast(1)
                    val vh = targetView.height.coerceAtLeast(1)
                    val scale = minOf(
                        targetW.toFloat() / vw.toFloat(),
                        targetH.toFloat() / vh.toFloat()
                    ).coerceIn(0.25f, 1.0f)
                    fallbackSoftwareCapture(
                        targetView,
                        (vw * scale).toInt().coerceAtLeast(1),
                        (vh * scale).toInt().coerceAtLeast(1),
                        scale
                    )
                }
            }
            return
        }

        if (!isPixelCopyInFlight.get()) {
            targetView.post {
                val vw = targetView.width
                val vh = targetView.height
                if (vw <= 0 || vh <= 0) return@post
                val scale = minOf(
                    targetW.toFloat() / vw.toFloat(),
                    targetH.toFloat() / vh.toFloat()
                ).coerceIn(0.25f, 1.0f)
                fallbackSoftwareCapture(
                    targetView,
                    (vw * scale).toInt().coerceAtLeast(1),
                    (vh * scale).toInt().coerceAtLeast(1),
                    scale
                )
            }
        }
    }

    private fun fallbackSoftwareCapture(
        targetView: View,
        bmpW: Int,
        bmpH: Int,
        scale: Float
    ) {
        try {
            val captured = createBitmap(bmpW, bmpH)
            val canvas = Canvas(captured)
            canvas.scale(scale, scale)
            targetView.draw(canvas)
            latestFrameBitmap = captured
            renderHandler?.post {
                drawBitmapToCarSurface(captured)
            }
        } catch (ex: Exception) {
            Log.v(TAG, "Yazılım tabanlı WebView yakalama atlandı: ${ex.message}")
        }
    }

    private fun drawCurrentFrameToSurface() {
        val bmp = latestFrameBitmap
        if (bmp != null && !bmp.isRecycled) {
            drawBitmapToCarSurface(bmp)
        } else {
            drawPlaceholderToCarSurface()
        }
    }

    private fun drawBitmapToCarSurface(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        synchronized(surfaceLock) {
            val surface = activeSurface ?: return
            if (!surface.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return

            val canvas: Canvas = try {
                surface.lockCanvas(null)
            } catch (ex: Exception) {
                Log.v(TAG, "Surface lockCanvas başarısız: ${ex.message}")
                return
            }

            try {
                canvas.drawColor(Color.BLACK)

                val bw = bitmap.width.toFloat()
                val bh = bitmap.height.toFloat()
                val sw = surfaceWidth.toFloat()
                val sh = surfaceHeight.toFloat()

                if (bw > 0f && bh > 0f) {
                    val browserUrl = CompanionStateRepository.browserState.value.currentUrl
                    val isYoutubeWatch = browserUrl.contains("youtube.com/watch") ||
                        browserUrl.contains("youtu.be/")

                    if (isYoutubeWatch && bh > bw * 1.15f) {
                        // Dikey telefon ekranındaki YouTube video oynatıcı bölgesini (üst %48'lik video + başlık alanı)
                        // araç ekranına (16:9) genişçe odakla
                        val cropTop = 0
                        val cropBottom = (bh * 0.52f).toInt().coerceAtMost(bitmap.height)
                        val srcRect = Rect(0, cropTop, bitmap.width, cropBottom)
                        val srcW = srcRect.width().toFloat()
                        val srcH = srcRect.height().toFloat()

                        val scale = minOf(sw / srcW, sh / srcH)
                        val dstW = srcW * scale
                        val dstH = srcH * scale
                        val left = (sw - dstW) / 2f
                        val top = (sh - dstH) / 2f
                        val dstRect = RectF(left, top, left + dstW, top + dstH)
                        canvas.drawBitmap(bitmap, srcRect, dstRect, bitmapPaint)
                    } else {
                        val scale = minOf(sw / bw, sh / bh)
                        val dstW = bw * scale
                        val dstH = bh * scale
                        val left = (sw - dstW) / 2f
                        val top = (sh - dstH) / 2f

                        val matrix = Matrix().apply {
                            postScale(scale, scale)
                            postTranslate(left, top)
                        }
                        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
                    }
                }

                val count = renderedFrameCount.incrementAndGet()
                if (count == 1L || count % 15L == 0L) {
                    val currentStatus = CompanionStateRepository.mirrorStatus.value
                    if (currentStatus is MirrorStatus.Active) {
                        CompanionStateRepository.updateCapturedFrames(count)
                    }
                }
            } finally {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (ex: Exception) {
                    Log.v(TAG, "Surface unlockCanvasAndPost hatası: ${ex.message}")
                }
            }
        }
    }

    private fun drawPlaceholderToCarSurface() {
        synchronized(surfaceLock) {
            val surface = activeSurface ?: return
            if (!surface.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return

            val canvas: Canvas = try {
                surface.lockCanvas(null)
            } catch (_: Exception) {
                return
            }

            try {
                canvas.drawColor(Color.rgb(15, 23, 42))
                val browserState = CompanionStateRepository.browserState.value
                val vehicle = CompanionStateRepository.selectedVehicle.value
                val cx = 48f
                val cy = surfaceHeight / 2f - 20f
                canvas.drawRect(
                    24f,
                    cy - 45f,
                    (surfaceWidth - 24).toFloat(),
                    cy + 65f,
                    hudBgPaint
                )
                canvas.drawText(
                    "${vehicle.shortTitle} • Canlı Video & Ekran Yansıtma Hazır",
                    cx,
                    cy,
                    hudTextPaint
                )
                canvas.drawText(
                    "Aktif Sayfa: ${browserState.pageTitle} (${browserState.currentUrl})",
                    cx,
                    cy + 32f,
                    hudSubTextPaint
                )
            } finally {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }
    }
}
