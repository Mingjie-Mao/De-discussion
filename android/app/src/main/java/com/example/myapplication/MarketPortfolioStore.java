package com.example.myapplication;

import android.content.Context;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import org.json.*;

/** Server portfolio projection. No local wallet, simulated quotes or asset writes. */
public final class MarketPortfolioStore {
  public static final int DAILY_FLOOR_TOKENS = 1000;
  public static final long LIVE_TICK_INTERVAL_MS = 30000L;

  private MarketPortfolioStore() {}

  public static void ensureDailyReset(Context context) {
    ServerFeatures.refreshMarket(context, false);
  }

  public static boolean advanceMarketTick(Context context) {
    ServerFeatures.refreshMarket(context, false);
    return false;
  }

  public static int getCashBalance(Context context) {
    return ServerFeatures.portfolio().optInt("cash");
  }

  public static int getLivePrice(Context context, String forum, int base) {
    return CampusMarketRepository.getMarket(forum).getCurrentPrice();
  }

  public static double getReturnPercent() {
    return ServerFeatures.portfolio().optDouble("returnPercent");
  }

  @NonNull
  public static PortfolioSnapshot getPortfolio(Context context) {
    ensureDailyReset(context);
    JSONObject value = ServerFeatures.portfolio();
    ArrayList<PositionSnapshot> longs = new ArrayList<>();
    ArrayList<ShortPositionSnapshot> shorts = new ArrayList<>();
    JSONArray rows = value.optJSONArray("positions");
    if (rows != null)
      for (int i = 0; i < rows.length(); i++) {
        JSONObject p = rows.optJSONObject(i);
        int units = p.optInt("units"), cost = p.optInt("cost"), price = p.optInt("price");
        if ("LONG".equals(p.optString("side")))
          longs.add(
              new PositionSnapshot(p.optString("forumKey"), units, cost, price, p.optInt("value")));
        else {
          ShortPositionSnapshot shortPosition =
              new ShortPositionSnapshot(
                  p.optString("forumKey"),
                  units,
                  units == 0 ? 0 : Math.round((float) cost / units),
                  price,
                  p.optInt("pnl"));
          shortPosition.value = p.optInt("value");
          shortPosition.cost = cost;
          shorts.add(shortPosition);
        }
      }
    return new PortfolioSnapshot(
        value.optInt("cash"),
        value.optInt("marketValue"),
        value.optInt("totalAssets"),
        value.optInt("openPnl"),
        longs,
        shorts);
  }

  public static final class PortfolioSnapshot {
    private final int cashBalance;
    private final int marketValue;
    private final int totalAssets;
    private final int openPnl;
    private final ArrayList<PositionSnapshot> positions;
    private final ArrayList<ShortPositionSnapshot> shortPositions;

    private PortfolioSnapshot(
        int cashBalance,
        int marketValue,
        int totalAssets,
        int openPnl,
        ArrayList<PositionSnapshot> positions,
        ArrayList<ShortPositionSnapshot> shortPositions) {
      this.cashBalance = cashBalance;
      this.marketValue = marketValue;
      this.totalAssets = totalAssets;
      this.openPnl = openPnl;
      this.positions = positions;
      this.shortPositions = shortPositions;
    }

    public int getCashBalance() {
      return cashBalance;
    }

    public int getMarketValue() {
      return marketValue;
    }

    public int getTotalAssets() {
      return totalAssets;
    }

    public int getOpenPnl() {
      return openPnl;
    }

    @NonNull
    public ArrayList<PositionSnapshot> getPositions() {
      return new ArrayList<>(positions);
    }

    @NonNull
    public ArrayList<ShortPositionSnapshot> getShortPositions() {
      return new ArrayList<>(shortPositions);
    }
  }

  public static final class ShortPositionSnapshot {
    private final String forumKey;
    private final int units;
    private final int entryPrice;
    private int value;
    private int cost;

    public int getCost() {
      return cost;
    }

    private final int currentPrice;
    private final int openPnl;

    private ShortPositionSnapshot(
        String forumKey, int units, int entryPrice, int currentPrice, int openPnl) {
      this.forumKey = forumKey;
      this.units = units;
      this.entryPrice = entryPrice;
      this.currentPrice = currentPrice;
      this.openPnl = openPnl;
    }

    public String getForumKey() {
      return forumKey;
    }

    public int getUnits() {
      return units;
    }

    public int getEntryPrice() {
      return entryPrice;
    }

    public int getCurrentPrice() {
      return currentPrice;
    }

    public int getOpenPnl() {
      return openPnl;
    }

    public int getCurrentValue() {
      return value;
    }
  }

  public static final class PositionSnapshot {
    private final String forumKey;
    private final int units;
    private final int totalCost;
    private final int currentPrice;
    private final int currentValue;

    private PositionSnapshot(
        String forumKey, int units, int totalCost, int currentPrice, int currentValue) {
      this.forumKey = forumKey;
      this.units = units;
      this.totalCost = totalCost;
      this.currentPrice = currentPrice;
      this.currentValue = currentValue;
    }

    public String getForumKey() {
      return forumKey;
    }

    public int getUnits() {
      return units;
    }

    public int getTotalCost() {
      return totalCost;
    }

    public int getCurrentPrice() {
      return currentPrice;
    }

    public int getCurrentValue() {
      return currentValue;
    }

    public int getAverageCost() {
      return units == 0 ? 0 : Math.round((float) totalCost / (float) units);
    }

    public int getOpenPnl() {
      return currentValue - totalCost;
    }
  }

  public static final class TradeResult {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_INVALID_AMOUNT = 1;
    public static final int STATUS_INSUFFICIENT_BALANCE = 2;
    public static final int STATUS_BELOW_UNIT_PRICE = 3;
    public static final int STATUS_NO_POSITION = 4;
    public static final int STATUS_INSUFFICIENT_POSITION = 5;
    public static final int STATUS_NO_SHORT_POSITION = 6;
    public static final int STATUS_INSUFFICIENT_SHORT_POSITION = 7;

    private final int status;
    private final int requestedSpend;
    private final int filledUnits;
    private final int actualCost;
    private final int cashBefore;
    private final int cashAfter;
    private final int priceAfterTrade;
    private final int priceDelta;

    private TradeResult(
        int status,
        int requestedSpend,
        int filledUnits,
        int actualCost,
        int cashBefore,
        int cashAfter,
        int priceAfterTrade,
        int priceDelta) {
      this.status = status;
      this.requestedSpend = requestedSpend;
      this.filledUnits = filledUnits;
      this.actualCost = actualCost;
      this.cashBefore = cashBefore;
      this.cashAfter = cashAfter;
      this.priceAfterTrade = priceAfterTrade;
      this.priceDelta = priceDelta;
    }

    public int getStatus() {
      return status;
    }

    public int getRequestedSpend() {
      return requestedSpend;
    }

    public int getFilledUnits() {
      return filledUnits;
    }

    public int getActualCost() {
      return actualCost;
    }

    public int getCashBefore() {
      return cashBefore;
    }

    public int getCashAfter() {
      return cashAfter;
    }

    public int getPriceAfterTrade() {
      return priceAfterTrade;
    }

    public int getPriceDelta() {
      return priceDelta;
    }

    public int getUnfilledCash() {
      return requestedSpend - actualCost;
    }
  }
}
