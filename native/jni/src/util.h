#pragma once

#include <unistd.h>

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

    uintptr_t find_signature(uintptr_t module_base, uintptr_t size, const std::string &pattern, int offset = 0) {
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