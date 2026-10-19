package com.ghostmagic.core;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.ghostmagic.core.RunResult.Status.*;

public class OcrEngineTest {
    private Map<String,String> config(){return new LinkedHashMap<>(Map.of("source","image","imageUri","content://test/image","outputVariable","text","script","latin","region","full","rotation","0"));}
    private Flow flow(Map<String,String> config,boolean recovery){
        return new Flow("ocr","OCR","start",List.of(new Node("start","trigger.manual",Map.of()),new Node("set","action.variable.set",Map.of("name","text","value","previous")),new Node("ocr","action.ocr.read",config),new Node("log","action.log",Map.of("message","${text}"))),List.of(new Edge("start","next","set"),new Edge("set","next","ocr"),new Edge("ocr",recovery?"error":"next","log")));
    }
    private static class Host implements ActionHost {
        String text="Hello world",logged;
        public void execute(String type,Map<String,String> config,Map<String,String> vars){logged=config.get("message");}
        public int batteryPercent(){return 50;}
        public String readText(Map<String,String> config,Cancellation cancellation)throws ActionException{return text;}
    }
    @Test public void recognizedTextFeedsTemplatesButOcrTraceContainsOnlyCount(){
        Host host=new Host();RunResult r=new Engine().run(flow(config(),false),host,Cancellation.NEVER);
        assertEquals(COMPLETED,r.status());assertEquals("Hello world",host.logged);assertEquals("Recognized 11 characters",r.trace().get(2).message());
    }
    @Test public void emptyRecognitionIsSuccessful(){Host h=new Host();h.text="";RunResult r=new Engine().run(flow(config(),false),h,Cancellation.NEVER);assertEquals(COMPLETED,r.status());assertEquals("",r.variables().get("text"));}
    @Test public void failureRoutesAndDoesNotOverwriteOutput(){Host h=new Host(){public String readText(Map<String,String> c,Cancellation x)throws ActionException{throw new ActionException("PERMISSION_REQUIRED","Read access missing");}};RunResult r=new Engine().run(flow(config(),true),h,Cancellation.NEVER);assertEquals(COMPLETED,r.status());assertEquals("previous",h.logged);assertEquals("PERMISSION_REQUIRED",r.variables().get("error.code"));}
    @Test public void cancellationDuringReadPreventsAssignmentAndNextAction(){AtomicBoolean stop=new AtomicBoolean();Host h=new Host(){public String readText(Map<String,String> c,Cancellation x){stop.set(true);return "discard";}};RunResult r=new Engine().run(flow(config(),false),h,stop::get);assertEquals(CANCELLED,r.status());assertEquals("previous",r.variables().get("text"));assertNull(h.logged);}
    @Test public void nullRecognitionIsTypedFailure(){Host h=new Host();h.text=null;RunResult r=new Engine().run(flow(config(),false),h,Cancellation.NEVER);assertEquals(FAILED,r.status());assertEquals("EXECUTION_FAILED",r.errorCode());assertEquals("previous",r.variables().get("text"));}
    @Test public void templatesAreValidatedAfterExpansion(){Map<String,String> c=config();c.put("outputVariable","${text}");c.put("region","${text}");RunResult r=new Engine().run(flow(c,true),new Host(),Cancellation.NEVER);assertEquals(COMPLETED,r.status());assertEquals("INVALID_CONFIG",r.variables().get("error.code"));}
    @Test public void validTemplateProducesNamedOutput(){Map<String,String> c=config();c.put("outputVariable","${text}");RunResult r=new Engine().run(flow(c,false),new Host(),Cancellation.NEVER);assertEquals(COMPLETED,r.status());assertEquals("Hello world",r.variables().get("previous"));}
    @Test public void invalidConfigurationsAreRejectedBeforeCallingHost()throws Exception {
        for(String[] invalid:new String[][]{{"source","camera"},{"imageUri"," "},{"outputVariable","error.code"},{"outputVariable","error.message"},{"outputVariable","3bad"},{"script","thai"},{"region","90,0,20,10"},{"region","0,0,0,10"},{"region","0,0,2147483647,10"},{"region","0,0,100"},{"rotation","360"}}){Map<String,String> c=config();c.put(invalid[0],invalid[1]);assertFalse(Arrays.toString(invalid),new Engine().validate(flow(c,false)).isEmpty());}
        Map<String,String> c=config();c.put("region","25, 10, 75, 90");assertEquals(new OcrConfig.Region(25,10,75,90),OcrConfig.parse(c).region());c.put("source","screen");c.remove("imageUri");assertEquals("screen",OcrConfig.parse(c).source());
    }
    @Test public void oldHostsFailWithRoutableUnsupported(){ActionHost h=new ActionHost(){public void execute(String t,Map<String,String> c,Map<String,String> v){}public int batteryPercent(){return 0;}};RunResult r=new Engine().run(flow(config(),true),h,Cancellation.NEVER);assertEquals(COMPLETED,r.status());assertEquals("UNSUPPORTED",r.variables().get("error.code"));}
    @Test public void throwingCancelledHostNeverRunsRecoveryOrOverwritesOutput(){
        AtomicBoolean stop=new AtomicBoolean();
        Host h=new Host(){public String readText(Map<String,String> c,Cancellation x)throws ActionException{stop.set(true);throw new ActionException("CANCELLED","Adapter stopped");}};
        for(boolean recovery:new boolean[]{false,true}){
            stop.set(false);RunResult r=new Engine().run(flow(config(),recovery),h,stop::get);
            assertEquals(CANCELLED,r.status());assertEquals("previous",r.variables().get("text"));assertFalse(r.variables().containsKey("error.code"));assertNull(h.logged);
        }
    }
    @Test public void throwingExpiredHostNeverRunsRecovery(){
        Host h=new Host(){public String readText(Map<String,String> c,Cancellation x)throws ActionException{
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(30);
            while(System.nanoTime()<until)Thread.yield();
            throw new ActionException("OCR_TIMEOUT","Adapter expired");
        }};
        RunResult r=new Engine(100,10).run(flow(config(),true),h,Cancellation.NEVER);
        assertEquals(TIMED_OUT,r.status());assertEquals("TIMEOUT",r.errorCode());assertEquals("previous",r.variables().get("text"));assertFalse(r.variables().containsKey("error.code"));assertNull(h.logged);
    }
}
