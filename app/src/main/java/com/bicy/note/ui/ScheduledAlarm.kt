package com.bicy.note.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bicy.note.R
import com.bicy.note.data.NoteRepository
import com.bicy.note.data.model.AlarmRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 闹钟调度：复用勿扰的 AlarmManager 精确闹钟机制。
 *
 * - 每个启用的闹钟规则，按其星期与时间调度精确闹钟。
 * - 触发后：播放铃声 + 震动 + 发送通知（带关闭按钮）。
 * - 每次只调度「最近一个触发点」的闹钟，触发后重新计算再调度。
 * - 进程被杀也能被闹钟唤醒。开机 / 时间 / 时区变更后自动重挂。
 */
object ScheduledAlarm {

    private const val TAG = "寄意闹钟"
    const val ACTION_ALARM_FIRE = "com.bicy.note.action.ALARM_FIRE"
    const val ACTION_ALARM_DISMISS = "com.bicy.note.action.ALARM_DISMISS"
    private const val REQUEST_ALARM = 2001
    private const val REQUEST_DISMISS = 2002
    private const val ALARM_CHANNEL = "alarm_channel"
    private const val ALARM_NOTIFICATION_ID = 200

    /** 重挂调度：规则变化 / 开机 / 时间与时区变更 / 闹钟触发后调用 */
    fun arm(context: Context) {
        cancelAlarm(context)
        scheduleNext(context)
    }

    /** 判断某条规则是否在当前时刻应该触发（今天匹配星期且时间已到） */
    fun isRuleActiveNow(rule: AlarmRule): Boolean {
        if (!rule.enabled) return false
        val today = LocalDate.now().dayOfWeek.value // 1=周一 .. 7=周日
        if (today !in rule.days) return false
        val now = LocalTime.now()
        val nowMinute = now.hour * 60 + now.minute
        return nowMinute == rule.minuteOfDay
    }

    private fun scheduleNext(context: Context) {
        val rules = NoteRepository.get(context).currentSettings().alarmRules
            .filter { it.enabled }
        if (rules.isEmpty()) return

        val today = LocalDate.now()
        var bestTrigger: Long? = null

        for (k in 0..6) {
            val date = today.plusDays(k.toLong())
            val dayOfWeek = date.dayOfWeek.value
            for (rule in rules) {
                if (dayOfWeek !in rule.days) continue
                val trigger = epochMillis(rule.minuteOfDay, k)
                if (trigger <= System.currentTimeMillis()) continue
                if (bestTrigger == null || trigger < bestTrigger) {
                    bestTrigger = trigger
                }
            }
        }

        if (bestTrigger == null) return
        val pi = pendingIntent(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            am.canScheduleExactAlarms()
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestTrigger, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, bestTrigger, pi)
        }
        Log.d(TAG, "已调度下次闹钟: epoch=$bestTrigger")
    }

    /** 闹钟触发：播放铃声 + 震动 + 通知 */
    fun fire(context: Context) {
        ensureChannel(context)

        // 播放闹钟铃声（优先使用用户选择的铃声）
        val settings = NoteRepository.get(context).currentSettings()
        val ringtoneUri = settings.alarmRingtoneUri?.let { android.net.Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (ringtoneUri != null) {
            val player = RingtoneManager.getRingtone(context, ringtoneUri)
            player?.play()
            // 5 秒后自动停止
            android.os.Handler(context.mainLooper).postDelayed({
                runCatching { player?.stop() }
            }, 5000L)
        }

        // 震动
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))

        // 发送通知
        val dismissPi = PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISS,
            Intent(context, ScheduledAlarmReceiver::class.java).setAction(ACTION_ALARM_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("闹钟响了")
            .setContentText("点击关闭")
            .setAutoCancel(true)
            .setOngoing(true)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 2000))
            .addAction(0, "关闭闹钟", dismissPi)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALARM_NOTIFICATION_ID, notification)

        // 通知栏关闭也停止铃声
        val cancelPi = PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISS + 100,
            Intent(context, ScheduledAlarmReceiver::class.java).setAction(ACTION_ALARM_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Builder(context, ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        Log.d(TAG, "闹钟已触发")
    }

    /** 关闭闹钟：取消通知 + 重挂下次调度 */
    fun dismiss(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ALARM_NOTIFICATION_ID)
        arm(context)
        Log.d(TAG, "闹钟已关闭")
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    ALARM_CHANNEL,
                    "闹钟",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 2000)
                },
            )
        }
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
            REQUEST_ALARM,
            Intent(context, ScheduledAlarmReceiver::class.java).setAction(ACTION_ALARM_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/**
 * 闹钟触发接收器：
 * - 闹钟到点 → 播放铃声 + 通知
 * - 开机 / 时间与时区变更 / 应用升级 → 重挂调度
 * - 「关闭闹钟」→ 取消通知 + 重挂
 */
class ScheduledAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ScheduledAlarm.ACTION_ALARM_FIRE -> ScheduledAlarm.fire(context)
            ScheduledAlarm.ACTION_ALARM_DISMISS -> ScheduledAlarm.dismiss(context)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ScheduledAlarm.arm(context)
        }
    }
}
