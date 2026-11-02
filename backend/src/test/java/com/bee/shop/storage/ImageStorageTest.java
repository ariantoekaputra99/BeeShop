package com.bee.shop.storage;

import com.bee.shop.config.AppProperties;
import com.bee.shop.exception.BusinessException;
import com.bee.shop.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageStorageTest {

    @TempDir Path dir;
    ImageStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new ImageStorage(props(dir, 5 * 1024 * 1024));
    }

    static AppProperties props(Path dir, long maxBytes) {
        return new AppProperties(List.of("http://localhost"), "http://localhost", null,
                new AppProperties.Admin(null, null, 12, 5, 15, 20), false,
                new AppProperties.Order(60, 60000),
                new AppProperties.Payment("mock", null, null, true),
                new AppProperties.Shipping("mock", null, "Jakarta", null, null, List.of("jne"), 10),
                new AppProperties.Storage(dir.toString(), maxBytes, 1600));
    }

    static byte[] fixture(String name) throws IOException {
        try (InputStream in = ImageStorageTest.class.getResourceAsStream("/images/" + name)) {
            return in.readAllBytes();
        }
    }

    BufferedImage readStored(ImageStorage.StoredImage s) throws IOException {
        return ImageIO.read(storage.resolve(s.fileName()).toFile());
    }

    @Test
    void jpegWithExifOrientationIsRotatedUpright() throws IOException {
        var stored = storage.store(fixture("orient6.jpg"));
        assertThat(stored.fileName()).endsWith(".jpg");
        assertThat(stored.url()).isEqualTo("/api/images/" + stored.fileName());
        // 40x20 dengan orientasi 6 -> tampil 20x40, sisi kiri (merah) jadi atas
        assertThat(stored.width()).isEqualTo(20);
        assertThat(stored.height()).isEqualTo(40);
        BufferedImage img = readStored(stored);
        assertThat(red(img.getRGB(10, 5))).isGreaterThan(200);
        assertThat(blue(img.getRGB(10, 35))).isGreaterThan(200);

        // orientasi 8 (rotate 270): sisi kiri (merah) jadi bawah
        BufferedImage img8 = readStored(storage.store(fixture("orient8.jpg")));
        assertThat(red(img8.getRGB(10, 35))).isGreaterThan(200);
    }

    @Test
    void exifMetadataIsStripped() throws IOException {
        byte[] original = fixture("with-exif.jpg");
        assertThat(new String(original, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("SecretCam");
        var stored = storage.store(original);
        byte[] saved = Files.readAllBytes(storage.resolve(stored.fileName()));
        assertThat(new String(saved, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("SecretCam");
    }

    @Test
    void transparentImagesStayPngOpaqueWebpBecomesJpeg() throws IOException {
        assertThat(storage.store(fixture("alpha.png")).fileName()).endsWith(".png");
        assertThat(storage.store(fixture("alpha.webp")).fileName()).endsWith(".png");
        assertThat(storage.store(fixture("opaque.webp")).fileName()).endsWith(".jpg");
    }

    @Test
    void largeImageIsResized() throws IOException {
        BufferedImage big = new BufferedImage(4000, 2000, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(big, "png", out);
        var stored = storage.store(out.toByteArray());
        assertThat(stored.width()).isEqualTo(1600);
        assertThat(stored.height()).isEqualTo(800);
    }

    @Test
    void rejectsNonImagesFakesAndOversize() throws IOException {
        assertThatThrownBy(() -> storage.store("<html>bukan gambar</html>".getBytes()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Format tidak didukung");
        assertThatThrownBy(() -> storage.store(fixture("fake.png")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("rusak");
        assertThatThrownBy(() -> storage.store(new byte[0])).isInstanceOf(BusinessException.class);

        ImageStorage tiny = new ImageStorage(props(dir, 100));
        assertThatThrownBy(() -> tiny.store(fixture("orient6.jpg")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("maksimal");
    }

    @Test
    void rejectsDecompressionBomb() throws IOException {
        // PNG 20000x20000 hitam: kecil di disk, raksasa di memori
        BufferedImage header = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(header, "png", out);
        byte[] png = out.toByteArray();
        // tulis ulang lebar/tinggi di chunk IHDR (offset 16..23) lalu hitung ulang CRC
        java.nio.ByteBuffer.wrap(png, 16, 8).putInt(20_000).putInt(20_000);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(png, 12, 17);
        java.nio.ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
        assertThatThrownBy(() -> storage.store(png))
                .isInstanceOf(BusinessException.class).hasMessageContaining("terlalu besar");
    }

    @Test
    void resolveRejectsTraversalAndUnknownNames() {
        assertThatThrownBy(() -> storage.resolve("../application.yml")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> storage.resolve("..%2F..%2Fetc%2Fpasswd")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> storage.resolve("00000000-0000-0000-0000-000000000000.jpg")).isInstanceOf(NotFoundException.class);
    }

    static int red(int rgb) { return (rgb >> 16) & 0xFF; }
    static int blue(int rgb) { return rgb & 0xFF; }
}
