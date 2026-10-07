package com.example.myapplication;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LeaderboardFragment extends Fragment implements RefreshablePage {
    private TextView textLeaderboardCashValue;
    private TextView textLeaderboardAssetsValue;
    private TextView textLeaderboardReturnValue;
    private TextView textLeaderboardUserPnl;
    private LinearLayout layoutLeaderboardEntries;
    private TextView syncStatus;
    private android.widget.Button syncRetry;
    private final Handler leaderboardTickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable leaderboardTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            MarketPortfolioStore.advanceMarketTick(requireContext());
            refreshContent();
            leaderboardTickerHandler.postDelayed(this, MarketPortfolioStore.LIVE_TICK_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        textLeaderboardCashValue = view.findViewById(R.id.textLeaderboardCashValue);
        textLeaderboardAssetsValue = view.findViewById(R.id.textLeaderboardAssetsValue);
        textLeaderboardReturnValue = view.findViewById(R.id.textLeaderboardReturnValue);
        textLeaderboardUserPnl = view.findViewById(R.id.textLeaderboardUserPnl);
        layoutLeaderboardEntries = view.findViewById(R.id.layoutLeaderboardEntries);
        syncStatus=view.findViewById(R.id.textLeaderboardStatus);
        syncRetry=view.findViewById(R.id.buttonLeaderboardRetry);
        syncRetry.setOnClickListener(v -> {
            if(ServerFeatures.marketReady())ServerFeatures.loadBoard(false);
            else ServerFeatures.refreshMarket(requireContext(),true);
        });
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
    public void refreshContent() {
        if (!isResumed() || !isAdded() || getView() == null) {
            return;
        }

        MarketPortfolioStore.PortfolioSnapshot snapshot = MarketPortfolioStore.getPortfolio(requireContext());
        double returnPercent = MarketPortfolioStore.getReturnPercent();

        textLeaderboardCashValue.setText(ServerFeatures.marketReady()?getString(R.string.market_tokens_format, snapshot.getCashBalance()):"—");
        textLeaderboardAssetsValue.setText(ServerFeatures.marketReady()?getString(R.string.market_tokens_format, snapshot.getTotalAssets()):"—");
        textLeaderboardReturnValue.setText(ServerFeatures.marketReady()?String.format(Locale.getDefault(), "%+.1f%%", returnPercent):"—");
        textLeaderboardReturnValue.setTextColor(resolvePnlColor((int)Math.signum(returnPercent)));
        textLeaderboardUserPnl.setText(getString(
                R.string.leaderboard_user_pnl,
                formatSignedTokens(snapshot.getOpenPnl()),
                getString(R.string.market_tokens_format, snapshot.getMarketValue())
        ));
        textLeaderboardUserPnl.setTextColor(resolvePnlColor(snapshot.getOpenPnl()));

        String error=ServerFeatures.boardError()!=null?ServerFeatures.boardError():ServerFeatures.marketError();
        syncStatus.setVisibility(error!=null||!ServerFeatures.boardReady()?View.VISIBLE:View.GONE);
        syncStatus.setText(error!=null?getString(R.string.feed_sync_failed):getString(R.string.feed_sync_loading));
        syncRetry.setVisibility(error!=null?View.VISIBLE:View.GONE);
        renderBoard(CampusMarketRepository.getLeaderboard());
    }

    private void renderBoard(List<CampusMarketRepository.LeaderboardEntry> entries) {
        layoutLeaderboardEntries.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        ArrayList<CampusMarketRepository.LeaderboardEntry> displayEntries = new ArrayList<>(entries);
        displayEntries.sort(Comparator.comparingInt(this::getDisplayAssets).reversed());

        for (int i = 0; i < displayEntries.size(); i++) {
            CampusMarketRepository.LeaderboardEntry entry = displayEntries.get(i);
            View item = inflater.inflate(R.layout.item_leaderboard_entry, layoutLeaderboardEntries, false);
            TextView rank = item.findViewById(R.id.textLeaderboardRank);
            TextView username = item.findViewById(R.id.textLeaderboardUsername);
            TextView meta = item.findViewById(R.id.textLeaderboardMeta);
            TextView pick = item.findViewById(R.id.textLeaderboardPick);
            TextView profit = item.findViewById(R.id.textLeaderboardProfit);
            TextView assets = item.findViewById(R.id.textLeaderboardAssets);
            ImageView avatar = item.findViewById(R.id.imageLeaderboardAvatar);

            int rankNumber = i + 1;
            rank.setText(String.valueOf(rankNumber));
            rank.setBackgroundTintList(android.content.res.ColorStateList.valueOf(resolveRankColor(rankNumber)));
            rank.setTypeface(rankNumber <= 3 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            avatar.setImageResource(AppData.getForumAvatarResId(entry.getForumKey()));
            avatar.setImageTintList(null);
            username.setText(entry.getUsername());
            meta.setText(getString(
                    R.string.leaderboard_entry_meta,
                    AppData.getForumLabel(requireContext(), entry.getForumKey()),
                    entry.getStreakDays()
            ));
            pick.setText(getString(
                    R.string.leaderboard_pick_format,
                    AppData.getForumLabel(requireContext(), entry.getForumKey())
            ));
            int displayAssets = getDisplayAssets(entry);
            int displayProfit = entry.getProfit();
            double displayReturnPercent = entry.getReturnPercent();
            profit.setText(String.format(Locale.getDefault(), "%+.1f%%", displayReturnPercent));
            profit.setTextColor(resolvePnlColor(displayProfit));
            assets.setText(getString(R.string.leaderboard_assets_format, displayAssets));
            layoutLeaderboardEntries.addView(item);
        }
        if (ServerFeatures.boardMore()) { android.widget.Button more=new android.widget.Button(requireContext());more.setText(R.string.feed_load_more);more.setEnabled(!ServerFeatures.boardBusy());more.setOnClickListener(v->{more.setEnabled(false);ServerFeatures.loadBoard(true);});layoutLeaderboardEntries.addView(more); }
    }

    private int getDisplayAssets(CampusMarketRepository.LeaderboardEntry entry) { return entry.getTotalAssets(); }

    private int resolveRankColor(int rank) {
        if (rank == 1) {
            return ContextCompat.getColor(requireContext(), R.color.rank_gold);
        }
        if (rank == 2) {
            return ContextCompat.getColor(requireContext(), R.color.rank_silver);
        }
        if (rank == 3) {
            return ContextCompat.getColor(requireContext(), R.color.rank_bronze);
        }
        return ContextCompat.getColor(requireContext(), R.color.tab_bar_fill);
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

    private String formatSignedTokens(int value) {
        return String.format(Locale.getDefault(), "%+d", value);
    }

    private void startTicker() {
        leaderboardTickerHandler.removeCallbacks(leaderboardTicker);
        leaderboardTickerHandler.postDelayed(leaderboardTicker, MarketPortfolioStore.LIVE_TICK_INTERVAL_MS);
    }

    private void stopTicker() {
        leaderboardTickerHandler.removeCallbacks(leaderboardTicker);
    }
}
