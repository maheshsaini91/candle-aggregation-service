package com.multibank.aggregationservice.models;

public record CandleKey(
        String symbol,
        long intervalSec,
        long bucketStart
) {}
