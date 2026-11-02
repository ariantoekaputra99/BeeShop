package com.bee.shop.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Gagal cepat dengan pesan jelas bila environment variable wajib belum di-set,
 * daripada error samar seperti "URL must start with 'jdbc'".
 * Didaftarkan lewat META-INF/spring.factories.
 */
public class RequiredConfigCheck implements EnvironmentPostProcessor, Ordered {

    /** property Spring -> nama environment variable yang mengisinya. */
    static final Map<String, String> REQUIRED = Map.of(
            "spring.datasource.url", "SPRING_DATASOURCE_URL",
            "spring.datasource.username", "SPRING_DATASOURCE_USERNAME",
            "spring.datasource.password", "SPRING_DATASOURCE_PASSWORD",
            "app.cors-origins", "CORS_ORIGINS",
            "app.storefront-url", "STOREFRONT_URL");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        List<String> missing = new ArrayList<>();
        REQUIRED.forEach((property, envVar) -> {
            try {
                if (env.getProperty(property) == null) missing.add(envVar);
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                missing.add(envVar);
            }
        });
        if (!missing.isEmpty()) {
            missing.sort(null);
            String[] profiles = env.getActiveProfiles().length > 0 ? env.getActiveProfiles() : env.getDefaultProfiles();
            throw new IllegalStateException("Environment variable wajib belum di-set untuk profile "
                    + Arrays.toString(profiles) + ": " + String.join(", ", missing)
                    + ". Lihat env/<profile>.env.example. Untuk lokal, env/local.env dibaca otomatis.");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
