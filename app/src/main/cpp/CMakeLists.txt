cmake_minimum_required(VERSION 4.1.2)
project("moria")

add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)
