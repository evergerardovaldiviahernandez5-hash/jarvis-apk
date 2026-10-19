cmake_minimum_required(VERSION 3.15)
project(NovaWindows LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Enlazar llama.cpp estáticamente
add_subdirectory(../llama.cpp llama.cpp_build)

# Buscar GLFW (viene instalado por MSYS2)
find_package(glfw3 REQUIRED)

# Incluir ImGui
set(IMGUI_DIR "../imgui")
add_executable(Nova WIN32 main.cpp
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp
    ${IMGUI_DIR}/backends/imgui_impl_glfw.cpp
    ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp
)

target_include_directories(Nova PRIVATE ${IMGUI_DIR} ${IMGUI_DIR}/backends)
target_link_libraries(Nova PRIVATE llama ggml glfw opengl32 gdi32 comdlg32)
