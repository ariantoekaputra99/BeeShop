package com.bee.shop;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ImageUploadApiTest extends TestSupport {

    static MockMultipartFile file(String fixture, String declaredType) throws Exception {
        try (InputStream in = ImageUploadApiTest.class.getResourceAsStream("/images/" + fixture)) {
            return new MockMultipartFile("file", fixture, declaredType, in.readAllBytes());
        }
    }

    @Test
    void adminUploadsImageAndItIsServedPublicly() throws Exception {
        String token = bearer(ownerToken());
        JsonNode res = json.readTree(mvc.perform(multipart("/api/admin/uploads/images")
                        .file(file("orient6.jpg", "image/jpeg")).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(20))
                .andReturn().getResponse().getContentAsString());
        String url = res.get("url").asText();
        assertThat(url).matches("/api/images/[0-9a-f-]{36}\\.jpg");

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));

        // Gambar dipakai di produk
        mvc.perform(post("/api/admin/products").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Teh Hijau", "sku", "TH-001", "price", 12000,
                                "stock", 5, "weightGram", 250, "imageUrl", url))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(url));
    }

    @Test
    void uploadRequiresLogin() throws Exception {
        mvc.perform(multipart("/api/admin/uploads/images").file(file("orient6.jpg", "image/jpeg")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lyingContentTypeAndFakeImagesAreRejected() throws Exception {
        String token = bearer(ownerToken());
        MockMultipartFile html = new MockMultipartFile("file", "evil.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes());
        mvc.perform(multipart("/api/admin/uploads/images").file(html).header("Authorization", token))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/admin/uploads/images").file(file("fake.png", "image/png")).header("Authorization", token))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/admin/uploads/images").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productImageUrlMustBeUploadOrHttps() throws Exception {
        String token = bearer(ownerToken());
        for (String bad : new String[]{"javascript:alert(1)", "http://insecure.example/a.jpg", "/api/images/../../x.jpg"}) {
            mvc.perform(post("/api/admin/products").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", "X", "sku", "X-" + Math.abs(bad.hashCode()),
                                    "price", 1000, "stock", 1, "weightGram", 100, "imageUrl", bad))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.imageUrl").exists());
        }
    }

    @Test
    void unknownOrTraversalImagePathIs404() throws Exception {
        mvc.perform(get("/api/images/00000000-0000-0000-0000-000000000000.jpg")).andExpect(status().isNotFound());
        mvc.perform(get("/api/images/application.yml")).andExpect(status().isNotFound());
    }
}
