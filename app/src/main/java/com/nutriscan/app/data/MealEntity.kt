package com.nutriscan.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.nutriscan.app.domain.Ingredient
import com.nutriscan.app.domain.IngredientJson
import com.nutriscan.app.domain.MealType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Día en formato ISO (yyyy-MM-dd), para agrupar. */
    val day: String,
    val mealType: String,
    val name: String,
    val imagePath: String?,
    val ingredientsJson: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val notes: String?,
) {
    val type: MealType get() = MealType.fromName(mealType)
    val ingredients: List<Ingredient> get() = IngredientJson.decode(ingredientsJson)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE day = :day ORDER BY timestamp")
    fun observeDay(day: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals ORDER BY day DESC, timestamp")
    fun observeAll(): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun get(id: Long): MealEntity?

    @Query("SELECT imagePath FROM meals WHERE imagePath IS NOT NULL")
    suspend fun allImagePaths(): List<String>

    @Upsert
    suspend fun upsert(meal: MealEntity): Long

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM meals")
    suspend fun deleteAll()
}

@Database(entities = [MealEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meals(): MealDao
}
