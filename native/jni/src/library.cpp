#include <jni.h>
#include <string>
#include <dobby.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fstream>
#include <vector>

#include "logger.h"
#include "config.h"

static native_config_t *native_config;

static auto fstat_original = fstat;
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


#define GET_BOOL_FIELD(env, clazz, field) env->GetBooleanField(clazz, env->GetFieldID(clazz, field, "Z"))

extern "C" JNIEXPORT void JNICALL
loadConfig(JNIEnv *env, jobject  clazz, jobject config_object) {
    auto native_config_class = env->GetObjectClass(config_object);

    native_config->disable_bitmoji = GET_BOOL_FIELD(env, native_config_class, "disableBitmoji");
    native_config->disable_metrics = GET_BOOL_FIELD(env, native_config_class, "disableMetrics");

    LOGD("config loaded");
}

//jni onload
extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD("initializing native");
    // config
    native_config = new native_config_t;

    // hooks
    DobbyHook((void *) fstat_original,(void *) fstat_hook,(void **) &fstat_original);

    // register native methods
    JNIEnv *env = nullptr;
    vm->GetEnv((void **) &env, JNI_VERSION_1_6);

    auto methods = std::vector<JNINativeMethod>();
    methods.push_back({"loadConfig", "(Lme/rhunk/snapenhance/nativelib/NativeConfig;)V", (void *) loadConfig});

    env->RegisterNatives(
            env->FindClass("me/rhunk/snapenhance/nativelib/NativeLib"),
            methods.data(),
            methods.size()
    );

    LOGD("native initialized");

    return JNI_VERSION_1_6;
}
