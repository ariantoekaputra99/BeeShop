package com.bee.shop.storage;

import com.bee.shop.config.AppProperties;
import com.bee.shop.exception.BusinessException;
import com.bee.shop.exception.NotFoundException;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Menyimpan gambar produk di disk dengan aman:
 * <ul>
 *   <li>Jenis file ditentukan dari isi (magic bytes), bukan nama file atau Content-Type dari browser.</li>
 *   <li>Dimensi dicek sebelum decode penuh (mencegah "decompression bomb").</li>
 *   <li>Gambar di-decode lalu di-encode ulang: metadata EXIF (termasuk lokasi GPS) terbuang,
 *       file "polyglot" (gambar yang sekaligus script/HTML) ikut ternetralisir.</li>
 *   <li>Orientasi EXIF foto HP diterapkan agar gambar tidak miring, lalu di-resize.</li>
 *   <li>Nama file acak (UUID); nama asli dari user tidak pernah dipakai sebagai path.</li>
 * </ul>
 */
@Slf4j
@Service
public class ImageStorage {

    public static final String PUBLIC_PREFIX = "/api/images/";
    public static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$");

    static final int MAX_SOURCE_DIMENSION = 10_000;
    static final long MAX_SOURCE_PIXELS = 50_000_000L;
    static final float JPEG_QUALITY = 0.85f;

    public record StoredImage(String url, String fileName, int width, int height, long bytes) {}

    enum SourceFormat { JPEG, PNG, WEBP }

    private final Path dir;
    private final long maxBytes;
    private final int maxDimension;

    public ImageStorage(AppProperties props) throws IOException {
        AppProperties.Storage s = props.storage();
        this.dir = Paths.get(s.imageDir()).toAbsolutePath().normalize();
        this.maxBytes = s.maxImageBytes();
        this.maxDimension = s.maxImageDimension();
        Files.createDirectories(dir);
        if (!Files.isWritable(dir)) throw new IllegalStateException("Folder gambar tidak bisa ditulis: " + dir);
        log.info("Gambar produk disimpan di {}", dir);
    }

    public StoredImage store(byte[] data) {
        if (data == null || data.length == 0) throw unsupported("File kosong");
        if (data.length > maxBytes) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "Ukuran gambar maksimal " + (maxBytes / 1024 / 1024) + " MB");
        }
        SourceFormat format = detect(data);
        if (format == null) throw unsupported("Format tidak didukung. Gunakan JPG, PNG, atau WebP");

        checkDimensions(data);
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException | RuntimeException e) {
            throw unsupported("File gambar rusak atau tidak bisa dibaca");
        }
        if (image == null) throw unsupported("File gambar rusak atau tidak bisa dibaca");

        if (format == SourceFormat.JPEG) image = applyExifOrientation(image, data);
        image = resize(image, maxDimension);

        boolean transparent = image.getColorModel().hasAlpha() && hasTransparentPixel(image);
        String ext = transparent ? "png" : "jpg";
        byte[] encoded = transparent ? writePng(image) : writeJpeg(toRgb(image));

        String name = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(name);
        try {
            Path tmp = Files.createTempFile(dir, "upload-", ".tmp");
            Files.write(tmp, encoded);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Gagal menyimpan gambar ke {}", target, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal menyimpan gambar");
        }
        return new StoredImage(PUBLIC_PREFIX + name, name, image.getWidth(), image.getHeight(), encoded.length);
    }

    /** Path file untuk nama yang valid; nama di luar pola (mis. ../) selalu 404. */
    public Path resolve(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) throw new NotFoundException("Gambar tidak ditemukan");
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) throw new NotFoundException("Gambar tidak ditemukan");
        return file;
    }

    public static String contentType(String name) {
        return name.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    // ---------------------------------------------------------------

    static SourceFormat detect(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return SourceFormat.JPEG;
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) return SourceFormat.PNG;
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return SourceFormat.WEBP;
        return null;
    }

    private static void checkDimensions(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw unsupported("Format tidak didukung. Gunakan JPG, PNG, atau WebP");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_SOURCE_DIMENSION || h > MAX_SOURCE_DIMENSION || (long) w * h > MAX_SOURCE_PIXELS) {
                    throw unsupported("Resolusi gambar terlalu besar (maks " + MAX_SOURCE_DIMENSION + " px per sisi)");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw unsupported("File gambar rusak atau tidak bisa dibaca");
        }
    }

    static BufferedImage applyExifOrientation(BufferedImage img, byte[] data) {
        int orientation = 1;
        try {
            Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));
            ExifIFD0Directory exif = md.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                orientation = exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            return img; // metadata rusak: pakai gambar apa adanya
        }
        if (orientation <= 1 || orientation > 8) return img;

        int w = img.getWidth(), h = img.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }

    static BufferedImage resize(BufferedImage img, int max) {
        int w = img.getWidth(), h = img.getHeight();
        if (Math.max(w, h) <= max) return img;
        double scale = (double) max / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * scale)), nh = Math.max(1, (int) Math.round(h * scale));
        int type = img.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(nw, nh, type);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static boolean hasTransparentPixel(BufferedImage img) {
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 200);
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                if ((img.getRGB(x, y) >>> 24) < 255) return true;
            }
        }
        return false;
    }

    private static BufferedImage toRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) return img;
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    private static byte[] writeJpeg(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(img, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] writePng(BufferedImage img) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static BusinessException unsupported(String msg) {
        return new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, msg);
    }
}
