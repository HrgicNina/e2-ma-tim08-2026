package com.example.slagalica.model;

public class RegionDefinition {
    public final String name;
    public final String icon;
    public final int color;
    public final float centerX;
    public final float centerY;
    public final float pointMinX;
    public final float pointMaxX;
    public final float pointMinY;
    public final float pointMaxY;

    public RegionDefinition(
            String name,
            String icon,
            int color,
            float centerX,
            float centerY,
            float pointMinX,
            float pointMaxX,
            float pointMinY,
            float pointMaxY
    ) {
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.centerX = centerX;
        this.centerY = centerY;
        this.pointMinX = pointMinX;
        this.pointMaxX = pointMaxX;
        this.pointMinY = pointMinY;
        this.pointMaxY = pointMaxY;
    }
}
