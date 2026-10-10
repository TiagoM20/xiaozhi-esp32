package pt.lumistudio.lumihub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Icones vetoriais simples para a Home. Sem emojis ou fontes externas. */
public final class HomeIconView extends View {
    private final String kind;
    private final int tint;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HomeIconView(Context context, String kind, int tint) {
        super(context);
        this.kind=kind;
        this.tint=tint;
        setContentDescription(kind);
    }

    private void line(Canvas c,float x1,float y1,float x2,float y2) {
        c.drawLine(x1,y1,x2,y2,pen);
    }
    private void oval(Canvas c,float l,float t,float r,float b) {
        c.drawOval(l,t,r,b,pen);
    }
    private void box(Canvas c,float l,float t,float r,float b,float radius) {
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,pen);
    }
    private void poly(Canvas c,float... pts) {
        Path p=new Path();
        p.moveTo(pts[0],pts[1]);
        for(int i=2;i<pts.length;i+=2)p.lineTo(pts[i],pts[i+1]);
        c.drawPath(p,pen);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float w=getWidth(),h=getHeight();
        float s=Math.min(w,h)/100f;
        canvas.translate((w-100*s)/2f,(h-100*s)/2f);
        canvas.scale(s,s);
        pen.setColor(tint);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(5.5f);
        pen.setStrokeJoin(Paint.Join.ROUND);
        pen.setStrokeCap(Paint.Cap.ROUND);
        switch(kind) {
            case "lumi":
                poly(canvas,48,12,59,40,87,50,59,60,48,89,37,60,10,50,37,40,48,12);
                pen.setStrokeWidth(3);
                line(canvas,81,15,81,32);line(canvas,73,23,89,23);
                line(canvas,17,74,17,86);line(canvas,11,80,23,80);
                break;
            case "tv":
                box(canvas,13,19,87,73,7);
                line(canvas,38,80,62,80);line(canvas,50,73,50,80);
                break;
            case "camera":
                box(canvas,17,24,83,76,10);
                oval(canvas,37,34,63,60);
                pen.setStyle(Paint.Style.FILL);
                canvas.drawCircle(73,34,3,pen);
                pen.setStyle(Paint.Style.STROKE);
                break;
            case "vacuum":
                oval(canvas,20,20,80,80);
                oval(canvas,40,40,60,60);
                line(canvas,30,71,70,71);
                line(canvas,50,20,50,30);
                break;
            case "tablet":
                box(canvas,22,12,78,88,6);
                line(canvas,43,80,57,80);
                break;
            case "phone":
                box(canvas,31,9,69,91,7);
                line(canvas,43,20,57,20);
                line(canvas,46,81,54,81);
                break;
            case "music":
                line(canvas,42,24,42,72);
                line(canvas,73,15,73,65);
                poly(canvas,42,24,73,15,73,30,42,38);
                oval(canvas,24,64,43,80);
                oval(canvas,55,57,74,73);
                break;
            case "ac":
                box(canvas,14,24,86,59,7);
                line(canvas,23,44,77,44);
                line(canvas,30,69,26,81);line(canvas,50,69,47,81);
                line(canvas,70,69,67,81);
                break;
            case "plug":
                line(canvas,38,15,38,37);line(canvas,62,15,62,37);
                box(canvas,26,34,74,61,8);
                line(canvas,50,61,50,86);
                break;
            case "plus":
                line(canvas,50,16,50,84);line(canvas,16,50,84,50);
                break;
            default:
                oval(canvas,25,25,75,75);
                break;
        }
        canvas.restore();
    }
}
