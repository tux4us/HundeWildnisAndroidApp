package com.hundewildnis.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ActivityTickLoupeBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class TickLoupeActivity : SecureBaseActivity(), SurfaceHolder.Callback {

    // ─── Kamera ────────────────────────────────────────────────────────────────
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler

    // Sensor-Rotation des Geräts (wird einmalig aus CameraCharacteristics gelesen)
    private var sensorOrientation: Int = 90

    // ─── Fokus ─────────────────────────────────────────────────────────────────
    // Gespeicherter Request-Builder — wird für AF-Updates wiederverwendet
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    // Maximale AF-Regionen laut Gerät (0 = nicht unterstützt)
    private var maxAfRegions: Int = 0

    // Handler auf dem Main-Thread für AF-Reset-Delay
    private val mainHandler = Handler(Looper.getMainLooper())

    // ─── Filter ────────────────────────────────────────────────────────────────
    private enum class FilterMode(val label: String) {
        THERMAL("🌡️ Wärmebild"),
        INVERT("🔲 Invertierung"),
        HIGH_CONTRAST("◑ Hoher Kontrast")
    }

    private var currentFilter = FilterMode.THERMAL
    private val thermalLut: IntArray by lazy { buildThermalLut() }

    // ─── Freeze / Save ─────────────────────────────────────────────────────────
    private var frozenBitmap: Bitmap? = null
    private var isFrozen = false

    // ─── UI ────────────────────────────────────────────────────────────────────
    private lateinit var cameraContainer: FrameLayout
    private lateinit var gridActions: RecyclerView
    private lateinit var tvFilterLabel: TextView
    private lateinit var tvFreezeLabel: TextView

    // ─── Rendering ─────────────────────────────────────────────────────────────
    private val renderPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    companion object {
        private const val PERMISSION_REQUEST = 102
        private const val PREVIEW_WIDTH  = 1280
        private const val PREVIEW_HEIGHT = 720
        // Tap-to-Focus: AF-Region als 15% der Sensorfläche um den Tipp-Punkt
        private const val AF_REGION_FRACTION = 0.15f
        // Nach dieser Zeit zurück zu CONTINUOUS (ms)
        private const val TAP_FOCUS_RESET_MS = 3000L
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tick_loupe)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "🔍 Zeckenlupe"
            setDisplayHomeAsUpEnabled(true)
        }

        cameraContainer = findViewById(R.id.cameraContainer)
        surfaceView     = findViewById(R.id.surfaceView)
        gridActions     = findViewById<RecyclerView>(R.id.gridTickLoupeActions)
        tvFilterLabel   = findViewById(R.id.tvFilterLabel)
        tvFreezeLabel   = findViewById(R.id.tvFreezeLabel)

        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)

        // Kamera-Container: Höhe = Breite × 4/3 (Portrait 4:3)
        cameraContainer.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    cameraContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val w = cameraContainer.width
                    if (w > 0) {
                        val params = cameraContainer.layoutParams
                        params.height = (w * 4f / 3f).toInt()
                        cameraContainer.layoutParams = params
                    }
                }
            }
        )

        updateFilterLabel()
        updateActionGrid()

        // Tap-to-Focus: Touch auf SurfaceView
        surfaceView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && !isFrozen) {
                triggerTapFocus(event.x, event.y,
                    surfaceView.width.toFloat(), surfaceView.height.toFloat())
            }
            true
        }

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onResume() {
        super.onResume()
        startCameraThread()
        if (surfaceHolder.surface.isValid && hasCameraPermission() && !isFrozen) openCamera()
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacksAndMessages(null)
        closeCamera()
        stopCameraThread()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SurfaceHolder.Callback
    // ──────────────────────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (hasCameraPermission() && !isFrozen) openCamera()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) { closeCamera() }

    // ──────────────────────────────────────────────────────────────────────────
    // Kamera öffnen
    // ──────────────────────────────────────────────────────────────────────────

    private fun startCameraThread() {
        cameraThread = HandlerThread("TickLoupeCameraThread").also { it.start() }
        cameraHandler = Handler(cameraThread.looper)
    }

    private fun stopCameraThread() {
        cameraThread.quitSafely()
        try { cameraThread.join() } catch (_: InterruptedException) {}
    }

    private fun hasCameraPermission() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun openCamera() {
        if (!hasCameraPermission()) return

        imageReader = ImageReader.newInstance(
            PREVIEW_WIDTH, PREVIEW_HEIGHT,
            ImageFormat.YUV_420_888, 2
        ).apply {
            setOnImageAvailableListener(normalFrameListener, cameraHandler)
        }

        val manager  = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: run {
            Toast.makeText(this, "Keine Rückkamera gefunden.", Toast.LENGTH_SHORT).show()
            return
        }

        val chars = manager.getCameraCharacteristics(cameraId)
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        maxAfRegions      = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startPreview()
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close(); cameraDevice = null
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close(); cameraDevice = null
                runOnUiThread {
                    Toast.makeText(this@TickLoupeActivity,
                        "Kamerafehler ($error)", Toast.LENGTH_SHORT).show()
                }
            }
        }, cameraHandler)
    }

    private fun startPreview() {
        val reader = imageReader ?: return
        val device = cameraDevice ?: return

        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(reader.surface)
            // CONTINUOUS_VIDEO: Fokus läuft permanent nach, ideal für Nahaufnahmen
            set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON)
        }
        previewRequestBuilder = builder

        device.createCaptureSession(
            listOf(reader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(builder.build(), null, cameraHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    runOnUiThread {
                        Toast.makeText(this@TickLoupeActivity,
                            "Kamera-Vorschau fehlgeschlagen.", Toast.LENGTH_SHORT).show()
                    }
                }
            }, cameraHandler
        )
    }

    private fun closeCamera() {
        mainHandler.removeCallbacksAndMessages(null)
        captureSession?.close(); captureSession = null
        cameraDevice?.close();   cameraDevice  = null
        imageReader?.close();    imageReader   = null
        previewRequestBuilder = null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tap-to-Focus
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Berechnet eine AF-MeteringRectangle aus dem Touch-Punkt auf dem SurfaceView
     * und schickt einen einmaligen AF-Trigger an die CaptureSession.
     * Nach [TAP_FOCUS_RESET_MS] ms wird zurück zu CONTINUOUS_VIDEO gewechselt.
     *
     * Koordinaten-Mapping:
     *   SurfaceView (px) → normalisiert [0..1] → Sensor-Array-Koordinaten
     *   Sensor-Array-Größe kommt aus SENSOR_INFO_ACTIVE_ARRAY_SIZE.
     *   Bei sensorOrientation=90/270 werden X/Y getauscht.
     */
    private fun triggerTapFocus(touchX: Float, touchY: Float,
                                viewW: Float, viewH: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        if (maxAfRegions == 0) return          // Gerät unterstützt keine AF-Regionen

        val manager  = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return
        val chars      = manager.getCameraCharacteristics(cameraId)
        val sensorRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val sensorW    = sensorRect.width().toFloat()
        val sensorH    = sensorRect.height().toFloat()

        // Normalisierte Touch-Position [0..1]
        val normX = (touchX / viewW).coerceIn(0f, 1f)
        val normY = (touchY / viewH).coerceIn(0f, 1f)

        // Bei 90° / 270° Sensor-Rotation: X↔Y tauschen
        val (sensorX, sensorY) = when (sensorOrientation) {
            90  -> Pair(normY * sensorW, (1f - normX) * sensorH)
            270 -> Pair((1f - normY) * sensorW, normX * sensorH)
            180 -> Pair((1f - normX) * sensorW, (1f - normY) * sensorH)
            else -> Pair(normX * sensorW, normY * sensorH)
        }

        // AF-Region: Quadrat mit 15% der Sensorgröße um den Tipp-Punkt
        val halfW = sensorW * AF_REGION_FRACTION / 2f
        val halfH = sensorH * AF_REGION_FRACTION / 2f
        val afRect = android.graphics.Rect(
            (sensorX - halfW).toInt().coerceAtLeast(0),
            (sensorY - halfH).toInt().coerceAtLeast(0),
            (sensorX + halfW).toInt().coerceAtMost(sensorW.toInt()),
            (sensorY + halfH).toInt().coerceAtMost(sensorH.toInt())
        )
        val afRegion = MeteringRectangle(afRect, MeteringRectangle.METERING_WEIGHT_MAX)

        // Schritt 1: AF-Trigger vorbereiten
        builder.apply {
            set(CaptureRequest.CONTROL_AF_MODE,    CaptureRequest.CONTROL_AF_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(afRegion))
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
        }
        session.setRepeatingRequest(builder.build(), null, cameraHandler)

        // Schritt 2: AF auslösen
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START)
        session.capture(builder.build(), null, cameraHandler)

        // Schritt 3: Trigger zurücksetzen (einmalig, nicht dauerhaft im Repeating)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_IDLE)

        // Nach TAP_FOCUS_RESET_MS zurück zu CONTINUOUS_VIDEO
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            resetToContinuousAf()
        }, TAP_FOCUS_RESET_MS)
    }

    /** Stellt CONTINUOUS_VIDEO-AF wieder her und löscht AF-Regionen */
    private fun resetToContinuousAf() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        builder.apply {
            set(CaptureRequest.CONTROL_AF_MODE,    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AF_REGIONS, null)
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
        }
        try {
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        } catch (_: IllegalStateException) {
            // Session bereits geschlossen — kein Fehler
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Frame-Listener
    // ──────────────────────────────────────────────────────────────────────────

    private val normalFrameListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        if (!isFrozen) {
            val raw      = yuvToBitmap(image)
            image.close()
            val rotated  = rotateBitmap(raw, sensorOrientation.toFloat())
            val filtered = applyFilter(rotated, currentFilter)
            drawToSurface(filtered)
        } else {
            image.close()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // YUV_420_888 → Bitmap
    // ──────────────────────────────────────────────────────────────────────────

    private fun yuvToBitmap(image: android.media.Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize   = yBuffer.remaining()
        val uSize   = uBuffer.remaining()
        val vSize   = vBuffer.remaining()
        val nv21    = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0,             ySize)
        vBuffer.get(nv21, ySize,         vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImg = android.graphics.YuvImage(
            nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImg.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rotation
    // ──────────────────────────────────────────────────────────────────────────

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Filter
    // ──────────────────────────────────────────────────────────────────────────

    private fun applyFilter(src: Bitmap, mode: FilterMode): Bitmap = when (mode) {
        FilterMode.THERMAL       -> applyThermal(src)
        FilterMode.INVERT        -> applyColorMatrix(src, buildInvertMatrix())
        FilterMode.HIGH_CONTRAST -> applyColorMatrix(src, buildHighContrastMatrix())
    }

    private fun buildThermalLut(): IntArray {
        val lut = IntArray(256)
        for (i in 0..255) {
            val (r, g, b) = when {
                i < 64  -> Triple(0,
                                  0,
                                  (i * 4).coerceIn(0, 255))
                i < 128 -> Triple(0,
                                  ((i - 64) * 4).coerceIn(0, 255),
                                  255)
                i < 192 -> Triple(((i - 128) * 4).coerceIn(0, 255),
                                  255,
                                  (255 - (i - 128) * 4).coerceIn(0, 255))
                else    -> Triple(255,
                                  (255 - (i - 192) * 4).coerceIn(0, 255),
                                  0)
            }
            lut[i] = Color.rgb(r, g, b)
        }
        return lut
    }

    private fun applyThermal(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c    = pixels[i]
            val gray = (Color.red(c) * 0.299 +
                        Color.green(c) * 0.587 +
                        Color.blue(c)  * 0.114).toInt().coerceIn(0, 255)
            pixels[i] = thermalLut[gray]
        }
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun buildInvertMatrix() = ColorMatrix(floatArrayOf(
        -1f,  0f,  0f, 0f, 255f,
         0f, -1f,  0f, 0f, 255f,
         0f,  0f, -1f, 0f, 255f,
         0f,  0f,  0f, 1f,   0f
    ))

    private fun buildHighContrastMatrix(): ColorMatrix {
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        cm.postConcat(ColorMatrix(floatArrayOf(
            2.5f,  0f,   0f,  0f, -80f,
            0f,   2.5f,  0f,  0f, -80f,
            0f,    0f,  2.5f, 0f, -80f,
            0f,    0f,   0f,  1f,   0f
        )))
        return cm
    }

    private fun applyColorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SurfaceView zeichnen
    // ──────────────────────────────────────────────────────────────────────────

    private fun drawToSurface(bitmap: Bitmap) {
        if (!surfaceHolder.surface.isValid) return
        val canvas = surfaceHolder.lockCanvas() ?: return
        try {
            val cw = canvas.width.toFloat()
            val ch = canvas.height.toFloat()
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val scale   = maxOf(cw / bw, ch / bh)
            val scaledW = bw * scale
            val scaledH = bh * scale
            val left    = (cw - scaledW) / 2f
            val top     = (ch - scaledH) / 2f
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, null,
                RectF(left, top, left + scaledW, top + scaledH), renderPaint)
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Filter-Umschaltung
    // ──────────────────────────────────────────────────────────────────────────

    private fun cycleFilter() {
        val values = FilterMode.entries
        currentFilter = values[(values.indexOf(currentFilter) + 1) % values.size]
        updateFilterLabel()
    }

    private fun updateFilterLabel() {
        tvFilterLabel.text = currentFilter.label
    }

    private fun updateActionGrid() {
        val actions = mutableListOf<StartTile>()
        
        actions.add(StartTile("tick_filter", R.drawable.ic_magnifier, "Filter"))
        
        val freezeLabel = if (isFrozen) "▶ Weiter" else "⏸ Einfrieren"
        val freezeIcon = if (isFrozen) R.drawable.ic_stopwatch else R.drawable.ic_remove
        actions.add(StartTile("tick_freeze", freezeIcon, freezeLabel))
        
        if (isFrozen) {
            actions.add(StartTile("tick_save", R.drawable.ic_notes, "Speichern"))
        }

        val colors = StartGroupColors.forGroup(this, 1) // Ocker/Gelb
        gridActions.layoutManager = GridLayoutManager(this, if (isFrozen) 3 else 2)
        gridActions.adapter = StartTileAdapter(actions, colors.chipColorRes, colors.accentColorRes) { id ->
            when(id) {
                "tick_filter" -> if (!isFrozen) cycleFilter()
                "tick_freeze" -> toggleFreeze()
                "tick_save" -> saveImage()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Freeze / Unfreeze
    // ──────────────────────────────────────────────────────────────────────────

    private fun toggleFreeze() {
        if (isFrozen) {
            isFrozen = false
            frozenBitmap?.recycle()
            frozenBitmap = null
            runOnUiThread {
                updateActionGrid()
                tvFreezeLabel.visibility = View.GONE
            }
        } else {
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val raw      = yuvToBitmap(image)
                    image.close()
                    val rotated  = rotateBitmap(raw, sensorOrientation.toFloat())
                    val filtered = applyFilter(rotated, currentFilter)
                    reader.setOnImageAvailableListener(normalFrameListener, cameraHandler)
                    frozenBitmap = filtered
                    isFrozen = true
                    runOnUiThread {
                        updateActionGrid()
                        tvFreezeLabel.visibility = View.VISIBLE
                        drawToSurface(filtered)
                    }
                }
            }, cameraHandler)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Speichern
    // ──────────────────────────────────────────────────────────────────────────

    private fun saveImage() {
        val bmp = frozenBitmap ?: run {
            Toast.makeText(this, "Kein eingefrorenes Bild vorhanden.", Toast.LENGTH_SHORT).show()
            return
        }
        val ts       = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Zeckenlupe_${currentFilter.name}_$ts.jpg"
        try {
            val stream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE,    "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/HundeWildnis")
                }
                contentResolver
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?.let { contentResolver.openOutputStream(it) }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES), "HundeWildnis")
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, fileName))
            }
            stream?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            Toast.makeText(this, "Gespeichert: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Berechtigungen
    // ──────────────────────────────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (surfaceHolder.surface.isValid) openCamera()
        } else {
            Toast.makeText(this, "Kamerazugriff wird benötigt.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
