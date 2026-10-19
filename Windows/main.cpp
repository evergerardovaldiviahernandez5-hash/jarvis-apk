#include <iostream>
#include <string>
#include <vector>
#include "llama.h"

int main() {
    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    std::cout << "Cargando modelo en RAM... (esto puede tardar)" << std::endl;
    llama_model * model = llama_load_model_from_file("model.gguf", model_params);

    if (model == NULL) {
        std::cerr << "Error: No se encontro 'model.gguf' en esta carpeta." << std::endl;
        return 1;
    }

    // NUEVO: Obtener el vocabulario por separado (requerido por la API de Octubre 2024)
    const llama_vocab * vocab = llama_model_get_vocab(model);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 2;
    ctx_params.n_threads_batch = 2;

    llama_context * ctx = llama_new_context_with_model(model, ctx_params);
    std::cout << "¡Modelo cargado! Escribe tu mensaje (o 'salir' para terminar)." << std::endl;
    std::string prompt;

    while (true) {
        std::cout << "\nTu: ";
        std::getline(std::cin, prompt);
        if (prompt == "salir" || prompt == "exit") break;

        std::vector<llama_token> tokens_list(prompt.length() + 1);
        // Usar vocab en lugar de model
        int n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.length(), tokens_list.data(), tokens_list.size(), true, false);

        llama_batch batch = llama_batch_init(n_tokens, 0, 1);
        for (int i = 0; i < n_tokens; i++) {
            batch.token[i] = tokens_list[i];
            batch.pos[i] = i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = false;
        }
        batch.n_tokens = n_tokens;
        batch.logits[batch.n_tokens - 1] = true;

        if (llama_decode(ctx, batch)) { std::cerr << "Error al procesar." << std::endl; llama_batch_free(batch); continue; }

        std::cout << "Jarvis: ";
        int current_pos = n_tokens;

        for (int i = 0; i < 256; i++) {
            auto * logits = llama_get_logits_ith(ctx, batch.n_tokens - 1);
            llama_token id = 0;
            float max_logit = -1e9;
            // Usar vocab para obtener el total de palabras
            for (int j = 0; j < llama_vocab_n_tokens(vocab); j++) {
                if (logits[j] > max_logit) { max_logit = logits[j]; id = j; }
            }
            // Usar vocab para verificar el fin de texto
            if (id == llama_vocab_eos(vocab)) break;
            
            char buf[128];
            // Usar vocab para convertir a texto
            int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
            if (n > 0) std::cout << std::string(buf, n) << std::flush;
            
            llama_batch next_batch = llama_batch_init(1, 0, 1);
            next_batch.token[0] = id;
            next_batch.pos[0] = current_pos;
            next_batch.n_seq_id[0] = 1;
            next_batch.seq_id[0][0] = 0;
            next_batch.logits[0] = true;
            next_batch.n_tokens = 1;

            if (llama_decode(ctx, next_batch)) { llama_batch_free(next_batch); break; }
            
            llama_batch_free(batch);
            batch = next_batch;
            current_pos++;
        }
        std::cout << std::endl;
        llama_batch_free(batch);
    }
    llama_free(ctx);
    llama_free_model(model);
    llama_backend_free();
    return 0;
}
