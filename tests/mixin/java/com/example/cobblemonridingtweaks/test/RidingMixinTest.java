package com.example.cobblemonridingtweaks.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies real Mixins to dependency bytecode without launching Minecraft. */
public final class RidingMixinTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("CLIENT") || args[0].equals("SERVER"))) {
            throw new IllegalArgumentException("Expected CLIENT or SERVER");
        }
        MixinEnvironment.Side side = MixinEnvironment.Side.valueOf(args[0]);
        System.setProperty("mixin.service", Service.class.getName());
        System.setProperty("mixin.service.boot", "");
        // The Gradle development classpath uses Mojang names on both loaders.
        System.setProperty("mixin.env.disableRefMap", "true");
        MixinBootstrap.init();
        MixinEnvironment environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(side);
        Mixins.addConfiguration("cobblemon_riding_tweaks.mixins.json");
        Service service = (Service) MixinService.getService();
        IMixinTransformer transformer = service.transformer();

        String base = "com.cobblemon.mod.common.api.riding.behaviour.types.";
        if (side == MixinEnvironment.Side.SERVER) {
            verifyClientHierarchyGuard(service, base + "air.HoverBehaviour");
        }
        // Stamina ticks run on clients; movement and boat/rocket hooks also run on servers.
        List<Target> targets = List.of(
                new Target("air.HoverBehaviour", 1, Map.of()),
                new Target("land.HorseBehaviour", 1, Map.of()),
                new Target("liquid.DolphinBehaviour", 1, Map.of()),
                new Target("liquid.SubmarineBehaviour", 1, Map.of()),
                new Target("air.BirdBehaviour", 2, Map.of(
                        "readUnscaledCollisionMovement", 1, "scaleGlidingDrain", 1)),
                new Target("air.JetBehaviour", 2, Map.of()),
                new Target("air.RocketBehaviour", 1, Map.of(
                        "readUnscaledMovement", 5, "scaleBoostCharge", 1)),
                new Target("liquid.BoatBehaviour", 0, Map.of("scaleSprintDrain", 1)));
        for (Target target : targets) {
            Map<String, Integer> expected = new HashMap<>(target.sharedCalls());
            if (side == MixinEnvironment.Side.CLIENT && target.clientStaminaCalls() > 0) {
                expected.put("scaleDrainBeforeClamp", target.clientStaminaCalls());
            }
            verify(transformer, environment, service, base + target.name(), expected);
        }
        verify(transformer, environment, service, "com.cobblemon.mod.common.entity.pokemon.PokemonEntity", Map.of(
                "scaleRelativeFrictionVelocity", 1, "scaleTravelVelocity", 1,
                "scaleRiddenInputVelocity", 1, "scaleRiddenSpeed", 1));
        System.out.println(side + ": riding mixin selection and frame computation verified for all 9 targets.");
    }

    private record Target(String name, int clientStaminaCalls, Map<String, Integer> sharedCalls) {}

    private static void verify(IMixinTransformer transformer, MixinEnvironment environment,
                               Service service, String name, Map<String, Integer> expectedCalls) throws Exception {
        ClassNode node = service.getClassNode(name);
        boolean transformed = transformer.transformClass(environment, name, node);
        if (transformed) {
            // NeoForge rewrites frames after a transformation. Resolve hierarchy metadata
            // without loading Minecraft classes, rejecting client lookups on servers.
            try {
                writeFrames(node, environment.getSide() == MixinEnvironment.Side.SERVER);
            } catch (ForbiddenClientClass failure) {
                throw new AssertionError("Server transformation of " + name
                        + " requires client class " + failure.getMessage(), failure);
            }
        }
        if (transformed != !expectedCalls.isEmpty()) {
            throw new AssertionError(name + ": unexpected transformation on " + environment.getSide());
        }
        Map<String, Integer> calls = new HashMap<>();
        String callbackPrefix = "$cobblemonRidingTweaks$";
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.contains(callbackPrefix)) {
                    String callback = call.name.substring(call.name.indexOf(callbackPrefix) + callbackPrefix.length());
                    calls.merge(callback, 1, Integer::sum);
                }
            }
        }
        if (!calls.equals(expectedCalls)) {
            throw new AssertionError(name + ": expected " + expectedCalls + " hook calls, got " + calls);
        }
        System.out.println(name.substring(name.lastIndexOf('.') + 1) + ": " + calls + " verified");
    }

    private static void verifyClientHierarchyGuard(Service service, String hover) throws Exception {
        // Positive control: the original Hover method triggers precisely the lookup
        // that dedicated servers reject, even before a riding method can execute.
        ClassNode node = service.getClassNode(hover);
        try {
            writeFrames(node, true);
            throw new AssertionError("Server frame guard missed Hover's client hierarchy lookup");
        } catch (ForbiddenClientClass expected) {
            if (!expected.getMessage().equals("net/minecraft/client/player/LocalPlayer")) {
                throw new AssertionError("Unexpected Hover client dependency", expected);
            }
        }
        writeFrames(node, false);
        System.out.println("Frame guard detects Hover's LocalPlayer lookup on SERVER and permits it on CLIENT.");
    }

    private static void writeFrames(ClassNode node, boolean server) {
        ClassWriter writer = new FrameWriter(server);
        node.accept(writer);
        writer.toByteArray();
    }

    private static final class ForbiddenClientClass extends RuntimeException {
        private ForbiddenClientClass(String name) { super(name); }
    }

    /** Mirrors hierarchy resolution during loader frame computation, without game initialization. */
    private static final class FrameWriter extends ClassWriter {
        private final boolean server;
        private final Map<String, ClassReader> headers = new HashMap<>();
        private final Map<String, Set<String>> hierarchies = new HashMap<>();

        private FrameWriter(boolean server) {
            super(COMPUTE_FRAMES);
            this.server = server;
        }

        private ClassReader header(String name) {
            if (server && name.startsWith("net/minecraft/client/")) {
                throw new ForbiddenClientClass(name);
            }
            return headers.computeIfAbsent(name, key -> {
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(key + ".class")) {
                    if (input == null) throw new IllegalStateException("Missing hierarchy metadata: " + key);
                    return new ClassReader(input);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read hierarchy metadata: " + key, e);
                }
            });
        }

        private Set<String> hierarchy(String name) {
            Set<String> cached = hierarchies.get(name);
            if (cached != null) return cached;
            Set<String> types = new HashSet<>();
            collect(name, types);
            hierarchies.put(name, types);
            return types;
        }

        private void collect(String name, Set<String> types) {
            if (name == null || !types.add(name)) return;
            ClassReader reader = header(name);
            collect(reader.getSuperName(), types);
            for (String iface : reader.getInterfaces()) collect(iface, types);
        }

        @Override protected String getCommonSuperClass(String first, String second) {
            Set<String> firstTypes = hierarchy(first), secondTypes = hierarchy(second);
            if (firstTypes.contains(second)) return second;
            if (secondTypes.contains(first)) return first;
            if ((header(first).getAccess() & Opcodes.ACC_INTERFACE) != 0
                    || (header(second).getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                return "java/lang/Object";
            }
            do { first = header(first).getSuperName(); } while (!secondTypes.contains(first));
            return first;
        }
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
