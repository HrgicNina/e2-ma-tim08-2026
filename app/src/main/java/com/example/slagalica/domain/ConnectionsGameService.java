package com.example.slagalica.domain;

import com.example.slagalica.data.ConnectionsRepository;
import com.example.slagalica.model.ConnectionRound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConnectionsGameService {
    public static final int ROUND_COUNT = 2;
    public static final int PAIR_COUNT = 5;
    public static final int PHASE_TIME_MILLIS = 30000;
    public static final int POINTS_PER_MATCH = 2;

    public interface RoundsCallback {
        void onLoaded(List<ConnectionRound> rounds);
    }

    private final ConnectionsRepository repository;

    public ConnectionsGameService(ConnectionsRepository repository) {
        this.repository = repository;
    }

    public void getGameRounds(RoundsCallback callback) {
        repository.getRounds(rounds -> {
            List<ConnectionRound> gameRounds = new ArrayList<>(rounds);
            Collections.shuffle(gameRounds);
            if (gameRounds.size() > ROUND_COUNT) {
                gameRounds = new ArrayList<>(gameRounds.subList(0, ROUND_COUNT));
            }
            callback.onLoaded(gameRounds);
        });
    }

    public boolean isCorrect(ConnectionRound round, int leftIndex, int rightIndex) {
        return round != null
                && leftIndex >= 0
                && leftIndex < round.getMapping().size()
                && rightIndex >= 0
                && rightIndex < PAIR_COUNT
                && round.getMapping().get(leftIndex) == rightIndex;
    }

    public boolean hasUnmatchedPairs(boolean[] matchedLeft) {
        for (boolean matched : matchedLeft) {
            if (!matched) {
                return true;
            }
        }
        return false;
    }

    public boolean isPhaseComplete(boolean[] matchedLeft, boolean[] wrongLeft) {
        for (int i = 0; i < PAIR_COUNT; i++) {
            if (!matchedLeft[i] && !wrongLeft[i]) {
                return false;
            }
        }
        return true;
    }
}
