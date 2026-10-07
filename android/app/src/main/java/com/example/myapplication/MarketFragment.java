package com.example.myapplication;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketFragment extends Fragment implements RefreshablePage {
    private static final String STATE_SELECTED_MARKET = "selected_market";
    private static final String STATE_LAST_TRADE_MESSAGE = "last_trade_message";
    private static final String STATE_LAST_TRADE_STATUS = "last_trade_status";

    private TextView textMarketBalanceValue;
    private TextView syncStatus;
    private Button syncRetry;
    private TextView textMarketHeldValue;
    private TextView textMarketOpenPnlValue;
    private TextView textMarketResetRule;
    private LinearLayout layoutMarketSelectors;
    private LinearLayout layoutMarketHero;
    private ImageView imageMarketAvatar;
    private TextView textMarketDemoBadge;
    private TextView textMarketForumName;
    private TextView textMarketIndex;
    private TextView textMarketChange;
    private TextView textMarketFormulaLabel;
    private TextView textMarketFormulaValue;
    private TextView textMarketTriggerLabel;
    private MarketCandleChartView viewMarketChart;
    private LinearLayout layoutMarketTriggers;
    private LinearLayout layoutChartPeriod;
    private LinearLayout layoutTradeMode;
    private String selectedPeriod = "history";
    private TextView textMarketChartNote;
    private String tradeMode = "long"; // "long" or "short"
    private TextView textTradePrice;
    private EditText inputTradeAmount;
    private TextView textTradeEstimate;
    private Button buttonTradeBuy;
    private Button buttonTradeSell;
    private TextView textTradeResult;
    private LinearLayout layoutMarketPositions;
    private TextView textPositionEmpty;
    private TextView textTradeExplain;
    private String selectedMarketKey;
    private String lastTradeMessage;
    private int lastTradeStatus = -1;
    private final Handler marketTickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable marketTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            MarketPortfolioStore.advanceMarketTick(requireContext());
            refreshContent();
            marketTickerHandler.postDelayed(this, MarketPortfolioStore.LIVE_TICK_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_market, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        textMarketBalanceValue = view.findViewById(R.id.textMarketBalanceValue);
        syncStatus=view.findViewById(R.id.textMarketStatus);
        syncRetry=view.findViewById(R.id.buttonMarketRetry);
        syncRetry.setOnClickListener(v -> ServerFeatures.refreshMarket(requireContext(),true));
        textMarketHeldValue = view.findViewById(R.id.textMarketHeldValue);
        textMarketOpenPnlValue = view.findViewById(R.id.textMarketOpenPnlValue);
        textMarketResetRule = view.findViewById(R.id.textMarketResetRule);
        layoutMarketSelectors = view.findViewById(R.id.layoutMarketSelectors);
        layoutMarketHero = view.findViewById(R.id.layoutMarketHero);
        imageMarketAvatar = view.findViewById(R.id.imageMarketAvatar);
        textMarketDemoBadge = view.findViewById(R.id.textMarketDemoBadge);
        textMarketForumName = view.findViewById(R.id.textMarketForumName);
        textMarketIndex = view.findViewById(R.id.textMarketIndex);
        textMarketChange = view.findViewById(R.id.textMarketChange);
        textMarketFormulaLabel = view.findViewById(R.id.textMarketFormulaLabel);
        textMarketFormulaValue = view.findViewById(R.id.textMarketFormulaValue);
        textMarketTriggerLabel = view.findViewById(R.id.textMarketTriggerLabel);
        viewMarketChart = view.findViewById(R.id.viewMarketChart);
        layoutMarketTriggers = view.findViewById(R.id.layoutMarketTriggers);
        layoutChartPeriod = view.findViewById(R.id.layoutChartPeriod);
        textMarketChartNote = view.findViewById(R.id.textMarketChartNote);
        layoutTradeMode = view.findViewById(R.id.layoutTradeMode);
        textTradePrice = view.findViewById(R.id.textTradePrice);
        inputTradeAmount = view.findViewById(R.id.inputTradeAmount);
        textTradeEstimate = view.findViewById(R.id.textTradeEstimate);
        buttonTradeBuy = view.findViewById(R.id.buttonTradeBuy);
        buttonTradeSell = view.findViewById(R.id.buttonTradeSell);
        textTradeResult = view.findViewById(R.id.textTradeResult);
        layoutMarketPositions = view.findViewById(R.id.layoutMarketPositions);
        textPositionEmpty = view.findViewById(R.id.textPositionEmpty);
        textTradeExplain = view.findViewById(R.id.textTradeExplain);

        if (savedInstanceState != null) {
            selectedMarketKey = savedInstanceState.getString(STATE_SELECTED_MARKET);
            selectedPeriod = savedInstanceState.getString("chartPeriod", "history");
            pendingTradePayload = savedInstanceState.getString("pendingTradePayload");
            String restoredTradeId = savedInstanceState.getString("pendingTradeId");
            if (restoredTradeId != null) pendingTradeId = java.util.UUID.fromString(restoredTradeId);
            lastTradeMessage = savedInstanceState.getString(STATE_LAST_TRADE_MESSAGE);
            lastTradeStatus = savedInstanceState.getInt(STATE_LAST_TRADE_STATUS, -1);
        }
        if (selectedMarketKey == null) {
            selectedMarketKey = AppData.getSelectedForumKey();
        }

        inputTradeAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateTradeEstimate();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        buttonTradeBuy.setOnClickListener(v -> handleBuy());
        buttonTradeSell.setOnClickListener(v -> handleSell());
        android.widget.Button history = new android.widget.Button(requireContext()); history.setText(R.string.server_trade_history); history.setOnClickListener(v -> ServerFeatures.history(requireContext())); ((android.view.ViewGroup)buttonTradeSell.getParent().getParent()).addView(history);

        refreshContent();
    }

    @Override
    public void onResume() {
        super.onResume();
        ServerFeatures.init(requireContext()); ServerFeatures.observe(this,this::refreshContent);
        MarketPortfolioStore.advanceMarketTick(requireContext());
        refreshContent();
        startTicker();
    }

    @Override
    public void onPause() {
        ServerFeatures.remove(this);
        super.onPause();
        stopTicker();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_MARKET, selectedMarketKey);
        outState.putString("chartPeriod", selectedPeriod);
        outState.putString("pendingTradePayload", pendingTradePayload);
        outState.putString("pendingTradeId", pendingTradeId == null ? null : pendingTradeId.toString());
        outState.putString(STATE_LAST_TRADE_MESSAGE, lastTradeMessage);
        outState.putInt(STATE_LAST_TRADE_STATUS, lastTradeStatus);
    }

    @Override
    public void refreshContent() {
        if (!isResumed() || !isAdded() || getView() == null) {
            return;
        }

        if (selectedMarketKey == null) {
            selectedMarketKey = AppData.getSelectedForumKey();
        }

        MarketPortfolioStore.PortfolioSnapshot snapshot = MarketPortfolioStore.getPortfolio(requireContext());
        textMarketBalanceValue.setText(ServerFeatures.marketReady() ? formatTokens(snapshot.getCashBalance()) : "—");
        textMarketHeldValue.setText(ServerFeatures.marketReady() ? formatTokens(snapshot.getMarketValue()) : "—");
        textMarketOpenPnlValue.setText(ServerFeatures.marketReady()?formatSignedTokens(snapshot.getOpenPnl()):"—");
        textMarketOpenPnlValue.setTextColor(resolvePnlColor(snapshot.getOpenPnl()));
        textMarketResetRule.setText(getString(
                R.string.market_reset_rule,
                MarketPortfolioStore.DAILY_FLOOR_TOKENS,
                MarketPortfolioStore.DAILY_FLOOR_TOKENS
        ));

        boolean ready=ServerFeatures.marketReady();
        String error=ServerFeatures.marketError();
        syncStatus.setVisibility(error!=null||!ready?View.VISIBLE:View.GONE);
        syncStatus.setText(error!=null?R.string.feed_sync_failed:R.string.feed_sync_loading);
        syncRetry.setVisibility(error!=null?View.VISIBLE:View.GONE);
        layoutMarketHero.setVisibility(ready?View.VISIBLE:View.GONE);
        renderSelectorRow();
        renderSelectedMarket();
        renderPositions(snapshot);
        renderTradeResult();
        updateTradeEstimate();
    }

    private void renderSelectorRow() {
        if (!isAdded() || layoutMarketSelectors == null) {
            return;
        }

        layoutMarketSelectors.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<CampusMarketRepository.SchoolMarket> markets = CampusMarketRepository.getMarkets();
        for (CampusMarketRepository.SchoolMarket market : markets) {
            View chip = inflater.inflate(R.layout.item_market_selector, layoutMarketSelectors, false);
            LinearLayout root = chip.findViewById(R.id.layoutSelectorRoot);
            ImageView avatar = chip.findViewById(R.id.imageSelectorAvatar);
            TextView label = chip.findViewById(R.id.textSelectorLabel);
            TextView price = chip.findViewById(R.id.textSelectorPrice);

            String forumKey = market.getForumKey();
            boolean selected = forumKey.equals(selectedMarketKey);
            int fillColor = ContextCompat.getColor(requireContext(),
                    selected ? AppData.getForumHeaderColorResId(forumKey) : R.color.surface);
            int primaryColor = ContextCompat.getColor(requireContext(),
                    selected ? R.color.forum_header_on : R.color.ink_primary);
            int secondaryColor = ContextCompat.getColor(requireContext(),
                    selected ? R.color.forum_header_on_secondary : R.color.ink_secondary);

            root.setBackgroundTintList(android.content.res.ColorStateList.valueOf(fillColor));
            root.setAlpha(selected ? 1.0f : 0.88f);
            avatar.setImageResource(AppData.getForumAvatarResId(forumKey));
            avatar.setImageTintList(null);
            if (AppData.FORUM_UM.equals(forumKey)) {
                avatar.setBackgroundResource(R.drawable.bg_community_avatar_um);
            }
            label.setText(AppData.getForumLabel(requireContext(), forumKey));
            label.setTextColor(primaryColor);
            label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            price.setText(ServerFeatures.marketReady()?getString(R.string.market_selector_price, getLivePrice(market)):"—");
            price.setTextColor(secondaryColor);
            root.setOnClickListener(v -> {
                selectedMarketKey = forumKey;
                refreshContent();
            });
            layoutMarketSelectors.addView(chip);
        }
    }

    private void renderSelectedMarket() {
        if (!isAdded() || layoutMarketHero == null) {
            return;
        }

        CampusMarketRepository.SchoolMarket market = CampusMarketRepository.getMarket(selectedMarketKey);
        int headerColor = ContextCompat.getColor(requireContext(), AppData.getForumHeaderColorResId(selectedMarketKey));
        int onColor = ContextCompat.getColor(requireContext(), R.color.forum_header_on);
        int onSecondary = ContextCompat.getColor(requireContext(), R.color.forum_header_on_secondary);

        GradientDrawable hero = new GradientDrawable();
        hero.setShape(GradientDrawable.RECTANGLE);
        hero.setCornerRadius(dp(28));
        hero.setColor(headerColor);
        layoutMarketHero.setBackground(hero);

        textMarketDemoBadge.setBackground(makePill(Color.argb(42, 255, 255, 255), 999));
        textMarketDemoBadge.setTextColor(onColor);

        imageMarketAvatar.setImageResource(AppData.getForumAvatarResId(selectedMarketKey));
        imageMarketAvatar.setImageTintList(null);
        if (AppData.FORUM_UM.equals(selectedMarketKey)) {
            imageMarketAvatar.setBackgroundResource(R.drawable.bg_community_avatar_um);
        } else {
            imageMarketAvatar.setBackgroundResource(R.drawable.bg_community_avatar);
        }
        textMarketForumName.setText(AppData.getForumLabel(requireContext(), selectedMarketKey));
        textMarketForumName.setTextColor(onColor);
        int livePrice = getLivePrice(market);
        int heatIndex = livePrice;
        textMarketIndex.setText(getString(R.string.market_index_format, heatIndex));
        textMarketIndex.setTextColor(onColor);
        textMarketChange.setText(getString(R.string.market_change_24h, formatPercent(getLiveDayChangePercent(market, livePrice))));
        textMarketChange.setTextColor(onColor);
        textMarketChange.setBackground(makePill(Color.argb(40, 255, 255, 255), 999));
        textMarketFormulaLabel.setTextColor(onSecondary);
        textMarketFormulaValue.setTextColor(onSecondary);
        textMarketFormulaValue.setText(getString(
                R.string.market_formula_value,
                market.getPostsToday(),
                market.getRepliesToday(),
                market.getLikesToday()
        ));
        textMarketTriggerLabel.setTextColor(onSecondary);

        setupPeriodChips(onColor, onSecondary);
        renderChart(market);
        renderTriggers(market.getTriggers(requireContext()), onColor);

        setupTradeModeChips();
        textTradePrice.setText(getString(R.string.market_trade_price_format, livePrice));
    }

    private void renderTriggers(List<String> triggers, int textColor) {
        layoutMarketTriggers.removeAllViews();
        for (String trigger : triggers) {
            TextView chip = new TextView(requireContext());
            chip.setText(trigger);
            chip.setTextColor(textColor);
            chip.setTextSize(13f);
            chip.setBackground(makePill(Color.argb(34, 255, 255, 255), 18));
            chip.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = layoutMarketTriggers.getChildCount() == 0 ? 0 : dp(8);
            layoutMarketTriggers.addView(chip, params);
        }
    }

    private void updateTradeEstimate() {
        if (!isAdded() || inputTradeAmount == null) {
            return;
        }

        CampusMarketRepository.SchoolMarket market = CampusMarketRepository.getMarket(selectedMarketKey);
        MarketPortfolioStore.PortfolioSnapshot snapshot = MarketPortfolioStore.getPortfolio(requireContext());
        int currentCash = snapshot.getCashBalance();
        int units = parsePositiveInt(inputTradeAmount.getText().toString());

        if (units <= 0 || units > 10000) {
            textTradeEstimate.setText(R.string.market_estimate_placeholder);
            setTradeButtonStates(false, false);
            return;
        }

        int unitPrice = getLivePrice(market);
        int totalCost = units * unitPrice;

        if ("short".equals(tradeMode)) {
            // 做空: Open = open short, Close = close short
            MarketPortfolioStore.ShortPositionSnapshot shortPos =
                    findShortPosition(snapshot.getShortPositions(), selectedMarketKey);
            boolean canOpenShort = totalCost <= currentCash;
            boolean canCloseShort = shortPos != null && units <= shortPos.getUnits();

            if (canOpenShort || canCloseShort) {
                if (canOpenShort) {
                    textTradeEstimate.setText(getString(
                            R.string.market_estimate_open, totalCost, units, unitPrice));
                } else {
                    // close short estimate
                    int released = units == shortPos.getUnits() ? shortPos.getCost() : (int)((long)shortPos.getCost() * units / shortPos.getUnits());
                    int receive = Math.max(0, 2 * released - unitPrice * units);
                    int pnl = receive - released;
                    textTradeEstimate.setText(getString(
                            R.string.market_estimate_close, receive, formatSignedTokens(pnl)));
                }
            } else {
                textTradeEstimate.setText(R.string.market_transaction_insufficient_balance);
            }
            setTradeButtonStates(canOpenShort, canCloseShort);
            return;
        }

        // 做多: Open = buy, Close = sell
        MarketPortfolioStore.PositionSnapshot longPos =
                findPosition(snapshot.getPositions(), selectedMarketKey);
        boolean canBuy = totalCost <= currentCash;
        boolean canSell = longPos != null && units <= longPos.getUnits();

        if (canBuy || canSell) {
            if (canBuy) {
                textTradeEstimate.setText(getString(
                        R.string.market_estimate_open, totalCost, units, unitPrice));
            } else {
                // close long estimate
                int pnl = (unitPrice - longPos.getAverageCost()) * units;
                int receive = units * unitPrice;
                textTradeEstimate.setText(getString(
                        R.string.market_estimate_close, receive, formatSignedTokens(pnl)));
            }
        } else {
            textTradeEstimate.setText(R.string.market_transaction_insufficient_balance);
        }

        setTradeButtonStates(canBuy, canSell);
    }

    private boolean tradePending;
    private java.util.UUID pendingTradeId;
    private String pendingTradePayload;
    private void handleBuy() { trade("short".equals(tradeMode) ? "OPEN_SHORT" : "BUY"); }
    private void handleSell() { trade("short".equals(tradeMode) ? "CLOSE_SHORT" : "SELL"); }
    private void trade(String action) {
        if (tradePending || !ServerFeatures.marketReady()) return;
        int units=parsePositiveInt(inputTradeAmount.getText().toString());
        if (units<1 || units>10000) { Toast.makeText(requireContext(), R.string.market_transaction_invalid_amount, Toast.LENGTH_SHORT).show(); return; }
        int price=CampusMarketRepository.getMarket(selectedMarketKey).getCurrentPrice();
        String logical=selectedMarketKey+"|"+action+"|"+units+"|";
        if (pendingTradePayload != null && pendingTradePayload.startsWith(logical)) price=Integer.parseInt(pendingTradePayload.substring(logical.length()));
        String payload=logical+price;
        // Preserve the key after an ambiguous network error so tapping Retry cannot buy twice.
        if (!payload.equals(pendingTradePayload)) { pendingTradeId=java.util.UUID.randomUUID(); pendingTradePayload=payload; }
        org.json.JSONObject body=new org.json.JSONObject();
        try { body.put("requestId",pendingTradeId).put("forumKey",selectedMarketKey).put("action",action).put("units",units).put("expectedPrice",price); } catch(org.json.JSONException e) { return; }
        android.content.Context tradeContext=requireContext().getApplicationContext();
        tradePending=true; setTradeButtonStates(false,false);
        ServerFeatures.call("POST","/api/market/trades",body,new backend.BackendModerationGateway.Callback<>() {
            public void onSuccess(org.json.JSONObject result) {
                tradePending=false; pendingTradePayload=null; pendingTradeId=null;
                ServerFeatures.refreshMarket(tradeContext,true);
                if (!isAdded() || getView()==null) return;
                lastTradeMessage=getString(action.equals("BUY") || action.equals("OPEN_SHORT") ? R.string.market_transaction_success : R.string.market_sell_success, result.optInt("units"),AppData.getForumLabel(requireContext(),selectedMarketKey),result.optInt("cashBefore"),result.optInt("cashAfter"),result.optInt("price"));
                lastTradeStatus=0; inputTradeAmount.setText(""); Toast.makeText(requireContext(),lastTradeMessage,Toast.LENGTH_SHORT).show();
                ServerFeatures.refreshMarket(requireContext(),true); refreshContent();
            }
            public void onError(backend.BackendException error) {
                tradePending=false; if (!error.hasAmbiguousWriteOutcome()) { pendingTradePayload=null; pendingTradeId=null; }
                ServerFeatures.refreshMarket(tradeContext,true);
                if (!isAdded() || getView()==null) return;
                lastTradeMessage=error.getMessage();lastTradeStatus=1;renderTradeResult();
                ServerFeatures.refreshMarket(requireContext(),true);refreshContent();
            }
        });
    }

    private void renderTradeResult() {
        if (textTradeResult == null) {
            return;
        }
        if (!ServerFeatures.marketReady()) {
            textTradeResult.setVisibility(View.VISIBLE); textTradeResult.setText(ServerFeatures.marketError() == null ? getString(R.string.feed_sync_loading) : ServerFeatures.marketError()); textTradeResult.setOnClickListener(v -> ServerFeatures.refreshMarket(requireContext(),true)); return;
        }
        textTradeResult.setOnClickListener(null);
        if (lastTradeMessage == null || lastTradeMessage.trim().isEmpty()) {
            textTradeResult.setVisibility(View.GONE);
            return;
        }

        textTradeResult.setVisibility(View.VISIBLE);
        textTradeResult.setText(lastTradeMessage);
        int colorRes = lastTradeStatus == MarketPortfolioStore.TradeResult.STATUS_SUCCESS
                ? R.color.market_gain
                : R.color.market_loss;
        textTradeResult.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
    }

    private void renderPositions(MarketPortfolioStore.PortfolioSnapshot snapshot) {
        List<MarketPortfolioStore.PositionSnapshot> positions = snapshot.getPositions();
        List<MarketPortfolioStore.ShortPositionSnapshot> shorts = snapshot.getShortPositions();
        layoutMarketPositions.removeAllViews();
        boolean noPositions = positions.isEmpty() && shorts.isEmpty();
        textPositionEmpty.setVisibility(noPositions ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (MarketPortfolioStore.PositionSnapshot position : positions) {
            View item = inflater.inflate(R.layout.item_market_position, layoutMarketPositions, false);
            ImageView avatar = item.findViewById(R.id.imagePositionAvatar);
            TextView forum = item.findViewById(R.id.textPositionForum);
            TextView meta = item.findViewById(R.id.textPositionMeta);
            TextView value = item.findViewById(R.id.textPositionValue);
            TextView units = item.findViewById(R.id.textPositionUnits);
            TextView pnl = item.findViewById(R.id.textPositionPnl);

            avatar.setImageResource(AppData.getForumAvatarResId(position.getForumKey()));
            avatar.setImageTintList(null);
            forum.setText(getString(R.string.market_position_type_long) + " · "
                    + AppData.getForumLabel(requireContext(), position.getForumKey()));
            meta.setText(getString(R.string.market_position_meta, position.getUnits(), position.getAverageCost()));
            value.setText(getString(R.string.market_position_value, position.getCurrentValue(), position.getCurrentPrice()));
            units.setText(getString(R.string.market_position_units, position.getUnits()));
            pnl.setText(getString(R.string.market_position_pnl, formatSignedTokens(position.getOpenPnl())));
            pnl.setTextColor(resolvePnlColor(position.getOpenPnl()));
            layoutMarketPositions.addView(item);
        }

        for (MarketPortfolioStore.ShortPositionSnapshot sp : shorts) {
            View item = inflater.inflate(R.layout.item_market_position, layoutMarketPositions, false);
            ImageView avatar = item.findViewById(R.id.imagePositionAvatar);
            TextView forum = item.findViewById(R.id.textPositionForum);
            TextView meta = item.findViewById(R.id.textPositionMeta);
            TextView value = item.findViewById(R.id.textPositionValue);
            TextView units = item.findViewById(R.id.textPositionUnits);
            TextView pnl = item.findViewById(R.id.textPositionPnl);

            avatar.setImageResource(AppData.getForumAvatarResId(sp.getForumKey()));
            avatar.setImageTintList(null);
            forum.setText(getString(R.string.market_position_type_short) + " · "
                    + AppData.getForumLabel(requireContext(), sp.getForumKey()));
            meta.setText(getString(R.string.market_position_short_meta, sp.getUnits(), sp.getEntryPrice()));
            value.setText(getString(R.string.market_position_value, sp.getCurrentValue(), sp.getCurrentPrice()));
            units.setText(getString(R.string.market_position_units, sp.getUnits()));
            pnl.setText(getString(R.string.market_position_pnl, formatSignedTokens(sp.getOpenPnl())));
            pnl.setTextColor(resolvePnlColor(sp.getOpenPnl()));
            layoutMarketPositions.addView(item);
        }
    }


    private void setupTradeModeChips() {
        if (layoutTradeMode == null) return;
        layoutTradeMode.removeAllViews();
        String[] modes = {"long", "short"};
        int[] labelRes = {R.string.market_trade_long, R.string.market_trade_short};
        int[] colors = {
                ContextCompat.getColor(requireContext(), R.color.accent_strong),
                ContextCompat.getColor(requireContext(), R.color.market_loss)
        };
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            boolean sel = mode.equals(tradeMode);
            TextView chip = new TextView(requireContext());
            chip.setText(labelRes[i]);
            chip.setTextSize(14f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setTextColor(sel ? ContextCompat.getColor(requireContext(), R.color.white) : colors[i]);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(999));
            bg.setColor(sel ? colors[i] : Color.TRANSPARENT);
            bg.setStroke(dp(1), colors[i]);
            chip.setBackground(bg);
            chip.setPadding(dp(20), dp(8), dp(20), dp(8));
            chip.setOnClickListener(v -> {
                tradeMode = mode;
                setupTradeModeChips();
                updateTradeModeButtons();
                updateTradeEstimate();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.leftMargin = i == 0 ? 0 : dp(10);
            layoutTradeMode.addView(chip, p);
        }
        updateTradeModeButtons();
    }

    private void updateTradeModeButtons() {
        if (buttonTradeBuy == null || buttonTradeSell == null) return;
        // Labels are always 买入开仓 / 卖出平仓; just refresh estimate on mode change
        updateTradeEstimate();
    }

    private void setTradeButtonStates(boolean buyEnabled, boolean sellEnabled) {
        buyEnabled = buyEnabled && !tradePending && ServerFeatures.marketReady();
        sellEnabled = sellEnabled && !tradePending && ServerFeatures.marketReady();
        buttonTradeBuy.setEnabled(buyEnabled);
        buttonTradeBuy.setAlpha(buyEnabled ? 1.0f : 0.62f);
        buttonTradeSell.setEnabled(sellEnabled);
        buttonTradeSell.setAlpha(sellEnabled ? 1.0f : 0.62f);
    }

    private void startTicker() {
        marketTickerHandler.removeCallbacks(marketTicker);
        marketTickerHandler.postDelayed(marketTicker, MarketPortfolioStore.LIVE_TICK_INTERVAL_MS);
    }

    private void stopTicker() {
        marketTickerHandler.removeCallbacks(marketTicker);
    }

    private GradientDrawable makePill(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setColor(color);
        return drawable;
    }

    private int resolvePnlColor(int value) {
        if (value > 0) {
            return ContextCompat.getColor(requireContext(), R.color.market_gain);
        }
        if (value < 0) {
            return ContextCompat.getColor(requireContext(), R.color.market_loss);
        }
        return ContextCompat.getColor(requireContext(), R.color.ink_primary);
    }

    private String formatTokens(int value) {
        return getString(R.string.market_tokens_format, value);
    }

    private String formatSignedTokens(int value) {
        return String.format(Locale.getDefault(), "%+d", value);
    }

    private String formatPercent(double value) {
        return String.format(Locale.getDefault(), "%+.1f%%", value);
    }

    private int getLivePrice(CampusMarketRepository.SchoolMarket market) {
        return MarketPortfolioStore.getLivePrice(requireContext(), market.getForumKey(), market.getCurrentPrice());
    }

    @Nullable
    private MarketPortfolioStore.PositionSnapshot findPosition(
            List<MarketPortfolioStore.PositionSnapshot> positions,
            String forumKey
    ) {
        for (MarketPortfolioStore.PositionSnapshot position : positions) {
            if (position.getForumKey().equals(forumKey)) {
                return position;
            }
        }
        return null;
    }

    private double getLiveDayChangePercent(CampusMarketRepository.SchoolMarket market, int livePrice) { return market.getDayChangePercent(); }

    private int parsePositiveInt(String raw) {
        if (raw == null) {
            return 0;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void setupPeriodChips(int onColor, int onSecondary) {
        if (layoutChartPeriod == null) return;
        layoutChartPeriod.removeAllViews();
        String[] periods = {"history", "intraday", "day", "week", "month"};
        int[] labelRes = {
            R.string.chart_period_history,
            R.string.chart_period_intraday,
            R.string.chart_period_day,
            R.string.chart_period_week,
            R.string.chart_period_month
        };
        for (int i = 0; i < periods.length; i++) {
            final String period = periods[i];
            boolean sel = period.equals(selectedPeriod);
            TextView chip = new TextView(requireContext());
            chip.setText(labelRes[i]);
            chip.setTextSize(13f);
            chip.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            chip.setTextColor(sel ? onColor : Color.argb(160,
                    Color.red(onColor), Color.green(onColor), Color.blue(onColor)));
            chip.setBackground(makePill(Color.argb(sel ? 90 : 34, 255, 255, 255), 999));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setOnClickListener(v -> {
                selectedPeriod = period;
                CampusMarketRepository.SchoolMarket m = CampusMarketRepository.getMarket(selectedMarketKey);
                renderChart(m);
                int oc = ContextCompat.getColor(requireContext(), R.color.forum_header_on);
                int os = ContextCompat.getColor(requireContext(), R.color.forum_header_on_secondary);
                setupPeriodChips(oc, os);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.leftMargin = i == 0 ? 0 : dp(8);
            layoutChartPeriod.addView(chip, p);
        }
    }

    private void renderChart(CampusMarketRepository.SchoolMarket market) {
        MarketChartSeries.Series series = MarketChartSeries.select(market.getCandles(), selectedPeriod);
        viewMarketChart.setSeries(series);
        if ("history".equals(selectedPeriod)) textMarketChartNote.setText(R.string.market_chart_history_note);
        else textMarketChartNote.setText(getString("intraday".equals(selectedPeriod)
                ? R.string.market_chart_intraday_note : R.string.market_chart_period_note, series.candles().size()));
        textMarketChartNote.setTextColor(ContextCompat.getColor(requireContext(), R.color.forum_header_on_secondary));
    }

    private MarketPortfolioStore.ShortPositionSnapshot findShortPosition(
            List<MarketPortfolioStore.ShortPositionSnapshot> shorts, String forumKey) {
        for (MarketPortfolioStore.ShortPositionSnapshot s : shorts) {
            if (s.getForumKey().equals(forumKey)) return s;
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private MainActivity host() {
        return (MainActivity) requireActivity();
    }
}
