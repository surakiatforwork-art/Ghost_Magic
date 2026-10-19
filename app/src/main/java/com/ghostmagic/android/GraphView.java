package com.ghostmagic.android;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import com.ghostmagic.core.*;

final class GraphView extends View {
    interface Listener { void edit(Node node); void moved(); void connect(Node source,Node target); void add(Node source,float x,float y); }
    private final FlowDocument document;
    private final Listener listener;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path line=new Path(),arrow=new Path();
    private final float density;
    private Node grabbed,selected,hover;
    private int handle; // 1: edit, 2: connector
    private float pointerX,pointerY;
    private float downX,downY,originalX,originalY;
    private boolean dragged;
    private float zoom=1;
    static final float WIDTH=240, HEIGHT=108;
    GraphView(Context c,FlowDocument d,Listener listener) { super(c); document=d; this.listener=listener; density=getResources().getDisplayMetrics().density; setContentDescription("Flow graph. Tap a node to select. Edit button on left; drag plus below to connect or add. Drag the node to move. Nodes menu also provides settings."); }
    @Override protected void onMeasure(int w,int h) {
        float width=720,height=800;
        for(float[] pos:document.positions.values()) { width=Math.max(width,pos[0]+WIDTH+40); height=Math.max(height,pos[1]+HEIGHT+120); }
        setMeasuredDimension((int)(width*density*zoom),(int)(height*density*zoom));
    }
    void zoom(float factor){zoom=Math.max(.25f,Math.min(2,factor));requestLayout();invalidate();}
    void fit(int availableWidth){float width=360;for(float[] pos:document.positions.values())width=Math.max(width,pos[0]+WIDTH+40);zoom(availableWidth/(width*density));}
    private void label(Canvas c,String s,float x,float y,int size,int color,float width){
        text.setColor(color);text.setTextSize(size);text.setTextAlign(Paint.Align.CENTER);
        c.drawText(TextUtils.ellipsize(s,text,width,TextUtils.TruncateAt.END).toString(),x,y,text);
    }
    private Path outline(Node n,float x,float y){
        Path shape=new Path();
        if(n.type().startsWith("condition")){shape.moveTo(x+WIDTH/2,y);shape.lineTo(x+WIDTH,y+HEIGHT/2);shape.lineTo(x+WIDTH/2,y+HEIGHT);shape.lineTo(x,y+HEIGHT/2);shape.close();}
        else if(n.type().startsWith("trigger"))shape.addRoundRect(x,y,x+WIDTH,y+HEIGHT,18,18,Path.Direction.CW);
        else shape.addRect(x,y,x+WIDTH,y+HEIGHT,Path.Direction.CW);
        return shape;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();canvas.scale(density*zoom,density*zoom);
        float w=getWidth()/(density*zoom),h=getHeight()/(density*zoom);
        canvas.drawColor(0xff1e1e1e);p.setStyle(Paint.Style.FILL);p.setColor(0xff505050);
        for(float x=16;x<w;x+=24)for(float y=16;y<h;y+=24)canvas.drawCircle(x,y,.6f,p);
        for(Edge e:document.edges){
            float[] a=document.positions.get(e.from()),b=document.positions.get(e.to());if(a==null||b==null)continue;
            float x1=a[0]+WIDTH/2,y1=a[1]+HEIGHT,x2=b[0]+WIDTH/2,y2=b[1];
            p.setColor(e.port().equals("error")?0xffd18484:0xff82a6b4);p.setStrokeWidth(1.5f);p.setStyle(Paint.Style.STROKE);
            line.reset();line.moveTo(x1,y1);line.cubicTo(x1,y1+32,x2,y2-32,x2,y2-9);canvas.drawPath(line,p);
            p.setStyle(Paint.Style.FILL);arrow.reset();arrow.moveTo(x2,y2);arrow.lineTo(x2-7,y2-12);arrow.lineTo(x2+7,y2-12);arrow.close();canvas.drawPath(arrow,p);
            if(!e.port().equals("next"))label(canvas,e.port().substring(0,1).toUpperCase(java.util.Locale.ROOT)+e.port().substring(1),(x1+x2)/2+22,(y1+y2)/2,12,Color.WHITE,80);
        }
        for(Node n:document.nodes){
            float[] pos=document.positions.get(n.id());if(pos==null)continue;float x=pos[0],y=pos[1];Path shape=outline(n,x,y);
            p.setStyle(Paint.Style.FILL);p.setColor(0xff133e4c);canvas.drawPath(shape,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(0xff31b6e7);canvas.drawPath(shape,p);p.setStyle(Paint.Style.FILL);
            boolean condition=n.type().startsWith("condition");float available=condition?WIDTH*.65f:WIDTH-24;
            label(canvas,Catalog.title(n.type()),x+WIDTH/2,y+HEIGHT/2-2,15,Color.WHITE,available);
            String summary=n.config().isEmpty()?"":n.config().toString();label(canvas,summary,x+WIDTH/2,y+HEIGHT/2+18,11,0xffbed1d7,available);
        }
        if(selected!=null){
            float[] a=document.positions.get(selected.id());
            if(a!=null){
                p.setStyle(Paint.Style.STROKE);p.setColor(Color.WHITE);p.setStrokeWidth(2);canvas.drawPath(outline(selected,a[0],a[1]),p);p.setStyle(Paint.Style.FILL);
                control(canvas,editX(a),a[1]+HEIGHT/2,"✎");if(handle!=2||!dragged)control(canvas,a[0]+WIDTH/2,a[1]+HEIGHT+30,"+");
                if(handle==2){
                    p.setColor(0xff31b6e7);p.setStrokeWidth(2);canvas.drawLine(a[0]+WIDTH/2,a[1]+HEIGHT,pointerX,pointerY,p);control(canvas,pointerX,pointerY,"+");
                    if(hover!=null&&!hover.type().startsWith("trigger")&&!hover.id().equals(selected.id())){float[] b=document.positions.get(hover.id());p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.WHITE);canvas.drawPath(outline(hover,b[0],b[1]),p);p.setStyle(Paint.Style.FILL);}
                }
            }
        }
        canvas.restore();
    }
    private float editX(float[] a){return Math.max(24,a[0]-30);}
    private void control(Canvas c,float x,float y,String glyph){p.setStyle(Paint.Style.FILL);p.setColor(0xff22718d);c.drawCircle(x,y,20,p);p.setStyle(Paint.Style.STROKE);p.setColor(0xff31b6e7);p.setStrokeWidth(1.5f);c.drawCircle(x,y,20,p);p.setStyle(Paint.Style.FILL);label(c,glyph,x,y+8,26,Color.WHITE,36);}
    private boolean near(float x,float y,float cx,float cy){float r=Math.min(28,Math.max(24,24/zoom));return (x-cx)*(x-cx)+(y-cy)*(y-cy)<=r*r;}
    private Node hit(float x,float y){
        for(int i=document.nodes.size()-1;i>=0;i--){Node n=document.nodes.get(i);float[] a=document.positions.get(n.id());if(a==null)continue;
            float dx=Math.abs(x-a[0]-WIDTH/2),dy=Math.abs(y-a[1]-HEIGHT/2);
            if(n.type().startsWith("condition")?dx/(WIDTH/2)+dy/(HEIGHT/2)<=1:dx<=WIDTH/2&&dy<=HEIGHT/2)return n;
        }return null;
    }
    private void release(){grabbed=null;handle=0;hover=null;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){
        float x=e.getX()/(density*zoom),y=e.getY()/(density*zoom);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=x;downY=y;pointerX=x;pointerY=y;dragged=false;grabbed=null;handle=0;
                if(selected!=null){float[] a=document.positions.get(selected.id());if(a!=null){if(near(x,y,editX(a),a[1]+HEIGHT/2))handle=1;else if(near(x,y,a[0]+WIDTH/2,a[1]+HEIGHT+30))handle=2;}}
                if(handle==0){grabbed=hit(x,y);if(grabbed!=null){float[] a=document.positions.get(grabbed.id());originalX=a[0];originalY=a[1];}else{selected=null;invalidate();return false;}}
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(handle!=0){pointerX=x;pointerY=y;if(Math.hypot(x-downX,y-downY)*density*zoom>ViewConfiguration.get(getContext()).getScaledTouchSlop())dragged=true;hover=hit(x,y);invalidate();return true;}
                if(grabbed==null)return false;
                if(!dragged&&Math.hypot(x-downX,y-downY)*density*zoom>ViewConfiguration.get(getContext()).getScaledTouchSlop()){dragged=true;listener.moved();}
                if(dragged){selected=grabbed;document.positions.put(grabbed.id(),new float[]{Math.max(42,Math.min(10000,originalX+x-downX)),Math.max(0,Math.min(30000,originalY+y-downY))});invalidate();}return true;
            case MotionEvent.ACTION_UP:
                if(handle!=0){int action=handle;Node source=selected;Node target=hit(x,y);boolean wasDragged=dragged;release();performClick();
                    if(action==1){float[] a=document.positions.get(source.id());if(!wasDragged&&near(x,y,editX(a),a[1]+HEIGHT/2))listener.edit(source);}
                    else if(wasDragged&&target!=null)listener.connect(source,target);
                    else if(!wasDragged||target==null)listener.add(source,Math.max(42,x-WIDTH/2),Math.max(0,y));
                    return true;
                }
                if(grabbed!=null){selected=grabbed;if(dragged){requestLayout();listener.moved();}else performClick();release();return true;}return false;
            case MotionEvent.ACTION_CANCEL:release();return true;
        }return super.onTouchEvent(e);
    }
    @Override public boolean performClick() { super.performClick();return true; }
}
