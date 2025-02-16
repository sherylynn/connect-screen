cmake_minimum_required(VERSION 3.22.1)
project(connect_screen)

add_subdirectory(wordexp)
add_subdirectory(enet)
add_subdirectory(dependencies/openssl-cmake)
include(dependencies/Boost_Sunshine.cmake)

add_library(
        server
        SHARED
        server.cpp
        logging.cpp
        network.cpp
        config.cpp
        stream.cpp
        globals.cpp
        crypto.cpp
        platform/common.cpp
        stat_trackers.cpp
        dependencies/nanors/rs.c
)

# 添加 ENet 的头文件路径
target_include_directories(server PRIVATE
        enet/include
        dependencies/nanors/deps/obl
        dependencies/nanors
        dependencies/ffmpeg
        dependencies
        ${Boost_INCLUDE_DIRS})

target_link_libraries(
        server
        android
        log
        enet
        ssl
        crypto
        ${Boost_LIBRARIES})