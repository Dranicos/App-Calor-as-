package com.nutriscan.app

import android.app.Application
import androidx.room.Room
import com.nutriscan.app.ai.FoodAnalyzer
import com.nutriscan.app.data.AppDatabase
import com.nutriscan.app.data.SettingsStore
import com.nutriscan.app.data.PhotoStore

/** Contenedor sencillo de dependencias. Todo se guarda en el propio dispositivo. */
class NutriApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "nutriscan.db").build()
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val photos: PhotoStore by lazy { PhotoStore(this) }
    val analyzer: FoodAnalyzer by lazy { FoodAnalyzer() }
}
