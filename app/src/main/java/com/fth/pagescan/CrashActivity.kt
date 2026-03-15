package com.fth.pagescan

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val crashLog = intent.getStringExtra(EXTRA_CRASH_LOG) ?: "No crash details available."
        
        val textView = TextView(this).apply {
            text = "Oops! The app crashed. Please share this log:\n\n$crashLog"
            setTextIsSelectable(true)
            textSize = 12f
            setPadding(32, 16, 32, 32)
        }

        val button = Button(this).apply {
            text = "Copy to Clipboard"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Crash Log", crashLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button)
            addView(textView)
        }

        val scrollView = ScrollView(this).apply {
            addView(layout)
        }

        setContentView(scrollView)
    }
}
