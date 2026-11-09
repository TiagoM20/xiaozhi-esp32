package pt.lumistudio.lumihub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * LUMI HUB V16 — vista local de expressões (sem ligação à DESK).
 * Desenho nativo Android, sem íris/pupila ou downloads externos.
 * O firmware da Waveshare mantém-se inalterado.
 */
public final class LumiFacePreviewView extends View {
    public static final String[] MOODS = {
        "Repouso", "A ouvir", "A pensar", "A falar", "Feliz",
        "Surpresa", "Confusa", "Sono", "Carinhosa", "Animada"
    };
    public static final int[] PALETTES = {
        Color.rgb(110, 235, 238), Color.rgb(240, 169, 216),
        Color.rgb(174, 154, 255), Color.rgb(255, 189, 118),
        Color.rgb(117, 235, 170), Color.rgb(232, 244, 255),
        Color.rgb(255, 121, 136)
    };
    public static final String[] PALETTE_NAMES = {
        "Ciano", "Rosa", "Violeta", "Dourado", "Verde", "Gelo", "Coral"
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int mood = 0;
    private int palette = 0;
    private boolean animated = true;

    public LumiFacePreviewView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Pré-visualização local do rosto da LUMI, não sincronizada com a DESK");
    }
    public void setMood(int value) {
        mood = Math.max(0, Math.min(value, MOODS.length - 1));
        invalidate();
    }
    public void setPalette(int value) {
        palette = Math.max(0, Math.min(value, PALETTES.length - 1));
        invalidate();
    }
    public void setAnimated(boolean value) {
        animated = value;
        invalidate();
    }
    private void circle(Canvas c, float x, float y, float r, int color, float alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha((int)(255 * alpha));
        paint.setShadowLayer(r * .40f, 0, 0, color);
        c.drawCircle(x, y, r, paint);
        paint.clearShadowLayer();
        paint.setAlpha(255);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        float side = Math.min(getWidth(), getHeight());
        float dx = (getWidth()-side)/2f, dy=(getHeight()-side)/2f;
        canvas.translate(dx, dy);
        canvas.scale(side/240f, side/240f);
        canvas.drawColor(Color.rgb(13, 22, 39));

        float t = SystemClock.uptimeMillis() / 1000f;
        float drift = animated ? (float)Math.sin(t * .64f)*4f : 0f;
        float breath = animated ? (float)Math.sin(t * 1.7f)*1.6f : 0f;
        float blinkPhase = t % 5.9f;
        float blink = animated && blinkPhase > 5.70f
            ? Math.max(.08f, Math.abs(blinkPhase - 5.80f) * 10f) : 1f;
        if(mood==7)blink=.16f;
        if(mood==1)blink=1f;
        int neon = PALETTES[palette];

        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(25, Color.red(neon), Color.green(neon), Color.blue(neon)));
        paint.setStrokeWidth(1.5f);
        canvas.drawCircle(120, 120, 107, paint);

        float spread=(mood==5?43:44);
        float left=120-spread+drift, right=120+spread+drift;
        float y=105+breath;
        float radius=(mood==5?21:mood==7?18:23);
        if(mood==2){y+=7;radius=18;}
        if(mood==6){left-=3;right+=3;}
        // Olhos: círculos luminosos simples, SEM qualquer íris ou pupila.
        eye(canvas,left,y,radius,blink,neon);
        eye(canvas,right,y,radius,blink,neon);

        paint.setShadowLayer(6,0,0,neon);
        paint.setColor(neon);
        paint.setStrokeWidth(6);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        if(mood==4 || mood==8 || mood==9){
            arc.set(82,137,158,181);canvas.drawArc(arc,8,164,false,paint);
        } else if(mood==5){
            paint.setStyle(Paint.Style.FILL);canvas.drawOval(108,151,132,175,paint);
        } else if(mood==6){
            arc.set(101,154,140,173);canvas.drawArc(arc,190,158,false,paint);
        } else if(mood==7){
            arc.set(102,148,138,163);canvas.drawArc(arc,195,147,false,paint);
        } else if(mood==3){
            float open=animated ? 6f + 8f*(.5f+.5f*(float)Math.sin(t*13f)) : 7f;
            paint.setStyle(Paint.Style.FILL);canvas.drawOval(110,158-open*.65f,130,158+open,paint);
        } else if(mood==1){
            arc.set(104,150,137,168);canvas.drawArc(arc,16,147,false,paint);
        } else {
            arc.set(104,150,136,165);canvas.drawArc(arc,13,142,false,paint);
        }
        paint.clearShadowLayer();
        canvas.restoreToCount(save);
        if(animated && isAttachedToWindow() && getVisibility()==View.VISIBLE)postInvalidateDelayed(45);
    }
    private void eye(Canvas canvas,float x,float y,float r,float blink,int color){
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setShadowLayer(r*.60f,0,0,color);
        canvas.drawOval(x-r, y-r*blink, x+r, y+r*blink, paint);
        paint.clearShadowLayer();
    }
}
