package com.example.nemebudget

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File

object PersistentShaderCache {
    private const val TAG = "PersistentShaderCache"
    private const val CACHE_DIR_NAME = "tvm_opencl_cache"
    private const val ENV_KEY = "TVM_OPENCL_CACHE_DIR"

    fun initialize(context: Context): File {
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        runCatching {
            Os.setenv(ENV_KEY, cacheDir.absolutePath, true)
            Log.d(TAG, "Set $ENV_KEY=${cacheDir.absolutePath}")
        }.onFailure { error ->
            Log.w(TAG, "Unable to set $ENV_KEY", error)
        }

        return cacheDir
    }

    fun cacheDir(context: Context): File = File(context.filesDir, CACHE_DIR_NAME)

    fun isReady(context: Context): Boolean {
        val dir = cacheDir(context)
        val files = runCatching { dir.listFiles() }.getOrNull().orEmpty()
        return dir.isDirectory && files.isNotEmpty()
    }
}

