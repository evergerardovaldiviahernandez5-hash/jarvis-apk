cmake_minimum_required(VERSION 3.15)
project(NovaWindows LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../llama.cpp")
set(IMGUI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../imgui")

add_subdirectory(${LLAMA_DIR} llama.cpp_build)
find_package(glfw3 REQUIRED)

add_executable(Nova
    ${CMAKE_CURRENT_SOURCE_DIR}/main.cpp
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp
    ${IMGUI_DIR}/backends/imgui_impl_glfw.cpp
    ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp
)

target_include_directories(Nova PRIVATE ${IMGUI_DIR} ${IMGUI_DIR}/backends)
target_link_libraries(Nova PRIVATE llama ggml glfw opengl32 gdi32 comdlg32 user32 shell32 ole32)
