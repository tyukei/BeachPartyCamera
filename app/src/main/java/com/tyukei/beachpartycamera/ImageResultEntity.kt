package com.tyukei.beachpartycamera

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_results")
data class ImageResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val image: ByteArray,
    val text: String
)
