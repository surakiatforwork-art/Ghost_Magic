package com.ghostmagic.core;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static com.ghostmagic.core.RunResult.Status.*;

public class EngineTest {
    private Node n(String id,String type,String... config) {
        Map<String,String> values=new HashMap<>(); for(int i=0;i<config.length;i+=2) values.put(config[i],config[i+1]);
        return new Node(id,type,values);
    }
    private Flow flow(List<Node> nodes,Edge... edges) { return new Flow("test","Test","start",nodes,List.of(edges)); }
    private Node start() { return n("start","trigger.manual"); }
    private static class Host implements ActionHost {
        List<String> calls=new ArrayList<>();
        public void execute(String type,Map<String,String> config,Map<String,String> vars) throws ActionException { calls.add(config.getOrDefault("message",type)); }
        public int batteryPercent() { return 75; }
    }
    @Test public void variableAndBatteryConditionsSelectOnlyTrueBranch() {
        Flow flow=flow(List.of(start(),n("set","action.variable.set","name","mode","value","home"),n("check","condition.variable.equals","name","mode","value","home"),n("battery","condition.battery.above","threshold","50"),n("good","action.log","message","yes"),n("bad","action.log","message","no")),new Edge("start","next","set"),new Edge("set","next","check"),new Edge("check","true","battery"),new Edge("check","false","bad"),new Edge("battery","true","good"),new Edge("battery","false","bad"));
        Host host=new Host(); RunResult result=new Engine().run(flow,host,Cancellation.NEVER);
        assertEquals(COMPLETED,result.status()); assertEquals(List.of("yes"),host.calls); assertEquals("home",result.variables().get("mode")); assertEquals(5,result.trace().size());
    }
    @Test public void unsupportedPlatformActionRoutesToErrorAndRetainsTypedFailure() {
        Flow flow=flow(List.of(start(),n("wifi","action.wifi.panel"),n("recover","action.log","message","Use Settings")),new Edge("start","next","wifi"),new Edge("wifi","error","recover"));
        Host host=new Host() { public void execute(String type,Map<String,String> config,Map<String,String> vars) throws ActionException {
            if(type.equals("action.wifi.panel")) throw new ActionException(ActionException.UNSUPPORTED,"No settings panel"); super.execute(type,config,vars);
        }};
        RunResult result=new Engine().run(flow,host,Cancellation.NEVER);
        assertEquals(COMPLETED,result.status()); assertEquals("UNSUPPORTED",result.variables().get("error.code")); assertEquals("error",result.trace().get(1).port()); assertEquals(List.of("Use Settings"),host.calls);
    }
    @Test public void unhandledPermissionFailureStopsRun() {
        Flow flow=flow(List.of(start(),n("brightness","action.brightness","percent","50")),new Edge("start","next","brightness"));
        Host host=new Host() { public void execute(String t,Map<String,String> c,Map<String,String> v) throws ActionException { throw new ActionException("PERMISSION_REQUIRED","Grant write settings"); }};
        RunResult result=new Engine().run(flow,host,Cancellation.NEVER);
        assertEquals(FAILED,result.status()); assertEquals("PERMISSION_REQUIRED",result.errorCode());
    }
    @Test public void cycleStopsAtStepBudget() {
        Flow flow=flow(List.of(start(),n("loop","action.log","message","loop")),new Edge("start","next","loop"),new Edge("loop","next","loop"));
        RunResult result=new Engine(4,1000).run(flow,new Host(),Cancellation.NEVER);
        assertEquals(STEP_LIMIT,result.status()); assertEquals(4,result.trace().size());
    }
    @Test public void cancellationPreventsFollowingAction() {
        AtomicBoolean cancel=new AtomicBoolean();
        Flow flow=flow(List.of(start(),n("one","action.log","message","one"),n("two","action.notification","title","Title","message","Body")),new Edge("start","next","one"),new Edge("one","next","two"));
        Host host=new Host() { public void execute(String t,Map<String,String> c,Map<String,String> v) { calls.add(t); cancel.set(true); }};
        assertEquals(CANCELLED,new Engine().run(flow,host,cancel::get).status()); assertEquals(List.of("action.log"),host.calls);
    }
    @Test public void delayEnforcesRunTimeout() {
        Flow flow=flow(List.of(start(),n("wait","action.delay","milliseconds","60000")),new Edge("start","next","wait"));
        long begin=System.nanoTime(); RunResult result=new Engine(100,30).run(flow,new Host(),Cancellation.NEVER);
        assertEquals(TIMED_OUT,result.status()); assertTrue((System.nanoTime()-begin)/1000000<1000);
    }
    @Test public void malformedGraphIsRejectedBeforeEffects() {
        Flow flow=flow(List.of(start(),n("one","action.log","message","one")),new Edge("start","true","one"),new Edge("one","next","absent"));
        Host host=new Host(); RunResult result=new Engine().run(flow,host,Cancellation.NEVER);
        assertEquals(FAILED,result.status()); assertEquals("INVALID_GRAPH",result.errorCode()); assertTrue(host.calls.isEmpty()); assertEquals(2,new Engine().validate(flow).size());
    }
    @Test public void duplicatePortsAndIdsAreRejected() {
        Flow flow=flow(List.of(start(),n("one","action.log","message","one"),n("one","action.log","message","one")),new Edge("start","next","one"),new Edge("start","next","one"));
        assertEquals(2,new Engine().validate(flow).size());
    }
    @Test public void invalidDelayRoutesConfigurationFailureWithoutSleeping() {
        Flow flow=flow(List.of(start(),n("wait","action.delay","milliseconds","60001")),new Edge("start","next","wait"));
        RunResult result=new Engine().run(flow,new Host(),Cancellation.NEVER); assertEquals(FAILED,result.status()); assertEquals("INVALID_GRAPH",result.errorCode());
    }
    @Test public void absentVariableSelectsFalseBranch() {
        Flow flow=flow(List.of(start(),n("check","condition.variable.equals","name","unset","value","yes"),n("false","action.log","message","false branch"),n("true","action.log","message","true branch")),new Edge("start","next","check"),new Edge("check","false","false"),new Edge("check","true","true"));
        Host host=new Host(); assertEquals(COMPLETED,new Engine().run(flow,host,Cancellation.NEVER).status()); assertEquals(List.of("false branch"),host.calls);
    }
    @Test public void cancellationIsObservedInsideDelay() {
        Flow flow=flow(List.of(start(),n("wait","action.delay","milliseconds","60000")),new Edge("start","next","wait"));
        int[] polls={0}; long begin=System.nanoTime();
        RunResult result=new Engine().run(flow,new Host(),()->++polls[0]>=5);
        assertEquals(CANCELLED,result.status()); assertTrue((System.nanoTime()-begin)/1000000<1000);
    }
    @Test public void templatesExpandAcrossAssignmentConditionsAndPlatformAction() {
        Flow flow=flow(List.of(start(),n("name","action.variable.set","name","name","value","Ghost"),n("label","action.variable.set","name","label","value","Hello ${name}!"),n("check","condition.variable.equals","name","label","value","Hello ${name}!"),n("log","action.log","message","${label}")),new Edge("start","next","name"),new Edge("name","next","label"),new Edge("label","next","check"),new Edge("check","true","log"));
        Host host=new Host(); RunResult result=new Engine().run(flow,host,Cancellation.NEVER);
        assertEquals(COMPLETED,result.status()); assertEquals(List.of("Hello Ghost!"),host.calls); assertEquals("Hello Ghost!",result.trace().get(4).message());
    }
    @Test public void templateReplacementPreservesLiteralRegexCharacters() {
        Flow flow=flow(List.of(start(),n("set","action.variable.set","name","value","value","$5\\path{}"),n("log","action.log","message","(${value}) ${value}")),new Edge("start","next","set"),new Edge("set","next","log"));
        Host host=new Host(); RunResult result=new Engine().run(flow,host,Cancellation.NEVER);
        assertEquals(COMPLETED,result.status()); assertEquals(List.of("($5\\path{}) $5\\path{}"),host.calls);
    }
    @Test public void missingTemplateVariableRoutesTypedError() {
        Flow flow=flow(List.of(start(),n("log","action.log","message","Hello ${missing}"),n("recover","action.log","message","${error.code}")),new Edge("start","next","log"),new Edge("log","error","recover"));
        Host host=new Host(); RunResult result=new Engine().run(flow,host,Cancellation.NEVER);
        assertEquals(COMPLETED,result.status()); assertEquals(List.of("INVALID_CONFIG"),host.calls); assertEquals("error",result.trace().get(1).port());
    }
    @Test public void numericTemplatesAreValidatedAtRuntime() {
        Flow flow=flow(List.of(start(),n("set","action.variable.set","name","ms","value","60001"),n("delay","action.delay","milliseconds","${ms}")),new Edge("start","next","set"),new Edge("set","next","delay"));
        assertTrue(new Engine().validate(flow).isEmpty());
        RunResult result=new Engine().run(flow,new Host(),Cancellation.NEVER);
        assertEquals(FAILED,result.status()); assertEquals("INVALID_CONFIG",result.errorCode());
    }
    @Test public void missingStaticConfigurationRejectedBeforeAnyEffect() {
        Flow flow=flow(List.of(start(),n("good","action.log","message","side effect"),n("bad","action.notification","title","Missing body")),new Edge("start","next","good"),new Edge("good","next","bad"));
        Host host=new Host(); assertEquals("INVALID_GRAPH",new Engine().run(flow,host,Cancellation.NEVER).errorCode()); assertTrue(host.calls.isEmpty());
    }
    @Test public void periodicEntryUsesSameGraphSemantics() {
        Flow flow=new Flow("periodic","Periodic","timer",List.of(n("timer","trigger.periodic","intervalMinutes","15"),n("log","action.log","message","Tick")),List.of(new Edge("timer","next","log")));
        Host host=new Host();assertTrue(new Engine().validate(flow).isEmpty());assertEquals(COMPLETED,new Engine().run(flow,host,Cancellation.NEVER).status());assertEquals(List.of("Tick"),host.calls);
    }
    @Test public void subMinimumOrTemplatedPeriodicIntervalRejected() {
        for(String interval:List.of("14","10081","${minutes}")){
            Flow flow=new Flow("periodic","Periodic","timer",List.of(n("timer","trigger.periodic","intervalMinutes",interval)),List.of());
            assertFalse(new Engine().validate(flow).isEmpty());
        }
    }
    @Test public void mixedTriggersAreRejected() {
        Flow flow=flow(List.of(start(),n("timer","trigger.periodic","intervalMinutes","15")));
        assertTrue(new Engine().validate(flow).contains("Exactly one trigger required"));
    }
}

