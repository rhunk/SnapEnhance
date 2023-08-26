#pragma once
#include <unistd.h>
#include <dlfcn.h>
#include <elf.h>

namespace util {
    typedef struct {
        uintptr_t base;
        size_t size;
    } ModuleInfo;

    static void hexDump(void* ptr, uint8_t line_length, uint32_t lines) {
        auto* p = (unsigned char*)ptr;
        for (uint8_t i = 0; i < lines; i++) {
            std::string line;
            for (uint8_t j = 0; j < line_length; j++) {
                char buf[3];
                sprintf(buf, "%02x", p[i * line_length + j]);
                line += buf;
                line += " ";
            }
            LOGI("%s", line.c_str());
        }
    }

    static ModuleInfo get_module(const char*  libname)
    {
        char path[256];
        char buff[256];
        int len_libname = strlen(libname);
        FILE* file;
        uintptr_t addr = 0;
        size_t size = 0;

        snprintf(path, sizeof path, "/proc/%d/smaps", getpid());
        file = fopen(path, "rt");
        if (file == NULL)
            return {0, 0};

        while (fgets(buff, sizeof buff, file) != NULL) {
            int  len = strlen(buff);
            if (len > 0 && buff[len-1] == '\n') {
                buff[--len] = '\0';
            }
            if (len <= len_libname || memcmp(buff + len - len_libname, libname, len_libname)) {
                continue;
            }
            size_t start, end, offset;
            char flags[4];
            if (sscanf(buff, "%zx-%zx %c%c%c%c %zx", &start, &end,
                       &flags[0], &flags[1], &flags[2], &flags[3], &offset) != 7) {
                continue;
            }

            if (flags[0] != 'r' || flags[2] != 'x') {
                continue;
            }
            addr = start - offset;
            size = end - start;
            break;
        }
        fclose(file);
        return {addr, size};
    }

    void load_library(JNIEnv* env, jobject classLoader, const char* libName) {
        auto runtimeClass = env->FindClass("java/lang/Runtime");
        auto getRuntimeMethod = env->GetStaticMethodID(runtimeClass, "getRuntime", "()Ljava/lang/Runtime;");
        auto runtime = env->CallStaticObjectMethod(runtimeClass, getRuntimeMethod);
        auto loadLibraryMethod = env->GetMethodID(runtimeClass, "loadLibrary0", "(Ljava/lang/ClassLoader;Ljava/lang/String;)V");
        env->CallVoidMethod(runtime, loadLibraryMethod, classLoader, env->NewStringUTF(libName));
    }
}