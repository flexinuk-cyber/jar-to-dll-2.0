package examples.agent;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

/**
 * TestAgent demonstrates Java Agent entrypoints:
 * - premain (for -javaagent:agent.jar CLI flag)
 * - agentmain (for dynamic runtime attachment via Attach API / VirtualMachine.loadAgent)
 */
public class TestAgent {

    /**
     * Called when attached dynamically at runtime.
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("[TestAgent] Successfully attached via agentmain!");
        initAgent(agentArgs, inst);
    }

    /**
     * Called when passed at JVM startup via -javaagent command line.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[TestAgent] Successfully loaded via premain!");
        initAgent(agentArgs, inst);
    }

    private static void initAgent(String agentArgs, Instrumentation inst) {
        System.out.println("[TestAgent] Instrumentation instance: " + inst);
        System.out.println("[TestAgent] Redefine classes supported: " + inst.isRedefineClassesSupported());
        System.out.println("[TestAgent] Retransform classes supported: " + inst.isRetransformClassesSupported());

        Class<?>[] loadedClasses = inst.getAllLoadedClasses();
        System.out.println("[TestAgent] Total currently loaded classes in target JVM: " + loadedClasses.length);
    }
}
