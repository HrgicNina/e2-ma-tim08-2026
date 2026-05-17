package com.example.slagalica;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.ChatService;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private TextView tvChatTitle;
    private ScrollView svMessages;
    private LinearLayout llMessages;
    private EditText etMessage;
    private Button btnSend;

    private ChatService chatService;
    private AuthService authService;
    private ListenerRegistration messagesListener;
    private String myUid = "";
    private String myUsername = "";
    private String myRegion = "";
    private String roomId = "";
    private boolean sendingMessage = false;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    private final Handler presenceHandler = new Handler(Looper.getMainLooper());
    private final Runnable presenceHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!TextUtils.isEmpty(myUid)) {
                setChatPresence(true);
                presenceHandler.postDelayed(this, 30_000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        authService = new AuthService();
        chatService = new ChatService();
        tvChatTitle = findViewById(R.id.tvChatTitle);
        svMessages = findViewById(R.id.svChatMessages);
        llMessages = findViewById(R.id.llChatMessages);
        etMessage = findViewById(R.id.etChatMessage);
        btnSend = findViewById(R.id.btnChatSend);

        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(this, "Cet je dostupan samo registrovanim igracima.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        myUid = uid;
        btnSend.setEnabled(false);

        btnSend.setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_UNSPECIFIED
                    || enterPressed) {
                sendMessage();
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        loadProfileAndStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!TextUtils.isEmpty(myUid)) {
            setChatPresence(true);
            presenceHandler.removeCallbacks(presenceHeartbeatRunnable);
            presenceHandler.postDelayed(presenceHeartbeatRunnable, 30_000L);
        }
    }

    @Override
    protected void onStop() {
        presenceHandler.removeCallbacks(presenceHeartbeatRunnable);
        if (!TextUtils.isEmpty(myUid)) {
            setChatPresence(false);
        }
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        super.onStop();
    }

    private void loadProfileAndStart() {
        chatService.loadProfileAndResolveRoom(myUid, new ChatService.InitCallback() {
            @Override
            public void onSuccess(ChatService.ChatInitData data) {
                myUsername = data.username;
                myRegion = data.region;
                roomId = data.roomId;
                tvChatTitle.setText(getString(R.string.chat_title_region, myRegion));
                btnSend.setEnabled(true);
                attachRealtimeMessages();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void attachRealtimeMessages() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        messagesListener = chatService.listenMessages(roomId, new ChatService.MessagesCallback() {
            @Override
            public void onSuccess(List<ChatService.ChatMessage> messages) {
                renderMessages(messages);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderMessages(List<ChatService.ChatMessage> messages) {
        llMessages.removeAllViews();
        for (ChatService.ChatMessage message : messages) {
            llMessages.addView(buildMessageView(message));
        }
        svMessages.post(() -> svMessages.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private LinearLayout buildMessageView(ChatService.ChatMessage message) {
        boolean mine = myUid.equals(message.senderUid);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dp(8);
        row.setLayoutParams(rowParams);
        row.setGravity(mine ? android.view.Gravity.END : android.view.Gravity.START);

        TextView meta = new TextView(this);
        meta.setTextColor(ContextCompat.getColor(this, R.color.app_on_surface));
        meta.setTextSize(13f);
        meta.setText(message.senderName + " • " + formatTime(message.createdAtMillis));
        meta.setGravity(mine ? android.view.Gravity.END : android.view.Gravity.START);
        meta.setTextAlignment(mine ? android.view.View.TEXT_ALIGNMENT_VIEW_END : android.view.View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.gravity = mine ? android.view.Gravity.END : android.view.Gravity.START;
        meta.setLayoutParams(metaParams);

        TextView bubble = new TextView(this);
        bubble.setText(message.text);
        bubble.setTextSize(18f);
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        bubble.setBackgroundResource(mine ? R.drawable.chat_bubble_me : R.drawable.chat_bubble_other);
        bubble.setTextColor(ContextCompat.getColor(this, mine ? R.color.app_on_primary : R.color.app_on_surface));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.topMargin = dp(4);
        bubbleParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        bubble.setLayoutParams(bubbleParams);
        bubble.setMaxWidth(dp(260));

        row.addView(meta);
        row.addView(bubble);
        return row;
    }

    private void sendMessage() {
        if (sendingMessage) {
            return;
        }
        String text = value(etMessage.getText().toString()).trim();
        if (text.isEmpty()) {
            return;
        }
        if (TextUtils.isEmpty(roomId)) {
            Toast.makeText(this, "Cet jos nije spreman.", Toast.LENGTH_SHORT).show();
            return;
        }

        sendingMessage = true;
        btnSend.setEnabled(false);
        chatService.sendMessage(roomId, myUid, myUsername, text, new ChatService.ActionCallback() {
            @Override
            public void onSuccess() {
                    etMessage.setText("");
                    sendingMessage = false;
                    btnSend.setEnabled(true);
                    notifyRegionMembersIfNeeded(text);
            }

            @Override
            public void onError(String message) {
                sendingMessage = false;
                btnSend.setEnabled(true);
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void notifyRegionMembersIfNeeded(String messageText) {
        chatService.notifyRegionMembersIfNeeded(myUid, myUsername, myRegion, roomId, messageText);
    }

    private void setChatPresence(boolean active) {
        chatService.setChatPresence(myUid, active);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String formatTime(long millis) {
        if (millis <= 0L) {
            return "";
        }
        return timeFormat.format(new Date(millis));
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
