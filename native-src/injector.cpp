#include <windows.h>

#include "injector.h"
#include "jvm/jni.h"
#include "utils.h"

#if __has_include("classes/injector.h")
#include "classes/injector.h"
#else
#include "stub_classes/injector.h"
#endif

#if __has_include("classes/fabric_injector.h")
#include "classes/fabric_injector.h"
#else
#include "stub_classes/fabric_injector.h"
#endif

#if __has_include("classes/jar.h")
#include "classes/jar.h"
#else
#include "stub_classes/jar.h"
#endif

static HMODULE GetJvmDll() {
  const auto jvm_dll = GetModuleHandleW(L"jvm.dll");
  if (!jvm_dll) {
    Error(L"Can't get jvm.dll handle");
  }
  return jvm_dll;
}

typedef jint(JNICALL* GetCreatedJavaVMs)(JavaVM**, jsize, jsize*);

static GetCreatedJavaVMs GetGetCreatedJavaVMsProc(HMODULE jvm_dll) {
  const auto get_created_java_vms_raw_proc =
      GetProcAddress(jvm_dll, "JNI_GetCreatedJavaVMs");
  if (!get_created_java_vms_raw_proc) {
    Error(L"Can't get JNI_GetCreatedJavaVMs proc");
  }
  return reinterpret_cast<GetCreatedJavaVMs>(get_created_java_vms_raw_proc);
}

static JavaVM* GetJVM() {
  const auto jvm_dll = GetJvmDll();
  const auto get_created_java_vms = GetGetCreatedJavaVMsProc(jvm_dll);

  JavaVM* jvms[1];
  jsize n_vms = 1;
  get_created_java_vms(jvms, n_vms, &n_vms);

  if (n_vms == 0) {
    Error(L"Can't get JVM");
  }

  return jvms[0];
}

static void GetJNIEnv(JavaVM* jvm, JNIEnv*& jni_env) {
  jni_env = nullptr;
  jvm->AttachCurrentThread(reinterpret_cast<void**>(&jni_env), nullptr);
  jvm->GetEnv(reinterpret_cast<void**>(&jni_env), JNI_VERSION_1_8);

  if (!jni_env) {
    Error(L"Can't get JNIEnv");
  }
}

static jclass DefineOrGetInjector(JNIEnv* jni_env) {
#if IS_FABRIC_MOD
  // --- Fabric path ---
  const auto existing = jni_env->FindClass(FABRIC_INJECTOR_CLASS_NAME);
  if (existing) {
    return existing;
  }
  const auto injector_class = jni_env->DefineClass(
      nullptr, nullptr, fabric_injector_class_data,
      sizeof(fabric_injector_class_data));
  if (!injector_class) {
    Error(L"Failed to define FabricInjector class");
  }
  return injector_class;
#else
  // --- Forge path ---
  const auto existing_injector_class = jni_env->FindClass(INJECTOR_CLASS_NAME);
  if (existing_injector_class) {
    return existing_injector_class;
  }
  const auto injector_class = jni_env->DefineClass(
      nullptr, nullptr, injector_class_data, sizeof(injector_class_data));
  if (!injector_class) {
    Error(L"Failed to define injector class");
  }
  return injector_class;
#endif
}

#if !IS_FABRIC_MOD
static jobjectArray GetJarClassesArray(JNIEnv* jni_env) {
  const auto byte_array_class = jni_env->FindClass("[B");
  if (!byte_array_class) {
    Error(L"Failed to get byte array class");
  }
  const auto jar_classes_array = jni_env->NewObjectArray(
      sizeof(jar_classes_sizes) / sizeof(jar_classes_sizes[0]),
      byte_array_class, nullptr);
  if (!jar_classes_array) {
    Error(L"Failed to create jar classes array");
  }
  for (size_t i = 0;
       i < sizeof(jar_classes_sizes) / sizeof(jar_classes_sizes[0]); i++) {
    const auto class_byte_array = jni_env->NewByteArray(jar_classes_sizes[i]);
    if (!class_byte_array) {
      Error(L"Failed to create class byte array");
    }
    jni_env->SetByteArrayRegion(class_byte_array, 0, jar_classes_sizes[i],
                                jar_classes_data[i]);
    jni_env->SetObjectArrayElement(jar_classes_array, static_cast<jint>(i),
                                   class_byte_array);
  }

  return jar_classes_array;
}
#endif

#if IS_FABRIC_MOD
static jbyteArray GetFabricModJsonArray(JNIEnv* jni_env) {
  const auto json_array = jni_env->NewByteArray(fabric_mod_json_size);
  if (!json_array) {
    Error(L"Failed to create fabric.mod.json byte array");
  }
  jni_env->SetByteArrayRegion(json_array, 0, fabric_mod_json_size, fabric_mod_json_data);
  return json_array;
}
#endif

static void CallInjector(JNIEnv* jni_env, jclass injector_class) {
#if IS_FABRIC_MOD
  // 1. Locate KnotClassLoader via FabricInjector.findFabricClassLoader()
  const auto find_cl_method = jni_env->GetStaticMethodID(
      injector_class, "findFabricClassLoader", "()Ljava/lang/ClassLoader;");
  if (!find_cl_method) {
    Error(L"Failed to find FabricInjector.findFabricClassLoader method ID");
  }
  jobject knot_loader = jni_env->CallStaticObjectMethod(injector_class, find_cl_method);
  if (!knot_loader) {
    Error(L"Fabric KnotClassLoader not found on any active thread");
  }

  // 2. Define all mod classes directly into KnotClassLoader via JNI DefineClass (bypasses Java 17/21 module checks)
  const size_t num_classes = sizeof(jar_classes_sizes) / sizeof(jar_classes_sizes[0]);
  for (size_t i = 0; i < num_classes; i++) {
    jclass defined_cls = jni_env->DefineClass(
        nullptr, knot_loader, (const jbyte*)jar_classes_data[i], jar_classes_sizes[i]);
    if (jni_env->ExceptionCheck()) {
      jni_env->ExceptionClear(); // Clear LinkageError / duplicate class exceptions
    }
  }

  // 3. Invoke entrypoints via FabricInjector.invokeEntrypoints(knot_loader, fabric_mod_json)
  const auto invoke_ep_method = jni_env->GetStaticMethodID(
      injector_class, "invokeEntrypoints", "(Ljava/lang/ClassLoader;[B)V");
  if (!invoke_ep_method) {
    Error(L"Failed to find FabricInjector.invokeEntrypoints method ID");
  }
  const auto fabric_json_array = GetFabricModJsonArray(jni_env);
  jni_env->CallStaticVoidMethod(injector_class, invoke_ep_method, knot_loader, fabric_json_array);

#else
  // ForgeInjector.inject(byte[][] classes)
  const auto jar_classes_array = GetJarClassesArray(jni_env);
  const auto inject_method_id =
      jni_env->GetStaticMethodID(injector_class, "inject", "([[B)V");
  if (!inject_method_id) {
    Error(L"Failed to find inject method ID");
  }
  jni_env->CallStaticVoidMethod(
      injector_class, inject_method_id, jar_classes_array);
#endif
}

void RunInjector() {
  const auto jvm = GetJVM();

  JNIEnv* jni_env;
  GetJNIEnv(jvm, jni_env);

  const auto injector_class = DefineOrGetInjector(jni_env);

  CallInjector(jni_env, injector_class);

  FreeLibraryAndExitThread(::global_dll_instance, 0);
}
