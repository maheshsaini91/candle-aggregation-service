package com.multibank.aggregationservice.controllers;

import com.multibank.aggregationservice.dtos.HistoryResponse;
import com.multibank.aggregationservice.exceptions.InvalidRequestException;
import com.multibank.aggregationservice.services.CandleAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class HistoryController {
    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);
    private static final long MAX_RANGE_SECONDS = 7 * 24 * 3600L;

    private final CandleAggregationService aggregationService;

    public HistoryController(CandleAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @GetMapping(value = { "/history", "/v1/history" })
    public ResponseEntity<HistoryResponse> getHistory(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam long from,
            @RequestParam long to
    ) {
        log.info("History request: symbol={} interval={} from={} to={}", symbol, interval, from, to);
        validateRequest(symbol, interval, from, to);
        return ResponseEntity.ok(aggregationService.getHistory(symbol, interval, from, to));
    }

    private void validateRequest(String symbol, String interval, long from, long to) {
        if (symbol == null || symbol.isBlank())
            throw new InvalidRequestException("symbol is required");
        if (interval == null || interval.isBlank())
            throw new InvalidRequestException("interval is required");
        if (from <= 0)
            throw new InvalidRequestException("from must be a positive UNIX timestamp in seconds");
        if (to <= 0)
            throw new InvalidRequestException("to must be a positive UNIX timestamp in seconds");
        if (from > to)
            throw new InvalidRequestException("from must not be after to");
        if ((to - from) > MAX_RANGE_SECONDS)
            throw new InvalidRequestException(
                    "Range exceeds 7-day maximum. Requested: " + (to - from) + "s");
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<HistoryResponse> handleInvalid(InvalidRequestException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<HistoryResponse> handleIllegal(IllegalArgumentException ex) {
        log.warn("Bad argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
    }

    private HistoryResponse errorResponse(String message) {
        return new HistoryResponse(
                "error: " + message,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
