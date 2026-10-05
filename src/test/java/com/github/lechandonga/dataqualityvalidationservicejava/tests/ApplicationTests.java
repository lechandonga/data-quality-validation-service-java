package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
@SpringBootTest(properties = "spring.profiles.active=test")
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
