package com.fth.pagescan

import android.app.Application
import android.content.Intent
import android.util.Log
import org.opencv.android.OpenCVLoader
import kotlin.system.exitProcess

class PagescanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize OpenCV Native Library globally before any Activity or Analyzer runs
        if (OpenCVLoader.initDebug()) {
            Log.i("PagescanApp", "OpenCV loaded successfully.")
        } else {
            Log.e("PagescanApp", "OpenCV initialization failed!")
        }
        
        // Catch all unexpected crashes and display them on screen (useful when ADB is unavailable)
        Thread.setDefaultUncaughtExceptionHandler { _, exception ->
            val stackTrace = Log.getStackTraceString(exception)
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_LOG, stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            
            // Kill the process so Android doesn't show the standard ANR/Crash dialog indefinitely
            exitProcess(1)
        }
    }
}
