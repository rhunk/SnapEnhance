#pragma once

#include "../common.h"
#include "../util.h"

namespace UnaryCallHook {
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

    static jmethodID native_lib_on_unary_call_method;

    HOOK_DEF(void *, unaryCall_hook, void *unk1, const char *uri, grpc::grpc_byte_buffer **buffer_ptr, void *unk4, void *unk5, void *unk6) {
        // request without reference counter can be hooked using xposed ig
        auto slice_buffer = (*buffer_ptr)->slice_buffer;
        if (slice_buffer->ref_counter == 0) {
            return unaryCall_hook_original(unk1, uri, buffer_ptr, unk4, unk5, unk6);
        }

        JNIEnv *env = nullptr;
        common::java_vm->GetEnv((void **)&env, JNI_VERSION_1_6);

        auto jni_buffer_array = env->NewByteArray(slice_buffer->length);
        env->SetByteArrayRegion(jni_buffer_array, 0, slice_buffer->length, (jbyte *)slice_buffer->data);

        auto native_request_data_object = env->CallObjectMethod(common::native_lib_object, native_lib_on_unary_call_method, env->NewStringUTF(uri), jni_buffer_array);

        if (native_request_data_object != nullptr) {
            auto native_request_data_class = env->GetObjectClass(native_request_data_object);
            auto is_canceled = env->GetBooleanField(native_request_data_object, env->GetFieldID(native_request_data_class, "canceled", "Z"));

            if (is_canceled) {
                LOGD("canceled request for %s", uri);
                return nullptr;
            }

            auto new_buffer = (jbyteArray)env->GetObjectField(native_request_data_object, env->GetFieldID(native_request_data_class, "buffer", "[B"));
            auto new_buffer_length = env->GetArrayLength(new_buffer);
            auto new_buffer_data = env->GetByteArrayElements(new_buffer, nullptr);

            //we need to allocate a new ref_counter struct and copy the old ref_counter and the new_buffer to it
            const static auto ref_counter_struct_size = (uintptr_t)slice_buffer->data - (uintptr_t)slice_buffer->ref_counter;

            auto new_ref_counter = malloc(ref_counter_struct_size + new_buffer_length);
            //copy the old ref_counter and the native_request_data_object
            memcpy(new_ref_counter, slice_buffer->ref_counter, ref_counter_struct_size);
            memcpy((void *)((uintptr_t)new_ref_counter + ref_counter_struct_size), new_buffer_data, new_buffer_length);

            //free the old ref_counter
            free(slice_buffer->ref_counter);

            //update the slice_buffer
            slice_buffer->ref_counter = new_ref_counter;
            slice_buffer->length = new_buffer_length;
            slice_buffer->data = (uint8_t *)((uintptr_t)new_ref_counter + ref_counter_struct_size);
        }

        return unaryCall_hook_original(unk1, uri, buffer_ptr, unk4, unk5, unk6);
    }

    void init(JNIEnv *env) {
        auto unaryCall_func = util::find_signature(
                common::client_module.base, common::client_module.size,
                ARM64 ? "A8 03 1F F8 C2 00 00 94" : "0A 90 00 F0 3F F9",
                ARM64 ? -0x48 : -0x37
        );

        native_lib_on_unary_call_method = env->GetMethodID(env->GetObjectClass(common::native_lib_object), "onNativeUnaryCall", "(Ljava/lang/String;[B)L" BUILD_NAMESPACE "/NativeRequestData;");

        if (unaryCall_func != 0) {
            DobbyHook((void *)unaryCall_func, (void *)unaryCall_hook, (void **)&unaryCall_hook_original);
        } else {
            LOGE("Can't find unaryCall signature");
        }
    }
}