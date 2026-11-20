package com.mmorpgik;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.mmorpgik.game.HeroClass;
import com.mmorpgik.game.Skill;
import com.mmorpgik.game.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private enum State { INTRO, CREATE, PLAY }

    private static class Npc {
        String name; float x, y; int hair, skin, cloak, style; String[] lines; boolean wander;
        int talkIdx = 0;
    }

    private static class Item {
        float y; int type; Object ref; int idx;
    }

    // --- sistem ---
    private final Object lock = new Object();
    private Thread thread;
    private volatile boolean running = false;
    private boolean surfaceReady = false;
    private float sc = 1f, lw = 1280f, lh = 720f;
    private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private float uiTime = 0f;

    // --- oyun ---
    private State state = State.INTRO;
    private World world = new World(20261120L);
    private final List<Npc> npcs = new ArrayList<>();
    private float[] treeX, treeY, treeS, treeSeed;
    private final List<Item> items = new ArrayList<>();
    private float camX = 0, camY = 330;
    private float introT = 0, flash = 0;
    private boolean classMenu = false;
    private int classSel = 0;
    private String speech = null;
    private float speechT = 0, speechX, speechY;

    // --- girdi ---
    private int joyPid = -1, attackPid = -1;
    private float joyCx, joyCy, joyKx, joyKy;
    private static final float JOY_R = 70f;

    // karakter oluşturma
    private int cHair = 0, cSkin = 1, cCloak = 1, cStyle = 0;

    public GameView(Context ctx) {
        super(ctx);
        getHolder().addCallback(this);
        setFocusable(true);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        initWorldArt();
        initNpcs();
    }

    private void initWorldArt() {
        int n = 240;
        treeX = new float[n]; treeY = new float[n]; treeS = new float[n]; treeSeed = new float[n];
        int k = 0;
        for (int i = 0; i < 4000 && k < n; i++) {
            float a = Art.hash(i, 11) * 6.2832f;
            float d = 470f + (float) Math.sqrt(Art.hash(i, 12)) * (World.RADIUS + 120f - 470f);
            float x = (float) Math.cos(a) * d, y = (float) Math.sin(a) * d;
            if (World.dist(x, y, 0, 0) < 470f) continue;
            boolean ok = true;
            for (int j = 0; j < k; j++) {
                if (World.dist(x, y, treeX[j], treeY[j]) < 110f) { ok = false; break; }
            }
            if (!ok) continue;
            treeX[k] = x; treeY[k] = y;
            treeS[k] = 0.8f + Art.hash(i, 13) * 0.7f;
            treeSeed[k] = Art.hash(i, 14);
            k++;
        }
        if (k < n) {
            float[] a = new float[k], b = new float[k], c = new float[k], d = new float[k];
            System.arraycopy(treeX, 0, a, 0, k); System.arraycopy(treeY, 0, b, 0, k);
            System.arraycopy(treeS, 0, c, 0, k); System.arraycopy(treeSeed, 0, d, 0, k);
            treeX = a; treeY = b; treeS = c; treeSeed = d;
        }
    }

    private Npc npc(String name, float x, float y, int hair, int skin, int cloak, int style, boolean wander, String... lines) {
        Npc n = new Npc();
        n.name = name; n.x = x; n.y = y; n.hair = hair; n.skin = skin; n.cloak = cloak; n.style = style;
        n.wander = wander; n.lines = lines;
        npcs.add(n);
        return n;
    }

    private void initNpcs() {
        npc("Demirci Tarkan", -300, 140, 6, 3, 5, 3, false,
                "Silahını bu köyde bile en iyi demirci yapar.", "Ormanın derinlerine gidersen canavarlar güçlenir. Dikkat et.");
        npc("Hancı Melek", -230, -90, 1, 1, 6, 1, false,
                "Hoş geldin yolcu! Han her zaman açık.", "Ulu Ağaç'a kim gelse bir şeyler değişir burada.");
        npc("Tüccar Barış", 270, -100, 2, 2, 4, 0, false,
                "Pazar yakında genişleyecek. Biraz sabret.", "Her şeyin bir bedeli vardır. Güç de öyle.");
        npc("Yaşlı Bekçi", 120, 260, 3, 0, 3, 2, false,
                "Dünya Ağacı beş kahramanın gücünü saklar.", "Beş kahraman bir zamanlar bu dünyayı kurtardı. Şimdi sıra sende.");
        npc("Kıyameti Sayan Adam", 160, -120, 3, 2, 0, 0, true,
                "Mühür çatlıyor... Ama kimse duymuyor!", "Otuz gün... Otuz gün kaldı...");
    }

    // ---------- yaşam döngüsü ----------
    public void resumeGame() {
        running = true;
        if (surfaceReady && thread == null) startThread();
    }

    public void pauseGame() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    private void startThread() {
        thread = new Thread(this, "game");
        thread.start();
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        surfaceReady = true;
        if (running && thread == null) startThread();
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hgt) {
        sc = hgt / 720f;
        lw = w / sc;
        lh = 720f;
    }
    @Override public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        pauseGame();
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1e9f;
            last = now;
            synchronized (lock) { update(dt); }
            SurfaceHolder h = getHolder();
            Canvas c = null;
            try {
                c = h.lockCanvas();
                if (c != null) {
                    synchronized (lock) {
                        c.save();
                        c.scale(sc, sc);
                        draw2(c);
                        c.restore();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try { h.unlockCanvasAndPost(c); } catch (Exception ignored) { }
                }
            }
            long spent = (System.nanoTime() - now) / 1000000L;
            if (spent < 14) {
                try { Thread.sleep(14 - spent); } catch (InterruptedException ignored) { }
            }
        }
    }

    // ---------- güncelleme ----------
    private void update(float dt) {
        if (dt > 0.05f) dt = 0.05f;
        uiTime += dt;
        if (state == State.INTRO) {
            introT += dt;
            if (introT > 38f) startCreate();
            return;
        }
        if (state == State.CREATE) return;
        World w = world;
        if (classMenu) { w.setMove(0, 0); }
        w.update(dt);
        // sınıf seçmeden köyden çıkılamaz
        if (w.player.hero == null) {
            float d = World.dist(w.player.x, w.player.y, 0, 0);
            float lim = World.SAFE_RADIUS - 40f;
            if (d > lim) { w.player.x *= lim / d; w.player.y *= lim / d; }
        }
        // basılı tutulan saldırı
        if (attackPid != -1 && !classMenu) w.basicAttack();
        // NPC dolaşması
        for (Npc n : npcs) {
            if (n.wander) {
                n.x = (float) Math.cos(uiTime * 0.18f) * 235f;
                n.y = 30f + (float) Math.sin(uiTime * 0.18f) * 120f;
            }
        }
        if (speechT > 0) speechT -= dt;
        if (flash > 0) flash -= dt;
        // kamera
        camX += (w.player.x - camX) * Math.min(1f, dt * 6f);
        camY += (w.player.y - 30 - camY) * Math.min(1f, dt * 6f);
    }

    private void startCreate() {
        state = State.CREATE;
        classMenu = false;
    }

    private void startPlay() {
        state = State.PLAY;
        world.player.hairColor = Art.HAIR[cHair];
        world.player.skinColor = Art.SKIN[cSkin];
        world.player.cloakColor = Art.CLOAK[cCloak];
        world.player.hairStyle = cStyle;
        camX = world.player.x; camY = world.player.y - 30;
        world.say("Dünya Ağacı'na yaklaş ve ona dokun");
    }

    // ---------- çizim ----------
    private void draw2(Canvas c) {
        switch (state) {
            case INTRO: drawIntro(c); break;
            case CREATE: drawCreate(c); break;
            default: drawPlay(c); break;
        }
    }

    private void caption(Canvas c, String s, float y, float alpha, float size) {
        text.setTextSize(size);
        text.setStyle(Paint.Style.FILL);
        String[] lines = s.split("\n");
        for (int i = 0; i < lines.length; i++) {
            text.setColor(Art.alpha(0xFF000000, alpha * 0.8f));
            c.drawText(lines[i], lw / 2 + 2, y + i * (size + 8) + 2, text);
            text.setColor(Art.alpha(0xFFF4E8D0, alpha));
            c.drawText(lines[i], lw / 2, y + i * (size + 8), text);
        }
    }

    private float fade(float t, float a, float b) {
        // a..b arası görünür, kenarlarda yumuşak giriş/çıkış
        float in = Math.min(1f, Math.max(0f, (t - a) / 0.8f));
        float out = Math.min(1f, Math.max(0f, (b - t) / 0.8f));
        return Math.min(in, out);
    }

    private void drawIntro(Canvas c) {
        float t = introT;
        float fl = 0;
        if ((t > 8 && t < 8.6f) || (t > 13.3f && t < 13.7f) || (t > 14.6f && t < 14.9f)) fl = 1f;
        Art.cinematicBackdrop(c, lw, lh, t, fl);
        // yıldırım
        if (t > 12 && t < 20 && ((int) (t * 3.3f)) % 5 == 0) {
            path.reset();
            float bx = lw * (0.25f + 0.5f * Art.hash((int) (t * 3.3f), 3));
            path.moveTo(bx, 0); path.lineTo(bx - 20, lh * 0.25f); path.lineTo(bx + 14, lh * 0.45f); path.lineTo(bx - 10, lh * 0.7f);
            pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(4); pt.setColor(Art.alpha(0xFFDDD0FF, 0.8f));
            c.drawPath(path, pt);
            pt.setStyle(Paint.Style.FILL);
        }
        // 0-4: Dünya Ağacı'nın huzuru
        if (t < 8f) {
            float a = fade(t, 0, 8);
            c.save();
            c.translate(lw / 2, lh * 0.88f);
            c.scale(0.9f, 0.9f);
            pt.setAlpha(255);
            Art.greatTree(c, t, false);
            c.restore();
            c.drawColor(Art.alpha(0xFF000000, 1f - a));
        }
        // 4-8: kötülük birikir
        if (t > 4f && t < 9f) {
            float a = Math.min(1f, (t - 4f) / 4f);
            for (int i = 0; i < 16; i++) {
                float x = lw * Art.hash(i, 1);
                float y = lh * (0.2f + 0.6f * Art.hash(i, 2)) - (t - 4f) * 20f;
                Art.glow(c, x + (float) Math.sin(t + i) * 30, y, 60 + 40 * Art.hash(i, 3), 0xFF2A1030, 0.5f * a);
            }
        }
        // 8-22: Kundun
        if (t > 8f && t < 26f) {
            float s = t < 14f ? 0.5f + (t - 8f) * 0.1f : 1.1f;
            if (t > 21f) s = 1.1f - (t - 21f) * 0.12f;
            float rage = t < 20f ? 1f : Math.max(0f, 1f - (t - 20f) / 6f);
            Art.kundun(c, lw / 2, lh * 0.55f, s * (lh / 720f), t, rage);
        }
        // 16-24: beş kahraman
        if (t > 16f && t < 26f) {
            float[] xs = {0.14f, 0.3f, 0.5f, 0.7f, 0.86f};
            int[] cols = {HeroClass.WARRIOR.color, HeroClass.ROGUE.color, HeroClass.MAGE.color, HeroClass.PRIEST.color, HeroClass.SUMMONER.color};
            for (int i = 0; i < 5; i++) {
                float ti = 16.5f + i * 0.9f;
                if (t < ti) continue;
                float rise = Math.min(1f, (t - ti) / 0.7f);
                Art.heroSilhouette(c, lw * xs[i], lh * 0.9f - 20 * rise + (1 - rise) * 40, 1.7f, cols[i], i, t);
            }
        }
        // 26-34: mühür ve Dünya Ağacı
        if (t > 26f && t < 34f) {
            float a = fade(t, 26f, 34f);
            c.drawColor(Art.alpha(0xFF060410, a * 0.85f));
            c.save();
            c.translate(lw / 2, lh * 0.9f);
            c.scale(0.95f, 0.95f);
            Art.greatTree(c, t, true);
            c.restore();
        }
        // başlık
        if (t > 34f) {
            float a = Math.min(1f, (t - 34f) / 1.5f);
            c.drawColor(Art.alpha(0xFF060410, 0.6f));
            caption(c, "MÜHRÜN ARDINDAKİ KARANLIK", lh * 0.45f, a, 54f);
            caption(c, "Devam etmek için dokun", lh * 0.62f, a * (0.6f + 0.4f * (float) Math.sin(uiTime * 3)), 24f);
        }
        // altyazılar
        String cap = null; float a = 0;
        if (t < 4) { cap = "Dünyada iyilik ve kötülük\nhep birlikte var oldu."; a = fade(t, 0.3f, 4f); }
        else if (t < 8) { cap = "Kötülük biriktikçe\nbir varlığa dönüştü."; a = fade(t, 4f, 8f); }
        else if (t < 12) { cap = "KUNDUN"; a = fade(t, 8.5f, 12f); }
        else if (t < 16) { cap = "Dünya yıkıma sürüklendi."; a = fade(t, 12f, 16f); }
        else if (t < 21) { cap = "Beş kahraman ortaya çıktı."; a = fade(t, 16f, 21f); }
        else if (t < 26) { cap = "Kundun yenildi...\nama kötülük hiç yok olmaz."; a = fade(t, 21f, 26f); }
        else if (t < 30) { cap = "Kahramanlar güçlerini ikiye böldü:\nyarısı mühre, yarısı Dünya Ağacı'na."; a = fade(t, 26f, 30f); }
        else if (t < 34) { cap = "Yıllar geçti. Mühür zayıfladı.\nDünya Ağacı yeni kahramanları çağırıyor..."; a = fade(t, 30f, 34f); }
        if (cap != null) caption(c, cap, lh * 0.16f, a, 36f);
        // geç
        text.setTextSize(20);
        text.setColor(Art.alpha(0xFFFFFFFF, 0.55f));
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Geç >>", lw - 24, 36, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    // karakter oluşturma ekranı
    private final RectF[] createRects = new RectF[10];

    private void drawCreate(Canvas c) {
        // arka plan
        pt.setStyle(Paint.Style.FILL);
        pt.setShader(new LinearGradient(0, 0, 0, lh,
                new int[]{0xFF14102A, 0xFF2B2A52, 0xFF4A6A5A}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, lw, lh, pt);
        pt.setShader(null);
        c.save();
        c.translate(lw * 0.3f, lh * 0.95f);
        c.scale(0.65f, 0.65f);
        Art.greatTree(c, uiTime, false);
        c.restore();
        // önizleme
        Art.glow(c, lw * 0.3f, lh * 0.62f, 190, 0xFFFFE8A0, 0.25f);
        Art.person(c, lw * 0.3f, lh * 0.8f, true, uiTime * 2f, false, Art.HAIR[cHair], Art.SKIN[cSkin], Art.CLOAK[cCloak],
                cStyle, null, 0, 3.4f, false, uiTime);

        // panel
        float px = lw * 0.58f, py = 70f, pw = lw * 0.38f, ph = lh - 140f;
        panel(c, px, py, pw, ph);
        text.setTextSize(34);
        text.setColor(0xFFF4E8D0);
        c.drawText("Karakterini Oluştur", px + pw / 2, py + 52, text);
        String[] labels = {"Saç Stili", "Saç Rengi", "Ten", "Pelerin"};
        int[] vals = {cStyle + 1, cHair + 1, cSkin + 1, cCloak + 1};
        for (int i = 0; i < 4; i++) {
            float ry = py + 100 + i * 70;
            text.setTextSize(24);
            text.setColor(0xFFE0D2B0);
            c.drawText(labels[i], px + pw / 2, ry - 8, text);
            arrow(c, px + 40, ry + 22, true, i * 2);
            arrow(c, px + pw - 40, ry + 22, false, i * 2 + 1);
            // renk örneği
            int col = i == 1 ? Art.HAIR[cHair] : (i == 2 ? Art.SKIN[cSkin] : (i == 3 ? Art.CLOAK[cCloak] : 0xFFBBA880));
            rect.set(px + pw / 2 - 52, ry + 4, px + pw / 2 + 52, ry + 40);
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(col);
            c.drawRoundRect(rect, 18, 18, pt);
            text.setTextSize(20);
            text.setColor(0xFFFFFFFF);
            if (i == 0) c.drawText("Stil " + vals[i], px + pw / 2, ry + 29, text);
        }
        // isim
        float ny = py + 100 + 4 * 70 + 6;
        rect.set(px + 30, ny, px + pw - 30, ny + 46);
        createRects[8] = new RectF(rect);
        pt.setColor(0x66000000);
        c.drawRoundRect(rect, 22, 22, pt);
        text.setTextSize(26);
        text.setColor(0xFFFFE9B0);
        c.drawText(world.player.name + "  (değiştir)", px + pw / 2, ny + 32, text);
        // başla
        float by = py + ph - 70;
        rect.set(px + 30, by, px + pw - 30, by + 56);
        createRects[9] = new RectF(rect);
        pt.setShader(new LinearGradient(0, by, 0, by + 56, new int[]{0xFF6AC070, 0xFF2E8048}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, 28, 28, pt);
        pt.setShader(null);
        text.setTextSize(30);
        text.setColor(0xFFFFFFFF);
        c.drawText("Dünyaya Adım At", px + pw / 2, by + 38, text);
    }

    private void arrow(Canvas c, float x, float y, boolean left, int idx) {
        rect.set(x - 28, y - 28, x + 28, y + 28);
        createRects[idx] = new RectF(rect);
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0x55000000);
        c.drawRoundRect(rect, 20, 20, pt);
        path.reset();
        float d = left ? -1 : 1;
        path.moveTo(x + d * 8, y - 12); path.lineTo(x - d * 8, y); path.lineTo(x + d * 8, y + 12);
        pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(5); pt.setStrokeCap(Paint.Cap.ROUND); pt.setStrokeJoin(Paint.Join.ROUND);
        pt.setColor(0xFFF4E8D0);
        c.drawPath(path, pt);
        pt.setStyle(Paint.Style.FILL);
    }

    private void panel(Canvas c, float x, float y, float w, float h) {
        rect.set(x, y, x + w, y + h);
        pt.setStyle(Paint.Style.FILL);
        pt.setShader(new LinearGradient(0, y, 0, y + h, new int[]{0xCC2A2418, 0xDD1A160E}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, 26, 26, pt);
        pt.setShader(null);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(3);
        pt.setColor(0xFFC8A860);
        c.drawRoundRect(rect, 26, 26, pt);
        pt.setStyle(Paint.Style.FILL);
    }

    // oyun ekranı
    private void drawPlay(Canvas c) {
        World w = world;
        World.Player p = w.player;
        c.save();
        c.translate(lw / 2 - camX, lh / 2 - camY);
        Art.ground(c, camX, camY, lw, lh, uiTime);
        Art.plaza(c, uiTime);
        // dünya sınırı
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(60);
        pt.setColor(Art.alpha(0xFF30184A, 0.55f));
        c.drawCircle(0, 0, World.RADIUS + 120, pt);
        pt.setStyle(Paint.Style.FILL);

        // derinlik sıralaması
        items.clear();
        float vx0 = camX - lw / 2 - 250, vx1 = camX + lw / 2 + 250, vy0 = camY - lh / 2 - 450, vy1 = camY + lh / 2 + 250;
        for (int i = 0; i < treeX.length; i++) {
            if (treeX[i] > vx0 && treeX[i] < vx1 && treeY[i] > vy0 && treeY[i] < vy1) add(treeY[i], 0, null, i);
        }
        add(18f, 1, null, 0); // büyük ağaç
        float[][] hs = {{-360, -170, 150}, {-420, 60, 130}, {350, -230, 140}, {110, -400, 150}, {-190, 330, 130}};
        int[] roofs = {0xFFB4503C, 0xFF4C78A8, 0xFF8A5C3A, 0xFF5E8A52, 0xFFA8783C};
        for (int i = 0; i < hs.length; i++) add(hs[i][1], 2, null, i);
        for (int i = 0; i < npcs.size(); i++) add(npcs.get(i).y, 3, npcs.get(i), i);
        for (World.Monster m : w.monsters) {
            if (m.alive && m.x > vx0 && m.x < vx1 && m.y > vy0 && m.y < vy1) add(m.y, 4, m, 0);
        }
        for (World.Summon s : w.summons) add(s.y + 30, 6, s, 0);
        if (!p.dead) add(p.y, 5, null, 0);
        Collections.sort(items, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Float.compare(a.y, b.y); }
        });
        boolean awaken = p.hero == null;
        for (Item it : items) {
            switch (it.type) {
                case 0: Art.tree(c, treeX[it.idx], treeY[it.idx], treeS[it.idx], treeSeed[it.idx], uiTime); break;
                case 1: Art.greatTree(c, uiTime, awaken); break;
                case 2: Art.house(c, hs[it.idx][0], hs[it.idx][1], hs[it.idx][2], roofs[it.idx], 0.2f * it.idx); break;
                case 3: {
                    Npc n = (Npc) it.ref;
                    boolean doom = n.wander;
                    Art.person(c, n.x, n.y, doom ? Math.sin(uiTime * 0.18f) > 0 : (n.x < 0), uiTime * (doom ? 4f : 1f), doom,
                            Art.HAIR[n.hair], Art.SKIN[n.skin], Art.CLOAK[n.cloak], n.style, null, 0, 1f, false, uiTime);
                    text.setTextSize(15);
                    text.setColor(0xFFF8E8B8);
                    c.drawText(n.name, n.x, n.y - 100, text);
                    break;
                }
                case 4: Art.monster(c, (World.Monster) it.ref, uiTime); break;
                case 5:
                    Art.person(c, p.x, p.y, p.faceX >= 0, p.walkPhase, p.moving,
                            p.hairColor, p.skinColor, p.cloakColor, p.hairStyle, p.hero,
                            Math.max(0f, p.attackAnim), 1.05f, p.hurt > 0, uiTime);
                    if (p.shield > 0) {
                        Art.glow(c, p.x, p.y - 40, 60, p.hero != null ? p.hero.color : 0xFFFFFFFF, 0.5f);
                    }
                    text.setTextSize(16);
                    text.setColor(0xFFFFFFFF);
                    c.drawText(p.name, p.x, p.y - 108, text);
                    break;
                case 6: Art.phoenix(c, (World.Summon) it.ref, uiTime); break;
            }
        }
        for (World.Projectile pr : w.projectiles) Art.projectile(c, pr, uiTime);
        text.setTextSize(26);
        for (World.Fx f : w.fx) Art.fx(c, f, text);
        if (p.dead) Art.greenSpirit(c, p.spiritX, p.spiritY, uiTime);
        // konuşma balonu
        if (speech != null && speechT > 0) {
            text.setTextSize(18);
            float tw = text.measureText(speech) + 28;
            rect.set(speechX - tw / 2, speechY - 150, speechX + tw / 2, speechY - 112);
            pt.setColor(0xEEFFF6DC);
            c.drawRoundRect(rect, 16, 16, pt);
            text.setColor(0xFF3A2A18);
            c.drawText(speech, speechX, speechY - 125, text);
        }
        c.restore();

        drawVignette(c);
        drawHud(c);
        if (classMenu) drawClassMenu(c);
    }

    private void add(float y, int type, Object ref, int idx) {
        Item it = new Item();
        it.y = y; it.type = type; it.ref = ref; it.idx = idx;
        items.add(it);
    }

    private void drawVignette(Canvas c) {
        pt.setStyle(Paint.Style.FILL);
        pt.setShader(new RadialGradient(lw / 2, lh / 2, lw * 0.62f,
                new int[]{0x00000000, 0x00000000, 0x55000510}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, lw, lh, pt);
        pt.setShader(null);
    }

    // ---------- HUD ----------
    private float[] skillX = new float[5], skillY = new float[5];
    private float atkX, atkY;
    private static final float SKILL_R = 29f, ATK_R = 50f;

    private void layoutButtons() {
        atkX = lw - 100; atkY = lh - 100;
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(180 + i * 22.5);
            skillX[i] = atkX + (float) Math.cos(a) * 142f;
            skillY[i] = atkY + (float) Math.sin(a) * 142f;
        }
    }

    private void bar(Canvas c, float x, float y, float w, float h, float frac, int c1, int c2) {
        rect.set(x, y, x + w, y + h);
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(0x99000000);
        c.drawRoundRect(rect, h / 2, h / 2, pt);
        frac = Math.max(0f, Math.min(1f, frac));
        if (frac > 0.01f) {
            rect.set(x + 1, y + 1, x + 1 + (w - 2) * frac, y + h - 1);
            pt.setShader(new LinearGradient(0, y, 0, y + h, new int[]{c1, c2}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, h / 2, h / 2, pt);
            pt.setShader(null);
        }
    }

    private void drawHud(Canvas c) {
        World w = world;
        World.Player p = w.player;
        layoutButtons();
        int hc = p.hero != null ? p.hero.color : 0xFFC8A860;
        // sol üst: kimlik
        panel(c, 14, 14, 330, 104);
        Art.glow(c, 54, 66, 40, hc, 0.9f);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(22);
        text.setColor(0xFFFFF0C8);
        String title = p.hero != null ? p.hero.title : "Yolcu";
        c.drawText(p.name + " · " + title, 100, 42, text);
        text.setTextSize(17);
        text.setColor(0xFFE0D2B0);
        c.drawText("Seviye " + p.level, 100, 64, text);
        bar(c, 100, 72, 230, 14, p.hp / p.maxHp, 0xFFE05050, 0xFF8A2020);
        bar(c, 100, 90, 230, 11, p.mp / p.maxMp, 0xFF4A90E0, 0xFF203A8A);
        bar(c, 20, 108, 318, 6, p.level >= World.MAX_LEVEL ? 1f : (float) p.xp / World.xpFor(p.level), 0xFFF0D060, 0xFFB08820);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(24);
        text.setColor(0xFFFFFFFF);
        c.drawText(String.valueOf(p.level), 54, 74, text);

        // sağ üst: bölge
        float d = World.dist(p.x, p.y, 0, 0);
        String zone = d < World.SAFE_RADIUS ? "Köy · Dünya Ağacı" : "Huzur Ormanı · Canavar sv. ~" + World.levelAt(d);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(20);
        text.setColor(0xFFF4E8D0);
        c.drawText(zone, lw - 20, 36, text);
        text.setTextAlign(Paint.Align.CENTER);

        // toast
        if (w.toast != null && w.toastT > 0) {
            caption(c, w.toast, 150, Math.min(1f, w.toastT), 26f);
        }
        if (p.hero == null && !classMenu) {
            caption(c, "Dünya Ağacı'na dokun: gücünü seç", lh - 40, 0.75f + 0.25f * (float) Math.sin(uiTime * 3f), 24f);
        }

        // joystick
        if (joyPid != -1) {
            Art.glow(c, joyCx, joyCy, JOY_R + 20, 0xFFFFFFFF, 0.18f);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(4);
            pt.setColor(0x88FFFFFF);
            c.drawCircle(joyCx, joyCy, JOY_R, pt);
            pt.setStyle(Paint.Style.FILL);
            Art.glow(c, joyKx, joyKy, 38, 0xFFFFE8A0, 0.9f);
        } else {
            Art.glow(c, 150, lh - 130, 70, 0xFFFFFFFF, 0.10f);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(3);
            pt.setColor(0x44FFFFFF);
            c.drawCircle(150, lh - 130, JOY_R, pt);
            pt.setStyle(Paint.Style.FILL);
        }

        // temel saldırı
        drawRoundButton(c, atkX, atkY, ATK_R, hc, attackPid != -1);
        iconSword(c, atkX, atkY);

        // yetenekler
        for (int i = 0; i < 5; i++) {
            if (p.hero == null) {
                drawRoundButton(c, skillX[i], skillY[i], SKILL_R, 0xFF555555, false);
                continue;
            }
            Skill s = p.hero.skills[i];
            boolean ready = p.cd[i] <= 0 && p.mp >= s.mana;
            drawRoundButton(c, skillX[i], skillY[i], SKILL_R, ready ? hc : 0xFF666666, false);
            icon(c, s.type, skillX[i], skillY[i]);
            if (p.cd[i] > 0) {
                rect.set(skillX[i] - SKILL_R, skillY[i] - SKILL_R, skillX[i] + SKILL_R, skillY[i] + SKILL_R);
                pt.setColor(0xAA000000);
                float sweep = 360f * (p.cd[i] / s.cooldown);
                c.drawArc(rect, -90, sweep, true, pt);
                text.setTextSize(20);
                text.setColor(0xFFFFFFFF);
                c.drawText(String.valueOf((int) Math.ceil(p.cd[i])), skillX[i], skillY[i] + 7, text);
            }
        }
    }

    private void drawRoundButton(Canvas c, float x, float y, float r, int col, boolean pressed) {
        Art.glow(c, x, y, r + 14, col, pressed ? 0.7f : 0.3f);
        pt.setStyle(Paint.Style.FILL);
        pt.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.4f, r * 1.6f,
                new int[]{Art.shade(col, 1.35f), Art.shade(col, 0.55f)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, pressed ? r * 0.93f : r, pt);
        pt.setShader(null);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(3);
        pt.setColor(0xCCF4E8D0);
        c.drawCircle(x, y, pressed ? r * 0.93f : r, pt);
        pt.setStyle(Paint.Style.FILL);
    }

    private void strokeP(Canvas c, int col, float w) {
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(w);
        pt.setStrokeCap(Paint.Cap.ROUND);
        pt.setStrokeJoin(Paint.Join.ROUND);
        pt.setColor(col);
        c.drawPath(path, pt);
        pt.setStyle(Paint.Style.FILL);
    }

    private void iconSword(Canvas c, float x, float y) {
        path.reset();
        path.moveTo(x - 18, y + 18); path.lineTo(x + 16, y - 16);
        path.moveTo(x - 6, y + 10); path.lineTo(x + 2, y + 18);
        path.moveTo(x + 10, y - 22); path.quadTo(x + 24, y - 20, x + 22, y - 8);
        strokeP(c, 0xFFFFFFFF, 5f);
    }

    private void icon(Canvas c, Skill.Type t, float x, float y) {
        path.reset();
        switch (t) {
            case MELEE:
                path.moveTo(x - 14, y - 14); path.quadTo(x + 4, y - 4, x + 14, y + 14);
                path.moveTo(x - 4, y - 16); path.quadTo(x + 12, y - 6, x + 16, y + 6);
                break;
            case BOLT:
                path.moveTo(x - 16, y + 12); path.lineTo(x + 12, y - 8);
                path.moveTo(x + 2, y - 16); path.lineTo(x + 14, y - 8); path.lineTo(x + 6, y + 2);
                break;
            case AOE_SELF:
                path.addOval(x - 16, y - 9, x + 16, y + 9, Path.Direction.CW);
                path.addOval(x - 7, y - 4, x + 7, y + 4, Path.Direction.CW);
                break;
            case AOE_TARGET:
                path.moveTo(x + 4, y - 18); path.lineTo(x - 8, y + 2); path.lineTo(x + 6, y + 2); path.lineTo(x - 4, y + 18);
                break;
            case HEAL:
                path.moveTo(x, y - 14); path.lineTo(x, y + 14);
                path.moveTo(x - 14, y); path.lineTo(x + 14, y);
                break;
            case SHIELD:
                path.moveTo(x - 13, y - 14); path.quadTo(x, y - 10, x + 13, y - 14);
                path.quadTo(x + 15, y + 6, x, y + 18); path.quadTo(x - 15, y + 6, x - 13, y - 14);
                break;
            case HASTE:
                path.moveTo(x - 14, y - 12); path.lineTo(x - 2, y); path.lineTo(x - 14, y + 12);
                path.moveTo(x, y - 12); path.lineTo(x + 12, y); path.lineTo(x, y + 12);
                break;
            case POWER:
                path.moveTo(x - 12, y + 6); path.lineTo(x, y - 12); path.lineTo(x + 12, y + 6);
                path.moveTo(x - 12, y + 16); path.lineTo(x, y + 0); path.lineTo(x + 12, y + 16);
                break;
            case DASH:
                path.moveTo(x - 16, y); path.lineTo(x + 14, y);
                path.moveTo(x + 4, y - 10); path.lineTo(x + 15, y); path.lineTo(x + 4, y + 10);
                break;
            default: // SUMMON
                path.moveTo(x, y + 16); path.quadTo(x - 18, y, x - 6, y - 16);
                path.quadTo(x, y - 6, x + 8, y - 12); path.quadTo(x + 18, y, x, y + 16);
                break;
        }
        strokeP(c, 0xFFFFFFFF, 3.6f);
    }

    // ---------- sınıf seçimi ----------
    private final RectF[] cardRects = new RectF[5];
    private RectF acceptRect = new RectF(), cancelRect = new RectF();

    private void drawClassMenu(Canvas c) {
        c.drawColor(0xCC050310);
        text.setTextSize(34);
        text.setColor(0xFFF4E8D0);
        c.drawText("Dünya Ağacı sana beş güç sunuyor", lw / 2, 56, text);
        HeroClass[] hs = HeroClass.values();
        float cw = 176, ch = 130, gap = 14;
        float x0 = (lw - (5 * cw + 4 * gap)) / 2;
        for (int i = 0; i < 5; i++) {
            float x = x0 + i * (cw + gap), y = 84;
            RectF r = new RectF(x, y, x + cw, y + ch);
            cardRects[i] = r;
            boolean sel = i == classSel;
            pt.setStyle(Paint.Style.FILL);
            pt.setShader(new LinearGradient(0, y, 0, y + ch,
                    new int[]{Art.shade(hs[i].color, sel ? 0.8f : 0.45f), 0xFF14101C}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 22, 22, pt);
            pt.setShader(null);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(sel ? 5 : 2);
            pt.setColor(sel ? 0xFFFFF0B0 : 0x88C8A860);
            c.drawRoundRect(r, 22, 22, pt);
            pt.setStyle(Paint.Style.FILL);
            Art.glow(c, x + cw / 2, y + 48, 40, hs[i].color, sel ? 1f : 0.6f);
            Art.person(c, x + cw / 2, y + 90, true, 0, false, Art.HAIR[cHair], Art.SKIN[cSkin], hs[i].color, cStyle, hs[i],
                    sel ? (float) Math.max(0, Math.sin(uiTime * 3) * 0.2) : 0, 0.82f, false, uiTime);
            text.setTextSize(20);
            text.setColor(0xFFFFFFFF);
            c.drawText(hs[i].title, x + cw / 2, y + ch - 10, text);
        }
        HeroClass h = hs[classSel];
        panel(c, lw * 0.08f, 232, lw * 0.84f, 380);
        float lx = lw * 0.08f + 36;
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(34);
        text.setColor(h.color);
        c.drawText(h.title, lx, 282, text);
        text.setTextSize(22);
        text.setColor(0xFFF4E8D0);
        c.drawText("Kahraman: " + h.hero, lx, 322, text);
        c.drawText("Yozlaşmış ruhu: " + h.corrupted + " (bir gün karşına çıkacak)", lx, 352, text);
        c.drawText("Ruh hayvanı: " + h.spirit, lx, 382, text);
        text.setTextSize(20);
        text.setColor(0xFFD8C8A0);
        drawWrapped(c, h.blurb, lx, 422, lw * 0.34f);
        // yetenekler
        float sx = lw * 0.5f;
        for (int i = 0; i < 5; i++) {
            float yy = 276 + i * 62;
            drawRoundButton(c, sx + 28, yy + 6, 24, h.color, false);
            icon(c, h.skills[i].type, sx + 28, yy + 6);
            text.setTextSize(22);
            text.setColor(0xFFFFF0C8);
            c.drawText(h.skills[i].name, sx + 70, yy, text);
            text.setTextSize(16);
            text.setColor(0xFFC8B890);
            c.drawText(h.skills[i].info, sx + 70, yy + 22, text);
        }
        text.setTextAlign(Paint.Align.CENTER);
        // düğmeler
        acceptRect.set(lw / 2 - 40, 636, lw / 2 + 250, 696);
        pt.setShader(new LinearGradient(0, 636, 0, 696, new int[]{0xFF6AC070, 0xFF2E8048}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(acceptRect, 30, 30, pt);
        pt.setShader(null);
        text.setTextSize(28);
        text.setColor(0xFFFFFFFF);
        c.drawText("Gücü Kabul Et", acceptRect.centerX(), 676, text);
        cancelRect.set(lw / 2 - 250, 636, lw / 2 - 60, 696);
        pt.setColor(0x88403028);
        c.drawRoundRect(cancelRect, 30, 30, pt);
        c.drawText("Vazgeç", cancelRect.centerX(), 676, text);
    }

    private void drawWrapped(Canvas c, String s, float x, float y, float maxW) {
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        float yy = y;
        for (String wd : words) {
            String test = line.length() == 0 ? wd : line + " " + wd;
            if (text.measureText(test) > maxW && line.length() > 0) {
                c.drawText(line.toString(), x, yy, text);
                yy += 26;
                line = new StringBuilder(wd);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) c.drawText(line.toString(), x, yy, text);
    }

    // ---------- dokunma ----------
    private boolean in(RectF r, float x, float y) { return r != null && r.contains(x, y); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        synchronized (lock) { handleTouch(e); }
        return true;
    }

    private void handleTouch(MotionEvent e) {
        int act = e.getActionMasked();
        int idx = e.getActionIndex();
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            touchDown(e.getPointerId(idx), e.getX(idx) / sc, e.getY(idx) / sc);
        } else if (act == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (e.getPointerId(i) == joyPid) joyMove(e.getX(i) / sc, e.getY(i) / sc);
            }
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP || act == MotionEvent.ACTION_CANCEL) {
            int pid = e.getPointerId(idx);
            if (act == MotionEvent.ACTION_CANCEL) { joyPid = -1; attackPid = -1; world.setMove(0, 0); }
            if (pid == joyPid) { joyPid = -1; world.setMove(0, 0); }
            if (pid == attackPid) attackPid = -1;
        }
    }

    private void touchDown(int pid, float x, float y) {
        if (state == State.INTRO) {
            if (introT > 34f || (x > lw - 150 && y < 70)) startCreate();
            else if (introT > 1f && x > lw - 150 && y < 70) startCreate();
            return;
        }
        if (state == State.CREATE) {
            for (int i = 0; i < 8; i++) {
                if (in(createRects[i], x, y)) {
                    int dir = (i % 2 == 0) ? -1 : 1;
                    switch (i / 2) {
                        case 0: cStyle = (cStyle + dir + 4) % 4; break;
                        case 1: cHair = (cHair + dir + Art.HAIR.length) % Art.HAIR.length; break;
                        case 2: cSkin = (cSkin + dir + Art.SKIN.length) % Art.SKIN.length; break;
                        default: cCloak = (cCloak + dir + Art.CLOAK.length) % Art.CLOAK.length; break;
                    }
                    return;
                }
            }
            if (in(createRects[8], x, y)) {
                ((MainActivity) getContext()).askName(world.player.name, new MainActivity.NameCallback() {
                    @Override public void onName(String name) { world.player.name = name; }
                });
            } else if (in(createRects[9], x, y)) {
                startPlay();
            }
            return;
        }
        // PLAY
        if (classMenu) {
            for (int i = 0; i < 5; i++) if (in(cardRects[i], x, y)) { classSel = i; return; }
            if (in(acceptRect, x, y)) {
                world.chooseClass(HeroClass.values()[classSel]);
                classMenu = false;
                flash = 1f;
            } else if (in(cancelRect, x, y)) {
                classMenu = false;
            }
            return;
        }
        World.Player p = world.player;
        // saldırı / yetenek
        if (World.dist(x, y, atkX, atkY) < ATK_R + 12) {
            attackPid = pid;
            world.basicAttack();
            return;
        }
        for (int i = 0; i < 5; i++) {
            if (World.dist(x, y, skillX[i], skillY[i]) < SKILL_R + 10) { world.useSkill(i); return; }
        }
        // joystick (sol yarı, alt kısım)
        if (x < lw * 0.45f && y > lh * 0.3f && joyPid == -1) {
            joyPid = pid;
            joyCx = x; joyCy = y; joyKx = x; joyKy = y;
            return;
        }
        // dünyaya dokunuş: Dünya Ağacı, NPC
        float wx = x - lw / 2 + camX, wy = y - lh / 2 + camY;
        if (World.dist(wx, wy, 0, -140) < 220f && World.dist(p.x, p.y, 0, 0) < 330f) {
            if (p.hero == null) { classMenu = true; classSel = 0; world.setMove(0, 0); }
            else { speech = "Dünya Ağacı: \"Beş kahramanın gücü bende yaşıyor.\""; speechX = 0; speechY = -300; speechT = 4f; }
            return;
        }
        for (Npc n : npcs) {
            if (World.dist(wx, wy, n.x, n.y - 50) < 70f && World.dist(p.x, p.y, n.x, n.y) < 260f) {
                speech = n.lines[n.talkIdx % n.lines.length];
                n.talkIdx++;
                speechX = n.x; speechY = n.y; speechT = 4f;
                return;
            }
        }
    }

    private void joyMove(float x, float y) {
        float dx = x - joyCx, dy = y - joyCy;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > JOY_R) { dx = dx / l * JOY_R; dy = dy / l * JOY_R; }
        joyKx = joyCx + dx; joyKy = joyCy + dy;
        world.setMove(dx / JOY_R, dy / JOY_R);
    }
}
