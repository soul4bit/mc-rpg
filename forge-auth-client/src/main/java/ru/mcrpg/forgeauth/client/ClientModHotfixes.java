package ru.mcrpg.forgeauth.client;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.fml.common.Loader;

final class ClientModHotfixes {

    private static final String ANIMANIA_MOD_ID = "animania";
    private static final String ANIMANIA_ITEM_HANDLER_CLASS = "com.animania.common.handler.ItemHandler";
    private static final String ANIMANIA_EGG_COLOR_FLAG = "hasSetEggColors";

    private ClientModHotfixes() {
    }

    static void apply(Logger logger) {
        applyAnimaniaEggColorHotfix(logger);
    }

    private static void applyAnimaniaEggColorHotfix(Logger logger) {
        if (!Loader.isModLoaded(ANIMANIA_MOD_ID)) {
            return;
        }

        try {
            Class<?> itemHandlerClass = Class.forName(ANIMANIA_ITEM_HANDLER_CLASS);
            Field eggColorFlag = itemHandlerClass.getDeclaredField(ANIMANIA_EGG_COLOR_FLAG);
            eggColorFlag.setAccessible(true);

            if (!eggColorFlag.getBoolean(null)) {
                eggColorFlag.setBoolean(null, true);
                logger.warning(
                    "Applied Animania egg color hotfix. Dynamic Animania spawn egg colors are disabled to avoid a known client crash on join."
                );
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            logger.log(Level.WARNING, "Failed to apply Animania egg color hotfix.", exception);
        }
    }
}
