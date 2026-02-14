package io.legado.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.model.AutoTaskProtocol
import io.legado.app.utils.CronSchedule
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.startService
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoTaskService : BaseService() {

    companion object {
        const val EXTRA_TASK_ID = "autoTaskId"
        private const val ALARM_REQUEST_CODE = 100108

        var isRun = false
            private set

        fun start(context: Context) {
            dispatchForeground(context, IntentAction.start)
        }

        fun stop(context: Context) {
            context.startService<AutoTaskService> {
                action = IntentAction.stop
            }
        }

        fun refresh(context: Context) {
            dispatchForeground(context, IntentAction.refreshSchedule)
        }

        fun runOnce(context: Context, taskId: String) {
            dispatchForeground(context, IntentAction.runOnce) {
                putExtra(EXTRA_TASK_ID, taskId)
            }
        }

        private inline fun dispatchForeground(
            context: Context,
            action: String,
            crossinline intentConfig: Intent.() -> Unit = {}
        ) {
            val intent = Intent(context, AutoTaskService::class.java).apply {
                this.action = action
                intentConfig()
            }
            context.startForegroundServiceCompat(intent)
        }
    }

    private var notificationContent = appCtx.getString(R.string.service_starting)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val maxLogLength = 4000
    private val taskLock = Mutex()

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, AppConst.channelIdWeb)
            .setSmallIcon(R.drawable.ic_web_service_noti)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.auto_task_service))
            .setContentText(notificationContent)
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<AutoTaskService>(IntentAction.stop)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == IntentAction.stop) {
            putPrefBoolean(PreferKey.autoTaskService, false)
            cancelNextAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val result = super.onStartCommand(intent, flags, startId)
        if (result == START_NOT_STICKY) {
            return result
        }
        when (intent?.action) {
            IntentAction.start, IntentAction.refreshSchedule, null -> runDueAndReschedule(startId)
            IntentAction.runOnce -> runOnce(intent, startId)
            else -> runDueAndReschedule(startId)
        }
        return result
    }

    override fun onDestroy() {
        isRun = false
        super.onDestroy()
        notificationManager.cancel(NotificationId.AutoTaskService)
    }

    private fun runDueAndReschedule(startId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!getPrefBoolean(PreferKey.autoTaskService)) {
                cancelNextAlarm()
                stopSelfResult(startId)
                return@launch
            }
            taskLock.withLock {
                isRun = true
                try {
                    processDueTasks()
                    scheduleNextRunFromRules()
                } finally {
                    isRun = false
                }
            }
            stopSelfResult(startId)
        }
    }

    private suspend fun processDueTasks() {
        val rules = AutoTask.getRules()
        if (rules.isEmpty()) {
            notificationContent = getString(R.string.auto_task_no_task)
            upNotification()
            cancelNextAlarm()
            return
        }
        val enabled = rules.filter { it.enable }
        if (enabled.isEmpty()) {
            notificationContent = getString(R.string.auto_task_no_enabled)
            upNotification()
            cancelNextAlarm()
            return
        }

        val now = System.currentTimeMillis()
        var hasDueTask = false
        enabled.forEach { task ->
            val cron = task.cron?.trim().orEmpty()
            if (cron.isBlank()) {
                updateCronError(task, getString(R.string.auto_task_cron_invalid))
                return@forEach
            }
            val schedule = CronSchedule.parse(cron)
            if (schedule == null) {
                updateCronError(task, getString(R.string.auto_task_cron_invalid))
                return@forEach
            }
            val baseTime = if (task.lastRunAt > 0L) task.lastRunAt else now - 60_000L
            val nextRun = schedule.nextTimeAfter(baseTime)
            if (nextRun == null) {
                updateCronError(task, getString(R.string.auto_task_cron_invalid))
                return@forEach
            }
            if (nextRun <= now) {
                hasDueTask = true
                runTask(task)
            }
        }

        if (!hasDueTask) {
            notificationContent = getString(R.string.auto_task_running_state)
            upNotification()
        }
    }

    private fun runOnce(intent: Intent, startId: Int) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        if (taskId.isBlank()) {
            stopSelfResult(startId)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            taskLock.withLock {
                isRun = true
                try {
                    val task = AutoTask.getRules().firstOrNull { it.id == taskId }
                    if (task == null) {
                        notificationContent = getString(R.string.auto_task_no_task)
                        upNotification()
                    } else {
                        runTask(task)
                    }
                    if (getPrefBoolean(PreferKey.autoTaskService)) {
                        scheduleNextRunFromRules()
                    }
                } finally {
                    isRun = false
                }
            }
            stopSelfResult(startId)
        }
    }

    private fun scheduleNextRunFromRules() {
        val nextRunAt = computeNextRunAt(AutoTask.getRules())
        if (nextRunAt == null) {
            cancelNextAlarm()
            return
        }
        scheduleNextAlarm(nextRunAt)
    }

    private fun computeNextRunAt(rules: List<AutoTaskRule>): Long? {
        val enabled = rules.filter { it.enable }
        if (enabled.isEmpty()) return null
        val now = System.currentTimeMillis()
        var nextRunAt: Long? = null
        enabled.forEach { task ->
            val cron = task.cron?.trim().orEmpty()
            if (cron.isBlank()) return@forEach
            val schedule = CronSchedule.parse(cron) ?: return@forEach
            val baseTime = if (task.lastRunAt > 0L) task.lastRunAt else now - 60_000L
            val next = schedule.nextTimeAfter(baseTime) ?: return@forEach
            val current = nextRunAt
            nextRunAt = if (current == null) next else minOf(current, next)
        }
        return nextRunAt
    }

    private fun scheduleNextAlarm(triggerAt: Long) {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMs = triggerAt.coerceAtLeast(System.currentTimeMillis() + 1000L)
        val pendingIntent = buildAlarmPendingIntent()
        kotlin.runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                }

                else -> {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                }
            }
            AppLog.put("AutoTask next run at ${timeFormat.format(Date(triggerAtMs))}")
        }.onFailure { error ->
            AppLog.put("AutoTask schedule alarm failed", error)
        }
    }

    private fun cancelNextAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildAlarmPendingIntent())
    }

    private fun buildAlarmPendingIntent(): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(
            this,
            ALARM_REQUEST_CODE,
            Intent(this, AutoTaskService::class.java).apply {
                action = IntentAction.refreshSchedule
            },
            flags
        )
    }

    private suspend fun runTask(task: AutoTaskRule) {
        val script = normalizeScript(task.script)
        if (script.isBlank()) {
            val now = System.currentTimeMillis()
            AutoTask.update(task.id) {
                it.copy(
                    lastRunAt = now,
                    lastError = getString(R.string.auto_task_script_empty)
                )
            }
            return
        }
        val source = AutoTask.buildSource(task)
        notificationContent = getString(R.string.auto_task_running, task.name)
        upNotification()
        val startAt = System.currentTimeMillis()
        runCatching {
            source.evalJS(script)
        }.onSuccess { result ->
            try {
                val cost = System.currentTimeMillis() - startAt
                val logLines = mutableListOf<String>()
                AutoTaskProtocol.handle(result, this, task.name) { msg ->
                    AppLog.put("AutoTask[${task.id}] ${task.name}: $msg")
                    logLines.add(msg)
                }
                val detail = result?.toString()?.take(200)
                val msg = if (detail.isNullOrBlank()) {
                    "AutoTask[${task.id}] ${task.name} done (${cost}ms)."
                } else {
                    "AutoTask[${task.id}] ${task.name} done (${cost}ms): $detail"
                }
                val lastRun = System.currentTimeMillis()
                val lastLog = buildLastLog(logLines, detail, cost, lastRun)
                AutoTask.update(task.id) {
                    it.copy(
                        lastRunAt = lastRun,
                        lastResult = detail,
                        lastError = null,
                        lastLog = lastLog
                    )
                }
                notificationContent = getString(
                    R.string.auto_task_last_run,
                    timeFormat.format(Date(lastRun))
                )
                upNotification()
                AppLog.put(msg)
            } catch (error: Throwable) {
                val msg = error.localizedMessage ?: error.toString()
                val lastLog = buildErrorLog(msg, error, System.currentTimeMillis())
                AutoTask.update(task.id) {
                    it.copy(
                        lastRunAt = System.currentTimeMillis(),
                        lastError = msg,
                        lastLog = lastLog
                    )
                }
                notificationContent = getString(R.string.auto_task_failed, msg)
                upNotification()
                AppLog.put("AutoTask[${task.id}] ${task.name} failed: $msg", error)
            }
        }.onFailure { error ->
            val msg = error.localizedMessage ?: error.toString()
            val lastLog = buildErrorLog(msg, error, System.currentTimeMillis())
            AutoTask.update(task.id) {
                it.copy(
                    lastRunAt = System.currentTimeMillis(),
                    lastError = msg,
                    lastLog = lastLog
                )
            }
            notificationContent = getString(R.string.auto_task_failed, msg)
            upNotification()
            AppLog.put("AutoTask[${task.id}] ${task.name} failed: $msg", error)
        }
    }

    private fun normalizeScript(script: String): String {
        return AutoTask.normalizeScript(script)
    }

    private fun upNotification() {
        notificationBuilder.setContentText(notificationContent)
        notificationManager.notify(NotificationId.AutoTaskService, notificationBuilder.build())
    }

    private fun updateCronError(task: AutoTaskRule, message: String) {
        if (task.lastError == message) return
        AutoTask.update(task.id) {
            val now = System.currentTimeMillis()
            it.copy(lastError = message, lastLog = buildErrorLog(message, null, now))
        }
    }

    private fun buildLastLog(
        lines: List<String>,
        detail: String?,
        cost: Long,
        runAt: Long
    ): String {
        val time = formatLogTime(runAt)
        val sb = StringBuilder()
        sb.append("[OK] ").append(time).append('\n')
        sb.append("耗时: ").append(cost).append("ms")
        if (lines.isNotEmpty()) {
            sb.append('\n').append("动作:")
            lines.forEach { line ->
                sb.append('\n').append("- ").append(line)
            }
        }
        if (!detail.isNullOrBlank()) {
            sb.append('\n').append("返回: ").append(detail)
        }
        val text = sb.toString().ifBlank { "执行完成" }
        return if (text.length > maxLogLength) text.take(maxLogLength) else text
    }

    private fun buildErrorLog(msg: String, error: Throwable?, runAt: Long): String {
        val time = formatLogTime(runAt)
        val detail = error?.stackTraceStr.orEmpty()
        val sb = StringBuilder()
        sb.append("[FAIL] ").append(time).append('\n')
        sb.append("错误: ").append(msg)
        if (detail.isNotBlank()) {
            sb.append('\n').append("堆栈:").append('\n').append(detail)
        }
        val text = sb.toString()
        return if (text.length > maxLogLength) text.take(maxLogLength) else text
    }

    private fun formatLogTime(time: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
    }

    override fun startForegroundNotification() {
        notificationBuilder.setContentText(notificationContent)
        startForeground(NotificationId.AutoTaskService, notificationBuilder.build())
    }
}
