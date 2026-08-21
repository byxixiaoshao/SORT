package com.bicy.note.ui.screens.clock

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelSelector(value = hour, range = 24, onValueChange = onHourChange, label = "时")
        Spacer(modifier = Modifier.width(28.dp))
        WheelSelector(value = minute, range = 60, onValueChange = onMinuteChange, label = "分")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelSelector(
    value: Int,
    range: Int,
    onValueChange: (Int) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 46.dp
    val visibleItems = 5
    val containerHeight = itemHeight * visibleItems
    val baseIndex = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % range
    val initialIndex = baseIndex + value

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(listState)

    val selectedIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo
            if (info.isEmpty()) {
                baseIndex
            } else {
                val center = listState.layoutInfo.viewportStartOffset +
                    listState.layoutInfo.viewportSize.height / 2
                info.minBy { abs((it.offset + it.size / 2) - center) }.index
            }
        }
    }
    val selectedValue = selectedIndex % range
    LaunchedEffect(selectedValue) { onValueChange(selectedValue) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = modifier
                .width(64.dp)
                .height(containerHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                    ),
            )
            LazyColumn(
                state = listState,
                flingBehavior = fling,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(Int.MAX_VALUE) { index ->
                    val itemValue = index % range
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = itemValue.toString().padStart(2, '0'),
                            fontSize = if (isSelected) 22.sp else 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        if (label != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}