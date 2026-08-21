package com.bicy.note.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bicy.note.data.NoteRepository
import com.bicy.note.data.model.NoteEntry
import java.io.File

/** 记录条目的媒体徽标行：图片缩略图 + 视频/音频计数。 */
@Composable
fun MediaBadges(entry: NoteEntry) {
    if (entry.images.isEmpty() && entry.videos.isEmpty() && entry.audios.isEmpty()) return
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        entry.images.forEach { name ->
            MediaThumb(
                name = name,
                dir = NoteRepository.DIR_MEDIA,
                context = context,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        entry.videos.forEach { name ->
            MediaTypeChip(
                text = "视频 ${name.substringAfterLast('.')}",
                icon = Icons.Outlined.Movie,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        entry.audios.forEach { name ->
            MediaTypeChip(
                text = "音频 ${name.substringAfterLast('.')}",
                icon = Icons.Outlined.Mic,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@Composable
private fun MediaThumb(name: String, dir: String, context: Context) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name) {
        bitmap = loadThumbnail(context, dir, name)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ),
        )
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "图",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaTypeChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun loadThumbnail(context: Context, dir: String, name: String): Bitmap? {
    return try {
        val file = File(context.filesDir, "$dir/$name")
        if (!file.exists()) return null
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = maxOf(info.size.width, info.size.height) / 96f
            if (scale > 1f) decoder.setTargetSampleSize(scale.toInt())
        }
    } catch (_: Exception) {
        null
    }
}