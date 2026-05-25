package com.example.slagalica.model;

import java.util.HashMap;
import java.util.Map;

public class PlayerStats {
    public long matchesTotal;
    public long wins;
    public long losses;
    public long draws;

    public final Map<String, Long> pointsTotal = new HashMap<>();
    public final Map<String, Long> maxPointsTotal = new HashMap<>();
    public final Map<String, Long> plays = new HashMap<>();

    public long quizCorrect;
    public long quizWrong;
    public long quizNoAnswer;

    public long connectionsMatched;
    public long connectionsMissed;

    public final long[] associationsSolvedCounts = new long[6];
    public final long[] stepSolvedAt = new long[8];
    public final long[] mastermindAttempts = new long[7];

    public long numberExact;
    public long numberDistance1To4;
    public long numberDistance5To9;
    public long numberDistance10To19;
    public long numberDistance20To49;
    public long numberDistance50To99;
    public long numberDistance100Plus;

    public long value(String key) {
        Long value = pointsTotal.get(key);
        return value == null ? 0L : value;
    }

    public long maxValue(String key) {
        Long value = maxPointsTotal.get(key);
        return value == null ? 0L : value;
    }

    public long plays(String key) {
        Long value = plays.get(key);
        return value == null ? 0L : value;
    }
}
