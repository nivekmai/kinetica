#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <sys/stat.h>

namespace {
std::atomic<int64_t> request{0};
std::once_flag initialized;
std::mutex engine_mutex;
using Clock = std::chrono::steady_clock;
struct Run {
    int64_t ticket;
    Clock::time_point deadline;
    bool cancelled() const { return request.load() != ticket || Clock::now() >= deadline; }
};
bool abort_run(void * data) { return static_cast<Run *>(data)->cancelled(); }
bool load_progress(float, void * data) { return !abort_run(data); }
void quiet_log(ggml_log_level, const char *, void *) {}
using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
Model model(nullptr, llama_model_free);
Context context(nullptr, llama_free);
std::string identity;
int capacity = 0;
// Only the verified prompt prefix of a completed request can be reused. Generated
// text is never included: the next request must still read authoritative raw ASR.
std::vector<llama_token> cached_prompt;
void release() { cached_prompt.clear(); context.reset(); model.reset(); identity.clear(); capacity = 0; }
jbyteArray result(JNIEnv * env, const std::string & code, const std::string & text = "") {
    std::string bytes = code + "\n" + text;
    auto out = env->NewByteArray(bytes.size());
    if (out) env->SetByteArrayRegion(out, 0, bytes.size(), reinterpret_cast<const jbyte *>(bytes.data()));
    return out;
}
jbyteArray failure(JNIEnv * env, const Run & run, const char * stage, const char * code) {
    return result(env, request.load() != run.ticket ? "CANCELLED" :
        Clock::now() >= run.deadline ? "TIMEOUT" : code, stage);
}
// Every exit detaches the stack-owned cancellation state; context can safely survive the request.
struct Detach {
    bool completed = false;
    ~Detach() {
        if (!completed) cached_prompt.clear(); // Failed/aborted decode may leave partial KV state.
        if (context) llama_set_abort_callback(context.get(), nullptr, nullptr);
    }
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kinetica_keyboard_voice_LocalCleanup_nextRequest(JNIEnv *, jobject) { return ++request; }

extern "C" JNIEXPORT void JNICALL
Java_com_kinetica_keyboard_voice_LocalCleanup_releaseEngine(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    release();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_kinetica_keyboard_voice_LocalCleanup_generate(
    JNIEnv * env, jobject, jlong ticket, jstring model_path, jbyteArray system_bytes, jbyteArray prompt_bytes,
    jint input_limit, jint output_limit, jlong timeout_ms) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    Run run{ticket, Clock::now() + std::chrono::milliseconds(timeout_ms)};
    Detach detach;
    try {
        if (input_limit <= 0 || input_limit > 4096 || output_limit <= 0 || output_limit > 2048 ||
            timeout_ms <= 0 || timeout_ms > 60000) return result(env, "INPUT_LIMIT", "invalid limits");
        if (run.cancelled()) return failure(env, run, "initialization", "ENGINE_ERROR");
        std::call_once(initialized, [] { llama_log_set(quiet_log, nullptr); llama_backend_init(); });
        const char * path_chars = env->GetStringUTFChars(model_path, nullptr);
        if (!path_chars) return nullptr;
        std::string path(path_chars);
        env->ReleaseStringUTFChars(model_path, path_chars);
        struct stat info{};
        if (stat(path.c_str(), &info) != 0) return result(env, "MODEL_ERROR", "model file unavailable");
        // Replacement/import at the same path must invalidate cached weights.
        std::string key = path + ":" + std::to_string(info.st_ino) + ":" + std::to_string(info.st_size) +
            ":" + std::to_string(info.st_mtim.tv_sec) + ":" + std::to_string(info.st_mtim.tv_nsec);
        const int system_length = env->GetArrayLength(system_bytes), length = env->GetArrayLength(prompt_bytes);
        if (system_length <= 0 || system_length > 16000 || length <= 0 || length > 32000)
            return result(env, "INPUT_LIMIT", "request bytes");
        std::string system(system_length, '\0'), prompt(length, '\0');
        env->GetByteArrayRegion(system_bytes, 0, system_length, reinterpret_cast<jbyte *>(system.data()));
        if (env->ExceptionCheck()) return nullptr;
        env->GetByteArrayRegion(prompt_bytes, 0, length, reinterpret_cast<jbyte *>(prompt.data()));
        if (env->ExceptionCheck()) return nullptr;
        if (!model || identity != key) {
            release();
            auto mp = llama_model_default_params();
            mp.n_gpu_layers = 0;
            mp.load_mode = LLAMA_LOAD_MODE_NONE;
            mp.progress_callback = load_progress;
            mp.progress_callback_user_data = &run;
            mp.no_alloc = true;
            Model metadata(llama_model_load_from_file(path.c_str(), mp), llama_model_free);
            if (!metadata || run.cancelled()) return failure(env, run, "metadata", "MODEL_ERROR");
            if (llama_model_n_params(metadata.get()) > 2200000000ULL || llama_model_size(metadata.get()) > 2200000000ULL)
                return result(env, "MODEL_ERROR", "unsupported model size");
            metadata.reset();
            mp.no_alloc = false;
            mp.load_mode = LLAMA_LOAD_MODE_MMAP;
            model.reset(llama_model_load_from_file(path.c_str(), mp));
            if (!model || run.cancelled()) { release(); return failure(env, run, "model loading", "MODEL_ERROR"); }
            identity = key;
        }
        const char * tmpl = llama_model_chat_template(model.get(), nullptr);
        if (!tmpl) return result(env, "MODEL_ERROR", "missing chat template");
        llama_chat_message messages[] = {{"system", system.c_str()}, {"user", prompt.c_str()}};
        int size = llama_chat_apply_template(tmpl, messages, 2, true, nullptr, 0);
        if (size <= 0 || size > 48000) return result(env, "INPUT_LIMIT", "chat template");
        std::vector<char> formatted(size + 1);
        size = llama_chat_apply_template(tmpl, messages, 2, true, formatted.data(), formatted.size());
        if (size <= 0 || size >= static_cast<int>(formatted.size())) return result(env, "MODEL_ERROR", "chat template");
        const llama_vocab * vocab = llama_model_get_vocab(model.get());
        int count = -llama_tokenize(vocab, formatted.data(), size, nullptr, 0, true, true);
        if (count <= 0 || count > input_limit) return result(env, "INPUT_LIMIT", "input tokens=" + std::to_string(count));
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, formatted.data(), size, tokens.data(), count, true, true) != count)
            return result(env, "ENGINE_ERROR", "tokenization");
        int required = input_limit + output_limit + 128;
        if (!context || capacity != required) {
            cached_prompt.clear();
            context.reset();
            auto cp = llama_context_default_params();
            cp.n_ctx = required; cp.n_batch = 128; cp.n_ubatch = 128;
            cp.n_threads = 4; cp.n_threads_batch = 4;
            cp.abort_callback = abort_run; cp.abort_callback_data = &run; cp.no_perf = true;
            context.reset(llama_init_from_model(model.get(), cp));
            if (!context) return failure(env, run, "context allocation", "ENGINE_ERROR");
            capacity = required;
        }
        llama_set_abort_callback(context.get(), abort_run, &run);
        int reused = 0;
        // Always decode at least the final input token to obtain fresh logits.
        while (reused < count - 1 && reused < static_cast<int>(cached_prompt.size()) &&
               tokens[reused] == cached_prompt[reused]) ++reused;
        auto memory = llama_get_memory(context.get());
        if (reused == 0 || !llama_memory_seq_rm(memory, 0, reused, -1)) {
            llama_memory_clear(memory, true);
            reused = 0; // Models without partial KV removal safely use the old path.
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(), llama_sampler_free);
        if (!sampler) return result(env, "ENGINE_ERROR", "sampler");
        for (int offset = reused; offset < count; offset += 128) {
            if (run.cancelled()) return failure(env, run, "prefill", "ENGINE_ERROR");
            if (llama_decode(context.get(), llama_batch_get_one(tokens.data() + offset, std::min(128, count - offset))))
                return failure(env, run, "prefill", "ENGINE_ERROR");
        }
        std::string output;
        for (int i = 0; i < output_limit; ++i) {
            if (run.cancelled()) return failure(env, run, "generation", "ENGINE_ERROR");
            llama_token token = llama_sampler_sample(sampler.get(), context.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) {
                cached_prompt = tokens;
                detach.completed = true;
                return result(env, "SUCCESS", output);
            }
            std::vector<char> piece(256);
            int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (n < 0) {
                if (-n > 16000) return result(env, "OUTPUT_LIMIT", "token bytes");
                piece.resize(-n); n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            if (n < 0) return result(env, "ENGINE_ERROR", "token decoding");
            output.append(piece.data(), n);
            if (output.size() > 16000) return result(env, "OUTPUT_LIMIT", "output bytes");
            if (llama_decode(context.get(), llama_batch_get_one(&token, 1))) return failure(env, run, "generation", "ENGINE_ERROR");
        }
        return result(env, "OUTPUT_LIMIT", "output tokens");
    } catch (...) { release(); return failure(env, run, "native exception", "ENGINE_ERROR"); }
}
