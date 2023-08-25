cmake_minimum_required(VERSION 3.22.1)

project("nativelib")

set(DOBBY_GENERATE_SHARED OFF)
add_subdirectory(external/dobby)

add_library(${CMAKE_PROJECT_NAME} SHARED
        src/library.cpp
    )

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        dobby_static
        )
