package com.mmorpgik.mapeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Bir bölgenin 3D sahne editörü. */
public class RegionActivity extends Activity implements SceneView.Listener {
    private SceneView sv;
    private TextView hint;
    private File file;
    private String regionName;
    private Button[] toolBtns = new Button[6];
    private Button placeBtn, zoneBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        int id = getIntent().getIntExtra("id", 0);
        int type = getIntent().getIntExtra("type", 1);
        regionName = getIntent().getStringExtra("name");
        if (regionName == null) regionName = "Bölge";
        file = new File(getFilesDir(), "scene_" + id + ".txt");

        sv = new SceneView(this);
        sv.listener = this;
        SceneData sc = null;
        try {
            if (file.exists()) {
                FileInputStream in = new FileInputStream(file);
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                in.close();
                sc = SceneData.parse(new String(o.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
        if (sc == null) { sc = SceneData.defaultScene(type, id); }
        sv.setScene(sc);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(20, 20, 24));
        FrameLayout top = new FrameLayout(this);
        top.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        hint = new TextView(this);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(13);
        hint.setPadding(20, 10, 20, 10);
        hint.setBackgroundColor(Color.argb(170, 0, 0, 0));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2);
        hp.gravity = Gravity.TOP | Gravity.START;
        top.addView(hint, hp);
        root.addView(top, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(8, 8, 8, 8);
        hs.addView(bar);
        root.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        toolBtns[SceneView.T_SELECT] = btn(bar, "Seç / Taşı", v -> setTool(SceneView.T_SELECT));
        toolBtns[SceneView.T_CAMERA] = btn(bar, "Kamera", v -> setTool(SceneView.T_CAMERA));
        placeBtn = btn(bar, "Koy: Ağaç", v -> choosePlace());
        zoneBtn = btn(bar, "Alan: PvP", v -> chooseZone());
        toolBtns[SceneView.T_PLACE] = placeBtn;
        toolBtns[SceneView.T_ZONE] = zoneBtn;
        toolBtns[SceneView.T_RAISE] = btn(bar, "Yükselt", v -> setTool(SceneView.T_RAISE));
        toolBtns[SceneView.T_LOWER] = btn(bar, "Alçalt", v -> setTool(SceneView.T_LOWER));
        btn(bar, "Döndür", v -> { sv.rotateSelected(30); save(); });
        btn(bar, "Büyüt", v -> { sv.scaleSelected(1.2f); save(); });
        btn(bar, "Küçült", v -> { sv.scaleSelected(0.83f); save(); });
        btn(bar, "Ad", v -> rename());
        btn(bar, "Sil", v -> { if (sv.selectedObj() == null) toast("Önce bir obje seç"); else { sv.deleteSelected(); onSelect(null); save(); } });
        btn(bar, "Paylaş", v -> share());
        btn(bar, "Sıfırla", v -> reset(type, id));
        btn(bar, "Geri", v -> finish());
        setContentView(root);
        setTool(SceneView.T_SELECT);
    }

    private Button btn(LinearLayout bar, String t, android.view.View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setOnClickListener(l);
        bar.addView(x, new LinearLayout.LayoutParams(-2, -2));
        return x;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void setTool(int t) {
        sv.tool = t;
        for (int i = 0; i < toolBtns.length; i++) {
            String s = toolBtns[i].getText().toString().replace("● ", "");
            toolBtns[i].setText(i == t ? "● " + s : s);
        }
        String[] h = {
            "Seç: objeye dokun/sürükle • boşta sürükle: kamerayı döndür • iki parmak: yakınlaş/kaydır",
            "Kamera: sürükle döndür • iki parmak yakınlaş/kaydır",
            "Koy: zemine dokununca obje yerleşir • sürükleyerek kamerayı döndür",
            "Alan: parmağınla sürükleyerek boya",
            "Yükselt: sürükleyerek araziyi yükselt",
            "Alçalt: sürükleyerek araziyi alçalt"};
        hint.setText(regionName + " — " + h[t]);
    }

    private void choosePlace() {
        new AlertDialog.Builder(this).setTitle("Ne koyalım?").setItems(SceneData.OBJ, (d, w) -> {
            sv.placeType = w;
            placeBtn.setText("Koy: " + SceneData.OBJ[w]);
            setTool(SceneView.T_PLACE);
        }).show();
    }

    private void chooseZone() {
        new AlertDialog.Builder(this).setTitle("Alan türü").setItems(SceneData.ZONES, (d, w) -> {
            sv.zoneType = w;
            zoneBtn.setText("Alan: " + SceneData.ZONES[w].replace(" (boş)", ""));
            setTool(SceneView.T_ZONE);
        }).show();
    }

    private void rename() {
        final SceneData.Obj o = sv.selectedObj();
        if (o == null) { toast("Önce bir obje seç"); return; }
        final EditText e = new EditText(this);
        e.setText(o.name);
        new AlertDialog.Builder(this).setTitle("Ad").setView(e).setPositiveButton("Tamam", (d, w) -> {
            o.name = e.getText().toString();
            onSelect(o);
            save();
        }).setNegativeButton("İptal", null).show();
    }

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        synchronized (sv) { i.putExtra(Intent.EXTRA_TEXT, sv.scene.serialize()); }
        startActivity(Intent.createChooser(i, "Sahneyi gönder"));
    }

    private void reset(final int type, final int id) {
        new AlertDialog.Builder(this).setTitle("Sıfırla").setMessage("Bu bölgenin 3D sahnesi baştan oluşturulsun mu?")
            .setPositiveButton("Sıfırla", (d, w) -> { sv.setScene(SceneData.defaultScene(type, id)); save(); })
            .setNegativeButton("İptal", null).show();
    }

    private void save() {
        try {
            String s;
            synchronized (sv) { s = sv.scene.serialize(); }
            FileOutputStream o = new FileOutputStream(file);
            o.write(s.getBytes(StandardCharsets.UTF_8));
            o.close();
        } catch (Exception e) { toast("Kaydedilemedi: " + e.getMessage()); }
    }

    @Override public void onChanged() { save(); }
    @Override public void onSelect(SceneData.Obj o) {
        runOnUiThread(() -> hint.setText(o == null ? regionName : regionName + " — seçili: " + o.name + " (" + SceneData.OBJ[o.type] + ")"));
    }
    @Override public void onHint(String s) { }

    @Override protected void onPause() { super.onPause(); sv.onPause(); save(); }
    @Override protected void onResume() { super.onResume(); sv.onResume(); }
}
