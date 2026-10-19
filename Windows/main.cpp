#include <iostream>
#include <string>
#include <vector>
#include "llama.h"

int main() {
    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Sin GPU (optimizado para tu Celeron)

    std::cout << "Cargando modelo en RAM... (esto puede tardar)" << std::endl;
    llama_model * model = llama_load_model_from_file("model.gguf", model_params);

    if (model == NULL) {
        std::cerr << "Error: No se encontro 'model.gguf' en esta carpeta." << std::endl;
        return 1;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 2; // 2 nucleos para el Celeron
    ctx_params.n_threads_batch = 2;

    llama_context * ctx = llama_new_context_with_model(model, ctx_params);
    std::cout << "¡Modelo cargado! Escribe tu mensaje (o 'salir' para terminar)." << std::endl;
    std::string prompt;

    while (true) {
        std::cout << "\nTu: ";
        std::getline(std::cin, prompt);
        if (prompt == "salir" || prompt == "exit") break;

        std::vector<llama_token> tokens_list(prompt.length() + 1);
        int n_tokens = llama_tokenize(model, prompt.c_str(), prompt.length(), tokens_list.data(), tokens_list.size(), true, false);

        llama_batch batch = llama_batch_get_one(tokens_list.data(), n_tokens, 0, 0);
        if (llama_decode(ctx, batch)) { std::cerr << "Error al procesar." << std::endl; continue; }

        std::cout << "Jarvis: ";
        for (int i = 0; i < 256; i++) {
            auto * logits = llama_get_logits_ith(ctx, batch.n_tokens - 1);
            llama_token id = 0;
            float max_logit = -1e9;
            for (int j = 0; j < llama_n_vocab(model); j++) {
                if (logits[j] > max_logit) { max_logit = logits[j]; id = j; }
            }
            if (id == llama_token_eos(model)) break;
            char buf[128];
            int n = llama_token_to_piece(model, id, buf, sizeof(buf), 0, true);
            if (n > 0) std::cout << std::string(buf, n) << std::flush;
            
            llama_batch next_batch = llama_batch_get_one(&id, 1, batch.n_tokens, 0);
            if (llama_decode(ctx, next_batch)) break;
            batch = next_batch;
        }
        std::cout << std::endl;
    }
    llama_free(ctx);
    llama_free_model(model);
    llama_backend_free();
    return 0;
}
