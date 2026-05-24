package com.keymonkmodern.dualswipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class DualSwipeKeyboardView extends View {
    public interface KeyboardListener {
        void onText(String text);
        void onBackspace();
    }

    private KeyboardListener listener;
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Key> keys = new ArrayList<>();
    private final Map<Integer, Stroke> active = new HashMap<>();
    private final List<Stroke> completed = new ArrayList<>();
    private float lastW = -1, lastH = -1;
    private static final float TAP_SLOP = 24f;

    public DualSwipeKeyboardView(Context context) { super(context); init(); }
    public DualSwipeKeyboardView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    public void setKeyboardListener(KeyboardListener listener) { this.listener = listener; }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        bgPaint.setColor(Color.rgb(32, 34, 38));
        keyPaint.setColor(Color.rgb(238, 238, 238));
        textPaint.setColor(Color.BLACK);
        textPaint.setTextAlign(Paint.Align.CENTER);
        tracePaint.setColor(Color.rgb(40, 120, 255));
        tracePaint.setStrokeWidth(8f);
        tracePaint.setStyle(Paint.Style.STROKE);
        tracePaint.setStrokeCap(Paint.Cap.ROUND);
        tracePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int)(getResources().getDisplayMetrics().density * 290);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (lastW != getWidth() || lastH != getHeight()) buildKeys(getWidth(), getHeight());
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        textPaint.setTextSize(sp(22));
        for (Key k : keys) {
            keyPaint.setColor(k.special ? Color.rgb(205, 210, 218) : Color.rgb(245, 245, 245));
            canvas.drawRoundRect(k.rect, 18, 18, keyPaint);
            canvas.drawText(k.label, k.rect.centerX(), k.rect.centerY() + (textPaint.getTextSize() * 0.35f), textPaint);
        }
        for (Stroke s : active.values()) drawStroke(canvas, s);
    }

    private void drawStroke(Canvas canvas, Stroke s) {
        if (s.points.size() < 2) return;
        Path p = new Path();
        p.moveTo(s.points.get(0).x, s.points.get(0).y);
        for (int i = 1; i < s.points.size(); i++) p.lineTo(s.points.get(i).x, s.points.get(i).y);
        canvas.drawPath(p, tracePaint);
    }

    private void buildKeys(float w, float h) {
        lastW = w; lastH = h; keys.clear();
        float gap = dp(5);
        float top = dp(8);
        float rowH = (h - top - dp(8) - 3 * gap) / 4f;
        addRow("qwertyuiop", 0, top, w, rowH, gap, 0f);
        addRow("asdfghjkl", 1, top + rowH + gap, w, rowH, gap, 0.5f);
        addRow("zxcvbnm", 2, top + 2*(rowH + gap), w, rowH, gap, 1.0f);
        float y = top + 3*(rowH + gap);
        keys.add(new Key("⌫", "BACKSPACE", new RectF(w - rowH - gap, y, w - gap, y + rowH), true));
        keys.add(new Key("space", " ", new RectF(gap, y, w - rowH - 2*gap, y + rowH), true));
    }

    private void addRow(String chars, int row, float y, float w, float rowH, float gap, float indentKeys) {
        int n = chars.length();
        float keyW = (w - gap * (n + 1 + indentKeys)) / (n + indentKeys);
        float x = gap + indentKeys * keyW;
        for (int i = 0; i < n; i++) {
            String c = String.valueOf(chars.charAt(i));
            keys.add(new Key(c, c, new RectF(x, y, x + keyW, y + rowH), false));
            x += keyW + gap;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int index = e.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = e.getPointerId(index);
            Stroke s = new Stroke(id, e.getX(index), e.getY(index));
            s.add(e.getX(index), e.getY(index), keyAt(e.getX(index), e.getY(index)));
            active.put(id, s);
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i);
                Stroke s = active.get(id);
                if (s != null) s.add(e.getX(i), e.getY(i), keyAt(e.getX(i), e.getY(i)));
            }
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            int id = e.getPointerId(index);
            Stroke s = active.remove(id);
            if (s != null) {
                s.add(e.getX(index), e.getY(index), keyAt(e.getX(index), e.getY(index)));
                completed.add(s);
            }
            if (active.isEmpty()) finishGestureBatch();
            invalidate();
            return true;
        }
        return true;
    }

    private void finishGestureBatch() {
        if (completed.isEmpty()) return;
        if (completed.size() == 1) {
            Stroke s = completed.get(0);
            Key start = keyAt(s.startX, s.startY);
            if (s.distance() < TAP_SLOP && start != null) {
                if ("BACKSPACE".equals(start.value)) {
                    if (listener != null) listener.onBackspace();
                } else if (listener != null) {
                    listener.onText(start.value);
                }
            } else {
                String path = s.collapsedLetters();
                if (!path.isEmpty() && listener != null) listener.onText("[" + path + "]");
            }
        } else {
            Stroke left = completed.get(0), right = completed.get(1);
            for (Stroke s : completed) {
                if (s.startX < left.startX) left = s;
                if (s.startX > right.startX) right = s;
            }
            String l = left.collapsedLetters();
            String r = right.collapsedLetters();
            if (listener != null) listener.onText("[L:" + l + " | R:" + r + "]");
        }
        completed.clear();
    }

    private Key keyAt(float x, float y) {
        for (Key k : keys) if (k.rect.contains(x, y)) return k;
        return null;
    }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

    private static class Key {
        final String label, value; final RectF rect; final boolean special;
        Key(String label, String value, RectF rect, boolean special) { this.label = label; this.value = value; this.rect = rect; this.special = special; }
    }
    private static class Pt { final float x,y; Pt(float x,float y){this.x=x;this.y=y;} }
    private static class Stroke {
        final int id; final float startX, startY; final List<Pt> points = new ArrayList<>(); final List<String> letters = new ArrayList<>();
        Stroke(int id, float x, float y) { this.id=id; startX=x; startY=y; }
        void add(float x, float y, Key k) { points.add(new Pt(x,y)); if (k != null && !k.special) letters.add(k.value); }
        float distance() { if (points.isEmpty()) return 0; Pt p=points.get(points.size()-1); float dx=p.x-startX, dy=p.y-startY; return (float)Math.sqrt(dx*dx+dy*dy); }
        String collapsedLetters() { LinkedHashSet<String> seen = new LinkedHashSet<>(); String last = null; for (String s: letters) { if (!s.equals(last)) seen.add(s); last=s; } StringBuilder b=new StringBuilder(); for(String s: seen) b.append(s); return b.toString(); }
    }
}
