#pragma once

namespace FstatHook {
    HOOK_DEF(int, fstat_hook, int fd, struct stat *buf) {
        char name[256];
        memset(name, 0, sizeof(name));
        snprintf(name, sizeof(name), "/proc/self/fd/%d", fd);
        readlink(name, name, sizeof(name));

        std::string fileName(name);

        if (common::native_config->disable_metrics && fileName.find("files/blizzardv2/queues") != std::string::npos) {
            unlink(name);
            return -1;
        }

        if (common::native_config->disable_bitmoji && fileName.find("com.snap.file_manager_4_SCContent") != std::string::npos) {
            return -1;
        }

        return fstat_hook_original(fd, buf);
    }

    void init() {
        DobbyHook((void *)DobbySymbolResolver("libc.so", "fstat"), (void *)fstat_hook, (void **)&fstat_hook_original);
    }
}