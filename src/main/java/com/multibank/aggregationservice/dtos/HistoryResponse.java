package com.multibank.aggregationservice.dtos;

import java.util.List;

public record HistoryResponse(
        String s,
        List<Long>   t,
        List<Double> o,
        List<Double> h,
        List<Double> l,
        List<Double> c,
        List<Long>   v
) {
    public static HistoryResponse ok(
            List<Long> t,
            List<Double> o,
            List<Double> h,
            List<Double> l,
            List<Double> c,
            List<Long> v
    ) {
        return new HistoryResponse("ok", t, o, h, l, c, v);
    }

    public static HistoryResponse noData() {
        return new HistoryResponse("no_data",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
