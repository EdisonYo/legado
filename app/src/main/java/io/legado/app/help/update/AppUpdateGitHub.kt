package io.legado.app.help.update

import androidx.annotation.Keep
import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "beta_releaseA_version" -> AppVariant.BETA_RELEASEA
            else -> AppConst.appInfo.appVariant
        }

    private suspend fun getLatestRelease(): List<AppReleaseInfo> {
        val apiUrls = AppUpdateSourceConfig.releaseApiUrls(checkVariant.isBeta())
        var lastError: Throwable? = null
        apiUrls.forEach { apiUrl ->
            kotlin.runCatching {
                val res = okHttpClient.newCallResponse {
                    url(apiUrl)
                }
                if (!res.isSuccessful) {
                    throw NoStackTraceException("获取新版本出错(${res.code})")
                }
                val body = res.body.text()
                if (body.isBlank()) {
                    throw NoStackTraceException("获取新版本出错")
                }
                return GSON.fromJsonObject<GithubRelease>(body)
                    .getOrElse {
                        throw NoStackTraceException("获取新版本出错 " + it.localizedMessage)
                    }
                    .gitReleaseToAppReleaseInfo()
                    .sortedByDescending { it.createdAt }
            }.onFailure {
                lastError = it
            }
        }
        throw NoStackTraceException("获取新版本出错 " + (lastError?.localizedMessage ?: "未知错误"))
    }

    override fun check(
        scope: CoroutineScope,
    ): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            getLatestRelease()
                .filter { it.appVariant == checkVariant }
                .firstOrNull { it.versionName > AppConst.appInfo.versionName }
                ?.let {
                    val downloadUrls = AppUpdateSourceConfig.downloadUrls(it.downloadUrl)
                    return@async AppUpdate.UpdateInfo(
                        it.versionName,
                        it.note,
                        downloadUrls.firstOrNull() ?: it.downloadUrl,
                        it.name,
                        downloadUrls
                    )
                }
                ?: throw NoStackTraceException("已是最新版本")
        }.timeout(20000)
    }
}
