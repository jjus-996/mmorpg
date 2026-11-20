package com.mmorpgik;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.mmorpgik.game.HeroClass;
import com.mmorpgik.game.World;

/**
 * Tüm görseller kodla çizilir: eğriler, degradeler, ışıma. Düz kare/daire sprite yok.
 * Canvas ölçeği çağıran tarafından ayarlanır; burada koordinatlar dünya birimidir.
 */
public final class Art {
    private Art() {}

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final Path PB = new Path();

    // ---------- renk yardımcıları ----------
    public static int rgb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    public static int shade(int c, float f) {
        int a = (c >>> 24) & 255, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r = Math.min(255, Math.max(0, Math.round(r * f)));
        g = Math.min(255, Math.max(0, Math.round(g * f)));
        b = Math.min(255, Math.max(0, Math.round(b * f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    public static int alpha(int c, float a) {
        int al = Math.min(255, Math.max(0, Math.round(a * 255)));
        return (al << 24) | (c & 0xFFFFFF);
    }
    public static int mix(int c1, int c2, float t) {
        int a = Math.round(((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    public static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 0xFFFFF) / (float) 0xFFFFF;
    }

    private static void fill(Canvas c, Path p, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawPath(p, P);
    }
    private static void stroke(Canvas c, Path p, int color, float w) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
        P.setColor(color);
        c.drawPath(p, P);
        P.setStyle(Paint.Style.FILL);
    }
    /** Yumuşak ışık lekesi. */
    public static void glow(Canvas c, float x, float y, float r, int color, float intensity) {
        P.setStyle(Paint.Style.FILL);
        P.setShader(new RadialGradient(x, y, Math.max(r, 1f),
                new int[]{alpha(color, intensity), alpha(color, intensity * 0.35f), alpha(color, 0f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, P);
        P.setShader(null);
    }
    /** Organik leke (daire/kare değil): kontrol noktalarıyla yuvarlatılmış gövde. */
    private static void blob(Path p, float cx, float cy, float rx, float ry, float seed) {
        p.reset();
        int n = 7;
        float[] px = new float[n], py = new float[n];
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            float k = 0.82f + 0.3f * hash((int) (seed * 100) + i, 7);
            px[i] = cx + (float) Math.cos(a) * rx * k;
            py[i] = cy + (float) Math.sin(a) * ry * k;
        }
        p.moveTo((px[0] + px[n - 1]) / 2, (py[0] + py[n - 1]) / 2);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            p.quadTo(px[i], py[i], (px[i] + px[j]) / 2, (py[i] + py[j]) / 2);
        }
        p.close();
    }

    // ---------- zemin ----------
    public static void ground(Canvas c, float camX, float camY, float vw, float vh, float time) {
        float left = camX - vw / 2, top = camY - vh / 2;
        // ana zemin rengi uzaklığa göre değişir
        float dc = (float) Math.sqrt(camX * camX + camY * camY);
        float far = Math.min(1f, dc / World.RADIUS);
        int near = rgb(255, 96, 150, 72), farC = rgb(255, 52, 84, 66);
        int base = mix(near, farC, far);
        c.drawColor(base);
        // geniş toprak/çim lekeleri
        int cell = 140;
        int x0 = (int) Math.floor(left / cell) - 1, x1 = (int) Math.ceil((left + vw) / cell) + 1;
        int y0 = (int) Math.floor(top / cell) - 1, y1 = (int) Math.ceil((top + vh) / cell) + 1;
        for (int gy = y0; gy <= y1; gy++) {
            for (int gx = x0; gx <= x1; gx++) {
                float wx = gx * cell + hash(gx, gy) * cell, wy = gy * cell + hash(gy, gx) * cell;
                float d = (float) Math.sqrt(wx * wx + wy * wy);
                float f = Math.min(1f, d / World.RADIUS);
                float h = hash(gx * 3, gy * 5);
                int col = mix(rgb(255, 118, 172, 84), rgb(255, 40, 62, 60), f);
                col = shade(col, 0.85f + h * 0.3f);
                blob(PA, wx, wy, 70 + h * 50, 38 + h * 26, h);
                fill(c, PA, alpha(col, 0.55f));
            }
        }
        // çim otları
        int tuft = 64;
        int tx0 = (int) Math.floor(left / tuft) - 1, tx1 = (int) Math.ceil((left + vw) / tuft) + 1;
        int ty0 = (int) Math.floor(top / tuft) - 1, ty1 = (int) Math.ceil((top + vh) / tuft) + 1;
        for (int gy = ty0; gy <= ty1; gy++) {
            for (int gx = tx0; gx <= tx1; gx++) {
                float h = hash(gx, gy);
                if (h < 0.35f) continue;
                float wx = gx * tuft + hash(gy, gx) * tuft, wy = gy * tuft + hash(gx + 9, gy) * tuft;
                float d = (float) Math.sqrt(wx * wx + wy * wy);
                if (d < 330f) continue; // meydan taşlı
                float f = Math.min(1f, d / World.RADIUS);
                int col = mix(rgb(255, 140, 196, 96), rgb(255, 70, 96, 98), f);
                float sway = (float) Math.sin(time * 1.6f + wx * 0.02f) * 3f;
                PA.reset();
                PA.moveTo(wx - 4, wy);
                PA.quadTo(wx - 3 + sway, wy - 9, wx - 5 + sway, wy - 15);
                PA.moveTo(wx, wy);
                PA.quadTo(wx + sway, wy - 12, wx + sway * 1.3f, wy - 19);
                PA.moveTo(wx + 4, wy);
                PA.quadTo(wx + 3 + sway, wy - 8, wx + 6 + sway, wy - 13);
                stroke(c, PA, col, 2.2f);
                if (h > 0.93f && d < 1900) { // küçük çiçekler
                    glow(c, wx + 2, wy - 8, 7, rgb(255, 255, 230, 150), 0.9f);
                }
            }
        }
    }

    /** Köy meydanı: taş zemin, yol izleri, Yaşam Pınarı. */
    public static void plaza(Canvas c, float time) {
        // dış toprak halka
        P.setStyle(Paint.Style.FILL);
        P.setShader(new RadialGradient(0, 0, World.SAFE_RADIUS + 40,
                new int[]{rgb(255, 190, 168, 128), rgb(255, 170, 148, 108), rgb(255, 130, 140, 90), alpha(rgb(255, 120, 150, 84), 0f)},
                new float[]{0f, 0.62f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, World.SAFE_RADIUS + 40, P);
        P.setShader(null);
        // taş döşeme parçaları
        for (int i = 0; i < 130; i++) {
            float a = hash(i, 1) * 6.283f;
            float d = (float) Math.sqrt(hash(i, 2)) * (World.SAFE_RADIUS - 30);
            float x = (float) Math.cos(a) * d, y = (float) Math.sin(a) * d * 0.9f;
            if (d < 120) continue;
            blob(PA, x, y, 22 + hash(i, 3) * 16, 12 + hash(i, 4) * 9, hash(i, 5));
            fill(c, PA, alpha(rgb(255, 150, 138, 120), 0.55f));
            blob(PA, x - 1, y - 2, 18 + hash(i, 3) * 12, 9 + hash(i, 4) * 6, hash(i, 5));
            fill(c, PA, alpha(rgb(255, 205, 192, 162), 0.5f));
        }
        // Dünya Ağacı çevresi: kök işlemeleri
        for (int i = 0; i < 8; i++) {
            float a = i * 0.785f + 0.2f;
            PA.reset();
            PA.moveTo(0, 0);
            float mx = (float) Math.cos(a + 0.4f) * 170, my = (float) Math.sin(a + 0.4f) * 150;
            PA.quadTo(mx, my, (float) Math.cos(a) * 300, (float) Math.sin(a) * 270);
            stroke(c, PA, rgb(255, 92, 64, 44), 15);
            stroke(c, PA, rgb(255, 128, 92, 60), 8);
        }
        // Yaşam Pınarı
        float sx = World.SPRING_X, sy = World.SPRING_Y;
        blob(PA, sx, sy + 6, 62, 34, 0.31f);
        fill(c, PA, rgb(255, 110, 100, 90));
        blob(PA, sx, sy + 2, 52, 27, 0.31f);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(sx - 40, sy - 20, sx + 40, sy + 30,
                new int[]{rgb(255, 120, 230, 200), rgb(255, 50, 160, 160)}, null, Shader.TileMode.CLAMP));
        c.drawPath(PA, P);
        P.setShader(null);
        float rp = (time * 0.6f) % 1f;
        PA.reset();
        PA.addOval(sx - 40 * rp, sy - 18 * rp, sx + 40 * rp, sy + 18 * rp, Path.Direction.CW);
        stroke(c, PA, alpha(rgb(255, 255, 255, 255), 0.6f * (1 - rp)), 2f);
        glow(c, sx, sy - 10, 70, rgb(255, 110, 255, 190), 0.55f + 0.15f * (float) Math.sin(time * 2));
    }

    // ---------- ağaçlar ----------
    public static void tree(Canvas c, float x, float y, float size, float seed, float time) {
        float s = size;
        // gölge
        blob(PA, x + 6, y + 4, 38 * s, 12 * s, seed);
        fill(c, PA, alpha(rgb(255, 10, 20, 10), 0.28f));
        // gövde
        PA.reset();
        PA.moveTo(x - 10 * s, y);
        PA.cubicTo(x - 7 * s, y - 28 * s, x - 5 * s, y - 52 * s, x - 6 * s, y - 80 * s);
        PA.lineTo(x + 6 * s, y - 80 * s);
        PA.cubicTo(x + 5 * s, y - 52 * s, x + 8 * s, y - 26 * s, x + 12 * s, y);
        PA.quadTo(x + 2 * s, y - 6 * s, x - 10 * s, y);
        P.setShader(new LinearGradient(x - 12 * s, 0, x + 12 * s, 0,
                new int[]{rgb(255, 92, 62, 40), rgb(255, 142, 98, 60), rgb(255, 80, 54, 36)}, null, Shader.TileMode.CLAMP));
        P.setStyle(Paint.Style.FILL);
        c.drawPath(PA, P);
        P.setShader(null);
        // taç: üst üste yaprak kümeleri
        float sway = (float) Math.sin(time * 0.9f + seed * 10) * 2.5f * s;
        int deep = rgb(255, 38, 94, 54), mid = rgb(255, 66, 138, 66), lite = rgb(255, 118, 186, 84);
        if (seed > 0.7f) { deep = rgb(255, 118, 86, 40); mid = rgb(255, 178, 126, 50); lite = rgb(255, 222, 170, 70); } // sonbahar
        blob(PA, x - 24 * s + sway, y - 92 * s, 34 * s, 28 * s, seed + 1);
        fill(c, PA, deep);
        blob(PA, x + 24 * s + sway, y - 94 * s, 34 * s, 28 * s, seed + 2);
        fill(c, PA, deep);
        blob(PA, x + sway, y - 120 * s, 44 * s, 36 * s, seed + 3);
        fill(c, PA, mid);
        blob(PA, x - 12 * s + sway, y - 126 * s, 28 * s, 22 * s, seed + 4);
        fill(c, PA, lite);
        blob(PA, x + 18 * s + sway, y - 112 * s, 18 * s, 12 * s, seed + 5);
        fill(c, PA, alpha(lite, 0.8f));
    }

    /** Dünya Ağacı: dev gövde, ışıyan rünler, beş güç küresi. */
    public static void greatTree(Canvas c, float time, boolean awaken) {
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 1.4f);
        // büyük ışıma
        glow(c, 0, -170, 330, rgb(255, 150, 255, 170), 0.20f * pulse + (awaken ? 0.12f : 0));
        // kökler
        for (int i = 0; i < 9; i++) {
            float a = (float) Math.PI * (0.08f + 0.105f * i);
            float dir = (float) Math.cos(a), dv = (float) Math.sin(a);
            PA.reset();
            PA.moveTo(dir * 40, -10);
            PA.cubicTo(dir * 110, 25 * dv + 10, dir * 150, 40 * dv + 32, dir * (200 + 25 * hash(i, 2)), 38 * dv + 52);
            PA.lineTo(dir * (190 + 25 * hash(i, 2)), 30 * dv + 54);
            PA.cubicTo(dir * 140, 28 * dv + 26, dir * 100, 12 * dv + 8, dir * 28, 6);
            PA.close();
            fill(c, PA, rgb(255, 84, 58, 40));
        }
        // gövde
        PA.reset();
        PA.moveTo(-86, 10);
        PA.cubicTo(-70, -60, -54, -130, -60, -250);
        PA.cubicTo(-40, -270, 40, -270, 60, -250);
        PA.cubicTo(58, -130, 76, -50, 92, 10);
        PA.quadTo(0, 34, -86, 10);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(-90, 0, 90, 0,
                new int[]{rgb(255, 70, 48, 34), rgb(255, 150, 106, 66), rgb(255, 92, 64, 42), rgb(255, 60, 42, 30)},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(PA, P);
        P.setShader(null);
        // kabuk çizgileri
        PA.reset();
        for (int i = 0; i < 6; i++) {
            float xx = -48 + i * 19;
            PA.moveTo(xx, -10);
            PA.cubicTo(xx - 6, -70, xx + 8, -150, xx + 2, -240);
        }
        stroke(c, PA, alpha(rgb(255, 40, 26, 18), 0.5f), 3f);
        // ışıyan rünler
        for (int i = 0; i < 7; i++) {
            float ry = -40 - i * 28;
            float rx = (i % 2 == 0 ? -22 : 22) + (hash(i, 8) - 0.5f) * 14;
            PA.reset();
            PA.moveTo(rx, ry);
            PA.lineTo(rx + 6, ry - 10);
            PA.lineTo(rx - 3, ry - 18);
            PA.moveTo(rx + 6, ry - 10);
            PA.lineTo(rx + 14, ry - 6);
            stroke(c, PA, alpha(rgb(255, 160, 255, 200), 0.55f + 0.4f * pulse), 3f);
            glow(c, rx + 4, ry - 9, 16, rgb(255, 120, 255, 190), 0.45f * pulse);
        }
        // dev taç
        int deep = rgb(255, 26, 86, 56), mid = rgb(255, 48, 128, 82), lite = rgb(255, 98, 186, 120);
        float sw = (float) Math.sin(time * 0.7f) * 4f;
        float[][] cl = {{-190, -300, 120, 80}, {190, -300, 120, 80}, {-120, -380, 130, 90}, {130, -380, 130, 90},
                {0, -430, 150, 100}, {-230, -380, 90, 60}, {230, -380, 90, 60}, {0, -330, 160, 80}};
        for (int i = 0; i < cl.length; i++) {
            blob(PA, cl[i][0] + sw, cl[i][1], cl[i][2], cl[i][3], 0.1f * i);
            fill(c, PA, deep);
        }
        for (int i = 0; i < cl.length; i++) {
            blob(PA, cl[i][0] + sw - 10, cl[i][1] - 10, cl[i][2] * 0.78f, cl[i][3] * 0.7f, 0.2f * i + 1);
            fill(c, PA, mid);
        }
        for (int i = 2; i < cl.length - 1; i++) {
            blob(PA, cl[i][0] + sw - 22, cl[i][1] - 22, cl[i][2] * 0.45f, cl[i][3] * 0.38f, 0.3f * i + 2);
            fill(c, PA, alpha(lite, 0.85f));
        }
        // beş güç küresi (beş sınıf)
        HeroClass[] hs = HeroClass.values();
        for (int i = 0; i < 5; i++) {
            float a = time * 0.5f + i * 1.2566f;
            float ox = (float) Math.cos(a) * 210, oy = -300 + (float) Math.sin(a) * 52 - 40;
            glow(c, ox, oy, 46, hs[i].color, 0.85f * (awaken ? 1f : 0.7f));
            glow(c, ox, oy, 14, rgb(255, 255, 255, 255), 0.9f);
        }
        // düşen parıltılı yapraklar
        for (int i = 0; i < 10; i++) {
            float t = (time * 0.16f + i * 0.1f) % 1f;
            float lx = -240 + hash(i, 3) * 480 + (float) Math.sin(t * 9 + i) * 24;
            float ly = -330 + t * 330;
            PA.reset();
            PA.moveTo(lx, ly);
            PA.quadTo(lx + 6, ly - 4, lx + 10, ly + 2);
            PA.quadTo(lx + 4, ly + 6, lx, ly);
            fill(c, PA, alpha(rgb(255, 170, 240, 150), 1f - t));
        }
    }

    // ---------- evler ----------
    public static void house(Canvas c, float x, float y, float w, int roof, float seed) {
        float h = w * 0.62f;
        blob(PA, x, y + 4, w * 0.62f, 12, seed);
        fill(c, PA, alpha(rgb(255, 10, 20, 10), 0.3f));
        // duvar
        PA.reset();
        PA.moveTo(x - w / 2, y);
        PA.lineTo(x - w / 2 + 2, y - h);
        PA.quadTo(x, y - h - 6, x + w / 2 - 2, y - h);
        PA.lineTo(x + w / 2, y);
        PA.quadTo(x, y + 8, x - w / 2, y);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, y - h, 0, y,
                new int[]{rgb(255, 214, 190, 150), rgb(255, 170, 138, 100)}, null, Shader.TileMode.CLAMP));
        c.drawPath(PA, P);
        P.setShader(null);
        // ahşap kirişler
        PB.reset();
        for (int i = 1; i < 4; i++) {
            PB.moveTo(x - w / 2 + w * i / 4f, y - h + 2);
            PB.lineTo(x - w / 2 + w * i / 4f, y - 2);
        }
        stroke(c, PB, rgb(255, 98, 66, 44), 4f);
        // kapı
        PA.reset();
        PA.moveTo(x - 10, y);
        PA.lineTo(x - 10, y - 30);
        PA.quadTo(x, y - 42, x + 10, y - 30);
        PA.lineTo(x + 10, y);
        PA.close();
        fill(c, PA, rgb(255, 92, 60, 40));
        glow(c, x + w * 0.28f, y - h * 0.55f, 22, rgb(255, 255, 210, 120), 0.8f); // pencere ışığı
        // çatı
        PA.reset();
        PA.moveTo(x - w / 2 - 14, y - h + 8);
        PA.quadTo(x - w * 0.18f, y - h - 22, x, y - h - 56);
        PA.quadTo(x + w * 0.18f, y - h - 22, x + w / 2 + 14, y - h + 8);
        PA.quadTo(x, y - h - 6, x - w / 2 - 14, y - h + 8);
        P.setShader(new LinearGradient(0, y - h - 56, 0, y - h + 8,
                new int[]{shade(roof, 1.15f), shade(roof, 0.8f)}, null, Shader.TileMode.CLAMP));
        P.setStyle(Paint.Style.FILL);
        c.drawPath(PA, P);
        P.setShader(null);
        // çatı sıraları
        PB.reset();
        for (int i = 1; i < 4; i++) {
            float yy = y - h - 56 + i * 15;
            PB.moveTo(x - w * 0.12f * i - 4, yy + 2);
            PB.quadTo(x, yy - 4, x + w * 0.12f * i + 4, yy + 2);
        }
        stroke(c, PB, alpha(shade(roof, 0.6f), 0.7f), 2f);
        // baca dumanı
        float t = (float) ((System.nanoTime() / 1e9) % 3.0) / 3f;
        glow(c, x + w * 0.3f + t * 10, y - h - 50 - t * 40, 10 + t * 14, rgb(255, 220, 220, 220), 0.5f * (1 - t));
    }

    // ---------- insanlar ----------
    public static final int[] HAIR = {rgb(255, 50, 34, 26), rgb(255, 120, 70, 36), rgb(255, 226, 190, 98), rgb(255, 210, 210, 214),
            rgb(255, 150, 60, 66), rgb(255, 58, 58, 120), rgb(255, 30, 30, 36)};
    public static final int[] SKIN = {rgb(255, 255, 224, 196), rgb(255, 238, 192, 154), rgb(255, 206, 150, 112),
            rgb(255, 160, 106, 76), rgb(255, 112, 74, 54)};
    public static final int[] CLOAK = {rgb(255, 150, 70, 60), rgb(255, 64, 100, 160), rgb(255, 80, 130, 84), rgb(255, 120, 90, 150),
            rgb(255, 180, 140, 70), rgb(255, 80, 80, 90), rgb(255, 170, 60, 100)};

    /** Karakter çizimi. (x,y) ayak noktası. */
    public static void person(Canvas c, float x, float y, boolean faceRight, float walk, boolean moving,
                              int hair, int skin, int cloak, int hairStyle, HeroClass hero,
                              float atk, float scale, boolean hurt, float time) {
        c.save();
        c.translate(x, y);
        c.scale(faceRight ? scale : -scale, scale);
        float bob = moving ? (float) Math.abs(Math.sin(walk)) * 3f : (float) Math.sin(time * 2f) * 1f;
        float step = moving ? (float) Math.sin(walk) * 7f : 0f;
        // gölge
        blob(PA, 0, 2, 20, 7, 0.4f);
        fill(c, PA, alpha(rgb(255, 10, 20, 10), 0.32f));
        // bacaklar
        PA.reset();
        PA.moveTo(-6, -22); PA.quadTo(-7 + step, -10, -8 + step, 0);
        PA.moveTo(6, -22); PA.quadTo(7 - step, -10, 8 - step, 0);
        stroke(c, PA, rgb(255, 60, 44, 34), 6f);
        // pelerin / gövde
        int body = hurt ? rgb(255, 255, 150, 150) : cloak;
        PA.reset();
        PA.moveTo(-12, -62 - bob);
        PA.cubicTo(-20, -40, -22, -22, -18, -8);
        PA.quadTo(0, -2, 18, -8);
        PA.cubicTo(22, -22, 20, -40, 12, -62 - bob);
        PA.quadTo(0, -68 - bob, -12, -62 - bob);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(-20, -66, 20, -4,
                new int[]{shade(body, 1.25f), shade(body, 0.75f)}, null, Shader.TileMode.CLAMP));
        c.drawPath(PA, P);
        P.setShader(null);
        // kemer ve sınıf işareti
        PA.reset();
        PA.moveTo(-14, -34 - bob * 0.5f);
        PA.quadTo(0, -30 - bob * 0.5f, 14, -34 - bob * 0.5f);
        stroke(c, PA, rgb(255, 90, 62, 40), 3f);
        if (hero != null) glow(c, 0, -46 - bob, 9, hero.color, 0.9f);
        // kol (silah kolu)
        float aa = atk > 0 ? (float) Math.sin(atk * 11f) * 16f : 0f;
        PA.reset();
        PA.moveTo(10, -58 - bob);
        PA.quadTo(20 + aa * 0.3f, -46, 24 + aa, -40 + aa * 0.4f);
        stroke(c, PA, shade(body, 0.9f), 6f);
        weapon(c, hero, 24 + aa, -40 + aa * 0.4f, atk, time);
        // diğer kol
        PA.reset();
        PA.moveTo(-10, -58 - bob);
        PA.quadTo(-19, -46, -17, -38);
        stroke(c, PA, shade(body, 0.8f), 6f);
        // baş
        PA.reset();
        PA.moveTo(-9, -74 - bob);
        PA.cubicTo(-10, -86 - bob, 10, -88 - bob, 9, -74 - bob);
        PA.cubicTo(8, -64 - bob, -8, -64 - bob, -9, -74 - bob);
        fill(c, PA, skin);
        // saç
        PA.reset();
        switch (hairStyle % 4) {
            case 0: // kısa
                PA.moveTo(-10, -76 - bob);
                PA.cubicTo(-12, -92 - bob, 12, -92 - bob, 10, -76 - bob);
                PA.quadTo(4, -82 - bob, -2, -80 - bob);
                PA.quadTo(-7, -80 - bob, -10, -76 - bob);
                break;
            case 1: // uzun
                PA.moveTo(-11, -76 - bob);
                PA.cubicTo(-13, -94 - bob, 13, -94 - bob, 11, -76 - bob);
                PA.quadTo(15, -56 - bob, 8, -50 - bob);
                PA.quadTo(9, -66 - bob, 5, -78 - bob);
                PA.quadTo(-2, -82 - bob, -6, -76 - bob);
                PA.quadTo(-8, -56 - bob, -10, -52 - bob);
                PA.quadTo(-14, -62 - bob, -11, -76 - bob);
                break;
            case 2: // topuz
                PA.moveTo(-10, -77 - bob);
                PA.cubicTo(-12, -92 - bob, 12, -92 - bob, 10, -77 - bob);
                PA.quadTo(0, -83 - bob, -10, -77 - bob);
                PA.moveTo(-3, -90 - bob);
                PA.cubicTo(-8, -102 - bob, 8, -102 - bob, 3, -90 - bob);
                break;
            default: // dikenli
                PA.moveTo(-10, -77 - bob);
                PA.lineTo(-12, -90 - bob); PA.lineTo(-5, -86 - bob);
                PA.lineTo(-3, -98 - bob); PA.lineTo(2, -88 - bob);
                PA.lineTo(8, -96 - bob); PA.lineTo(8, -84 - bob);
                PA.lineTo(12, -88 - bob); PA.lineTo(10, -77 - bob);
                PA.quadTo(0, -82 - bob, -10, -77 - bob);
                break;
        }
        fill(c, PA, hair);
        // yüz: göz ve ağız
        PB.reset();
        PB.moveTo(3, -75 - bob); PB.lineTo(6, -75 - bob);
        stroke(c, PB, rgb(255, 40, 30, 30), 2f);
        PB.reset();
        PB.moveTo(2, -69 - bob); PB.quadTo(5, -67 - bob, 7, -69 - bob);
        stroke(c, PB, alpha(rgb(255, 120, 60, 60), 0.7f), 1.5f);
        c.restore();
    }

    private static void weapon(Canvas c, HeroClass hero, float hx, float hy, float atk, float time) {
        if (hero == null) return;
        c.save();
        c.translate(hx, hy);
        switch (hero) {
            case WARRIOR: {
                float rot = atk > 0 ? -70f + atk * 250f : -20f;
                c.rotate(rot);
                PA.reset();
                PA.moveTo(0, 6); PA.lineTo(0, -46);
                stroke(c, PA, rgb(255, 104, 72, 46), 5f);
                PA.reset();
                PA.moveTo(-14, -46);
                PA.quadTo(-16, -62, 0, -64);
                PA.quadTo(16, -62, 14, -46);
                PA.quadTo(0, -40, -14, -46);
                P.setStyle(Paint.Style.FILL);
                P.setShader(new LinearGradient(-14, -64, 14, -44,
                        new int[]{rgb(255, 214, 214, 224), rgb(255, 110, 112, 126)}, null, Shader.TileMode.CLAMP));
                c.drawPath(PA, P);
                P.setShader(null);
                glow(c, 0, -54, 14, rgb(255, 255, 160, 80), 0.5f);
                break;
            }
            case ROGUE: {
                // yay
                float draw = atk > 0 ? 6f : 0f;
                PA.reset();
                PA.moveTo(8, -44);
                PA.cubicTo(-14 - draw, -26, -14 - draw, 4, 8, 24);
                stroke(c, PA, rgb(255, 120, 82, 48), 4f);
                PA.reset();
                PA.moveTo(8, -44); PA.lineTo(-4 - draw * 2, -10); PA.lineTo(8, 24);
                stroke(c, PA, rgb(255, 230, 230, 230), 1.2f);
                if (atk > 0) glow(c, 14, -10, 12, rgb(255, 255, 230, 140), 0.8f);
                break;
            }
            case MAGE: {
                PA.reset();
                PA.moveTo(0, 22); PA.lineTo(0, -56);
                stroke(c, PA, rgb(255, 96, 78, 110), 4f);
                PA.reset();
                PA.moveTo(0, -56); PA.quadTo(-12, -64, -6, -74); PA.quadTo(0, -68, 6, -74); PA.quadTo(12, -64, 0, -56);
                stroke(c, PA, rgb(255, 150, 130, 190), 3f);
                glow(c, 0, -66, 24 + (atk > 0 ? 14 : 0), rgb(255, 80, 230, 240), 0.9f);
                break;
            }
            case PRIEST: {
                PA.reset();
                PA.moveTo(0, 22); PA.lineTo(0, -58);
                stroke(c, PA, rgb(255, 214, 180, 100), 4f);
                PA.reset();
                PA.moveTo(0, -78); PA.quadTo(10, -66, 0, -52); PA.quadTo(-10, -66, 0, -78);
                fill(c, PA, rgb(255, 255, 240, 160));
                glow(c, 0, -65, 26 + (atk > 0 ? 14 : 0), rgb(255, 255, 220, 100), 0.9f);
                break;
            }
            case SUMMONER: {
                PA.reset();
                PA.moveTo(0, 22); PA.lineTo(0, -48);
                stroke(c, PA, rgb(255, 110, 66, 50), 4f);
                float fl = (float) Math.sin(time * 9f) * 3f;
                PA.reset();
                PA.moveTo(-8, -48); PA.quadTo(-10 + fl, -66, 0, -78 + fl); PA.quadTo(10 - fl, -66, 8, -48); PA.quadTo(0, -42, -8, -48);
                fill(c, PA, rgb(255, 255, 140, 40));
                glow(c, 0, -58, 26 + (atk > 0 ? 14 : 0), rgb(255, 255, 110, 30), 0.85f);
                break;
            }
        }
        c.restore();
    }

    // ---------- canavarlar ----------
    public static void monster(Canvas c, World.Monster m, float time) {
        float x = m.x, y = m.y;
        float ph = m.phase;
        c.save();
        c.translate(x, y);
        if (m.faceX < 0 && m.kind == 1) c.scale(-1, 1);
        boolean hurt = m.hurt > 0;
        blob(PA, 0, 3, 24 + m.kind * 8, 8, 0.7f);
        fill(c, PA, alpha(rgb(255, 10, 10, 20), 0.35f));
        switch (m.kind) {
            case 0: { // Çürük Tohum
                float sq = (float) Math.sin(ph * 3f) * 2f;
                PA.reset();
                PA.moveTo(0, -4);
                PA.cubicTo(-26, -6, -24, -40 - sq, 0, -50 - sq);
                PA.cubicTo(24, -40 - sq, 26, -6, 0, -4);
                P.setStyle(Paint.Style.FILL);
                P.setShader(new LinearGradient(-20, -50, 20, 0,
                        hurt ? new int[]{rgb(255, 255, 200, 200), rgb(255, 240, 120, 120)}
                                : new int[]{rgb(255, 150, 108, 72), rgb(255, 70, 46, 56)}, null, Shader.TileMode.CLAMP));
                c.drawPath(PA, P);
                P.setShader(null);
                // çatlak
                PA.reset();
                PA.moveTo(-4, -46); PA.lineTo(2, -34); PA.lineTo(-3, -22); PA.lineTo(3, -10);
                stroke(c, PA, rgb(255, 34, 20, 30), 2f);
                // filiz
                PA.reset();
                PA.moveTo(0, -50 - sq); PA.quadTo(-4, -64, 10, -72 + sq);
                stroke(c, PA, rgb(255, 120, 150, 70), 3f);
                PA.reset();
                PA.moveTo(10, -72 + sq); PA.quadTo(24, -76, 22, -62); PA.quadTo(14, -64, 10, -72 + sq);
                fill(c, PA, rgb(255, 150, 110, 150));
                // kırmızı gözler
                glow(c, -8, -30, 9, rgb(255, 255, 40, 40), 1f);
                glow(c, 8, -30, 9, rgb(255, 255, 40, 40), 1f);
                break;
            }
            case 1: { // Kara Kurt
                float run = (float) Math.sin(ph * 8f) * 5f;
                PA.reset();
                PA.moveTo(-34, -22);
                PA.cubicTo(-30, -44, -6, -46, 10, -40);
                PA.cubicTo(18, -48, 28, -48, 34, -40);
                PA.lineTo(46, -38);
                PA.lineTo(34, -32);
                PA.cubicTo(28, -22, 14, -18, 6, -16);
                PA.lineTo(4, -2 + run);
                PA.lineTo(-2, -2 + run);
                PA.lineTo(-6, -16);
                PA.lineTo(-20, -16);
                PA.lineTo(-24, -2 - run);
                PA.lineTo(-30, -2 - run);
                PA.lineTo(-28, -16);
                PA.quadTo(-38, -16, -34, -22);
                P.setStyle(Paint.Style.FILL);
                P.setShader(new LinearGradient(0, -48, 0, 0,
                        hurt ? new int[]{rgb(255, 255, 190, 190), rgb(255, 230, 110, 110)}
                                : new int[]{rgb(255, 70, 70, 92), rgb(255, 28, 28, 40)}, null, Shader.TileMode.CLAMP));
                c.drawPath(PA, P);
                P.setShader(null);
                // kulak
                PA.reset();
                PA.moveTo(20, -44); PA.lineTo(24, -58); PA.lineTo(30, -44);
                fill(c, PA, rgb(255, 44, 44, 60));
                // kuyruk
                PA.reset();
                PA.moveTo(-32, -26); PA.quadTo(-48, -34 + run, -52, -48);
                stroke(c, PA, rgb(255, 50, 50, 70), 5f);
                glow(c, 30, -38, 8, rgb(255, 230, 60, 255), 1f);
                break;
            }
            default: { // Yozlaşmış Ent
                float sw = (float) Math.sin(ph * 1.5f) * 4f;
                PA.reset();
                PA.moveTo(-26, 0);
                PA.cubicTo(-30, -40, -22, -70, -18, -96);
                PA.cubicTo(-6, -110, 10, -110, 18, -96);
                PA.cubicTo(22, -70, 30, -40, 26, 0);
                PA.quadTo(0, 10, -26, 0);
                P.setStyle(Paint.Style.FILL);
                P.setShader(new LinearGradient(-28, 0, 28, 0,
                        hurt ? new int[]{rgb(255, 250, 190, 190), rgb(255, 240, 120, 120)}
                                : new int[]{rgb(255, 56, 40, 52), rgb(255, 110, 84, 82), rgb(255, 44, 32, 46)}, null, Shader.TileMode.CLAMP));
                c.drawPath(PA, P);
                P.setShader(null);
                // dallar (kollar)
                PA.reset();
                PA.moveTo(-18, -80); PA.cubicTo(-44, -84 + sw, -52, -60, -58, -46 + sw);
                PA.moveTo(18, -80); PA.cubicTo(44, -84 - sw, 52, -60, 58, -46 - sw);
                stroke(c, PA, rgb(255, 70, 52, 62), 9f);
                // çürük taç
                blob(PA, 0, -118, 30, 20, 0.9f);
                fill(c, PA, rgb(255, 78, 52, 100));
                blob(PA, 8, -124, 16, 10, 0.4f);
                fill(c, PA, rgb(255, 118, 70, 130));
                glow(c, -8, -86, 11, rgb(255, 255, 50, 90), 1f);
                glow(c, 8, -86, 11, rgb(255, 255, 50, 90), 1f);
                break;
            }
        }
        c.restore();
        // can çubuğu + seviye
        if (m.hp < m.maxHp) {
            float w = 50, hh = 5, by = y - (m.kind == 2 ? 140 : (m.kind == 1 ? 70 : 82));
            PA.reset();
            PA.addRoundRect(x - w / 2, by, x + w / 2, by + hh, 3, 3, Path.Direction.CW);
            fill(c, PA, alpha(rgb(255, 20, 10, 10), 0.7f));
            PA.reset();
            PA.addRoundRect(x - w / 2, by, x - w / 2 + w * Math.max(0, m.hp / m.maxHp), by + hh, 3, 3, Path.Direction.CW);
            fill(c, PA, rgb(255, 224, 60, 60));
        }
    }

    // ---------- efektler ----------
    public static void fx(Canvas c, World.Fx f, Paint text) {
        float t = f.t / f.dur;
        int col = f.color;
        switch (f.type) {
            case World.Fx.RING: {
                float r = f.r * (0.25f + 0.75f * t);
                PA.reset();
                PA.addOval(f.x - r, f.y - r * 0.55f, f.x + r, f.y + r * 0.55f, Path.Direction.CW);
                stroke(c, PA, alpha(col, 1f - t), 6f * (1 - t) + 1f);
                glow(c, f.x, f.y, r * 0.9f, col, 0.35f * (1 - t));
                break;
            }
            case World.Fx.BLAST: {
                float r = f.r * (0.4f + 0.8f * t);
                glow(c, f.x, f.y, r, col, 0.9f * (1 - t));
                glow(c, f.x, f.y, r * 0.4f, rgb(255, 255, 255, 255), 0.8f * (1 - t));
                for (int i = 0; i < 6; i++) {
                    float a = i * 1.047f + hash(i, (int) f.x) * 2f;
                    float d = r * (0.6f + 0.6f * t);
                    glow(c, f.x + (float) Math.cos(a) * d, f.y + (float) Math.sin(a) * d * 0.7f, 8, col, 1f - t);
                }
                break;
            }
            case World.Fx.SPARK: {
                float r = f.r * (0.3f + t);
                glow(c, f.x, f.y, r, col, 0.8f * (1 - t));
                break;
            }
            case World.Fx.BOLT_STRIKE: {
                // gökten inen ışık sütunu + yer patlaması
                float a = 1f - t;
                float topY = f.y - 420f;
                PA.reset();
                PA.moveTo(f.x - 4, topY);
                PA.lineTo(f.x + 14, topY + (f.y - topY) * 0.3f);
                PA.lineTo(f.x - 10, topY + (f.y - topY) * 0.55f);
                PA.lineTo(f.x + 8, f.y);
                stroke(c, PA, alpha(rgb(255, 255, 255, 255), a), 5f);
                stroke(c, PA, alpha(col, a * 0.7f), 13f);
                glow(c, f.x, f.y, f.r, col, 0.9f * a);
                glow(c, f.x, f.y, f.r * 0.45f, rgb(255, 255, 255, 255), 0.9f * a);
                break;
            }
            case World.Fx.HEAL: {
                for (int i = 0; i < 9; i++) {
                    float a = hash(i, 4) * 6.28f;
                    float rx = (float) Math.cos(a) * f.r * 0.5f;
                    float yy = f.y + 10 - t * 90 * (0.6f + hash(i, 5));
                    glow(c, f.x + rx, yy, 9, col, 1f - t);
                }
                glow(c, f.x, f.y - 20, f.r, col, 0.3f * (1 - t));
                break;
            }
            case World.Fx.TEXT: {
                text.setColor(alpha(f.color, 1f - t * t));
                c.drawText(f.text, f.x, f.y, text);
                break;
            }
        }
    }

    public static void projectile(Canvas c, World.Projectile pr, float time) {
        int col = pr.hero == null ? rgb(255, 255, 255, 255) : pr.hero.color;
        float len = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy);
        float dx = pr.vx / len, dy = pr.vy / len;
        for (int i = 5; i >= 1; i--) {
            glow(c, pr.x - dx * i * 12, pr.y - dy * i * 12, 12 - i, col, 0.18f * (6 - i));
        }
        glow(c, pr.x, pr.y, 20, col, 0.95f);
        glow(c, pr.x, pr.y, 8, rgb(255, 255, 255, 255), 1f);
        if (pr.hero == HeroClass.ROGUE) {
            PA.reset();
            PA.moveTo(pr.x - dx * 20, pr.y - dy * 20);
            PA.lineTo(pr.x + dx * 14, pr.y + dy * 14);
            stroke(c, PA, rgb(255, 240, 240, 240), 3f);
        }
    }

    /** Yeşil ruh: ölünce Yaşam Pınarı'na giden ışık. */
    public static void greenSpirit(Canvas c, float x, float y, float time) {
        float bob = (float) Math.sin(time * 6f) * 5f;
        glow(c, x, y - 30 + bob, 46, rgb(255, 90, 255, 150), 0.7f);
        PA.reset();
        PA.moveTo(x, y - 52 + bob);
        PA.cubicTo(x - 16, y - 36 + bob, x - 12, y - 12 + bob, x, y - 8 + bob);
        PA.cubicTo(x + 12, y - 12 + bob, x + 16, y - 36 + bob, x, y - 52 + bob);
        fill(c, PA, alpha(rgb(255, 190, 255, 210), 0.85f));
        glow(c, x - 4, y - 36 + bob, 5, rgb(255, 20, 80, 40), 1f);
        glow(c, x + 5, y - 36 + bob, 5, rgb(255, 20, 80, 40), 1f);
    }

    /** Çağrılan Phoenix. */
    public static void phoenix(Canvas c, World.Summon s, float time) {
        float fl = (float) Math.sin(time * 10f);
        float x = s.x, y = s.y - 40 + (float) Math.sin(time * 3f) * 5f;
        glow(c, x, y, 90, rgb(255, 255, 120, 30), 0.55f);
        // kanatlar
        for (int side = -1; side <= 1; side += 2) {
            PA.reset();
            PA.moveTo(x, y);
            PA.cubicTo(x + side * 30, y - 30 - fl * 12, x + side * 60, y - 30 - fl * 18, x + side * 74, y - 6 - fl * 24);
            PA.cubicTo(x + side * 52, y - 6, x + side * 38, y + 8, x, y + 14);
            P.setStyle(Paint.Style.FILL);
            P.setShader(new LinearGradient(x, y, x + side * 74, y - 20,
                    new int[]{rgb(255, 255, 230, 90), rgb(255, 240, 90, 30), alpha(rgb(255, 200, 30, 30), 0.3f)}, null, Shader.TileMode.CLAMP));
            c.drawPath(PA, P);
            P.setShader(null);
        }
        // gövde ve kuyruk
        PA.reset();
        PA.moveTo(x, y - 12);
        PA.cubicTo(x - 12, y - 4, x - 8, y + 14, x, y + 20);
        PA.cubicTo(x + 8, y + 14, x + 12, y - 4, x, y - 12);
        fill(c, PA, rgb(255, 255, 200, 60));
        PA.reset();
        PA.moveTo(x, y + 18);
        PA.quadTo(x - 14, y + 44, x - 6, y + 66);
        PA.moveTo(x, y + 18);
        PA.quadTo(x + 4, y + 46, x + 12, y + 62);
        stroke(c, PA, rgb(255, 250, 120, 40), 5f);
        // gaga
        PA.reset();
        PA.moveTo(x + 2, y - 12); PA.lineTo(x + 12, y - 8); PA.lineTo(x + 2, y - 4);
        fill(c, PA, rgb(255, 255, 255, 190));
    }

    // ---------- Sinematik ----------
    public static void cinematicBackdrop(Canvas c, float w, float h, float t, float flash) {
        // gece gökyüzü
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{rgb(255, 10, 8, 20), rgb(255, 40, 24, 56), rgb(255, 18, 14, 26)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, P);
        P.setShader(null);
        // yıkık zemin
        PA.reset();
        PA.moveTo(0, h * 0.82f);
        PA.cubicTo(w * 0.25f, h * 0.76f, w * 0.5f, h * 0.88f, w * 0.75f, h * 0.8f);
        PA.quadTo(w * 0.9f, h * 0.77f, w, h * 0.84f);
        PA.lineTo(w, h); PA.lineTo(0, h); PA.close();
        fill(c, PA, rgb(255, 14, 12, 18));
        if (flash > 0) c.drawColor(alpha(rgb(255, 210, 200, 255), flash * 0.45f));
    }

    /** Kundun: dev, karanlık, parçalanan bir kütle. */
    public static void kundun(Canvas c, float cx, float cy, float size, float t, float rage) {
        float breath = (float) Math.sin(t * 1.3f) * 0.03f;
        c.save();
        c.translate(cx, cy);
        c.scale(size * (1 + breath), size * (1 + breath));
        glow(c, 0, -40, 320, rgb(255, 150, 20, 40), 0.25f + 0.25f * rage);
        // ana kütle
        PA.reset();
        PA.moveTo(-220, 120);
        PA.cubicTo(-260, 0, -200, -120, -130, -170);
        PA.cubicTo(-120, -260, -40, -300, 0, -240);
        PA.cubicTo(60, -300, 130, -250, 130, -170);
        PA.cubicTo(210, -110, 260, 0, 220, 120);
        PA.cubicTo(120, 80, -120, 80, -220, 120);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, -300, 0, 120,
                new int[]{rgb(255, 8, 6, 12), rgb(255, 36, 22, 40), rgb(255, 6, 4, 8)}, null, Shader.TileMode.CLAMP));
        c.drawPath(PA, P);
        P.setShader(null);
        // dumanlı kollar
        for (int i = 0; i < 6; i++) {
            float a = i * 1.05f + t * 0.4f;
            PA.reset();
            PA.moveTo((float) Math.cos(a) * 160, -60 + (float) Math.sin(a) * 90);
            PA.quadTo((float) Math.cos(a + 0.5f) * 280, -90 + (float) Math.sin(a) * 120,
                    (float) Math.cos(a + 0.9f) * 320, -140 + (float) Math.sin(a + 1f) * 140);
            stroke(c, PA, alpha(rgb(255, 20, 12, 28), 0.6f), 26f);
        }
        // kırmızı çatlaklar
        PA.reset();
        PA.moveTo(-60, -200); PA.lineTo(-30, -130); PA.lineTo(-70, -60); PA.lineTo(-20, 10);
        PA.moveTo(70, -210); PA.lineTo(40, -140); PA.lineTo(90, -70);
        stroke(c, PA, alpha(rgb(255, 255, 40, 70), 0.7f + 0.3f * rage), 5f);
        // gözler
        float e = 0.8f + 0.2f * (float) Math.sin(t * 5f);
        glow(c, -44, -172, 44, rgb(255, 255, 30, 50), e);
        glow(c, 44, -172, 44, rgb(255, 255, 30, 50), e);
        glow(c, -44, -172, 12, rgb(255, 255, 230, 200), 1f);
        glow(c, 44, -172, 12, rgb(255, 255, 230, 200), 1f);
        c.restore();
    }

    /** Beş kahramanın silueti. */
    public static void heroSilhouette(Canvas c, float x, float y, float s, int accent, int kind, float t) {
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        glow(c, 0, -60, 80, accent, 0.35f);
        PA.reset();
        PA.moveTo(-18, 0);
        PA.cubicTo(-26, -40, -20, -62, -10, -70);
        PA.cubicTo(-14, -86, 14, -86, 10, -70);
        PA.cubicTo(22, -60, 26, -40, 18, 0);
        PA.quadTo(0, 8, -18, 0);
        fill(c, PA, rgb(255, 6, 6, 10));
        // sınıf göstergesi
        PA.reset();
        switch (kind) {
            case 0: PA.moveTo(22, -30); PA.lineTo(40, -90); PA.lineTo(46, -86); PA.lineTo(26, -26); break;           // savaşçı
            case 1: PA.moveTo(20, -90); PA.cubicTo(50, -60, 50, -20, 20, 6); break;                                    // yay
            case 2: PA.moveTo(24, 4); PA.lineTo(24, -100); break;                                                      // asa
            case 3: PA.moveTo(24, 4); PA.lineTo(24, -96); PA.moveTo(16, -84); PA.lineTo(32, -84); break;               // rün asası
            default: PA.moveTo(24, 4); PA.lineTo(24, -92); break;
        }
        stroke(c, PA, rgb(255, 6, 6, 10), 5f);
        glow(c, 0, -52, 12, accent, 0.9f);
        c.restore();
    }

    /** Dünya Ağacı'nın sönük kutsal ışığı (sinematik sonu). */
    public static void treeGlimmer(Canvas c, float cx, float cy, float s, float t) {
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        greatTree(c, t, true);
        c.restore();
    }
}
