package com.bee.shop.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class ImageController {

    private final ImageStorage storage;

    /** Upload gambar produk (admin). Field multipart: {@code file}. */
    @PostMapping(path = "/api/admin/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageStorage.StoredImage upload(@RequestParam("file") MultipartFile file) throws IOException {
        return storage.store(file.getBytes());
    }

    /** Gambar publik. Nama file berisi UUID dan isinya tidak pernah berubah, jadi aman di-cache lama. */
    @GetMapping("/api/images/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        Path file = storage.resolve(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ImageStorage.contentType(name)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(new FileSystemResource(file));
    }
}
