package examples.memoryloader;

import java.lang.reflect.Method;

public class MemoryClassLoaderTest {
    public static void main(String[] args) {
        System.out.println("--- [1] MemoryClassLoader Test ---");
        try {
            MemoryClassLoader loader = new MemoryClassLoader();

            // Example dummy byte structure or simulated bytecode loading demonstration
            System.out.println("MemoryClassLoader initialized.");
            System.out.println("Parent ClassLoader: " + loader.getParent().getClass().getName());
            System.out.println("Ready to register in-memory class byte arrays via loader.addClass(name, bytecode).");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
