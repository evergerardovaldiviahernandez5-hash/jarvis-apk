#include <iostream>
#include <fstream>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
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
char input_buffer[2048] = "";
std::atomic<bool> is_generating(false);
std::atomic<bool> is_loading(false);
std::mutex chat_mutex;

// Estructura para separar los mensajes
struct ChatMessage {
    bool is_user;
    std::string text;
    bool is_generating_this;
};
std::vector<ChatMessage> messages;

ID3D11Device*            g_pd3dDevice = nullptr;
ID3D11DeviceContext*     g_pd3dDeviceContext = nullptr;
IDXGISwapChain*          g_pSwapChain = nullptr;
ID3D11RenderTargetView*  g_mainRenderTargetView = nullptr;

void log_msg(const std::string& msg) {
    std::ofstream log("nova_log.txt", std::ios_base::app);
    log << msg << "\n";
    log.close();
}

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
    ofn.lpstrFile = szFile;
    ofn.nMaxFile = sizeof(szFile);
    ofn.lpstrFilter = "Modelos GGUF\0*.gguf\0";
    ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST;
    if (GetOpenFileNameA(&ofn) == TRUE) return std::string(ofn.lpstrFile);
    return "";
}

void LoadModel(const std::string& path) {
    is_loading = true;
    log_msg("Iniciando carga: " + path);

    if (model) { llama_free_model(model); model = nullptr; }
    if (ctx) { llama_free(ctx); ctx = nullptr; }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model = llama_load_model_from_file(path.c_str(), model_params);

    if (!model) {
        std::lock_guard<std::mutex> lock(chat_mutex);
        messages.push_back({false, "Sistema: [Error] No se pudo cargar el modelo.", false});
        is_loading = false;
        return;
    }

    vocab = llama_model_get_vocab(model);
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;
    ctx_params.n_threads = 1;
    ctx_params.n_threads_batch = 1;
    ctx = llama_new_context_with_model(model, ctx_params);

    if (!ctx) {
        std::lock_guard<std::mutex> lock(chat_mutex);
        messages.push_back({false, "Sistema: [Error] No hay RAM suficiente.", false});
        llama_free_model(model); model = nullptr;
        is_loading = false;
        return;
    }

    current_model_path = path;
    std::lock_guard<std::mutex> lock(chat_mutex);
    messages.push_back({false, "Sistema: ¡Modelo cargado con éxito! Nova está lista.", false});
    is_loading = false;
}

void GenerateResponse(std::string prompt) {
    if (!model || !ctx) return;
    is_generating = true;
    
    // Formato estricto para evitar que la IA escriba por el usuario
    std::string full_prompt = "<|im_start|>system\nEres Nova, un asistente de IA útil y amigable. Responde en español.<|im_end|>\n<|im_start|>user\n" + prompt + "<|im_end|>\n<|im_start|>assistant\n";
    
    std::vector<llama_token> tokens_list(full_prompt.length() + 1);
    int n_tokens = llama_tokenize(vocab, full_prompt.c_str(), full_prompt.length(), tokens_list.data(), tokens_list.size(), true, false);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens_list[i]; batch.pos[i] = i; batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0; batch.logits[i] = false;
    }
    batch.n_tokens = n_tokens; batch.logits[batch.n_tokens - 1] = true;

    {
        std::lock_guard<std::mutex> lock(chat_mutex);
        messages.push_back({false, "", true}); // Burbuja para la IA
    }

    if (llama_decode(ctx, batch) == 0) {
        int current_pos = n_tokens;
        for (int i = 0; i < 512; i++) {
            auto * logits = llama_get_logits_ith(ctx, batch.n_tokens - 1);
            llama_token id = 0; float max_logit = -1e9;
            for (int j = 0; j < llama_vocab_n_tokens(vocab); j++) {
                if (logits[j] > max_logit) { max_logit = logits[j]; id = j; }
            }
            
            // Si el modelo intenta cerrar el diálogo o escribir por el usuario, detenerse
            if (id == llama_vocab_eos(vocab)) break;
            
            char buf[128];
            int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
            if (n > 0) {
                std::string piece(buf, n);
                // EL ARREGLO DEL BUCLE: Si genera "user" o "Usuario" o "<|", cortamos la generación
                if (piece.find("User:") != std::string::npos || piece.find("Usuario:") != std::string::npos || piece.find("<|im_end|>") != std::string::npos) {
                    break; 
                }
                std::lock_guard<std::mutex> lock(chat_mutex);
                messages.back().text += piece;
            }
            
            llama_batch next_batch = llama_batch_init(1, 0, 1);
            next_batch.token[0] = id; next_batch.pos[0] = current_pos;
            next_batch.n_seq_id[0] = 1; next_batch.seq_id[0][0] = 0;
            next_batch.logits[0] = true; next_batch.n_tokens = 1;

            if (llama_decode(ctx, next_batch)) { llama_batch_free(next_batch); break; }
            llama_batch_free(batch); batch = next_batch; current_pos++;
        }
    }
    llama_batch_free(batch);
    std::lock_guard<std::mutex> lock(chat_mutex);
    messages.back().is_generating_this = false;
    is_generating = false;
}

int main() {
    log_msg("--- INICIANDO NOVA GUI (CLAUDE STYLE) ---");
    try {
        if (!glfwInit()) return 1;
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        
        GLFWwindow* window = glfwCreateWindow(900, 700, "Nova AI", NULL, NULL);
        if (!window) return 1;

        HWND hwnd = glfwGetWin32Window(window);
        if (!CreateDeviceD3D(hwnd)) return 1;

        IMGUI_CHECKVERSION();
        ImGui::CreateContext();
        ImGuiIO& io = ImGui::GetIO(); (void)io;
        
        // === ESTILO CLAUDE (LIMPIO, ELEGANTE) ===
        ImGui::StyleColorsDark();
        ImGuiStyle& style = ImGui::GetStyle();
        style.WindowRounding = 0.0f;
        style.FrameRounding = 6.0f;
        style.Colors[ImGuiCol_WindowBg] = ImVec4(0.11f, 0.11f, 0.11f, 1.00f); // Fondo gris oscuro
        style.Colors[ImGuiCol_ChildBg] = ImVec4(0.11f, 0.11f, 0.11f, 1.00f);
        style.Colors[ImGuiCol_Button] = ImVec4(0.85f, 0.45f, 0.35f, 1.00f);    // Naranja Claude
        style.Colors[ImGuiCol_ButtonHovered] = ImVec4(0.95f, 0.55f, 0.45f, 1.00f);
        style.Colors[ImGuiCol_FrameBg] = ImVec4(0.18f, 0.18f, 0.18f, 1.00f);   // Input gris claro

        ImGui_ImplGlfw_InitForOther(window, true);
        ImGui_ImplDX11_Init(g_pd3dDevice, g_pd3dDeviceContext);

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            ImGui_ImplDX11_NewFrame();
            ImGui_ImplGlfw_NewFrame();
            ImGui::NewFrame();

            ImGui::SetNextWindowPos(ImVec2(0, 0));
            ImGui::SetNextWindowSize(io.DisplaySize);
            ImGui::Begin("NovaMain", nullptr, ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoScrollbar);

            // --- BARRA SUPERIOR ---
            ImGui::SetCursorPos(ImVec2(20, 20));
            ImGui::TextColored(ImVec4(0.9f, 0.9f, 0.9f, 1.0f), "Nova AI");
            ImGui::SameLine();
            ImGui::TextColored(ImVec4(0.5f, 0.5f, 0.5f, 1.0f), "|  Asistente Local");

            ImGui::SetCursorPos(ImVec2(io.DisplaySize.x - 200, 15));
            if (ImGui::Button("Cargar Modelo .gguf", ImVec2(180, 30))) {
                std::string path = OpenFileDialog();
                if (!path.empty()) std::thread(LoadModel, path).detach();
            }

            ImGui::SetCursorPosY(60);
            ImGui::Separator();

            // --- ÁREA DE CHAT (BLOQUES ESTILO CLAUDE) ---
            ImGui::SetCursorPos(ImVec2(0, 65));
            ImGui::BeginChild("ChatArea", ImVec2(0, io.DisplaySize.y - 140), false);
            {
                std::lock_guard<std::mutex> lock(chat_mutex);
                
                if (messages.empty()) {
                    // Pantalla de bienvenida
                    ImGui::Spacing(); ImGui::Spacing(); ImGui::Spacing(); ImGui::Spacing(); ImGui::Spacing();
                    ImGui::SetCursorPosX((io.DisplaySize.x - 400) / 2);
                    ImGui::TextColored(ImVec4(0.85f, 0.45f, 0.35f, 1.0f), "         * * *");
                    ImGui::SetCursorPosX((io.DisplaySize.x - 400) / 2);
                    ImGui::Text("¿En qué puedo ayudarte hoy?");
                }

                for (size_t i = 0; i < messages.size(); i++) {
                    auto& msg = messages[i];
                    
                    if (msg.is_user) {
                        // Bloque Usuario (Gris)
                        ImGui::PushStyleColor(ImGuiCol_ChildBg, ImVec4(0.18f, 0.18f, 0.18f, 1.0f));
                        ImGui::SetCursorPosX(20);
                        ImGui::BeginChild(("msg_u" + std::to_string(i)).c_str(), ImVec2(io.DisplaySize.x - 40, 0), true);
                        ImGui::TextWrapped("%s", msg.text.c_str());
                        ImGui::EndChild();
                        ImGui::PopStyleColor();
                    } else {
                        // Bloque IA (Fondo oscuro, sin borde)
                        ImGui::SetCursorPosX(20);
                        ImGui::BeginChild(("msg_a" + std::to_string(i)).c_str(), ImVec2(io.DisplaySize.x - 40, 0), false);
                        ImGui::TextColored(ImVec4(0.85f, 0.45f, 0.35f, 1.0f), "* Nova");
                        ImGui::TextWrapped("%s", msg.text.c_str());
                        if (msg.is_generating_this) {
                            ImGui::TextColored(ImVec4(0.5f, 0.5f, 0.5f, 1.0f), "Escribiendo...");
                        }
                        ImGui::EndChild();
                    }
                    ImGui::Spacing();
                }
                
                if (ImGui::GetScrollY() >= ImGui::GetScrollMaxY() - 10) {
                    ImGui::SetScrollHereY(1.0f);
                }
            }
            ImGui::EndChild();

            // --- CAJA DE TEXTO FLOTANTE (Estilo Claude) ---
            ImGui::SetCursorPos(ImVec2(20, io.DisplaySize.y - 65));
            ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(15, 15));
            ImGui::PushStyleColor(ImGuiCol_FrameBg, ImVec4(0.15f, 0.15f, 0.15f, 1.0f));
            ImGui::PushItemWidth(io.DisplaySize.x - 100);
            
            bool enter_pressed = ImGui::InputTextWithHint("##input", "Escribe tu mensaje aquí...", input_buffer, sizeof(input_buffer), ImGuiInputTextFlags_EnterReturnsTrue);
            
            ImGui::PopItemWidth();
            ImGui::PopStyleColor();
            ImGui::PopStyleVar();

            ImGui::SameLine();
            
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.85f, 0.45f, 0.35f, 1.0f));
            bool send_clicked = ImGui::Button(" ^ ", ImVec2(50, 45));
            ImGui::PopStyleColor();

            if ((send_clicked || enter_pressed) && strlen(input_buffer) > 0 && !is_generating) {
                if (model) {
                    std::string prompt(input_buffer);
                    {
                        std::lock_guard<std::mutex> lock(chat_mutex);
                        messages.push_back({true, prompt, false});
                    }
                    input_buffer[0] = '\0';
                    std::thread(GenerateResponse, prompt).detach();
                }
            }

            ImGui::End();
            ImGui::Render();
            
            const float clear_color[4] = { 0.11f, 0.11f, 0.11f, 1.0f };
            g_pd3dDeviceContext->OMSetRenderTargets(1, &g_mainRenderTargetView, nullptr);
            g_pd3dDeviceContext->ClearRenderTargetView(g_mainRenderTargetView, clear_color);
            ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
            g_pSwapChain->Present(1, 0);
        }

        ImGui_ImplDX11_Shutdown();
        ImGui_ImplGlfw_Shutdown();
        ImGui::DestroyContext();
        CleanupDeviceD3D();
        glfwDestroyWindow(window);
        glfwTerminate();

        if (model) llama_free_model(model);
        if (ctx) llama_free(ctx);
    } catch (...) {
        log_msg("Excepcion capturada en main");
        return 1;
    }
    return 0;
}
