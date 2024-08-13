package com.tyukei.beachpartycamera

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ImageResultDao {
    @Query("SELECT * FROM image_results")
    suspend fun getAll(): List<ImageResultEntity>

    @Insert
    suspend fun insert(imageResult: ImageResultEntity)

    @Delete
    suspend fun delete(imageResult: ImageResultEntity)
}
