package com.example.voicenotes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VoicenotesApplicationTests {

    @Test
    void contextLoads() {
        // Starts the whole app against a real DB: Flyway migrates, Hibernate validates the schema.
    }
}
