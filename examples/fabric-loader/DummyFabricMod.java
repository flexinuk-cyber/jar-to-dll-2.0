package examples.fabricloader;

public class DummyFabricMod {
    public DummyFabricMod() {
        System.out.println("[DummyFabricMod] Constructor called!");
    }

    public void onInitialize() {
        System.out.println("[DummyFabricMod] onInitialize() invoked successfully by FabricModLoader!");
    }
}
