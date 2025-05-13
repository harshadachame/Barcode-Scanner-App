package com.example.barcodescannerapp

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.barcodescannerapp.ui.theme.BarcodeScannerAppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BarcodeScannerAppTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    var navigateToCamera by remember { mutableStateOf(false) }
    var navigateToGallery by remember { mutableStateOf(false) }
    var scannedValue by remember { mutableStateOf("") }

    // BackHandler to navigate back to the main screen
    BackPressedHandler(
        onBackPressed = {
            if (navigateToCamera || navigateToGallery) {
                // Return to the main screen with buttons
                navigateToCamera = false
                navigateToGallery = false
            }
        }
    )

    if (!navigateToCamera && !navigateToGallery) {
        // Main Screen with buttons
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { navigateToCamera = true }) {
                Text("Open Camera")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navigateToGallery = true }) {
                Text("Open Gallery")
            }
        }
    } else if (navigateToCamera) {
        CameraPreview { result ->
            scannedValue = result
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, ) {
            Text(
                text = if (scannedValue.isEmpty()) "Scan a barcode..." else "Scanned: $scannedValue",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else if (navigateToGallery) {
        GalleryPreview { result ->
            scannedValue = result
        }
        Text(
            text = if (scannedValue.isEmpty()) "Scan a barcode..." else "Scanned: $scannedValue",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun BackPressedHandler(onBackPressed: () -> Unit) {
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    BackHandler(onBack = {
        onBackPressed()
        backPressedDispatcher?.onBackPressed()
    })
}

@Composable
fun CameraPreview(onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            ,
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(onBarcodeScanned))
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@Composable
fun GalleryPreview(onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputImage = InputImage.fromFilePath(context, it)
                val scanner = BarcodeScanning.getClient()
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val value = barcode.rawValue
                            if (value != null) {
                                onBarcodeScanned(value)
                                break
                            }
                        }
                        if (barcodes.isEmpty()) {
                            Toast.makeText(context, "No barcode found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Trigger image picker immediately when this screen appears
    LaunchedEffect(Unit) {
        launcher.launch("image/*")
    }

    // Optional UI while scanning or after image picked

}


class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage =
                InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null) {
                            Log.d("Barcode", "Detected: $rawValue")
                            onBarcodeDetected(rawValue)
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("Barcode", "Error: ${it.message}")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
