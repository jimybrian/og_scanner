package com.dzworks.ogscanner

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.dzworks.ogscanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this@MainActivity, R.layout.activity_main)

        setSupportActionBar(binding.tlBar)

        val myNavHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        //val inflater = myNavHostFragment.navController.navInflater
        val navController = myNavHostFragment.navController
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        binding.tlBar.setupWithNavController(navController, appBarConfiguration)

    }
}