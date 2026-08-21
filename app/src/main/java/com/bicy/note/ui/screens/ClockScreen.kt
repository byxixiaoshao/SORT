package com.bicy.note.ui.screens

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bicy.note.data.LocalRepository
import com.bicy.note.data.SystemDndManager
import com.bicy.note.data.model.AlarmRule
import com.bicy.note.data.model.DndRule
import com.bicy.note.ui.LocalSettingsOverlayHost
import com.bicy.note.ui.ScheduledAlarm
import com.bicy.note.ui.ScheduledDnd
import com.bicy.note.ui.components.ScreenHeader
import com.bicy.note.ui.screens.clock.WheelTimePicker
import com.bicy.note.ui.screens.settings.SettingCategorySection
import com.bicy.note.ui.screens.settings.SettingClickItem
import com.bicy.note.ui.screens.settings.SettingClickItemWithIcon
import com.bicy.note.ui.screens.settings.SettingSwitchItem
import com.bicy.note.ui.screens.settings.ShadowConfig
import com.bicy.note.ui.screens.settings.SettingsPopupShell
import com.bicy.note.ui.screens.settings.dropShadow
import com.bicy.note.util.formatMinuteOfDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val overlayHost = LocalSettingsOverlayHost.current
    val repository = LocalRepository.current
    val settings by repository.settings.collectAsState()
    // 每秒对齐秒边界刷新，保证秒针精准
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }

    // 勿扰：权限 + 立刻勿扰状态（跟随 SharedPreferences，倒计时到期/系统勿扰变化时刷新）
    var accessGranted by remember { mutableStateOf(SystemDndManager.isAccessGranted(context)) }
    var instantActive by remember { mutableStateOf(ScheduledDnd.instantUntil(context) > System.currentTimeMillis()) }
    var pendingDnd by remember { mutableStateOf(false) }
    var dndExpanded by remember { mutableStateOf(false) }
    var alarmExpanded by remember { mutableStateOf(false) }

    fun refreshInstant() {
        instantActive = ScheduledDnd.instantUntil(context) > System.currentTimeMillis()
    }

    // 首次进入页面：先尝试自授勿扰权限，再重挂调度并读取一次当前状态
    LaunchedEffect(Unit) {
        if (!SystemDndManager.isAccessGranted(context)) {
            SystemDndManager.selfGrantAccess(context)
            accessGranted = SystemDndManager.isAccessGranted(context)
        }
        ScheduledDnd.arm(context)
        ScheduledAlarm.arm(context)
        refreshInstant()
    }

    // 监听系统「勿扰状态变化」和「勿扰权限变化」广播：实时同步立刻勿扰开关与权限项
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED ->
                        accessGranted = SystemDndManager.isAccessGranted(context)
                    else -> refreshInstant()
                }
            }
        }
        val filter = IntentFilter("android.app.action.INTERRUPTION_FILTER_CHANGED").apply {
            addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    LifecycleResumeEffect(Unit) {
        accessGranted = SystemDndManager.isAccessGranted(context)
        if (pendingDnd) {
            pendingDnd = false
            Toast.makeText(
                context,
                if (accessGranted) "勿扰权限已授予" else "未授予勿扰权限",
                Toast.LENGTH_SHORT,
            ).show()
        }
        refreshInstant()
        onPauseOrDispose { }
    }

    fun openRuleDialog(rule: DndRule?) {
        overlayHost.open {
            DndRuleDialog(
                rule = rule,
                onDismiss = { overlayHost.close() },
                onSave = { name, days, startMinute, endMinute ->
                    if (rule == null) {
                        repository.addDndRule(name, startMinute, endMinute, days)
                    } else {
                        repository.updateDndRule(rule.id, name, startMinute, endMinute, days)
                    }
                    ScheduledDnd.arm(context)
                    overlayHost.close()
                },
            )
        }
    }

    fun openAlarmDialog(rule: AlarmRule?) {
        overlayHost.open {
            AlarmRuleDialog(
                rule = rule,
                onDismiss = { overlayHost.close() },
                onSave = { name, days, minuteOfDay ->
                    if (rule == null) {
                        repository.addAlarmRule(name, minuteOfDay, days)
                    } else {
                        repository.updateAlarmRule(rule.id, name, minuteOfDay, days)
                    }
                    ScheduledAlarm.arm(context)
                    overlayHost.close()
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        ScreenHeader(title = "时钟")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnalogClock(now = now, modifier = Modifier.size(280.dp))
        }

        Text(
            text = now.format(timeFormatter),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = LocalDate.now().format(dateFormatter),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ── 勿扰 ──
        SettingCategorySection(
            title = "勿扰",
            isExpanded = dndExpanded,
            onToggle = { dndExpanded = !dndExpanded },
        ) {
            if (!accessGranted) {
                SettingClickItem(
                    title = "勿扰权限",
                    value = "未授予，点击授权",
                    onClick = {
                        accessGranted = SystemDndManager.selfGrantAccess(context)
                        if (accessGranted) {
                            Toast.makeText(context, "勿扰权限已自动授予", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingDnd = true
                            SystemDndManager.openAccessSettings(context)
                        }
                    },
                )
            }
            // ── 立刻勿扰 ──
            SettingSwitchItem(
                title = "立刻勿扰",
                checked = instantActive,
                onCheckedChange = { on ->
                    if (on) {
                        overlayHost.open {
                            InstantDndDialog(
                                onDismiss = { overlayHost.close() },
                                onKeep = {
                                    ScheduledDnd.setInstant(context, Long.MAX_VALUE)
                                    refreshInstant()
                                    overlayHost.close()
                                },
                                onCountdown = { minutes ->
                                    ScheduledDnd.setInstant(
                                        context,
                                        System.currentTimeMillis() + minutes * 60_000L,
                                    )
                                    refreshInstant()
                                    overlayHost.close()
                                },
                            )
                        }
                    } else {
                        ScheduledDnd.clearInstant(context)
                        refreshInstant()
                    }
                },
            )

            // ── 定时勿扰：无主开关，有规则即生效 ──
            SettingClickItemWithIcon(
                icon = Icons.Outlined.Add,
                title = "添加规则",
                subtitle = "按星期与时段自动开启/关闭系统勿扰",
                onClick = { openRuleDialog(null) },
            )
            if (settings.dndRules.isEmpty()) {
                Text(
                    text = "暂无规则，点击「添加规则」创建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            settings.dndRules.forEach { rule ->
                DndRuleCard(
                    rule = rule,
                    active = ScheduledDnd.isRuleActiveNow(rule),
                    onEdit = { openRuleDialog(rule) },
                    onDelete = {
                        repository.removeDndRule(rule.id)
                        ScheduledDnd.arm(context)
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── 闹钟 ──
        SettingCategorySection(
            title = "闹钟",
            isExpanded = alarmExpanded,
            onToggle = { alarmExpanded = !alarmExpanded },
        ) {
            SettingClickItemWithIcon(
                icon = Icons.Outlined.Add,
                title = "添加闹钟",
                subtitle = "按星期与时间自动响铃提醒",
                onClick = { openAlarmDialog(null) },
            )
            if (settings.alarmRules.isEmpty()) {
                Text(
                    text = "暂无闹钟，点击「添加闹钟」创建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            settings.alarmRules.forEach { rule ->
                AlarmRuleCard(
                    rule = rule,
                    onToggle = {
                        repository.toggleAlarmRule(rule.id)
                        ScheduledAlarm.arm(context)
                    },
                    onEdit = { openAlarmDialog(rule) },
                    onDelete = {
                        repository.removeAlarmRule(rule.id)
                        ScheduledAlarm.arm(context)
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 规则卡片：呼吸点 + 名称 + 星期/时段 + 删除（设置页卡片风格） */
@Composable
private fun DndRuleCard(
    rule: DndRule,
    active: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .dropShadow(
                config = ShadowConfig.Light,
                shape = shape,
                clip = false,
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit,
            )
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    BreathingDot()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "${daysText(rule.days)} ${formatMinuteOfDay(rule.startMinute)} - ${formatMinuteOfDay(rule.endMinute)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "删除规则",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun daysText(days: List<Int>): String {
    if (days.isEmpty()) return "从不"
    if (days.size == 7) return "每天"
    return days.sorted().joinToString(" ") { "周${weekdayLabels[it - 1]}" }
}

/** 正在执行规则时的强调色呼吸点 */
@Composable
private fun BreathingDot() {
    val transition = rememberInfiniteTransition(label = "dndDot")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dndDotAlpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape),
    )
}

/** 新建/编辑规则弹窗：名称 + 星期按钮 + 开始/结束时间（滚轮） */
@Composable
private fun DndRuleDialog(
    rule: DndRule?,
    onDismiss: () -> Unit,
    onSave: (name: String, days: List<Int>, startMinute: Int, endMinute: Int) -> Unit,
) {
    var name by remember { mutableStateOf(rule?.name ?: "") }
    var days by remember { mutableStateOf((rule?.days ?: (1..7).toList()).toMutableSet()) }
    var editStart by remember { mutableStateOf(true) }
    var startHour by remember { mutableIntStateOf((rule?.startMinute ?: 22 * 60) / 60) }
    var startMinute by remember { mutableIntStateOf((rule?.startMinute ?: 22 * 60) % 60) }
    var endHour by remember { mutableIntStateOf((rule?.endMinute ?: 7 * 60) / 60) }
    var endMinute by remember { mutableIntStateOf((rule?.endMinute ?: 7 * 60) % 60) }

    SettingsPopupShell(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (rule == null) "新建勿扰规则" else "编辑勿扰规则",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "规则名称") },
                singleLine = true,
            )
            // 星期选择：点亮=当天生效（1=周一 .. 7=周日）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (d in 1..7) {
                    DayButton(
                        label = weekdayLabels[d - 1],
                        selected = d in days,
                        onClick = {
                            days = if (d in days) {
                                (days - d).toMutableSet()
                            } else {
                                (days + d).toMutableSet()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeChip(
                    text = "开始 ${formatMinuteOfDay(startHour * 60 + startMinute)}",
                    active = editStart,
                    onClick = { editStart = true },
                )
                TimeChip(
                    text = "结束 ${formatMinuteOfDay(endHour * 60 + endMinute)}",
                    active = !editStart,
                    onClick = { editStart = false },
                )
            }
            WheelTimePicker(
                hour = if (editStart) startHour else endHour,
                minute = if (editStart) startMinute else endMinute,
                onHourChange = { if (editStart) startHour = it else endHour = it },
                onMinuteChange = { if (editStart) startMinute = it else endMinute = it },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(text = "取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            name.trim().ifEmpty { rule?.name ?: "勿扰规则" },
                            days.sorted(),
                            startHour * 60 + startMinute,
                            endHour * 60 + endMinute,
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = if (rule == null) "创建" else "保存")
                }
            }
        }
    }
}

/** 星期按钮：圆形，点亮表示该天生效 */
@Composable
private fun DayButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier.size(36.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 立刻勿扰弹窗：持续开启 或 倒计时后关闭（到期若在规则时段内会询问） */
@Composable
private fun InstantDndDialog(
    onKeep: () -> Unit,
    onCountdown: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCountdown by remember { mutableStateOf(false) }
    var minutes by remember { mutableIntStateOf(60) }
    val options = listOf(15, 30, 60, 90, 120, 180)

    SettingsPopupShell(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "立刻勿扰",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(text = "持续开启")
            }
            if (!showCountdown) {
                TextButton(
                    onClick = { showCountdown = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "倒计时后关闭")
                }
            } else {
                Text(
                    text = "倒计时后关闭",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    options.forEach { opt ->
                        TimeChip(
                            text = "$opt 分钟",
                            active = minutes == opt,
                            onClick = { minutes = opt },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(text = "取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCountdown(minutes) },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(text = "确定")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** 闹钟规则卡片：名称 + 星期/时间 + 开关 + 删除 */
@Composable
private fun AlarmRuleCard(
    rule: AlarmRule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .dropShadow(
                config = ShadowConfig.Light,
                shape = shape,
                clip = false,
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit,
            )
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rule.enabled) {
                    BreathingDot()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "${daysText(rule.days)} ${formatMinuteOfDay(rule.minuteOfDay)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "删除闹钟",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 新建/编辑闹钟弹窗：名称 + 星期按钮 + 时间（滚轮） */
@Composable
private fun AlarmRuleDialog(
    rule: AlarmRule?,
    onDismiss: () -> Unit,
    onSave: (name: String, days: List<Int>, minuteOfDay: Int) -> Unit,
) {
    var name by remember { mutableStateOf(rule?.name ?: "") }
    var days by remember { mutableStateOf((rule?.days ?: (1..7).toList()).toMutableSet()) }
    var hour by remember { mutableIntStateOf((rule?.minuteOfDay ?: 7 * 60) / 60) }
    var minute by remember { mutableIntStateOf((rule?.minuteOfDay ?: 7 * 60) % 60) }

    SettingsPopupShell(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (rule == null) "新建闹钟" else "编辑闹钟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "闹钟名称") },
                singleLine = true,
            )
            // 星期选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (d in 1..7) {
                    AlarmDayButton(
                        label = weekdayLabels[d - 1],
                        selected = d in days,
                        onClick = {
                            days = if (d in days) {
                                (days - d).toMutableSet()
                            } else {
                                (days + d).toMutableSet()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            WheelTimePicker(
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(text = "取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            name.trim().ifEmpty { rule?.name ?: "闹钟" },
                            days.sorted(),
                            hour * 60 + minute,
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = if (rule == null) "创建" else "保存")
                }
            }
        }
    }
}

/** 闹钟星期按钮 */
@Composable
private fun AlarmDayButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier.size(36.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 指针时钟：表盘 + 60 刻度 + 1~12 数字 + 时/分/秒三针 */
@Composable
private fun AnalogClock(now: LocalTime, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val numeralStyle = MaterialTheme.typography.labelLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    val faceColor = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = MaterialTheme.colorScheme.outlineVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val handColor = MaterialTheme.colorScheme.primary
    val secondColor = MaterialTheme.colorScheme.error

    val hourAngle = (now.hour % 12 + now.minute / 60f + now.second / 3600f) * 30f
    val minuteAngle = (now.minute + now.second / 60f) * 6f
    val secondAngle = now.second * 6f

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 表盘与边框
        drawCircle(
            color = faceColor.copy(alpha = 0.45f),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = ringColor,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        // 60 个刻度：整点加粗加长
        for (i in 0 until 60) {
            val isHourTick = i % 5 == 0
            val inner = radius - (if (isHourTick) 16.dp.toPx() else 11.dp.toPx())
            val outer = radius - 4.dp.toPx()
            rotate(degrees = i * 6f, pivot = center) {
                drawLine(
                    color = if (isHourTick) {
                        tickColor.copy(alpha = 0.9f)
                    } else {
                        tickColor.copy(alpha = 0.45f)
                    },
                    start = Offset(center.x, center.y - inner),
                    end = Offset(center.x, center.y - outer),
                    strokeWidth = if (isHourTick) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // 数字 1~12
        val numeralRadius = radius - 34.dp.toPx()
        for (h in 1..12) {
            val layout = textMeasurer.measure(AnnotatedString("$h"), numeralStyle)
            rotate(degrees = h * 30f, pivot = center) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        center.x - layout.size.width / 2f,
                        center.y - numeralRadius - layout.size.height / 2f,
                    ),
                )
            }
        }

        // 时针（粗短）
        rotate(degrees = hourAngle, pivot = center) {
            drawLine(
                color = handColor,
                start = Offset(center.x, center.y + 14.dp.toPx()),
                end = Offset(center.x, center.y - radius * 0.5f),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // 分针（细长）
        rotate(degrees = minuteAngle, pivot = center) {
            drawLine(
                color = handColor,
                start = Offset(center.x, center.y + 16.dp.toPx()),
                end = Offset(center.x, center.y - radius * 0.72f),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // 秒针（最细、红色、带尾）
        rotate(degrees = secondAngle, pivot = center) {
            drawLine(
                color = secondColor,
                start = Offset(center.x, center.y + radius * 0.2f),
                end = Offset(center.x, center.y - radius * 0.8f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // 中心轴
        drawCircle(color = handColor, radius = 6.dp.toPx(), center = center)
        drawCircle(color = secondColor, radius = 2.5.dp.toPx(), center = center)
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE)