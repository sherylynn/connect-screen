cmake_minimum_required(VERSION 3.22.1)
project(connect_screen)

add_library(
        server
        SHARED
        server.cpp)

target_link_libraries(
        server
        android
        log) 