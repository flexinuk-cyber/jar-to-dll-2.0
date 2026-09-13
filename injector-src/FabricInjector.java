import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * FabricInjector — Helper invoked from C++ JNI (Silent).
 */
public class FabricInjector {

    /**
     * Called from C++ JNI to locate the Fabric KnotClassLoader.
     */
    public static ClassLoader findFabricClassLoader() {
        try {
            ClassLoader best = null;
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread == null) continue;
                ClassLoader cl = thread.getContextClassLoader();
                if (cl == null) continue;

                String name = cl.getClass().getName();
                String nameLower = name.toLowerCase();
                if (nameLower.contains("knot") || nameLower.contains("fabric")) {
                    return cl;
                }
                if (best == null && !nameLower.contains("appclassloader") && !nameLower.contains("bootstrap")) {
                    best = cl;
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Called from C++ JNI after all mod classes have been defined into KnotClassLoader via JNI DefineClass.
     */
    public static void invokeEntrypoints(ClassLoader cl, byte[] fabricModJson) {
        try {
            if (cl == null) return;

            String jsonText = new String(fabricModJson, StandardCharsets.UTF_8);
            List<String> preLaunchEntrypoints = parseEntrypoints(jsonText, "preLaunch");
            List<String> mainEntrypoints      = parseEntrypoints(jsonText, "main");
            List<String> clientEntrypoints    = parseEntrypoints(jsonText, "client");

            Class<?> preLaunchClass            = loadIfPresent(cl, "net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint");
            Class<?> modInitializerClass       = loadIfPresent(cl, "net.fabricmc.api.ModInitializer");
            Class<?> clientModInitializerClass = loadIfPresent(cl, "net.fabricmc.api.ClientModInitializer");

            // 1. Invoke preLaunch
            for (String entrypoint : preLaunchEntrypoints) {
                callEntrypoint(cl, entrypoint, preLaunchClass, "onPreLaunch");
            }

            // 2. Invoke main (onInitialize)
            for (String entrypoint : mainEntrypoints) {
                callEntrypoint(cl, entrypoint, modInitializerClass, "onInitialize");
            }

            // 3. Invoke client (onInitializeClient)
            for (String entrypoint : clientEntrypoints) {
                callEntrypoint(cl, entrypoint, clientModInitializerClass, "onInitializeClient");
            }
        } catch (Throwable t) {
            // Quiet
        }
    }

    private static List<String> parseEntrypoints(String json, String key) {
        List<String> result = new ArrayList<>();
        int epIdx = json.indexOf("\"entrypoints\"");
        if (epIdx == -1) return result;

        int keyIdx = json.indexOf("\"" + key + "\"", epIdx);
        if (keyIdx == -1) return result;

        int bracketOpen = json.indexOf('[', keyIdx);
        int bracketClose = json.indexOf(']', keyIdx);

        if (bracketOpen == -1 || bracketClose == -1 || bracketOpen > bracketClose) {
            int colonIdx = json.indexOf(':', keyIdx);
            if (colonIdx != -1) {
                int q1 = json.indexOf('"', colonIdx);
                int q2 = json.indexOf('"', q1 + 1);
                if (q1 != -1 && q2 != -1) {
                    result.add(json.substring(q1 + 1, q2));
                }
            }
            return result;
        }

        String arrayContent = json.substring(bracketOpen + 1, bracketClose);
        int pos = 0;
        while (pos < arrayContent.length()) {
            int q1 = arrayContent.indexOf('"', pos);
            if (q1 == -1) break;
            int q2 = arrayContent.indexOf('"', q1 + 1);
            if (q2 == -1) break;
            result.add(arrayContent.substring(q1 + 1, q2));
            pos = q2 + 1;
        }
        return result;
    }

    private static Class<?> loadIfPresent(ClassLoader cl, String className) {
        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static void callEntrypoint(ClassLoader cl, String className, Class<?> ifaceClass, String methodName) {
        try {
            Class<?> clazz = cl.loadClass(className);
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();

            Method method;
            if (ifaceClass != null && ifaceClass.isAssignableFrom(clazz)) {
                method = ifaceClass.getMethod(methodName);
            } else {
                method = clazz.getMethod(methodName);
            }
            method.setAccessible(true);
            method.invoke(instance);
        } catch (Throwable t) {
            // Quiet
        }
    }
}
