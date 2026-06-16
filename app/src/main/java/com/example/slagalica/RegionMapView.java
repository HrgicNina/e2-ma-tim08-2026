package com.example.slagalica;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.example.slagalica.model.RegionCatalog;
import com.example.slagalica.model.RegionDefinition;
import com.example.slagalica.model.RegionPlayerPoint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RegionMapView extends WebView {
    public interface RegionClickListener {
        void onRegionClick(String region);
    }

    private static final double MIN_LON = 18.65;
    private static final double MAX_LON = 23.25;
    private static final double MIN_LAT = 41.65;
    private static final double MAX_LAT = 46.32;

    private final List<RegionPlayerPoint> points = new ArrayList<>();
    private String selectedRegion = "";
    private String myRegion = "";
    private RegionClickListener listener;

    public RegionMapView(Context context) {
        super(context);
        init();
    }

    public RegionMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        setWebViewClient(new WebViewClient());
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        addJavascriptInterface(new RegionBridge(), "AndroidRegionMap");
    }

    public void setRegionClickListener(RegionClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<RegionPlayerPoint> points, String myRegion, String selectedRegion) {
        this.points.clear();
        if (points != null) {
            this.points.addAll(points);
        }
        this.myRegion = RegionCatalog.canonicalName(value(myRegion));
        this.selectedRegion = RegionCatalog.canonicalName(value(selectedRegion));
        loadDataWithBaseURL(
                "file:///android_asset/",
                html(),
                "text/html",
                "UTF-8",
                null
        );
    }

    private String html() {
        return "<!doctype html>"
                + "<html><head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0'>"
                + "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>"
                + "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"
                + "<style>"
                + "html,body,#map{height:100%;margin:0;padding:0;background:#dceaf3;overflow:hidden;}"
                + ".leaflet-container{font-family:Arial,sans-serif;background:#dceaf3;}"
                + ".leaflet-control-attribution{font-size:9px;}"
                + "</style></head><body><div id='map'></div><script>"
                + "const points=" + pointsJson() + ";"
                + "const map=L.map('map',{zoomControl:true,attributionControl:true,dragging:true,"
                + "touchZoom:true,scrollWheelZoom:true,doubleClickZoom:true,boxZoom:true,keyboard:true,"
                + "tap:true,zoomSnap:.25,minZoom:6.5,maxZoom:11,"
                + "maxBounds:[[41.25,18.05],[46.75,23.75]],maxBoundsViscosity:.55}).setView([44.05,20.78],7.05);"
                + "L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',{"
                + "subdomains:'abcd',maxZoom:18,attribution:'&copy; OpenStreetMap &copy; CARTO'}).addTo(map);"
                + "L.imageOverlay('serbia_border_overlay.png',[[41.794,18.332],[46.402,23.409]],{"
                + "opacity:1,interactive:false}).addTo(map);"
                + "points.forEach(p=>{"
                + "L.circleMarker([p.lat,p.lon],{radius:3.5,color:'#0c2f52',weight:1,"
                + "fillColor:'#ffffff',fillOpacity:1,interactive:false}).addTo(map);"
                + "});"
                + "</script></body></html>";
    }

    private String regionsJson() {
        JSONArray array = new JSONArray();
        for (RegionDefinition def : RegionCatalog.all()) {
            JSONObject object = new JSONObject();
            try {
                object.put("name", def.name);
                object.put("label", labelFor(def.name));
                object.put("color", colorHex(def.color));
                object.put("polygon", normalizedPolygon(regionShape(def.name)));
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    private String serbiaBorderJson() {
        try {
            return coordinates(serbiaBorderCoordinates()).toString();
        } catch (JSONException ignored) {
            return "[]";
        }
    }

    private String pointsJson() {
        JSONArray array = new JSONArray();
        for (RegionPlayerPoint point : points) {
            JSONObject object = new JSONObject();
            try {
                object.put("region", RegionCatalog.canonicalName(point.region));
                object.put("lat", latFor(point.y));
                object.put("lon", lonFor(point.x));
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    private JSONArray normalizedPolygon(float[][] shape) throws JSONException {
        JSONArray array = new JSONArray();
        for (float[] point : shape) {
            JSONArray coordinate = new JSONArray();
            coordinate.put(latFor(point[1]));
            coordinate.put(lonFor(point[0]));
            array.put(coordinate);
        }
        return array;
    }

    private JSONArray coordinates(double[][] shape) throws JSONException {
        JSONArray array = new JSONArray();
        for (double[] point : shape) {
            JSONArray coordinate = new JSONArray();
            coordinate.put(point[0]);
            coordinate.put(point[1]);
            array.put(coordinate);
        }
        return array;
    }

    private float[][] regionShape(String region) {
        if ("Vojvodina".equals(region)) {
            return new float[][]{
                    {.18f, .04f}, {.55f, .03f}, {.78f, .15f}, {.83f, .28f},
                    {.73f, .38f}, {.54f, .38f}, {.42f, .34f}, {.25f, .36f},
                    {.10f, .28f}, {.11f, .14f}
            };
        }
        if ("Podrinje i Posavina".equals(region)) {
            return new float[][]{
                    {.10f, .28f}, {.25f, .36f}, {.42f, .34f}, {.47f, .44f},
                    {.44f, .56f}, {.36f, .66f}, {.25f, .70f}, {.12f, .62f},
                    {.07f, .50f}, {.10f, .39f}
            };
        }
        if ("Sumadija".equals(region)) {
            return new float[][]{
                    {.42f, .34f}, {.54f, .38f}, {.61f, .48f}, {.56f, .62f},
                    {.48f, .70f}, {.36f, .66f}, {.44f, .56f}, {.47f, .44f}
            };
        }
        if ("Timok i Branicevo".equals(region)) {
            return new float[][]{
                    {.54f, .38f}, {.73f, .38f}, {.86f, .46f}, {.90f, .62f},
                    {.82f, .76f}, {.66f, .74f}, {.56f, .62f}, {.61f, .48f}
            };
        }
        if ("Raska".equals(region)) {
            return new float[][]{
                    {.25f, .70f}, {.36f, .66f}, {.48f, .70f}, {.45f, .82f},
                    {.31f, .84f}
            };
        }
        if ("Rasina i Toplica".equals(region)) {
            return new float[][]{
                    {.48f, .70f}, {.56f, .62f}, {.66f, .74f}, {.62f, .86f},
                    {.45f, .82f}
            };
        }
        if ("Sopluk".equals(region)) {
            return new float[][]{
                    {.82f, .76f}, {.90f, .62f}, {.94f, .78f}, {.86f, .91f}
            };
        }
        if ("Juzno Pomoravlje".equals(region)) {
            return new float[][]{
                    {.66f, .74f}, {.82f, .76f}, {.86f, .91f}, {.73f, .95f},
                    {.62f, .86f}
            };
        }
        return new float[][]{
                {.31f, .84f}, {.45f, .82f}, {.62f, .86f}, {.73f, .95f},
                {.58f, .99f}, {.33f, .96f}, {.21f, .88f}
        };
    }

    private double[][] serbiaBorderCoordinates() {
        return new double[][]{
                {45.416375, 20.874313}, {45.181170, 21.483526}, {44.768947, 21.562023},
                {44.478422, 22.145088}, {44.702517, 22.459022}, {44.578003, 22.705726},
                {44.409228, 22.474008}, {44.234923, 22.657150}, {44.008063, 22.410446},
                {43.642814, 22.500157}, {43.211161, 22.986019}, {42.898519, 22.604801},
                {42.580321, 22.436595}, {42.461362, 22.545012}, {42.320260, 22.380526},
                {42.303640, 21.917080}, {42.245224, 21.576636}, {42.320250, 21.543320},
                {42.439220, 21.662920}, {42.682700, 21.775050}, {42.677170, 21.633020},
                {42.862550, 21.438660}, {42.909590, 21.274210}, {43.068685, 21.143395},
                {43.130940, 20.956510}, {43.272050, 20.814480}, {43.216710, 20.635080},
                {42.884690, 20.496790}, {42.812750, 20.257580}, {42.898520, 20.339800},
                {43.106040, 19.958570}, {43.213780, 19.630000}, {43.352290, 19.483890},
                {43.523840, 19.218520}, {43.568100, 19.454000}, {44.038470, 19.599760},
                {44.423070, 19.117610}, {44.863000, 19.368030}, {44.860230, 19.005480},
                {45.236516, 19.390476}, {45.521511, 19.072769}, {45.908880, 18.829820},
                {46.171730, 19.596045}, {46.127469, 20.220192}, {45.734573, 20.762175}
        };
    }

    private String labelFor(String region) {
        if ("Podrinje i Posavina".equals(region)) return "PODRINJE I<br>POSAVINA";
        if ("Timok i Branicevo".equals(region)) return "TIMOK I<br>BRANICEVO";
        if ("Rasina i Toplica".equals(region)) return "RASINA I<br>TOPLICA";
        if ("Juzno Pomoravlje".equals(region)) return "JUZNO<br>POMORAVLJE";
        if ("Kosovo i Metohija".equals(region)) return "KOSOVO I<br>METOHIJA";
        return region.toUpperCase(Locale.ROOT);
    }

    private double latFor(float y) {
        return MAX_LAT - clamp(y) * (MAX_LAT - MIN_LAT);
    }

    private double lonFor(float x) {
        return MIN_LON + clamp(x) * (MAX_LON - MIN_LON);
    }

    private float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private String colorHex(int color) {
        return String.format(Locale.ROOT, "#%06X", 0xFFFFFF & color);
    }

    private String jsonString(String value) {
        return JSONObject.quote(value(value));
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private final class RegionBridge {
        @JavascriptInterface
        public void onRegionClicked(String region) {
            post(() -> {
                selectedRegion = RegionCatalog.canonicalName(region);
                if (listener != null) {
                    listener.onRegionClick(selectedRegion);
                }
            });
        }
    }
}
