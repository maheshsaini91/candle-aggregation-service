package com.multibank.aggregationservice;

import com.multibank.aggregationservice.models.Candle;
import com.multibank.aggregationservice.models.CandleKey;
import com.multibank.aggregationservice.repositories.CandleRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for CandleRepository (JPA implementation) against real PostgreSQL (Testcontainers).
 * Verifies finalize, findFinalized range, flushAllActive, and finalizedCandleCount.
 * Requires Docker. Run with: mvn test -Dtest=CandleRepositoryIntegrationTest
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "candle.generator.enabled=false")
@ActiveProfiles("db")
class CandleRepositoryIntegrationTest {

    private static final String SYMBOL = "BTC-USD";
    private static final long INTERVAL_SEC = 60L;
    private static final long BUCKET_1 = 1620000000L;
    private static final long BUCKET_2 = 1620000060L;

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("aggregation")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CandleRepository repository;

    @Test
    @DisplayName("Finalize writes to DB; findFinalized returns the candle")
    void finalizeAndFind() {
        CandleKey key = new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_1);
        repository.updateActiveCandle(key, 29505.0);
        repository.updateActiveCandle(key, 29490.0);
        repository.updateActiveCandle(key, 29520.0);

        repository.finalizeCandle(key);

        assertThat(repository.getActiveSnapshot(key)).isNull();
        assertThat(repository.activeCandleCount()).isZero();

        List<Candle> list = repository.findFinalized(SYMBOL, INTERVAL_SEC, BUCKET_1, BUCKET_1 + 59);
        assertThat(list).hasSize(1);
        Candle c = list.get(0);
        assertThat(c.time()).isEqualTo(BUCKET_1);
        assertThat(c.open()).isEqualTo(29505.0);
        assertThat(c.high()).isEqualTo(29520.0);
        assertThat(c.low()).isEqualTo(29490.0);
        assertThat(c.close()).isEqualTo(29520.0);
        assertThat(c.volume()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Idempotent finalize: second finalize overwrites with same key")
    void idempotentFinalize() {
        CandleKey key = new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_1);
        repository.updateActiveCandle(key, 100.0);
        repository.finalizeCandle(key);

        repository.updateActiveCandle(key, 200.0);
        repository.finalizeCandle(key);

        List<Candle> list = repository.findFinalized(SYMBOL, INTERVAL_SEC, BUCKET_1, BUCKET_1 + 59);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).close()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("findFinalized returns only candles in range; multiple buckets")
    void findFinalizedRange() {
        repository.updateActiveCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_1), 100.0);
        repository.finalizeCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_1));
        repository.updateActiveCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_2), 101.0);
        repository.finalizeCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_2));

        List<Candle> list = repository.findFinalized(SYMBOL, INTERVAL_SEC, BUCKET_1, BUCKET_2 + 59);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).time()).isEqualTo(BUCKET_1);
        assertThat(list.get(1).time()).isEqualTo(BUCKET_2);
    }

    @Test
    @DisplayName("flushAllActive finalizes all open candles to DB")
    void flushAllActive() {
        repository.updateActiveCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_1), 100.0);
        repository.updateActiveCandle(new CandleKey("ETH-USD", INTERVAL_SEC, BUCKET_1), 200.0);

        repository.flushAllActive();

        assertThat(repository.activeCandleCount()).isZero();
        assertThat(repository.findFinalized(SYMBOL, INTERVAL_SEC, BUCKET_1, BUCKET_1 + 59)).hasSize(1);
        assertThat(repository.findFinalized("ETH-USD", INTERVAL_SEC, BUCKET_1, BUCKET_1 + 59)).hasSize(1);
    }

    @Test
    @DisplayName("finalizedCandleCount returns DB row count")
    void finalizedCandleCount() {
        assertThat(repository.finalizedCandleCount()).isZero();

        repository.updateActiveCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_1), 100.0);
        repository.finalizeCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_1));
        assertThat(repository.finalizedCandleCount()).isEqualTo(1);

        repository.updateActiveCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_2), 101.0);
        repository.finalizeCandle(new CandleKey(SYMBOL, INTERVAL_SEC, BUCKET_2));
        assertThat(repository.finalizedCandleCount()).isEqualTo(2);
    }
}
