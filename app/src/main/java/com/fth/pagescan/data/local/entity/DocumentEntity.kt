package com.fth.pagescan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "document_id")
    val documentId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    var title: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis()
)
