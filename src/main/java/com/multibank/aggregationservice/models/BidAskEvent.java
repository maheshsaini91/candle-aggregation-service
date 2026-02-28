package com.multibank.aggregationservice.models;

public record BidAskEvent(
        String symbol,
        double bid,
        double ask,
        long timestamp
) {
    public double midPrice() {
        return (bid + ask) / 2.0; // Mid-price is used for OHLC calculation.
    }
}
