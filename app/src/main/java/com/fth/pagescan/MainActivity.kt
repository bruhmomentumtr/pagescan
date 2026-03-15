package com.fth.pagescan

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.fth.pagescan.R
import com.fth.pagescan.databinding.ActivityMainBinding
import com.google.android.material.color.DynamicColors
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Material You Dynamic Colors support for THIS activity
        DynamicColors.applyToActivityIfAvailable(this)
        
        // Edge-to-Edge ekran kullanımı
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sistem çubuklarının (Status Bar & Nav Bar) altına taşma sorununu önle
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Kamerya veya Edit ekranına geçildiğinde BottomNav'i gizle, Listede göster
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.libraryFragment -> {
                    // binding.bottomNavigation.visibility = View.VISIBLE
                }
                else -> {
                    // binding.bottomNavigation.visibility = View.GONE
                }
            }
        }
    }
}
