package com.multibank.aggregationservice.models;

public class MutableCandle {

    private final long time;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    public MutableCandle(long time, double firstPrice) {
        this.time   = time;
        this.open   = firstPrice;
        this.high   = firstPrice;
        this.low    = firstPrice;
        this.close  = firstPrice;
        this.volume = 1L;
    }

    public void update(double price) {
        if (price > this.high) this.high = price;
        if (price < this.low)  this.low  = price;
        this.close = price;
        this.volume++;
    }

    public Candle toCandle() {
        return new Candle(time, open, high, low, close, volume);
    }

    public long getTime()   { return time; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow()  { return low; }
    public double getClose(){ return close; }
    public long getVolume() { return volume; }
}
