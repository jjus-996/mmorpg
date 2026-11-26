package com.mmorpgik.mapeditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bir bölgenin 3D sahnesi: arazi yüksekliği, alan boyaması, objeler. Android'den bağımsız. */
public class SceneData {
    public static final int N = 40;
    public static final String[] OBJ = {"Ağaç", "Ev", "Kule", "Çadır", "Kaya", "NPC", "Kapı", "Ulu Ağaç", "Canavar noktası"};
    public static final String[] ZONES = {"Sil (boş)", "PvP alanı", "Güvenli alan", "Canavar alanı", "Özel / kapı alanı"};

    public static class Obj {
        public int id, type;
        public float x, z, rot, scale = 1f;
        public String name = "";
    }

    public final float[] h = new float[(N + 1) * (N + 1)];
    public final byte[] zone = new byte[N * N];
    public final List<Obj> objs = new ArrayList<>();
    public int nextId = 1;

    public float vh(int i, int j) { return h[i * (N + 1) + j]; }

    public float heightAt(float x, float z) {
        float fx = Math.max(0, Math.min(N - 0.001f, x + N / 2f));
        float fz = Math.max(0, Math.min(N - 0.001f, z + N / 2f));
        int i = (int) fx, j = (int) fz;
        float u = fx - i, v = fz - j;
        float a = vh(i, j), b = vh(i + 1, j), c = vh(i, j + 1), d = vh(i + 1, j + 1);
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
    }

    public Obj add(int type, float x, float z) {
        Obj o = new Obj();
        o.id = nextId++;
        o.type = type; o.x = x; o.z = z;
        o.scale = type == 7 ? 1.5f : 1f;
        o.name = OBJ[type];
        objs.add(o);
        return o;
    }

    public Obj byId(int id) {
        for (Obj o : objs) if (o.id == id) return o;
        return null;
    }

    public void remove(int id) { objs.removeIf(o -> o.id == id); }

    /** regionType: 0 köy, 1 gelişim, 2 lonca, 3 Kundun, 4 diğer */
    public static SceneData defaultScene(int regionType, long seed) {
        SceneData s = new SceneData();
        Random r = new Random(seed * 31 + 7);
        double p1 = r.nextDouble() * 6, p2 = r.nextDouble() * 6;
        int half = N / 2;
        for (int i = 0; i <= N; i++)
            for (int j = 0; j <= N; j++) {
                float x = i - half, z = j - half;
                float d = (float) Math.sqrt(x * x + z * z) / half;
                float hh = (1 - d * d) * 2.4f - 0.2f
                        + 0.35f * (float) (Math.sin(i * 0.45 + p1) * Math.cos(j * 0.38 + p2));
                if (regionType == 0 && d < 0.45f) hh = hh * Math.max(0f, (d - 0.3f) / 0.15f) + 0.9f * Math.min(1f, (0.45f - d) / 0.15f);
                s.h[i * (N + 1) + j] = hh;
            }
        int trees = regionType == 0 ? 14 : 34;
        for (int k = 0; k < trees; k++) {
            float x = (r.nextFloat() - 0.5f) * (N - 6), z = (r.nextFloat() - 0.5f) * (N - 6);
            if (s.heightAt(x, z) < 0.4f || Math.sqrt(x * x + z * z) < 6) continue;
            s.add(0, x, z).rot = r.nextFloat() * 360;
        }
        for (int k = 0; k < 8; k++) {
            float x = (r.nextFloat() - 0.5f) * (N - 6), z = (r.nextFloat() - 0.5f) * (N - 6);
            if (s.heightAt(x, z) < 0.3f || Math.sqrt(x * x + z * z) < 5) continue;
            s.add(4, x, z).rot = r.nextFloat() * 360;
        }
        if (regionType == 0) {
            s.add(7, 0, 0);
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3;
                Obj o = s.add(1, (float) Math.cos(a) * 10, (float) Math.sin(a) * 10);
                o.rot = (float) (-Math.toDegrees(a) + 90);
            }
            for (int k = 0; k < 5; k++) {
                double a = k * Math.PI * 2 / 5 + 0.3;
                Obj o = s.add(5, (float) Math.cos(a) * 5, (float) Math.sin(a) * 5);
                o.name = "NPC " + (k + 1);
            }
            for (int i = 0; i < N; i++) for (int j = 0; j < N; j++) {
                float x = i + 0.5f - half, z = j + 0.5f - half;
                if (Math.sqrt(x * x + z * z) < 13) s.zone[i * N + j] = 2;
            }
        } else if (regionType == 2) {
            float[][] t = {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}, {0, 0}};
            for (float[] q : t) s.add(2, q[0], q[1]);
            s.add(6, 0, 10);
        } else if (regionType == 3) {
            s.add(6, 0, 0).name = "Kundun kapısı";
            for (int k = 0; k < 4; k++) s.add(8, (k % 2 == 0 ? -1 : 1) * 7, (k < 2 ? -1 : 1) * 7);
        } else {
            for (int k = 0; k < 5; k++) {
                double a = r.nextDouble() * Math.PI * 2;
                float rr = 4 + r.nextFloat() * 8;
                float x = (float) Math.cos(a) * rr, z = (float) Math.sin(a) * rr;
                if (s.heightAt(x, z) > 0.4f) s.add(8, x, z);
            }
        }
        return s;
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n"); }
    private static String unesc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) { char n = s.charAt(++i); b.append(n == 'p' ? '|' : n == 'n' ? '\n' : n); }
            else b.append(c);
        }
        return b.toString();
    }

    public String serialize() {
        StringBuilder b = new StringBuilder("S1\nH|");
        for (int i = 0; i < h.length; i++) { if (i > 0) b.append(','); b.append(Math.round(h[i] * 100) / 100f); }
        b.append("\nZ|");
        for (byte z : zone) b.append((char) ('0' + z));
        b.append('\n');
        for (Obj o : objs)
            b.append("O|").append(o.id).append('|').append(o.type).append('|').append(o.x).append('|').append(o.z)
             .append('|').append(o.rot).append('|').append(o.scale).append('|').append(esc(o.name)).append('\n');
        return b.toString();
    }

    public static SceneData parse(String str) {
        try {
            String[] lines = str.split("\n");
            if (!lines[0].trim().equals("S1")) return null;
            SceneData s = new SceneData();
            for (int k = 1; k < lines.length; k++) {
                String l = lines[k];
                if (l.startsWith("H|")) {
                    String[] p = l.substring(2).split(",");
                    if (p.length != s.h.length) return null;
                    for (int i = 0; i < p.length; i++) s.h[i] = Float.parseFloat(p[i]);
                } else if (l.startsWith("Z|")) {
                    String z = l.substring(2);
                    if (z.length() != s.zone.length) return null;
                    for (int i = 0; i < z.length(); i++) s.zone[i] = (byte) (z.charAt(i) - '0');
                } else if (l.startsWith("O|")) {
                    String[] p = l.split("\\|", -1);
                    Obj o = new Obj();
                    o.id = Integer.parseInt(p[1]); o.type = Integer.parseInt(p[2]);
                    if (o.type < 0 || o.type >= OBJ.length) continue;
                    o.x = Float.parseFloat(p[3]); o.z = Float.parseFloat(p[4]);
                    o.rot = Float.parseFloat(p[5]); o.scale = Float.parseFloat(p[6]); o.name = unesc(p[7]);
                    s.objs.add(o);
                    s.nextId = Math.max(s.nextId, o.id + 1);
                }
            }
            return s;
        } catch (Exception e) { return null; }
    }
}
