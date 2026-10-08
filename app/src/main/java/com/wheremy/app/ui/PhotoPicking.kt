package com.wheremy.app.ui

import android.content.Context
import android.net.Uri

/** 选图器给的是 content URI，读成字节交给 `PhotoStore`（唯一一处读相册字节的地方）。 */
fun readJpeg(context: Context, uri: Uri): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
}.getOrNull()
