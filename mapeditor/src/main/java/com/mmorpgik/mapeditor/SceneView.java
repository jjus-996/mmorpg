package com.mmorpgik.mapeditor;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SceneView extends GLSurfaceView implements GLSurfaceView.Renderer {
    public interface Listener {
        void onChanged();
        void onSelect(SceneData.Obj o);
        void onHint(String s);
    }

    public static final int T_SELECT = 0, T_CAMERA = 1, T_PLACE = 2, T_ZONE = 3, T_RAISE = 4, T_LOWER = 5;

    public SceneData scene = new SceneData();
    public Listener listener;
    public int tool = T_SELECT, placeType = 0, zoneType = 1, selected = -1;

    private float yaw = 0.6f, pitch = 0.95f, dist = 34f, tx = 0, tz = 0;
    private final float[] proj = new float[16], view = new float[16], vp = new float[16], mdl = new float[16], mvp = new float[16];
    private int vw = 1, vh = 1;
    private boolean terrainDirty = true;
    private boolean vpValid = false;

    private int prog, aPos, aNor, aCol, uMVP, uModel, uTint;
    private FloatBuffer terrainBuf, waterBuf;
    private int terrainCount, waterCount;
    private final FloatBuffer[] protoBuf = new FloatBuffer[SceneData.OBJ.length];
    private final int[] protoCount = new int[SceneData.OBJ.length];
    private static final float[] PICK_H = {1.0f, 0.8f, 1.5f, 0.6f, 0.4f, 0.7f, 1.0f, 3.0f, 0.5f};
    private static final float[] PICK_R = {0.8f, 1.0f, 0.9f, 1.0f, 0.8f, 0.5f, 1.3f, 2.2f, 0.6f};

    public SceneView(Context c) {
        super(c);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public void redraw() { terrainDirty = true; requestRender(); }

    public void setScene(SceneData s) {
        synchronized (this) { scene = s; selected = -1; terrainDirty = true; }
        requestRender();
    }

    // ---------------- mesh üretimi ----------------
    private static class MB {
        float[] d = new float[9 * 3 * 64];
        int n = 0; // float sayısı
        float cx, cy, cz;
        void tri(float ax, float ay, float az, float bx, float by, float bz, float cx2, float cy2, float cz2, float[] col) {
            float ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx2 - ax, vy = cy2 - ay, vz = cz2 - az;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-8f) return;
            nx /= l; ny /= l; nz /= l;
            float mx = (ax + bx + cx2) / 3 - cx, my = (ay + by + cy2) / 3 - cy, mz = (az + bz + cz2) / 3 - cz;
            if (nx * mx + ny * my + nz * mz < 0) { nx = -nx; ny = -ny; nz = -nz; }
            if (n + 27 > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
            float[][] v = {{ax, ay, az}, {bx, by, bz}, {cx2, cy2, cz2}};
            for (float[] p : v) {
                d[n++] = p[0]; d[n++] = p[1]; d[n++] = p[2];
                d[n++] = nx; d[n++] = ny; d[n++] = nz;
                d[n++] = col[0]; d[n++] = col[1]; d[n++] = col[2];
            }
        }
        void quad(float[] a, float[] b, float[] c, float[] e, float[] col) {
            tri(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], col);
            tri(a[0], a[1], a[2], c[0], c[1], c[2], e[0], e[1], e[2], col);
        }
        void box(float x, float y, float z, float hx, float hy, float hz, float[] col) {
            cx = x; cy = y; cz = z;
            float[][] p = new float[8][];
            for (int i = 0; i < 8; i++)
                p[i] = new float[]{x + ((i & 1) == 0 ? -hx : hx), y + ((i & 2) == 0 ? -hy : hy), z + ((i & 4) == 0 ? -hz : hz)};
            quad(p[0], p[1], p[3], p[2], col); quad(p[4], p[5], p[7], p[6], col);
            quad(p[0], p[1], p[5], p[4], col); quad(p[2], p[3], p[7], p[6], col);
            quad(p[0], p[2], p[6], p[4], col); quad(p[1], p[3], p[7], p[5], col);
        }
        void cyl(float x, float y0, float z, float r, float h, int seg, float[] col) {
            cx = x; cy = y0 + h / 2; cz = z;
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                float x0 = x + (float) Math.cos(a0) * r, z0 = z + (float) Math.sin(a0) * r;
                float x1 = x + (float) Math.cos(a1) * r, z1 = z + (float) Math.sin(a1) * r;
                quad(new float[]{x0, y0, z0}, new float[]{x1, y0, z1}, new float[]{x1, y0 + h, z1}, new float[]{x0, y0 + h, z0}, col);
                tri(x, y0 + h, z, x0, y0 + h, z0, x1, y0 + h, z1, col);
            }
        }
        void cone(float x, float y0, float z, float r, float h, int seg, double phase, float[] col) {
            cx = x; cy = y0 + h * 0.3f; cz = z;
            for (int i = 0; i < seg; i++) {
                double a0 = phase + i * Math.PI * 2 / seg, a1 = phase + (i + 1) * Math.PI * 2 / seg;
                float x0 = x + (float) Math.cos(a0) * r, z0 = z + (float) Math.sin(a0) * r;
                float x1 = x + (float) Math.cos(a1) * r, z1 = z + (float) Math.sin(a1) * r;
                tri(x0, y0, z0, x1, y0, z1, x, y0 + h, z, col);
                tri(x0, y0, z0, x1, y0, z1, x, y0, z, col);
            }
        }
        FloatBuffer buf() {
            FloatBuffer fb = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(d, 0, n);
            fb.position(0);
            return fb;
        }
    }

    private static float[] c(int hex) {
        return new float[]{((hex >> 16) & 255) / 255f, ((hex >> 8) & 255) / 255f, (hex & 255) / 255f};
    }

    private MB proto(int t) {
        MB m = new MB();
        switch (t) {
            case 0:
                m.cyl(0, 0, 0, 0.12f, 0.7f, 6, c(0x6B4A2B));
                m.cone(0, 0.45f, 0, 0.7f, 1.0f, 7, 0, c(0x2E7D32));
                m.cone(0, 1.05f, 0, 0.5f, 0.9f, 7, 0, c(0x43A047));
                break;
            case 1:
                m.box(0, 0.45f, 0, 0.8f, 0.45f, 0.6f, c(0xE8D8A0));
                m.cone(0, 0.9f, 0, 1.05f, 0.75f, 4, Math.PI / 4, c(0xB5472E));
                m.box(0, 0.3f, 0.6f, 0.15f, 0.3f, 0.02f, c(0x5A3A1F));
                break;
            case 2:
                m.cyl(0, 0, 0, 0.6f, 2.2f, 8, c(0x9A9A9A));
                m.cone(0, 2.2f, 0, 0.85f, 1.0f, 8, 0, c(0x3B6FC4));
                m.box(0, 3.5f, 0, 0.03f, 0.3f, 0.03f, c(0x444444));
                break;
            case 3:
                m.cone(0, 0, 0, 1.0f, 1.2f, 4, Math.PI / 4, c(0x3B8DE0));
                break;
            case 4:
                m.cone(0, 0, 0, 0.75f, 0.6f, 5, 0.3, c(0x8A8A8A));
                m.cone(0.5f, 0, 0.2f, 0.4f, 0.35f, 5, 0, c(0x777777));
                break;
            case 5:
                m.cyl(0, 0, 0, 0.22f, 0.7f, 8, c(0x3B6FC4));
                m.box(0, 0.88f, 0, 0.16f, 0.16f, 0.16f, c(0xF1C9A0));
                m.cone(0, 1.02f, 0, 0.2f, 0.25f, 6, 0, c(0xC0392B));
                m.box(0, 1.6f, 0, 0.04f, 0.14f, 0.04f, c(0xFFD21F));
                m.box(0, 1.35f, 0, 0.04f, 0.04f, 0.04f, c(0xFFD21F));
                break;
            case 6:
                m.box(-1.0f, 0.9f, 0, 0.2f, 0.9f, 0.2f, c(0x7A7A80));
                m.box(1.0f, 0.9f, 0, 0.2f, 0.9f, 0.2f, c(0x7A7A80));
                m.box(0, 1.95f, 0, 1.25f, 0.15f, 0.22f, c(0x7A7A80));
                m.box(0, 0.9f, 0, 0.8f, 0.8f, 0.03f, c(0x7A3FD1));
                break;
            case 7:
                m.cyl(0, 0, 0, 0.9f, 3.0f, 10, c(0x6B4A2B));
                m.cone(0, 2.2f, 0, 3.0f, 3.2f, 10, 0, c(0x2E7D32));
                m.cone(0, 3.9f, 0, 2.2f, 2.8f, 10, 0, c(0x66BB6A));
                break;
            default:
                m.cyl(0, 0, 0, 0.6f, 0.05f, 10, c(0x8B1A1A));
                m.cone(0, 0.05f, 0, 0.35f, 0.9f, 6, 0, c(0xE53935));
        }
        return m;
    }

    // ---------------- GL ----------------
    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
        String vs = "uniform mat4 uMVP; uniform mat4 uModel; attribute vec3 aPos; attribute vec3 aNor; attribute vec3 aCol;"
                + "varying vec3 vCol; varying vec3 vNor;"
                + "void main(){ gl_Position = uMVP * vec4(aPos,1.0); vec4 n = uModel * vec4(aNor,0.0); vNor = n.xyz; vCol = aCol; }";
        String fs = "precision mediump float; varying vec3 vCol; varying vec3 vNor; uniform vec3 uTint;"
                + "void main(){ vec3 n = normalize(vNor); float d = max(dot(n, normalize(vec3(0.5,0.9,0.4))), 0.0);"
                + "gl_FragColor = vec4(vCol * (0.42 + 0.62 * d) + uTint, 1.0); }";
        prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, vs));
        GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, fs));
        GLES20.glLinkProgram(prog);
        aPos = GLES20.glGetAttribLocation(prog, "aPos");
        aNor = GLES20.glGetAttribLocation(prog, "aNor");
        aCol = GLES20.glGetAttribLocation(prog, "aCol");
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP");
        uModel = GLES20.glGetUniformLocation(prog, "uModel");
        uTint = GLES20.glGetUniformLocation(prog, "uTint");
        for (int i = 0; i < protoBuf.length; i++) {
            MB m = proto(i);
            protoBuf[i] = m.buf();
            protoCount[i] = m.n / 9;
        }
        MB w = new MB();
        float R = 90;
        w.cx = 0; w.cy = -5; w.cz = 0;
        w.quad(new float[]{-R, 0, -R}, new float[]{R, 0, -R}, new float[]{R, 0, R}, new float[]{-R, 0, R}, c(0x2F7FC4));
        waterBuf = w.buf();
        waterCount = w.n / 9;
        terrainDirty = true;
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
        vw = Math.max(1, w); vh = Math.max(1, h);
        GLES20.glViewport(0, 0, w, h);
        Matrix.perspectiveM(proj, 0, 50f, vw / (float) vh, 0.5f, 250f);
    }

    private void buildTerrain() {
        MB m = new MB();
        int N = SceneData.N, half = N / 2;
        float[] col = new float[3];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) {
                float h00 = scene.vh(i, j), h10 = scene.vh(i + 1, j), h11 = scene.vh(i + 1, j + 1), h01 = scene.vh(i, j + 1);
                float avg = (h00 + h10 + h11 + h01) / 4f;
                float[] base;
                if (avg < 0.25f) base = c(0xD9C78C);
                else if (avg < 3f) {
                    float t = Math.min(1f, avg / 3f);
                    base = new float[]{0.36f - 0.1f * t, 0.68f - 0.15f * t, 0.27f - 0.05f * t};
                } else base = c(0x8A8780);
                float chk = ((i + j) & 1) == 0 ? 1f : 0.93f;
                col[0] = base[0] * chk; col[1] = base[1] * chk; col[2] = base[2] * chk;
                int z = scene.zone[i * N + j];
                if (z > 0) {
                    float[] tint = z == 1 ? c(0xFF2A2A) : z == 2 ? c(0x2AE060) : z == 3 ? c(0xB040E8) : c(0xFFD21F);
                    for (int k = 0; k < 3; k++) col[k] = col[k] * 0.45f + tint[k] * 0.55f;
                }
                float x0 = i - half, x1 = x0 + 1, z0 = j - half, z1 = z0 + 1;
                m.cx = (x0 + x1) / 2; m.cy = -1000; m.cz = (z0 + z1) / 2;
                m.tri(x0, h00, z0, x1, h10, z0, x1, h11, z1, col);
                m.tri(x0, h00, z0, x1, h11, z1, x0, h01, z1, col);
            }
        terrainBuf = m.buf();
        terrainCount = m.n / 9;
    }

    private void drawMesh(FloatBuffer fb, int count, float[] model, float[] tint) {
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES20.glUniform3f(uTint, tint[0], tint[1], tint[2]);
        fb.position(0);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 36, fb);
        GLES20.glEnableVertexAttribArray(aPos);
        fb.position(3);
        GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, 36, fb);
        GLES20.glEnableVertexAttribArray(aNor);
        fb.position(6);
        GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, 36, fb);
        GLES20.glEnableVertexAttribArray(aCol);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
    }

    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.55f, 0.78f, 0.95f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        synchronized (this) {
            if (terrainDirty || terrainBuf == null) { buildTerrain(); terrainDirty = false; }
            float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
            float ty = scene.heightAt(tx, tz);
            Matrix.setLookAtM(view, 0,
                    tx + dist * cp * (float) Math.sin(yaw), ty + dist * sp, tz + dist * cp * (float) Math.cos(yaw),
                    tx, ty, tz, 0, 1, 0);
            Matrix.multiplyMM(vp, 0, proj, 0, view, 0);
            vpValid = true;
            GLES20.glUseProgram(prog);
            float[] none = {0, 0, 0};
            Matrix.setIdentityM(mdl, 0);
            drawMesh(waterBuf, waterCount, mdl, none);
            drawMesh(terrainBuf, terrainCount, mdl, none);
            for (SceneData.Obj o : scene.objs) {
                Matrix.setIdentityM(mdl, 0);
                Matrix.translateM(mdl, 0, o.x, scene.heightAt(o.x, o.z), o.z);
                Matrix.rotateM(mdl, 0, o.rot, 0, 1, 0);
                Matrix.scaleM(mdl, 0, o.scale, o.scale, o.scale);
                float[] tint = o.id == selected ? new float[]{0.35f, 0.3f, 0.05f} : none;
                drawMesh(protoBuf[o.type], protoCount[o.type], mdl, tint);
            }
        }
    }

    // ---------------- seçme (ray) ----------------
    private float[] unproj(float[] inv, float nx, float ny, float nz) {
        float[] in = {nx, ny, nz, 1}, out = new float[4];
        Matrix.multiplyMV(out, 0, inv, 0, in, 0);
        return new float[]{out[0] / out[3], out[1] / out[3], out[2] / out[3]};
    }

    private float[][] ray(float sx, float sy) {
        if (!vpValid) return null;
        float[] inv = new float[16];
        if (!Matrix.invertM(inv, 0, vp, 0)) return null;
        float nx = 2 * sx / vw - 1, ny = 1 - 2 * sy / vh;
        float[] a = unproj(inv, nx, ny, -1), b = unproj(inv, nx, ny, 1);
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new float[][]{a, {dx / l, dy / l, dz / l}, {l, 0, 0}};
    }

    private float[] pickTerrain(float sx, float sy) {
        synchronized (this) {
            float[][] r = ray(sx, sy);
            if (r == null) return null;
            float half = SceneData.N / 2f;
            for (float t = 0; t <= r[2][0]; t += 0.25f) {
                float x = r[0][0] + r[1][0] * t, y = r[0][1] + r[1][1] * t, z = r[0][2] + r[1][2] * t;
                if (Math.abs(x) >= half || Math.abs(z) >= half) continue;
                if (y <= scene.heightAt(x, z)) return new float[]{x, z};
            }
            return null;
        }
    }

    private int pickObject(float sx, float sy) {
        synchronized (this) {
            float[][] r = ray(sx, sy);
            if (r == null) return -1;
            int best = -1;
            float bt = 1e9f;
            for (SceneData.Obj o : scene.objs) {
                float cy = scene.heightAt(o.x, o.z) + PICK_H[o.type] * o.scale;
                float rad = PICK_R[o.type] * o.scale;
                float px = o.x - r[0][0], py = cy - r[0][1], pz = o.z - r[0][2];
                float t = px * r[1][0] + py * r[1][1] + pz * r[1][2];
                if (t < 0) continue;
                float d2 = px * px + py * py + pz * pz - t * t;
                if (d2 <= rad * rad && t < bt) { bt = t; best = o.id; }
            }
            return best;
        }
    }

    // ---------------- dokunma ----------------
    private static final int M_NONE = 0, M_ORBIT = 1, M_DRAG = 2, M_PAINT = 3, M_PINCH = 4, M_WAIT = 5;
    private int mode = M_NONE;
    private float lastX, lastY, downX, downY, pinchD, pinchDist, pcx, pcy;
    private boolean moved, changed;

    private float d2(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void paintAt(float sx, float sy) {
        float[] p = pickTerrain(sx, sy);
        if (p == null) return;
        int N = SceneData.N, half = N / 2;
        synchronized (this) {
            if (tool == T_ZONE) {
                for (int i = 0; i < N; i++)
                    for (int j = 0; j < N; j++) {
                        float dx = i + 0.5f - half - p[0], dz = j + 0.5f - half - p[1];
                        if (dx * dx + dz * dz <= 2.6f) scene.zone[i * N + j] = (byte) zoneType;
                    }
            } else {
                float sgn = tool == T_RAISE ? 1f : -1f;
                for (int i = 0; i <= N; i++)
                    for (int j = 0; j <= N; j++) {
                        float dx = i - half - p[0], dz = j - half - p[1];
                        float d = (float) Math.sqrt(dx * dx + dz * dz);
                        if (d < 3f) {
                            int k = i * (N + 1) + j;
                            scene.h[k] = Math.max(-2f, Math.min(8f, scene.h[k] + sgn * 0.12f * (1 - d / 3f)));
                        }
                    }
            }
            terrainDirty = true;
        }
        changed = true;
        requestRender();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2) {
            mode = M_PINCH;
            pinchD = Math.max(1f, d2(e));
            pinchDist = dist;
            pcx = (e.getX(0) + e.getX(1)) / 2; pcy = (e.getY(0) + e.getY(1)) / 2;
            return true;
        }
        if (mode == M_PINCH) {
            if (a == MotionEvent.ACTION_MOVE && e.getPointerCount() >= 2) {
                dist = Math.max(6f, Math.min(70f, pinchDist * pinchD / Math.max(1f, d2(e))));
                float cx = (e.getX(0) + e.getX(1)) / 2, cy = (e.getY(0) + e.getY(1)) / 2;
                float k = dist * 0.0016f, dx = cx - pcx, dy = cy - pcy;
                float sy = (float) Math.sin(yaw), cyw = (float) Math.cos(yaw);
                tx -= cyw * dx * k; tz += sy * dx * k;
                tx += -sy * dy * k; tz += -cyw * dy * k;
                tx = Math.max(-20, Math.min(20, tx)); tz = Math.max(-20, Math.min(20, tz));
                pcx = cx; pcy = cy;
                requestRender();
            } else if (a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                mode = a == MotionEvent.ACTION_POINTER_UP ? M_WAIT : M_NONE;
            }
            return true;
        }
        float x = e.getX(), y = e.getY();
        switch (a) {
            case MotionEvent.ACTION_DOWN: {
                downX = lastX = x; downY = lastY = y; moved = false; changed = false;
                if (tool == T_SELECT) {
                    int id = pickObject(x, y);
                    if (id >= 0) {
                        selected = id; mode = M_DRAG;
                        if (listener != null) listener.onSelect(scene.byId(id));
                        requestRender();
                    } else mode = M_ORBIT;
                } else if (tool == T_ZONE || tool == T_RAISE || tool == T_LOWER) {
                    mode = M_PAINT; paintAt(x, y);
                } else mode = M_ORBIT;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == M_WAIT || mode == M_NONE) return true;
                if (Math.abs(x - downX) > 12 || Math.abs(y - downY) > 12) moved = true;
                if (mode == M_PAINT) paintAt(x, y);
                else if (!moved) return true;
                else if (mode == M_DRAG) {
                    float[] p = pickTerrain(x, y);
                    SceneData.Obj o = scene.byId(selected);
                    if (p != null && o != null) {
                        synchronized (this) { o.x = p[0]; o.z = p[1]; }
                        changed = true;
                        requestRender();
                    }
                } else if (mode == M_ORBIT) {
                    yaw -= (x - lastX) * 0.006f;
                    pitch = Math.max(0.25f, Math.min(1.45f, pitch + (y - lastY) * 0.005f));
                    requestRender();
                }
                lastX = x; lastY = y;
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mode != M_WAIT && a == MotionEvent.ACTION_UP && !moved) {
                    if (tool == T_PLACE) {
                        float[] p = pickTerrain(x, y);
                        if (p != null) {
                            SceneData.Obj o;
                            synchronized (this) { o = scene.add(placeType, p[0], p[1]); o.rot = 0; selected = o.id; }
                            changed = true;
                            if (listener != null) listener.onSelect(o);
                            requestRender();
                        }
                    } else if (tool == T_SELECT && mode == M_ORBIT) {
                        selected = -1;
                        if (listener != null) listener.onSelect(null);
                        requestRender();
                    }
                }
                if (changed && listener != null) listener.onChanged();
                changed = false;
                mode = M_NONE;
                return true;
            }
        }
        return true;
    }

    // ---------------- dışarıdan çağrılan işlemler ----------------
    public SceneData.Obj selectedObj() { return scene.byId(selected); }

    public void rotateSelected(float deg) {
        SceneData.Obj o = selectedObj();
        if (o == null) return;
        synchronized (this) { o.rot = (o.rot + deg) % 360; }
        requestRender();
    }

    public void scaleSelected(float f) {
        SceneData.Obj o = selectedObj();
        if (o == null) return;
        synchronized (this) { o.scale = Math.max(0.3f, Math.min(4f, o.scale * f)); }
        requestRender();
    }

    public void deleteSelected() {
        synchronized (this) { scene.remove(selected); selected = -1; }
        requestRender();
    }
}
