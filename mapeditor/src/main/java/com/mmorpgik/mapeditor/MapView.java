package com.mmorpgik.mapeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

public class MapView extends View {
    public interface Listener {
        void onChanged();
        void onSelect(MapData.Region r);
        void onHint(String s);
    }

    public MapData map = MapData.defaultLayout();
    public Listener listener;
    public Bitmap bg;
    public int bgAlpha = 160;
    public boolean showBg = true;
    public boolean connectMode = false;
    public int selected = -1;

    private float scale = 0.4f, offX = 0, offY = 0;
    private boolean fitted = false;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    // dokunma durumu
    private static final int NONE = 0, DRAG = 1, PAN = 2, RESIZE = 3, PINCH = 4;
    private int mode = NONE;
    private float lastX, lastY, downX, downY;
    private boolean moved;
    private int connectFrom = -1;
    private float pinchDist, pinchScale, pinchWX, pinchWY;

    public MapView(Context c) { super(c); }

    private float sx(float wx) { return wx * scale + offX; }
    private float sy(float wy) { return wy * scale + offY; }
    private float wx(float x) { return (x - offX) / scale; }
    private float wy(float y) { return (y - offY) / scale; }

    public float screenToWorldX(float x) { return wx(x); }
    public float screenToWorldY(float y) { return wy(y); }

    public MapData.Region selectedRegion() { return map.byId(selected); }

    public void fit() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        scale = Math.min(w, h) / (float) MapData.W;
        offX = (w - MapData.W * scale) / 2f;
        offY = (h - MapData.W * scale) / 2f;
        fitted = true;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (!fitted) fit();
    }

    public void setConnectMode(boolean on) {
        connectMode = on;
        connectFrom = -1;
        if (listener != null)
            listener.onHint(on ? "Yol: önce birinci bölgeye, sonra ikinciye dokun (varsa yol silinir)" : "");
        invalidate();
    }

    private MapData.Region hit(float wx, float wy) {
        for (int i = map.regions.size() - 1; i >= 0; i--) {
            MapData.Region g = map.regions.get(i);
            float dx = wx - g.x, dy = wy - g.y;
            if (dx * dx + dy * dy <= g.r * g.r) return g;
        }
        return null;
    }

    private boolean onHandle(MapData.Region g, float x, float y) {
        float hx = sx(g.x + g.r), hy = sy(g.y);
        float dx = x - hx, dy = y - hy;
        float t = 36;
        return dx * dx + dy * dy <= t * t;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2) {
            mode = PINCH;
            float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
            pinchDist = (float) Math.sqrt(dx * dx + dy * dy);
            pinchScale = scale;
            float cx = (e.getX(0) + e.getX(1)) / 2, cy = (e.getY(0) + e.getY(1)) / 2;
            pinchWX = wx(cx); pinchWY = wy(cy);
            return true;
        }
        if (mode == PINCH) {
            if (a == MotionEvent.ACTION_MOVE && e.getPointerCount() >= 2) {
                float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float cx = (e.getX(0) + e.getX(1)) / 2, cy = (e.getY(0) + e.getY(1)) / 2;
                scale = Math.max(0.15f, Math.min(2.5f, pinchScale * d / Math.max(1f, pinchDist)));
                offX = cx - pinchWX * scale;
                offY = cy - pinchWY * scale;
                invalidate();
            } else if (a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_UP
                    || a == MotionEvent.ACTION_CANCEL) {
                mode = NONE;
                if (a == MotionEvent.ACTION_POINTER_UP) mode = -1; // parmak kalktı, sonraki dokunuşa kadar bekle
            }
            return true;
        }
        float x = e.getX(), y = e.getY();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = x; downY = lastY = y; moved = false;
                MapData.Region sel = selectedRegion();
                if (!connectMode && sel != null && onHandle(sel, x, y)) { mode = RESIZE; return true; }
                MapData.Region h = hit(wx(x), wy(y));
                if (h != null && !connectMode) {
                    selected = h.id;
                    mode = DRAG;
                    if (listener != null) listener.onSelect(h);
                } else mode = PAN;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == -1) return true;
                float dx = x - lastX, dy = y - lastY;
                if (Math.abs(x - downX) > 12 || Math.abs(y - downY) > 12) moved = true;
                if (!moved) return true;
                MapData.Region s = selectedRegion();
                if (mode == DRAG && s != null) {
                    s.x += dx / scale; s.y += dy / scale;
                    s.x = Math.max(0, Math.min(MapData.W, s.x));
                    s.y = Math.max(0, Math.min(MapData.W, s.y));
                } else if (mode == RESIZE && s != null) {
                    float nr = wx(x) - s.x;
                    s.r = Math.max(30, Math.min(400, nr));
                } else if (mode == PAN) {
                    offX += dx; offY += dy;
                }
                lastX = x; lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mode == -1) { mode = NONE; return true; }
                if (!moved && a == MotionEvent.ACTION_UP) {
                    MapData.Region t = hit(wx(x), wy(y));
                    if (connectMode) {
                        if (t != null) {
                            if (connectFrom < 0) {
                                connectFrom = t.id; selected = t.id;
                                if (listener != null) listener.onHint("İkinci bölgeye dokun");
                            } else {
                                map.toggleRoad(connectFrom, t.id);
                                connectFrom = -1;
                                if (listener != null) { listener.onChanged(); listener.onHint("Yol güncellendi. Yeni yol için tekrar dokun"); }
                            }
                        }
                    } else if (t == null) {
                        selected = -1;
                        if (listener != null) listener.onSelect(null);
                    }
                } else if (moved && (mode == DRAG || mode == RESIZE)) {
                    if (listener != null) listener.onChanged();
                }
                mode = NONE;
                invalidate();
                return true;
        }
        return true;
    }

    // ---------- çizim ----------
    @Override protected void onDraw(Canvas c) {
        c.drawColor(Color.rgb(0x1B, 0x3A, 0x57));
        drawGrid(c);
        drawMap(c, true);
    }

    private void drawGrid(Canvas c) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(Color.argb(40, 255, 255, 255));
        for (int i = 0; i <= MapData.W; i += 200) {
            c.drawLine(sx(i), sy(0), sx(i), sy(MapData.W), p);
            c.drawLine(sx(0), sy(i), sx(MapData.W), sy(i), p);
        }
        p.setColor(Color.argb(120, 255, 255, 255));
        p.setStrokeWidth(3);
        c.drawRect(sx(0), sy(0), sx(MapData.W), sy(MapData.W), p);
    }

    private void drawMap(Canvas c, boolean ui) {
        if (bg != null && showBg) {
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(bgAlpha);
            rect.set(sx(0), sy(0), sx(MapData.W), sy(MapData.W));
            c.drawBitmap(bg, null, rect, p);
            p.setAlpha(255);
        }
        // kara tonu
        p.setStyle(Paint.Style.FILL);
        for (MapData.Region g : map.regions) {
            p.setColor((g.color & 0x00FFFFFF) | 0x38000000);
            c.drawCircle(sx(g.x), sy(g.y), (g.r + 80) * scale, p);
        }
        // yollar
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (MapData.Road r : map.roads) {
            MapData.Region a = map.byId(r.a), b = map.byId(r.b);
            if (a == null || b == null) continue;
            float mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
            float nx = -(b.y - a.y), ny = (b.x - a.x);
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len > 0) { nx /= len; ny /= len; }
            float bend = len * 0.08f;
            path.reset();
            path.moveTo(sx(a.x), sy(a.y));
            path.quadTo(sx(mx + nx * bend), sy(my + ny * bend), sx(b.x), sy(b.y));
            p.setColor(Color.rgb(0x6B, 0x4A, 0x2B));
            p.setStrokeWidth(Math.max(3, 16 * scale));
            c.drawPath(path, p);
            p.setColor(Color.rgb(0xD9, 0xBE, 0x8A));
            p.setStrokeWidth(Math.max(2, 10 * scale));
            c.drawPath(path, p);
        }
        // bölgeler
        for (MapData.Region g : map.regions) drawRegion(c, g, ui);
    }

    private void blob(MapData.Region g, float grow) {
        Random rnd = new Random(g.id * 7919L);
        int n = 14;
        float[] px = new float[n], py = new float[n];
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            float rr = (g.r + grow) * (0.88f + rnd.nextFloat() * 0.22f);
            px[i] = sx(g.x + (float) Math.cos(ang) * rr);
            py[i] = sy(g.y + (float) Math.sin(ang) * rr);
        }
        path.reset();
        path.moveTo((px[0] + px[n - 1]) / 2, (py[0] + py[n - 1]) / 2);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            path.quadTo(px[i], py[i], (px[i] + px[j]) / 2, (py[i] + py[j]) / 2);
        }
        path.close();
    }

    private void drawRegion(Canvas c, MapData.Region g, boolean ui) {
        boolean sel = ui && g.id == selected;
        blob(g, 0);
        p.setStyle(Paint.Style.FILL);
        p.setColor(g.color);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2, 5 * scale));
        p.setColor(darker(g.color));
        c.drawPath(path, p);
        if (sel) {
            blob(g, 14);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3, 6 * scale));
            p.setColor(Color.WHITE);
            c.drawPath(path, p);
        }
        float cx = sx(g.x), cy = sy(g.y), rs = g.r * scale;
        glyph(c, g, cx, cy, rs);
        // numara rozeti
        float br = Math.max(11, 24 * scale);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(230, 30, 30, 30));
        float bx = cx - rs * 0.62f, by = cy - rs * 0.62f;
        c.drawCircle(bx, by, br, p);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(br * 1.1f);
        c.drawText(String.valueOf(g.no), bx, by + br * 0.38f, p);
        // ad + seviye
        float ts = Math.max(13, 30 * scale);
        p.setTextSize(ts);
        p.setColor(Color.WHITE);
        p.setShadowLayer(4, 0, 0, Color.BLACK);
        c.drawText(g.name, cx, cy + rs + ts * 1.1f, p);
        if (g.level != null && g.level.length() > 0) {
            p.setTextSize(ts * 0.85f);
            c.drawText("Sv " + g.level, cx, cy + rs + ts * 2.1f, p);
        }
        p.clearShadowLayer();
        if (sel) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawCircle(sx(g.x + g.r), sy(g.y), 14, p);
            p.setColor(Color.rgb(40, 40, 40));
            c.drawCircle(sx(g.x + g.r), sy(g.y), 7, p);
        }
    }

    private void glyph(Canvas c, MapData.Region g, float cx, float cy, float rs) {
        p.setStyle(Paint.Style.FILL);
        float s = rs * 0.45f;
        if (g.type == 0) { // ağaç
            p.setColor(Color.rgb(0x6B, 0x4A, 0x2B));
            c.drawRect(cx - s * 0.12f, cy, cx + s * 0.12f, cy + s * 0.7f, p);
            p.setColor(Color.rgb(0x2E, 0x6B, 0x2E));
            c.drawCircle(cx, cy - s * 0.1f, s * 0.6f, p);
            c.drawCircle(cx - s * 0.4f, cy + s * 0.1f, s * 0.4f, p);
            c.drawCircle(cx + s * 0.4f, cy + s * 0.1f, s * 0.4f, p);
        } else if (g.type == 2) { // kule
            p.setColor(Color.argb(220, 60, 60, 60));
            c.drawRect(cx - s * 0.35f, cy - s * 0.5f, cx + s * 0.35f, cy + s * 0.6f, p);
            for (int i = -1; i <= 1; i++)
                c.drawRect(cx + i * s * 0.25f - s * 0.08f, cy - s * 0.7f, cx + i * s * 0.25f + s * 0.08f, cy - s * 0.5f, p);
        } else if (g.type == 3) { // kırmızı çatlak
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3, s * 0.18f));
            p.setColor(Color.rgb(0xE2, 0x2B, 0x2B));
            path.reset();
            path.moveTo(cx - s * 0.5f, cy - s * 0.7f);
            path.lineTo(cx + s * 0.1f, cy - s * 0.2f);
            path.lineTo(cx - s * 0.2f, cy + s * 0.1f);
            path.lineTo(cx + s * 0.5f, cy + s * 0.7f);
            c.drawPath(path, p);
        }
    }

    private static int darker(int col) {
        return Color.rgb((int) (Color.red(col) * 0.6f), (int) (Color.green(col) * 0.6f), (int) (Color.blue(col) * 0.6f));
    }

    /** Arayüzsüz PNG (px x px). */
    public Bitmap renderBitmap(int px) {
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float os = scale, ox = offX, oy = offY;
        scale = px / (float) MapData.W; offX = 0; offY = 0;
        c.drawColor(Color.rgb(0x1B, 0x3A, 0x57));
        drawMap(c, false);
        scale = os; offX = ox; offY = oy;
        return bmp;
    }
}
