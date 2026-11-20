package com.mmorpgik;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class MainActivity extends Activity {
    public interface NameCallback { void onName(String name); }

    private GameView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this);
        setContentView(view);
        hideBars();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.resumeGame();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pauseGame();
    }

    /** Karakter ismi için küçük bir giriş penceresi. */
    public void askName(final String current, final NameCallback cb) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final EditText et = new EditText(MainActivity.this);
                et.setText(current);
                et.setSingleLine(true);
                et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Kahramanın adı")
                        .setView(et)
                        .setPositiveButton("Tamam", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                String s = et.getText().toString().trim();
                                if (s.length() > 0) cb.onName(s);
                                hideBars();
                            }
                        })
                        .setNegativeButton("Vazgeç", null)
                        .show();
            }
        });
    }
}
