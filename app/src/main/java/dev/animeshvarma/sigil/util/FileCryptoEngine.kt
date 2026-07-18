package dev.animeshvarma.sigil.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles reading a single file or a whole directory (picked via SAF) into a single
 * in-memory ZIP payload that [dev.animeshvarma.sigil.crypto.CryptoEngine] can encrypt as
 * one blob, and reconstructing the original file/directory from that payload on decrypt.
 *
 * A small internal manifest entry ("SIGIL_META") records whether the original
 * selection was a single file or a directory, and its display name, so decryption
 * can restore the correct shape on disk.
 */
object FileCryptoEngine {

    private const val META_ENTRY = "SIGIL_META"
    private const val CONTENT_PREFIX = "content/"
    private const val MAX_SIZE = 10 * 1024 * 1024 // Matches CryptoEngine's 10MB safety limit

    data class PackedSource(val name: String, val isDirectory: Boolean, val sizeBytes: Int)

    /**
     * Reads a single document Uri (from OpenDocument) into a ZIP payload with a manifest
     * marking it as a single file.
     */
    fun packFile(context: Context, uri: Uri): Pair<ByteArray, PackedSource> {
        val doc = DocumentFile.fromSingleUri(context, uri)
            ?: throw IllegalArgumentException("无法读取所选文件。")
        val name = doc.name ?: "unnamed"

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法打开文件流。")

        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry(META_ENTRY))
                zos.write("FILE:$name".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry(CONTENT_PREFIX + name))
                zos.write(bytes)
                zos.closeEntry()
            }
            bos.toByteArray()
        }

        require(zipBytes.size <= MAX_SIZE) { "打包后大小超过 10MB 安全限制。" }
        return zipBytes to PackedSource(name, isDirectory = false, sizeBytes = zipBytes.size)
    }

    /**
     * Recursively walks a directory tree Uri (from OpenDocumentTree) into a ZIP payload
     * with a manifest marking it as a directory, preserving relative paths.
     */
    fun packDirectory(context: Context, treeUri: Uri): Pair<ByteArray, PackedSource> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("无法读取所选文件夹。")
        val rootName = root.name ?: "folder"

        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry(META_ENTRY))
                zos.write("DIR:$rootName".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                var runningSize = 0
                fun walk(dir: DocumentFile, relativePath: String) {
                    for (child in dir.listFiles()) {
                        val childName = child.name ?: continue
                        val childPath = if (relativePath.isEmpty()) childName else "$relativePath/$childName"
                        if (child.isDirectory) {
                            walk(child, childPath)
                        } else {
                            val childBytes = context.contentResolver.openInputStream(child.uri)?.use { it.readBytes() }
                                ?: ByteArray(0)
                            runningSize += childBytes.size
                            require(runningSize <= MAX_SIZE) { "文件夹总大小超过 10MB 安全限制。" }
                            zos.putNextEntry(ZipEntry(CONTENT_PREFIX + childPath))
                            zos.write(childBytes)
                            zos.closeEntry()
                        }
                    }
                }
                walk(root, "")
            }
            bos.toByteArray()
        }

        require(zipBytes.size <= MAX_SIZE) { "打包后大小超过 10MB 安全限制。" }
        return zipBytes to PackedSource(rootName, isDirectory = true, sizeBytes = zipBytes.size)
    }

    /**
     * Reads raw bytes from an arbitrary content Uri (used to load an existing .sigil
     * container for decryption).
     */
    fun readBytes(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取所选文件。")
    }

    /**
     * Writes raw bytes as a new document inside the given destination tree Uri.
     * Returns the created document's Uri.
     */
    fun writeToTree(context: Context, destTreeUri: Uri, fileName: String, bytes: ByteArray, mimeType: String = "application/octet-stream"): Uri {
        val destDir = DocumentFile.fromTreeUri(context, destTreeUri)
            ?: throw IllegalArgumentException("无法访问所选保存目录。")

        destDir.findFile(fileName)?.delete()

        val newFile = destDir.createFile(mimeType, fileName)
            ?: throw IllegalArgumentException("无法在所选目录创建文件。")

        context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(bytes) }
            ?: throw IllegalArgumentException("无法写入文件。")

        return newFile.uri
    }

    /**
     * Unpacks a decrypted ZIP payload (produced by [packFile]/[packDirectory]) back onto
     * disk inside the given destination tree Uri, restoring a single file or a whole
     * directory structure as appropriate.
     *
     * @return The display name of the restored file or root directory.
     */
    fun unpackToTree(context: Context, destTreeUri: Uri, decryptedZip: ByteArray): String {
        val destDir = DocumentFile.fromTreeUri(context, destTreeUri)
            ?: throw IllegalArgumentException("无法访问所选保存目录。")

        var metaLine: String? = null
        val entries = mutableMapOf<String, ByteArray>()

        ZipInputStream(decryptedZip.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val content = zis.readBytes()
                if (entry.name == META_ENTRY) {
                    metaLine = String(content, Charsets.UTF_8)
                } else if (entry.name.startsWith(CONTENT_PREFIX)) {
                    entries[entry.name.removePrefix(CONTENT_PREFIX)] = content
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val meta = metaLine ?: throw IllegalArgumentException("加密容器缺少内部清单，可能已损坏。")
        val (kind, originalName) = meta.split(":", limit = 2).let { it.getOrElse(0) { "FILE" } to it.getOrElse(1) { "restored" } }

        if (kind == "DIR") {
            val rootDir = destDir.findFile(originalName)?.takeIf { it.isDirectory }
                ?: destDir.createDirectory(originalName)
                ?: throw IllegalArgumentException("无法创建目标文件夹。")

            for ((relativePath, content) in entries) {
                val parts = relativePath.split("/")
                var currentDir = rootDir
                for (i in 0 until parts.size - 1) {
                    currentDir = currentDir.findFile(parts[i])?.takeIf { it.isDirectory }
                        ?: currentDir.createDirectory(parts[i])
                        ?: throw IllegalArgumentException("无法创建子文件夹 ${parts[i]}。")
                }
                val fileName = parts.last()
                currentDir.findFile(fileName)?.delete()
                val newFile = currentDir.createFile("application/octet-stream", fileName)
                    ?: throw IllegalArgumentException("无法创建文件 $fileName。")
                context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(content) }
            }
            return originalName
        } else {
            val content = entries.values.firstOrNull() ?: ByteArray(0)
            destDir.findFile(originalName)?.delete()
            val newFile = destDir.createFile("application/octet-stream", originalName)
                ?: throw IllegalArgumentException("无法创建文件。")
            context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(content) }
            return originalName
        }
    }
}
