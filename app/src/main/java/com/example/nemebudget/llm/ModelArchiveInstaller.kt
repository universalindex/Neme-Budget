package com.example.nemebudget.llm

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipInputStream

object ModelArchiveInstaller {
    private const val MODEL_SENTINEL = "mlc-chat-config.json"

    fun isModelReady(modelDir: File): Boolean {
        return modelDir.isDirectory && File(modelDir, MODEL_SENTINEL).isFile
    }

    fun findMatchingZip(downloadsRoot: File, modelName: String): File? {
        if (!downloadsRoot.exists()) return null

        return runCatching { findMatchingZipRecursive(downloadsRoot, modelName) }.getOrNull()
    }

    fun installFromZip(zipFile: File, targetDir: File, modelName: String): Boolean {
        if (!zipFile.isFile) return false

        val stagingDir = File(targetDir.parentFile ?: return false, "${targetDir.name}.staging")
        stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs() && !stagingDir.isDirectory) return false

        return try {
            extractZip(zipFile, stagingDir)
            val sourceRoot = resolveSourceRoot(stagingDir, modelName) ?: return false

            if (targetDir.exists()) targetDir.deleteRecursively()
            if (!targetDir.mkdirs() && !targetDir.isDirectory) return false

            copyDirectoryContents(sourceRoot, targetDir)
            isModelReady(targetDir)
        } catch (_: Exception) {
            false
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    fun installFromZipStream(zipStream: InputStream, targetDir: File, modelName: String): Boolean {
        val stagingDir = File(targetDir.parentFile ?: return false, "${targetDir.name}.staging")
        stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs() && !stagingDir.isDirectory) return false

        return try {
            extractZip(zipStream, stagingDir)
            val sourceRoot = resolveSourceRoot(stagingDir, modelName) ?: return false

            if (targetDir.exists()) targetDir.deleteRecursively()
            if (!targetDir.mkdirs() && !targetDir.isDirectory) return false

            copyDirectoryContents(sourceRoot, targetDir)
            isModelReady(targetDir)
        } catch (_: Exception) {
            false
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun findMatchingZipRecursive(directory: File, modelName: String): File? {
        val children = runCatching { directory.listFiles() }.getOrNull() ?: return null
        for (child in children) {
            when {
                child.isFile -> {
                    if (isLikelyModelZip(child.name, modelName)) {
                        return child
                    }
                }
                child.isDirectory -> {
                    findMatchingZipRecursive(child, modelName)?.let { return it }
                }
            }
        }
        return null
    }

    private fun isLikelyModelZip(fileName: String, modelName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        if (!lower.endsWith(".zip")) return false

        val normalized = normalizeName(lower.removeSuffix(".zip"))
        val normalizedModelName = normalizeName(modelName)
        if (normalized.contains(normalizedModelName) || normalizedModelName.contains(normalized)) {
            return true
        }

        val coreSignals = listOf("qwen3", "q4f16", "mlc")
        if (coreSignals.any { !normalized.contains(it) }) return false

        return normalized.contains("06b") || normalized.contains("06")
    }

    private fun normalizeName(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace('o', '0')
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun resolveSourceRoot(stagingDir: File, modelName: String): File? {
        val nestedModelDir = File(stagingDir, modelName)
        if (isModelReady(nestedModelDir)) return nestedModelDir
        if (isModelReady(stagingDir)) return stagingDir
        return findDirectoryContainingSentinel(stagingDir)
    }

    private fun findDirectoryContainingSentinel(root: File): File? {
        if (isModelReady(root)) return root

        val children = runCatching { root.listFiles() }.getOrNull() ?: return null
        for (child in children) {
            if (child.isDirectory) {
                findDirectoryContainingSentinel(child)?.let { return it }
            }
        }
        return null
    }

    private fun extractZip(zipFile: File, destinationDir: File) {
        val destinationCanonicalPath = destinationDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipInput ->
            extractZipEntries(zipInput, destinationDir, destinationCanonicalPath)
        }
    }

    private fun extractZip(zipStream: InputStream, destinationDir: File) {
        val destinationCanonicalPath = destinationDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(zipStream)).use { zipInput ->
            extractZipEntries(zipInput, destinationDir, destinationCanonicalPath)
        }
    }

    private fun extractZipEntries(zipInput: ZipInputStream, destinationDir: File, destinationCanonicalPath: String) {
        while (true) {
            val entry = zipInput.nextEntry ?: break
            val outFile = File(destinationDir, entry.name)
            val canonicalOutPath = outFile.canonicalPath
            if (canonicalOutPath != destinationDir.canonicalPath && !canonicalOutPath.startsWith(destinationCanonicalPath)) {
                throw IOException("Zip entry escapes destination: ${entry.name}")
            }

            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { output ->
                    zipInput.copyTo(output)
                }
            }

            zipInput.closeEntry()
        }
    }

    private fun copyDirectoryContents(sourceDir: File, destinationDir: File) {
        val children = sourceDir.listFiles() ?: return
        for (child in children) {
            val destinationChild = File(destinationDir, child.name)
            if (child.isDirectory) {
                child.copyRecursively(destinationChild, overwrite = true)
            } else {
                destinationChild.parentFile?.mkdirs()
                child.copyTo(destinationChild, overwrite = true)
            }
        }
    }
}



