package com.example.myapplication;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MarketChartSeriesTest {
    private CampusMarketRepository.MarketCandle candle(String at,int o,int h,int l,int c,String source) {
        return new CampusMarketRepository.MarketCandle(o,h,l,c,Instant.parse(at).toEpochMilli(),source);
    }
    private final long now=Instant.parse("2026-10-08T12:00:00Z").toEpochMilli();
    @Test public void originalHistoryKeepsExactCandlesAndNeverAppendsLivePrice() {
        var sample=candle("2026-10-05T14:30:00Z",126,138,120,132,"IMPORTED_DEMO");
        var rows=List.of(candle("2026-10-08T10:00:00Z",100,100,100,100,"LIVE"),sample);
        var history=MarketChartSeries.select(rows,"history",now);
        assertEquals(List.of(sample),history.candles());assertEquals(List.of("01:30"),history.labels());
        var day=MarketChartSeries.select(rows,"day",now);
        assertEquals(1,day.candles().size());assertEquals(100,day.candles().get(0).close);
        assertEquals(List.of("10/08"),day.labels());
    }
    @Test public void dailyBucketsSortAndPreserveOpenExtremesAndLastClose() {
        var rows=List.of(candle("2026-10-08T10:00:00Z",110,115,99,102,"LIVE"),
                candle("2026-10-07T10:00:00Z",95,105,90,100,"LIVE"),
                candle("2026-10-08T00:05:00Z",100,120,98,110,"LIVE"));
        var day=MarketChartSeries.select(rows,"day",now);
        assertEquals(List.of("10/07","10/08"),day.labels());var c=day.candles().get(1);
        assertEquals(100,c.open);assertEquals(120,c.high);assertEquals(98,c.low);assertEquals(102,c.close);
        assertEquals(1,MarketChartSeries.select(rows,"week",now).candles().size());
        assertEquals(List.of("26/10"),MarketChartSeries.select(rows,"month",now).labels());
    }
    @Test public void intradayUsesTodayAndActualTimesWithoutHourlyCandleAggregation() {
        var rows=List.of(candle("2026-10-07T11:55:00Z",90,95,85,90,"LIVE"),
                candle("2026-10-08T11:05:00Z",100,105,98,102,"LIVE"),
                candle("2026-10-08T11:45:00Z",102,107,100,106,"LIVE"));
        var s=MarketChartSeries.select(rows,"intraday",now);
        assertEquals(2,s.candles().size());assertEquals(106,s.candles().get(1).close);
        assertEquals(List.of("22:05","22:45"),s.labels());assertTrue(s.intraday());
    }
    @Test public void longHistoryShowsLatest40BucketsAndRejectsInvalidAndFutureData() {
        var rows=new ArrayList<CampusMarketRepository.MarketCandle>();
        for(int i=60;i>0;i--) rows.add(new CampusMarketRepository.MarketCandle(100,105,95,101,now-i*86_400_000L,"LIVE"));
        rows.add(candle("2026-10-09T00:00:00Z",100,105,95,101,"LIVE"));
        rows.add(candle("2026-10-08T00:00:00Z",100,50,95,101,"LIVE"));
        var s=MarketChartSeries.select(rows,"day",now);
        assertEquals(40,s.candles().size());assertEquals(s.candles().size(),s.labels().size());
        assertTrue(s.candles().get(0).at<s.candles().get(39).at);assertTrue(s.candles().get(39).at<now);
    }
    @Test public void calendarBucketsUseSydneyMidnightAndCorrectWeekAndMonthBoundaries() {
        var rows=List.of(candle("2026-09-30T13:55:00Z",100,105,99,102,"LIVE"),
            candle("2026-10-01T00:05:00Z",102,108,100,106,"LIVE"),
            candle("2026-10-04T14:05:00Z",106,110,103,108,"LIVE"));
        var day=MarketChartSeries.select(rows,"day",now);
        assertEquals(List.of("09/30","10/01","10/05"),day.labels());
        assertEquals(2,MarketChartSeries.select(rows,"week",now).candles().size());
        assertEquals(List.of("26/09","26/10"),MarketChartSeries.select(rows,"month",now).labels());
        assertEquals(2,MarketChartSeries.select(rows,"month",now).candles().size());
    }
    @Test public void intradayHandlesDaylightSavingAndRetainsMoreThan40Observations() {
        long spring=Instant.parse("2026-10-04T10:00:00Z").toEpochMilli();
        var rows=new ArrayList<CampusMarketRepository.MarketCandle>();
        long start=Instant.parse("2026-10-03T14:00:00Z").toEpochMilli();
        for(int i=0;i<50;i++) rows.add(new CampusMarketRepository.MarketCandle(100,101,99,100,start+i*300_000L,"LIVE"));
        var series=MarketChartSeries.select(rows,"intraday",spring);
        assertEquals(50,series.candles().size());assertEquals(23*3_600_000L,series.axisEnd()-series.axisStart());
    }
}
