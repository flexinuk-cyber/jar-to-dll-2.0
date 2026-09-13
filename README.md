# jar-to-dll

A simple tool that embeds raw `.jar` file bytes directly into a Windows dynamic link library (`.dll`).

## Features

- **Raw Byte Packing:** Takes `input.jar` and embeds its raw binary content directly into `.dll`.
- **No JVM / Injection Dependencies:** Lightweight, clean, and silent. No JNI, no JVM searching, no pop-ups.
- **Exported Symbols:** Readily exposes exported functions and symbols to access the `.jar` bytes from native code or loaders:
  - `GetJarBytes()` / `jar_bytes` / `jar_data`: Pointer to the raw `.jar` byte array.
  - `GetJarSize()` / `jar_bytes_size` / `jar_size`: Total byte size of the embedded `.jar`.

## Requirements

- JDK 21 (or JDK 8+)
- MinGW-w64 GCC compiler (`x86_64-w64-mingw32-g++` or standard `g++`)
- `make` or `mingw32-make`

## How to Use

1. Place your target `.jar` file into the repository root directory as `input.jar`.
2. Run the build command:
   ```shell
   make build
   ```
   *(On Windows with MinGW, you can use `mingw32-make build`)*
3. The generated `output.dll` will be placed in the repository root directory.
