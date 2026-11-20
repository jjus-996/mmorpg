package com.mmorpgik.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Oyun dünyası ve savaş mantığı. Android'e bağımlı değildir (javac ile test edilebilir).
 * Merkez (0,0) = Dünya Ağacı. Merkezden uzaklaştıkça canavarlar güçlenir.
 */
public class World {
    public static final int MAX_LEVEL = 300;
    public static final float RADIUS = 2600f;       // dünya yarıçapı
    public static final float SAFE_RADIUS = 430f;   // köy güvenli alanı
    public static final float SPRING_X = 300f, SPRING_Y = 210f; // Yaşam Pınarı

    // ---------- Veri sınıfları ----------
    public static class Player {
        public HeroClass hero;      // null = henüz sınıf seçmedi
        public String name = "Kahraman";
        public int hairColor, skinColor, cloakColor, hairStyle;
        public float x = 0, y = 330, faceX = 0, faceY = 1;
        public int level = 1;
        public long xp = 0;
        public float hp, mp, maxHp, maxMp;
        public float[] cd = new float[5];
        public float atkCd = 0;
        public float shield = 0, haste = 0, power = 0;
        public boolean dead = false;
        public float spiritX, spiritY;
        public float walkPhase = 0;
        public boolean moving = false;
        public float hurt = 0;
        public float attackAnim = 0;
    }

    public static class Monster {
        public int kind;        // 0 Çürük Tohum, 1 Kara Kurt, 2 Yozlaşmış Ent
        public int level;
        public float x, y, homeX, homeY, hp, maxHp;
        public float atkCd, hurt, phase, respawn;
        public boolean alive = true;
        public float faceX = 1;
        public String name() { return KIND_NAMES[kind]; }
    }

    public static class Projectile {
        public float x, y, vx, vy, life, dmg, radius;
        public HeroClass hero;
        public boolean aoe;
    }

    /** Görsel efekt: halka, patlama, yazı vb. */
    public static class Fx {
        public static final int RING = 0, BLAST = 1, TEXT = 2, HEAL = 3, BOLT_STRIKE = 4, SPARK = 5;
        public int type;
        public float x, y, r, t, dur;
        public String text;
        public int color;
        public HeroClass hero;
    }

    public static class Summon {
        public float x, y, t, dur, atkCd, dmg;
        public HeroClass hero;
    }

    public static final String[] KIND_NAMES = {"Çürük Tohum", "Kara Kurt", "Yozlaşmış Ent"};

    // ---------- Durum ----------
    public final Player player = new Player();
    public final List<Monster> monsters = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Fx> fx = new ArrayList<>();
    public final List<Summon> summons = new ArrayList<>();
    public final Random rng;
    public float time = 0;
    public float moveX = 0, moveY = 0;   // -1..1 joystick
    public String toast = null;
    public float toastT = 0;

    public World(long seed) {
        rng = new Random(seed);
        spawnMonsters();
    }

    // ---------- Başlangıç ----------
    private void spawnMonsters() {
        int placed = 0, tries = 0;
        while (placed < 110 && tries < 5000) {
            tries++;
            double a = rng.nextDouble() * Math.PI * 2;
            float d = 560f + rng.nextFloat() * (RADIUS - 700f);
            float x = (float) Math.cos(a) * d;
            float y = (float) Math.sin(a) * d;
            Monster m = new Monster();
            m.level = levelAt(d);
            m.kind = d < 1150 ? 0 : (d < 1850 ? 1 : 2);
            if (rng.nextFloat() < 0.18f && m.kind > 0) m.kind--;
            m.homeX = m.x = x;
            m.homeY = m.y = y;
            m.maxHp = m.hp = 40 + m.level * 28f * (m.kind == 2 ? 1.6f : 1f);
            m.phase = rng.nextFloat() * 6.28f;
            monsters.add(m);
            placed++;
        }
    }

    /** Merkezden uzaklığa göre canavar seviyesi (ilk demo: 1-30). */
    public static int levelAt(float dist) {
        float t = (dist - SAFE_RADIUS) / (RADIUS - SAFE_RADIUS);
        if (t < 0) t = 0;
        return 1 + Math.round(t * 29f);
    }

    public void chooseClass(HeroClass h) {
        player.hero = h;
        recalcStats(true);
        say(h.hero + "'in gücünü aldın: " + h.title);
    }

    // ---------- Seviye ve statlar ----------
    public static long xpFor(int level) {
        return Math.round(30.0 * Math.pow(level, 1.55));
    }

    public float attackPower() {
        HeroClass h = player.hero;
        float base = 9 + player.level * 3.2f;
        float scale = h == null ? 1f : h.atkScale;
        return base * scale * (player.power > 0 ? 1.4f : 1f);
    }

    public void recalcStats(boolean refill) {
        HeroClass h = player.hero;
        int hb = h == null ? 100 : h.hpBase;
        int mb = h == null ? 60 : h.mpBase;
        player.maxHp = hb + player.level * 24f;
        player.maxMp = mb + player.level * 9f;
        if (refill) { player.hp = player.maxHp; player.mp = player.maxMp; }
        player.hp = Math.min(player.hp, player.maxHp);
        player.mp = Math.min(player.mp, player.maxMp);
    }

    private void gainXp(long amount) {
        if (player.level >= MAX_LEVEL) return;
        player.xp += amount;
        while (player.level < MAX_LEVEL && player.xp >= xpFor(player.level)) {
            player.xp -= xpFor(player.level);
            player.level++;
            recalcStats(true);
            text(player.x, player.y - 70, "SEVİYE " + player.level + "!", 0xFFFFE070);
            addFx(Fx.RING, player.x, player.y, 140, 0.9f, 0xFFFFE070, null);
        }
    }

    // ---------- Girdi ----------
    public void setMove(float dx, float dy) {
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 1f) { dx /= l; dy /= l; }
        moveX = dx;
        moveY = dy;
    }

    public Monster nearestMonster(float x, float y, float maxDist) {
        Monster best = null;
        float bd = maxDist;
        for (Monster m : monsters) {
            if (!m.alive) continue;
            float d = dist(x, y, m.x, m.y);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    /** Temel saldırı. */
    public void basicAttack() {
        Player p = player;
        if (p.dead || p.hero == null || p.atkCd > 0) return;
        Monster m = nearestMonster(p.x, p.y, Math.max(p.hero.basicRange + 40f, 120f));
        if (m != null) face(p, m.x - p.x, m.y - p.y);
        p.atkCd = p.hero.basicIsRanged() ? 0.75f : 0.55f;
        p.attackAnim = 0.28f;
        float dmg = attackPower();
        if (p.hero.basicIsRanged()) {
            shoot(p.x, p.y - 20, p.faceX, p.faceY, 520f, dmg, 8f, p.hero, 0.9f, false);
        } else {
            hitArc(p.x, p.y, p.faceX, p.faceY, p.hero.basicRange + 25f, dmg);
            addFx(Fx.SPARK, p.x + p.faceX * 50, p.y + p.faceY * 50 - 20, 50, 0.22f, p.hero.color, p.hero);
        }
    }

    /** 0..4 numaralı yeteneği kullan. */
    public boolean useSkill(int i) {
        Player p = player;
        if (p.dead || p.hero == null || i < 0 || i > 4) return false;
        Skill s = p.hero.skills[i];
        if (p.cd[i] > 0) return false;
        if (p.mp < s.mana) { say("Mana yetersiz"); return false; }
        Monster tgt = nearestMonster(p.x, p.y, 520f);
        if (tgt != null && s.type != Skill.Type.DASH && dist(p.x, p.y, tgt.x, tgt.y) < 520f) {
            face(p, tgt.x - p.x, tgt.y - p.y);
        }
        p.mp -= s.mana;
        p.cd[i] = s.cooldown;
        p.attackAnim = 0.35f;
        float dmg = attackPower() * s.mult;
        switch (s.type) {
            case MELEE:
                hitArc(p.x, p.y, p.faceX, p.faceY, s.size, dmg);
                addFx(Fx.BLAST, p.x + p.faceX * 60, p.y + p.faceY * 60 - 15, 70, 0.35f, p.hero.color, p.hero);
                break;
            case BOLT:
                shoot(p.x, p.y - 20, p.faceX, p.faceY, 600f, dmg, 12f, p.hero, s.size / 600f, false);
                break;
            case AOE_SELF:
                hitCircle(p.x, p.y, s.size, dmg);
                addFx(Fx.RING, p.x, p.y, s.size, 0.55f, p.hero.color, p.hero);
                addFx(Fx.BLAST, p.x, p.y, s.size * 0.7f, 0.5f, p.hero.color, p.hero);
                break;
            case AOE_TARGET: {
                float tx = p.x + p.faceX * 210f, ty = p.y + p.faceY * 210f;
                if (tgt != null && dist(p.x, p.y, tgt.x, tgt.y) < 520f) { tx = tgt.x; ty = tgt.y; }
                hitCircle(tx, ty, s.size, dmg);
                addFx(Fx.BOLT_STRIKE, tx, ty, s.size, 0.7f, p.hero.color, p.hero);
                addFx(Fx.RING, tx, ty, s.size, 0.6f, p.hero.color, p.hero);
                break;
            }
            case HEAL: {
                float heal = p.maxHp * s.size;
                p.hp = Math.min(p.maxHp, p.hp + heal);
                text(p.x, p.y - 70, "+" + Math.round(heal), 0xFF6CE07A);
                addFx(Fx.HEAL, p.x, p.y, 90, 0.9f, 0xFF6CE07A, p.hero);
                break;
            }
            case SHIELD:
                p.shield = s.size;
                addFx(Fx.RING, p.x, p.y, 70, 0.6f, p.hero.color, p.hero);
                break;
            case HASTE:
                p.haste = s.size;
                addFx(Fx.RING, p.x, p.y, 60, 0.6f, p.hero.color, p.hero);
                break;
            case POWER:
                p.power = s.size;
                addFx(Fx.RING, p.x, p.y, 100, 0.7f, p.hero.color, p.hero);
                text(p.x, p.y - 70, "GÜÇ!", p.hero.color);
                break;
            case DASH: {
                float nx = p.x + p.faceX * s.size, ny = p.y + p.faceY * s.size;
                if (s.mult > 0) {
                    for (int k = 1; k <= 4; k++) {
                        hitCircle(p.x + p.faceX * s.size * k / 4f, p.y + p.faceY * s.size * k / 4f, 70f, dmg / 2f, true);
                    }
                }
                addFx(Fx.SPARK, p.x, p.y, 60, 0.4f, p.hero.color, p.hero);
                p.x = clampX(nx, ny);
                p.y = clampY(nx, ny);
                addFx(Fx.SPARK, p.x, p.y, 60, 0.4f, p.hero.color, p.hero);
                break;
            }
            case SUMMON: {
                Summon sm = new Summon();
                sm.x = p.x + 40; sm.y = p.y - 30; sm.dur = s.size; sm.dmg = dmg; sm.hero = p.hero;
                summons.add(sm);
                addFx(Fx.BLAST, sm.x, sm.y, 100, 0.6f, p.hero.color, p.hero);
                break;
            }
        }
        return true;
    }

    // ---------- Yardımcılar ----------
    private float clampX(float x, float y) {
        float d = (float) Math.sqrt(x * x + y * y);
        return d > RADIUS - 30 ? x * (RADIUS - 30) / d : x;
    }
    private float clampY(float x, float y) {
        float d = (float) Math.sqrt(x * x + y * y);
        return d > RADIUS - 30 ? y * (RADIUS - 30) / d : y;
    }

    private void face(Player p, float dx, float dy) {
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-3f) return;
        p.faceX = dx / l;
        p.faceY = dy / l;
    }

    public static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private boolean inSafe(float x, float y) { return dist(x, y, 0, 0) < SAFE_RADIUS; }

    private void shoot(float x, float y, float dx, float dy, float speed, float dmg, float radius,
                       HeroClass h, float life, boolean aoe) {
        Projectile pr = new Projectile();
        pr.x = x; pr.y = y; pr.vx = dx * speed; pr.vy = dy * speed;
        pr.dmg = dmg; pr.radius = radius; pr.hero = h; pr.life = life; pr.aoe = aoe;
        projectiles.add(pr);
    }

    private void hitArc(float x, float y, float fx, float fy, float range, float dmg) {
        for (Monster m : monsters) {
            if (!m.alive) continue;
            float dx = m.x - x, dy = m.y - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > range) continue;
            float dot = d < 1 ? 1 : (dx * fx + dy * fy) / d;
            if (dot > 0.15f) damageMonster(m, dmg);
        }
    }

    private void hitCircle(float x, float y, float r, float dmg) { hitCircle(x, y, r, dmg, false); }

    private void hitCircle(float x, float y, float r, float dmg, boolean quiet) {
        for (Monster m : monsters) {
            if (!m.alive) continue;
            if (dist(x, y, m.x, m.y) <= r + 12f) damageMonster(m, dmg);
        }
    }

    private void damageMonster(Monster m, float base) {
        float dmg = base * (0.9f + rng.nextFloat() * 0.2f);
        boolean crit = rng.nextFloat() < 0.12f;
        if (crit) dmg *= 1.7f;
        m.hp -= dmg;
        m.hurt = 0.2f;
        text(m.x, m.y - 50, (crit ? "!" : "") + Math.round(dmg), crit ? 0xFFFFE070 : 0xFFFFFFFF);
        if (m.hp <= 0) {
            m.alive = false;
            m.respawn = 18f;
            long xp = Math.round(8 + m.level * 6 * (m.kind == 2 ? 1.6f : 1f));
            gainXp(xp);
            addFx(Fx.BLAST, m.x, m.y, 60, 0.5f, 0xFF77DD66, null);
        }
    }

    private void damagePlayer(float dmg) {
        Player p = player;
        if (p.dead) return;
        if (p.shield > 0) dmg *= 0.25f;
        p.hp -= dmg;
        p.hurt = 0.25f;
        text(p.x, p.y - 60, "-" + Math.round(dmg), 0xFFFF6A6A);
        if (p.hp <= 0) {
            p.hp = 0;
            p.dead = true;
            // Yeşil ruh Yaşam Pınarı'na doğru ilerler. (Siyah ruh oyuncudan gizlidir.)
            p.spiritX = p.x;
            p.spiritY = p.y;
            say("Yeşil ruhunu Yaşam Pınarı'na ulaştır");
        }
    }

    public void addFx(int type, float x, float y, float r, float dur, int color, HeroClass h) {
        Fx f = new Fx();
        f.type = type; f.x = x; f.y = y; f.r = r; f.dur = dur; f.color = color; f.hero = h;
        fx.add(f);
    }

    public void text(float x, float y, String s, int color) {
        Fx f = new Fx();
        f.type = Fx.TEXT; f.x = x; f.y = y; f.dur = 0.9f; f.text = s; f.color = color;
        fx.add(f);
    }

    public void say(String s) { toast = s; toastT = 2.5f; }

    // ---------- Güncelleme ----------
    public void update(float dt) {
        if (dt > 0.05f) dt = 0.05f;
        time += dt;
        Player p = player;

        for (int i = 0; i < 5; i++) if (p.cd[i] > 0) p.cd[i] = Math.max(0, p.cd[i] - dt);
        if (p.atkCd > 0) p.atkCd -= dt;
        if (p.shield > 0) p.shield -= dt;
        if (p.haste > 0) p.haste -= dt;
        if (p.power > 0) p.power -= dt;
        if (p.hurt > 0) p.hurt -= dt;
        if (p.attackAnim > 0) p.attackAnim -= dt;
        if (toastT > 0) toastT -= dt;

        if (!p.dead) {
            // mana ve can yenilenmesi
            p.mp = Math.min(p.maxMp, p.mp + (4f + p.level * 0.25f) * dt);
            if (inSafe(p.x, p.y)) p.hp = Math.min(p.maxHp, p.hp + p.maxHp * 0.03f * dt);
            float speed = 190f * (p.haste > 0 ? 1.7f : 1f);
            float mx = moveX, my = moveY;
            p.moving = mx * mx + my * my > 0.01f;
            if (p.moving) {
                float nx = p.x + mx * speed * dt, ny = p.y + my * speed * dt;
                p.x = clampX(nx, ny);
                p.y = clampY(nx, ny);
                face(p, mx, my);
                p.walkPhase += dt * (p.haste > 0 ? 14f : 9f);
            }
        } else {
            // yeşil ruh pınara gider
            float dx = SPRING_X - p.spiritX, dy = SPRING_Y - p.spiritY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float sp = 330f * dt;
            if (d <= sp + 8f) {
                p.dead = false;
                p.x = SPRING_X - 40; p.y = SPRING_Y + 40;
                p.hp = p.maxHp * 0.6f;
                p.mp = p.maxMp * 0.6f;
                addFx(Fx.HEAL, p.x, p.y, 120, 1.2f, 0xFF6CE07A, null);
                say("Yaşam Pınarı seni geri getirdi");
            } else {
                p.spiritX += dx / d * sp;
                p.spiritY += dy / d * sp;
            }
        }

        updateMonsters(dt);
        updateProjectiles(dt);
        updateSummons(dt);

        for (int i = fx.size() - 1; i >= 0; i--) {
            Fx f = fx.get(i);
            f.t += dt;
            if (f.type == Fx.TEXT) f.y -= 38f * dt;
            if (f.t >= f.dur) fx.remove(i);
        }
    }

    private void updateMonsters(float dt) {
        Player p = player;
        for (Monster m : monsters) {
            m.phase += dt;
            if (m.hurt > 0) m.hurt -= dt;
            if (!m.alive) {
                m.respawn -= dt;
                if (m.respawn <= 0) {
                    m.alive = true;
                    m.hp = m.maxHp;
                    m.x = m.homeX;
                    m.y = m.homeY;
                }
                continue;
            }
            if (m.atkCd > 0) m.atkCd -= dt;
            float aggro = 260f + m.kind * 40f;
            float speed = (m.kind == 1 ? 125f : (m.kind == 2 ? 70f : 85f));
            float d = dist(m.x, m.y, p.x, p.y);
            boolean chase = !p.dead && d < aggro && !inSafe(p.x, p.y) && !inSafe(m.x, m.y);
            if (chase) {
                if (d > 46f) {
                    m.x += (p.x - m.x) / d * speed * dt;
                    m.y += (p.y - m.y) / d * speed * dt;
                    m.faceX = p.x >= m.x ? 1 : -1;
                } else if (m.atkCd <= 0) {
                    m.atkCd = 1.3f;
                    damagePlayer(4f + m.level * 2.6f * (m.kind == 2 ? 1.4f : 1f));
                }
            } else {
                // yuvasına dön
                float hd = dist(m.x, m.y, m.homeX, m.homeY);
                if (hd > 6f) {
                    m.x += (m.homeX - m.x) / hd * speed * 0.8f * dt;
                    m.y += (m.homeY - m.y) / hd * speed * 0.8f * dt;
                    m.hp = Math.min(m.maxHp, m.hp + m.maxHp * 0.1f * dt);
                }
            }
        }
    }

    private void updateProjectiles(float dt) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile pr = projectiles.get(i);
            pr.x += pr.vx * dt;
            pr.y += pr.vy * dt;
            pr.life -= dt;
            boolean hit = false;
            for (Monster m : monsters) {
                if (!m.alive) continue;
                if (dist(pr.x, pr.y, m.x, m.y) < 34f + pr.radius) {
                    damageMonster(m, pr.dmg);
                    addFx(Fx.BLAST, pr.x, pr.y, 45, 0.3f, pr.hero == null ? 0xFFFFFFFF : pr.hero.color, pr.hero);
                    hit = true;
                    break;
                }
            }
            if (hit || pr.life <= 0) projectiles.remove(i);
        }
    }

    private void updateSummons(float dt) {
        Player p = player;
        for (int i = summons.size() - 1; i >= 0; i--) {
            Summon s = summons.get(i);
            s.t += dt;
            if (s.t >= s.dur) { summons.remove(i); continue; }
            Monster m = nearestMonster(s.x, s.y, 360f);
            float tx = p.x + 50, ty = p.y - 50;
            if (m != null) { tx = m.x - 40; ty = m.y - 30; }
            float d = dist(s.x, s.y, tx, ty);
            if (d > 8f) {
                float sp = 260f * dt;
                s.x += (tx - s.x) / d * Math.min(sp, d);
                s.y += (ty - s.y) / d * Math.min(sp, d);
            }
            if (s.atkCd > 0) s.atkCd -= dt;
            if (m != null && s.atkCd <= 0 && dist(s.x, s.y, m.x, m.y) < 100f) {
                s.atkCd = 0.8f;
                hitCircle(m.x, m.y, 70f, s.dmg);
                addFx(Fx.BLAST, m.x, m.y, 70, 0.4f, s.hero.color, s.hero);
            }
        }
    }
}
