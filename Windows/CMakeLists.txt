cmake_minimum_required(VERSION 3.15)
project(JarvisWindows LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Incluir llama.cpp (debes tener la carpeta llama.cpp al lado de Windows)
add_subdirectory(../llama.cpp llama.cpp_build)

add_executable(jarvis_win main.cpp)
target_link_libraries(jarvis_win PRIVATE llama httplib nlohmann_json::nlohmann_json)
