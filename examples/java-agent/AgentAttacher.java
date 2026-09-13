package examples.agent;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

/**
 * AgentAttacher demonstrates dynamic attachment to a target JVM PID using Java Attach API / Reflection.
 */
public class AgentAttacher {

    public static void attachToSelf(String agentJarPath) {
        String pid = getProcessId();
        System.out.println("[AgentAttacher] Target PID: " + pid);
        attachToPid(pid, agentJarPath);
    }

    public static void attachToPid(String pid, String agentJarPath) {
        try {
            File agentFile = new File(agentJarPath);
            if (!agentFile.exists()) {
                System.err.println("[AgentAttacher] Error: Agent JAR file not found at " + agentFile.getAbsolutePath());
                return;
            }

            // Using reflection to invoke com.sun.tools.attach.VirtualMachine without direct compile dependency
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Method attachMethod = vmClass.getMethod("attach", String.class);
            Method loadAgentMethod = vmClass.getMethod("loadAgent", String.class);
            Method detachMethod = vmClass.getMethod("detach");

            Object vm = attachMethod.invoke(null, pid);
            System.out.println("[AgentAttacher] Attached to JVM PID: " + pid);

            loadAgentMethod.invoke(vm, agentFile.getAbsolutePath());
            System.out.println("[AgentAttacher] Agent loaded successfully.");

            detachMethod.invoke(vm);
            System.out.println("[AgentAttacher] Detached from target JVM.");
        } catch (ClassNotFoundException e) {
            System.err.println("[AgentAttacher] VirtualMachine class not found. Ensure jdk.attach module or tools.jar is available.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getProcessId() {
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        return jvmName.split("@")[0];
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java AgentAttacher <path-to-agent.jar> [target-pid]");
            return;
        }

        String agentJar = args[0];
        if (args.length >= 2) {
            attachToPid(args[1], agentJar);
        } else {
            attachToSelf(agentJar);
        }
    }
}
