#pragma once


namespace AssetHook {
    jmethodID native_lib_on_asset_load;
    AAsset* (*AAssetManager_open_original)(AAssetManager*, const char*, int) = nullptr;

    AAsset* AAssetManager_open_hook(AAssetManager* mgr, const char* filename, int mode) {
        if (common::native_config->hook_asset_open) {
            JNIEnv *env = nullptr;
            common::java_vm->GetEnv((void **)&env, JNI_VERSION_1_6);

            if (!env->CallBooleanMethod(common::native_lib_object, native_lib_on_asset_load, env->NewStringUTF(filename))) {
                return nullptr;
            }
        }

        return AAssetManager_open_original(mgr, filename, mode);
    }

    void init(JNIEnv *env) {
        native_lib_on_asset_load = env->GetMethodID(env->GetObjectClass(common::native_lib_object), "shouldLoadAsset", "(Ljava/lang/String;)Z");
        DobbyHook((void *) AAssetManager_open, (void *) AAssetManager_open_hook, (void **) &AAssetManager_open_original);
    }
}