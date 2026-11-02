package com.bee.shop.service;

import com.bee.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Format: BEE-20261102-K7Q2XM. Bagian acak mencegah nomor order mudah ditebak. */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    private final SecureRandom random = new SecureRandom();
    private final OrderRepository orders;

    public String next() {
        String date = LocalDate.now(JAKARTA).format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder("BEE-").append(date).append('-');
            for (int i = 0; i < 6; i++) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            String candidate = sb.toString();
            if (!orders.existsByOrderNumber(candidate)) return candidate;
        }
        throw new IllegalStateException("Gagal membuat nomor order unik");
    }
}
