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
        setBackgroundColor(Color.rgb(220, 234, 243));
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setOverScrollMode(OVER_SCROLL_NEVER);
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
                + ".leaflet-overlay-pane,.leaflet-marker-pane{will-change:transform;backface-visibility:hidden;transform:translateZ(0);}"
                + ".leaflet-overlay-pane img,.leaflet-marker-icon{backface-visibility:hidden;will-change:transform;}"
                + ".player-point{filter:drop-shadow(0 0 3px #ff1744);}"
                + "</style></head><body><div id='map'></div><script>"
                + "const points=" + pointsJson() + ";"
                + "const map=L.map('map',{zoomControl:true,attributionControl:true,dragging:true,"
                + "touchZoom:true,scrollWheelZoom:true,doubleClickZoom:true,boxZoom:true,keyboard:true,"
                + "tap:true,zoomSnap:.25,minZoom:6.5,maxZoom:11,zoomAnimation:false,"
                + "fadeAnimation:false,markerZoomAnimation:false,preferCanvas:false,"
                + "maxBounds:[[41.25,18.05],[46.75,23.75]],maxBoundsViscosity:1}).setView([44.05,20.78],7.05);"
                + "L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',{"
                + "subdomains:'abcd',maxZoom:18,updateWhenIdle:false,keepBuffer:4,"
                + "attribution:'&copy; OpenStreetMap &copy; CARTO'}).addTo(map);"
                + "const addRegionHover=(layer,color)=>{"
                + "layer.on('mouseover',()=>layer.setStyle({color:color,weight:3,opacity:.95}));"
                + "layer.on('mouseout',()=>layer.setStyle({weight:0,opacity:0}));"
                + "};"
                + "const vojvodinaBounds=[[44.165,18.270],[46.395,21.770]];"
                + "const vojvodinaLayer=L.imageOverlay('vojvodina_overlay.png',vojvodinaBounds,{"
                + "opacity:1,interactive:true}).addTo(map);"
                + "vojvodinaLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Vojvodina');}});"
                + "const vojvodinaHoverLayer=L.polygon(" + vojvodinaJson() + ","
                + "{color:'#e53935',weight:0,fillColor:'#e53935',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "vojvodinaHoverLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Vojvodina');}});"
                + "addRegionHover(vojvodinaHoverLayer,'#b71c1c');"
                + "L.marker([45.55,19.89],{interactive:false,icon:L.divIcon({"
                + "className:'',html:'<div style=\"font-weight:800;color:#111;font-size:15px;"
                + "text-shadow:0 1px 2px rgba(255,255,255,.95);white-space:nowrap;\">Vojvodina</div>',"
                + "iconSize:[90,22],iconAnchor:[45,11]})}).addTo(map);"
                + "const centralBounds=[[43.40,18.45],[45.45,22.95]];"
                + "const fullSerbiaBounds=[[41.794,18.332],[46.402,23.409]];"
                + "L.imageOverlay('vojvodina_gap_overlay.png',centralBounds,{opacity:1,interactive:false}).addTo(map);"
                + "const vojvodinaGapLayer=L.polygon([[45.05,18.72],[45.12,19.72],[45.05,20.45],"
                + "[44.82,21.55],[44.55,21.42],[44.62,20.78],[44.48,20.22],[44.56,19.45],"
                + "[44.42,18.95]],{color:'#e53935',weight:0,fillColor:'#e53935',fillOpacity:.01}).addTo(map);"
                + "vojvodinaGapLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Vojvodina');}});"
                + "addRegionHover(vojvodinaGapLayer,'#b71c1c');"
                + "L.imageOverlay('raska_overlay.png',fullSerbiaBounds,{opacity:1,interactive:false}).addTo(map);"
                + "L.imageOverlay('rasina_toplica_overlay.png',fullSerbiaBounds,{opacity:1,interactive:false}).addTo(map);"
                + "L.imageOverlay('sopluk_overlay.png',fullSerbiaBounds,{opacity:1,interactive:false}).addTo(map);"
                + "L.imageOverlay('juzno_pomoravlje_overlay.png',fullSerbiaBounds,{opacity:1,interactive:false}).addTo(map);"
                + "L.imageOverlay('kosovo_metohija_overlay.png',fullSerbiaBounds,{opacity:1,interactive:false}).addTo(map);"
                + "L.imageOverlay('podrinje_overlay.png',centralBounds,{opacity:1,interactive:false}).addTo(map);"
                + "L.imageOverlay('sumadija_overlay.png',centralBounds,{opacity:1,interactive:false}).addTo(map);"
                + "L.imageOverlay('timok_overlay.png',centralBounds,{opacity:1,interactive:false}).addTo(map);"
                + "const raskaLayer=L.polygon(" + raskaJson() + ",{color:'#d1aa00',weight:0,"
                + "fillColor:'#d1aa00',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "raskaLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Raška');}});"
                + "addRegionHover(raskaLayer,'#c46200');"
                + "const rasinaToplicaLayer=L.polygon(" + rasinaToplicaJson() + ",{color:'#c4b86b',weight:0,"
                + "fillColor:'#c4b86b',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "rasinaToplicaLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Rasina i Toplica');}});"
                + "addRegionHover(rasinaToplicaLayer,'#7e57c2');"
                + "const soplukLayer=L.polygon(" + soplukJson() + ",{color:'#65b85c',weight:0,"
                + "fillColor:'#65b85c',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "soplukLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Šopluk');}});"
                + "addRegionHover(soplukLayer,'#111111');"
                + "const juznoPomoravljeLayer=L.polygon(" + juznoPomoravljeJson() + ",{color:'#9bb889',weight:0,"
                + "fillColor:'#9bb889',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "juznoPomoravljeLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Južno Pomoravlje');}});"
                + "addRegionHover(juznoPomoravljeLayer,'#1565c0');"
                + "const kosovoMetohijaLayer=L.polygon(" + kosovoMetohijaJson() + ",{color:'#8fb472',weight:0,"
                + "fillColor:'#8fb472',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "kosovoMetohijaLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Kosovo i Metohija');}});"
                + "addRegionHover(kosovoMetohijaLayer,'#5d4037');"
                + "const podrinjeLayer=L.polygon(" + podrinjePosavinaJson() + ",{color:'#2ca85d',weight:0,"
                + "fillColor:'#2ca85d',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "podrinjeLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Podrinje i Posavina');}});"
                + "addRegionHover(podrinjeLayer,'#1b7f3a');"
                + "const sumadijaLayer=L.polygon(" + sumadijaJson() + ",{color:'#777',weight:0,"
                + "fillColor:'#777',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "sumadijaLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Šumadija');}});"
                + "addRegionHover(sumadijaLayer,'#555555');"
                + "const timokLayer=L.polygon(" + timokBranicevoJson() + ",{color:'#c7a700',weight:0,"
                + "fillColor:'#c7a700',fillOpacity:.01,smoothFactor:.35}).addTo(map);"
                + "timokLayer.on('click',()=>{if(window.AndroidRegionMap){AndroidRegionMap.onRegionClicked('Timok i Braničevo');}});"
                + "addRegionHover(timokLayer,'#b08a00');"
                + "L.marker([44.32,19.66],{interactive:false,icon:L.divIcon({className:'',"
                + "html:'<div style=\"font-weight:800;color:#111;font-size:12px;text-shadow:0 1px 2px rgba(255,255,255,.95);text-align:center;line-height:12px;\">Podrinje i<br>Posavina</div>',"
                + "iconSize:[92,28],iconAnchor:[46,14]})}).addTo(map);"
                + "L.marker([44.25,20.55],{interactive:false,icon:L.divIcon({className:'',"
                + "html:'<div style=\"font-weight:800;color:#111;font-size:13px;text-shadow:0 1px 2px rgba(255,255,255,.95);white-space:nowrap;\">Šumadija</div>',"
                + "iconSize:[80,22],iconAnchor:[40,11]})}).addTo(map);"
                + "L.marker([44.17,21.75],{interactive:false,icon:L.divIcon({className:'',"
                + "html:'<div style=\"font-weight:800;color:#111;font-size:12px;text-shadow:0 1px 2px rgba(255,255,255,.95);text-align:center;line-height:12px;\">Timok i<br>Braničevo</div>',"
                + "iconSize:[92,28],iconAnchor:[46,14]})}).addTo(map);"
                + "L.marker([43.35,19.75],{interactive:false,icon:L.divIcon({className:'',"
                + "html:'<div style=\"font-weight:800;color:#111;font-size:13px;text-shadow:0 1px 2px rgba(255,255,255,.95);white-space:nowrap;\">Raška</div>',"
                + "iconSize:[70,22],iconAnchor:[35,11]})}).addTo(map);"
                + "L.marker([43.21,20.80],{interactive:false,icon:L.divIcon({className:'',"
                + "html:'<div style=\"font-weight:800;color:#111;font-size:12px;text-shadow:0 1px 2px rgba(255,255,255,.95);text-align:center;line-height:12px;\">Rasina i<br>Toplica</div>',"
                + "iconSize:[92,28],iconAnchor:[46,14]})}).addTo(map);"
                + "L.marker([43.02,22.13],{interactive:false,icon:L.divIcon({className:'',"
                + "html:'<div style=\"font-weight:800;color:#111;font-size:13px;text-shadow:0 1px 2px rgba(255,255,255,.95);white-space:nowrap;\">Šopluk</div>',"
                + "iconSize:[72,22],iconAnchor:[36,11]})}).addTo(map);"
                + "L.marker([42.58,21.39],{interactive:false,icon:L.divIcon({className:'',"
                + "html:'<div style=\"font-weight:800;color:#111;font-size:12px;text-shadow:0 1px 2px rgba(255,255,255,.95);text-align:center;line-height:12px;\">Južno<br>Pomoravlje</div>',"
                + "iconSize:[98,28],iconAnchor:[49,14]})}).addTo(map);"
                + "L.marker([42.45,20.52],{interactive:false,icon:L.divIcon({className:'',"
                + "html:'<div style=\"font-weight:800;color:#111;font-size:12px;text-shadow:0 1px 2px rgba(255,255,255,.95);text-align:center;line-height:12px;\">Kosovo i<br>Metohija</div>',"
                + "iconSize:[96,28],iconAnchor:[48,14]})}).addTo(map);"
                + "L.imageOverlay('serbia_border_overlay.png',fullSerbiaBounds,{"
                + "opacity:1,interactive:false}).addTo(map);"
                + "points.forEach(p=>{"
                + "L.circleMarker([p.lat,p.lon],{radius:4.5,color:'#ff003c',weight:1.5,"
                + "fillColor:'#ff1744',fillOpacity:1,interactive:false,className:'player-point'}).addTo(map);"
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

    private String podrinjePosavinaJson() {
        return regionPolygonJson("Podrinje i Posavina");
    }

    private String sumadijaJson() {
        return regionPolygonJson("Šumadija");
    }

    private String timokBranicevoJson() {
        return regionPolygonJson("Timok i Braničevo");
    }

    private String raskaJson() {
        return regionPolygonJson("Raška");
    }

    private String rasinaToplicaJson() {
        return regionPolygonJson("Rasina i Toplica");
    }

    private String soplukJson() {
        return regionPolygonJson("Šopluk");
    }

    private String juznoPomoravljeJson() {
        return regionPolygonJson("Južno Pomoravlje");
    }

    private String kosovoMetohijaJson() {
        return regionPolygonJson("Kosovo i Metohija");
    }

    private String vojvodinaJson() {
        return regionPolygonJson("Vojvodina");
    }

    private String regionPolygonJson(String region) {
        try {
            return normalizedPolygon(RegionCatalog.polygon(region)).toString();
        } catch (JSONException ignored) {
            return "[]";
        }
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
        return RegionCatalog.polygon(region);
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
        if ("Timok i Braničevo".equals(region)) return "TIMOK I<br>BRANIČEVO";
        if ("Rasina i Toplica".equals(region)) return "RASINA I<br>TOPLICA";
        if ("Južno Pomoravlje".equals(region)) return "JUŽNO<br>POMORAVLJE";
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
