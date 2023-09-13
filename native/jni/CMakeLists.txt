cmake_minimum_required(VERSION 3.22.1)

find_program(CCACHE_FOUND ccache)
if(CCACHE_FOUND)
    set_property(GLOBAL PROPERTY RULE_LAUNCH_COMPILE ccache)
    set_property(GLOBAL PROPERTY RULE_LAUNCH_LINK ccache)
endif(CCACHE_FOUND)

project("nativelib")

set(DOBBY_GENERATE_SHARED OFF)
add_subdirectory(external/dobby)

add_library(${CMAKE_PROJECT_NAME} SHARED
        src/library.cpp
    )

add_compile_definitions(BUILD_NAMESPACE="${BUILD_NAMESPACE}")
target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        dobby_static
        )

set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES OUTPUT_NAME ${OBFUSCATED_NAME})