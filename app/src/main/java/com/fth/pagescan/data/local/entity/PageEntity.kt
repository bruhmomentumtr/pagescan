package com.fth.pagescan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["document_id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE 
        )
    ],
    indices = [Index(value = ["document_id"])] 
)
data class PageEntity(
    @PrimaryKey
    @ColumnInfo(name = "page_id")
    val pageId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "document_id")
    val documentId: String,

    @ColumnInfo(name = "page_number")
    var pageNumber: Int,

    @ColumnInfo(name = "original_image_path")
    var originalImagePath: String, 

    @ColumnInfo(name = "processed_image_path")
    var processedImagePath: String, 

    @ColumnInfo(name = "recognized_text")
    var recognizedText: String? = null,

    @ColumnInfo(name = "applied_filter")
    var appliedFilter: FilterType = FilterType.MAGIC_COLOR,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

enum class FilterType {
    NONE,           
    MAGIC_COLOR,    
    GRAYSCALE,      
    B_AND_W         
}
