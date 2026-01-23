package io.legado.app.ui.autoTask

import android.app.Application
import android.content.Intent
import io.legado.app.base.BaseViewModel
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.utils.toastOnUi

class AutoTaskEditViewModel(app: Application) : BaseViewModel(app) {

    var task: AutoTaskRule? = null

    fun initData(intent: Intent, finally: (AutoTaskRule) -> Unit) {
        execute {
            val id = intent.getStringExtra("id")
            task = AutoTask.getRules().firstOrNull { it.id == id } ?: AutoTaskRule()
        }.onFinally {
            task?.let { finally(it) }
        }
    }

    fun save(rule: AutoTaskRule, success: () -> Unit) {
        execute {
            AutoTask.upsert(rule)
        }.onSuccess {
            success()
        }.onError {
            context.toastOnUi("save error, ${it.localizedMessage}")
        }
    }
}
