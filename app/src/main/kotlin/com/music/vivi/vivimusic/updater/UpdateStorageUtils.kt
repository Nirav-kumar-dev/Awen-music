package com.music.vivi.vivimusic.updater

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Utility functions for managing downloaded updater APKs.
 */

fun getDownloadedApksDir(context: Context): File {
    return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "vivi_updates")
}

fun getDownloadedApkCount(context: Context): Int {
    val dir = getDownloadedApksDir(context)
    if (!dir.exists() || !dir.isDirectory) return 0
    return dir.listFiles { file ->
        file.isFile && file.name.endsWith(".apk", ignoreCase = true)
    }?.size ?: 0
}

fun clearDownloadedApks(context: Context): Boolean {
    val dir = getDownloadedApksDir(context)
    if (!dir.exists() || !dir.isDirectory) return true
    var allDeleted = true
    dir.listFiles { file ->
        file.isFile && file.name.endsWith(".apk", ignoreCase = true)
    }?.forEach { file ->
        if (!file.delete()) {
            allDeleted = false
        }
    }
    return allDeleted
}

fun autoClearOldApks(context: Context) {
    val dir = getDownloadedApksDir(context)
    if (!dir.exists() || !dir.isDirectory) return
    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
    dir.listFiles { file ->
        file.isFile && file.name.endsWith(".apk", ignoreCase = true) && file.lastModified() < oneDayAgo
    }?.forEach { it.delete() }
}

fun getLatestDownloadedApk(context: Context): File? {
    val dir = getDownloadedApksDir(context)
    val defaultFile = File(dir, "vivi.apk")
    if (defaultFile.exists() && defaultFile.length() > 1024 * 1024) {
        return defaultFile
    }
    if (dir.exists() && dir.isDirectory) {
        val apks = dir.listFiles { file ->
            file.isFile && file.name.endsWith(".apk", ignoreCase = true) && file.length() > 1024 * 1024
        }
        return apks?.maxByOrNull { it.lastModified() }
    }
    return null
}

