#include <iostream>
#include <string>
#include <thread>
#include <chrono>
#include <cstdlib>
#include "httplib.h"

bool puerto_abierto(const std::string& host, int puerto) {
    httplib::Client cli(host, puerto);
    auto res = cli.Get("/health");
    return (res && res->status == 200);
}

int main() {
    std::string modelo = "model.gguf"; 
    std::string comando = "start /B llama-server.exe -m " + modelo + " --host 127.0.0.1 --port 8081 --ctx-size 2048 -t 2";

    std::cout << "Iniciando servidor de IA local..." << std::endl;
    int ret = std::system(comando.c_str());
    
    if (ret != 0) {
        std::cerr << "Error al iniciar llama-server. Verifica que este en el PATH." << std::endl;
        return 1;
    }

    int intentos = 0;
    while (!puerto_abierto("127.0.0.1", 8081) && intentos < 180) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
        intentos++;
        std::cout << "Esperando al servidor... (" << intentos << "/180)" << std::endl;
    }

    if (intentos >= 180) {
        std::cerr << "Tiempo de espera agotado." << std::endl;
        return 1;
    }

    std::cout << "¡Servidor listo! Abriendo interfaz web..." << std::endl;
    std::system("start http://127.0.0.1:8081");

    std::cout << "Presiona Enter para salir..." << std::endl;
    std::cin.get();
    return 0;
}
