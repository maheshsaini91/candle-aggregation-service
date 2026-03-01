package com.multibank.aggregationservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CandleId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "interval_sec", nullable = false)
    private Long intervalSec;

    @Column(name = "bucket_start", nullable = false)
    private Long bucketStart;

    public CandleId() {
    }

    public CandleId(String symbol, Long intervalSec, Long bucketStart) {
        this.symbol = symbol;
        this.intervalSec = intervalSec;
        this.bucketStart = bucketStart;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getIntervalSec() {
        return intervalSec;
    }

    public void setIntervalSec(Long intervalSec) {
        this.intervalSec = intervalSec;
    }

    public Long getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(Long bucketStart) {
        this.bucketStart = bucketStart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CandleId candleId = (CandleId) o;
        return Objects.equals(symbol, candleId.symbol)
                && Objects.equals(intervalSec, candleId.intervalSec)
                && Objects.equals(bucketStart, candleId.bucketStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, intervalSec, bucketStart);
    }
}
