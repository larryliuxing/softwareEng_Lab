package com.taplab.experiment.store

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.taplab.experiment.model.ModeBundle
import com.taplab.experiment.model.ModePackage
import java.io.File

object PackageImporter {
    fun readTree(resolver: ContentResolver, treeUri: Uri, scratch: File): ModeBundle {
        scratch.deleteRecursively()
        scratch.mkdirs()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                if (!accepted(name)) continue
                val documentId = cursor.getString(idColumn) ?: continue
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                resolver.openInputStream(documentUri)?.use { input ->
                    File(scratch, name).outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return ModePackage.read(scratch)
    }

    private fun accepted(name: String): Boolean {
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return false
        return name == ModePackage.FILE_NAME || name.endsWith(".bin")
    }
}
