cmake_minimum_required(VERSION 3.22.1)
project(connect_screen)

add_subdirectory(wordexp)
add_subdirectory(enet)
include(${CMAKE_CURRENT_SOURCE_DIR}/dependencies/Boost_Sunshine.cmake)

add_library(
        server
        SHARED
        server.cpp
        logging.cpp
        network.cpp
        config.cpp
        stream.cpp)

# 添加 ENet 的头文件路径
target_include_directories(server PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/enet/include
        ${Boost_INCLUDE_DIRS})

target_link_libraries(
        server
        android
        log
        enet
        ${Boost_LIBRARIES})