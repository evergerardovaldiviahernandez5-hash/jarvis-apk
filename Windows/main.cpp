#include <iostream>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include "llama.h"
#include "imgui.h"
#include "imgui_impl_glfw.h"
#include "imgui_impl_opengl3.h"
#include <GLFW/glfw3.h>
#include <windows.h>
#include <commdlg.h>

// Variables globales
llama_model * model = nullptr;
llama_context * ctx = nullptr;
const llama_vocab * vocab = nullptr;
std::string current_model_path = "Ninguno";

char input_buffer[1024] = "";
std::string chat_history = "Nova: ¡Hola! Soy Nova. Por favor, carga un modelo (.gguf) para empezar a chatear.\n\n";
std::atomic<bool> is_generating(false);
std::mutex chat_mutex;

// Función para abrir el explorador de archivos de Windows
std::string OpenFileDialog() {
    OPENFILENAMEA ofn;
    char szFile[260] = { 0 };
    ZeroMemory(&ofn, sizeof(ofn));
    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = NULL;
    ofn.lpstrFile = szFile;
    ofn.nMaxFile = sizeof(szFile);
    ofn.lpstrFilter = "Modelos GGUF\0*.gguf\0Todos los archivos\0*.*\0";
    ofn.nFilterIndex = 1;
    ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST;

    if (GetOpenFileNameA(&ofn) == TRUE) {
        return std::string(ofn.lpstrFile);
    }
    return "";
}

// Función para cargar el modelo
void LoadModel(const std::string& path) {
    if (model) {
        llama_free_model(model);
        model = nullptr;
    }
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Sin GPU
    model = llama_load_model_from_file(path.c_str(), model_params);

    if (model) {
        vocab = llama_model_get_vocab(model);
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = 2048;
        ctx_params.n_threads = 2;
        ctx_params.n_threads_batch = 2;
        ctx = llama_new_context_with_model(model, ctx_params);
        
        std::lock_guard<std::mutex> lock(chat_mutex);
        current_model_path = path;
        chat_history += "\n[Sistema]: Modelo cargado correctamente.\n\n";
    } else {
        std::lock_guard<std::mutex> lock(chat_mutex);
        chat_history += "\n[Error]: No se pudo cargar el modelo.\n\n";
    }
}

// Función que corre la IA en un hilo separado para no congelar la ventana
void GenerateResponse(std::string prompt) {
    if (!model || !ctx) return;
    is_generating = true;

    std::string full_prompt = "Eres Nova, un asistente de IA amigable y util. Responde en español.\nUsuario: " + prompt + "\nNova: ";
    
    // Tokenizar
    std::vector<llama_token> tokens_list(full_prompt.length() + 1);
    int n_tokens = llama_tokenize(vocab, full_prompt.c_str(), full_prompt.length(), tokens_list.data(), tokens_list.size(), true, false);

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

    if (llama_decode(ctx, batch) == 0) {
        int current_pos = n_tokens;
        std::string response = "Nova: ";

        for (int i = 0; i < 512; i++) {
            auto * logits = llama_get_logits_ith(ctx, batch.n_tokens - 1);
            llama_token id = 0;
            float max_logit = -1e9;
            for (int j = 0; j < llama_vocab_n_tokens(vocab); j++) {
                if (logits[j] > max_logit) { max_logit = logits[j]; id = j; }
            }
            if (id == llama_vocab_eos(vocab)) break;
            
            char buf[128];
            int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
            if (n > 0) {
                response += std::string(buf, n);
                // Actualizar la interfaz en tiempo real
                std::lock_guard<std::mutex> lock(chat_mutex);
                chat_history += std::string(buf, n);
            }
            
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
        std::lock_guard<std::mutex> lock(chat_mutex);
        chat_history += "\n\n";
    }
    llama_batch_free(batch);
    is_generating = false;
}

int main() {
    llama_backend_init();

    // Configurar ventana GLFW
    glfwInit();
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0);
    GLFWwindow* window = glfwCreateWindow(800, 600, "Nova AI", NULL, NULL);
    glfwMakeContextCurrent(window);
    glfwSwapInterval(1);

    // Configurar ImGui
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO(); (void)io;
    ImGui::StyleColorsDark();
    ImGui_ImplGlfw_InitForOpenGL(window, true);
    ImGui_ImplOpenGL3_Init("#version 130");

    // Bucle principal de la GUI
    while (!glfwWindowShouldClose(window)) {
        glfwPollEvents();
        ImGui_ImplOpenGL3_NewFrame();
        ImGui_ImplGlfw_NewFrame();
        ImGui::NewFrame();

        // Crear la ventana de Nova
        ImGui::SetNextWindowPos(ImVec2(0, 0));
        ImGui::SetNextWindowSize(io.DisplaySize);
        ImGui::Begin("Nova AI", nullptr, ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove);

        if (ImGui::Button("Cargar Modelo (.gguf)")) {
            std::string path = OpenFileDialog();
            if (!path.empty()) {
                LoadModel(path);
            }
        }
        ImGui::SameLine();
        ImGui::Text("Modelo actual: %s", current_model_path.c_str());

        ImGui::Separator();

        // Área de chat
        ImGui::BeginChild("Chat", ImVec2(0, -50), true);
        {
            std::lock_guard<std::mutex> lock(chat_mutex);
            ImGui::TextWrapped("%s", chat_history.c_str());
            // Auto-scroll al final
            if (ImGui::GetScrollY() >= ImGui::GetScrollMaxY())
                ImGui::SetScrollHereY(1.0f);
        }
        ImGui::EndChild();

        // Área de entrada de texto
        ImGui::PushItemWidth(-80);
        if (ImGui::InputText("##input", input_buffer, sizeof(input_buffer), ImGuiInputTextFlags_EnterReturnsTrue) && !is_generating) {
            if (strlen(input_buffer) > 0) {
                std::string prompt(input_buffer);
                {
                    std::lock_guard<std::mutex> lock(chat_mutex);
                    chat_history += "Tú: " + prompt + "\n";
                }
                std::thread(GenerateResponse, prompt).detach();
                input_buffer[0] = '\0';
            }
        }
        ImGui::PopItemWidth();
        ImGui::SameLine();
        
        if (is_generating) {
            ImGui::TextColored(ImVec4(1, 1, 0, 1), "Pensando...");
        } else {
            if (ImGui::Button("Enviar")) {
                if (strlen(input_buffer) > 0) {
                    std::string prompt(input_buffer);
                    {
                        std::lock_guard<std::mutex> lock(chat_mutex);
                        chat_history += "Tú: " + prompt + "\n";
                    }
                    std::thread(GenerateResponse, prompt).detach();
                    input_buffer[0] = '\0';
                }
            }
        }

        ImGui::End();

        // Renderizar
        ImGui::Render();
        int display_w, display_h;
        glfwGetFramebufferSize(window, &display_w, &display_h);
        glViewport(0, 0, display_w, display_h);
        glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
        glfwSwapBuffers(window);
    }

    // Limpieza
    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplGlfw_Shutdown();
    ImGui::DestroyContext();
    glfwDestroyWindow(window);
    glfwTerminate();

    if (model) llama_free_model(model);
    if (ctx) llama_free(ctx);
    llama_backend_free();

    return 0;
}
