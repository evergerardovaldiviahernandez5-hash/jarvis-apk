cmake_minimum_required(VERSION 3.15)
project(JarvisWindows LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(../llama.cpp llama.cpp_build)
add_executable(jarvis_win main.cpp)
target_link_libraries(jarvis_win PRIVATE llama ggml)
