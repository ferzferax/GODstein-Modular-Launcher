package com.ferzferaz.godstein;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import java.text.Normalizer;
import java.util.Locale;

public class AppItem {
    public final String label;
    public final String normalizedLabel;
    public final String packageName;
    public final String activityName;
    public final Drawable iconDrawable;
    public Bitmap iconBitmap;

    // Coordenadas base unitarias na esfera 3D de Fibonacci
    public float bx, by, bz;
    // Coordenadas rotacionadas atuais
    public float rx, ry, rz;
    // Coordenadas projetadas em 2D na tela
    public float screenX, screenY, scale;
    public int alpha;

    public AppItem(String label, String packageName, String activityName, Drawable iconDrawable, Bitmap iconBitmap) {
        this.label = label;
        this.normalizedLabel = normalize(label);
        this.packageName = packageName;
        this.activityName = activityName;
        this.iconDrawable = iconDrawable;
        this.iconBitmap = iconBitmap;
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String nfd = Normalizer.normalize(input, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase(Locale.ROOT).trim();
    }
}
