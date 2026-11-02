package com.bee.shop.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menolak aplikasi start di UAT/PROD bila konfigurasi tidak aman atau tidak lengkap,
 * supaya kesalahan setting (mis. masih mock, key sandbox di prod, CORS localhost)
 * ketahuan saat deploy, bukan saat ada pelanggan bertransaksi.
 */
@Slf4j
@Component
@Profile({"uat", "prod"})
public class EnvironmentGuard implements InitializingBean {

    static final int MIN_SECRET_LENGTH = 32;
    static final int MIN_DB_PASSWORD_LENGTH = 12;

    private final AppProperties props;
    private final Environment env;

    public EnvironmentGuard(AppProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));
        List<String> problems = validate(props, env.getProperty("spring.datasource.password"), prod);
        if (!problems.isEmpty()) {
            String msg = "Konfigurasi " + (prod ? "PROD" : "UAT") + " tidak valid:\n - " + String.join("\n - ", problems);
            throw new IllegalStateException(msg);
        }
        if (notBlank(props.admin().bootstrapPassword())) {
            log.warn("ADMIN_BOOTSTRAP_PASSWORD masih di-set. Hapus dari environment setelah akun OWNER pertama dibuat.");
        }
        log.info("EnvironmentGuard: konfigurasi {} lolos validasi", prod ? "PROD" : "UAT");
    }

    static List<String> validate(AppProperties p, String dbPassword, boolean prod) {
        List<String> errors = new ArrayList<>();

        // Payment
        AppProperties.Payment pay = p.payment();
        if (!pay.useMidtrans()) errors.add("PAYMENT_PROVIDER harus 'midtrans' (mock tidak diizinkan)");
        if (!notBlank(pay.serverKey())) errors.add("MIDTRANS_SERVER_KEY wajib diisi");
        if (!notBlank(pay.clientKey())) errors.add("MIDTRANS_CLIENT_KEY wajib diisi");
        if (notBlank(pay.serverKey())) {
            boolean sandboxKey = pay.serverKey().startsWith("SB-");
            if (prod && sandboxKey) errors.add("MIDTRANS_SERVER_KEY masih key sandbox (SB-...), PROD wajib key production");
            if (!prod && !sandboxKey) errors.add("UAT wajib memakai Midtrans sandbox key (SB-...), jangan key production");
        }
        if (prod == pay.sandbox()) errors.add("Mode sandbox Midtrans tidak sesuai environment");

        // Shipping
        String ship = p.shipping().provider() == null ? "" : p.shipping().provider().toLowerCase(Locale.ROOT);
        if (ship.isBlank() || ship.equals("mock")) errors.add("SHIPPING_PROVIDER harus 'rajaongkir' atau 'biteship' (mock tidak diizinkan)");
        else if (!notBlank(p.shipping().apiKey())) errors.add("SHIPPING_API_KEY wajib diisi");

        // Integrasi & data
        if (p.integrationApiKey() == null || p.integrationApiKey().length() < MIN_SECRET_LENGTH) {
            errors.add("BEEPOS_INTEGRATION_KEY minimal " + MIN_SECRET_LENGTH + " karakter (openssl rand -hex 32)");
        }
        if (p.seedDemoData()) errors.add("SEED_DEMO_DATA harus false");
        if (dbPassword == null || dbPassword.length() < MIN_DB_PASSWORD_LENGTH || dbPassword.equals("bee123")) {
            errors.add("SPRING_DATASOURCE_PASSWORD terlalu lemah (minimal " + MIN_DB_PASSWORD_LENGTH + " karakter, bukan password lokal)");
        }

        // URL publik
        for (String origin : p.corsOrigins()) {
            if (!isPublicHttps(origin)) errors.add("CORS_ORIGINS harus https dan bukan localhost: " + origin);
        }
        if (!isPublicHttps(p.storefrontUrl())) errors.add("STOREFRONT_URL harus https dan bukan localhost: " + p.storefrontUrl());

        // Admin bootstrap
        String bootstrapPassword = p.admin().bootstrapPassword();
        if (notBlank(bootstrapPassword) && bootstrapPassword.length() < 16) {
            errors.add("ADMIN_BOOTSTRAP_PASSWORD minimal 16 karakter di " + (prod ? "PROD" : "UAT"));
        }
        return errors;
    }

    private static boolean isPublicHttps(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("https://") && !u.contains("localhost") && !u.contains("127.0.0.1");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
