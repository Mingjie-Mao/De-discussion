package com.example.myapplication;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import backend.BackendException;
import backend.BackendMemberGateway;
import backend.BackendModerationGateway;
import backend.BackendRuntime;

public class NotificationsFragment extends Fragment implements RefreshablePage {
    private RecyclerView recyclerNotifications;
    private RadioGroup radioNotificationFilters;
    private android.widget.TextView notificationStatus;
    private android.widget.Button notificationRetry;
    private final ArrayList<AppData.AppNotification> cachedNotifications=new ArrayList<>();
    private boolean loading, loaded, failed;
    private String accountScope="";
    private long requestRevision;
    private AppData.NotificationType currentFilter = AppData.NotificationType.ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerNotifications = view.findViewById(R.id.recyclerNotifications);
        radioNotificationFilters = view.findViewById(R.id.radioNotificationFilters);
        notificationStatus=view.findViewById(R.id.textNotificationsStatus);
        notificationRetry=view.findViewById(R.id.buttonNotificationsRetry);
        notificationRetry.setOnClickListener(v -> loadOnlineNotifications(BackendRuntime.from(requireContext())));
        recyclerNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        prepareFilterButtons();
        radioNotificationFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.filterNotificationsLikes) {
                currentFilter = AppData.NotificationType.LIKE;
            } else if (checkedId == R.id.filterNotificationsBookmarks) {
                currentFilter = AppData.NotificationType.BOOKMARK;
            } else if (checkedId == R.id.filterNotificationsComments) {
                currentFilter = AppData.NotificationType.COMMENT;
            } else if (checkedId == R.id.filterNotificationsMentions) {
                currentFilter = AppData.NotificationType.MENTION;
            } else {
                currentFilter = AppData.NotificationType.ALL;
            }
            styleFilterButtons();
            refreshContent();
        });
        styleFilterButtons();
        refreshContent();
    }

    private void prepareFilterButtons() {
        for (int i = 0; i < radioNotificationFilters.getChildCount(); i++) {
            View child = radioNotificationFilters.getChildAt(i);
            if (!(child instanceof RadioButton button)) {
                continue;
            }
            button.setButtonDrawable(null);
            button.setGravity(Gravity.CENTER);
            button.setMinWidth(dp(76));
            button.setPadding(dp(16), 0, dp(16), 0);
            button.setTextSize(15);
            button.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }

    private void styleFilterButtons() {
        for (int i = 0; i < radioNotificationFilters.getChildCount(); i++) {
            View child = radioNotificationFilters.getChildAt(i);
            if (!(child instanceof RadioButton button)) {
                continue;
            }
            boolean selected = button.isChecked();
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(dp(999));
            background.setColor(selected
                    ? Color.argb(170, 232, 236, 243)
                    : Color.argb(74, 255, 255, 255));
            background.setStroke(dp(1), selected
                    ? ContextCompat.getColor(requireContext(), R.color.tab_bar_stroke)
                    : Color.argb(128, 226, 229, 234));
            button.setBackground(background);
            button.setTextColor(ContextCompat.getColor(
                    requireContext(),
                    selected ? R.color.ink_primary : R.color.ink_secondary
            ));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshContent();
        if(loaded&&!loading) loadOnlineNotifications(BackendRuntime.from(requireContext()));
    }

    @Override
    public void refreshContent() {
        if (!isResumed() || !isAdded() || recyclerNotifications == null) {
            return;
        }

        BackendRuntime backend = BackendRuntime.from(requireContext());
        if (backend.config().isEnabled()) {
            radioNotificationFilters.setVisibility(View.VISIBLE);
            String scope=AppData.getCurrentUserId()+"|"+backend.config().baseUrl();
            if(!scope.equals(accountScope)) { accountScope=scope; cachedNotifications.clear(); loaded=false;failed=false;loading=false;requestRevision++; }
            renderNotifications();
            if(!loaded&&!loading&&!failed) loadOnlineNotifications(backend);
            return;
        }

        radioNotificationFilters.setVisibility(View.VISIBLE);
        showNotifications(AppData.getNotifications(requireContext(), currentFilter));
    }

    private void loadOnlineNotifications(BackendRuntime backend) {
        if(loading) return;
        UUID userId = AppData.getCurrentUserId();
        String scope=accountScope;
        long revision=++requestRevision;
        loading=true;failed=false;renderNotifications();
        backend.member().fetchNotifications(userId, new BackendModerationGateway.Callback<>() {
            @Override
            public void onSuccess(List<BackendMemberGateway.NotificationSnapshot> values) {
                if (!isAdded() || getView()==null || revision!=requestRevision || !scope.equals(accountScope)) {
                    return;
                }
                ArrayList<AppData.AppNotification> notifications = new ArrayList<>();
                for (BackendMemberGateway.NotificationSnapshot value : values) {
                    notifications.add(new AppData.AppNotification(
                            notificationType(value.type()), userId, null, null, value.referenceId(),
                            value.title(), value.body(), value.createdAt(), value.id(),
                            value.referenceType(), value.referenceId(), value.read()));
                }
                loading=false;loaded=true;failed=false;
                cachedNotifications.clear();cachedNotifications.addAll(notifications);renderNotifications();
            }

            @Override
            public void onError(BackendException error) {
                if (!isAdded() || getView()==null || revision!=requestRevision || !scope.equals(accountScope)) return;
                if (UiPreferences.handleExpiredSession(requireActivity(), error)) return;
                loading=false;failed=true;renderNotifications();
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override public void onDestroyView() {
        requestRevision++;loading=false;
        super.onDestroyView();
    }

    private void renderNotifications() {
        ArrayList<AppData.AppNotification> rows=new ArrayList<>(cachedNotifications);
        rows.removeIf(n -> currentFilter!=AppData.NotificationType.ALL&&n.type()!=currentFilter);
        showNotifications(rows);
        notificationStatus.setVisibility(loading||failed||rows.isEmpty()?View.VISIBLE:View.GONE);
        notificationStatus.setText(loading?R.string.feed_sync_loading:failed?R.string.feed_sync_failed:R.string.notifications_empty);
        notificationRetry.setVisibility(failed?View.VISIBLE:View.GONE);
    }

    private void showNotifications(List<AppData.AppNotification> notifications) {
        NotificationAdapter adapter = new NotificationAdapter(notifications);
        adapter.setOnClickListener(this::openNotification);
        recyclerNotifications.setAdapter(adapter);
    }

    private void openNotification(AppData.AppNotification notification) {
        if (!isAdded() || notification == null) {
            return;
        }
        if (notification.remoteNotificationId() != null) {
            BackendRuntime backend = BackendRuntime.from(requireContext());
            backend.member().markNotificationRead(
                    AppData.getCurrentUserId(), notification.remoteNotificationId(),
                    new BackendModerationGateway.Callback<>() {
                        @Override public void onSuccess(Void ignored) {
                            if(isAdded()&&getView()!=null) loadOnlineNotifications(backend);
                        }
                        @Override public void onError(BackendException error) { if (isAdded()) UiPreferences.handleExpiredSession(requireActivity(), error); }
                    });
            showOnlineNotification(notification, backend);
            return;
        }

        if (notification.postId() != null) {
            Intent intent = new Intent(requireContext(), PostViewerActivity.class);
            intent.putExtra(PostViewerActivity.EXTRA_POST_ID, notification.postId().toString());
            startActivity(intent);
        }
    }

    private void showOnlineNotification(AppData.AppNotification notification, BackendRuntime backend) {
        if("POST".equals(notification.referenceType()) && notification.referenceId()!=null) {
            backend.forum().fetchPost(notification.referenceId(),new BackendModerationGateway.Callback<>() {
                public void onSuccess(backend.BackendForumGateway.PostSnapshot post) {
                    if(!isAdded()) return;
                    AppData.upsertRemotePost(post,backend.config().baseUrl());
                    Intent intent=new Intent(requireContext(),PostViewerActivity.class);
                    intent.putExtra(PostViewerActivity.EXTRA_POST_ID,post.id().toString()); startActivity(intent);
                }
                public void onError(BackendException error) { if(isAdded()) Toast.makeText(requireContext(),error.getMessage(),Toast.LENGTH_LONG).show(); }
            }); return;
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle(notification.title())
                .setMessage(notification.body())
                .setNegativeButton(R.string.action_close, null);

        boolean canAppeal = notification.type() == AppData.NotificationType.MODERATION_APPEALABLE
                && "MODERATION_CASE".equals(notification.referenceType())
                && notification.referenceId() != null
                && !notification.body().contains("NONE");
        if (canAppeal) {
            dialog.setPositiveButton(R.string.action_appeal,
                    (ignored, which) -> showAppealDialog(backend, notification.referenceId()));
        }
        dialog.show();
    }

    private void showAppealDialog(BackendRuntime backend, UUID caseId) {
        EditText reason = new EditText(requireContext());
        reason.setHint(R.string.appeal_reason_hint);
        reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reason.setMinLines(3);
        int padding = dp(18);
        reason.setPadding(padding, padding, padding, padding);

        new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle(R.string.appeal_title)
                .setView(reason)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_submit, (dialog, which) -> {
                    String value = reason.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.appeal_reason_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    backend.member().createAppeal(
                            AppData.getCurrentUserId(), caseId, value,
                            new BackendModerationGateway.Callback<>() {
                                @Override
                                public void onSuccess(BackendMemberGateway.AppealSnapshot appeal) {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), R.string.appeal_submitted, Toast.LENGTH_LONG).show();
                                    }
                                }

                                @Override
                                public void onError(BackendException error) {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(),
                                                getString(R.string.appeal_failed, error.getMessage()),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                })
                .show();
    }

    private static AppData.NotificationType notificationType(String type) {
        if(java.util.List.of("LIKE","BOOKMARK","COMMENT","MENTION","FOLLOW").contains(type)) return AppData.NotificationType.valueOf(type);
        if ("APPEAL_DECISION".equals(type) || "APPEAL_FILED".equals(type)) {
            return AppData.NotificationType.APPEAL;
        }
        return "MODERATION_DECISION_APPEALABLE".equals(type)
                ? AppData.NotificationType.MODERATION_APPEALABLE
                : AppData.NotificationType.MODERATION;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
