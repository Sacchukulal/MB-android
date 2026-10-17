package com.magicbill.app.ui.screens.pair

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** The camera, looking for one QR. Calls [onCode] with its text; the caller decides what it was. */
@Composable
fun QrScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier, onUnavailable: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val currentCode = rememberUpdatedState(onCode)
    val currentUnavailable = rememberUpdatedState(onUnavailable)
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }

    DisposableEffect(lifecycle) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
        }
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        val p = future.get()
                        provider = p
                        val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                        val cameraAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()
                        preview = cameraPreview
                        analysis = cameraAnalysis
                        cameraAnalysis.setAnalyzer(executor) { image ->
                            decode(image, reader)?.let { code ->
                                main.execute { if (!disposed) currentCode.value(code) }
                            }
                        }
                        p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, cameraAnalysis)
                    } catch (e: Exception) {
                        currentUnavailable.value()
                    }
                }
            }, main)
        } catch (e: Exception) {
            currentUnavailable.value()
        }
        onDispose {
            disposed = true
            analysis?.clearAnalyzer()
            val owned = listOfNotNull(preview, analysis)
            if (owned.isNotEmpty()) provider?.unbind(*owned.toTypedArray())
            executor.shutdown()
        }
    }
    AndroidView({ view }, modifier.fillMaxSize())
}

private fun decode(image: ImageProxy, reader: MultiFormatReader): String? = try {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
    reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
} catch (e: NotFoundException) {
    null
} catch (e: Exception) {
    null
} finally {
    reader.reset()
    image.close()
}
