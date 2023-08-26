#include <jni.h>
#include <string>
#include <dobby.h>
#include <unistd.h>
#include <vector>

#include "logger.h"
#include "config.h"
#include "util.h"

static native_config_t *native_config;

static auto fstat_original = (int (*)(int, struct stat *)) nullptr;
static int fstat_hook(int fd, struct stat *buf) {
    char name[256];
    memset(name, 0, 256);
    snprintf(name, sizeof(name), "/proc/self/fd/%d", fd);
    readlink(name, name, sizeof(name));

    auto fileName = std::string(name);

    //prevent blizzardv2 metrics
    if (native_config->disable_metrics && fileName.find("files/blizzardv2/queues") != std::string::npos) {
        unlink(name);
        return -1;
    }

    //prevent bitmoji to load
    if (native_config->disable_bitmoji && fileName.find("com.snap.file_manager_4_SCContent") != std::string::npos) {
        return -1;
    }

    return fstat_original(fd, buf);
}


extern "C" JNIEXPORT void JNICALL
init(JNIEnv *env, jobject clazz, jobject classloader) {
    LOGD("initializing native");
    // config
    native_config = new native_config_t;

    // load libclient.so
    util::load_library(env, classloader, "client");
    auto client_module = util::get_module("libclient.so");
    if (client_module.base == 0) {
        LOGE("libclient not found");
        return;
    }
    client_module.base -= 0x1000;
    LOGD("libclient: offset: 0x%x size: 0x%x", client_module.base, client_module.size);

    // hooks
    DobbyHook((void *) DobbySymbolResolver("libc.so", "fstat"), (void *) fstat_hook,
              (void **) &fstat_original);

    LOGD("native initialized");
}


extern "C" JNIEXPORT void JNICALL
loadConfig(JNIEnv *env, jobject clazz, jobject config_object) {
    auto native_config_clazz = env->GetObjectClass(config_object);
    #define GET_CONFIG_BOOL(name) env->GetBooleanField(config_object, env->GetFieldID(native_config_clazz, name, "Z"))

    native_config->disable_bitmoji = GET_CONFIG_BOOL("disableBitmoji");
    native_config->disable_metrics = GET_CONFIG_BOOL("disableMetrics");

    LOGD("config loaded");
}

//jni onload
extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    // register native methods
    JNIEnv *env = nullptr;
    vm->GetEnv((void **) &env, JNI_VERSION_1_6);

    auto methods = std::vector<JNINativeMethod>();
    methods.push_back({"init", "(Ljava/lang/ClassLoader;)V", (void *) init});
    methods.push_back({"loadConfig", "(Lme/rhunk/snapenhance/nativelib/NativeConfig;)V", (void *) loadConfig});

    env->RegisterNatives(
            env->FindClass("me/rhunk/snapenhance/nativelib/NativeLib"),
            methods.data(),
            methods.size()
    );
    return JNI_VERSION_1_6;
}
