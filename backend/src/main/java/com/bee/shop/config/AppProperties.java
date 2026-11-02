package com.bee.shop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:5173") List<String> corsOrigins,
        @DefaultValue("http://localhost:5173") String storefrontUrl,
        String integrationApiKey,
        @DefaultValue Admin admin,
        @DefaultValue("false") boolean seedDemoData,
        @DefaultValue Order order,
        @DefaultValue Payment payment,
        @DefaultValue Shipping shipping,
        @DefaultValue Storage storage
) {
    public record Storage(
            /** Folder penyimpanan gambar produk. Di server, arahkan ke volume persisten. */
            @DefaultValue("./data/images") String imageDir,
            /** Batas ukuran file upload (byte). Selaraskan dengan spring.servlet.multipart.max-file-size. */
            @DefaultValue("5242880") long maxImageBytes,
            /** Sisi terpanjang setelah resize (px). */
            @DefaultValue("1600") int maxImageDimension
    ) {}

    public record Admin(
            /** Dipakai sekali saat tabel admin masih kosong untuk membuat akun OWNER pertama. */
            String bootstrapUsername,
            String bootstrapPassword,
            @DefaultValue("12") int sessionHours,
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15") int lockMinutes,
            /** Maks percobaan login per IP dalam 15 menit. */
            @DefaultValue("20") int loginAttemptsPerIp
    ) {}

    public record Order(
            @DefaultValue("60") int paymentTimeoutMinutes,
            @DefaultValue("60000") long expiryCheckIntervalMs
    ) {}

    public record Payment(
            String provider,
            String serverKey,
            String clientKey,
            @DefaultValue("true") boolean sandbox
    ) {
        public boolean useMidtrans() {
            if ("mock".equalsIgnoreCase(provider)) return false;
            return "midtrans".equalsIgnoreCase(provider) || (serverKey != null && !serverKey.isBlank());
        }
    }

    public record Shipping(
            @DefaultValue("mock") String provider,
            String apiKey,
            /** Nama kota asal, dipakai provider mock. */
            @DefaultValue("Jakarta") String originCity,
            /** Kode pos gudang/toko, wajib untuk Biteship. */
            String originPostalCode,
            /** ID destinasi asal dari RajaOngkir (endpoint destination/domestic-destination). */
            String originId,
            @DefaultValue({"jne", "sicepat", "jnt", "anteraja"}) List<String> couriers,
            /** Lama cache hasil cek ongkir agar checkout tidak memanggil API dua kali. */
            @DefaultValue("10") int cacheTtlMinutes
    ) {}
}
