package com.ghostmagic.android;

import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.os.SystemClock;
import com.ghostmagic.core.Node;

/** Exercises real Android touch dispatch, independent of the engine routing tests. */
public final class GraphGestureIntegrationTest extends InstrumentationTestCase {
    private FlowDocument doc;private GraphView view;private float density;private Node source,target;
    private int edits,adds,links,moves;private Node linked;private float addX,addY;
    private void setupGraph(){
        doc=new FlowDocument();source=doc.add("trigger.manual");target=doc.add("action.log");
        doc.positions.put(source.id(),new float[]{60,50});doc.positions.put(target.id(),new float[]{360,350});
        density=getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density;
        view=new GraphView(getInstrumentation().getTargetContext(),doc,new GraphView.Listener(){
            public void edit(Node n){edits++;}public void moved(){moves++;}
            public void connect(Node a,Node b){assertEquals(source,a);links++;linked=b;}
            public void add(Node a,float x,float y){assertEquals(source,a);adds++;addX=x;addY=y;}
        });view.measure(View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(3000,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());
    }
    private void touch(int action,float x,float y,float zoom){MotionEvent e=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,x*density*zoom,y*density*zoom,0);try{view.dispatchTouchEvent(e);}finally{e.recycle();}}
    private void tap(float x,float y){touch(MotionEvent.ACTION_DOWN,x,y,1);touch(MotionEvent.ACTION_UP,x,y,1);}
    public void testNodeTapSelectsAndLeftButtonEdits()throws Throwable{runTestOnUiThread(()->{setupGraph();tap(180,104);assertEquals(0,edits);tap(30,104);assertEquals(1,edits);assertEquals(0,adds);});}
    public void testPlusDragTargetsExistingNodeWithoutMovingIt()throws Throwable{runTestOnUiThread(()->{setupGraph();tap(180,104);touch(0,180,188,1);touch(2,480,404,1);touch(1,480,404,1);assertEquals(1,links);assertEquals(target,linked);assertEquals(0,adds);assertEquals(360f,doc.positions.get(target.id())[0]);assertEquals(60f,doc.positions.get(source.id())[0]);});}
    public void testPlusDropOnCanvasRequestsPositionedNode()throws Throwable{runTestOnUiThread(()->{setupGraph();tap(180,104);touch(0,180,188,1);touch(2,500,650,1);touch(1,500,650,1);assertEquals(1,adds);assertEquals(380f,addX);assertEquals(650f,addY);assertEquals(0,links);});}
    public void testCancelledConnectorDoesNotCreateOrConnect()throws Throwable{runTestOnUiThread(()->{setupGraph();tap(180,104);touch(0,180,188,1);touch(2,480,404,1);touch(3,480,404,1);touch(1,480,404,1);assertEquals(0,links);assertEquals(0,adds);assertEquals(0,moves);});}
    public void testDraggingNodeStillMovesWithoutOpeningEditor()throws Throwable{runTestOnUiThread(()->{setupGraph();touch(0,180,104,1);touch(2,240,164,1);touch(1,240,164,1);assertEquals(120f,doc.positions.get(source.id())[0]);assertEquals(110f,doc.positions.get(source.id())[1]);assertTrue(moves>0);assertEquals(0,edits);assertEquals(0,links);});}
    public void testConnectorUsesGraphCoordinatesAfterZoom()throws Throwable{runTestOnUiThread(()->{setupGraph();view.zoom(.5f);touch(0,180,104,.5f);touch(1,180,104,.5f);touch(0,180,188,.5f);touch(2,480,404,.5f);touch(1,480,404,.5f);assertEquals(1,links);assertEquals(target,linked);});}
    public void testSelectedNodeTapAtMinimumZoomDoesNotHitPlus()throws Throwable{runTestOnUiThread(()->{setupGraph();view.zoom(.25f);touch(0,180,104,.25f);touch(1,180,104,.25f);touch(0,180,104,.25f);touch(1,180,104,.25f);assertEquals(0,adds);assertEquals(0,edits);});}
}
