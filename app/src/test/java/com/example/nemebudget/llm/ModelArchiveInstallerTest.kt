package com.example.nemebudget.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModelArchiveInstallerTest {

    @Test
    fun findMatchingZip_recursesIntoNestedDownloadFolders() {
        val downloadsRoot = Files.createTempDirectory("downloads-root").toFile()
        try {
            val nestedFolder = File(downloadsRoot, "archives/2026")
            nestedFolder.mkdirs()
            val zipFile = File(nestedFolder, "Qwen3-0.6B-q4f16_1-MLC.zip")
            zipFile.writeText("not-a-real-zip-but-good-enough-for-name-search")

            val found = ModelArchiveInstaller.findMatchingZip(downloadsRoot, "Qwen3-0.6B-q4f16_1-MLC")

            assertNotNull(found)
            assertEquals(zipFile.canonicalPath, found!!.canonicalPath)
        } finally {
            downloadsRoot.deleteRecursively()
        }
    }

    @Test
    fun findMatchingZip_acceptsVariantArchiveNameWithLetterOAndHyphenLayout() {
        val downloadsRoot = Files.createTempDirectory("downloads-variant").toFile()
        try {
            val nestedFolder = File(downloadsRoot, "incoming")
            nestedFolder.mkdirs()
            val zipFile = File(nestedFolder, "Qwen3-o.6B-q4f16-MLC.zip")
            zipFile.writeText("name-matching-test")

            val found = ModelArchiveInstaller.findMatchingZip(downloadsRoot, "Qwen3-0.6B-q4f16_1-MLC")

            assertNotNull(found)
            assertEquals(zipFile.canonicalPath, found!!.canonicalPath)
        } finally {
            downloadsRoot.deleteRecursively()
        }
    }

    @Test
    fun installFromZip_supportsNestedModelFolderArchives() {
        val tempRoot = Files.createTempDirectory("model-install-nested").toFile()
        try {
            val zipFile = File(tempRoot, "Qwen3-0.6B-q4f16_1-MLC.zip")
            createZipArchive(
                zipFile,
                mapOf(
                    "Qwen3-0.6B-q4f16_1-MLC/mlc-chat-config.json" to "{}",
                    "Qwen3-0.6B-q4f16_1-MLC/tokenizer.json" to "nested-tokenizer"
                )
            )
            val targetDir = File(tempRoot, "installed-model")

            val installed = ModelArchiveInstaller.installFromZip(
                zipFile = zipFile,
                targetDir = targetDir,
                modelName = "Qwen3-0.6B-q4f16_1-MLC"
            )

            assertTrue(installed)
            assertTrue(File(targetDir, "mlc-chat-config.json").isFile)
            assertTrue(File(targetDir, "tokenizer.json").isFile)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun installFromZip_supportsFlatArchivesThatAlreadyContainTheSentinel() {
        val tempRoot = Files.createTempDirectory("model-install-flat").toFile()
        try {
            val zipFile = File(tempRoot, "Qwen3-0.6B-q4f16_1-MLC.zip")
            createZipArchive(
                zipFile,
                mapOf(
                    "mlc-chat-config.json" to "{}",
                    "tokenizer.json" to "flat-tokenizer"
                )
            )
            val targetDir = File(tempRoot, "installed-model")

            val installed = ModelArchiveInstaller.installFromZip(
                zipFile = zipFile,
                targetDir = targetDir,
                modelName = "Qwen3-0.6B-q4f16_1-MLC"
            )

            assertTrue(installed)
            assertTrue(File(targetDir, "mlc-chat-config.json").isFile)
            assertTrue(File(targetDir, "tokenizer.json").isFile)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun createZipArchive(zipFile: File, entries: Map<String, String>) {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            entries.forEach { (path, content) ->
                zipOut.putNextEntry(ZipEntry(path))
                zipOut.write(content.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
        }
    }
}


