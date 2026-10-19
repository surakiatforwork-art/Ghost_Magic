package com.ghostmagic.android;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import com.ghostmagic.core.*;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import java.util.Map;

/** Independent Latin OCR adapter. No code, models or resources from a reference APK. */
final class OcrReader {
    private static final int MAX_BYTES=16*1024*1024, MAX_SIDE=2048, MAX_TEXT=100000;
    private final Context context;
    OcrReader(Context context){this.context=context.getApplicationContext();}

    String read(Map<String,String> config,Cancellation cancellation)throws ActionException{
        OcrConfig options=OcrConfig.parse(config);
        check(cancellation);
        if(Looper.myLooper()==Looper.getMainLooper())throw failure("OCR must run on a worker thread");
        Bitmap bitmap=options.source().equals("screen")?OcrScreenshotService.capture(cancellation):decode(options.imageUri(),cancellation);
        return readBitmap(bitmap,config,cancellation);
    }

    private Bitmap decode(String location,Cancellation cancellation)throws ActionException{
        if(!"content".equals(Uri.parse(location).getScheme()))throw new ActionException(ActionException.INVALID_CONFIG,"Image source must be a content:// URI selected through Android");
        try(InputStream input=context.getContentResolver().openInputStream(Uri.parse(location));ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            if(input==null)throw failure("Image provider returned no data");
            byte[] buffer=new byte[16384];int count;
            while((count=input.read(buffer))!=-1){check(cancellation);if(bytes.size()+count>MAX_BYTES)throw failure("Image exceeds 16 MiB");bytes.write(buffer,0,count);}
            byte[] data=bytes.toByteArray();BitmapFactory.Options probe=new BitmapFactory.Options();probe.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(data,0,data.length,probe);
            if(probe.outWidth<=0||probe.outHeight<=0)throw failure("The selected content is not a supported image");
            if((long)probe.outWidth*probe.outHeight>64000000L)throw failure("Image exceeds 64 megapixels");
            BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=1;decode.inPreferredConfig=Bitmap.Config.ARGB_8888;
            while((Math.max(probe.outWidth,probe.outHeight)+decode.inSampleSize-1)/Math.max(1,decode.inSampleSize)>MAX_SIDE)decode.inSampleSize=Math.max(1,decode.inSampleSize)*2;
            check(cancellation);Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,decode);
            if(bitmap==null)throw failure("Image could not be decoded");return bitmap;
        }catch(SecurityException e){throw new ActionException(ActionException.PERMISSION_REQUIRED,"Image permission is missing or expired; select the image again");}
        catch(IOException e){throw failure("Cannot read selected image: "+e.getClass().getSimpleName());}
        catch(OutOfMemoryError e){throw failure("Not enough memory to decode the image");}
    }

    /** Consumes ownership of input, including on failure. Order: bound size, rotate, crop, recognize. */
    String readBitmap(Bitmap input,Map<String,String> config,Cancellation cancellation)throws ActionException{
        Bitmap prepared=input;TextRecognizer recognizer=null;Task<Text> task=null;
        try{
            OcrConfig options=OcrConfig.parse(config);check(cancellation);
            if(Looper.myLooper()==Looper.getMainLooper())throw failure("OCR must run on a worker thread");
            if(prepared==null||prepared.isRecycled())throw failure("No image available for OCR");
            int side=Math.max(prepared.getWidth(),prepared.getHeight());
            if(side>MAX_SIDE){float scale=(float)MAX_SIDE/side;Bitmap next=Bitmap.createScaledBitmap(prepared,Math.max(1,Math.round(prepared.getWidth()*scale)),Math.max(1,Math.round(prepared.getHeight()*scale)),true);if(next!=prepared){prepared.recycle();prepared=next;}}
            if(options.rotation()!=0){Matrix matrix=new Matrix();matrix.postRotate(options.rotation());Bitmap next=Bitmap.createBitmap(prepared,0,0,prepared.getWidth(),prepared.getHeight(),matrix,true);if(next!=prepared){prepared.recycle();prepared=next;}}
            OcrConfig.Region region=options.region();int width=prepared.getWidth(),height=prepared.getHeight();
            int left=width*region.x()/100,top=height*region.y()/100;
            int right=Math.min(width,(int)Math.ceil(width*(region.x()+region.width())/100.0));
            int bottom=Math.min(height,(int)Math.ceil(height*(region.y()+region.height())/100.0));
            Bitmap cropped=Bitmap.createBitmap(prepared,left,top,Math.max(1,right-left),Math.max(1,bottom-top));if(cropped!=prepared){prepared.recycle();prepared=cropped;}
            check(cancellation);recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            task=recognizer.process(InputImage.fromBitmap(prepared,0));
            // Completion owns cleanup even if cancellation/timeout returns before the native recognizer finishes.
            Bitmap owned=prepared;TextRecognizer ownedRecognizer=recognizer;
            task.addOnCompleteListener(Runnable::run,ignored->{ownedRecognizer.close();owned.recycle();});
            prepared=null;recognizer=null;
            long deadline=SystemClock.elapsedRealtime()+30000;
            while(!task.isComplete()){
                check(cancellation);if(SystemClock.elapsedRealtime()>=deadline)throw failure("OCR exceeded the 30 second limit");
                try{Thread.sleep(25);}catch(InterruptedException e){Thread.currentThread().interrupt();throw failure("OCR cancelled");}
            }
            check(cancellation);
            if(!task.isSuccessful())throw failure("Text recognition failed"+(task.getException()==null?"":": "+task.getException().getClass().getSimpleName()));
            String result=task.getResult().getText();if(result.length()>MAX_TEXT)throw failure("OCR result exceeds 100000 characters");return result;
        }catch(OutOfMemoryError e){throw failure("Not enough memory to prepare OCR image");}
        catch(IllegalArgumentException e){throw failure("Cannot prepare image for OCR");}
        catch(IllegalStateException e){throw failure("OCR recognizer is unavailable");}
        finally{if(task==null){if(recognizer!=null)recognizer.close();if(prepared!=null&&!prepared.isRecycled())prepared.recycle();}}
    }
    private static void check(Cancellation cancellation)throws ActionException{if(cancellation.isCancelled()||Thread.currentThread().isInterrupted())throw failure("OCR cancelled");}
    private static ActionException failure(String message){return new ActionException(ActionException.EXECUTION_FAILED,message);}
}
