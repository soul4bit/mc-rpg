package ru.mcrpg.launcher.ui;

import java.util.Locale;
import java.util.UUID;
import javafx.scene.image.Image;
import ru.mcrpg.launcher.AuthAccount;

public final class AvatarImages {

    private static final String FALLBACK_AVATAR = "/ru/mcrpg/launcher/assets/player-avatar.png";
    private static final String AVATAR_PACK_LOCATION = "/ru/mcrpg/launcher/assets/minecraft_avatar_pack_256/";
    private static final String[] AVATAR_PACK = {
        "01_steve.png",
        "02_alex.png",
        "03_villager.png",
        "04_zombie.png",
        "05_skeleton.png",
        "06_creeper.png",
        "07_enderman.png",
        "08_witch.png",
        "09_piglin.png",
        "10_wither_skeleton.png",
        "11_iron_golem.png",
        "12_wolf.png",
        "13_cat.png",
        "14_fox.png",
        "15_panda.png",
        "16_bee.png",
        "17_pig.png",
        "18_cow.png",
        "19_sheep.png",
        "20_warden.png"
    };

    private AvatarImages() {
    }

    public static Image forAccount(AuthAccount account) {
        if (account != null) {
            Image localAvatar = localAvatar(account);
            if (localAvatar != null) {
                return localAvatar;
            }
            if (hasText(account.getAvatarUrl())) {
                return new Image(account.getAvatarUrl(), true);
            }
        }
        return fallback();
    }

    public static Image fallback() {
        return new Image(AvatarImages.class.getResource(FALLBACK_AVATAR).toExternalForm());
    }

    private static Image localAvatar(AuthAccount account) {
        String avatar = normalizeAvatarFile(account.getAvatar());
        if (!hasText(avatar)) {
            avatar = AVATAR_PACK[Math.floorMod(stableHash(account), AVATAR_PACK.length)];
        }
        if (!isKnownAvatar(avatar)) {
            return null;
        }
        return resourceImage(AVATAR_PACK_LOCATION + avatar);
    }

    private static Image resourceImage(String path) {
        var resource = AvatarImages.class.getResource(path);
        return resource == null ? null : new Image(resource.toExternalForm());
    }

    private static boolean isKnownAvatar(String avatar) {
        for (String knownAvatar : AVATAR_PACK) {
            if (knownAvatar.equals(avatar)) {
                return true;
            }
        }
        return false;
    }

    private static int stableHash(AuthAccount account) {
        if (account == null) {
            return 0;
        }
        if (hasText(account.getId())) {
            try {
                return UUID.fromString(account.getId()).hashCode();
            } catch (IllegalArgumentException ignored) {
                return account.getId().toLowerCase(Locale.ROOT).hashCode();
            }
        }
        return hasText(account.getUsername()) ? account.getUsername().toLowerCase(Locale.ROOT).hashCode() : 0;
    }

    private static String normalizeAvatarFile(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.trim().replace('\\', '/').replaceAll("^.*/", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
