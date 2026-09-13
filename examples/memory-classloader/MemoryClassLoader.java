package examples.memoryloader;

import java.util.HashMap;
import java.util.Map;

/**
 * MemoryClassLoader demonstrates how to load Java class bytecode directly from in-memory byte arrays.
 */
public class MemoryClassLoader extends ClassLoader {
    private final Map<String, byte[]> classBytecodeMap = new HashMap<>();

    public MemoryClassLoader() {
        super(ClassLoader.getSystemClassLoader());
    }

    public MemoryClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Registers a class bytecode array under a fully qualified class name.
     */
    public void addClass(String className, byte[] bytecode) {
        classBytecodeMap.put(className, bytecode);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classBytecodeMap.get(name);
        if (bytes == null) {
            return super.findClass(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
