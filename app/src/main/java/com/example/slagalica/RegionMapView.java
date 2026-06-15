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

    private static final double MIN_LON = 18.75;
    private static final double MAX_LON = 23.10;
    private static final double MIN_LAT = 41.75;
    private static final double MAX_LAT = 46.25;

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
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
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
        this.myRegion = value(myRegion);
        this.selectedRegion = value(selectedRegion);
        loadDataWithBaseURL(
                "https://www.openstreetmap.org/",
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
                + "html,body,#map{height:100%;margin:0;padding:0;background:#dceaf3;}"
                + ".leaflet-container{font-family:Arial,sans-serif;border-radius:12px;}"
                + ".region-label{background:rgba(255,255,255,.92);border:1px solid #153856;"
                + "border-radius:14px;color:#153856;font-size:12px;font-weight:700;padding:2px 7px;}"
                + ".leaflet-control-attribution{font-size:10px;}"
                + "</style></head><body><div id='map'></div><script>"
                + "const selectedRegion=" + jsonString(selectedRegion) + ";"
                + "const myRegion=" + jsonString(myRegion) + ";"
                + "const regions=" + regionsJson() + ";"
                + "const points=" + pointsJson() + ";"
                + "const map=L.map('map',{zoomControl:true,attributionControl:true,zoomSnap:.25,zoomDelta:.5,"
                + "maxBounds:[[41.35,18.15],[46.55,23.55]],maxBoundsViscosity:.75}).setView([44.05,20.75],7.25);"
                + "L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',{"
                + "subdomains:'abcd',maxZoom:18,attribution:'&copy; OpenStreetMap &copy; CARTO'}).addTo(map);"
                + "const bounds=[];"
                + "regions.forEach(r=>{"
                + "const mine=r.name===myRegion;"
                + "const selected=r.name===selectedRegion;"
                + "const poly=L.polygon(r.polygon,{color:selected?'#0c2f52':(mine?'#f6c65b':'#ffffff'),"
                + "weight:selected?5:(mine?4:2),fillColor:r.color,fillOpacity:.52}).addTo(map);"
                + "poly.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked(r.name);}});"
                + "poly.bindTooltip(r.icon,{permanent:true,direction:'center',className:'region-label'});"
                + "bounds.push(...r.polygon);"
                + "});"
                + "points.forEach(p=>{"
                + "const mine=p.region===myRegion;"
                + "L.circleMarker([p.lat,p.lon],{radius:mine?6:4,color:'#0c2f52',weight:1,"
                + "fillColor:mine?'#f6c65b':'#ffffff',fillOpacity:1}).addTo(map);"
                + "});"
                + "if(bounds.length){map.fitBounds(bounds,{padding:[2,2]});"
                + "setTimeout(()=>{map.panTo([44.05,20.75]);map.setZoom(Math.min(map.getZoom()+.6,7.85));},80);}"
                + "</script></body></html>";
    }

    private String regionsJson() {
        JSONArray array = new JSONArray();
        for (RegionDefinition def : RegionCatalog.all()) {
            JSONObject object = new JSONObject();
            try {
                object.put("name", def.name);
                object.put("icon", def.icon);
                object.put("color", colorHex(def.color));
                object.put("polygon", polygonFor(def.name));
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    private String pointsJson() {
        JSONArray array = new JSONArray();
        for (RegionPlayerPoint point : points) {
            JSONObject object = new JSONObject();
            try {
                object.put("region", RegionCatalog.canonicalName(value(point.region)));
                object.put("lat", latFor(point.y));
                object.put("lon", lonFor(point.x));
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    private JSONArray polygonFor(String region) throws JSONException {
        if ("Vojvodina".equals(region)) {
            return polygon(new double[][]{
                    {46.18, 18.86}, {46.18, 21.55}, {45.74, 21.42},
                    {45.34, 20.92}, {44.94, 20.47}, {44.84, 19.47}, {45.22, 18.82}
            });
        }
        if ("Beograd".equals(region)) {
            return polygon(new double[][]{
                    {45.02, 20.16}, {45.00, 20.72}, {44.66, 20.75},
                    {44.54, 20.35}, {44.72, 20.10}
            });
        }
        if ("Zapadna Srbija".equals(region)) {
            return polygon(new double[][]{
                    {44.88, 18.92}, {44.72, 20.10}, {44.54, 20.35},
                    {43.95, 20.28}, {43.18, 20.16}, {42.96, 19.98},
                    {43.28, 19.34}, {44.02, 18.88}
            });
        }
        if ("Sumadija".equals(region)) {
            return polygon(new double[][]{
                    {44.72, 20.10}, {45.00, 20.72}, {44.66, 20.75},
                    {44.30, 21.05}, {43.76, 21.02}, {43.18, 20.16},
                    {43.95, 20.28}, {44.54, 20.35}
            });
        }
        if ("Istocna Srbija".equals(region)) {
            return polygon(new double[][]{
                    {45.34, 20.92}, {45.72, 21.42}, {44.92, 22.66},
                    {43.80, 22.76}, {43.48, 21.66}, {43.76, 21.02},
                    {44.30, 21.05}, {44.66, 20.75}
            });
        }
        if ("Juzna Srbija".equals(region)) {
            return polygon(new double[][]{
                    {43.76, 21.02}, {43.48, 21.66}, {43.80, 22.76},
                    {42.54, 22.52}, {42.12, 21.58}, {42.72, 20.80},
                    {43.18, 20.16}
            });
        }
        return polygon(new double[][]{
                {43.18, 20.16}, {42.72, 20.80}, {42.58, 21.66},
                {41.88, 20.96}, {42.15, 20.06}, {42.78, 19.88}
        });
    }

    private JSONArray polygon(double[][] coordinates) throws JSONException {
        JSONArray outer = new JSONArray();
        for (double[] coordinate : coordinates) {
            JSONArray point = new JSONArray();
            point.put(coordinate[0]);
            point.put(coordinate[1]);
            outer.put(point);
        }
        return outer;
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
        return input == null ? "" : input;
    }

    private final class RegionBridge {
        @JavascriptInterface
        public void onRegionClicked(String region) {
            post(() -> {
                selectedRegion = value(region);
                if (listener != null) {
                    listener.onRegionClick(selectedRegion);
                }
            });
        }
    }
}
