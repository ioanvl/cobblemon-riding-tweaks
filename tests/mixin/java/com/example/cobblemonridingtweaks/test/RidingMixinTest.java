package com.example.cobblemonridingtweaks.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Applies real Mixins to dependency bytecode without launching Minecraft. */
public final class RidingMixinTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("mixin.service", Service.class.getName());
        System.setProperty("mixin.service.boot", "");
        // The Gradle development classpath uses Mojang names on both loaders.
        System.setProperty("mixin.env.disableRefMap", "true");
        MixinBootstrap.init();
        MixinEnvironment environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("cobblemon_riding_tweaks.mixins.json");
        Service service = (Service) MixinService.getService();
        IMixinTransformer transformer = service.transformer();

        String base = "com.cobblemon.mod.common.api.riding.behaviour.types.";
        Map<String, Integer> expectedCalls = Map.of(
                "land.HorseBehaviour", 1, "liquid.DolphinBehaviour", 1,
                "liquid.SubmarineBehaviour", 1, "air.BirdBehaviour", 4,
                "air.HoverBehaviour", 1, "air.JetBehaviour", 2,
                "air.RocketBehaviour", 7, "liquid.BoatBehaviour", 1);
        for (var entry : expectedCalls.entrySet()) {
            verify(transformer, environment, service, base + entry.getKey(), entry.getValue());
        }
        verify(transformer, environment, service, "com.cobblemon.mod.common.entity.pokemon.PokemonEntity", 4);
        System.out.println("Riding mixins applied successfully to all 9 Cobblemon target classes.");
    }

    private static void verify(IMixinTransformer transformer, MixinEnvironment environment,
                               Service service, String name, int expectedCalls) throws Exception {
        ClassNode node = service.getClassNode(name);
        if (!transformer.transformClass(environment, name, node)) {
            throw new AssertionError("No mixins applied to " + name);
        }
        int calls = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.contains("$cobblemonRidingTweaks$")) {
                    calls++;
                }
            }
        }
        if (calls != expectedCalls) {
            throw new AssertionError(name + ": expected " + expectedCalls + " hook calls, got " + calls);
        }
        System.out.println(name.substring(name.lastIndexOf('.') + 1) + ": " + calls + " hook calls verified");
    }

    public static final class Service extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
        @Override public String getName() { return "Riding mixin verification"; }
        @Override public boolean isValid() { return true; }
        @Override public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
        @Override public IClassProvider getClassProvider() { return this; }
        @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
        @Override public ITransformerProvider getTransformerProvider() { return null; }
        @Override public IClassTracker getClassTracker() { return null; }
        @Override public IMixinAuditTrail getAuditTrail() { return null; }
        @Override public Collection<String> getPlatformAgents() { return List.of(); }
        @Override public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("riding-test"); }
        @Override public InputStream getResourceAsStream(String name) { return getClass().getClassLoader().getResourceAsStream(name); }
        @Override public URL[] getClassPath() { return new URL[0]; }
        @Override public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
        @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, getClass().getClassLoader());
        }
        @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
            return findClass(name, initialize);
        }
        @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
            return getClassNode(name, false);
        }
        @Override public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
            return getClassNode(name, runTransformers, ClassReader.EXPAND_FRAMES);
        }
        @Override public ClassNode getClassNode(String name, boolean runTransformers, int flags) throws ClassNotFoundException, IOException {
            try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (input == null) { throw new ClassNotFoundException(name); }
                ClassNode node = new ClassNode();
                new ClassReader(input).accept(node, flags);
                return node;
            }
        }
        IMixinTransformer transformer() { return getInternal(IMixinTransformerFactory.class).createTransformer(); }
    }

    public static final class Properties implements IGlobalPropertyService {
        private record Key(String name) implements IPropertyKey {}
        private final Map<IPropertyKey, Object> values = new HashMap<>();
        @Override public IPropertyKey resolveKey(String name) { return new Key(name); }
        @SuppressWarnings("unchecked")
        @Override public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
        @Override public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
        @SuppressWarnings("unchecked")
        @Override public <T> T getProperty(IPropertyKey key, T fallback) { return (T) values.getOrDefault(key, fallback); }
        @Override public String getPropertyString(IPropertyKey key, String fallback) { return String.valueOf(values.getOrDefault(key, fallback)); }
    }
}
