package com.bicy.note.ui.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bicy.note.data.LocalRepository
import com.bicy.note.data.model.MARKER_CIRCLE
import com.bicy.note.data.model.MARKER_STAR
import com.bicy.note.data.model.NoteEntry
import com.bicy.note.ui.components.MediaBadges
import com.bicy.note.ui.components.MarkerToggleSegments
import com.bicy.note.ui.components.NoteDetailSheet
import com.bicy.note.ui.components.markerIcon
import com.bicy.note.ui.dockPopEnter
import com.bicy.note.ui.dockPullExit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日期记录弹层：从顶部「弹」下，关闭/切换时被「拉」回。
 * 窗口高度随内容自适应（min/max），不再出现内容被截掉一半的情况。
 */
@Composable
fun DayDetailOverlay(
    visible: Boolean,
    date: LocalDate,
    onDismiss: () -> Unit,
    topOffset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    entryToView: NoteEntry? = null,
    onEntryViewed: () -> Unit = {},
    onOpenDate: (LocalDate, NoteEntry) -> Unit = { _, _ -> },
) {
    val repository = LocalRepository.current
    val notesVersion by repository.notesVersion.collectAsStateWithLifecycle()
    var dayRecords by remember(date) { mutableStateOf(emptyList<NoteEntry>()) }
    var activeMarkers by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf<NoteEntry?>(null) }
    var viewing by remember { mutableStateOf<NoteEntry?>(null) }

    LaunchedEffect(date, notesVersion) {
        dayRecords = repository.getDayNotes(date).records
    }

    LaunchedEffect(date) {
        // 切换日期时复位标记筛选
        activeMarkers = emptySet()
    }

    LaunchedEffect(entryToView) {
        if (entryToView != null) {
            viewing = entryToView
            onEntryViewed()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val overlayMax = (maxHeight - topOffset - 20.dp).coerceAtLeast(300.dp)
        val listMax = (overlayMax - 190.dp).coerceAtLeast(120.dp)

        AnimatedVisibility(
            visible = visible,
            enter = dockPopEnter(),
            exit = dockPullExit(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { onDismiss() },
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = topOffset + 10.dp)
                        .padding(horizontal = 10.dp)
                        .heightIn(min = 260.dp, max = overlayMax)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("M月d日")),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "当天共 ${dayRecords.size} 条记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "关闭",
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        MarkerToggleSegments(
                            activeMarkers = activeMarkers,
                            onToggle = { value ->
                                activeMarkers = if (value in activeMarkers) {
                                    activeMarkers - value
                                } else {
                                    activeMarkers + value
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val visibleRecords = if (activeMarkers.isEmpty()) {
                            dayRecords
                        } else {
                            dayRecords.filter { it.effectiveMarker() in activeMarkers }
                        }
                        if (visibleRecords.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (dayRecords.isEmpty()) {
                                        "这一天没有记录"
                                    } else {
                                        "没有符合筛选的记录"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .heightIn(min = 120.dp, max = listMax)
                                    .wrapContentHeight(),
                            ) {
                                itemsIndexed(
                                    visibleRecords,
                                    key = { index, entry -> "$index|${entry.time}|${entry.text}" },
                                ) { index, entry ->
                                    DayRecordItem(
                                        entry = entry,
                                        onOpen = { viewing = entry },
                                        onDelete = { pendingDelete = entry },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = "删除这条记录？") },
            text = { Text(text = "将同时删除本条记录关联的图片、视频与音频文件") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.deleteNote(date, target)
                        pendingDelete = null
                    },
                ) {
                    Text(text = "删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = "取消")
                }
            },
        )
    }

    val viewingEntry = viewing
    if (viewingEntry != null) {
        Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
            NoteDetailSheet(
                entry = viewingEntry,
                date = date,
                onDismiss = { viewing = null },
            )
        }
    }
}

@Composable
private fun DayRecordItem(
    entry: NoteEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val marker = entry.effectiveMarker()
                if (marker != MARKER_CIRCLE) {
                    Icon(
                        imageVector = markerIcon(marker),
                        contentDescription = if (marker == MARKER_STAR) "临时标记" else "收藏标记",
                        tint = if (marker == MARKER_STAR) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = entry.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "删除这条记录",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (entry.text.isNotEmpty()) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (entry.images.isNotEmpty() || entry.videos.isNotEmpty() || entry.audios.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                MediaBadges(entry)
            }
        }
    }
}