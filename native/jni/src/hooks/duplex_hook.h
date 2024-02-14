#pragma once


namespace DuplexHook {
    HOOK_DEF(jboolean, IsSameObject, JNIEnv * env, jobject obj1, jobject obj2) {
        if (obj1 == nullptr || obj2 == nullptr) return IsSameObject_original(env, obj1, obj2);

        auto clazz = env->FindClass("java/lang/Class");
        if (!env->IsInstanceOf(obj1, clazz)) return IsSameObject_original(env, obj1, obj2);

        jstring obj1ClassName = (jstring) env->CallObjectMethod(obj1, env->GetMethodID(clazz, "getName", "()Ljava/lang/String;"));
        const char* obj1ClassNameStr = env->GetStringUTFChars(obj1ClassName, nullptr);

        if (strstr(obj1ClassNameStr, "com.snapchat.client.duplex.MessageHandler") != 0) {
            env->ReleaseStringUTFChars(obj1ClassName, obj1ClassNameStr);
            return JNI_FALSE;
        }

        env->ReleaseStringUTFChars(obj1ClassName, obj1ClassNameStr);
        return IsSameObject_original(env, obj1, obj2);
    }

    void init(JNIEnv* env) {
        DobbyHook((void *)env->functions->IsSameObject, (void *)IsSameObject, (void **)&IsSameObject_original);
    }
}