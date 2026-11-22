package com.ferzferaz.godstein;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Esfera Holo 3D Fibonacci interativa (inspirada no conceito AuraOrbit / Holo Sphere).
 * Renderiza aplicativos instalados em 3D com fisica de inercia suave e sem barra de pesquisa fixa.
 */
public class HoloSphereView extends View {

    public interface Listener {
        void onAppClicked(AppItem item);
        void onAppLongClicked(AppItem item);
        void onDoubleTapEmpty();
        void onSwipeUpFromBottom();
    }

    private final List<AppItem> items = new ArrayList<>();
    private final List<AppItem> renderOrder = new ArrayList<>();
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dstRect = new RectF();

    private Listener listener;
    private float density;
    private float touchSlop;

    // Fisica de rotacao
    private float velYaw = 0.0025f;   // rotacao horizontal (eixo Y)
    private float velPitch = 0.0006f; // rotacao vertical (eixo X)
    private boolean autoSpin = true;
    private boolean showLabels = true;
    private boolean isDragging = false;
    private boolean longPressed = false;

    private float downX, downY;
    private float lastX, lastY;
    private long lastMoveTime;
    private final GestureDetector gestureDetector;

    private final Comparator<AppItem> zComparator = new Comparator<AppItem>() {
        @Override
        public int compare(AppItem a, AppItem b) {
            return Float.compare(a.rz, b.rz);
        }
    };

    public HoloSphereView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(11.5f * density);
        labelPaint.setShadowLayer(4f, 0f, 1.5f, 0xCC000000);

        labelShadowPaint.setColor(0x55000000);
        labelShadowPaint.setStyle(Paint.Style.FILL);

        haloPaint.setColor(0x2838BDF8);
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setStrokeWidth(1.5f * density);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                AppItem hit = hitTest(e.getX(), e.getY());
                if (hit == null && listener != null) {
                    listener.onDoubleTapEmpty();
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (!isDragging) {
                    AppItem hit = hitTest(e.getX(), e.getY());
                    if (hit != null && listener != null) {
                        longPressed = true;
                        listener.onAppLongClicked(hit);
                    }
                }
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setAutoSpin(boolean enabled) {
        this.autoSpin = enabled;
        invalidate();
    }

    public boolean isAutoSpin() {
        return autoSpin;
    }

    public void setShowLabels(boolean enabled) {
        this.showLabels = enabled;
        invalidate();
    }

    public void setApps(List<AppItem> apps) {
        items.clear();
        renderOrder.clear();
        if (apps != null) {
            items.addAll(apps);
            renderOrder.addAll(apps);
        }
        distributeFibonacci();
        invalidate();
    }

    private void distributeFibonacci() {
        int n = items.size();
        if (n == 0) return;

        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < n; i++) {
            AppItem item = items.get(i);
            double y = 1.0 - ((2.0 * i + 1.0) / n);
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * i;

            item.bx = (float) (Math.cos(theta) * r);
            item.by = (float) y;
            item.bz = (float) (Math.sin(theta) * r);

            item.rx = item.bx;
            item.ry = item.by;
            item.rz = item.bz;
        }
    }

    private void rotateAll(float dYaw, float dPitch) {
        if (Math.abs(dYaw) < 1e-6f && Math.abs(dPitch) < 1e-6f) return;

        float cosY = (float) Math.cos(dYaw);
        float sinY = (float) Math.sin(dYaw);
        float cosX = (float) Math.cos(dPitch);
        float sinX = (float) Math.sin(dPitch);

        for (int i = 0; i < items.size(); i++) {
            AppItem item = items.get(i);
            // Rotacao em torno do eixo Y (arraste horizontal)
            float x1 = item.rx * cosY + item.rz * sinY;
            float z1 = -item.rx * sinY + item.rz * cosY;
            float y1 = item.ry;

            // Rotacao em torno do eixo X (arraste vertical)
            float y2 = y1 * cosX - z1 * sinX;
            float z2 = y1 * sinX + z1 * cosX;

            // Renormaliza suavemente para manter raio unitario exato
            float len = (float) Math.sqrt(x1 * x1 + y2 * y2 + z2 * z2);
            if (len > 0.0001f) {
                item.rx = x1 / len;
                item.ry = y2 / len;
                item.rz = z2 / len;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.48f;
        float radius = Math.min(w, h) * 0.37f;

        // Anel holo sutil de referencia
        canvas.drawCircle(cx, cy, radius * 1.06f, haloPaint);

        if (items.isEmpty()) return;

        // Atualiza fisica de rotacao quando nao esta arrastando
        if (!isDragging) {
            if (Math.abs(velYaw) > 0.0002f || Math.abs(velPitch) > 0.0002f) {
                rotateAll(velYaw, velPitch);
                velYaw *= 0.945f;
                velPitch *= 0.945f;
            } else if (autoSpin) {
                rotateAll(0.0028f, 0.0004f);
            }
        }

        // Projecao 3D -> 2D
        float baseIconSize = Math.max(36f * density, Math.min(52f * density, radius * 0.28f));
        float fov = 2.65f;

        for (int i = 0; i < items.size(); i++) {
            AppItem item = items.get(i);
            // rz in [-1 (tras), +1 (frente)]
            float persp = fov / (fov - item.rz);
            item.screenX = cx + item.rx * radius * persp;
            item.screenY = cy + item.ry * radius * persp;

            // Escala e transparencia por profundidade
            float depthNorm = (item.rz + 1f) * 0.5f; // 0 tras .. 1 frente
            item.scale = 0.46f + 0.62f * depthNorm;
            item.alpha = (int) (55 + 200 * (depthNorm * depthNorm));
            if (item.alpha > 255) item.alpha = 255;
        }

        // Ordena de tras para frente
        Collections.sort(renderOrder, zComparator);

        for (int i = 0; i < renderOrder.size(); i++) {
            AppItem item = renderOrder.get(i);
            float size = baseIconSize * item.scale;
            float half = size * 0.5f;

            iconPaint.setAlpha(item.alpha);
            Bitmap bmp = item.iconBitmap;
            if (bmp != null && !bmp.isRecycled()) {
                dstRect.set(item.screenX - half, item.screenY - half, item.screenX + half, item.screenY + half);
                canvas.drawBitmap(bmp, null, dstRect, iconPaint);
            }

            if (showLabels && item.rz > 0.08f) {
                int labelAlpha = (int) (255f * Math.min(1f, (item.rz - 0.08f) / 0.55f));
                labelPaint.setAlpha(labelAlpha);
                labelPaint.setTextSize(10.5f * density * (0.75f + 0.3f * ((item.rz + 1f) * 0.5f)));
                String txt = item.label;
                if (txt.length() > 14) {
                    txt = txt.substring(0, 13) + "…";
                }
                canvas.drawText(txt, item.screenX, item.screenY + half + 13f * density, labelPaint);
            }
        }

        // Continua animando se houver inercia ou giro automatico
        if (!isDragging && (autoSpin || Math.abs(velYaw) > 0.0002f || Math.abs(velPitch) > 0.0002f)) {
            postInvalidateOnAnimation();
        }
    }

    private AppItem hitTest(float x, float y) {
        if (renderOrder.isEmpty()) return null;
        int w = getWidth();
        int h = getHeight();
        float radius = Math.min(w, h) * 0.37f;
        float baseIconSize = Math.max(36f * density, Math.min(52f * density, radius * 0.28f));

        // Percorre de frente para tras (final da lista -> inicio)
        for (int i = renderOrder.size() - 1; i >= 0; i--) {
            AppItem item = renderOrder.get(i);
            if (item.rz < -0.15f) continue; // ignora icones muito no verso
            float half = (baseIconSize * item.scale) * 0.65f;
            if (Math.abs(x - item.screenX) <= half && Math.abs(y - item.screenY) <= half) {
                return item;
            }
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                lastX = x;
                lastY = y;
                lastMoveTime = System.currentTimeMillis();
                isDragging = false;
                longPressed = false;
                velYaw = 0f;
                velPitch = 0f;
                return true;

            case MotionEvent.ACTION_MOVE:
                float totalDx = x - downX;
                float totalDy = y - downY;
                if (!isDragging && (Math.hypot(totalDx, totalDy) > touchSlop)) {
                    isDragging = true;
                }
                if (isDragging) {
                    float dx = x - lastX;
                    float dy = y - lastY;
                    float sensitivity = 0.0045f;
                    float dYaw = dx * sensitivity;
                    float dPitch = -dy * sensitivity;
                    rotateAll(dYaw, dPitch);

                    long now = System.currentTimeMillis();
                    long dt = Math.max(8L, now - lastMoveTime);
                    velYaw = dYaw * (16f / dt);
                    velPitch = dPitch * (16f / dt);
                    lastMoveTime = now;
                    invalidate();
                }
                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_UP:
                if (!isDragging && !longPressed) {
                    AppItem hit = hitTest(x, y);
                    if (hit != null && listener != null) {
                        listener.onAppClicked(hit);
                    }
                } else if (isDragging) {
                    // Se o usuario arrastou rapido de muito perto da borda inferior para cima, abre gaveta
                    if (downY > getHeight() * 0.86f && (downY - y) > 90f * density && Math.abs(x - downX) < 120f * density) {
                        if (listener != null) {
                            velYaw = 0f;
                            velPitch = 0f;
                            listener.onSwipeUpFromBottom();
                        }
                    }
                }
                isDragging = false;
                postInvalidateOnAnimation();
                return true;

            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                postInvalidateOnAnimation();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
