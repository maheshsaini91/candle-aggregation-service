package com.multibank.aggregationservice.enums;

import java.util.Arrays;
import java.util.Optional;

public enum Interval {

    ONE_SEC(1L,      "1s"),
    FIVE_SEC(5L,     "5s"),
    ONE_MIN(60L,     "1m"),
    FIFTEEN_MIN(900L,"15m"),
    ONE_HOUR(3600L,  "1h");

    private final long seconds;
    private final String label;

    Interval(long seconds, String label) {
        this.seconds = seconds;
        this.label   = label;
    }

    public long getSeconds() { return seconds; }
    public String getLabel() { return label; }

    public static Optional<Interval> fromLabel(String label) {
        return Arrays.stream(values())
                .filter(i -> i.label.equalsIgnoreCase(label))
                .findFirst();
    }

    public static Optional<Interval> fromSeconds(long seconds) {
        return Arrays.stream(values())
                .filter(i -> i.seconds == seconds)
                .findFirst();
    }
}
