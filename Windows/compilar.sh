#!/bin/bash
echo "=== INICIANDO COMPILACION AUTOMATICA ==="

if [ ! -d "../llama.cpp" ]; then
    echo "Descargando motor llama.cpp..."
    cd ..
    git clone https://github.com/ggerganov/llama.cpp.git
    cd Windows
fi

rm -rf build
mkdir -p build
cd build

echo "Configurando CMake (esto puede tardar)..."
# Desactivamos AVX2 porque tu procesador es antiguo
cmake .. -G "MinGW Makefiles" -DCMAKE_BUILD_TYPE=Release -DLLAMA_AVX2=OFF -DLLAMA_AVX=ON -DLLAMA_F16C=OFF

echo "Compilando (esto puede tardar varios minutos)..."
mingw32-make -j2

echo "=== COMPILACION TERMINADA ==="
echo "El archivo jarvis_win.exe esta en la carpeta build/"
