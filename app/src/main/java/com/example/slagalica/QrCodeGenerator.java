package com.example.slagalica;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.util.HashMap;
import java.util.Map;

public final class QrCodeGenerator {

    private QrCodeGenerator() {
    }

    public static Bitmap create(String payload, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.rgb(12, 47, 82) : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            return bitmap;
        }
    }
}
