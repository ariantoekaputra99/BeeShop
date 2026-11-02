package com.bee.shop.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final AuthService auth;

    @Override
    public void run(ApplicationArguments args) {
        auth.bootstrapOwnerIfEmpty();
    }
}
