#include <iostream>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <stdexcept>
#include "llama.h"
#include "imgui.h"
#include "imgui_impl_glfw.h"
#include "imgui_impl_opengl3.h"
#include <GLFW/glfw3.h>
#include <windows.h>
#include <commdlg.h>

llama_model * model = nullptr;
llama_context * ctx = nullptr;
const llama_vocab * vocab = nullptr;
std::string current_model_path = "Ninguno";

char input_buffer[1024] = "";
std::string chat_history = "Nova: ¡Hola! Soy Nova. Por favor, carga un modelo (.gguf) para empezar a chatear.\n\n";
std::atomic<bool> is_generating(false);
std::mutex chat_mutex;

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
    if (GetOpenFileNameA(&ofn) == TRUE) return std::string(ofn.lpstrFile);
    return "";
}

void LoadModel(const std::string& path) {
    if (model) { llama_free_model(model); model = nullptr; }
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model = llama_load_model_from_file(path.c_str(), model_params);
    if (model) {
        vocab = llama_model_get_vocab(model);
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = 2048; ctx_params.n_threads = 2; ctx_params.n_threads_batch = 2;
        ctx = llama_new_context_with_model(model, ctx_params);
        std::lock_guard<std::mutex> lock(chat_mutex);
        current_model_path = path;
        chat_history += "\n[Sistema]: Modelo cargado correctamente.\n\n";
    } else {
        std::lock_guard<std::mutex> lock(chat_mutex);
        chat_history += "\n[Error]: No se pudo cargar el modelo.\n\n";
    }
}

void GenerateResponse(std::string prompt) {
    if (!model || !ctx) return;
    is_generating = true;
    std::string full_prompt = "Eres Nova, un asistente de IA amigable. Responde en español.\nUsuario: " + prompt + "\nNova: ";
    std::vector<llama_token> tokens_list(full_prompt.length() + 1);
    int n_tokens = llama_tokenize(vocab, full_prompt.c_str(), full_prompt.length(), tokens_list.data(), tokens_list.size(), true, false);
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens_list[i]; batch.pos[i] = i; batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0; batch.logits[i] = false;
    }
    batch.n_tokens = n_tokens; batch.logits[batch.n_tokens - 1] = true;
    if (llama_decode(ctx, batch) == 0) {
        int current_pos = n_tokens;
        for (int i = 0; i < 512; i++) {
            auto * logits = llama_get_logits_ith(ctx, batch.n_tokens - 1);
            llama_token id = 0; float max_logit = -1e9;
            for (int j = 0; j < llama_vocab_n_tokens(vocab); j++) {
                if (logits[j] > max_logit) { max_logit = logits[j]; id = j; }
            }
            if (id == llama_vocab_eos(vocab)) break;
            char buf[128];
            int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
            if (n > 0) { std::lock_guard<std::mutex> lock(chat_mutex); chat_history += std::string(buf, n); }
            llama_batch next_batch = llama_batch_init(1, 0, 1);
            next_batch.token[0] = id; next_batch.pos[0] = current_pos;
            next_batch.n_seq_id[0] = 1; next_batch.seq_id[0][0] = 0;
            next_batch.logits[0] = true; next_batch.n_tokens = 1;
            if (llama_decode(ctx, next_batch)) { llama_batch_free(next_batch); break; }
            llama_batch_free(batch); batch = next_batch; current_pos++;
        }
        std::lock_guard<std::mutex> lock(chat_mutex); chat_history += "\n\n";
    }
    llama_batch_free(batch); is_generating = false;
}

int main() {
    std::cout << "=== NOVA SE ESTA INICIANDO ===" << std::endl;
    std::cout << "Presiona ENTER para iniciar..." << std::endl;
    std::cin.get();

    try {
        std::cout << "[1/5] Iniciando backend de llama..." << std::endl;
        // Protegemos la inicialización por si acaso
        try {
            llama_backend_init();
        } catch (const std::exception& e) {
            std::cerr << "Error en llama_backend_init: " << e.what() << std::endl;
            system("pause");
        }
        std::cout << "[1/5] Backend OK." << std::endl;

        std::cout << "[2/5] Iniciando GLFW..." << std::endl;
        if (!glfwInit()) {
            std::cerr << "ERROR: Fallo al iniciar GLFW." << std::endl;
            system("pause"); return 1;
        }

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);

        std::cout << "[3/5] Creando ventana..." << std::endl;
        GLFWwindow* window = glfwCreateWindow(800, 600, "Nova AI", NULL, NULL);
        if (!window) {
            std::cerr << "ERROR: Fallo al crear la ventana." << std::endl;
            system("pause"); glfwTerminate(); return 1;
        }

        std::cout << "[4/5] Configurando ImGui..." << std::endl;
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        IMGUI_CHECKVERSION();
        ImGui::CreateContext();
        ImGuiIO& io = ImGui::GetIO(); (void)io;
        ImGui::StyleColorsDark();
        ImGui_ImplGlfw_InitForOpenGL(window, true);
        ImGui_ImplOpenGL3_Init("#version 120");

        std::cout << "[5/5] Entrando al bucle principal..." << std::endl;
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            ImGui_ImplOpenGL3_NewFrame();
            ImGui_ImplGlfw_NewFrame();
            ImGui::NewFrame();

            ImGui::SetNextWindowPos(ImVec2(0, 0));
            ImGui::SetNextWindowSize(io.DisplaySize);
            ImGui::Begin("Nova AI", nullptr, ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove);

            if (ImGui::Button("Cargar Modelo (.gguf)")) {
                std::string path = OpenFileDialog();
                if (!path.empty()) LoadModel(path);
            }
            ImGui::SameLine();
            ImGui::Text("Modelo actual: %s", current_model_path.c_str());
            ImGui::Separator();

            ImGui::BeginChild("Chat", ImVec2(0, -50), true);
            {
                std::lock_guard<std::mutex> lock(chat_mutex);
                ImGui::TextWrapped("%s", chat_history.c_str());
                if (ImGui::GetScrollY() >= ImGui::GetScrollMaxY()) ImGui::SetScrollHereY(1.0f);
            }
            ImGui::EndChild();

            ImGui::PushItemWidth(-80);
            if (ImGui::InputText("##input", input_buffer, sizeof(input_buffer), ImGuiInputTextFlags_EnterReturnsTrue) && !is_generating) {
                if (strlen(input_buffer) > 0) {
                    std::string prompt(input_buffer);
                    { std::lock_guard<std::mutex> lock(chat_mutex); chat_history += "Tú: " + prompt + "\n"; }
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
                        { std::lock_guard<std::mutex> lock(chat_mutex); chat_history += "Tú: " + prompt + "\n"; }
                        std::thread(GenerateResponse, prompt).detach();
                        input_buffer[0] = '\0';
                    }
                }
            }

            ImGui::End();
            ImGui::Render();
            int display_w, display_h;
            glfwGetFramebufferSize(window, &display_w, &display_h);
            glViewport(0, 0, display_w, display_h);
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
            glfwSwapBuffers(window);
        }

        ImGui_ImplOpenGL3_Shutdown();
        ImGui_ImplGlfw_Shutdown();
        ImGui::DestroyContext();
        glfwDestroyWindow(window);
        glfwTerminate();

        if (model) llama_free_model(model);
        if (ctx) llama_free(ctx);
        llama_backend_free();
    } catch (const std::exception& e) {
        std::cerr << "ERROR FATAL: " << e.what() << std::endl;
        system("pause");
    } catch (...) {
        std::cerr << "ERROR FATAL DESCONOCIDO" << std::endl;
        system("pause");
    }
    return 0;
}
