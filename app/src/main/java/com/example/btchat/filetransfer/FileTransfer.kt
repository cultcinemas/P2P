package com.example.btchat.filetransfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream

object FileTransfer {
    fun readAll(ctx: Context, uri: Uri, onChunk: (ByteArray) -> Unit) {
        val cr: ContentResolver = ctx.contentResolver
        cr.openInputStream(uri)?.use { input ->
            pipe(input, onChunk)
        }
    }

    private fun pipe(input: InputStream, onChunk: (ByteArray) -> Unit) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            onChunk(buf.copyOf(n))
        }
    }
}
