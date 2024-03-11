#pragma once

#include <unistd.h>
#include <sys/mman.h>

#define HOOK_DEF(ret, func, ...) ret (*func##_original)(__VA_ARGS__); ret func(__VA_ARGS__)

namespace util {
    typedef struct {
        uintptr_t base;
        size_t size;
    } module_info_t;

    static module_info_t get_module(const char *libname) {
        char buff[256];
        int len_libname = strlen(libname);
        uintptr_t start_offset = 0;
        uintptr_t end_offset = 0;

        auto file = fopen("/proc/self/smaps", "rt");
        if (file == NULL)
            return {0, 0};

        while (fgets(buff, sizeof buff, file) != NULL) {
            int len = strlen(buff);
            if (len > 0 && buff[len - 1] == '\n') {
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

            if (start_offset == 0) {
                start_offset = start;
            }
            end_offset = end;
        }
        fclose(file);
        if (start_offset == 0) {
            return {0, 0};
        }
        return { start_offset, end_offset - start_offset };
    }

    static void remap_sections(const char* path) {
        char buff[256];
        auto maps = fopen("/proc/self/maps", "rt");

        while (fgets(buff, sizeof buff, maps) != NULL) {
            int len = strlen(buff);
            if (len > 0 && buff[len - 1] == '\n') buff[--len] = '\0';
            if (strstr(buff, path) == nullptr) continue;

            size_t start, end, offset;
            char flags[4];

            if (sscanf(buff, "%zx-%zx %c%c%c%c %zx", &start, &end,
                       &flags[0], &flags[1], &flags[2], &flags[3], &offset) != 7) continue;

            LOGD("Remapping 0x%zx-0x%zx", start, end);

            auto section_size = end - start;
            auto section_ptr = mmap(0, section_size, PROT_READ | PROT_EXEC | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);

            if (section_ptr == MAP_FAILED) {
                LOGE("mmap failed: %s", strerror(errno));
                break;
            }

            memcpy(section_ptr, (void *)start, section_size);

            if (mremap(section_ptr, section_size, section_size, MREMAP_MAYMOVE | MREMAP_FIXED, start) == MAP_FAILED) {
                LOGE("mremap failed: %s", strerror(errno));
                break;
            }

            mprotect((void *)start, section_size, (flags[0] == 'r' ? PROT_READ : 0) | (flags[1] == 'w' ? PROT_WRITE : 0) | (flags[2] == 'x' ? PROT_EXEC : 0));
        }
        fclose(maps);
    }

    static uintptr_t find_signature(uintptr_t module_base, uintptr_t size, const std::string &pattern, int offset = 0) {
        std::vector<char> bytes;
        std::vector<char> mask;
        for (size_t i = 0; i < pattern.size(); i += 3) {
            if (pattern[i] == '?') {
                bytes.push_back(0);
                mask.push_back('?');
            } else {
                bytes.push_back(std::stoi(pattern.substr(i, 2), nullptr, 16));
                mask.push_back('x');
            }
        }

        for (size_t i = 0; i < size; i++) {
            bool found = true;
            for (size_t j = 0; j < bytes.size(); j++) {
                if (mask[j] == '?' || bytes[j] == *(char *) (module_base + i + j)) {
                    continue;
                }
                found = false;
                break;
            }
            if (found) {
                return module_base + i + offset;
            }
        }
        return 0;
    }
}