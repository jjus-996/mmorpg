package com.mmorpgik.mapeditor;

import java.util.ArrayList;
import java.util.List;

/** Harita verisi: bölgeler + yollar. Android'den bağımsız. */
public class MapData {
    public static final int W = 2000;
    public static final String[] TYPES = {"Köy", "Gelişim", "Lonca", "Kundun", "Diğer"};

    public static class Region {
        public int id, no, type, color;
        public float x, y, r;
        public String name = "", level = "", note = "";
    }

    public static class Road { public int a, b; }

    public final List<Region> regions = new ArrayList<>();
    public final List<Road> roads = new ArrayList<>();
    public int nextId = 1;

    public Region add(float x, float y, float r, int color, int type, String name, String level) {
        Region g = new Region();
        g.id = nextId++;
        g.no = regions.size() + 1;
        g.x = x; g.y = y; g.r = r; g.color = color; g.type = type; g.name = name; g.level = level;
        regions.add(g);
        return g;
    }

    public Region byId(int id) {
        for (Region g : regions) if (g.id == id) return g;
        return null;
    }

    public void remove(int id) {
        regions.removeIf(g -> g.id == id);
        roads.removeIf(r -> r.a == id || r.b == id);
    }

    public boolean hasRoad(int a, int b) {
        for (Road r : roads) if ((r.a == a && r.b == b) || (r.a == b && r.b == a)) return true;
        return false;
    }

    public void toggleRoad(int a, int b) {
        if (a == b) return;
        for (int i = 0; i < roads.size(); i++) {
            Road r = roads.get(i);
            if ((r.a == a && r.b == b) || (r.a == b && r.b == a)) { roads.remove(i); return; }
        }
        Road r = new Road(); r.a = a; r.b = b; roads.add(r);
    }

    public static int rgb(int hex) { return 0xFF000000 | hex; }

    public static MapData defaultLayout() {
        MapData m = new MapData();
        int[][] ramps = {
            {0xC0DD97, 0x97C459, 0x639922, 0x3B6D11},
            {0xCECBF6, 0xAFA9EC, 0x7F77DD, 0x534AB7},
            {0xB5D4F4, 0x85B7EB, 0x378ADD, 0x185FA5},
            {0xF5C4B3, 0xF0997B, 0xD85A30, 0x993C1D},
            {0xFAC775, 0xEF9F27, 0xBA7517, 0x854F0B}};
        double[] ang = {-90, -18, 54, 126, 198};
        String[] lvl = {"20-90", "90-170", "170-250"};
        float[] ring = {230, 430, 630};
        float c = W / 2f;
        Region center = m.add(c, c, 120, rgb(0x9FE1CB), 0, "Huzur Ormanı", "1-20");
        int[][] ids = new int[5][4];
        for (int w = 0; w < 5; w++) {
            double a = Math.toRadians(ang[w]);
            for (int k = 0; k < 3; k++) {
                Region g = m.add((float) (c + Math.cos(a) * ring[k]), (float) (c + Math.sin(a) * ring[k]),
                        90, rgb(ramps[w][k]), 1, "Gelişim " + (w + 1) + "-" + (k + 1), lvl[k]);
                ids[w][k] = g.id;
            }
            Region g = m.add((float) (c + Math.cos(a) * 820), (float) (c + Math.sin(a) * 820),
                    110, rgb(ramps[w][3]), 2, "Lonca bölgesi " + (w + 1), "250-300");
            ids[w][3] = g.id;
        }
        m.add(1820, 180, 100, rgb(0x5F5E5A), 3, "Kundun Haritası", "300");
        for (int w = 0; w < 5; w++) {
            m.toggleRoad(center.id, ids[w][0]);
            for (int k = 0; k < 3; k++) m.toggleRoad(ids[w][k], ids[w][k + 1]);
        }
        // numaralar: merkez 1, gelişim 2-16, lonca 17-21, Kundun 22
        int dev = 2, guild = 17;
        for (Region g : m.regions) {
            if (g.type == 1) g.no = dev++;
            else if (g.type == 2) g.no = guild++;
        }
        m.regions.get(m.regions.size() - 1).no = 22;
        return m;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }
    private static String unesc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'p' ? '|' : n == 'n' ? '\n' : n);
            } else b.append(ch);
        }
        return b.toString();
    }

    public String serialize() {
        StringBuilder b = new StringBuilder("V1\n");
        for (Region g : regions)
            b.append("R|").append(g.id).append('|').append(g.no).append('|').append(g.x).append('|')
             .append(g.y).append('|').append(g.r).append('|').append(g.color).append('|').append(g.type)
             .append('|').append(esc(g.name)).append('|').append(esc(g.level)).append('|')
             .append(esc(g.note)).append('\n');
        for (Road r : roads) b.append("E|").append(r.a).append('|').append(r.b).append('\n');
        return b.toString();
    }

    /** Hatalıysa null döner. */
    public static MapData parse(String s) {
        try {
            String[] lines = s.split("\n");
            if (lines.length == 0 || !lines[0].trim().equals("V1")) return null;
            MapData m = new MapData();
            for (int i = 1; i < lines.length; i++) {
                String l = lines[i];
                if (l.isEmpty()) continue;
                String[] p = l.split("\\|", -1);
                if (p[0].equals("R")) {
                    Region g = new Region();
                    g.id = Integer.parseInt(p[1]); g.no = Integer.parseInt(p[2]);
                    g.x = Float.parseFloat(p[3]); g.y = Float.parseFloat(p[4]); g.r = Float.parseFloat(p[5]);
                    g.color = Integer.parseInt(p[6]); g.type = Integer.parseInt(p[7]);
                    g.name = unesc(p[8]); g.level = unesc(p[9]); g.note = unesc(p[10]);
                    m.regions.add(g);
                    m.nextId = Math.max(m.nextId, g.id + 1);
                } else if (p[0].equals("E")) {
                    Road r = new Road(); r.a = Integer.parseInt(p[1]); r.b = Integer.parseInt(p[2]);
                    m.roads.add(r);
                }
            }
            return m;
        } catch (Exception e) { return null; }
    }
}
