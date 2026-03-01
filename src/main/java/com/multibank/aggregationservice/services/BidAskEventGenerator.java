package com.multibank.aggregationservice.services;

import com.multibank.aggregationservice.entities.Symbol;
import com.multibank.aggregationservice.enums.SymbolStatus;
import com.multibank.aggregationservice.models.BidAskEvent;
import com.multibank.aggregationservice.repositories.SymbolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "candle.generator.enabled", havingValue = "true", matchIfMissing = true)
public class BidAskEventGenerator {

    private static final Logger log = LoggerFactory.getLogger(BidAskEventGenerator.class);

    private final IngestionService ingestionService;
    private final SymbolRepository symbolRepository;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, Double> lastPrices = new ConcurrentHashMap<>();
    private volatile Map<String, Double> basePricesFromDb = Collections.emptyMap();

    public BidAskEventGenerator(
            IngestionService ingestionService,
            @Autowired(required = false) SymbolRepository symbolRepository
    ) {
        this.ingestionService = ingestionService;
        this.symbolRepository = symbolRepository;
        log.info("Event generator: symbols and base-prices from {}",
                symbolRepository != null ? "DB (SymbolRepository)" : "none (no DB)");
    }

    @Scheduled(fixedRateString = "${candle.generator.rate-ms:200}")
    public void generate() {
        List<String> symbols = getSymbols();
        if (symbols.isEmpty()) return;
        long now = Instant.now().getEpochSecond();
        for (String symbol : symbols) {
            BidAskEvent event = generateEvent(symbol, now);
            ingestionService.ingest(event);
        }
    }

    private List<String> getSymbols() {
        if (symbolRepository == null) {
            return List.of();
        }
        List<Symbol> list = symbolRepository.findByStatus(SymbolStatus.ACTIVE);
        basePricesFromDb = list.stream().collect(Collectors.toUnmodifiableMap(Symbol::getSymbol, s -> s.getBasePrice() != null ? s.getBasePrice() : 100.0));
        return list.stream().map(Symbol::getSymbol).toList();
    }

    private double getBasePrice(String symbol) {
        return basePricesFromDb.getOrDefault(symbol, 100.0);
    }

    private BidAskEvent generateEvent(String symbol, long timestamp) {
        double base    = getBasePrice(symbol);
        double lastMid = lastPrices.getOrDefault(symbol, base);

        double change = lastMid * (random.nextDouble() * 0.001 - 0.0005);
        double mid    = Math.max(lastMid + change, 0.01);
        lastPrices.put(symbol, mid);
        double halfSpread = mid * 0.0005;
        return new BidAskEvent(symbol, mid - halfSpread, mid + halfSpread, timestamp);
    }
}
