package com.example.nemebudget

import android.content.Context
import android.system.Os
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object PersistentShaderCache {
    private const val TAG = "PersistentShaderCache"
    private const val CACHE_DIR_NAME = "tvm_opencl_cache"
    private const val STATE_FILE_NAME = "tvm_opencl_cache_state.json"
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

    fun stateFile(context: Context): File = File(context.filesDir, STATE_FILE_NAME)

    fun isReady(context: Context, modelFingerprint: String?): Boolean {
        if (!hasCacheArtifacts(context)) return false
        val state = readWarmupState(context) ?: return true
        return modelFingerprint == null || state.modelFingerprint == modelFingerprint
    }

    fun hasCacheArtifacts(context: Context): Boolean {
        val dir = cacheDir(context)
        return dir.isDirectory && countFilesRecursively(dir) > 0
    }

    fun markWarmupComplete(context: Context, modelFingerprint: String) {
        val artifactCount = countFilesRecursively(cacheDir(context))
        val payload = JSONObject().apply {
            put("modelFingerprint", modelFingerprint)
            put("completedAtMillis", System.currentTimeMillis())
            put("artifactCount", artifactCount)
        }

        runCatching {
            stateFile(context).writeText(payload.toString())
        }.onSuccess {
            Log.d(TAG, "Recorded warmup completion for model fingerprint=$modelFingerprint (artifactCount=$artifactCount)")
        }.onFailure { error ->
            Log.w(TAG, "Unable to persist shader warmup state", error)
        }
    }

    fun clearWarmupMark(context: Context) {
        runCatching { stateFile(context).delete() }
    }

    fun syncToDisk() {
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sync"))
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy()
                Log.w(TAG, "Filesystem sync command timed out.")
            } else {
                Log.d(TAG, "Requested filesystem sync for shader cache.")
            }
        }.onFailure { error ->
            Log.w(TAG, "Filesystem sync request failed", error)
        }
    }

    private fun readWarmupState(context: Context): WarmupState? {
        val file = stateFile(context)
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            WarmupState(
                modelFingerprint = json.optString("modelFingerprint").takeIf { it.isNotBlank() },
                completedAtMillis = json.optLong("completedAtMillis", 0L),
                artifactCount = json.optInt("artifactCount", 0)
            )
        }.getOrNull()
    }

    private data class WarmupState(
        val modelFingerprint: String?,
        val completedAtMillis: Long,
        val artifactCount: Int
    )

    private fun countFilesRecursively(root: File): Int {
        if (!root.exists()) return 0
        if (root.isFile) return 1
        val children = runCatching { root.listFiles() }.getOrNull().orEmpty()
        var count = 0
        for (child in children) {
            count += countFilesRecursively(child)
        }
        return count
    }
}




