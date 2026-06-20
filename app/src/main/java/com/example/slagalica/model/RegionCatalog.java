package com.example.slagalica.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class RegionCatalog {
    private static final double MAP_MIN_LON = 18.65;
    private static final double MAP_MAX_LON = 23.25;
    private static final double MAP_MIN_LAT = 41.65;
    private static final double MAP_MAX_LAT = 46.32;

    private static final RegionDefinition[] REGIONS = {
            new RegionDefinition("Vojvodina", "VO", 0xFFFFF3A5, 0.43f, 0.20f, 0.18f, 0.72f, 0.07f, 0.33f),
            new RegionDefinition("Podrinje i Posavina", "PP", 0xFFDDEFC9, 0.24f, 0.52f, 0.10f, 0.38f, 0.39f, 0.66f),
            new RegionDefinition("Šumadija", "SU", 0xFFE9F4D6, 0.50f, 0.53f, 0.38f, 0.59f, 0.40f, 0.65f),
            new RegionDefinition("Timok i Braničevo", "TB", 0xFFBEE8AE, 0.73f, 0.54f, 0.59f, 0.86f, 0.41f, 0.72f),
            new RegionDefinition("Raška", "RA", 0xFFFFED8A, 0.35f, 0.75f, 0.25f, 0.47f, 0.68f, 0.82f),
            new RegionDefinition("Rasina i Toplica", "RT", 0xFFF6F0C4, 0.57f, 0.77f, 0.48f, 0.68f, 0.71f, 0.84f),
            new RegionDefinition("Šopluk", "SO", 0xFFB7E9A7, 0.82f, 0.82f, 0.70f, 0.89f, 0.75f, 0.92f),
            new RegionDefinition("Južno Pomoravlje", "JP", 0xFFDDECC9, 0.66f, 0.89f, 0.58f, 0.79f, 0.82f, 0.95f),
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
            return "Južno Pomoravlje";
        }
        if ("beograd".equals(normalized)) {
            return "Šumadija";
        }
        if ("zapadna srbija".equals(normalized)) {
            return "Podrinje i Posavina";
        }
        if ("istocna srbija".equals(normalized)) {
            return "Timok i Braničevo";
        }
        if ("juzna srbija".equals(normalized)) {
            return "Južno Pomoravlje";
        }
        if ("sumadija".equals(normalized) || "šumadija".equals(normalized)) {
            return "Šumadija";
        }
        if ("timok i branicevo".equals(normalized) || "timok i braničevo".equals(normalized)) {
            return "Timok i Braničevo";
        }
        if ("raska".equals(normalized) || "raška".equals(normalized)) {
            return "Raška";
        }
        if ("juzno pomoravlje".equals(normalized) || "južno pomoravlje".equals(normalized)) {
            return "Južno Pomoravlje";
        }
        if ("sopluk".equals(normalized) || "šopluk".equals(normalized)) {
            return "Šopluk";
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
        float[][] polygon = polygon(def.name);
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] vertex : polygon) {
            minX = Math.min(minX, vertex[0]);
            maxX = Math.max(maxX, vertex[0]);
            minY = Math.min(minY, vertex[1]);
            maxY = Math.max(maxY, vertex[1]);
        }
        for (int attempt = 0; attempt < 256; attempt++) {
            float x = lerp(minX, maxX, random.nextFloat());
            float y = lerp(minY, maxY, random.nextFloat());
            if (containsPoint(def.name, x, y)) {
                return new float[]{x, y};
            }
        }

        float x = 0f;
        float y = 0f;
        for (float[] vertex : polygon) {
            x += vertex[0];
            y += vertex[1];
        }
        return new float[]{x / polygon.length, y / polygon.length};
    }

    public static float[] stablePoint(String uid, String region) {
        String canonical = canonicalName(region);
        long seed = (uid == null ? "" : uid).hashCode() * 31L + normalize(canonical).hashCode();
        return randomPoint(canonical, new Random(seed));
    }

    public static boolean containsPoint(String region, float x, float y) {
        float[][] polygon = polygon(region);
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            float xi = polygon[i][0];
            float yi = polygon[i][1];
            float xj = polygon[j][0];
            float yj = polygon[j][1];
            boolean crosses = (yi > y) != (yj > y)
                    && x < (xj - xi) * (y - yi) / (yj - yi) + xi;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static float[][] polygon(String region) {
        String canonical = canonicalName(region);
        if ("Vojvodina".equals(canonical)) {
            return normalizedMapPolygon(new double[][]{
                    {46.26, 18.78}, {46.30, 20.30}, {45.95, 21.30},
                    {45.30, 21.55}, {44.80, 20.90}, {44.72, 19.95},
                    {44.78, 18.95}, {45.38, 18.48}
            });
        }
        if ("Podrinje i Posavina".equals(canonical)) {
            return normalizedMapPolygon(new double[][]{
                    {44.52, 18.92}, {44.45, 19.45}, {44.15, 19.78},
                    {43.80, 19.92}, {43.55, 19.95}, {43.45, 19.62},
                    {43.56, 19.20}, {43.82, 18.86}, {44.25, 18.72}
            });
        }
        if ("Šumadija".equals(canonical)) {
            return normalizedMapPolygon(new double[][]{
                    {44.56, 19.78}, {44.62, 20.72}, {44.28, 21.05},
                    {43.85, 20.94}, {43.42, 20.74}, {43.18, 20.15},
                    {43.48, 19.94}, {43.92, 19.80}
            });
        }
        if ("Timok i Braničevo".equals(canonical)) {
            return normalizedMapPolygon(new double[][]{
                    {44.76, 20.72}, {44.84, 21.36}, {44.62, 22.20},
                    {44.15, 22.70}, {43.52, 22.55}, {43.18, 21.90},
                    {43.24, 21.10}, {43.84, 20.94}, {44.28, 21.05}
            });
        }
        if ("Raška".equals(canonical)) {
            return normalizedMapPolygon(new double[][]{
                    {43.76, 18.60}, {43.76, 20.12}, {43.54, 20.36},
                    {43.19, 20.28}, {42.88, 20.03}, {42.74, 19.45},
                    {42.94, 18.88}, {43.32, 18.60}
            });
        }
        if ("Rasina i Toplica".equals(canonical)) {
            return normalizedMapPolygon(new double[][]{
                    {43.75, 19.94}, {43.76, 21.72}, {43.44, 21.62},
                    {42.92, 21.30}, {42.66, 20.92}, {42.88, 20.03},
                    {43.19, 20.28}
            });
        }
        if ("Šopluk".equals(canonical)) {
            return normalizedMapPolygon(new double[][]{
                    {43.76, 21.24}, {43.74, 22.30}, {43.48, 22.90},
                    {42.94, 23.04}, {42.39, 22.78}, {42.24, 22.06},
                    {42.66, 21.66}, {43.38, 21.46}
            });
        }
        if ("Južno Pomoravlje".equals(canonical)) {
            return normalizedMapPolygon(new double[][]{
                    {42.92, 21.30}, {43.38, 21.46}, {42.66, 21.66},
                    {42.34, 22.02}, {42.22, 21.44}, {42.34, 20.92},
                    {42.66, 20.92}
            });
        }
        return normalizedMapPolygon(new double[][]{
                {42.74, 19.45}, {42.88, 20.03}, {42.66, 20.92},
                {42.34, 20.92}, {42.12, 20.68}, {41.86, 20.64},
                {42.10, 19.80}, {42.28, 19.20}, {42.54, 18.92}
        });
    }

    private static float[][] normalizedMapPolygon(double[][] latLonPoints) {
        float[][] polygon = new float[latLonPoints.length][2];
        for (int i = 0; i < latLonPoints.length; i++) {
            double latitude = latLonPoints[i][0];
            double longitude = latLonPoints[i][1];
            polygon[i][0] = clampNormalized(
                    (float) ((longitude - MAP_MIN_LON) / (MAP_MAX_LON - MAP_MIN_LON))
            );
            polygon[i][1] = clampNormalized(
                    (float) ((MAP_MAX_LAT - latitude) / (MAP_MAX_LAT - MAP_MIN_LAT))
            );
        }
        return polygon;
    }

    private static float clampNormalized(float value) {
        return Math.max(0f, Math.min(1f, value));
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
