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
#include "imgui_impl_dx11.h"
#include <GLFW/glfw3.h>
#define GLFW_EXPOSE_NATIVE_WIN32
#include <GLFW/glfw3native.h>
#include <windows.h>
#include <commdlg.h>
#include <d3d11.h>

llama_model * model = nullptr;
llama_context * ctx = nullptr;
const llama_vocab * vocab = nullptr;
std::string current_model_path = "Ninguno";

char input_buffer[1024] = "";
std::string chat_history = "Nova AI: ¡Hola! Soy Nova. Selecciona un modelo .gguf arriba para comenzar.\n\n";
std::atomic<bool> is_generating(false);
std::atomic<bool> is_loading(false);
std::mutex chat_mutex;

ID3D11Device*            g_pd3dDevice = nullptr;
ID3D11DeviceContext*     g_pd3dDeviceContext = nullptr;
IDXGISwapChain*          g_pSwapChain = nullptr;
ID3D11RenderTargetView*  g_mainRenderTargetView = nullptr;

void CreateRenderTarget() {
    ID3D11Texture2D* pBackBuffer = nullptr;
    g_pSwapChain->GetBuffer(0, IID_PPV_ARGS(&pBackBuffer));
    if (pBackBuffer) {
        g_pd3dDevice->CreateRenderTargetView(pBackBuffer, nullptr, &g_mainRenderTargetView);
        pBackBuffer->Release();
    }
}

bool CreateDeviceD3D(HWND hWnd) {
    DXGI_SWAP_CHAIN_DESC sd;
    ZeroMemory(&sd, sizeof(sd));
    sd.BufferCount = 2;
    sd.BufferDesc.Width = 0;
    sd.BufferDesc.Height = 0;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferDesc.RefreshRate.Numerator = 60;
    sd.BufferDesc.RefreshRate.Denominator = 1;
    sd.Flags = DXGI_SWAP_CHAIN_FLAG_ALLOW_MODE_SWITCH;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = hWnd;
    sd.SampleDesc.Count = 1;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;
    UINT createDeviceFlags = 0;
    D3D_FEATURE_LEVEL featureLevel;
    const D3D_FEATURE_LEVEL featureLevelArray[2] = { D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_0, };
    if (D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, createDeviceFlags, featureLevelArray, 2, D3D11_SDK_VERSION, &sd, &g_pSwapChain, &g_pd3dDevice, &featureLevel, &g_pd3dDeviceContext) != S_OK)
        return false;
    CreateRenderTarget();
    return true;
}

void CleanupDeviceD3D() {
    if (g_mainRenderTargetView) { g_mainRenderTargetView->Release(); g_mainRenderTargetView = nullptr; }
    if (g_pSwapChain) { g_pSwapChain->Release(); g_pSwapChain = nullptr; }
    if (g_pd3dDeviceContext) { g_pd3dDeviceContext->Release(); g_pd3dDeviceContext = nullptr; }
    if (g_pd3dDevice) { g_pd3dDevice->Release(); g_pd3dDevice = nullptr; }
}

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
    is_loading = true;
    try {
        if (model) { llama_free_model(model); model = nullptr; }
        if (ctx) { llama_free(ctx); ctx = nullptr; }

        std::lock_guard<std::mutex> lock(chat_mutex);
        chat_history += "[Sistema]: Cargando modelo... (esto puede tardar unos segundos)\n";
        
        // INICIALIZAR EL BACKEND AQUI ES SEGURO
        llama_backend_init();

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        model = llama_load_model_from_file(path.c_str(), model_params);
        
        if (!model) {
            chat_history += "[Error]: No se pudo cargar. Posiblemente no hay suficiente RAM.\n\n";
            is_loading = false;
            return;
        }

        vocab = llama_model_get_vocab(model);
        llama_context_params ctx_params = llama_context_default_params();
        
        ctx_params.n_ctx = 512; // Bajo consumo de RAM
        ctx_params.n_threads = 2;
        ctx_params.n_threads_batch = 2;
        
        ctx = llama_new_context_with_model(model, ctx_params);
        
        if (!ctx) {
            chat_history += "[Error]: No hay RAM para crear el contexto.\n\n";
            llama_free_model(model); model = nullptr;
            is_loading = false;
            return;
        }

        current_model_path = path;
        chat_history += "[Sistema]: ¡Modelo cargado con éxito! Ya puedes chatear.\n\n";
    } catch (...) {
        std::lock_guard<std::mutex> lock(chat_mutex);
        chat_history += "\n[Error Crítico]: Fallo en la carga.\n\n";
    }
    is_loading = false;
}

void GenerateResponse(std::string prompt) {
    if (!model || !ctx) return;
    is_generating = true;
    try {
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
        llama_batch_free(batch);
    } catch (...) {
        std::lock_guard<std::mutex> lock(chat_mutex);
        chat_history += "\n[Error Crítico al generar]\n\n";
    }
    is_generating = false;
}

int main() {
    try {
        // Ya no pedimos ENTER, la ventana abre directo
        if (!glfwInit()) return 1;

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        GLFWwindow* window = glfwCreateWindow(900, 700, "Nova AI", NULL, NULL);
        if (!window) return 1;

        HWND hwnd = glfwGetWin32Window(window);
        if (!CreateDeviceD3D(hwnd)) return 1;

        IMGUI_CHECKVERSION();
        ImGui::CreateContext();
        ImGuiIO& io = ImGui::GetIO(); (void)io;
        
        // === DISEÑO DE LA INTERFAZ (ESTILO MODERNO) ===
        ImGui::StyleColorsDark();
        ImGuiStyle& style = ImGui::GetStyle();
        style.WindowRounding = 5.0f;
        style.FrameRounding = 5.0f;
        style.ItemSpacing = ImVec2(10, 10);
        style.Colors[ImGuiCol_WindowBg] = ImVec4(0.08f, 0.08f, 0.10f, 1.00f);
        style.Colors[ImGuiCol_Button] = ImVec4(0.20f, 0.40f, 0.70f, 1.00f);
        style.Colors[ImGuiCol_ButtonHovered] = ImVec4(0.25f, 0.50f, 0.85f, 1.00f);
        style.Colors[ImGuiCol_FrameBg] = ImVec4(0.15f, 0.15f, 0.18f, 1.00f);
        io.FontGlobalScale = 1.1f; // Letra un poco más grande

        ImGui_ImplGlfw_InitForOther(window, true);
        ImGui_ImplDX11_Init(g_pd3dDevice, g_pd3dDeviceContext);

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            ImGui_ImplDX11_NewFrame();
            ImGui_ImplGlfw_NewFrame();
            ImGui::NewFrame();

            // Crear ventana que ocupa toda la pantalla
            ImGui::SetNextWindowPos(ImVec2(0, 0));
            ImGui::SetNextWindowSize(io.DisplaySize);
            ImGui::Begin("NovaMain", nullptr, ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoScrollbar);

            // --- BARRA SUPERIOR ---
            ImGui::BeginChild("TopBar", ImVec2(0, 50), false);
            ImGui::TextColored(ImVec4(0.4f, 0.8f, 1.0f, 1.0f), "NOVA AI");
            ImGui::SameLine();
            if (ImGui::Button("Seleccionar Modelo .gguf", ImVec2(200, 0))) {
                std::string path = OpenFileDialog();
                if (!path.empty()) {
                    std::thread(LoadModel, path).detach();
                }
            }
            ImGui::SameLine();
            if (is_loading) {
                ImGui::TextColored(ImVec4(1, 1, 0, 1), "Cargando modelo...");
            } else {
                ImGui::TextColored(ImVec4(0.5f, 0.5f, 0.5f, 1.0f), "Modelo: %s", current_model_path.c_str());
            }
            ImGui::EndChild();

            ImGui::Separator();

            // --- ÁREA DE CHAT ---
            ImGui::BeginChild("Chat", ImVec2(0, -60), true);
            {
                std::lock_guard<std::mutex> lock(chat_mutex);
                ImGui::TextWrapped("%s", chat_history.c_str());
                if (ImGui::GetScrollY() >= ImGui::GetScrollMaxY()) ImGui::SetScrollHereY(1.0f);
            }
            ImGui::EndChild();

            // --- ÁREA DE ENTRADA ---
            ImGui::PushItemWidth(-100); // Deja espacio para el botón
            bool enter_pressed = ImGui::InputText("##input", input_buffer, sizeof(input_buffer), ImGuiInputTextFlags_EnterReturnsTrue);
            ImGui::PopItemWidth();
            
            ImGui::SameLine();
            
            if (ImGui::Button("Enviar", ImVec2(90, 0)) || enter_pressed) {
                if (strlen(input_buffer) > 0 && !is_generating && model) {
                    std::string prompt(input_buffer);
                    {
                        std::lock_guard<std::mutex> lock(chat_mutex);
                        chat_history += "Tú: " + prompt + "\nNova: ";
                    }
                    std::thread(GenerateResponse, prompt).detach();
                    input_buffer[0] = '\0';
                }
            }
            if (is_generating) {
                ImGui::SameLine();
                ImGui::TextColored(ImVec4(1, 1, 0, 1), "Escribiendo...");
            }

            ImGui::End();

            ImGui::Render();
            
            const float clear_color_with_alpha[4] = { 0.08f, 0.08f, 0.10f, 1.0f };
            g_pd3dDeviceContext->OMSetRenderTargets(1, &g_mainRenderTargetView, nullptr);
            g_pd3dDeviceContext->ClearRenderTargetView(g_mainRenderTargetView, clear_color_with_alpha);
            ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
            g_pSwapChain->Present(1, 0);
        }

        // Limpieza
        ImGui_ImplDX11_Shutdown();
        ImGui_ImplGlfw_Shutdown();
        ImGui::DestroyContext();
        CleanupDeviceD3D();
        glfwDestroyWindow(window);
        glfwTerminate();

        if (model) llama_free_model(model);
        if (ctx) llama_free(ctx);
        llama_backend_free();
    } catch (...) {
        return 1;
    }
    return 0;
}
