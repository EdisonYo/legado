package io.legado.app.model

import android.content.Context
import io.legado.app.constant.IntentAction
import io.legado.app.service.DownloadService
import io.legado.app.utils.startService

object Download {

    fun start(context: Context, url: String, fileName: String) {
        start(context, listOf(url), fileName)
    }

    fun start(context: Context, urls: List<String>, fileName: String) {
        val validUrls = urls.filter { it.isNotBlank() }
        if (validUrls.isEmpty()) return
        context.startService<DownloadService> {
            action = IntentAction.start
            putExtra("url", validUrls.first())
            putStringArrayListExtra("urls", ArrayList(validUrls))
            putExtra("fileName", fileName)
        }
    }

}
