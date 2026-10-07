package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MarketCandleChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean intraday;
    private long axisStart, axisEnd;
    private final RectF rect = new RectF();
    private final ArrayList<CampusMarketRepository.MarketCandle> candles = new ArrayList<>();
    private final ArrayList<String> timeLabels = new ArrayList<>();

    public MarketCandleChartView(Context context) {
        this(context, null);
    }

    public MarketCandleChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MarketCandleChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.chart_grid));

        gainPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        gainPaint.setStrokeWidth(dp(2));
        gainPaint.setColor(ContextCompat.getColor(getContext(), R.color.market_gain));

        lossPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        lossPaint.setStrokeWidth(dp(2));
        lossPaint.setColor(ContextCompat.getColor(getContext(), R.color.market_loss));

        labelPaint.setTextSize(dp(9));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(Color.argb(190,255,255,255));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setColor(Color.WHITE);
    }

    public void setCandles(List<CampusMarketRepository.MarketCandle> values) {
        setData(values, null);
    }

    public void setSeries(MarketChartSeries.Series series) {
        setData(series.candles(),series.labels());
        intraday=series.intraday(); axisStart=series.axisStart(); axisEnd=series.axisEnd();
        invalidate();
    }

    public void setData(List<CampusMarketRepository.MarketCandle> values, List<String> labels) {
        intraday=false;
        candles.clear();
        if (values != null) candles.addAll(values);
        timeLabels.clear();
        if (labels != null) timeLabels.addAll(labels);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (candles.isEmpty()) {
            canvas.drawText(getContext().getString(R.string.market_chart_empty), getWidth() / 2f,
                    getHeight() / 2f, labelPaint);
            return;
        }

        boolean hasLabels = !timeLabels.isEmpty();
        float left = dp(10);
        float top = dp(12);
        float right = getWidth() - dp(38);
        float bottom = getHeight() - (hasLabels ? dp(28) : dp(24));
        float chartHeight = Math.max(dp(40), bottom - top);

        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (CampusMarketRepository.MarketCandle candle : candles) {
            max = Math.max(max, intraday ? candle.close : candle.high);
            min = Math.min(min, intraday ? candle.close : candle.low);
        }
        int padding = Math.max(1, (int) Math.ceil((max - min) * 0.12));
        max += padding;
        min -= padding;

        drawGrid(canvas, left, top, right, bottom);
        for (int tick=0; tick<3; tick++) {
            int value=Math.round(max-(max-min)*tick/2f);
            float y=top+chartHeight*tick/2f;
            canvas.drawText(String.valueOf(value),getWidth()-dp(17),y+dp(3),labelPaint);
        }
        if (intraday) {
            drawIntraday(canvas,left,top,right,chartHeight,min,max);
            return;
        }

        float slotWidth = (right - left) / Math.max(1, candles.size());
        float bodyWidth = Math.min(dp(18), slotWidth * 0.52f);

        for (int i = 0; i < candles.size(); i++) {
            CampusMarketRepository.MarketCandle candle = candles.get(i);
            Paint paint = candle.close >= candle.open ? gainPaint : lossPaint;
            float centerX = left + (slotWidth * i) + (slotWidth / 2f);
            float highY = valueToY(candle.high, min, max, top, chartHeight);
            float lowY = valueToY(candle.low, min, max, top, chartHeight);
            float openY = valueToY(candle.open, min, max, top, chartHeight);
            float closeY = valueToY(candle.close, min, max, top, chartHeight);

            canvas.drawLine(centerX, highY, centerX, lowY, paint);

            float bodyTop = Math.min(openY, closeY);
            float bodyBottom = Math.max(openY, closeY);
            if (Math.abs(bodyBottom - bodyTop) < dp(3)) {
                float middle = (bodyTop + bodyBottom) / 2f;
                bodyTop = middle - dp(1);
                bodyBottom = middle + dp(1);
            }

            rect.set(centerX - (bodyWidth / 2f), bodyTop, centerX + (bodyWidth / 2f), bodyBottom);
            if (candle.open == candle.close) canvas.drawLine(rect.left,(openY+closeY)/2f,rect.right,(openY+closeY)/2f,paint);
            else canvas.drawRoundRect(rect, dp(1), dp(1), paint);
        }

        // Draw evenly-spaced time labels at the bottom
        if (hasLabels) {
            int n = candles.size();
            // Show up to 5 labels: first, ~25%, ~50%, ~75%, last
            int[] positions;
            if (n <= 5) {
                positions = new int[n];
                for (int i = 0; i < n; i++) positions[i] = i;
            } else {
                positions = new int[]{0, n / 4, n / 2, n * 3 / 4, n - 1};
            }
            float labelY = getHeight() - dp(4);
            for (int idx : positions) {
                if (idx < timeLabels.size()) {
                    float cx = left + (slotWidth * idx) + (slotWidth / 2f);
                    // clamp to avoid clipping at edges
                    cx = Math.max(left + dp(16), Math.min(right - dp(16), cx));
                    canvas.drawText(timeLabels.get(idx), cx, labelY, labelPaint);
                }
            }
        }
    }

    private void drawIntraday(Canvas canvas,float left,float top,float right,float height,int min,int max) {
        Path path=new Path();
        for(int i=0;i<candles.size();i++) {
            var candle=candles.get(i);
            float x=left+(right-left)*(float)(candle.at-axisStart)/(axisEnd-axisStart);
            float y=valueToY(candle.close,min,max,top,height);
            if(i==0) path.moveTo(x,y); else path.lineTo(x,y);
            canvas.drawCircle(x,y,dp(2),linePaint);
        }
        canvas.drawPath(path,linePaint);
        var format=java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(MarketChartSeries.MARKET_ZONE);
        for(int i=0;i<=4;i++) {
            long at=axisStart+(axisEnd-axisStart)*i/4;
            String label=i==4 ? "24:00" : format.format(java.time.Instant.ofEpochMilli(at));
            float x=left+(right-left)*i/4f;
            x=Math.max(left+dp(16),Math.min(right-dp(16),x));
            canvas.drawText(label,x,getHeight()-dp(4),labelPaint);
        }
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        float middle = top + ((bottom - top) / 2f);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), dp(20), dp(20), gridPaint);
        canvas.drawLine(left + dp(12), top + dp(24), right - dp(12), top + dp(24), gridPaint);
        canvas.drawLine(left + dp(12), middle, right - dp(12), middle, gridPaint);
        canvas.drawLine(left + dp(12), bottom - dp(18), right - dp(12), bottom - dp(18), gridPaint);
    }

    private float valueToY(int value, int min, int max, float top, float chartHeight) {
        float ratio = (float) (value - min) / (float) (max - min);
        return top + chartHeight - (ratio * chartHeight);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
