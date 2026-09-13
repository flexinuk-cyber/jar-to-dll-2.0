package examples.fabricloader;

import java.util.HashMap;
import java.util.Map;

public class FabricModLoaderTest {

    public static void main(String[] args) {
        System.out.println("--- [4] FabricModLoader Test ---");

        String dummyFabricModJson = "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"id\": \"example-mod\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"entrypoints\": {\n" +
                "    \"main\": [\n" +
                "      \"examples.fabricloader.DummyFabricMod\"\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        Map<String, byte[]> classMap = new HashMap<>();

        try {
            System.out.println("Testing FabricModLoader with simulated fabric.mod.json...");
            byte[] jsonBytes = dummyFabricModJson.getBytes();
            FabricModLoader.injectFabricMod(classMap, jsonBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
