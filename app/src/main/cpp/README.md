# Berry v3 — native AI engine (`libberry_llama.so`)

The Kotlin side (`ai/InferenceService.kt`) declares the JNI entry points and
loads `libberry_llama.so` if present. **This folder intentionally contains no
compiled `.so` and no llama.cpp source.** The library is built separately and
dropped into `app/src/main/jniLibs/<abi>/libberry_llama.so` before building
the APK. Without it, the app still builds and runs — Ask Berry just reports
"AI engine not installed yet" instead of answering. That is by design: a
missing engine must never be faked as a working model.

## Required JNI functions

The Kotlin object `com.alexanderlingle.berry.ai.LlamaBridge` declares three
non-static external functions, so the native signatures are (C linkage):

```c
JNIEXPORT jboolean JNICALL
Java_com_alexanderlingle_berry_ai_LlamaBridge_nativeLoad(
    JNIEnv* env, jobject thiz, jstring modelPath, jint contextSize);

JNIEXPORT jstring JNICALL
Java_com_alexanderlingle_berry_ai_LlamaBridge_nativeGenerate(
    JNIEnv* env, jobject thiz, jstring prompt,
    jint maxTokens, jfloat temperature, jint topK, jfloat repeatPenalty);

JNIEXPORT void JNICALL
Java_com_alexanderlingle_berry_ai_LlamaBridge_nativeUnload(
    JNIEnv* env, jobject thiz);
```

Semantics the Kotlin side relies on:

- `nativeLoad` loads the GGUF at `modelPath` with `contextSize` tokens of
  context and returns `JNI_TRUE` on success, `JNI_FALSE` on any failure.
  Calling it twice with the same path must be safe (the Kotlin side already
  guards repeat loads, but the native side should too).
- `nativeGenerate` runs one completion and returns the generated text as a
  Java string, or `NULL` on failure. It must NOT echo the prompt.
- `nativeUnload` frees the model and context. Calling it when nothing is
  loaded must be a safe no-op.

## Build sketch (llama.cpp v0.8.0 or newer — LFM3 support required)

LFM3's tri-hybrid architecture (gated short convolutions + sliding-window
attention + global attention) only loads on llama.cpp builds with LFM3
support, merged upstream and present in b11960 (v0.8.0) and later. Older
builds will fail to load the model — that surfaces as a plain load error
in the app, not a crash.

1. Check out llama.cpp at a v0.8.0+ tag and build the Android shared
   library for `arm64-v8a` (the ABI on every modern phone; add `armeabi-v7a`
   only if you need older devices) using the Android NDK's Clang with the
   llama.cpp CMake Android toolchain file.
2. Add a small JNI translation unit implementing the three functions above
   on top of llama.cpp's C API (`llama_model_load_from_file`,
   `llama_new_context_with_model`, sampler chain, `llama_decode` loop),
   with the sampling parameters passed straight through from the Kotlin
   call (temperature, top_k, repeat penalty, max tokens).
3. Link it together with llama.cpp and ggml into `libberry_llama.so` and
   copy it to:
   - `app/src/main/jniLibs/arm64-v8a/libberry_llama.so`
4. Rebuild the APK. Ask Berry's engine check (`LlamaBridge.isAvailable`)
   flips to true automatically — no Kotlin change needed.

## Fallback model swap (no engine rebuild)

If LFM3 ever fails on a device, the engine stays the same and only the
model file changes: in `ai/AiConfig.kt`, point `MODEL_FILE_NAME` and
`MODEL_URL` at a Qwen3-1.7B GGUF Q4_K_M file (Apache 2.0, also ~1 GB,
also loaded by the same llama.cpp backend). Delete any previously
downloaded LFM3 file from the app (Ask Berry has a "Delete downloaded
model" button) so the new file downloads cleanly.

## What is NOT in this build

- No model file. The ~1.1 GB GGUF is downloaded once, over Wi-Fi, by the
  app itself (see `ai/ModelManager.kt`). Never commit a `.gguf` to this repo.
- No network inference. There is no server call in the answer path; the
  only network use in v3 is that one download.
