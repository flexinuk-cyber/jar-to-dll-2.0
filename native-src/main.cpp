#include <windows.h>
#include <stddef.h>

#if __has_include("classes/jar.h")
#include "classes/jar.h"
#else
#include "stub_classes/jar.h"
#endif

#define DLL_EXPORT __declspec(dllexport)

extern "C" {

DLL_EXPORT const unsigned char* GetJarBytes() {
    return jar_data;
}

DLL_EXPORT size_t GetJarSize() {
    return jar_size;
}

DLL_EXPORT extern const unsigned char* const jar_bytes;
const unsigned char* const jar_bytes = jar_data;

DLL_EXPORT extern const size_t jar_bytes_size;
const size_t jar_bytes_size = jar_size;

}

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    (void)hinstDLL;
    (void)fdwReason;
    (void)lpvReserved;
    return TRUE;
}
