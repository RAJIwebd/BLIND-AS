package com.example.blind

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class NudeNetClassifier(context: Context) {
    private var interpreter: Interpreter? = null

    init {
        val options = Interpreter.Options().apply {
            setNumThreads(4)  // Optimize model performance
        }
        interpreter = Interpreter(loadModelFile(context), options)
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("640m_static_float32.tflite")
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    fun classifyImage(input: FloatArray): Array<Array<FloatArray>> {  // ✅ Return 3D array (correct shape)
        val modelInputSize = 640
        val batchSize = 1
        val channels = 3

        // Convert input to 4D tensor format expected by TensorFlow Lite
        val inputTensor = Array(batchSize) { Array(modelInputSize) { Array(modelInputSize) { FloatArray(channels) } } }

        var index = 0
        for (i in 0 until modelInputSize) {
            for (j in 0 until modelInputSize) {
                for (k in 0 until channels) {
                    inputTensor[0][i][j][k] = input[index++]
                }
            }
        }

        // 🛠️ Debugging Log: Check Model's Expected Input Shape
        val expectedShape = interpreter?.getInputTensor(0)?.shape()
        Log.d("TensorFlow", "✅ Model Expected Input Shape: ${expectedShape?.joinToString()}")

        // 🛠️ Debugging Log: Check Final Input Shape
        Log.d("TensorFlow", "✅ Final Input Shape: ${inputTensor.size}, ${inputTensor[0].size}, ${inputTensor[0][0].size}, ${inputTensor[0][0][0].size}")

        // ✅ Output should match model's expected shape [1, 22, 8400]
        val output = Array(1) { Array(22) { FloatArray(8400) } }

        // Run model inference
        interpreter?.run(inputTensor, output)

        // 🛠️ Debugging Log: Check Output Shape
        Log.d("TensorFlow", "✅ Model Output Shape: ${output.size}, ${output[0].size}, ${output[0][0].size}")

        return output  // ✅ Matches expected model output shape [1, 22, 8400]
    }




}
