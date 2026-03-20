package com.webvault.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class VaultFile(
    val id: String,
    val encryptedFile: File,
    val originalName: String,
    val type: VaultType,
    val sizeBytes: Long
)

enum class VaultType { VIDEO, PHOTO, OTHER }

object VaultManager {
    private const val PREFS = "vault_secure_prefs"
    private const val PIN_HASH = "pin_hash"

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        masterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasPin(context: Context): Boolean = prefs(context).contains(PIN_HASH)

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(PIN_HASH, sha256(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val saved = prefs(context).getString(PIN_HASH, null) ?: return false
        return sha256(pin) == saved
    }

    fun vaultDir(context: Context): File = File(context.filesDir, "vault").apply { mkdirs() }

    fun moveFileToVault(context: Context, source: File): Result<File> = runCatching {
        val targetName = "${UUID.randomUUID()}_${source.name}.vault"
        val target = File(vaultDir(context), targetName)

        val encryptedFile = EncryptedFile.Builder(
            context,
            target,
            masterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        source.inputStream().use { input ->
            encryptedFile.openFileOutput().use { output ->
                input.copyTo(output)
            }
        }

        source.delete()
        target
    }

    fun moveDownloadToVault(context: Context, download: DownloadEntity): Result<File> {
        val src = File(download.filePath)
        if (!src.exists()) return Result.failure(IllegalStateException("File not found"))
        return moveFileToVault(context, src)
    }

    fun listVaultFiles(context: Context): List<VaultFile> {
        return vaultDir(context).listFiles().orEmpty().map { file ->
            val originalName = file.name.removePrefix(file.name.substringBefore("_") + "_").removeSuffix(".vault")
            VaultFile(
                id = file.name,
                encryptedFile = file,
                originalName = originalName,
                type = typeForName(originalName),
                sizeBytes = file.length()
            )
        }.sortedByDescending { it.encryptedFile.lastModified() }
    }

    fun decryptImagePreview(context: Context, file: VaultFile): Bitmap? {
        if (file.type != VaultType.PHOTO) return null
        return runCatching {
            val encryptedFile = EncryptedFile.Builder(
                context,
                file.encryptedFile,
                masterKey(context),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileInput().use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }

    private fun typeForName(name: String): VaultType {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".webm") -> VaultType.VIDEO
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") -> VaultType.PHOTO
            else -> VaultType.OTHER
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
