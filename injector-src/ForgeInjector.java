import java.io.File;
import java.io.PrintWriter;
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

    private static Class<?> tryGetClass(PrintWriter writer, ClassLoader cl, String... names) throws ClassNotFoundException {
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
        try (PrintWriter writer = new PrintWriter(System.getProperty("user.home") + File.separator + "jar-to-dll-log.txt", "UTF-8")) {
            writer.println("[ForgeInjector] Starting!");
            writer.flush();
            try {
                ClassLoader cl = null;
                for (Thread thread : Thread.getAllStackTraces().keySet()) {
                    ClassLoader threadLoader;
                    if (thread == null || thread.getContextClassLoader() == null || (threadLoader = thread.getContextClassLoader()).getClass() == null || 
                        threadLoader.getClass().getName() == null) continue;
                    String loaderName = threadLoader.getClass().getName();
                    writer.println("[ForgeInjector] Thread: " + thread.getName() + " [" + loaderName + "]");
                    writer.flush();
                    if (loaderName.contains("LaunchClassLoader") || loaderName.contains("TransformingClassLoader")) {
                        cl = threadLoader;
                        break;
                    }
                }
                if (cl == null) {
                    throw new Exception("Could not find LaunchClassLoader or TransformingClassLoader");
                }
                this.setContextClassLoader(cl);
                writer.println("[ForgeInjector] Found ClassLoader: " + cl.getClass().getName());
                writer.flush();

                Class<?> fmlPreInitClass = null;
                Class<?> fmlInitClass = null;
                try {
                    fmlPreInitClass = tryGetClass(writer, cl, 
                        "cpw.mods.fml.common.event.FMLPreInitializationEvent",
                        "net.minecraftforge.fml.common.event.FMLPreInitializationEvent");

                    fmlInitClass = tryGetClass(writer, cl, 
                        "cpw.mods.fml.common.event.FMLInitializationEvent",
                        "net.minecraftforge.fml.common.event.FMLInitializationEvent");
                }
                catch (ClassNotFoundException e) {
                    writer.println("[ForgeInjector] Event handler annotations not found, probably newer FML version");
                    writer.flush();
                }

                Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE, ProtectionDomain.class);
                defineClass.setAccessible(true);
                writer.println("[ForgeInjector] Loading " + classes.length + " classes");
                writer.flush();
                ArrayList<Class<?>> loadedClasses = new ArrayList<>();
                for (byte[] classData : classes) {
                    if (classData == null) {
                        throw new Exception("classData is null");
                    }
                    try {
                        Class<?> loadedClass = (Class<?>) defineClass.invoke(cl, null, classData, 0, classData.length, cl.getClass().getProtectionDomain());
                        loadedClasses.add(loadedClass);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof LinkageError) {
                            if (e.getMessage() != null && e.getMessage().contains("duplicate class definition for name: ")) {
                                String className = e.getMessage().split("\"")[1];
                                writer.println("[ForgeInjector] Duplicate class skipping: " + className);
                                writer.flush();
                                continue;
                            }
                        }
                        e.printStackTrace(writer);
                        writer.flush();
                        throw new Exception("Exception on defineClass (InvocationTargetException)", e.getCause());
                    } catch (Exception e) {
                        e.printStackTrace(writer);
                        writer.flush();
                        throw new Exception("Exception on defineClass", e);
                    }
                }
                writer.println("[ForgeInjector] " + loadedClasses.size() + " classes loaded successfully");
                writer.flush();

                Class<?> modAnnotationClass = null;
                try {
                    modAnnotationClass = tryGetClass(writer, cl,
                        "cpw.mods.fml.common.Mod",
                        "net.minecraftforge.fml.common.Mod");
                }
                catch (ClassNotFoundException e) {
                    writer.println("[ForgeInjector] Could not find @Mod annotation class");
                    writer.flush();
                }

                for (Class<?> modClass : loadedClasses) {
                    if (modAnnotationClass != null && modClass.getAnnotation((Class)modAnnotationClass) != null) {
                        writer.println("[ForgeInjector] Instancing " + modClass.getName());
                        writer.flush();
                        Object modInstance;
                        try {
                            modInstance = modClass.newInstance();
                            writer.println("[ForgeInjector] Instanced");
                            writer.flush();
                        } catch (Exception e) {
                            writer.println("[ForgeInjector] Exception on instancing: " + e);
                            e.printStackTrace(writer);
                            writer.flush();
                            throw new Exception("Exception on instancing", e);
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
                                writer.println("[ForgeInjector] Preiniting " + preInitMethod);
                                writer.flush();
                                preInitMethod.invoke(modInstance, new Object[]{null});
                                writer.println("[ForgeInjector] Preinited");
                                writer.flush();
                            } catch (InvocationTargetException e) {
                                writer.println("[ForgeInjector] Exception on preiniting: " + e);
                                e.getCause().printStackTrace(writer);
                                writer.flush();
                                throw new Exception("Exception on preiniting", e.getCause());
                            } catch (Exception e) {
                                writer.println("[ForgeInjector] Exception on preiniting: " + e);
                                e.printStackTrace(writer);
                                writer.flush();
                                throw new Exception("Exception on preiniting", e);
                            }
                        }

                        for (Method initMethod : fmlInitMethods) {
                            try {
                                writer.println("[ForgeInjector] Initing " + initMethod);
                                writer.flush();
                                initMethod.invoke(modInstance, new Object[]{null});
                                writer.println("[ForgeInjector] Inited");
                                writer.flush();
                            } catch (InvocationTargetException e) {
                                writer.println("[ForgeInjector] Exception on initing: " + e);
                                e.getCause().printStackTrace(writer);
                                writer.flush();
                                throw new Exception("Exception on initing", e.getCause());
                            } catch (Exception e) {
                                writer.println("[ForgeInjector] Exception on initing: " + e);
                                e.printStackTrace(writer);
                                writer.flush();
                                throw new Exception("Exception on initing", e);
                            }
                        }
                    }
                }
                writer.println("[ForgeInjector] Successfully injected");
                writer.flush();
            } catch (Throwable e) {
                e.printStackTrace(writer);
                writer.flush();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
