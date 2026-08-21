package com.bicy.note.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bicy.note.R
import com.bicy.note.data.NoteRepository
import com.bicy.note.data.SystemDndManager
import com.bicy.note.data.model.DndRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 勿扰调度（无主开关）：定时规则 + 立刻勿扰，全部基于系统闹钟（AlarmManager）。
 *
 * - 任一启用规则处于时段内（且今天匹配星期）→ 开启系统勿扰；全部在时段外 → 恢复关闭
 *   （仅当勿扰是本组件开启的才恢复，不干涉用户手动开关）。
 * - 立刻勿扰：持续开启（Long.MAX_VALUE）或倒计时后关闭；倒计时到期时若正处于某规则
 *   时段内，不自动关闭，改发通知询问「继续勿扰 / 关闭勿扰」。
 * - 每次只调度「最近一个状态切换点」的精确闹钟，触发后重新计算再调度；
 *   进程被杀也能被闹钟唤醒。开机 / 时间 / 时区变更 / 应用升级后自动重挂。
 */
object ScheduledDnd {

    private const val TAG = "寄意定时勿扰"
    const val ACTION_TICK = "com.bicy.note.action.DND_TICK"
    const val ACTION_DND_CONTINUE = "com.bicy.note.action.DND_CONTINUE"
    const val ACTION_DND_OFF = "com.bicy.note.action.DND_OFF"
    private const val REQUEST_TICK = 1001
    private const val REQUEST_CONTINUE = 1002
    private const val REQUEST_OFF = 1003
    private const val PREFS = "scheduled_dnd"
    private const val KEY_TURNED_ON = "turned_on"
    private const val KEY_INSTANT_UNTIL = "instant_until"
    private const val KEY_OVERRIDE_UNTIL = "override_until"
    private const val PROMPT_CHANNEL = "dnd_prompt"
    private const val PROMPT_NOTIFICATION_ID = 12

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun turnedOnByUs(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TURNED_ON, false)

    private fun setTurnedOnByUs(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_TURNED_ON, on).apply()
    }

    /** 立刻勿扰到期时间（epoch 毫秒）：0=未开启；Long.MAX_VALUE=持续开启 */
    fun instantUntil(context: Context): Long = prefs(context).getLong(KEY_INSTANT_UNTIL, 0L)

    /** 「关闭勿扰」后到当前规则时段结束前，不再被规则自动开启 */
    private fun overrideUntil(context: Context): Long =
        prefs(context).getLong(KEY_OVERRIDE_UNTIL, 0L)

    /** 规则是否正在执行：今天匹配星期且在时段内 */
    fun isRuleActiveNow(rule: DndRule): Boolean {
        if (!rule.enabled) return false
        val today = LocalDate.now().dayOfWeek.value // 1=周一 .. 7=周日
        if (today !in rule.days) return false
        return isInWindow(nowMinute(), rule)
    }

    private fun anyRuleActive(context: Context): Boolean {
        val settings = NoteRepository.get(context).currentSettings()
        return settings.dndRules.any { isRuleActiveNow(it) }
    }

    /** 重挂调度：规则变化 / 开机 / 时间与时区变更 / 闹钟触发后调用 */
    fun arm(context: Context) {
        expireInstantIfDue(context)
        applyDnd(context)
        scheduleNext(context)
    }

    /** 设置立刻勿扰：untilEpochMillis 为到期时间（Long.MAX_VALUE=持续开启） */
    fun setInstant(context: Context, untilEpochMillis: Long) {
        prefs(context).edit().putLong(KEY_INSTANT_UNTIL, untilEpochMillis).apply()
        applyDnd(context)
        scheduleNext(context)
    }

    /** 关闭立刻勿扰 */
    fun clearInstant(context: Context) {
        prefs(context).edit().putLong(KEY_INSTANT_UNTIL, 0L).apply()
        applyDnd(context)
        scheduleNext(context)
    }

    /** 通知「继续勿扰」：改为持续开启 */
    fun onContinue(context: Context) {
        prefs(context).edit().putLong(KEY_INSTANT_UNTIL, Long.MAX_VALUE).apply()
        dismissPrompt(context)
        applyDnd(context)
        scheduleNext(context)
    }

    /** 通知「关闭勿扰」：立即关闭，并在当前规则时段结束前不再被规则自动开启 */
    fun onOff(context: Context) {
        prefs(context).edit().putLong(KEY_INSTANT_UNTIL, 0L).apply()
        prefs(context).edit().putLong(KEY_OVERRIDE_UNTIL, activeRuleEndEpoch(context)).apply()
        dismissPrompt(context)
        applyDnd(context)
        scheduleNext(context)
    }

    private fun applyDnd(context: Context) {
        val now = System.currentTimeMillis()
        val ruleActive = anyRuleActive(context) && now >= overrideUntil(context)
        val instantActive = instantUntil(context) > now
        if (ruleActive || instantActive) {
            if (!SystemDndManager.isDndActive(context)) {
                val error = SystemDndManager.setDnd(context, true)
                if (error != null) {
                    Log.d(TAG, "开启系统勿扰失败: $error")
                } else {
                    setTurnedOnByUs(context, true)
                    Log.d(TAG, "已开启系统勿扰")
                }
            }
        } else if (turnedOnByUs(context)) {
            val error = SystemDndManager.setDnd(context, false)
            if (error != null) {
                Log.d(TAG, "恢复关闭失败: $error")
            } else {
                setTurnedOnByUs(context, false)
                Log.d(TAG, "已恢复系统勿扰关闭")
            }
        }
    }

    /** 立刻勿扰倒计时到期：清理标记；若正处于规则时段内则发通知询问，否则交给 applyDnd 恢复 */
    private fun expireInstantIfDue(context: Context) {
        val until = instantUntil(context)
        if (until <= 0L || until == Long.MAX_VALUE || until > System.currentTimeMillis()) return
        prefs(context).edit().putLong(KEY_INSTANT_UNTIL, 0L).apply()
        Log.d(TAG, "立刻勿扰倒计时到期")
        if (anyRuleActive(context)) {
            showContinuePrompt(context)
        }
    }

    /** 当前所有执行中规则里，最晚的时段结束时间（epoch 毫秒） */
    private fun activeRuleEndEpoch(context: Context): Long {
        var max = 0L
        NoteRepository.get(context).currentSettings().dndRules.forEach { rule ->
            if (isRuleActiveNow(rule)) {
                val endDay = if (rule.endMinute > nowMinute()) 0 else 1
                max = maxOf(max, epochMillis(rule.endMinute, endDay))
            }
        }
        return max
    }

    private fun showContinuePrompt(context: Context) {
        val name = NoteRepository.get(context).currentSettings().dndRules
            .firstOrNull { isRuleActiveNow(it) }?.name ?: "勿扰规则"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    PROMPT_CHANNEL,
                    "勿扰询问",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { setSound(null, null) },
            )
        }
        val continuePi = PendingIntent.getBroadcast(
            context,
            REQUEST_CONTINUE,
            Intent(context, ScheduledDndReceiver::class.java).setAction(ACTION_DND_CONTINUE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val offPi = PendingIntent.getBroadcast(
            context,
            REQUEST_OFF,
            Intent(context, ScheduledDndReceiver::class.java).setAction(ACTION_DND_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, PROMPT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("当前处于「$name」的勿扰时间段")
            .setContentText("立刻勿扰倒计时已结束，是否继续勿扰？")
            .setAutoCancel(true)
            .addAction(0, "继续勿扰", continuePi)
            .addAction(0, "关闭勿扰", offPi)
            .build()
        nm.notify(PROMPT_NOTIFICATION_ID, notification)
        Log.d(TAG, "已发送勿扰继续询问通知")
    }

    private fun dismissPrompt(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(PROMPT_NOTIFICATION_ID)
    }

    private fun nowMinute(): Int {
        val t = LocalTime.now()
        return t.hour * 60 + t.minute
    }

    /** 是否处于规则时段内；开始 == 结束视为整天 */
    private fun isInWindow(nowMinute: Int, rule: DndRule): Boolean =
        if (rule.startMinute == rule.endMinute) {
            true
        } else if (rule.startMinute < rule.endMinute) {
            nowMinute in rule.startMinute until rule.endMinute
        } else {
            nowMinute >= rule.startMinute || nowMinute < rule.endMinute
        }

    /** 所有规则的下一个状态切换点（当日分钟数，天偏移 0..6），无规则返回 null */
    private fun nextTransition(nowMinute: Int, rules: List<DndRule>): Pair<Int, Int>? {
        val today = LocalDate.now()
        var best: Pair<Int, Int>? = null
        for (rule in rules) {
            for (k in 0..6) {
                val date = today.plusDays(k.toLong())
                if (date.dayOfWeek.value !in rule.days) continue
                val event: Pair<Int, Int>? = when {
                    k == 0 && isInWindow(nowMinute, rule) ->
                        rule.endMinute to if (rule.endMinute > nowMinute) 0 else 1
                    k == 0 && rule.startMinute <= nowMinute -> null
                    else -> rule.startMinute to k
                }
                if (event != null &&
                    (best == null ||
                        event.first < best.first ||
                        (event.first == best.first && event.second < best.second))
                ) {
                    best = event
                }
            }
        }
        return best
    }

    private fun scheduleNext(context: Context) {
        cancelAlarm(context)
        val now = System.currentTimeMillis()
        val rules = NoteRepository.get(context).currentSettings().dndRules
        val ruleEpoch = nextTransition(nowMinute(), rules)
            ?.let { epochMillis(it.first, it.second) }
        val until = instantUntil(context)
        val instantEpoch =
            if (until > now && until != Long.MAX_VALUE) until else null
        var trigger = listOfNotNull(ruleEpoch, instantEpoch).minOrNull() ?: return
        if (trigger <= now + 1000L) {
            trigger += 24 * 3600_000L
        }
        val pi = pendingIntent(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            am.canScheduleExactAlarms()
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
        Log.d(TAG, "已调度下次触发: epoch=$trigger")
    }

    private fun epochMillis(minuteOfDay: Int, dayOffset: Int): Long {
        val zone = ZoneId.systemDefault()
        val base = ZonedDateTime.now(zone).toLocalDate().plusDays(dayOffset.toLong())
        return base.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toInstant().toEpochMilli()
    }

    private fun cancelAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_TICK,
            Intent(context, ScheduledDndReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/**
 * 勿扰触发接收器：
 * - 闹钟到点 / 开机 / 时间与时区变更 / 应用升级 → 重算并重挂
 * - 「继续勿扰」「关闭勿扰」→ 处理通知按钮
 */
class ScheduledDndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ScheduledDnd.ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ScheduledDnd.arm(context)

            ScheduledDnd.ACTION_DND_CONTINUE -> ScheduledDnd.onContinue(context)
            ScheduledDnd.ACTION_DND_OFF -> ScheduledDnd.onOff(context)
        }
    }
}