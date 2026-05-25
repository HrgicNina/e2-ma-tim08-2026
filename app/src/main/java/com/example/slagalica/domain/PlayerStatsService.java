package com.example.slagalica.domain;

import android.content.Intent;

import com.example.slagalica.MatchActivity;
import com.example.slagalica.data.PlayerStatsRepository;
import com.example.slagalica.model.PlayerStats;

import java.util.HashMap;
import java.util.Map;

public class PlayerStatsService {

    public static final String EXTRA_STATS_GAME_ID = "stats_game_id";
    public static final String EXTRA_STATS_GAME_POINTS = "stats_game_points";
    public static final String EXTRA_STATS_GAME_MAX_POINTS = "stats_game_max_points";
    public static final String EXTRA_STATS_QUIZ_CORRECT = "stats_quiz_correct";
    public static final String EXTRA_STATS_QUIZ_WRONG = "stats_quiz_wrong";
    public static final String EXTRA_STATS_QUIZ_NO_ANSWER = "stats_quiz_no_answer";
    public static final String EXTRA_STATS_CONNECTIONS_MATCHED = "stats_connections_matched";
    public static final String EXTRA_STATS_CONNECTIONS_MISSED = "stats_connections_missed";
    public static final String EXTRA_STATS_ASSOCIATIONS_SOLVED_COUNT = "stats_associations_solved_count";
    public static final String EXTRA_STATS_STEP_SOLVED_AT = "stats_step_solved_at";
    public static final String EXTRA_STATS_MASTER_ATTEMPT = "stats_master_attempt";
    public static final String EXTRA_STATS_NUMBER_DISTANCE = "stats_number_distance";

    public interface StatsCallback {
        void onSuccess(PlayerStats stats);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    private final PlayerStatsRepository repository;

    public PlayerStatsService() {
        this(new PlayerStatsRepository());
    }

    public PlayerStatsService(PlayerStatsRepository repository) {
        this.repository = repository;
    }

    public void loadStats(String uid, StatsCallback callback) {
        repository.loadStats(uid, new PlayerStatsRepository.StatsCallback() {
            @Override
            public void onSuccess(PlayerStats stats) {
                callback.onSuccess(stats);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void recordGameFromResult(String uid, Intent result, ActionCallback callback) {
        if (result == null) {
            callback.onSuccess();
            return;
        }
        String gameId = result.getStringExtra(EXTRA_STATS_GAME_ID);
        if (gameId == null || gameId.trim().isEmpty()) {
            callback.onSuccess();
            return;
        }
        int points = result.getIntExtra(EXTRA_STATS_GAME_POINTS, 0);
        int maxPoints = result.getIntExtra(EXTRA_STATS_GAME_MAX_POINTS, defaultMaxPoints(gameId));
        repository.recordGameStats(uid, gameId, points, maxPoints, detailsFromResult(result, gameId), adapt(callback));
    }

    public void recordMatchResult(String uid, boolean win, boolean draw, ActionCallback callback) {
        repository.recordMatchResult(uid, win, draw, adapt(callback));
    }

    private Map<String, Long> detailsFromResult(Intent result, String gameId) {
        Map<String, Long> details = new HashMap<>();
        if ("quiz".equals(gameId)) {
            put(details, "quizCorrect", result.getIntExtra(EXTRA_STATS_QUIZ_CORRECT, 0));
            put(details, "quizWrong", result.getIntExtra(EXTRA_STATS_QUIZ_WRONG, 0));
            put(details, "quizNoAnswer", result.getIntExtra(EXTRA_STATS_QUIZ_NO_ANSWER, 0));
        } else if ("connections".equals(gameId)) {
            put(details, "connectionsMatched", result.getIntExtra(EXTRA_STATS_CONNECTIONS_MATCHED, 0));
            put(details, "connectionsMissed", result.getIntExtra(EXTRA_STATS_CONNECTIONS_MISSED, 0));
        } else if ("associations".equals(gameId)) {
            int solvedCount = clamp(result.getIntExtra(EXTRA_STATS_ASSOCIATIONS_SOLVED_COUNT, 0), 0, 5);
            put(details, "associationsSolved" + solvedCount, 1);
        } else if ("step".equals(gameId)) {
            int solvedAt = result.getIntExtra(EXTRA_STATS_STEP_SOLVED_AT, 0);
            if (solvedAt >= 1 && solvedAt <= 7) {
                put(details, "stepSolvedAt" + solvedAt, 1);
            } else {
                put(details, "stepUnsolved", 1);
            }
        } else if ("master".equals(gameId)) {
            int attempt = result.getIntExtra(EXTRA_STATS_MASTER_ATTEMPT, 0);
            if (attempt >= 1 && attempt <= 6) {
                put(details, "masterAttempt" + attempt, 1);
            } else {
                put(details, "masterUnsolved", 1);
            }
        } else if ("number".equals(gameId)) {
            int distance = result.getIntExtra(EXTRA_STATS_NUMBER_DISTANCE, -1);
            if (distance == 0) {
                put(details, "numberExact", 1);
            } else if (distance >= 1 && distance <= 4) {
                put(details, "numberDistance1To4", 1);
            } else if (distance >= 5 && distance <= 9) {
                put(details, "numberDistance5To9", 1);
            } else if (distance >= 10 && distance <= 19) {
                put(details, "numberDistance10To19", 1);
            } else if (distance >= 20 && distance <= 49) {
                put(details, "numberDistance20To49", 1);
            } else if (distance >= 50 && distance <= 99) {
                put(details, "numberDistance50To99", 1);
            } else {
                put(details, "numberDistance100Plus", 1);
            }
        }
        return details;
    }

    public static int pointsForCurrentPlayer(Intent data, int myPlayerNumber, int basePlayer1, int basePlayer2) {
        int player1Score = data.getIntExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, basePlayer1);
        int player2Score = data.getIntExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, basePlayer2);
        if (myPlayerNumber == 1) {
            return Math.max(0, player1Score - basePlayer1);
        }
        return Math.max(0, player2Score - basePlayer2);
    }

    public static void putBaseGameStats(Intent result, String gameId, int points, int maxPoints) {
        result.putExtra(EXTRA_STATS_GAME_ID, gameId);
        result.putExtra(EXTRA_STATS_GAME_POINTS, Math.max(0, points));
        result.putExtra(EXTRA_STATS_GAME_MAX_POINTS, Math.max(0, maxPoints));
    }

    public int defaultMaxPoints(String gameId) {
        if ("quiz".equals(gameId)) return 50;
        if ("connections".equals(gameId)) return 20;
        if ("associations".equals(gameId)) return 60;
        if ("master".equals(gameId)) return 60;
        if ("step".equals(gameId)) return 40;
        if ("number".equals(gameId)) return 20;
        return 100;
    }

    private void put(Map<String, Long> map, String key, long value) {
        if (value > 0) {
            map.put(key, value);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private PlayerStatsRepository.ActionCallback adapt(ActionCallback callback) {
        return new PlayerStatsRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }
}
