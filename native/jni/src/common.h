#pragma once

#include <stdint.h>
#include "util.h"

#ifdef __aarch64__
#define ARM64 true
#else
#define ARM64 false
#endif

typedef struct {
    bool disable_bitmoji;
    bool disable_metrics;
    bool hook_asset_open;
} native_config_t;

namespace common {
    static JavaVM *java_vm;
    static jobject native_lib_object;

    static util::module_info_t client_module;
    static native_config_t *native_config = new native_config_t;
}
