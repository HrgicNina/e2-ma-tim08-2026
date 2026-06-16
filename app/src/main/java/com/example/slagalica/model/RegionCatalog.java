package com.example.slagalica.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class RegionCatalog {
    private static final RegionDefinition[] REGIONS = {
            new RegionDefinition("Vojvodina", "VO", 0xFFFFF3A5, 0.43f, 0.20f, 0.18f, 0.72f, 0.07f, 0.33f),
            new RegionDefinition("Podrinje i Posavina", "PP", 0xFFDDEFC9, 0.24f, 0.52f, 0.10f, 0.38f, 0.39f, 0.66f),
            new RegionDefinition("Sumadija", "SU", 0xFFE9F4D6, 0.50f, 0.53f, 0.38f, 0.59f, 0.40f, 0.65f),
            new RegionDefinition("Timok i Branicevo", "TB", 0xFFBEE8AE, 0.73f, 0.54f, 0.59f, 0.86f, 0.41f, 0.72f),
            new RegionDefinition("Raska", "RA", 0xFFFFED8A, 0.35f, 0.75f, 0.25f, 0.47f, 0.68f, 0.82f),
            new RegionDefinition("Rasina i Toplica", "RT", 0xFFF6F0C4, 0.57f, 0.77f, 0.48f, 0.68f, 0.71f, 0.84f),
            new RegionDefinition("Sopluk", "SO", 0xFFB7E9A7, 0.82f, 0.82f, 0.70f, 0.89f, 0.75f, 0.92f),
            new RegionDefinition("Juzno Pomoravlje", "JP", 0xFFDDECC9, 0.66f, 0.89f, 0.58f, 0.79f, 0.82f, 0.95f),
            new RegionDefinition("Kosovo i Metohija", "KM", 0xFFD3E7BC, 0.40f, 0.90f, 0.24f, 0.58f, 0.84f, 0.96f)
    };

    private RegionCatalog() {
    }

    public static List<RegionDefinition> all() {
        return Arrays.asList(REGIONS);
    }

    public static RegionDefinition find(String region) {
        String normalized = normalize(canonicalName(region));
        for (RegionDefinition item : REGIONS) {
            if (normalize(item.name).equals(normalized)) {
                return item;
            }
        }
        return REGIONS[0];
    }

    public static String canonicalName(String region) {
        String normalized = normalize(region);
        if ("sumadija i zapadna srbija".equals(normalized)) {
            return "Podrinje i Posavina";
        }
        if ("juzna i istocna srbija".equals(normalized)) {
            return "Juzno Pomoravlje";
        }
        if ("beograd".equals(normalized)) {
            return "Sumadija";
        }
        if ("zapadna srbija".equals(normalized)) {
            return "Podrinje i Posavina";
        }
        if ("istocna srbija".equals(normalized)) {
            return "Timok i Branicevo";
        }
        if ("juzna srbija".equals(normalized)) {
            return "Juzno Pomoravlje";
        }
        for (RegionDefinition item : REGIONS) {
            if (normalize(item.name).equals(normalized)) {
                return item.name;
            }
        }
        return region == null ? "" : region.trim();
    }

    public static String iconFor(String region) {
        return find(region).icon;
    }

    public static float[] randomPoint(String region) {
        return randomPoint(region, new Random());
    }

    public static float[] randomPoint(String region, Random random) {
        RegionDefinition def = find(region);
        return new float[]{
                lerp(def.pointMinX, def.pointMaxX, random.nextFloat()),
                lerp(def.pointMinY, def.pointMaxY, random.nextFloat())
        };
    }

    public static float[] stablePoint(String uid, String region) {
        String canonical = canonicalName(region);
        long seed = (uid == null ? "" : uid).hashCode() * 31L + normalize(canonical).hashCode();
        return randomPoint(canonical, new Random(seed));
    }

    public static String docId(String region) {
        return normalize(canonicalName(region)).replace(' ', '_');
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
