package com.ghostmagic.android;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import com.ghostmagic.core.ActionException;
import com.ghostmagic.core.Cancellation;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Screenshot-only service. User enables it in Android settings; no event content is consumed. */
public final class OcrScreenshotService extends AccessibilityService {
    private static final Object CONNECTION=new Object();
    private static OcrScreenshotService connected;
    private static final long TIMEOUT_MS=5000;
    private static final long MAX_PIXELS=16000000;
    @Override protected void onServiceConnected(){super.onServiceConnected();synchronized(CONNECTION){connected=this;}}
    private void disconnect(){synchronized(CONNECTION){if(connected==this)connected=null;}}
    @Override public boolean onUnbind(Intent intent){disconnect();return super.onUnbind(intent);}
    @Override public void onDestroy(){disconnect();super.onDestroy();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    static boolean isConnected(){synchronized(CONNECTION){return connected!=null;}}
    static Bitmap capture(Cancellation cancellation)throws ActionException {
        if(Build.VERSION.SDK_INT<30)throw new ActionException(ActionException.UNSUPPORTED,"Screen OCR requires Android 11 or newer");
        return Api30.capture(cancellation);
    }
    /** Isolates Android 11 screenshot classes from service loading on Android 8 through 10. */
    @androidx.annotation.RequiresApi(30)
    private static final class Api30 {
        private static final Executor CALLBACKS=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"ghost-ocr-screenshot");thread.setDaemon(true);return thread;});
        static Bitmap capture(Cancellation cancellation)throws ActionException {
        if(Looper.myLooper()==Looper.getMainLooper())throw new ActionException(ActionException.EXECUTION_FAILED,"Screen OCR must run on a worker");
        OcrScreenshotService service;synchronized(CONNECTION){service=connected;}
        if(service==null)throw new ActionException(ActionException.PERMISSION_REQUIRED,"Enable Ghost Magic screen OCR in Android Accessibility settings");
        Slot slot=new Slot();
        try {
            checkCancellation(cancellation);
            service.takeScreenshot(Display.DEFAULT_DISPLAY,CALLBACKS,new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result){
                    // Every callback owns a HardwareBuffer, including callbacks arriving after timeout.
                    try(HardwareBuffer buffer=result.getHardwareBuffer()){
                        synchronized(slot){
                            if(slot.abandoned)return;
                            Bitmap wrapped=null,copy=null;
                            try{
                                if((long)buffer.getWidth()*buffer.getHeight()>MAX_PIXELS)throw new IllegalArgumentException("Screenshot exceeds 16 megapixels");
                                wrapped=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                                if(wrapped==null)throw new IllegalArgumentException("Unsupported screenshot buffer");
                                copy=wrapped.copy(Bitmap.Config.ARGB_8888,false);
                                if(copy==null)throw new IllegalArgumentException("Cannot copy screenshot");
                                slot.bitmap=copy;copy=null;
                            }catch(RuntimeException|OutOfMemoryError ex){slot.error=new ActionException(ActionException.EXECUTION_FAILED,"Cannot decode screenshot");}
                            finally{if(wrapped!=null)wrapped.recycle();if(copy!=null)copy.recycle();slot.done=true;slot.notifyAll();}
                        }
                    }
                }
                @Override public void onFailure(int code){synchronized(slot){if(slot.abandoned)return;slot.error=screenshotError(code);slot.done=true;slot.notifyAll();}}
            });
            long deadline=SystemClock.elapsedRealtime()+TIMEOUT_MS;
            synchronized(slot){
                for(;;){
                    checkCancellation(cancellation);
                    if(slot.done){if(slot.error!=null)throw slot.error;Bitmap result=slot.bitmap;slot.bitmap=null;return result;}
                    long remaining=deadline-SystemClock.elapsedRealtime();
                    if(remaining<=0)throw new ActionException("OCR_CAPTURE_TIMEOUT","Screen capture did not finish within five seconds");
                    try{slot.wait(Math.min(50,remaining));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ActionException("CANCELLED","Screen capture cancelled");}
                }
            }
        }catch(SecurityException ex){throw new ActionException(ActionException.PERMISSION_REQUIRED,"Screen capture access was revoked");}
        catch(RuntimeException ex){throw new ActionException(ActionException.EXECUTION_FAILED,"Android could not start screen capture");}
        finally{synchronized(slot){slot.abandoned=true;if(slot.bitmap!=null){slot.bitmap.recycle();slot.bitmap=null;}}}
    }
    private static void checkCancellation(Cancellation c)throws ActionException {if(c.isCancelled()||Thread.currentThread().isInterrupted())throw new ActionException("CANCELLED","Screen capture cancelled");}
    private static ActionException screenshotError(int code){
        switch(code){
            case ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS:return new ActionException(ActionException.PERMISSION_REQUIRED,"Screen capture Accessibility access is unavailable");
            case ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT:return new ActionException("OCR_CAPTURE_RATE_LIMITED","Android requires more time between screenshots");
            case ERROR_TAKE_SCREENSHOT_SECURE_WINDOW:return new ActionException("OCR_SECURE_SCREEN","Android blocked capture of secure content");
            case ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY:return new ActionException(ActionException.UNSUPPORTED,"The default display cannot be captured");
            default:return new ActionException("OCR_CAPTURE_FAILED","Android screen capture failed ("+code+")");
        }
    }
    private static final class Slot {boolean done,abandoned;Bitmap bitmap;ActionException error;}
    }
}
