package com.alexanderlingle.berry.ai

/**
 * Berry v3 — on-device AI configuration.
 *
 * The model is NEVER bundled in the source tree or the APK. It is fetched
 * once, over Wi-Fi, on first use (see [ModelManager]) into app-private
 * storage, and everything after that runs fully offline.
 *
 * Default model: Liquid AI LFM3-1.7B, GGUF Q4_K_M (~1.1 GB on disk).
 * LFM3 support landed in llama.cpp b11960 (v0.8.0); the native library
 * must be built from llama.cpp v0.8.0 or newer (see app/src/main/cpp/README.md).
 *
 * FALLBACK SWAP (one line): if LFM3 ever fails to load on a device, point
 * [MODEL_FILE_NAME] and [MODEL_URL] at a Qwen3-1.7B GGUF Q4_K_M file
 * (Apache 2.0, same ~1 GB size, same llama.cpp backend) and rebuild.
 * Nothing else in the AI layer depends on which 1.7B GGUF is loaded.
 */
object AiConfig {
    /** Hugging Face repo hosting the official GGUF builds. */
    const val MODEL_REPO = "LiquidAI/LFM3-1.7B-GGUF"

    /** File downloaded on first run. Verify this name against the repo's file
     *  list before shipping: if the repo renames it, the download fails
     *  loudly with an HTTP error instead of silently fetching the wrong file. */
    const val MODEL_FILE_NAME = "LFM3-1.7B-Q4_K_M.gguf"

    const val MODEL_URL =
        "https://huggingface.co/$MODEL_REPO/resolve/main/$MODEL_FILE_NAME"

    /** Sanity floor for the download: a real Q4_K_M file is ~1.1 GB. Anything
     *  smaller is a truncated download or an error page, never a model. */
    const val MIN_MODEL_BYTES = 800L * 1024 * 1024

    /** Optional SHA-256 of the model file, for post-download verification.
     *  Null = skip hash verification (size check still applies). Fill this
     *  in from the Hugging Face file page when cutting a release build. */
    const val EXPECTED_SHA256: String? = null

    /** Directory (under filesDir) holding the downloaded model. */
    const val MODEL_DIR_NAME = "ai"

    /** Devices with less total RAM than this cannot safely hold the model
     *  plus the app; the UI refuses to load and explains why. */
    const val MIN_TOTAL_RAM_BYTES = 3L * 1024 * 1024 * 1024

    /** llama.cpp context size for our prompts. Our prompts are a few hundred
     *  tokens; 2048 leaves generous headroom without wasting KV-cache RAM.
     *  (The model's 256K native context is irrelevant at this prompt size.) */
    const val CONTEXT_TOKENS = 2048

    /** Sampling, following Liquid's published guidance for LFM3 (temp 0.1,
     *  top_k 50, repeat penalty 1.1), nudged slightly warmer for friendly tips. */
    const val TEMPERATURE = 0.2f
    const val TOP_K = 50
    const val REPEAT_PENALTY = 1.1f

    /** Short answers on purpose: 100–200 tokens keeps a small model accurate
     *  and each answer to a few seconds of inference. */
    const val MAX_TOKENS = 200
}
