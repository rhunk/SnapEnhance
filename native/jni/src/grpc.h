#pragma once

namespace grpc {
    typedef struct {
        void* ref_counter;
        size_t length;
        uint8_t* data;
    } ref_counted_slice_byte_buffer;

    typedef struct {
        void* reserved;
        void* type;
        void* compression;
        ref_counted_slice_byte_buffer *slice_buffer;
    } grpc_byte_buffer;
}
