package org.threadlys.digest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.threadlys.utils.configuration.CommonsUtilsSpringConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.threadlys.configuration.CommonsThreadingSpringConfig;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration
class ThreadLocalMessageDigestTest {
    @EnableAsync
    @Configuration
    @Import({ CommonsThreadingSpringConfig.class, CommonsUtilsSpringConfig.class })
    static class ContextConfiguration {
    }

    @Autowired
    ThreadLocalMessageDigest messageDigest;

    @Test
    void testDigestSHA256() {
        // GIVEN
        var data = "hello";

        // WHEN
        var digest = messageDigest.digestSHA256(data.getBytes(StandardCharsets.UTF_8));

        // THEN
        var base64digest = Base64.getEncoder()
                .encodeToString(digest);

        assertThat(base64digest).isEqualTo("LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=");
    }

    @Test
    void testDigestSHA512() {
        // GIVEN
        var data = "hello";

        // WHEN
        var digest = messageDigest.digestSHA512(data.getBytes(StandardCharsets.UTF_8));

        // THEN
        var base64digest = Base64.getEncoder()
                .encodeToString(digest);

        assertThat(base64digest).isEqualTo("m3HSJL1i83hdltRq0+o9czGb+8KJDKra4t/3JRlnPKcjI8PZm6XBHXx6zG4UuMXaDEZjR1wuXDre9G9zvN7AQw==");
    }
}
