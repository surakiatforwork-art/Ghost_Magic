package com.ghostmagic.android;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import com.ghostmagic.core.*;

final class GraphView extends View {
    interface Listener { void edit(Node node); void moved(); }
    private final FlowDocument document;
    private final Listener listener;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path line=new Path(),arrow=new Path();
    private final float density;
    private Node grabbed;
    private float downX,downY,originalX,originalY;
    private boolean dragged;
    private float zoom=1;
    static final float WIDTH=280, HEIGHT=108;
    GraphView(Context c,FlowDocument d,Listener listener) { super(c); document=d; this.listener=listener; density=getResources().getDisplayMetrics().density; setContentDescription("Flow graph. Tap a node to edit; drag to move. Swipe empty canvas to scroll."); }
    @Override protected void onMeasure(int w,int h) {
        float width=720,height=800;
        for(float[] pos:document.positions.values()) { width=Math.max(width,pos[0]+WIDTH+40); height=Math.max(height,pos[1]+HEIGHT+80); }
        setMeasuredDimension((int)(width*density*zoom),(int)(height*density*zoom));
    }
    void zoom(float factor){zoom=Math.max(.25f,Math.min(2,factor));requestLayout();invalidate();}
    void fit(int availableWidth){float width=360;for(float[] pos:document.positions.values())width=Math.max(width,pos[0]+WIDTH+40);zoom(availableWidth/(width*density));}
    private int color(String type) { return Color.parseColor(type.startsWith("trigger")?"#E9B66F":type.startsWith("condition")?"#B7A0F6":"#70E4C5"); }
    private void label(Canvas c,String s,float x,float y,int size,int color,float width) {
        text.setColor(color); text.setTextSize(size);
        c.drawText(TextUtils.ellipsize(s,text,width,TextUtils.TruncateAt.END).toString(),x,y,text);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save(); canvas.scale(density*zoom,density*zoom);
        float w=getWidth()/(density*zoom),h=getHeight()/(density*zoom);
        p.setColor(Color.parseColor("#263140"));
        for(float x=16;x<w;x+=24) for(float y=16;y<h;y+=24) canvas.drawCircle(x,y,1,p);
        for(Edge e:document.edges) {
            float[] a=document.positions.get(e.from()), b=document.positions.get(e.to()); if(a==null||b==null) continue;
            float x1=a[0]+WIDTH/2,y1=a[1]+HEIGHT,x2=b[0]+WIDTH/2,y2=b[1];
            int col=Color.parseColor(e.port().equals("error")?"#F58E95":e.port().equals("false")?"#B7A0F6":"#70E4C5");
            p.setColor(col); p.setStrokeWidth(2); p.setStyle(Paint.Style.STROKE);
            line.reset(); line.moveTo(x1,y1); line.cubicTo(x1,y1+48,x2,y2-48,x2,y2); canvas.drawPath(line,p);
            p.setStyle(Paint.Style.FILL); arrow.reset(); arrow.moveTo(x2,y2); arrow.lineTo(x2-5,y2-8); arrow.lineTo(x2+5,y2-8); arrow.close(); canvas.drawPath(arrow,p);
            label(canvas,e.port().toUpperCase(java.util.Locale.ROOT),(x1+x2)/2+8,(y1+y2)/2,10,col,120);
        }
        for(int i=0;i<document.nodes.size();i++) {
            Node n=document.nodes.get(i); float[] pos=document.positions.get(n.id()); if(pos==null)continue;
            float x=pos[0],y=pos[1]; int accent=color(n.type());
            p.setColor(Color.parseColor("#202A38")); canvas.drawRoundRect(x,y,x+WIDTH,y+HEIGHT,14,14,p);
            p.setColor(accent); canvas.drawRoundRect(x,y,x+5,y+HEIGHT,3,3,p);
            label(canvas,String.format(java.util.Locale.ROOT,"%02d  %s",i+1,n.type().split("\\.")[0].toUpperCase(java.util.Locale.ROOT)),x+18,y+24,10,accent,WIDTH-35);
            label(canvas,Catalog.title(n.type()),x+18,y+49,17,Color.WHITE,WIDTH-35);
            String summary=n.config().isEmpty()?"Tap to configure routes":n.config().toString();
            label(canvas,summary,x+18,y+74,11,Color.parseColor("#A7B5C8"),WIDTH-35);
            label(canvas,n.type().startsWith("condition")?"TRUE  /  FALSE  /  ERROR":"NEXT  /  ERROR",x+18,y+96,9,accent,WIDTH-35);
        }
        canvas.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX()/(density*zoom),y=e.getY()/(density*zoom);
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=x;downY=y;dragged=false;grabbed=null;
                for(int i=document.nodes.size()-1;i>=0;i--) { Node n=document.nodes.get(i);float[] pos=document.positions.get(n.id()); if(pos!=null&&x>=pos[0]&&x<=pos[0]+WIDTH&&y>=pos[1]&&y<=pos[1]+HEIGHT) { grabbed=n;originalX=pos[0];originalY=pos[1];break; } }
                if(grabbed!=null) getParent().requestDisallowInterceptTouchEvent(true);
                return grabbed!=null;
            case MotionEvent.ACTION_MOVE:
                if(grabbed==null)return false;
                if(!dragged&&Math.abs(x-downX)+Math.abs(y-downY)>8){dragged=true;listener.moved();}
                if(dragged) { document.positions.put(grabbed.id(),new float[]{Math.max(0,Math.min(10000,originalX+x-downX)),Math.max(0,Math.min(30000,originalY+y-downY))});invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                if(grabbed!=null) { if(dragged) { requestLayout();listener.moved(); } else { performClick();listener.edit(grabbed); } grabbed=null;getParent().requestDisallowInterceptTouchEvent(false);return true; } return false;
            case MotionEvent.ACTION_CANCEL: grabbed=null;getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick() { super.performClick();return true; }
}
