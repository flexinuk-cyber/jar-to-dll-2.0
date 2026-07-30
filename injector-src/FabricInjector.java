import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * FabricInjector — injects Fabric mods (up to MC 1.21.1) into a running JVM.
 *
 * Contract exposed to native (injector.cpp):
 *   public static void inject(byte[][] classes, byte[] fabricModJson)
 *
 * The injector:
 *  1. Finds the Fabric KnotClassLoader on any running thread.
 *  2. Loads all classes from the embedded JAR via defineClass.
 *  3. Parses fabric.mod.json (passed as raw bytes) to discover entrypoint class names.
 *  4. Instantiates each entrypoint class and calls onInitialize() / onInitializeClient().
 */
public class FabricInjector extends Thread {

    private final byte[][] classes;
    private final byte[] fabricModJson;

    private FabricInjector(byte[][] classes, byte[] fabricModJson) {
        this.classes = classes;
        this.fabricModJson = fabricModJson;
    }

    /** Entry-point called from native code (injector.cpp). */
    public static void inject(byte[][] classes, byte[] fabricModJson) {
        new Thread(new FabricInjector(classes, fabricModJson)).start();
    }

    // -------------------------------------------------------------------------
    // Thread body
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        try (PrintWriter writer = new PrintWriter(
                System.getProperty("user.home") + File.separator + "jar-to-dll-log.txt", "UTF-8")) {
            writer.println("[FabricInjector] Starting!");
            writer.flush();
            try {
                runImpl(writer);
            } catch (Throwable e) {
                writer.println("[FabricInjector] Fatal error:");
                e.printStackTrace(writer);
                writer.flush();
            }
            writer.println("[FabricInjector] Done.");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void runImpl(PrintWriter writer) throws Exception {
        // ------------------------------------------------------------------
        // 1. Find the Fabric KnotClassLoader
        // ------------------------------------------------------------------
        ClassLoader cl = findFabricClassLoader(writer);
        if (cl == null) {
            throw new Exception("[FabricInjector] Could not find Fabric KnotClassLoader");
        }
        this.setContextClassLoader(cl);
        writer.println("[FabricInjector] Found ClassLoader: " + cl.getClass().getName());
        writer.flush();

        // ------------------------------------------------------------------
        // 2. Load all classes via defineClass
        // ------------------------------------------------------------------
        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE, ProtectionDomain.class);
        defineClass.setAccessible(true);

        writer.println("[FabricInjector] Loading " + classes.length + " classes");
        writer.flush();

        for (byte[] classData : classes) {
            if (classData == null) {
                throw new Exception("[FabricInjector] classData is null");
            }
            try {
                defineClass.invoke(cl, null, classData, 0, classData.length,
                        cl.getClass().getProtectionDomain());
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getCause();
                if (cause instanceof LinkageError) {
                    String msg = cause.getMessage();
                    // Duplicate class — already loaded, skip
                    if (msg != null && msg.contains("duplicate class definition for name: ")) {
                        String className = msg.split("\"")[1];
                        writer.println("[FabricInjector] Class already loaded (skipping): " + className);
                        writer.flush();
                        continue;
                    }
                }
                throw new Exception("[FabricInjector] defineClass failed", cause);
            }
        }

        writer.println("[FabricInjector] All classes loaded");
        writer.flush();

        // ------------------------------------------------------------------
        // 3. Parse fabric.mod.json to extract entrypoints
        // ------------------------------------------------------------------
        String jsonText = new String(fabricModJson, StandardCharsets.UTF_8);
        writer.println("[FabricInjector] fabric.mod.json length: " + jsonText.length());
        writer.flush();

        List<String> mainEntrypoints   = parseEntrypoints(jsonText, "main");
        List<String> clientEntrypoints = parseEntrypoints(jsonText, "client");

        writer.println("[FabricInjector] main entrypoints:   " + mainEntrypoints);
        writer.println("[FabricInjector] client entrypoints: " + clientEntrypoints);
        writer.flush();

        // ------------------------------------------------------------------
        // 4. Instantiate and call onInitialize() / onInitializeClient()
        // ------------------------------------------------------------------
        Class<?> modInitializerClass       = loadIfPresent(cl, "net.fabricmc.api.ModInitializer");
        Class<?> clientModInitializerClass = loadIfPresent(cl, "net.fabricmc.api.ClientModInitializer");

        for (String entrypoint : mainEntrypoints) {
            callEntrypoint(writer, cl, entrypoint, modInitializerClass, "onInitialize");
        }
        for (String entrypoint : clientEntrypoints) {
            callEntrypoint(writer, cl, entrypoint, clientModInitializerClass, "onInitializeClient");
        }

        writer.println("[FabricInjector] Successfully injected all entrypoints");
        writer.flush();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Scans all live threads and returns the first ClassLoader whose name contains
     * "knot" or "fabric" (case-insensitive), which identifies Fabric's KnotClassLoader.
     * Falls back to any non-bootstrap loader if none is found with that name.
     */
    private static ClassLoader findFabricClassLoader(PrintWriter writer) {
        ClassLoader best = null;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == null) continue;
            ClassLoader cl = thread.getContextClassLoader();
            if (cl == null) continue;
            String name = cl.getClass().getName();
            writer.println("[FabricInjector] Thread: " + thread.getName() + " [" + name + "]");
            writer.flush();
            String nameLower = name.toLowerCase();
            if (nameLower.contains("knot") || nameLower.contains("fabric")) {
                return cl;
            }
            // Keep as fallback any non-trivial loader
            if (best == null && !nameLower.contains("appclassloader") && !nameLower.contains("bootstrap")) {
                best = cl;
            }
        }
        return best;
    }

    /**
     * Minimal JSON parser for fabric.mod.json entrypoints.
     * Looks for:
     *   "entrypoints": { "main": [ "com.example.MyMod", ... ], "client": [ ... ] }
     *
     * Does NOT require any external JSON library.
     */
    private static List<String> parseEntrypoints(String json, String key) {
        List<String> result = new ArrayList<>();
        // Find "entrypoints" object
        int epIdx = json.indexOf("\"entrypoints\"");
        if (epIdx == -1) return result;

        // Find the opening brace of the entrypoints object
        int braceStart = json.indexOf('{', epIdx);
        if (braceStart == -1) return result;

        // Find the closing brace (naive, non-nested scan — fine for well-formed mod JSONs)
        int braceEnd = findMatchingBrace(json, braceStart);
        if (braceEnd == -1) return result;

        String epSection = json.substring(braceStart, braceEnd + 1);

        // Find the key inside the entrypoints object
        String keyToken = "\"" + key + "\"";
        int keyIdx = epSection.indexOf(keyToken);
        if (keyIdx == -1) return result;

        // Find the opening bracket of the array
        int arrStart = epSection.indexOf('[', keyIdx);
        if (arrStart == -1) return result;
        int arrEnd = epSection.indexOf(']', arrStart);
        if (arrEnd == -1) return result;

        String arrSection = epSection.substring(arrStart + 1, arrEnd);

        // Extract all quoted strings from the array
        int pos = 0;
        while (pos < arrSection.length()) {
            int q1 = arrSection.indexOf('"', pos);
            if (q1 == -1) break;
            int q2 = arrSection.indexOf('"', q1 + 1);
            if (q2 == -1) break;
            String entry = arrSection.substring(q1 + 1, q2);
            if (!entry.isEmpty()) {
                // Fabric entrypoints can be "className::methodName" or just "className"
                // We only need the class name part
                int colonIdx = entry.indexOf("::");
                String className = (colonIdx != -1) ? entry.substring(0, colonIdx) : entry;
                result.add(className.replace('/', '.'));
            }
            pos = q2 + 1;
        }
        return result;
    }

    /** Returns the index of the matching '}' for the '{' at startIdx. */
    private static int findMatchingBrace(String s, int startIdx) {
        int depth = 0;
        boolean inString = false;
        for (int i = startIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /** Loads a class if present, returns null otherwise (no exception). */
    private static Class<?> loadIfPresent(ClassLoader cl, String className) {
        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Loads an entrypoint class, instantiates it, and calls the given lifecycle method.
     * Works whether or not the interface class is available (falls back to name-based lookup).
     */
    private static void callEntrypoint(PrintWriter writer, ClassLoader cl,
            String className, Class<?> ifaceClass, String methodName) throws Exception {
        writer.println("[FabricInjector] Instantiating entrypoint: " + className);
        writer.flush();

        Class<?> clazz;
        try {
            clazz = cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new Exception("[FabricInjector] Entrypoint class not found: " + className, e);
        }

        Object instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new Exception("[FabricInjector] Failed to instantiate: " + className, e);
        }

        writer.println("[FabricInjector] Calling " + methodName + "() on " + className);
        writer.flush();

        try {
            Method method;
            if (ifaceClass != null && ifaceClass.isAssignableFrom(clazz)) {
                method = ifaceClass.getMethod(methodName);
            } else {
                // Fallback: look for the method by name directly on the class
                method = clazz.getMethod(methodName);
            }
            method.invoke(instance);
        } catch (InvocationTargetException ite) {
            throw new Exception("[FabricInjector] " + methodName + "() threw an exception in " + className, ite.getCause());
        } catch (NoSuchMethodException e) {
            throw new Exception("[FabricInjector] Method " + methodName + "() not found in " + className, e);
        }

        writer.println("[FabricInjector] " + methodName + "() completed for " + className);
        writer.flush();
    }
}
