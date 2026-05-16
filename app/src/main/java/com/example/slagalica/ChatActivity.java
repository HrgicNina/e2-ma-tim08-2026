package com.example.slagalica;

import android.os.Bundle;
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

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private TextView tvChatTitle;
    private ScrollView svMessages;
    private LinearLayout llMessages;
    private EditText etMessage;
    private Button btnSend;

    private FirebaseFirestore db;
    private ListenerRegistration messagesListener;
    private String myUid = "";
    private String myUsername = "";
    private String myRegion = "";
    private String roomId = "";
    private boolean sendingMessage = false;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseFirestore.getInstance();
        tvChatTitle = findViewById(R.id.tvChatTitle);
        svMessages = findViewById(R.id.svChatMessages);
        llMessages = findViewById(R.id.llChatMessages);
        etMessage = findViewById(R.id.etChatMessage);
        btnSend = findViewById(R.id.btnChatSend);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Cet je dostupan samo registrovanim igracima.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        myUid = user.getUid();
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
        }
    }

    @Override
    protected void onStop() {
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
        db.collection("users")
                .document(myUid)
                .get()
                .addOnSuccessListener(doc -> {
                    myUsername = value(doc.getString("username"));
                    myRegion = value(doc.getString("region"));
                    if (TextUtils.isEmpty(myUsername)) {
                        myUsername = "Igrac";
                    }
                    if (TextUtils.isEmpty(myRegion)) {
                        myRegion = "Srbija";
                    }
                    roomId = normalizeRegionRoom(myRegion);
                    tvChatTitle.setText(getString(R.string.chat_title_region, myRegion));
                    btnSend.setEnabled(true);
                    attachRealtimeMessages();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Ne mogu da ucitam korisnicke podatke za cet.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void attachRealtimeMessages() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        messagesListener = db.collection("regionChats")
                .document(roomId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(300)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(ChatActivity.this, "Greska pri osvezavanju poruka.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshot == null) {
                        return;
                    }
                    List<ChatMessage> items = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ChatMessage message = new ChatMessage();
                        message.senderUid = value(doc.getString("senderUid"));
                        message.senderName = value(doc.getString("senderName"));
                        message.text = value(doc.getString("text"));
                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts != null) {
                            message.createdAtMillis = ts.toDate().getTime();
                        } else {
                            Long fallback = doc.getLong("createdAtMillis");
                            message.createdAtMillis = fallback == null ? 0L : fallback;
                        }
                        items.add(message);
                    }
                    renderMessages(items);
                });
    }

    private void renderMessages(List<ChatMessage> messages) {
        llMessages.removeAllViews();
        for (ChatMessage message : messages) {
            llMessages.addView(buildMessageView(message));
        }
        svMessages.post(() -> svMessages.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private LinearLayout buildMessageView(ChatMessage message) {
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

        Map<String, Object> payload = new HashMap<>();
        payload.put("senderUid", myUid);
        payload.put("senderName", myUsername);
        payload.put("text", text);
        payload.put("createdAt", Timestamp.now());
        payload.put("createdAtMillis", System.currentTimeMillis());

        sendingMessage = true;
        btnSend.setEnabled(false);
        db.collection("regionChats")
                .document(roomId)
                .collection("messages")
                .add(payload)
                .addOnSuccessListener(doc -> {
                    etMessage.setText("");
                    sendingMessage = false;
                    btnSend.setEnabled(true);
                    notifyRegionMembersIfNeeded(text);
                })
                .addOnFailureListener(e -> {
                    sendingMessage = false;
                    btnSend.setEnabled(true);
                    Toast.makeText(ChatActivity.this, "Poruka nije poslata.", Toast.LENGTH_SHORT).show();
                });
    }

    private void notifyRegionMembersIfNeeded(String messageText) {
        if (TextUtils.isEmpty(myRegion)) {
            return;
        }
        db.collection("users")
                .whereEqualTo("region", myRegion)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        return;
                    }
                    WriteBatch batch = db.batch();
                    int count = 0;
                    for (DocumentSnapshot userDoc : snapshot.getDocuments()) {
                        String targetUid = userDoc.getId();
                        if (TextUtils.isEmpty(targetUid) || myUid.equals(targetUid)) {
                            continue;
                        }
                        Boolean chatActive = userDoc.getBoolean("chatActive");
                        if (chatActive != null && chatActive) {
                            continue;
                        }
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("type", "chat");
                        payload.put("title", "Nova poruka u cetu");
                        payload.put("message", myUsername + ": " + trimPreview(messageText));
                        payload.put("read", false);
                        payload.put("localShown", false);
                        payload.put("createdAt", Timestamp.now());
                        payload.put("actionType", "open_chat");
                        payload.put("actionPayload", roomId);
                        batch.set(
                                db.collection("users")
                                        .document(targetUid)
                                        .collection("notifications")
                                        .document(),
                                payload
                        );
                        count++;
                    }
                    if (count > 0) {
                        batch.commit();
                    }
                });
    }

    private void setChatPresence(boolean active) {
        if (TextUtils.isEmpty(myUid)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("chatActive", active);
        payload.put("chatLastSeenAt", Timestamp.now());
        db.collection("users")
                .document(myUid)
                .update(payload);
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

    private String normalizeRegionRoom(String region) {
        String normalized = value(region).trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        if (normalized.isEmpty()) {
            return "srbija";
        }
        return normalized;
    }

    private String trimPreview(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= 90) {
            return trimmed;
        }
        return trimmed.substring(0, 90) + "...";
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private static class ChatMessage {
        String senderUid;
        String senderName;
        String text;
        long createdAtMillis;
    }
}
