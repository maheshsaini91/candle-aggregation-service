package com.multibank.aggregationservice.models;

public record Candle(
        long time,
        double open,
        double high,
        double low,
        double close,
        long volume
) {}
