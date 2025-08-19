package com.amos_tech_code.utils

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData

// Helper extension function to convert single file item to MultiPartData
fun List<PartData.FileItem>.toMultiPartData(): MultiPartData = object : MultiPartData {
    private val partsIterator = this@toMultiPartData.iterator()

    override suspend fun readPart(): PartData? {
        return if (partsIterator.hasNext()) {
            partsIterator.next()
        } else {
            null
        }
    }
}