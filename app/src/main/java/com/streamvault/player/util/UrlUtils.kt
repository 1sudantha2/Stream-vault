package com.streamvault.player.util

import android.net.Uri

fun guessTitleFromUri(value: String): String {
    return Uri.parse(value).lastPathSegment?.substringBeforeLast('.')?.replace('_', ' ')?.ifBlank { null }
        ?: "Stream"
}
