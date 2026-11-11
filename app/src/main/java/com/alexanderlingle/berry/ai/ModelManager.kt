package com.alexanderlingle.berry.ai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Owns the model file's lifecycle: where it lives, whether it is present
 * and intact, and the one-time Wi-Fi download that fetches it.
 *
 * Privacy: the ONLY network call in Berry v3 is this download, from
 * Hugging Face, once. Nothing about the baby, the logs, or the prompts
 * ever leaves the device — inference itself is fully offline.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val BUFFER_SIZE = 8192
    }

    /** The model file, or null when it has not been downloaded yet. */
    val modelFile: File
        get() = File(File(context.filesDir, AiConfig.MODEL_DIR_NAME), AiConfig.MODEL_FILE_NAME)

    /** True when a plausibly complete model file is on disk. */
    fun isDownloaded(): Boolean {
        val file = modelFile
        return file.exists() && file.length() >= AiConfig.MIN_MODEL_BYTES
    }

    /** Deletes the downloaded model (frees ~1.1 GB). The app keeps working;
     *  Ask Berry just returns to its "not downloaded yet" state. */
    fun deleteModel() {
        runCatching { modelFile.delete() }
    }

    /**
     * Refuses to load on devices that cannot safely hold the model.
     * Returns null when loading is fine, otherwise a human explanation.
     */
    fun lowRamReason(): String? {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return if (info.totalMem < AiConfig.MIN_TOTAL_RAM_BYTES) {
            "This phone has about ${info.totalMem / (1024 * 1024)} MB of RAM, " +
                "which is too little to safely hold the ~1 GB model. " +
                "Ask Berry needs a phone with at least 3 GB of RAM."
        } else {
            null
        }
    }

    /** True when the active network is Wi-Fi. Downloads default to Wi-Fi only. */
    fun isOnWifi(): Boolean {
        val manager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Downloads the model, emitting progress 0.0–1.0 (-1.0 when the server
     * did not report a length). Writes to a temp file and renames only on
     * success, so a failed or cancelled download never leaves a corrupt
     * "model" behind. When [allowCellular] is false and the phone is not
     * on Wi-Fi, the flow fails immediately with a plain explanation.
     */
    fun download(allowCellular: Boolean = false): Flow<Float> = flow {
        if (!allowCellular && !isOnWifi()) {
            throw IllegalStateException(
                "Connect to Wi-Fi to download the model (about 1.1 GB), " +
                    "or confirm a cellular download."
            )
        }
        val dir = File(context.filesDir, AiConfig.MODEL_DIR_NAME).apply { mkdirs() }
        val tmp = File(dir, AiConfig.MODEL_FILE_NAME + ".part")
        val connection = URL(AiConfig.MODEL_URL).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(
                    "Model download failed (HTTP ${connection.responseCode}). " +
                        "The file name may have changed on Hugging Face — " +
                        "check AiConfig.MODEL_FILE_NAME against the repo file list."
                )
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            var lastEmitted = -1f
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (total > 0) downloaded.toFloat() / total else -1f
                        if (progress < 0 || progress - lastEmitted >= 0.01f) {
                            emit(progress)
                            lastEmitted = progress
                        }
                    }
                }
            }
            if (tmp.length() < AiConfig.MIN_MODEL_BYTES) {
                throw IllegalStateException(
                    "Download finished but the file is only ${tmp.length() / (1024 * 1024)} MB — " +
                        "too small to be the model. Check your connection and try again."
                )
            }
            AiConfig.EXPECTED_SHA256?.let { expected ->
                if (sha256(tmp) != expected.lowercase()) {
                    throw IllegalStateException(
                        "Downloaded file failed its SHA-256 check and was discarded."
                    )
                }
            }
            if (!tmp.renameTo(modelFile)) {
                throw IllegalStateException("Could not install the downloaded model file.")
            }
            emit(1f)
        } finally {
            connection.disconnect()
            if (tmp.exists() && !modelFile.exists()) {
                tmp.delete()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
