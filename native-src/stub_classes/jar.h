#ifndef STUB_CLASSES_JAR_H_
#define STUB_CLASSES_JAR_H_

#include "../jvm/jni.h"

#define IS_FABRIC_MOD 0
#define IS_FORGE_MOD  1

const jbyte fabric_mod_json_data[] = {0x00};
const jint fabric_mod_json_size = 0;

const jbyte test_class_1[] = {0x01, 0x02, 0x03};
const jbyte* jar_classes_data[] = {test_class_1};
const jint jar_classes_sizes[] = {3};

#endif  //STUB_CLASSES_JAR_H_
