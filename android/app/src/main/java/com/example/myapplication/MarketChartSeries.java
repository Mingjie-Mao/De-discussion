package com.example.myapplication;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** Real OHLC buckets; imported history and live observations are never mixed or interpolated. */
final class MarketChartSeries {
    static final ZoneId MARKET_ZONE = ZoneId.of("Australia/Sydney");
    record Series(List<CampusMarketRepository.MarketCandle> candles, List<String> labels,
                  boolean intraday, long axisStart, long axisEnd) {}
    static Series select(List<CampusMarketRepository.MarketCandle> rows, String period) {
        return select(rows, period, System.currentTimeMillis());
    }
    static Series select(List<CampusMarketRepository.MarketCandle> rows, String period, long now) {
        boolean history = "history".equals(period);
        boolean intraday = "intraday".equals(period);
        long axisStart = date(now).atStartOfDay(MARKET_ZONE).toInstant().toEpochMilli();
        long axisEnd = date(now).plusDays(1).atStartOfDay(MARKET_ZONE).toInstant().toEpochMilli();
        var sorted = new ArrayList<CampusMarketRepository.MarketCandle>();
        for (var c : rows) {
            if (history != "IMPORTED_DEMO".equals(c.source)) continue;
            if (c.at > now || c.low <= 0 || c.high < Math.max(c.open,c.close) || c.low > Math.min(c.open,c.close)) continue;
            if (intraday && c.at < axisStart) continue;
            sorted.add(c);
        }
        sorted.sort(Comparator.comparingLong(c -> c.at));
        var values = new ArrayList<CampusMarketRepository.MarketCandle>();
        if (history || intraday) values.addAll(sorted); // Keep original history and actual five-minute observations.
        else {
            var buckets = new TreeMap<Long,CampusMarketRepository.MarketCandle>();
            for (var c : sorted) {
                long at = bucket(c.at,period);
                var first = buckets.get(at);
                buckets.put(at, first == null
                    ? new CampusMarketRepository.MarketCandle(c.open,c.high,c.low,c.close,at,"LIVE")
                    : new CampusMarketRepository.MarketCandle(first.open,Math.max(first.high,c.high),
                        Math.min(first.low,c.low),c.close,at,"LIVE"));
            }
            values.addAll(buckets.values());
        }
        // Show complete recent buckets at a legible body width.
        if (!intraday && values.size() > 40) values = new ArrayList<>(values.subList(values.size()-40,values.size()));
        String pattern = "month".equals(period) ? "yy/MM" : "intraday".equals(period) ? "HH:mm" : "MM/dd";
        if (history && !values.isEmpty() && date(values.get(0).at).equals(date(values.get(values.size()-1).at))) pattern="HH:mm";
        var format=DateTimeFormatter.ofPattern(pattern).withZone(MARKET_ZONE);
        var labels=new ArrayList<String>();
        for(var c:values) labels.add(format.format(Instant.ofEpochMilli(c.at)));
        return new Series(List.copyOf(values),List.copyOf(labels),intraday,axisStart,axisEnd);
    }
    private static LocalDate date(long at) {
        return Instant.ofEpochMilli(at).atZone(MARKET_ZONE).toLocalDate();
    }
    private static long bucket(long at,String period) {
        LocalDate date=date(at);
        if("week".equals(period)) date=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if("month".equals(period)) date=date.withDayOfMonth(1);
        return date.atStartOfDay(MARKET_ZONE).toInstant().toEpochMilli();
    }
}
