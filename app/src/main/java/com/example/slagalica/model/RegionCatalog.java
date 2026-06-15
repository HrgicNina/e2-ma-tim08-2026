package com.example.slagalica.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class RegionCatalog {
    private static final RegionDefinition[] REGIONS = {
            new RegionDefinition("Vojvodina", "VO", 0xFF79B7E8, 0.44f, 0.14f, 0.22f, 0.63f, 0.05f, 0.25f),
            new RegionDefinition("Beograd", "BG", 0xFFE2B15C, 0.43f, 0.34f, 0.38f, 0.50f, 0.30f, 0.39f),
            new RegionDefinition("Zapadna Srbija", "ZS", 0xFF72C58F, 0.27f, 0.54f, 0.14f, 0.36f, 0.38f, 0.66f),
            new RegionDefinition("Sumadija", "SU", 0xFFF3CC62, 0.45f, 0.53f, 0.34f, 0.54f, 0.40f, 0.64f),
            new RegionDefinition("Istocna Srbija", "IS", 0xFFE77F74, 0.68f, 0.55f, 0.56f, 0.78f, 0.38f, 0.70f),
            new RegionDefinition("Juzna Srbija", "JS", 0xFF5FC7B2, 0.55f, 0.76f, 0.39f, 0.68f, 0.65f, 0.86f),
            new RegionDefinition("Kosovo i Metohija", "KM", 0xFF9F8CE8, 0.49f, 0.91f, 0.36f, 0.61f, 0.83f, 0.97f)
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
            return "Sumadija";
        }
        if ("juzna i istocna srbija".equals(normalized)) {
            return "Juzna Srbija";
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
