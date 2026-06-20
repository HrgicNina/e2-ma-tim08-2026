package com.example.slagalica.domain;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MatchRealtimeClient {

    public interface Listener {
        void onConnected();
        void onAuthenticated();

        void onDisconnected(String reason);

        void onError(String message);

        void onMatchFound(String roomId, boolean friendly, int playerNumber, String opponentUid, String opponentUsername);

        void onInviteReceived(String inviteId, String fromUid, String fromUsername);
        void onInviteSent(String inviteId, int expiresInSeconds);

        void onInviteDeclined(String byUsername);
        void onInviteExpired(String inviteId);
        void onInviteCancelled(String inviteId, String byUsername);

        void onInfo(String message);
        void onQueueJoined();
        void onQueueCancelled();

        void onMatchFinished(String winnerUid, int yourScore, int opponentScore, boolean forfeit, boolean draw);
        void onGameEvent(String roomId, String game, String event, String fromUid, JSONObject data);
    }

    private final OkHttpClient httpClient = new OkHttpClient();
    private WebSocket socket;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void connect(String serverUrl, String uid, String username, Listener listener) {
        this.listener = listener;
        Request request = new Request.Builder().url(serverUrl).build();
        socket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                sendAuth(uid, username);
                if (MatchRealtimeClient.this.listener != null) {
                    MatchRealtimeClient.this.listener.onConnected();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleIncoming(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                if (MatchRealtimeClient.this.listener != null) {
                    MatchRealtimeClient.this.listener.onDisconnected(reason);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (MatchRealtimeClient.this.listener != null) {
                    MatchRealtimeClient.this.listener.onError("WS greska: " + t.getMessage());
                }
            }
        });
    }

    public void disconnect() {
        if (socket != null) {
            socket.close(1000, "client_closed");
            socket = null;
        }
    }

    public void joinRandomQueue() {
        send("queue.join", null);
    }

    public void cancelQueue() {
        send("queue.cancel", null);
    }

    public void sendInvite(String targetIdentity) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("target", targetIdentity);
        } catch (JSONException ignored) {
        }
        send("invite.send", payload);
    }

    public void respondInvite(String inviteId, boolean accept) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("inviteId", inviteId);
            payload.put("accept", accept);
        } catch (JSONException ignored) {
        }
        send("invite.respond", payload);
    }

    public void cancelInvite(String inviteId) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("inviteId", inviteId);
        } catch (JSONException ignored) {
        }
        send("invite.cancel", payload);
    }

    public void submitScore(String roomId, int score) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("roomId", roomId);
            payload.put("score", score);
        } catch (JSONException ignored) {
        }
        send("match.submit_score", payload);
    }

    public void forfeit(String roomId) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("roomId", roomId);
        } catch (JSONException ignored) {
        }
        send("match.forfeit", payload);
    }

    public void sendGameEvent(String roomId, String game, String event, JSONObject data) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("roomId", roomId);
            payload.put("game", game);
            payload.put("event", event);
            payload.put("data", data == null ? new JSONObject() : data);
        } catch (JSONException ignored) {
        }
        send("game.event", payload);
    }

    private void sendAuth(String uid, String username) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("uid", uid);
            payload.put("username", username == null ? "" : username);
        } catch (JSONException ignored) {
        }
        send("auth", payload);
    }

    private void send(String type, JSONObject payload) {
        if (socket == null) return;
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", type);
            msg.put("payload", payload == null ? new JSONObject() : payload);
            socket.send(msg.toString());
        } catch (JSONException ignored) {
        }
    }

    private void handleIncoming(String text) {
        if (listener == null) return;
        try {
            JSONObject msg = new JSONObject(text);
            String type = msg.optString("type");
            JSONObject payload = msg.optJSONObject("payload");
            if (payload == null) payload = new JSONObject();

            switch (type) {
                case "match.start":
                    listener.onMatchFound(
                            payload.optString("roomId"),
                            payload.optBoolean("friendly"),
                            payload.optInt("playerNumber", 1),
                            payload.optString("opponentUid"),
                            payload.optString("opponentUsername")
                    );
                    break;
                case "auth.ok":
                    listener.onAuthenticated();
                    break;
                case "queue.joined":
                    listener.onQueueJoined();
                    break;
                case "queue.cancelled":
                    listener.onQueueCancelled();
                    break;
                case "invite.received":
                    listener.onInviteReceived(
                            payload.optString("inviteId"),
                            payload.optString("fromUid"),
                            payload.optString("fromUsername")
                    );
                    break;
                case "invite.sent":
                    listener.onInviteSent(
                            payload.optString("inviteId"),
                            payload.optInt("expiresInSeconds", 10)
                    );
                    break;
                case "invite.declined":
                    listener.onInviteDeclined(payload.optString("byUsername"));
                    break;
                case "invite.expired":
                    listener.onInviteExpired(payload.optString("inviteId"));
                    break;
                case "invite.cancelled":
                    listener.onInviteCancelled(
                            payload.optString("inviteId"),
                            payload.optString("byUsername")
                    );
                    break;
                case "info":
                    listener.onInfo(payload.optString("message"));
                    break;
                case "match.finished":
                    listener.onMatchFinished(
                            payload.optString("winnerUid"),
                            payload.optInt("yourScore"),
                            payload.optInt("opponentScore"),
                            payload.optBoolean("forfeit"),
                            payload.optBoolean("draw")
                    );
                    break;
                case "game.event":
                    listener.onGameEvent(
                            payload.optString("roomId"),
                            payload.optString("game"),
                            payload.optString("event"),
                            payload.optString("fromUid"),
                            payload.optJSONObject("data") == null ? new JSONObject() : payload.optJSONObject("data")
                    );
                    break;
                case "error":
                    listener.onError(payload.optString("message", "Nepoznata greska."));
                    break;
                default:
                    break;
            }
        } catch (JSONException e) {
            listener.onError("Neispravna WS poruka.");
        }
    }
}
