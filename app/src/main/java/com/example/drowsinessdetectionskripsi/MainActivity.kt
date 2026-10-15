package com.example.drowsinessdetectionskripsi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Range
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drowsinessdetectionskripsi.ui.screens.RealTimeScreen
import com.example.drowsinessdetectionskripsi.ui.theme.DrowsinessDetectionSkripsiTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCameraIfNeeded()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            }
        }

    // SAF file picker for saving CSV
    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                viewModelRef?.saveCsvToUri(contentResolver, uri)
            }
        }

    // Track what the UI wants
    private var pendingCameraStart = false
    private var viewModelRef: DetectionViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        enableEdgeToEdge()

        setContent {
            var lightMode by remember { mutableStateOf(false) }
            DrowsinessDetectionSkripsiTheme(lightMode = lightMode) {
                val vm: DetectionViewModel = viewModel()
                viewModelRef = vm

                LaunchedEffect(Unit) {
                    vm.initFaceMesh()
                    vm.startRealTimeMode()
                    startCameraWithPermissionCheck()
                }

                val state by vm.uiState.collectAsState()

                var showCamera by remember { mutableStateOf(false) }

                RealTimeScreen(
                    state = state,
                    lightMode = lightMode,
                    showCamera = showCamera,
                    onToggleLightMode = { lightMode = !lightMode },
                    onToggleCamera = { showCamera = !showCamera },
                    onToggleRun = {
                        if (state.isRunning) {
                            vm.stopDetection()
                            stopCamera()
                        } else {
                            vm.startRealTimeMode()
                            startCameraWithPermissionCheck()
                        }
                    },
                    onToggleLandmarks = vm::toggleLandmarks
                )
            }
        }
    }

    private fun startCameraWithPermissionCheck() {
        pendingCameraStart = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraIfNeeded()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCameraIfNeeded() {
        if (!pendingCameraStart) return
        pendingCameraStart = false

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val analysisBuilder = ImageAnalysis.Builder()
                .setTargetResolution(Size(480, 360))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

            // Request 30fps from the camera hardware
            Camera2Interop.Extender(analysisBuilder)
                .setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(30, 30)
                )

            imageAnalysis = analysisBuilder.build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        viewModelRef?.processFrame(imageProxy)
                    }
                }

            val cameraSelector = if (Constants.USE_FRONT_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera init failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        imageAnalysis = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
