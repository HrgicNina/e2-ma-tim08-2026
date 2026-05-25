package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.NotificationService;
import com.example.slagalica.domain.SessionManager;
import com.example.slagalica.model.AppNotification;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {

    private NotificationService service;
    private NotificationService.Filter currentFilter = NotificationService.Filter.ALL;
    private String currentTypeFilter = null;
    private LinearLayout notificationsContainer;
    private TextView tvEmpty;
    private Button btnTypeChat;
    private Button btnTypeRanking;
    private Button btnTypeReward;
    private Button btnTypeOther;
    private List<AppNotification> latestLoadedItems = new ArrayList<>();
    private boolean initialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isGuestMode()) {
            Toast.makeText(this, R.string.notifications_registered_only, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_notifications);

        service = new NotificationService();
        notificationsContainer = findViewById(R.id.notificationsContainer);
        tvEmpty = findViewById(R.id.tvNotificationsEmpty);
        TextView btnOpenFilterMenu = findViewById(R.id.btnOpenFilterMenu);

        btnTypeChat = findViewById(R.id.btnTestChat);
        btnTypeRanking = findViewById(R.id.btnTestRanking);
        btnTypeReward = findViewById(R.id.btnTestReward);
        btnTypeOther = findViewById(R.id.btnTestOther);

        btnOpenFilterMenu.setOnClickListener(this::showFilterMenu);

        btnTypeChat.setOnClickListener(v -> toggleTypeFilter("chat"));
        btnTypeRanking.setOnClickListener(v -> toggleTypeFilter("ranking"));
        btnTypeReward.setOnClickListener(v -> toggleTypeFilter("rewards"));
        btnTypeOther.setOnClickListener(v -> toggleTypeFilter("other"));

        service.deleteHardcodedSeedNotifications(new NotificationService.UiActionCallback() {
            @Override
            public void onSuccess() {
                initialized = true;
                loadNotifications();
            }

            @Override
            public void onError(String message) {
                initialized = true;
                loadNotifications();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (initialized) {
            loadNotifications();
        }
    }

    private void changeFilter(NotificationService.Filter filter) {
        currentFilter = filter;
        loadNotifications();
    }

    private void toggleTypeFilter(String type) {
        if (type.equals(currentTypeFilter)) {
            currentTypeFilter = null;
        } else {
            currentTypeFilter = type;
        }
        refreshTypeFilterButtons();
        renderNotifications(latestLoadedItems);
    }

    private void refreshTypeFilterButtons() {
        updateTypeButtonState(btnTypeChat, "chat".equals(currentTypeFilter));
        updateTypeButtonState(btnTypeRanking, "ranking".equals(currentTypeFilter));
        updateTypeButtonState(btnTypeReward, "rewards".equals(currentTypeFilter));
        updateTypeButtonState(btnTypeOther, "other".equals(currentTypeFilter));
    }

    private void updateTypeButtonState(Button button, boolean selected) {
        button.setEnabled(!selected);
        button.setAlpha(selected ? 0.55f : 1f);
    }

    private void showFilterMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().setGroupCheckable(0, true, true);

        android.view.MenuItem itemAll = popup.getMenu().add(0, 1, 1, getString(R.string.notifications_filter_all));
        android.view.MenuItem itemUnread = popup.getMenu().add(0, 2, 2, getString(R.string.notifications_filter_unread));
        android.view.MenuItem itemRead = popup.getMenu().add(0, 3, 3, getString(R.string.notifications_filter_read));

        itemAll.setCheckable(true);
        itemUnread.setCheckable(true);
        itemRead.setCheckable(true);

        if (currentFilter == NotificationService.Filter.UNREAD) {
            itemUnread.setChecked(true);
        } else if (currentFilter == NotificationService.Filter.READ) {
            itemRead.setChecked(true);
        } else {
            itemAll.setChecked(true);
        }

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 2) {
                changeFilter(NotificationService.Filter.UNREAD);
                return true;
            }
            if (item.getItemId() == 3) {
                changeFilter(NotificationService.Filter.READ);
                return true;
            }
            changeFilter(NotificationService.Filter.ALL);
            return true;
        });
        popup.show();
    }

    private void loadNotifications() {
        service.load(currentFilter, new NotificationService.UiLoadCallback() {
            @Override
            public void onSuccess(List<AppNotification> items) {
                latestLoadedItems = items;
                renderNotifications(items);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(NotificationsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderNotifications(List<AppNotification> items) {
        notificationsContainer.removeAllViews();
        refreshTypeFilterButtons();

        List<AppNotification> filteredItems = new ArrayList<>();
        for (AppNotification item : items) {
            if (currentTypeFilter == null || currentTypeFilter.equals(item.type)) {
                filteredItems.add(item);
            }
        }

        tvEmpty.setVisibility(filteredItems.isEmpty() ? View.VISIBLE : View.GONE);
        if (filteredItems.isEmpty()) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AppNotification item : filteredItems) {
            View row = inflater.inflate(R.layout.item_notification, notificationsContainer, false);
            TextView tvType = row.findViewById(R.id.tvNotType);
            TextView tvTitle = row.findViewById(R.id.tvNotTitle);
            TextView tvMessage = row.findViewById(R.id.tvNotMessage);
            TextView tvMeta = row.findViewById(R.id.tvNotMeta);
            Button btnRead = row.findViewById(R.id.btnMarkRead);

            tvType.setText(typeLabel(item.type));
            tvTitle.setText(item.title);
            tvMessage.setText(item.message);
            tvMeta.setText(formatMeta(item));

            if (item.read) {
                btnRead.setEnabled(false);
                btnRead.setText(R.string.notifications_already_read);
            }

            btnRead.setOnClickListener(v -> service.markAsRead(item.id, new NotificationService.UiActionCallback() {
                @Override
                public void onSuccess() {
                    loadNotifications();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(NotificationsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }));

            row.setOnClickListener(v -> openNotificationDestination(item));

            notificationsContainer.addView(row);
        }
    }

    private String formatMeta(AppNotification item) {
        String state = item.read ? "Procitano" : "Neprocitano";
        String date = item.createdAtMillis > 0
                ? new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(item.createdAtMillis))
                : "-";
        return state + " | " + date;
    }

    private String typeLabel(String type) {
        if ("chat".equals(type)) return "Cet";
        if ("ranking".equals(type)) return "Rang lista";
        if ("rewards".equals(type)) return "Nagrade";
        return "Ostalo";
    }

    private void openNotificationDestination(AppNotification item) {
        if (item == null) {
            return;
        }
        if (!item.read) {
            item.read = true;
            service.markAsRead(item.id, new NotificationService.UiActionCallback() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onError(String message) {
                }
            });
        }
        android.content.Intent intent = NotificationIntentRouter.buildOpenIntent(
                this,
                item.type,
                item.actionType,
                item.actionPayload,
                item.id
        );
        startActivity(intent);
    }
}
