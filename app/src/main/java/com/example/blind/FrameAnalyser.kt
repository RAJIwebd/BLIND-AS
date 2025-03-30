package com.example.blind

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

class FrameAnalyzer(
    context: Context,
    private val shouldProcessFrame: () -> Boolean
) : ImageAnalysis.Analyzer {

    private val classifier = NudeNetClassifier(context)
    private var frameCounter = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (shouldProcessFrame()) {  // Only process if recording is active
            if (frameCounter++ % 3 == 0) {  // Process every 3rd frame for optimization
                val image = imageProxy.image
                if (image != null) {
                    val bitmap = convertYUVToBitmap(image)
                    val input = convertImageToFloatArray(bitmap, 640)
                    val result = classifier.classifyImage(input)  // Now returns Array<Array<FloatArray>>

                    // ✅ Fix: Extract correctly
                    val detectionResults = result[0]  // Extract Array<FloatArray> (Shape: [22, 8400])

                    // ✅ Fix: Correctly access confidence score
                    val confidenceScore = detectionResults[1][0] // FloatArray[8400] → Take first Float

                    if (confidenceScore > 0.5) {
                        val roi = extractROI(result)  // Pass the original result (Array<Array<FloatArray>>)
                        sendToNextStep(roi)
                    }
                }
            }
        }
        imageProxy.close()  // Always close to prevent memory leaks
    }

    // ✅ Fix: Ensure extractROI handles the correct shape
    private fun extractROI(result: Array<Array<FloatArray>>): List<Float> {
        return result.flatMap { it.flatMap { it.toList() } }  // Convert 3D to List<Float>
    }


    private fun sendToNextStep(roi: List<Float>) {
        // Implement function to handle ROI (e.g., blur, highlight, etc.)
        Log.d("ROI Detected", "Nudity Found! Bounding Box: $roi")
    }
}

fun convertYUVToBitmap(image: Image): Bitmap {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    uBuffer.get(nv21, ySize, uSize)
    vBuffer.get(nv21, ySize + uSize, vSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    val jpegBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}

fun convertImageToFloatArray(bitmap: Bitmap, modelInputSize: Int): FloatArray {
    val resizedBitmap = bitmap.scale(modelInputSize, modelInputSize)

    val intValues = IntArray(modelInputSize * modelInputSize)
    resizedBitmap.getPixels(intValues, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)

    val floatValues = FloatArray(1 * 3 * modelInputSize * modelInputSize) // Ensure correct shape
    var pixelIndex = 0

    for (y in 0 until modelInputSize) {
        for (x in 0 until modelInputSize) {
            val pixel = intValues[pixelIndex++]

            val r = ((pixel shr 16) and 0xFF) / 255.0f // Red
            val g = ((pixel shr 8) and 0xFF) / 255.0f  // Green
            val b = (pixel and 0xFF) / 255.0f         // Blue

            val index = (y * modelInputSize + x)  // Correct channel order
            floatValues[index] = r
            floatValues[modelInputSize * modelInputSize + index] = g
            floatValues[2 * modelInputSize * modelInputSize + index] = b
        }
    }

    return floatValues
}

