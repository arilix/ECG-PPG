package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * Menyalin berkas rekaman ke folder yang dipilih pengguna lewat Storage Access Framework
 * (ACTION_OPEN_DOCUMENT_TREE). Rekaman utama tetap di penyimpanan internal aplikasi; ini hanya salinan.
 */
object StorageExporter {
    @Throws(IOException::class)
    fun export(ctx: Context, tree: Uri, files: List<File>) {
        val resolver = ctx.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        for (f in files) {
            val mime = if (f.name.endsWith(".csv")) "text/csv" else "application/json"
            val doc = DocumentsContract.createDocument(resolver, parent, mime, f.name)
                ?: throw IOException("Tidak bisa membuat ${f.name} di folder tujuan")
            val out = resolver.openOutputStream(doc) ?: throw IOException("Tidak bisa menulis ${f.name}")
            out.use { o -> f.inputStream().use { it.copyTo(o) } }
        }
    }

    /** Teks ramah-pengguna untuk folder pilihan, mis. "Documents/ECG · Penyimpanan perangkat". */
    fun folderLabel(tree: Uri): String = runCatching {
        val id = DocumentsContract.getTreeDocumentId(tree)           // "primary:Documents/ECG"
        when (tree.authority) {
            "com.android.externalstorage.documents" -> {
                val volume = id.substringBefore(':')
                val path = id.substringAfter(':', "").ifEmpty { "(root)" }
                val where = if (volume == "primary") "Penyimpanan perangkat" else "Kartu SD ($volume)"
                "$path · $where"
            }
            "com.android.providers.downloads.documents" -> "Downloads · Unduhan"
            else -> id.substringAfterLast(':').ifEmpty { "Folder terpilih" }
        }
    }.getOrDefault("Folder terpilih")
}

/** Akun Google untuk demo. Login nyata (OAuth + Drive API) belum diimplementasikan. */
data class DummyAccount(val name: String, val email: String)

object DummyAccounts {
    val list = listOf(
        DummyAccount("Demo Pengguna", "demo.user@example.com"),
        DummyAccount("Akun Lab ECG", "lab.ecg@example.com"),
    )
}
