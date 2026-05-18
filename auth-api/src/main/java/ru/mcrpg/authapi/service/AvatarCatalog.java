package ru.mcrpg.authapi.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import ru.mcrpg.authapi.domain.entity.AccountEntity;

@Service
public class AvatarCatalog {

    private static final String AVATAR_PATTERN = "classpath:/minecraft_avatar_pack_256/*";
    private static final String AVATAR_LOCATION = "classpath:/minecraft_avatar_pack_256/";
    private static final String FALLBACK_AVATAR = "01_steve.png";

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final List<String> avatarFiles;

    public AvatarCatalog() {
        this.avatarFiles = loadAvatarFiles();
    }

    public String avatarFor(AccountEntity account) {
        if (avatarFiles.isEmpty()) {
            return FALLBACK_AVATAR;
        }
        int index = Math.floorMod(stableHash(account), avatarFiles.size());
        return avatarFiles.get(index);
    }

    public Optional<Resource> findAvatar(String fileName) {
        String normalized = normalizeFileName(fileName);
        if (!avatarFiles.contains(normalized)) {
            return Optional.empty();
        }
        Resource resource = resourceResolver.getResource(AVATAR_LOCATION + normalized);
        return resource.exists() && resource.isReadable() ? Optional.of(resource) : Optional.empty();
    }

    private List<String> loadAvatarFiles() {
        try {
            List<String> files = new ArrayList<>();
            for (Resource resource : resourceResolver.getResources(AVATAR_PATTERN)) {
                String fileName = normalizeFileName(resource.getFilename());
                if (resource.isReadable() && isImage(fileName)) {
                    files.add(fileName);
                }
            }
            Collections.sort(files);
            return Collections.unmodifiableList(files);
        } catch (IOException exception) {
            return Collections.emptyList();
        }
    }

    private static int stableHash(AccountEntity account) {
        if (account == null) {
            return 0;
        }
        UUID id = account.getId();
        if (id != null) {
            return id.hashCode();
        }
        String username = account.getUsername();
        return username == null ? 0 : username.toLowerCase(Locale.ROOT).hashCode();
    }

    private static boolean isImage(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".png")
            || normalized.endsWith(".jpg")
            || normalized.endsWith(".jpeg")
            || normalized.endsWith(".webp");
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.trim().replace('\\', '/').replaceAll("^.*/", "");
    }
}
