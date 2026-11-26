package com.mmorpgik.mapeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity implements MapView.Listener {
    private static final int REQ_BG = 7;
    private MapView mv;
    private TextView hint;
    private Button connectBtn;
    private File saveFile;

    private static final int[] SWATCH = {
        0x97C459, 0x3B6D11, 0x9FE1CB, 0xAFA9EC, 0x534AB7, 0x85B7EB, 0x185FA5, 0xF0997B, 0x993C1D,
        0xEF9F27, 0x854F0B, 0xE8D8A0, 0xD3D1C7, 0x5F5E5A, 0xFFFFFF, 0x2C2C2A, 0xE24B4A, 0xD4537E};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        saveFile = new File(getFilesDir(), "map.txt");
        mv = new MapView(this);
        mv.listener = this;
        try {
            if (saveFile.exists()) {
                MapData m = MapData.parse(new String(readAll(new java.io.FileInputStream(saveFile)), StandardCharsets.UTF_8));
                if (m != null) mv.map = m;
            }
            File bgf = new File(getFilesDir(), "bg.jpg");
            if (bgf.exists()) mv.bg = BitmapFactory.decodeFile(bgf.getPath());
        } catch (Exception ignored) { }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(20, 20, 24));

        FrameLayout top = new FrameLayout(this);
        top.addView(mv, new FrameLayout.LayoutParams(-1, -1));
        hint = new TextView(this);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(13);
        hint.setPadding(20, 10, 20, 10);
        hint.setBackgroundColor(Color.argb(170, 0, 0, 0));
        hint.setText("Sürükle: bölge taşı • boş yerde sürükle: kaydır • iki parmak: yakınlaş");
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2);
        hp.gravity = Gravity.TOP | Gravity.START;
        top.addView(hint, hp);
        root.addView(top, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(8, 8, 8, 8);
        hs.addView(bar);
        root.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        addBtn(bar, "+ Bölge", v -> addRegion());
        addBtn(bar, "Düzenle", v -> editRegion());
        addBtn(bar, "3D aç", v -> open3d());
        addBtn(bar, "Renk", v -> pickColor());
        connectBtn = addBtn(bar, "Yol", v -> { mv.setConnectMode(!mv.connectMode); connectBtn.setText(mv.connectMode ? "Yol: AÇIK" : "Yol"); });
        addBtn(bar, "Sil", v -> deleteRegion());
        addBtn(bar, "Küçült", v -> resize(0.85f));
        addBtn(bar, "Büyüt", v -> resize(1.18f));
        addBtn(bar, "Sığdır", v -> mv.fit());
        addBtn(bar, "Arka plan", v -> bgMenu());
        addBtn(bar, "Dışa aktar", v -> exportMenu());
        addBtn(bar, "Sıfırla", v -> resetMap());

        setContentView(root);
    }

    private Button addBtn(LinearLayout bar, String t, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(t);
        bt.setAllCaps(false);
        bt.setOnClickListener(l);
        bar.addView(bt, new LinearLayout.LayoutParams(-2, -2));
        return bt;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        return o.toByteArray();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // --- Listener ---
    @Override public void onChanged() { save(); }
    @Override public void onSelect(MapData.Region r) {
        hint.setText(r == null ? "Bölge seçili değil" : r.no + " • " + r.name + " • Sv " + r.level + " • " + MapData.TYPES[r.type]);
    }
    @Override public void onHint(String s) { hint.setText(s); }

    private void save() {
        try {
            FileOutputStream o = new FileOutputStream(saveFile);
            o.write(mv.map.serialize().getBytes(StandardCharsets.UTF_8));
            o.close();
        } catch (Exception e) { toast("Kaydedilemedi: " + e.getMessage()); }
    }

    // --- bölge işlemleri ---
    private void addRegion() {
        float cx = mv.getWidth() / 2f, cy = mv.getHeight() / 2f;
        MapData.Region g = mv.map.add(1000, 1000, 90, MapData.rgb(0x97C459), 1, "Yeni bölge", "");
        // ekran ortasına koy
        g.x = mv.screenToWorldX(cx); g.y = mv.screenToWorldY(cy);
        g.no = nextNo();
        mv.selected = g.id;
        onSelect(g);
        mv.invalidate();
        save();
    }

    private int nextNo() {
        int max = 0;
        for (MapData.Region g : mv.map.regions) max = Math.max(max, g.no);
        return max + 1;
    }

    private void editRegion() {
        final MapData.Region g = mv.selectedRegion();
        if (g == null) { toast("Önce bir bölge seç"); return; }
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(40, 20, 40, 0);
        final EditText name = field(l, "Ad", g.name, false);
        final EditText lvl = field(l, "Seviye (örn 20-90)", g.level, false);
        final EditText no = field(l, "Numara", String.valueOf(g.no), true);
        final EditText note = field(l, "Not", g.note, false);
        note.setMinLines(3);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView tl = new TextView(this);
        tl.setText("Tür");
        l.addView(tl);
        final int[] type = {g.type};
        final Button tb = new Button(this);
        tb.setText(MapData.TYPES[g.type]);
        tb.setOnClickListener(v -> {
            type[0] = (type[0] + 1) % MapData.TYPES.length;
            tb.setText(MapData.TYPES[type[0]]);
        });
        l.addView(tb);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(l);
        new AlertDialog.Builder(this).setTitle("Bölgeyi düzenle").setView(sv)
            .setPositiveButton("Tamam", (d, w) -> {
                g.name = name.getText().toString();
                g.level = lvl.getText().toString();
                g.note = note.getText().toString();
                try { g.no = Integer.parseInt(no.getText().toString().trim()); } catch (Exception ignored) { }
                g.type = type[0];
                onSelect(g);
                mv.invalidate();
                save();
            }).setNegativeButton("İptal", null).show();
    }

    private void open3d() {
        MapData.Region g = mv.selectedRegion();
        if (g == null) { toast("Önce bir bölge seç"); return; }
        Intent i = new Intent(this, RegionActivity.class);
        i.putExtra("id", g.id);
        i.putExtra("type", g.type);
        i.putExtra("name", g.name);
        startActivity(i);
    }

    private EditText field(LinearLayout l, String label, String val, boolean number) {
        TextView t = new TextView(this);
        t.setText(label);
        l.addView(t);
        EditText e = new EditText(this);
        e.setText(val);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        l.addView(e);
        return e;
    }

    private void pickColor() {
        final MapData.Region g = mv.selectedRegion();
        if (g == null) { toast("Önce bir bölge seç"); return; }
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(30, 20, 30, 10);
        LinearLayout row = null;
        for (int i = 0; i < SWATCH.length; i++) {
            if (i % 6 == 0) {
                row = new LinearLayout(this);
                grid.addView(row);
            }
            final int col = MapData.rgb(SWATCH[i]);
            View sw = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(col);
            gd.setCornerRadius(12);
            gd.setStroke(2, Color.GRAY);
            sw.setBackground(gd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(110, 110);
            lp.setMargins(8, 8, 8, 8);
            sw.setOnClickListener(v -> {
                g.color = col;
                mv.invalidate();
                save();
                dlg[0].dismiss();
            });
            row.addView(sw, lp);
        }
        dlg[0] = new AlertDialog.Builder(this).setTitle("Renk seç").setView(grid)
            .setNegativeButton("Kapat", null).show();
    }

    private void deleteRegion() {
        final MapData.Region g = mv.selectedRegion();
        if (g == null) { toast("Önce bir bölge seç"); return; }
        new AlertDialog.Builder(this).setTitle("Sil").setMessage(g.name + " silinsin mi? Bağlı yollar da silinir.")
            .setPositiveButton("Sil", (d, w) -> {
                mv.map.remove(g.id);
                mv.selected = -1;
                onSelect(null);
                mv.invalidate();
                save();
            }).setNegativeButton("İptal", null).show();
    }

    private void resize(float f) {
        MapData.Region g = mv.selectedRegion();
        if (g == null) { toast("Önce bir bölge seç"); return; }
        g.r = Math.max(30, Math.min(400, g.r * f));
        mv.invalidate();
        save();
    }

    private void resetMap() {
        new AlertDialog.Builder(this).setTitle("Sıfırla")
            .setMessage("Tüm değişiklikler silinip varsayılan 22 bölgelik düzen yüklensin mi?")
            .setPositiveButton("Sıfırla", (d, w) -> {
                mv.map = MapData.defaultLayout();
                mv.selected = -1;
                mv.fit();
                save();
            }).setNegativeButton("İptal", null).show();
    }

    // --- arka plan ---
    private void bgMenu() {
        String[] items = {"Resim seç", mv.showBg ? "Gizle" : "Göster", "Saydamlık", "Kaldır"};
        new AlertDialog.Builder(this).setTitle("Arka plan resmi").setItems(items, (d, which) -> {
            if (which == 0) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                startActivityForResult(Intent.createChooser(i, "Resim seç"), REQ_BG);
            } else if (which == 1) {
                mv.showBg = !mv.showBg;
                mv.invalidate();
            } else if (which == 2) {
                SeekBar sb = new SeekBar(this);
                sb.setMax(255);
                sb.setProgress(mv.bgAlpha);
                sb.setPadding(50, 40, 50, 40);
                sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar s, int p, boolean u) { mv.bgAlpha = p; mv.invalidate(); }
                    @Override public void onStartTrackingTouch(SeekBar s) { }
                    @Override public void onStopTrackingTouch(SeekBar s) { }
                });
                new AlertDialog.Builder(this).setTitle("Saydamlık").setView(sb).setPositiveButton("Tamam", null).show();
            } else {
                mv.bg = null;
                new File(getFilesDir(), "bg.jpg").delete();
                mv.invalidate();
            }
        }).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_BG && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                byte[] bytes = readAll(in);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                int ss = 1;
                while (o.outWidth / ss > 2400 || o.outHeight / ss > 2400) ss *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = ss;
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                if (bmp == null) { toast("Resim açılamadı"); return; }
                FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), "bg.jpg"));
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fo);
                fo.close();
                mv.bg = bmp;
                mv.showBg = true;
                mv.invalidate();
                toast("Arka plan ayarlandı (2000x2000 alana gerilir)");
            } catch (Exception e) { toast("Hata: " + e.getMessage()); }
        }
    }

    // --- dışa aktar ---
    private void exportMenu() {
        String[] items = {"PNG olarak kaydet", "PNG paylaş", "Haritayı metin olarak paylaş", "Metinden geri yükle"};
        new AlertDialog.Builder(this).setTitle("Dışa aktar").setItems(items, (d, w) -> {
            if (w == 0) { Uri u = savePng(); if (u != null) toast("Galeriye kaydedildi: Pictures/Mmorpgik"); }
            else if (w == 1) {
                Uri u = savePng();
                if (u != null) {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/png");
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Paylaş"));
                }
            } else if (w == 2) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, mv.map.serialize());
                startActivity(Intent.createChooser(i, "Haritayı gönder"));
            } else restoreText();
        }).show();
    }

    private Uri savePng() {
        try {
            Bitmap bmp = mv.renderBitmap(2000);
            String name = "harita_" + System.currentTimeMillis() + ".png";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mmorpgik");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                OutputStream o = getContentResolver().openOutputStream(u);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, o);
                o.close();
                return u;
            } else {
                File dir = getExternalFilesDir(null);
                File f = new File(dir, name);
                FileOutputStream o = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, o);
                o.close();
                toast("Kaydedildi: " + f.getPath());
                return null;
            }
        } catch (Exception e) { toast("Hata: " + e.getMessage()); return null; }
    }

    private void restoreText() {
        final EditText e = new EditText(this);
        e.setHint("Harita metnini buraya yapıştır");
        e.setMinLines(6);
        e.setGravity(Gravity.TOP);
        new AlertDialog.Builder(this).setTitle("Metinden geri yükle").setView(e)
            .setPositiveButton("Yükle", (d, w) -> {
                MapData m = MapData.parse(e.getText().toString());
                if (m == null) { toast("Geçersiz harita metni"); return; }
                mv.map = m;
                mv.selected = -1;
                mv.fit();
                save();
            }).setNegativeButton("İptal", null).show();
    }
}
