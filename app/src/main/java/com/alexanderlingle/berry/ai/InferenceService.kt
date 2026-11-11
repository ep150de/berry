package com.alexanderlingle.berry.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Abstraction over the on-device inference backend, so the model file and
 * the engine can each be swapped without touching the UI or ViewModel.
 *
 * Current backend: llama.cpp via JNI ([LlamaCppInferenceService]).
 * The native library is OPTIONAL at build time: if `libberry_llama.so` is
 * not in `jniLibs/`, [LlamaBridge.isAvailable] is false, the app still
 * builds and runs, and every generate call fails with a plain message
 * telling the user the AI engine isn't installed yet. That is the stub
 * fallback — it is never presented as a working model.
 */
interface InferenceService {
    val engineName: String

    /** False when the native library is missing; generate() then fails fast. */
    val isAvailable: Boolean

    /** Loads [modelPath] into memory. Safe to call repeatedly (no-op if loaded). */
    suspend fun load(modelPath: String): Result<Unit>

    /** Generates at most [AiConfig.MAX_TOKENS] tokens. Must be called after [load]. */
    suspend fun generate(prompt: String): Result<String>

    /** Frees the model. Safe to call when nothing is loaded. */
    fun unload()
}

/**
 * JNI bridge to the llama.cpp-based native library.
 *
 * The Kotlin side declares the functions; the native side is built
 * separately from llama.cpp (see app/src/main/cpp/README.md). JNI names
 * for these members (non-static, inside a Kotlin object) are:
 * - Java_com_alexanderlingle_berry_ai_LlamaBridge_nativeLoad
 * - Java_com_alexanderlingle_berry_ai_LlamaBridge_nativeGenerate
 * - Java_com_alexanderlingle_berry_ai_LlamaBridge_nativeUnload
 * Each takes the object as its second (jobject) argument.
 */
object LlamaBridge {
    /** True only if `libberry_llama.so` loaded. Never throws. */
    val isAvailable: Boolean = try {
        System.loadLibrary("berry_llama")
        true
    } catch (e: Throwable) {
        false
    }

    external fun nativeLoad(modelPath: String, contextSize: Int): Boolean

    /** Returns the generated text, or null on native failure. */
    external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        repeatPenalty: Float
    ): String?

    external fun nativeUnload()
}

/** llama.cpp backend. All native calls happen off the main thread. */
class LlamaCppInferenceService : InferenceService {
    override val engineName: String = "llama.cpp (LFM3-1.7B GGUF)"
    override val isAvailable: Boolean = LlamaBridge.isAvailable

    private val lock = Mutex()
    private var loadedPath: String? = null

    override suspend fun load(modelPath: String): Result<Unit> {
        if (!isAvailable) {
            return Result.failure(
                IllegalStateException(
                    "AI engine not installed: libberry_llama.so is missing. " +
                        "See app/src/main/cpp/README.md for the build steps."
                )
            )
        }
        return lock.withLock {
            if (loadedPath == modelPath) {
                Result.success(Unit)
            } else {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        LlamaBridge.nativeLoad(modelPath, AiConfig.CONTEXT_TOKENS)
                    }.getOrDefault(false)
                }
                if (ok) {
                    loadedPath = modelPath
                    Result.success(Unit)
                } else {
                    Result.failure(
                        IllegalStateException(
                            "Could not load the model file. It may be the wrong " +
                                "format — check that it is a GGUF file for this engine."
                        )
                    )
                }
            }
        }
    }

    override suspend fun generate(prompt: String): Result<String> {
        if (!isAvailable) {
            return Result.failure(
                IllegalStateException("AI engine not installed (libberry_llama.so missing).")
            )
        }
        if (loadedPath == null) {
            return Result.failure(IllegalStateException("Model is not loaded yet."))
        }
        return lock.withLock {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    LlamaBridge.nativeGenerate(
                        prompt,
                        AiConfig.MAX_TOKENS,
                        AiConfig.TEMPERATURE,
                        AiConfig.TOP_K,
                        AiConfig.REPEAT_PENALTY
                    )
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("The model returned an empty answer. Try again."))
            } else {
                Result.success(text.trim())
            }
        }
    }

    override fun unload() {
        if (isAvailable && loadedPath != null) {
            runCatching { LlamaBridge.nativeUnload() }
            loadedPath = null
        }
    }
}
