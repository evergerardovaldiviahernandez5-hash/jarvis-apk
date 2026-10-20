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

set(GLFW_BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../llama.cpp")
set(IMGUI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../imgui")
set(GLFW_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../glfw")

add_subdirectory(${LLAMA_DIR} llama.cpp_build)
add_subdirectory(${GLFW_DIR} glfw_build)

add_executable(Nova
    ${CMAKE_CURRENT_SOURCE_DIR}/main.cpp
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp
    ${IMGUI_DIR}/backends/imgui_impl_glfw.cpp
    ${IMGUI_DIR}/backends/imgui_impl_dx11.cpp
)

target_include_directories(Nova PRIVATE ${IMGUI_DIR} ${IMGUI_DIR}/backends)
# Añadimos d3dcompiler a la lista de librerías
target_link_libraries(Nova PRIVATE llama ggml glfw d3d11 dxgi d3dcompiler comdlg32 user32 shell32 ole32)
