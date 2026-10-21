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
std::string chat_history = ""; // Vacío al inicio para mostrar la pantalla de bienvenida
char input_buffer[2048] = "";
std::atomic<bool> is_generating(false);
std::atomic<bool> is_loading(false);
std::atomic<bool> has_chat = false;
std::mutex chat_mutex;

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
        has_chat = true;
        chat_history += "Sistema: [Error] No se pudo cargar el modelo. Verifica el archivo.\n\n";
        is_loading = false;
        return;
    }

    vocab = llama_model_get_vocab(model);
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;
    ctx_params.n_threads = 1; // 1 hilo para el Celeron
    ctx_params.n_threads_batch = 1;
    
    ctx = llama_new_context_with_model(model, ctx_params);

    if (!ctx) {
        std::lock_guard<std::mutex> lock(chat_mutex);
        has_chat = true;
        chat_history += "Sistema: [Error] No hay RAM suficiente.\n\n";
        llama_free_model(model); model = nullptr;
        is_loading = false;
        return;
    }

    current_model_path = path;
    {
        std::lock_guard<std::mutex> lock(chat_mutex);
        has_chat = true;
        chat_history += "Sistema: Modelo cargado. ¡Ya puedes chatear!\n\n";
    }
    is_loading = false;
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

    {
        std::lock_guard<std::mutex> lock(chat_mutex);
        chat_history += "Nova: ";
    }

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
            if (n > 0) {
                std::lock_guard<std::mutex> lock(chat_mutex);
                chat_history += std::string(buf, n);
            }
            
            llama_batch next_batch = llama_batch_init(1, 0, 1);
            next_batch.token[0] = id; next_batch.pos[0] = current_pos;
            next_batch.n_seq_id[0] = 1; next_batch.seq_id[0][0] = 0;
            next_batch.logits[0] = true; next_batch.n_tokens = 1;

            if (llama_decode(ctx, next_batch)) { llama_batch_free(next_batch); break; }
            llama_batch_free(batch); batch = next_batch; current_pos++;
        }
        std::lock_guard<std::mutex> lock(chat_mutex);
        chat_history += "\n\n";
    }
    llama_batch_free(batch);
    is_generating = false;
}

// Helper para el diseño
void DrawWelcomeScreen(float width) {
    ImGui::Spacing(); ImGui::Spacing(); ImGui::Spacing(); ImGui::Spacing();
    
    // Cuadro naranja simulando el logo
    ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.85f, 0.45f, 0.35f, 1.0f)); // Naranja
    ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(0.85f, 0.45f, 0.35f, 1.0f));
    ImGui::SetCursorPosX((width - 60) / 2);
    ImGui::Button(" * * ", ImVec2(60, 60)); 
    ImGui::PopStyleColor(2);

    ImGui::Spacing();
    
    ImGui::PushFont(ImGui::GetIO().Fonts->Fonts[0]); // Usar fuente normal pero más grande
    float textWidth = ImGui::CalcTextSize("¿En qué puedo ayudarte?").x;
    ImGui::SetCursorPosX((width - textWidth) / 2);
    ImGui::TextColored(ImVec4(1,1,1,1), "¿En qué puedo ayudarte?");
    
    ImGui::Spacing();
    std::string sub = "Escribe lo que necesites: código, análisis, redacción o cualquier pregunta.";
    float subWidth = ImGui::CalcTextSize(sub.c_str()).x;
    ImGui::SetCursorPosX((width - subWidth) / 2);
    ImGui::TextColored(ImVec4(0.6f, 0.6f, 0.6f, 1.0f), "%s", sub.c_str());
    
    ImGui::Spacing(); ImGui::Spacing(); ImGui::Spacing();

    // Botones de sugerencia
    const char* suggestions[] = {
        "Explícame qué es una API REST y dame un ejemplo práctico en JavaScript. ->",
        "Escribe una función en Python que lea un CSV y devuelva estadísticas. ->",
        "Ayúdame a redactar un correo profesional ->"
    };

    for (int i = 0; i < 3; i++) {
        ImGui::SetCursorPosX(20);
        ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.15f, 0.15f, 0.15f, 1.0f)); // Botón oscuro
        ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(0.25f, 0.25f, 0.25f, 1.0f));
        if (ImGui::Button(suggestions[i], ImVec2(width - 40, 50))) {
            strcpy(input_buffer, suggestions[i]);
        }
        ImGui::PopStyleColor(2);
        ImGui::Spacing();
    }
}

int main() {
    log_msg("--- INICIANDO NOVA GUI (V3) ---");
    try {
        if (!glfwInit()) return 1;
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        
        GLFWwindow* window = glfwCreateWindow(900, 750, "Nova AI", NULL, NULL);
        if (!window) return 1;

        HWND hwnd = glfwGetWin32Window(window);
        if (!CreateDeviceD3D(hwnd)) return 1;

        IMGUI_CHECKVERSION();
        ImGui::CreateContext();
        ImGuiIO& io = ImGui::GetIO(); (void)io;
        
        // === ESTILO IDÉNTICO A LA FOTO ===
        ImGui::StyleColorsDark();
        ImGuiStyle& style = ImGui::GetStyle();
        style.WindowRounding = 0.0f;
        style.FrameRounding = 8.0f;
        style.Colors[ImGuiCol_WindowBg] = ImVec4(0.07f, 0.07f, 0.07f, 1.00f); // Fondo casi negro
        style.Colors[ImGuiCol_FrameBg] = ImVec4(0.14f, 0.14f, 0.14f, 1.00f); // Fondo del input
        style.Colors[ImGuiCol_Button] = ImVec4(0.14f, 0.14f, 0.14f, 1.00f); // Botón oscuro

        io.FontGlobalScale = 1.2f; // Letra grande

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
            ImGui::SetCursorPos(ImVec2(15, 15));
            ImGui::TextColored(ImVec4(0.9f, 0.9f, 0.9f, 1.0f), "|||  Nueva conversación");
            
            // Botón de Elegir Modelo a la derecha
            ImGui::SetCursorPos(ImVec2(io.DisplaySize.x - 200, 10));
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.14f, 0.14f, 0.14f, 1.0f));
            if (ImGui::Button(" (o) Elegir modelo  v ", ImVec2(180, 40))) {
                std::string path = OpenFileDialog();
                if (!path.empty()) std::thread(LoadModel, path).detach();
            }
            ImGui::PopStyleColor();

            ImGui::SetCursorPosY(60);
            ImGui::Separator();

            // --- ÁREA PRINCIPAL ---
            float areaHeight = io.DisplaySize.y - 150; // Espacio para arriba y abajo
            ImGui::SetCursorPos(ImVec2(0, 65));
            ImGui::BeginChild("MainArea", ImVec2(0, areaHeight), false);
            {
                if (!has_chat) {
                    DrawWelcomeScreen(io.DisplaySize.x);
                } else {
                    ImGui::SetCursorPosX(20);
                    ImGui::PushTextWrapPos(io.DisplaySize.x - 20);
                    std::lock_guard<std::mutex> lock(chat_mutex);
                    ImGui::TextUnformatted(chat_history.c_str());
                    if (ImGui::GetScrollY() >= ImGui::GetScrollMaxY()) {
                        ImGui::SetScrollHereY(1.0f);
                    }
                    ImGui::PopTextWrapPos();
                }
            }
            ImGui::EndChild();

            // --- BARRA DE ENTRADA INFERIOR ---
            ImGui::SetCursorPos(ImVec2(20, io.DisplaySize.y - 70));
            
            // Caja de texto gigante
            ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(15, 15));
            ImGui::PushItemWidth(io.DisplaySize.x - 100);
            bool enter_pressed = ImGui::InputTextWithHint("##input", "Escribe un mensaje...", input_buffer, sizeof(input_buffer), ImGuiInputTextFlags_EnterReturnsTrue);
            ImGui::PopItemWidth();
            ImGui::PopStyleVar();

            // Botón de enviar (flecha)
            ImGui::SameLine();
            ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.85f, 0.45f, 0.35f, 1.0f)); // Botón naranja
            ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(1, 1, 1, 1));
            bool send_clicked = ImGui::Button(" ^ ", ImVec2(50, 50));
            ImGui::PopStyleColor(2);

            if (is_loading) {
                ImGui::SetCursorPos(ImVec2(20, io.DisplaySize.y - 90));
                ImGui::TextColored(ImVec4(1, 0.8f, 0.2f, 1), "Cargando modelo...");
            }

            if ((send_clicked || enter_pressed) && strlen(input_buffer) > 0 && !is_generating) {
                if (model) {
                    std::string prompt(input_buffer);
                    has_chat = true;
                    {
                        std::lock_guard<std::mutex> lock(chat_mutex);
                        chat_history += "Tú: " + prompt + "\n";
                    }
                    input_buffer[0] = '\0';
                    std::thread(GenerateResponse, prompt).detach();
                } else {
                    has_chat = true;
                    std::lock_guard<std::mutex> lock(chat_mutex);
                    chat_history += "Sistema: [Error] Primero debes cargar un modelo.\n\n";
                    input_buffer[0] = '\0';
                }
            }

            ImGui::End();
            ImGui::Render();
            
            const float clear_color[4] = { 0.07f, 0.07f, 0.07f, 1.0f };
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
