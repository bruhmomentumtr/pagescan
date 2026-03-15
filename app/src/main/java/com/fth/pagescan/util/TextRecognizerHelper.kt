package com.fth.pagescan.util

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Helper class for extracting text from images using Google ML Kit Text Recognition.
 * Fully offline (On-Device), supports Latin script (including Turkish: ğ, ü, ş, vb.).
 */
class TextRecognizerHelper {

    private val TAG = "TextRecognizerHelper"

    // TextRecognizer instance initialized with Latin script options
    // Provides excellent support for Turkish and English out of the box.
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Processes the given Bitmap to extract text asynchronously.
     * Uses coroutines to avoid blocking the main UI thread.
     * 
     * @param bitmap The cropped and filtered document image.
     * @return A ML Kit [Text] object containing the recognized blocks, lines, and words, or null if an error occurred.
     */
    suspend fun recognizeText(bitmap: Bitmap): Text? = withContext(Dispatchers.Default) {
        try {
            // InputImage converts Bitmap into a format ML Kit understands
            val image = InputImage.fromBitmap(bitmap, 0)
            
            // await() converts the Task API to coroutines suspend function
            val result = recognizer.process(image).await()
            
            Log.d(TAG, "Successfully recognized text. Word count: ${result.text.split("\\s+".toRegex()).size}")
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error recognizing text from image", e)
            return@withContext null
        }
    }

    /**
     * Extracts plain text string from the Text object.
     * 
     * @param result The ML Kit [Text] object.
     * @return The entire recognized text string, with preserved line breaks.
     */
    fun extractPlainText(result: Text?): String {
        if (result == null || result.text.isEmpty()) return ""
        
        val builder = java.lang.StringBuilder()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                // Her kelimeyi ve satırı koruyarak çıktı oluşturur
                builder.append(line.text).append("\n")
            }
            builder.append("\n") // Paragraflar arasına boşluk koy
        }
        return builder.toString().trim()
    }

    /**
     * Closes the TextRecognizer when it is no longer needed (e.g., ViewModel onCleared).
     * Vital for cleanup and avoiding memory leaks.
     */
    fun close() {
        try {
            recognizer.close()
            Log.d(TAG, "Text recognizer closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing text recognizer", e)
        }
    }
}
