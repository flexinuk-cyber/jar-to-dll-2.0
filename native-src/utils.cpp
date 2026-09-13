#include "utils.h"
#include <windows.h>

HMODULE global_dll_instance = nullptr;

void ShowMessage(const wchar_t* message) {
    // SILENT: No popups!
    (void)message;
}

void Error(const wchar_t* error) {
    (void)error;
    FreeLibraryAndExitThread(global_dll_instance, 1);
}
