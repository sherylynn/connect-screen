cmake_minimum_required(VERSION 3.22.1)
project(connect_screen)

add_subdirectory(enet)

add_library(
        server
        SHARED
        server.cpp)

# 添加 ENet 的头文件路径
target_include_directories(server PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/enet/include)

target_link_libraries(
        server
        android
        log
        enet) 