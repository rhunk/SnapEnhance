#include <jni.h>
#include <string>
#include <dobby.h>
#include <vector>
#include <android/asset_manager.h>

#include "logger.h"
#include "config.h"
#include "util.h"
#include "grpc.h"

#ifdef __aarch64__
#define ARM64 true
#else
#define ARM64 false
#endif

static native_config_t *native_config;
static JavaVM *java_vm;
static jobject native_lib_object;
static jmethodID native_lib_on_unary_call_method;
static jmethodID native_lib_on_asset_load;

// original functions
static void *(*unaryCall_original)(void *, const char *, grpc::grpc_byte_buffer **, void *, void *, void *);
static auto fstat_original = (int (*)(int, struct stat *)) nullptr;
static AAsset* (*AAssetManager_open_original)(AAssetManager*, const char*, int) = nullptr;

static int fstat_hook(int fd, struct stat *buf) {
    char name[256];
    memset(name, 0, sizeof(name));
    snprintf(name, sizeof(name), "/proc/self/fd/%d", fd);
    readlink(name, name, sizeof(name));

    std::string fileName(name);

    if (native_config->disable_metrics && fileName.find("files/blizzardv2/queues") != std::string::npos) {
        unlink(name);
        return -1;
    }

    if (native_config->disable_bitmoji && fileName.find("com.snap.file_manager_4_SCContent") != std::string::npos) {
        return -1;
    }

    return fstat_original(fd, buf);
}


static void *unaryCall_hook(void *unk1, const char *uri, grpc::grpc_byte_buffer **buffer_ptr, void *unk4, void *unk5, void *unk6) {
    // request without reference counter can be hooked using xposed ig
    auto slice_buffer = (*buffer_ptr)->slice_buffer;
    if (slice_buffer->ref_counter == 0) {
        return unaryCall_original(unk1, uri, buffer_ptr, unk4, unk5, unk6);
    }

    JNIEnv *env = nullptr;
    java_vm->GetEnv((void **)&env, JNI_VERSION_1_6);

    auto jni_buffer_array = env->NewByteArray(slice_buffer->length);
    env->SetByteArrayRegion(jni_buffer_array, 0, slice_buffer->length, (jbyte *)slice_buffer->data);

    auto native_request_data_object = env->CallObjectMethod(native_lib_object, native_lib_on_unary_call_method, env->NewStringUTF(uri), jni_buffer_array);

    if (native_request_data_object != nullptr) {
        auto native_request_data_class = env->GetObjectClass(native_request_data_object);
        auto is_canceled = env->GetBooleanField(native_request_data_object, env->GetFieldID(native_request_data_class, "canceled", "Z"));

        if (is_canceled) {
            LOGD("canceled request for %s", uri);
            return nullptr;
        }

        auto new_buffer = env->GetObjectField(native_request_data_object, env->GetFieldID(native_request_data_class, "buffer", "[B"));
        auto new_buffer_length = env->GetArrayLength((jbyteArray)new_buffer);
        auto new_buffer_data = env->GetByteArrayElements((jbyteArray)new_buffer, nullptr);

        LOGD("rewrote request for %s (length: %d)", uri, new_buffer_length);
        //we need to allocate a new ref_counter struct and copy the old ref_counter and the new_buffer to it
        const static auto ref_counter_struct_size = (uintptr_t)slice_buffer->data - (uintptr_t)slice_buffer->ref_counter;

        auto new_ref_counter = malloc(ref_counter_struct_size + new_buffer_length);
        //copy the old ref_counter and the native_request_data_object
        memcpy(new_ref_counter, slice_buffer->ref_counter, ref_counter_struct_size);
        memcpy((void *)((uintptr_t)new_ref_counter + ref_counter_struct_size), new_buffer_data, new_buffer_length);

        //free the old ref_counter
        free(slice_buffer->ref_counter);

        //update the slice_buffer
        slice_buffer->ref_counter = new_ref_counter;
        slice_buffer->length = new_buffer_length;
        slice_buffer->data = (uint8_t *)((uintptr_t)new_ref_counter + ref_counter_struct_size);
    }

    return unaryCall_original(unk1, uri, buffer_ptr, unk4, unk5, unk6);
}

static AAsset* AAssetManager_open_hook(AAssetManager* mgr, const char* filename, int mode) {
    if (native_config->hook_asset_open) {
        JNIEnv *env = nullptr;
        java_vm->GetEnv((void **)&env, JNI_VERSION_1_6);

        if (!env->CallBooleanMethod(native_lib_object, native_lib_on_asset_load, env->NewStringUTF(filename))) {
            return nullptr;
        }
    }

    return AAssetManager_open_original(mgr, filename, mode);
}

void JNICALL init(JNIEnv *env, jobject clazz, jobject classloader) {
    LOGD("Initializing native");
    // config
    native_config = new native_config_t;

    // native lib object
    native_lib_object = env->NewGlobalRef(clazz);
    native_lib_on_unary_call_method = env->GetMethodID(env->GetObjectClass(clazz), "onNativeUnaryCall", "(Ljava/lang/String;[B)L" BUILD_NAMESPACE "/NativeRequestData;");
    native_lib_on_asset_load = env->GetMethodID(env->GetObjectClass(clazz), "shouldLoadAsset", "(Ljava/lang/String;)Z");

    // load libclient.so
    util::load_library(env, classloader, "client");
    auto client_module = util::get_module("libclient.so");

    if (client_module.base == 0) {
        LOGE("libclient not found");
        return;
    }

    // client_module.base -= 0x1000;
    // debugging purposes
    LOGD("libclient.so base=0x%0lx, size=0x%0lx", client_module.base, client_module.size);

    // hooks
    DobbyHook((void *)DobbySymbolResolver("libc.so", "fstat"), (void *)fstat_hook, (void **)&fstat_original);

    auto unaryCall_func = util::find_signature(
            client_module.base, client_module.size,
            ARM64 ? "A8 03 1F F8 C2 00 00 94" : "0A 90 00 F0 3F F9",
            ARM64 ? -0x48 : -0x37
    );

    if (unaryCall_func != 0) {
        DobbyHook((void *)unaryCall_func, (void *)unaryCall_hook, (void **)&unaryCall_original);
    } else {
        LOGE("can't find unaryCall signature");
    }

    DobbyHook((void *) AAssetManager_open, (void *) AAssetManager_open_hook, (void **) &AAssetManager_open_original);
    LOGD("Native initialized");
}

void JNICALL load_config(JNIEnv *env, jobject _, jobject config_object) {
    auto native_config_clazz = env->GetObjectClass(config_object);
#define GET_CONFIG_BOOL(name) env->GetBooleanField(config_object, env->GetFieldID(native_config_clazz, name, "Z"))

    native_config->disable_bitmoji = GET_CONFIG_BOOL("disableBitmoji");
    native_config->disable_metrics = GET_CONFIG_BOOL("disableMetrics");
    native_config->hook_asset_open = GET_CONFIG_BOOL("hookAssetOpen");
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *_) {
    // register native methods
    java_vm = vm;
    JNIEnv *env = nullptr;
    vm->GetEnv((void **)&env, JNI_VERSION_1_6);

    auto methods = std::vector<JNINativeMethod>();
    methods.push_back({"init", "(Ljava/lang/ClassLoader;)V", (void *)init});
    methods.push_back({"loadConfig", "(L" BUILD_NAMESPACE "/NativeConfig;)V", (void *)load_config});

    env->RegisterNatives(env->FindClass(std::string(BUILD_NAMESPACE "/NativeLib").c_str()), methods.data(), methods.size());
    return JNI_VERSION_1_6;
}
