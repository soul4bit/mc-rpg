package ru.mcrpg.authapi.web;

import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.mcrpg.authapi.service.AvatarCatalog;

@RestController
public class AvatarController {

    private final AvatarCatalog avatarCatalog;

    public AvatarController(AvatarCatalog avatarCatalog) {
        this.avatarCatalog = avatarCatalog;
    }

    @GetMapping("/avatars/{fileName:.+}")
    public ResponseEntity<Resource> avatar(@PathVariable String fileName) {
        return avatarCatalog.findAvatar(fileName)
            .map(resource -> ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(resource))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
