package com.madus.mobile.data

/**
 * 应用内「检查更新 / 下载最新版」入口。
 *
 * 用法：
 * 1. 把下面 [GITHUB_RELEASES_URL] 改成你的公开仓库 Releases 地址
 * 2. 发版时在 GitHub Release 里上传 `Madus-x.y.z.apk` / debug 包
 * 3. 用户点「检查更新」会用系统浏览器打开该页下载
 *
 * 推荐用 `.../releases/latest`，会自动跳到最新一条 Release。
 */
object AppUpdate {
    /** GitHub Releases 最新版（用户「检查更新」会打开此页） */
    const val GITHUB_RELEASES_URL =
        "https://github.com/zyjshb/Madus/releases/latest"

    fun isPlaceholderUrl(): Boolean = false
}
