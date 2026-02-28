package com.multibank.aggregationservice.enums;

import java.util.Arrays;

public enum Interval {
    ONE_SECOND("1s", 1),
    FIVE_SECONDS("5s", 5),
    ONE_MINUTE("1m", 60),
    FIFTEEN_MINUTES("15m", 900),
    ONE_HOUR("1h", 3600);

    private final String code;
    private final int seconds;

    Interval(String code, int seconds) {
        this.code = code;
        this.seconds = seconds;
    }

    public String code() {
        return code;
    }

    public int seconds() {
        return seconds;
    }

    public static Interval fromCode(String input) {
        return Arrays.stream(values())
                .filter(interval -> interval.code.equalsIgnoreCase(input))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported interval type : " + input));
    }
}
