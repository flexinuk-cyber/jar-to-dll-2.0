#ifndef STUB_CLASSES_JAR_H_
#define STUB_CLASSES_JAR_H_

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

extern const unsigned char jar_data[4];
const unsigned char jar_data[4] = {0x50, 0x4B, 0x03, 0x04};

extern const size_t jar_size;
const size_t jar_size = 4;

#ifdef __cplusplus
}
#endif

#endif  // STUB_CLASSES_JAR_H_
