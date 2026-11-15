package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.repository.MAX_ATTACHMENT_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A file the user picked, read into memory. */
class PickedFile(val name: String, val mimeType: String?, val bytes: ByteArray)

/** Reads a picked document. Returns null (and shows a message) if it is too big or unreadable. */
suspend fun readPickedFile(context: Context, uri: Uri): PickedFile? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var name = "file"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = c.getColumnIndex(OpenableColumns.SIZE)
            if (ni >= 0) name = c.getString(ni) ?: name
            if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
        }
    }
    if (size > MAX_ATTACHMENT_BYTES) {
        withContext(Dispatchers.Main) { Toast.makeText(context, "Files must be 5 MB or smaller.", Toast.LENGTH_LONG).show() }
        return@withContext null
    }
    val bytes = try {
        resolver.openInputStream(uri)?.use { input ->
            val buf = input.readBytes()
            if (buf.size > MAX_ATTACHMENT_BYTES) null else buf
        }
    } catch (e: Exception) {
        null
    }
    if (bytes == null) {
        withContext(Dispatchers.Main) { Toast.makeText(context, "That file could not be read (or is over 5 MB).", Toast.LENGTH_LONG).show() }
        return@withContext null
    }
    PickedFile(name, resolver.getType(uri), bytes)
}

/** Saves a downloaded attachment to the app's cache and opens it with another app. */
suspend fun openDownloadedFile(context: Context, fileName: String, mimeType: String?, bytes: ByteArray) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safe = fileName.replace(Regex("[^\\w.\\- ()]"), "_").ifBlank { "file" }
        File(dir, safe).apply { writeBytes(bytes) }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType ?: "application/octet-stream")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent.createChooser(intent, "Open $fileName").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open this kind of file.", Toast.LENGTH_LONG).show()
    }
}

/** Opens a call link (Jitsi Meet app if installed, otherwise the browser). */
fun openCallLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Install a web browser or the Jitsi Meet app to join calls.", Toast.LENGTH_LONG).show()
    }
}
