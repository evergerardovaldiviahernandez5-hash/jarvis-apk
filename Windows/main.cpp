#include <iostream>
#include <string>
#include "llama.h"

int main() {
    std::cout << "=== NOVA DEBUG (CONSOLA MSVC) ===" << std::endl;

    std::cout << "1. Escribe la ruta completa del modelo .gguf: ";
    std::string path;
    std::getline(std::cin, path);

    std::cout << "2. Cargando modelo en RAM (esto puede tardar 1 minuto)..." << std::endl;
    
    // Ya no llamamos a llama_backend_init(). Cargamos el modelo directo.
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    llama_model * model = llama_load_model_from_file(path.c_str(), model_params);

    if (!model) {
        std::cerr << "ERROR: El modelo devolvio NULL. Archivo corrupto o sin RAM." << std::endl;
        system("pause");
        return 1;
    }
    std::cout << "3. ¡Modelo cargado exitosamente en memoria!" << std::endl;

    const llama_vocab * vocab = llama_model_get_vocab(model);
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;
    ctx_params.n_threads = 1; // 1 solo hilo para evitar colapso de CPU
    ctx_params.n_threads_batch = 1;
    
    std::cout << "4. Creando contexto..." << std::endl;
    llama_context * ctx = llama_new_context_with_model(model, ctx_params);

    if (!ctx) {
        std::cerr << "ERROR: No se pudo crear el contexto (falta de RAM)." << std::endl;
        system("pause");
        return 1;
    }
    std::cout << "5. ¡Contexto creado! Todo listo. Presiona ENTER para salir." << std::endl;
    std::cin.get();

    llama_free(ctx);
    llama_free_model(model);
    return 0;
}
