package com.example.slagalica.domain;

public final class LeagueRules {
    public static final int MAX_LEAGUE = 5;
    public static final int BASE_DAILY_TOKENS = 5;

    private static final long[] THRESHOLDS = {0L, 100L, 200L, 400L, 800L, 1600L};
    private static final String[] NAMES = {"Nulta liga", "Mali pioniri", "Pioniri", "Kadeti", "Juniori", "Seniori"};
    private static final String[] ICONS = {"\uD83E\uDDB5", "\uD83D\uDEB2", "\uD83D\uDE97", "\u2708\uFE0F", "\uD83D\uDE80", "\uD83D\uDEF8"};

    private LeagueRules() {
    }

    public static long leagueForStars(long stars) {
        long safeStars = Math.max(0L, stars);
        for (int i = MAX_LEAGUE; i >= 0; i--) {
            if (safeStars >= THRESHOLDS[i]) {
                return i;
            }
        }
        return 0L;
    }

    public static long thresholdForLeague(long league) {
        return THRESHOLDS[normalize(league)];
    }

    public static long dailyTokenGrant(long league) {
        return BASE_DAILY_TOKENS + normalize(league);
    }

    public static String nameForLeague(long league) {
        return NAMES[normalize(league)];
    }

    public static String iconForLeague(long league) {
        return ICONS[normalize(league)];
    }

    public static String labelForLeague(long league) {
        long normalized = normalize(league);
        return iconForLeague(normalized) + " " + nameForLeague(normalized);
    }

    public static String stackedLabelForLeague(long league) {
        long normalized = normalize(league);
        return nameForLeague(normalized) + "\n" + iconForLeague(normalized);
    }

    public static String shortLabelForLeague(long league) {
        long normalized = normalize(league);
        return iconForLeague(normalized) + " L" + normalized;
    }

    private static int normalize(long league) {
        if (league < 0L) {
            return 0;
        }
        if (league > MAX_LEAGUE) {
            return MAX_LEAGUE;
        }
        return (int) league;
    }
}
