package ru.mcrpg.forgeauth.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class SpawnProtectionService {

    private static final Path CONFIG_PATH = Paths.get("config", "obsidiangate-spawn-protection.properties");
    private static final int DEFAULT_RADIUS = 96;
    private static final int MAX_RADIUS = 2048;
    private static final String HOSTILE_MARKER_CLASS = "net.minecraft.entity.monster.IMob";

    private final Logger logger;
    private volatile Config config;

    SpawnProtectionService(Logger logger) {
        this.logger = logger;
        this.config = Config.defaults();
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(event)) {
            return;
        }
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        if (canBypass(player)) {
            return;
        }
        cancel(event);
        sendMessage(player, "Spawn is protected.");
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(event)) {
            return;
        }
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        if (canBypass(player)) {
            return;
        }
        cancel(event);
        sendMessage(player, "Spawn is protected.");
    }

    @SubscribeEvent
    public void onEntityBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(event)) {
            return;
        }
        Object entity = invokeZeroArgIfPresent(event, "getEntity");
        if (canBypass(entity)) {
            return;
        }
        cancel(event);
    }

    @SubscribeEvent
    public void onMobSpawnCheck(LivingSpawnEvent.CheckSpawn event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.denyHostileSpawns || !isHostile(invokeZeroArgIfPresent(event, "getEntityLiving", "getEntity"))) {
            return;
        }
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        double x = readDouble(event, "getX");
        double z = readDouble(event, "getZ");
        if (isProtected(world, x, z, snapshot.radius)) {
            event.setResult(Result.DENY);
        }
    }

    @SubscribeEvent
    public void onExplosionStart(ExplosionEvent.Start event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.denyExplosions) {
            return;
        }
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        Object explosion = invokeZeroArgIfPresent(event, "getExplosion");
        double x = readExplosionCoordinate(explosion, 0, "explosionX", "field_77284_b");
        double z = readExplosionCoordinate(explosion, 2, "explosionZ", "field_77285_d");
        if (isProtected(world, x, z, snapshot.radius)) {
            cancel(event);
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.denyExplosions) {
            return;
        }
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        Object affectedBlocks = invokeZeroArgIfPresent(event, "getAffectedBlocks");
        if (!(affectedBlocks instanceof List<?>)) {
            return;
        }
        Iterator<?> iterator = ((List<?>) affectedBlocks).iterator();
        while (iterator.hasNext()) {
            Object pos = iterator.next();
            if (isProtected(world, pos, snapshot.radius)) {
                iterator.remove();
            }
        }
    }

    synchronized void load() {
        config = loadConfig();
        logger.info(String.format(
            "Spawn protection loaded. enabled=%s radius=%d protectBlocks=%s denyHostileSpawns=%s denyExplosions=%s",
            config.enabled,
            config.radius,
            config.protectBlocks,
            config.denyHostileSpawns,
            config.denyExplosions
        ));
    }

    synchronized void setEnabled(boolean enabled) {
        Config current = config();
        config = new Config(enabled, current.radius, current.protectBlocks, current.denyHostileSpawns, current.denyExplosions);
        save(config);
    }

    synchronized void setRadius(int radius) {
        Config current = config();
        config = new Config(current.enabled, clampRadius(radius), current.protectBlocks, current.denyHostileSpawns, current.denyExplosions);
        save(config);
    }

    Config config() {
        Config snapshot = config;
        if (snapshot == null) {
            load();
            snapshot = config;
        }
        return snapshot;
    }

    private Config loadConfig() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                properties.load(input);
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Failed to read spawn protection config. Using defaults.", exception);
            }
        }

        Config loaded = new Config(
            readBoolean(properties, "enabled", true),
            clampRadius(readInt(properties, "radius", DEFAULT_RADIUS)),
            readBoolean(properties, "protectBlocks", true),
            readBoolean(properties, "denyHostileSpawns", true),
            readBoolean(properties, "denyExplosions", true)
        );

        if (!Files.exists(CONFIG_PATH)) {
            save(loaded);
        }
        return loaded;
    }

    private void save(Config value) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(value.enabled));
        properties.setProperty("radius", Integer.toString(value.radius));
        properties.setProperty("protectBlocks", Boolean.toString(value.protectBlocks));
        properties.setProperty("denyHostileSpawns", Boolean.toString(value.denyHostileSpawns));
        properties.setProperty("denyExplosions", Boolean.toString(value.denyExplosions));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(output, "ObsidianGate spawn protection. Region is centered on the overworld /setworldspawn point.");
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Failed to write spawn protection config.", exception);
        }
    }

    private boolean isProtected(Object event) {
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        Object pos = invokeZeroArgIfPresent(event, "getPos");
        return isProtected(world, pos, config().radius);
    }

    private boolean isProtected(Object world, Object pos, int radius) {
        if (world == null || pos == null || dimension(world) != 0) {
            return false;
        }
        Object spawn = invokeZeroArgIfPresent(world, "getSpawnPoint", "func_175694_M");
        if (spawn == null) {
            return false;
        }
        double x = readBlockCoordinate(pos, "getX", "func_177958_n");
        double z = readBlockCoordinate(pos, "getZ", "func_177952_p");
        double spawnX = readBlockCoordinate(spawn, "getX", "func_177958_n");
        double spawnZ = readBlockCoordinate(spawn, "getZ", "func_177952_p");
        return Math.abs(x - spawnX) <= radius && Math.abs(z - spawnZ) <= radius;
    }

    private boolean isProtected(Object world, double x, double z, int radius) {
        if (world == null || dimension(world) != 0) {
            return false;
        }
        Object spawn = invokeZeroArgIfPresent(world, "getSpawnPoint", "func_175694_M");
        if (spawn == null) {
            return false;
        }
        double spawnX = readBlockCoordinate(spawn, "getX", "func_177958_n");
        double spawnZ = readBlockCoordinate(spawn, "getZ", "func_177952_p");
        return Math.abs(x - spawnX) <= radius && Math.abs(z - spawnZ) <= radius;
    }

    private boolean canBypass(Object entity) {
        if (entity == null) {
            return false;
        }
        Object result = invokeIfPresent(entity, new Object[] { Integer.valueOf(2), "spawnprotect" }, "canUseCommand", "func_70003_b");
        return Boolean.TRUE.equals(result);
    }

    private boolean isHostile(Object entity) {
        if (entity == null) {
            return false;
        }
        try {
            return Class.forName(HOSTILE_MARKER_CLASS).isInstance(entity);
        } catch (ClassNotFoundException ignored) {
            return entity.getClass().getName().contains(".monster.");
        }
    }

    private int dimension(Object world) {
        Object provider = readFieldIfPresent(world, "provider", "field_73011_w");
        Object value = invokeZeroArgIfPresent(provider, "getDimension", "func_186058_p");
        return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
    }

    private static void cancel(Object event) {
        invokeIfPresent(event, new Object[] { Boolean.TRUE }, "setCanceled");
    }

    private static double readDouble(Object target, String methodName) {
        Object value = invokeZeroArgIfPresent(target, methodName);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static double readExplosionCoordinate(Object explosion, int vectorIndex, String... fieldNames) {
        Object value = readFieldIfPresent(explosion, fieldNames);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        Object vector = invokeZeroArgIfPresent(explosion, "getPosition");
        if (vector == null) {
            return 0.0D;
        }
        Object[] fields = {
            readFieldIfPresent(vector, "x", "field_72450_a"),
            readFieldIfPresent(vector, "y", "field_72448_b"),
            readFieldIfPresent(vector, "z", "field_72449_c")
        };
        Object vectorValue = fields[vectorIndex];
        return vectorValue instanceof Number ? ((Number) vectorValue).doubleValue() : 0.0D;
    }

    private static double readBlockCoordinate(Object blockPos, String deobfuscatedName, String srgName) {
        Object value = invokeZeroArgIfPresent(blockPos, deobfuscatedName, srgName);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static void sendMessage(Object sender, String message) {
        if (sender == null) {
            return;
        }
        try {
            Object textComponent = Class.forName("net.minecraft.util.text.TextComponentString")
                .getConstructor(String.class)
                .newInstance(message);
            invokeIfPresent(sender, new Object[] { textComponent }, "sendMessage", "func_145747_a");
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object invokeZeroArgIfPresent(Object target, String... methodNames) {
        return invokeIfPresent(target, new Object[0], methodNames);
    }

    private static Object invokeIfPresent(Object target, Object[] args, String... methodNames) {
        if (target == null) {
            return null;
        }
        Object[] safeArgs = args == null ? new Object[0] : args;
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (methodMatches(method, safeArgs, methodNames)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Failed to invoke " + method.getName() + ".", exception);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean methodMatches(Method method, Object[] args, String... methodNames) {
        if (method.getParameterTypes().length != args.length) {
            return false;
        }
        boolean nameMatches = false;
        for (String methodName : methodNames) {
            if (methodName.equals(method.getName())) {
                nameMatches = true;
                break;
            }
        }
        if (!nameMatches) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isAssignable(parameterTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAssignable(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(value.getClass());
        }
        if (parameterType == Integer.TYPE) {
            return value instanceof Integer;
        }
        if (parameterType == Double.TYPE) {
            return value instanceof Double;
        }
        if (parameterType == Float.TYPE) {
            return value instanceof Float;
        }
        if (parameterType == Boolean.TYPE) {
            return value instanceof Boolean;
        }
        return false;
    }

    private static Object readFieldIfPresent(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int readInt(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clampRadius(int radius) {
        return Math.max(0, Math.min(MAX_RADIUS, radius));
    }

    static final class Config {
        final boolean enabled;
        final int radius;
        final boolean protectBlocks;
        final boolean denyHostileSpawns;
        final boolean denyExplosions;

        private Config(boolean enabled, int radius, boolean protectBlocks, boolean denyHostileSpawns, boolean denyExplosions) {
            this.enabled = enabled;
            this.radius = radius;
            this.protectBlocks = protectBlocks;
            this.denyHostileSpawns = denyHostileSpawns;
            this.denyExplosions = denyExplosions;
        }

        private static Config defaults() {
            return new Config(true, DEFAULT_RADIUS, true, true, true);
        }
    }
}
