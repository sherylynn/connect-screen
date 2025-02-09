cmake_minimum_required(VERSION 3.0.0)

project(libwordexp C)

add_definitions(-D__USE_GNU)

add_library(wordexp_a STATIC wordexp.c)
add_library(wordexp   SHARED wordexp.c)

include(GNUInstallDirs)

install(TARGETS wordexp LIBRARY                    DESTINATION ${CMAKE_INSTALL_LIBDIR})
install(FILES ${PROJECT_BINARY_DIR}/libwordexp_a.a DESTINATION ${CMAKE_INSTALL_LIBDIR} RENAME libwordexp.a)
install(FILES ${PROJECT_SOURCE_DIR}/wordexp.h      DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})