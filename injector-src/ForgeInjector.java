import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;

public class ForgeInjector extends Thread {

    private byte[][] classes;

    private ForgeInjector(byte[][] classes) {
        this.classes = classes;
    }

    public static void inject(byte[][] classes) {
        new Thread(new ForgeInjector(classes)).start();
    }

    private static Class<?> tryGetClass(ClassLoader cl, String... names) throws ClassNotFoundException {
        ClassNotFoundException lastException = null;
        for (String name : names) {
            try {
                return cl.loadClass(name);
            } catch (ClassNotFoundException e) {
                lastException = e;
            }
        }
        throw lastException;
    }

    @Override
    public void run() {
        try {
            ClassLoader cl = null;
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                ClassLoader threadLoader;
                if (thread == null || thread.getContextClassLoader() == null || (threadLoader = thread.getContextClassLoader()).getClass() == null || 
                    threadLoader.getClass().getName() == null) continue;
                String loaderName = threadLoader.getClass().getName();
                if (loaderName.contains("LaunchClassLoader") || loaderName.contains("TransformingClassLoader")) {
                    cl = threadLoader;
                    break;
                }
            }
            if (cl == null) {
                return;
            }
            this.setContextClassLoader(cl);

            Class<?> fmlPreInitClass = null;
            Class<?> fmlInitClass = null;
            try {
                fmlPreInitClass = tryGetClass(cl, 
                    "cpw.mods.fml.common.event.FMLPreInitializationEvent",
                    "net.minecraftforge.fml.common.event.FMLPreInitializationEvent");

                fmlInitClass = tryGetClass(cl, 
                    "cpw.mods.fml.common.event.FMLInitializationEvent",
                    "net.minecraftforge.fml.common.event.FMLInitializationEvent");
            } catch (ClassNotFoundException e) {
                // Quiet
            }

            Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE, ProtectionDomain.class);
            defineClass.setAccessible(true);

            ArrayList<Class<?>> loadedClasses = new ArrayList<>();
            for (byte[] classData : classes) {
                if (classData == null) continue;
                try {
                    Class<?> loadedClass = (Class<?>) defineClass.invoke(cl, null, classData, 0, classData.length, cl.getClass().getProtectionDomain());
                    loadedClasses.add(loadedClass);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof LinkageError) {
                        continue;
                    }
                } catch (Exception e) {
                    // Quiet
                }
            }

            Class<?> modAnnotationClass = null;
            try {
                modAnnotationClass = tryGetClass(cl,
                    "cpw.mods.fml.common.Mod",
                    "net.minecraftforge.fml.common.Mod");
            } catch (ClassNotFoundException e) {
                // Quiet
            }

            for (Class<?> modClass : loadedClasses) {
                if (modAnnotationClass != null && modClass.getAnnotation((Class)modAnnotationClass) != null) {
                    Object modInstance;
                    try {
                        modInstance = modClass.newInstance();
                    } catch (Exception e) {
                        continue;
                    }

                    ArrayList<Method> fmlPreInitMethods = new ArrayList<>();
                    ArrayList<Method> fmlInitMethods = new ArrayList<>();

                    for (Method method : modClass.getDeclaredMethods()) {
                        if (method.getParameterTypes().length == 1) {
                            if (fmlPreInitClass != null && method.getParameterTypes()[0].equals(fmlPreInitClass)) {
                                fmlPreInitMethods.add(method);
                            }
                            if (fmlInitClass != null && method.getParameterTypes()[0].equals(fmlInitClass)) {
                                fmlInitMethods.add(method);
                            }
                        }
                    }

                    for (Method preInitMethod : fmlPreInitMethods) {
                        try {
                            preInitMethod.invoke(modInstance, new Object[]{null});
                        } catch (Throwable t) {
                            // Quiet
                        }
                    }

                    for (Method initMethod : fmlInitMethods) {
                        try {
                            initMethod.invoke(modInstance, new Object[]{null});
                        } catch (Throwable t) {
                            // Quiet
                        }
                    }
                }
            }
        } catch (Throwable e) {
            // Quiet
        }
    }
}
