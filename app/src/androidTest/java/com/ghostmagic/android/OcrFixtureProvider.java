package com.ghostmagic.android;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Test APK only. Exposes generated public fixtures, never arbitrary paths or app files. */
public final class OcrFixtureProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        String fixture=uri.getLastPathSegment();if(!"text".equals(fixture)&&!"malformed".equals(fixture)&&!"oversized".equals(fixture))throw new FileNotFoundException("Unknown generated fixture");
        File file=new File(getContext().getCacheDir(),"ocr-fixture-"+fixture);
        try(FileOutputStream output=new FileOutputStream(file)){
            if("text".equals(fixture)){
                Bitmap b=Bitmap.createBitmap(1200,400,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(90);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));canvas.drawText("GHOST MAGIC 123",50,180,paint);b.compress(Bitmap.CompressFormat.PNG,100,output);b.recycle();
            }else if("oversized".equals(fixture)){byte[] bytes=new byte[65536];for(int i=0;i<257;i++)output.write(bytes);}
            else output.write(new byte[]{1,2,3,4});
        }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){return "image/png";}
    @Override public Cursor query(Uri uri,String[] p,String s,String[] a,String sort){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
