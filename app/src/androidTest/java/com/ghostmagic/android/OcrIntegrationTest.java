package com.ghostmagic.android;

import android.graphics.*;
import android.test.AndroidTestCase;
import com.ghostmagic.core.*;
import java.util.*;

/** Real bundled recognizer tests using generated images, never reference-app content. */
public final class OcrIntegrationTest extends AndroidTestCase {
    private Map<String,String> config(){Map<String,String> c=new HashMap<>();c.put("source","image");c.put("imageUri","content://generated/test");c.put("outputVariable","ocr_text");c.put("script","latin");c.put("region","full");c.put("rotation","0");return c;}
    private Bitmap image(boolean text){Bitmap b=Bitmap.createBitmap(1200,600,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);if(text){Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(90);canvas.drawText("GHOST MAGIC 123",60,180,paint);canvas.drawText("BOTTOM LINE",60,480,paint);}return b;}
    public void testReadsGeneratedLatinText()throws Exception{String text=new OcrReader(getContext()).readBitmap(image(true),config(),()->false);assertTrue(text,text.contains("GHOST MAGIC 123"));assertTrue(text,text.contains("BOTTOM LINE"));}
    public void testNormalizedCropExcludesBottomText()throws Exception{Map<String,String> c=config();c.put("region","0,0,100,50");String text=new OcrReader(getContext()).readBitmap(image(true),c,()->false);assertTrue(text,text.contains("GHOST MAGIC 123"));assertFalse(text,text.contains("BOTTOM LINE"));}
    public void testRotationIsAppliedBeforeRecognition()throws Exception{Bitmap upright=image(true);Matrix matrix=new Matrix();matrix.postRotate(-90);Bitmap sideways=Bitmap.createBitmap(upright,0,0,upright.getWidth(),upright.getHeight(),matrix,true);upright.recycle();Map<String,String> c=config();c.put("rotation","90");String text=new OcrReader(getContext()).readBitmap(sideways,c,()->false);assertTrue(text,text.contains("GHOST MAGIC 123"));}
    public void testBlankImageReturnsEmptySuccess()throws Exception{assertEquals("",new OcrReader(getContext()).readBitmap(image(false),config(),()->false));}
    public void testCancelledReadReleasesInput()throws Exception{Bitmap b=image(true);try{new OcrReader(getContext()).readBitmap(b,config(),()->true);fail("Expected cancellation");}catch(ActionException expected){assertTrue(expected.getMessage().contains("cancelled"));}assertTrue(b.isRecycled());}
    public void testUnavailableContentIsRoutableFailure()throws Exception{Map<String,String> c=config();c.put("imageUri","content://com.ghostmagic.nonexistent/image");try{new OcrReader(getContext()).read(c,()->false);fail("Expected unavailable provider");}catch(ActionException expected){assertEquals(ActionException.EXECUTION_FAILED,expected.code());}}
    public void testContentProviderDecodeAndRecognition()throws Exception{Map<String,String> c=config();c.put("imageUri","content://com.ghostmagic.android.test.ocrfixtures/text");String text=new OcrReader(getContext()).read(c,()->false);assertTrue(text,text.contains("GHOST MAGIC 123"));}
    public void testMalformedProviderImageIsRejected()throws Exception{assertProviderFailure("malformed","supported image");}
    public void testOversizedProviderImageIsRejected()throws Exception{assertProviderFailure("oversized","16 MiB");}
    public void testEngineAndAndroidAdapterStoreRecognizedText()throws Exception{
        Map<String,String> c=config();c.put("imageUri","content://com.ghostmagic.android.test.ocrfixtures/text");
        Flow flow=new Flow("ocr-test","Generated image OCR","start",List.of(new Node("start","trigger.manual",Map.of()),new Node("read","action.ocr.read",c),new Node("log","action.log",Map.of("message","${ocr_text}"))),List.of(new Edge("start","next","read"),new Edge("read","next","log")));
        RunResult result=new Engine().run(flow,new AndroidActionHost(getContext()),()->false);
        assertEquals(result.message(),RunResult.Status.COMPLETED,result.status());assertTrue(result.variables().get("ocr_text").contains("GHOST MAGIC 123"));
    }
    private void assertProviderFailure(String fixture,String message)throws Exception{Map<String,String> c=config();c.put("imageUri","content://com.ghostmagic.android.test.ocrfixtures/"+fixture);try{new OcrReader(getContext()).read(c,()->false);fail("Expected bounded decoder failure");}catch(ActionException expected){assertEquals(ActionException.EXECUTION_FAILED,expected.code());assertTrue(expected.getMessage(),expected.getMessage().contains(message));}}
}
