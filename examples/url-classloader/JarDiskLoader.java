package examples.diskloader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * JarDiskLoader demonstrates dynamic class loading of an external .jar file from disk using URLClassLoader.
 */
public class JarDiskLoader {

    public static void loadJarFromDisk(String jarFilePath, String classNameToLoad) {
        File jarFile = new File(jarFilePath);
        if (!jarFile.exists()) {
            System.err.println("[JarDiskLoader] File not found: " + jarFile.getAbsolutePath());
            return;
        }

        try {
            URL jarUrl = jarFile.toURI().toURL();
            System.out.println("[JarDiskLoader] Loading JAR from URL: " + jarUrl);

            try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, ClassLoader.getSystemClassLoader())) {
                Class<?> loadedClass = classLoader.loadClass(classNameToLoad);
                System.out.println("[JarDiskLoader] Class loaded successfully: " + loadedClass.getName());

                Object instance = loadedClass.getDeclaredConstructor().newInstance();
                System.out.println("[JarDiskLoader] Instance created: " + instance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java JarDiskLoader <path-to-jar> <fully-qualified-class-name>");
            return;
        }
        loadJarFromDisk(args[0], args[1]);
    }
}
