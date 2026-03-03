package io.legado.app.help.update

object AppUpdateSourceConfig {

    /**
     * 更新检查使用的仓库
     * 需要切换到你自己的仓库时，只改这里即可
     */
    const val repoOwner: String = "skybbk1001"
    const val repoName: String = "legado"

    /**
     * GitHub API 镜像前缀，按顺序回退
     * 空字符串表示直连 GitHub
     */
    private val apiMirrorPrefixes = listOf(
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/",
        ""
    )

    /**
     * 下载链接镜像前缀，按顺序回退
     * 空字符串表示直连 GitHub
     */
    private val downloadMirrorPrefixes = listOf(
        "https://ghproxy.net/",
        "https://gh-proxy.org/",
        ""
    )

    fun releaseApiUrls(beta: Boolean): List<String> {
        val githubApiUrl = if (beta) {
            "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/beta"
        } else {
            "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        }
        return apiMirrorPrefixes
            .map { prefix -> if (prefix.isBlank()) githubApiUrl else prefix + githubApiUrl }
            .distinct()
    }

    fun downloadUrls(githubDownloadUrl: String): List<String> {
        return downloadMirrorPrefixes
            .map { prefix ->
                if (prefix.isBlank()) githubDownloadUrl else prefix + githubDownloadUrl
            }
            .distinct()
    }
}

