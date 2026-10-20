cmake_minimum_required(VERSION 3.15)
project(NovaWindows LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_AVX OFF CACHE BOOL "" FORCE)
set(GGML_AVX2 OFF CACHE BOOL "" FORCE)
set(GGML_F16C OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../llama.cpp")
add_subdirectory(${LLAMA_DIR} llama.cpp_build)

# Compilar solo la consola
add_executable(Nova main.cpp)
target_link_libraries(Nova PRIVATE llama ggml)
