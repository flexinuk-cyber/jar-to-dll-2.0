package examples.fabricloader;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FabricModLoader — A functional loader for Fabric mods inside a running JVM.
 * 
 * Key Steps:
 *  1. Locate Fabric's KnotClassLoader across all active Threads.
 *  2. Define mod bytecode directly into KnotClassLoader using reflection/defineClass.
 *  3. Parse fabric.mod.json to locate main and client entrypoints.
 *  4. Instantiate entrypoints and invoke onInitialize() / onInitializeClient().
 */
public class FabricModLoader {

    /**
     * Entrypoint to inject a Fabric mod into the target Fabric JVM environment.
     * 
     * @param modClassBytes Map of className -> bytecode array for all classes in the mod JAR.
     * @param fabricModJson Raw bytes of fabric.mod.json.
     */
    public static void injectFabricMod(Map<String, byte[]> modClassBytes, byte[] fabricModJson) {
        try {
            System.out.println("[FabricModLoader] Searching for Fabric KnotClassLoader...");
            ClassLoader knotClassLoader = findFabricClassLoader();

            if (knotClassLoader == null) {
                throw new IllegalStateException("Fabric KnotClassLoader not found on any active thread.");
            }
            System.out.println("[FabricModLoader] Found KnotClassLoader: " + knotClassLoader.getClass().getName());

            // 1. Define mod classes directly into KnotClassLoader
            Method defineClassMethod = getDefineClassMethod();
            ProtectionDomain domain = knotClassLoader.getClass().getProtectionDomain();

            int loadedCount = 0;
            for (Map.Entry<String, byte[]> entry : modClassBytes.entrySet()) {
                String className = entry.getKey();
                byte[] bytecode = entry.getValue();

                try {
                    // Check if class already exists in KnotClassLoader
                    knotClassLoader.loadClass(className);
                    System.out.println("[FabricModLoader] Class already present, skipping: " + className);
                } catch (ClassNotFoundException e) {
                    defineClassMethod.invoke(knotClassLoader, className, bytecode, 0, bytecode.length, domain);
                    loadedCount++;
                }
            }
            System.out.println("[FabricModLoader] Defined " + loadedCount + " classes into KnotClassLoader.");

            // 2. Parse fabric.mod.json for entrypoint class names
            String jsonText = new String(fabricModJson, StandardCharsets.UTF_8);
            List<String> mainEntrypoints = parseEntrypoints(jsonText, "main");
            List<String> clientEntrypoints = parseEntrypoints(jsonText, "client");

            System.out.println("[FabricModLoader] Discovered main entrypoints: " + mainEntrypoints);
            System.out.println("[FabricModLoader] Discovered client entrypoints: " + clientEntrypoints);

            // 3. Obtain Fabric ModInitializer interface classes if present
            Class<?> modInitializerClass = loadIfPresent(knotClassLoader, "net.fabricmc.api.ModInitializer");
            Class<?> clientModInitializerClass = loadIfPresent(knotClassLoader, "net.fabricmc.api.ClientModInitializer");

            // 4. Instantiate and invoke entrypoints
            for (String entrypointClass : mainEntrypoints) {
                callEntrypoint(knotClassLoader, entrypointClass, modInitializerClass, "onInitialize");
            }
            for (String entrypointClass : clientEntrypoints) {
                callEntrypoint(knotClassLoader, entrypointClass, clientModInitializerClass, "onInitializeClient");
            }

            System.out.println("[FabricModLoader] Successfully injected and initialized Fabric mod!");

        } catch (Exception e) {
            System.err.println("[FabricModLoader] Error injecting Fabric mod:");
            e.printStackTrace();
        }
    }

    /**
     * Scans all active JVM threads to find KnotClassLoader.
     */
    private static ClassLoader findFabricClassLoader() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }

        Thread[] threads = new Thread[rootGroup.activeCount() * 2];
        int count = rootGroup.enumerate(threads, true);

        for (int i = 0; i < count; i++) {
            Thread thread = threads[i];
            if (thread == null) continue;
            ClassLoader cl = thread.getContextClassLoader();
            if (cl != null && isKnotClassLoader(cl)) {
                return cl;
            }
        }

        // Fallback: check current thread context class loader or system classloader hierarchy
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while (cl != null) {
            if (isKnotClassLoader(cl)) return cl;
            cl = cl.getParent();
        }
        return null;
    }

    private static boolean isKnotClassLoader(ClassLoader cl) {
        String name = cl.getClass().getName();
        return name.contains("KnotClassLoader") || name.contains("fabricmc") || name.contains("TransformingClassLoader");
    }

    /**
     * Resolves ClassLoader.defineClass method via reflection and sets it accessible.
     */
    private static Method getDefineClassMethod() throws Exception {
        Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
        m.setAccessible(true);
        return m;
    }

    /**
     * Minimal JSON parser to extract entrypoint array strings for a given key ("main" or "client").
     */
    private static List<String> parseEntrypoints(String json, String key) {
        List<String> entrypoints = new ArrayList<>();
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex == -1) return entrypoints;

        int bracketOpen = json.indexOf('[', keyIndex);
        int bracketClose = json.indexOf(']', keyIndex);
        if (bracketOpen == -1 || bracketClose == -1 || bracketOpen > bracketClose) {
            // Check single value: "main": "com.example.ModMain"
            int colonIndex = json.indexOf(':', keyIndex);
            if (colonIndex != -1) {
                int quote1 = json.indexOf('"', colonIndex);
                int quote2 = json.indexOf('"', quote1 + 1);
                if (quote1 != -1 && quote2 != -1) {
                    entrypoints.add(json.substring(quote1 + 1, quote2));
                }
            }
            return entrypoints;
        }

        String content = json.substring(bracketOpen + 1, bracketClose);
        int pos = 0;
        while (pos < content.length()) {
            int q1 = content.indexOf('"', pos);
            if (q1 == -1) break;
            int q2 = content.indexOf('"', q1 + 1);
            if (q2 == -1) break;
            entrypoints.add(content.substring(q1 + 1, q2));
            pos = q2 + 1;
        }
        return entrypoints;
    }

    private static Class<?> loadIfPresent(ClassLoader cl, String className) {
        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static void callEntrypoint(ClassLoader cl, String className, Class<?> ifaceClass, String methodName) throws Exception {
        System.out.println("[FabricModLoader] Instantiating entrypoint: " + className);
        Class<?> clazz = cl.loadClass(className);

        Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();

        System.out.println("[FabricModLoader] Invoking " + methodName + "() on " + className);
        Method m;
        if (ifaceClass != null && ifaceClass.isAssignableFrom(clazz)) {
            m = ifaceClass.getMethod(methodName);
        } else {
            m = clazz.getMethod(methodName);
        }
        m.setAccessible(true);
        m.invoke(instance);
        System.out.println("[FabricModLoader] " + methodName + "() execution completed for " + className);
    }
}
