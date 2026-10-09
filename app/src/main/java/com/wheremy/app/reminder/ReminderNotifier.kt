package com.wheremy.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wheremy.app.MainActivity
import com.wheremy.app.R

/**
 * 通知这一层（`RULES.md` §11.4）。一条聚合通知只占一格。
 *
 * 🔒 **不给就地申请通知权限**（2026-10-03 真机否掉：HyperOS 在总开关关着时会静默驳回那次申请，
 * 按下去连弹框都没有）。两种理由共用同一颗按钮跳系统页。
 */
object ReminderNotifier {

    const val CHANNEL_ID = "wheremy_expire"
    const val CHANNEL_NAME = "到期提醒"
    const val NOTIFICATION_ID = 1001

    /** 点通知进来要落到的位置：筛过"快到期/已过期"的照片墙。 */
    const val ENTRY_EXPIRING = "expiring"
    const val EXTRA_ENTRY = "entry"

    /** 通知未开启（运行时权限没给）。 */
    const val PROBLEM_PERMISSION = "通知未开启，到期只在应用内标记"

    /** 系统里被关掉了。 */
    const val PROBLEM_DISABLED =
        "通知在系统里被关掉了：到 设置 → 应用 → Wheremy → 通知 里打开，不然到期只能靠你自己想起来。"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    /** 能发就 null，不能发就给出**要说给用户听的那句话**。 */
    fun notifyProblem(context: Context): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED -> PROBLEM_PERMISSION

        !NotificationManagerCompat.from(context).areNotificationsEnabled() -> PROBLEM_DISABLED

        else -> null
    }

    fun post(context: Context, body: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ENTRY, ENTRY_EXPIRING)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(CHANNEL_NAME)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
