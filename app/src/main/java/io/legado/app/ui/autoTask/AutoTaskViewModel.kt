package io.legado.app.ui.autoTask

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.outputStream
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AutoTaskViewModel(application: Application) : BaseViewModel(application) {

    private val _rulesFlow = MutableStateFlow<List<AutoTaskRule>>(emptyList())
    val rulesFlow = _rulesFlow.asStateFlow()

    fun refresh() {
        execute {
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun save(rule: AutoTaskRule) {
        execute {
            AutoTask.upsert(rule)
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun delete(rule: AutoTaskRule) {
        execute {
            AutoTask.delete(rule.id)
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        execute {
            AutoTask.delete(*ids.toTypedArray())
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun updateEnabled(ids: List<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        execute {
            val idSet = ids.toHashSet()
            val updated = AutoTask.getRules().map {
                if (idSet.contains(it.id)) it.copy(enable = enabled) else it
            }
            AutoTask.saveRules(updated)
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun exportToFile(success: (File) -> Unit) {
        execute {
            val path = "${context.filesDir}/exportAutoTask.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use {
                GSON.writeToOutputStream(it, AutoTask.getRules())
            }
            file
        }.onSuccess {
            success.invoke(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun exportSelection(ids: List<String>, success: (File) -> Unit) {
        if (ids.isEmpty()) return
        execute {
            val idSet = ids.toHashSet()
            val tasks = AutoTask.getRules().filter { idSet.contains(it.id) }
            val path = "${context.filesDir}/exportAutoTaskSelection.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use {
                GSON.writeToOutputStream(it, tasks)
            }
            file
        }.onSuccess {
            success.invoke(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }
}
