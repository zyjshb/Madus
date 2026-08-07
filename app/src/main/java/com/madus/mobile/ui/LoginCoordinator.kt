package com.madus.mobile.ui

import kotlinx.coroutines.CompletableDeferred

/** Bridges suspend login() to Activity result (Bilibili WebView). */
object LoginCoordinator {
    @Volatile
    var pendingCookie: CompletableDeferred<String?>? = null

    fun beginLogin(): CompletableDeferred<String?> {
        pendingCookie?.cancel()
        return CompletableDeferred<String?>().also { pendingCookie = it }
    }

    fun complete(cookie: String?) {
        pendingCookie?.complete(cookie)
        pendingCookie = null
    }
}
