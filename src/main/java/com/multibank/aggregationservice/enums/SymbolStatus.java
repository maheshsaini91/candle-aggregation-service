package com.multibank.aggregationservice.enums;

/**
 * Status of a trading symbol for aggregation and history API.
 * Only ACTIVE symbols are typically used for candle generation and history.
 */
public enum SymbolStatus {

    ACTIVE,
    INACTIVE;

    public static SymbolStatus fromString(String value) {
        if (value == null) return INACTIVE;
        try {
            return valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return INACTIVE;
        }
    }
}
