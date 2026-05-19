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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class SpawnProtectionService {

    private static final Path CONFIG_PATH = Paths.get("config", "obsidiangate-spawn-protection.properties");
    private static final int DEFAULT_RADIUS = 96;
    private static final int MAX_RADIUS = 2048;
    private static final String HOSTILE_MARKER_CLASS = "net.minecraft.entity.monster.IMob";
    private static final String SUBJECT = "Защита спавна";
    static final String CENTER_MODE_WORLDSPAWN = "worldspawn";
    static final String CENTER_MODE_FIXED = "fixed";

    private final Logger logger;
    private volatile Config config;

    SpawnProtectionService(Logger logger) {
        this.logger = logger;
        this.config = Config.defaults();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(event, snapshot)) {
            return;
        }
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        if (canBypass(player, snapshot)) {
            return;
        }
        cancel(event);
        event.setExpToDrop(0);
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ломать блоки здесь нельзя.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(event, snapshot)) {
            return;
        }
        Object player = invokeZeroArgIfPresent(event, "getEntityPlayer", "getEntity");
        if (canBypass(player, snapshot)) {
            return;
        }
        denyInteraction(event);
        cancel(event);
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ломать блоки здесь нельзя.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerBreakSpeed(PlayerEvent.BreakSpeed event) {
        Config snapshot = config();
        Object player = invokeZeroArgIfPresent(event, "getEntityPlayer", "getEntity");
        Object world = readFieldIfPresent(player, "world", "field_70170_p");
        Object pos = invokeZeroArgIfPresent(event, "getPos");
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(world, pos, snapshot)) {
            return;
        }
        if (canBypass(player, snapshot)) {
            return;
        }
        invokeIfPresent(event, new Object[] { Float.valueOf(0.0F) }, "setNewSpeed");
        cancel(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(event, snapshot)) {
            return;
        }
        Object player = invokeZeroArgIfPresent(event, "getPlayer");
        if (canBypass(player, snapshot)) {
            return;
        }
        cancel(event);
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "ставить блоки здесь нельзя.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(event, snapshot)) {
            return;
        }
        Object entity = invokeZeroArgIfPresent(event, "getEntity");
        if (canBypass(entity, snapshot)) {
            return;
        }
        cancel(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Config snapshot = config();
        if (!snapshot.enabled || !snapshot.protectBlocks || !isProtected(event, snapshot)) {
            return;
        }
        Object player = invokeZeroArgIfPresent(event, "getEntityPlayer", "getEntity");
        if (canBypass(player, snapshot)) {
            return;
        }
        denyInteraction(event);
        cancel(event);
        ServerChat.status(player, ServerChat.Tone.ERROR, SUBJECT, "взаимодействие с этим блоком закрыто.");
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
        if (isProtected(world, x, z, snapshot)) {
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
        if (isProtected(world, x, z, snapshot)) {
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
            if (isProtected(world, pos, snapshot)) {
                iterator.remove();
            }
        }
    }

    synchronized void load() {
        config = loadConfig();
        logger.info(String.format(
            "Защита спавна загружена. enabled=%s radius=%d protectBlocks=%s denyHostileSpawns=%s denyExplosions=%s allowOperatorBypass=%s",
            config.enabled,
            config.radius,
            config.protectBlocks,
            config.denyHostileSpawns,
            config.denyExplosions,
            config.allowOperatorBypass
        ));
    }

    synchronized void setEnabled(boolean enabled) {
        Config current = config();
        config = new Config(
            enabled,
            current.radius,
            current.centerMode,
            current.dimension,
            current.centerX,
            current.centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass
        );
        save(config);
    }

    synchronized void setRadius(int radius) {
        Config current = config();
        config = new Config(
            current.enabled,
            clampRadius(radius),
            current.centerMode,
            current.dimension,
            current.centerX,
            current.centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass
        );
        save(config);
    }

    synchronized void setFixedCenter(int dimension, double centerX, double centerZ) {
        Config current = config();
        config = new Config(
            current.enabled,
            current.radius,
            CENTER_MODE_FIXED,
            dimension,
            centerX,
            centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass
        );
        save(config);
    }

    synchronized void useWorldSpawnCenter() {
        Config current = config();
        config = new Config(
            current.enabled,
            current.radius,
            CENTER_MODE_WORLDSPAWN,
            current.dimension,
            current.centerX,
            current.centerZ,
            current.protectBlocks,
            current.denyHostileSpawns,
            current.denyExplosions,
            current.allowOperatorBypass
        );
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
                logger.log(Level.WARNING, "Не удалось прочитать конфиг защиты спавна. Используем значения по умолчанию.", exception);
            }
        }

        Config loaded = new Config(
            readBoolean(properties, "enabled", true),
            clampRadius(readInt(properties, "radius", DEFAULT_RADIUS)),
            readCenterMode(properties),
            readInt(properties, "dimension", 0),
            readDouble(properties, "centerX", 0.0D),
            readDouble(properties, "centerZ", 0.0D),
            readBoolean(properties, "protectBlocks", true),
            readBoolean(properties, "denyHostileSpawns", true),
            readBoolean(properties, "denyExplosions", true),
            readBoolean(properties, "allowOperatorBypass", false)
        );

        if (!Files.exists(CONFIG_PATH) || !properties.containsKey("allowOperatorBypass") || !properties.containsKey("centerMode")) {
            save(loaded);
        }
        return loaded;
    }

    private void save(Config value) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(value.enabled));
        properties.setProperty("radius", Integer.toString(value.radius));
        properties.setProperty("centerMode", value.centerMode);
        properties.setProperty("dimension", Integer.toString(value.dimension));
        properties.setProperty("centerX", Double.toString(value.centerX));
        properties.setProperty("centerZ", Double.toString(value.centerZ));
        properties.setProperty("protectBlocks", Boolean.toString(value.protectBlocks));
        properties.setProperty("denyHostileSpawns", Boolean.toString(value.denyHostileSpawns));
        properties.setProperty("denyExplosions", Boolean.toString(value.denyExplosions));
        properties.setProperty("allowOperatorBypass", Boolean.toString(value.allowOperatorBypass));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(output, "Защита спавна ObsidianGate. Регион центрируется по точке /setworldspawn в overworld.");
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Не удалось записать конфиг защиты спавна.", exception);
        }
    }

    private boolean isProtected(Object event, Config snapshot) {
        Object world = invokeZeroArgIfPresent(event, "getWorld");
        Object pos = invokeZeroArgIfPresent(event, "getPos");
        return isProtected(world, pos, snapshot);
    }

    private boolean isProtected(Object world, Object pos, Config snapshot) {
        if (world == null || pos == null || dimension(world) != snapshot.dimension) {
            return false;
        }
        Center center = protectedCenter(world, snapshot);
        if (center == null) {
            return false;
        }
        double x = readBlockCoordinate(pos, "getX", "func_177958_n");
        double z = readBlockCoordinate(pos, "getZ", "func_177952_p");
        return Math.abs(x - center.x) <= snapshot.radius && Math.abs(z - center.z) <= snapshot.radius;
    }

    private boolean isProtected(Object world, double x, double z, Config snapshot) {
        if (world == null || dimension(world) != snapshot.dimension) {
            return false;
        }
        Center center = protectedCenter(world, snapshot);
        if (center == null) {
            return false;
        }
        return Math.abs(x - center.x) <= snapshot.radius && Math.abs(z - center.z) <= snapshot.radius;
    }

    private Center protectedCenter(Object world, Config snapshot) {
        if (CENTER_MODE_FIXED.equals(snapshot.centerMode)) {
            return new Center(snapshot.centerX, snapshot.centerZ);
        }

        Object spawn = invokeZeroArgIfPresent(world, "getSpawnPoint", "func_175694_M");
        if (spawn == null) {
            return null;
        }
        return new Center(
            readBlockCoordinate(spawn, "getX", "func_177958_n"),
            readBlockCoordinate(spawn, "getZ", "func_177952_p")
        );
    }

    private boolean canBypass(Object entity, Config snapshot) {
        if (entity == null || !snapshot.allowOperatorBypass) {
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

    private static void denyInteraction(Object event) {
        invokeIfPresent(event, new Object[] { Result.DENY }, "setUseBlock");
        invokeIfPresent(event, new Object[] { Result.DENY }, "setUseItem");
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
                        throw new IllegalStateException("Не удалось вызвать " + method.getName() + ".", exception);
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

    private static double readDouble(Properties properties, String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String readCenterMode(Properties properties) {
        String value = properties.getProperty("centerMode", CENTER_MODE_WORLDSPAWN).trim().toLowerCase();
        return CENTER_MODE_FIXED.equals(value) ? CENTER_MODE_FIXED : CENTER_MODE_WORLDSPAWN;
    }

    private static int clampRadius(int radius) {
        return Math.max(0, Math.min(MAX_RADIUS, radius));
    }

    static final class Config {
        final boolean enabled;
        final int radius;
        final String centerMode;
        final int dimension;
        final double centerX;
        final double centerZ;
        final boolean protectBlocks;
        final boolean denyHostileSpawns;
        final boolean denyExplosions;
        final boolean allowOperatorBypass;

        private Config(
            boolean enabled,
            int radius,
            String centerMode,
            int dimension,
            double centerX,
            double centerZ,
            boolean protectBlocks,
            boolean denyHostileSpawns,
            boolean denyExplosions,
            boolean allowOperatorBypass
        ) {
            this.enabled = enabled;
            this.radius = radius;
            this.centerMode = centerMode;
            this.dimension = dimension;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.protectBlocks = protectBlocks;
            this.denyHostileSpawns = denyHostileSpawns;
            this.denyExplosions = denyExplosions;
            this.allowOperatorBypass = allowOperatorBypass;
        }

        private static Config defaults() {
            return new Config(true, DEFAULT_RADIUS, CENTER_MODE_WORLDSPAWN, 0, 0.0D, 0.0D, true, true, true, false);
        }
    }

    private static final class Center {
        private final double x;
        private final double z;

        private Center(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}
