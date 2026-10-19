#include <iostream>
#include <string>
#include <vector>
#include "llama.h"

int main() {
    // 1. Inicializar el backend
    llama_backend_init();

    // 2. Configurar parámetros del modelo
    llama_model_params model_params = llama_model_default_params();
    // Desactivar el offloading a GPU (tu laptop no tiene, ahorra RAM)
    model_params.n_gpu_layers = 0; 

    std::cout << "Cargando modelo en RAM... (esto puede tardar un poco)" << std::endl;
    llama_model * model = llama_load_model_from_file("model.gguf", model_params);

    if (model == NULL) {
        std::cerr << "Error: No se pudo cargar 'model.gguf'. Asegúrate de que este en la misma carpeta." << std::endl;
        return 1;
    }

    // 3. Configurar el contexto (2 hilos para tu Celeron B830, 2048 de contexto)
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 2; 
    ctx_params.n_threads_batch = 2;

    llama_context * ctx = llama_new_context_with_model(model, ctx_params);
    if (ctx == NULL) {
        std::cerr << "Error: No se pudo crear el contexto de IA." << std::endl;
        llama_free_model(model);
        return 1;
    }

    std::cout << "¡Modelo cargado! Escribe tu mensaje (o 'salir' para terminar)." << std::endl;
    std::string prompt;

    // Bucle de chat
    while (true) {
        std::cout << "\nTu: ";
        std::getline(std::cin, prompt);

        if (prompt == "salir" || prompt == "exit") break;

        // Tokenizar el prompt
        std::vector<llama_token> tokens_list(prompt.length() + 1);
        int n_tokens = llama_tokenize(model, prompt.c_str(), prompt.length(), tokens_list.data(), tokens_list.size(), true, false);

        // Evaluar el prompt
        llama_batch batch = llama_batch_get_one(tokens_list.data(), n_tokens, 0, 0);
        if (llama_decode(ctx, batch)) {
            std::cerr << "Error al procesar el texto." << std::endl;
            continue;
        }

        // Generar respuesta (simplificado)
        std::cout << "Jarvis: ";
        int n_predict = 256; // Máximo de palabras a generar
        for (int i = 0; i < n_predict; i++) {
            // Obtener el token más probable
            auto * logits = llama_get_logits_ith(ctx, batch.n_tokens - 1);
            llama_token id = 0;
            float max_logit = -1e9;
            for (int j = 0; j < llama_n_vocab(model); j++) {
                if (logits[j] > max_logit) {
                    max_logit = logits[j];
                    id = j;
                }
            }

            // Si es el final del texto, detenerse
            if (id == llama_token_eos(model)) break;

            // Convertir token a texto e imprimir
            char buf[128];
            int n = llama_token_to_piece(model, id, buf, sizeof(buf), 0, true);
            if (n > 0) {
                std::cout << std::string(buf, n) << std::flush;
            }

            // Preparar el siguiente token
            llama_batch next_batch = llama_batch_get_one(&id, 1, batch.n_tokens, 0);
            if (llama_decode(ctx, next_batch)) break;
            batch = next_batch;
        }
        std::cout << std::endl;
    }

    // Liberar memoria
    llama_free(ctx);
    llama_free_model(model);
    llama_backend_free();
    return 0;
}
