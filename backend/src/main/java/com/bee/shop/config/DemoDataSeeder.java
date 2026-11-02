package com.bee.shop.config;

import com.bee.shop.entity.Product;
import com.bee.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Mengisi katalog contoh bila database masih kosong dan SEED_DEMO_DATA=true. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final ProductRepository products;
    private final AppProperties props;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seedDemoData() || products.count() > 0) return;
        products.saveAll(List.of(
                p("Beras Premium 5kg", "BR-005", 72000, 24, 5000, "Sembako", """
                        Beras putih kualitas premium dengan tekstur pulen dan wangi alami, cocok untuk makan sehari-hari keluarga.

                        Keunggulan:
                        - Butiran utuh, minim patahan
                        - Tanpa pemutih dan pewangi buatan
                        - Dikemas dalam karung 5 kg dengan zipper

                        Cara penyimpanan:
                        1. Simpan di tempat kering dan sejuk
                        2. Tutup rapat setelah dibuka
                        3. Baik dikonsumsi maksimal 6 bulan setelah dibuka"""),
                p("Minyak Goreng 2L", "MG-002", 34000, 16, 1900, "Sembako", "Minyak goreng sawit kemasan pouch."),
                p("Gula Pasir 1kg", "GP-001", 17500, 40, 1000, "Sembako", "Gula pasir putih kristal."),
                p("Telur Ayam 1kg", "TL-001", 29000, 12, 1100, "Sembako", "Telur ayam negeri segar."),
                p("Coca Cola 1L", "CC-001", 18000, 42, 1100, "Minuman", "Minuman berkarbonasi."),
                p("Teh Botol 450ml", "TB-450", 6000, 60, 500, "Minuman", "Teh melati siap minum."),
                p("Kopi Bubuk 200g", "KP-200", 26000, 18, 220, "Minuman", """
                        Kopi robusta pilihan dari dataran tinggi Lampung, disangrai medium dan digiling halus.

                        Profil rasa:
                        - Body tebal dengan pahit yang bersih
                        - Aroma cokelat dan kacang
                        - Cocok untuk kopi tubruk atau dicampur susu

                        Penyajian: seduh 1–2 sendok makan dengan 150 ml air panas (90–95°C), diamkan 3 menit."""),
                p("Mi Instan Goreng (5 pcs)", "MI-005", 15500, 30, 450, "Makanan", "Paket 5 bungkus mi goreng."),
                p("Sabun Mandi Cair 450ml", "SB-450", 24000, 4, 500, "Perawatan", "Sabun cair aroma segar."),
                p("Deterjen Bubuk 800g", "DT-800", 21000, 20, 850, "Rumah Tangga", "Deterjen untuk mesin & tangan.")
        ));
        log.info("Data demo katalog ditambahkan");
    }

    private static Product p(String name, String sku, long price, int stock, int weight, String category, String desc) {
        return Product.builder().name(name).sku(sku).price(BigDecimal.valueOf(price)).stock(stock)
                .weightGram(weight).category(category).description(desc).active(true).build();
    }
}
