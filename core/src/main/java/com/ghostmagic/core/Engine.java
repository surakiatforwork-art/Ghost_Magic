package com.ghostmagic.core;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.ghostmagic.core.RunResult.Status.*;

/** Synchronous interpreter; callers run this on a worker, never the Android main thread. */
public final class Engine {
    private final int maxSteps;
    private final long timeoutMillis;
    public Engine() { this(1000,120000); }
    public Engine(int maxSteps,long timeoutMillis) {
        if(maxSteps<1 || timeoutMillis<1) throw new IllegalArgumentException("Positive budgets required");
        this.maxSteps=maxSteps; this.timeoutMillis=timeoutMillis;
    }
    private static final Set<String> TYPES=Set.of("trigger.manual","trigger.periodic","condition.variable.equals","condition.battery.above","action.variable.set","action.log","action.delay","action.notification","action.brightness","action.wifi.panel","action.ocr.read");
    private static final Pattern VARIABLE=Pattern.compile("\\$\\{([^{}]+)\\}");
    public List<String> validate(Flow flow) {
        List<String> errors=new ArrayList<>(); Map<String,Node> nodes=new HashMap<>(); int triggers=0;
        for(Node n:flow.nodes()) {
            if(n.id().trim().isEmpty()) errors.add("Node id cannot be blank");
            if(nodes.put(n.id(),n)!=null) errors.add("Duplicate node: "+n.id());
            if(n.type().startsWith("trigger.")) triggers++;
            if(!TYPES.contains(n.type())) errors.add("Unknown node type: "+n.type());
            try { validateConfig(n,true); } catch(ActionException ex) { errors.add(n.id()+": "+ex.getMessage()); }
        }
        Node start=nodes.get(flow.startId());
        if(start==null || !start.type().startsWith("trigger.")) errors.add("Start must be a trigger");
        if(triggers!=1) errors.add("Exactly one trigger required");
        Set<String> ports=new HashSet<>();
        for(Edge edge:flow.edges()) {
            Node from=nodes.get(edge.from()); Node to=nodes.get(edge.to());
            if(from==null || to==null) { errors.add("Dangling edge: "+edge); continue; }
            if(to.type().startsWith("trigger.")) errors.add("Edges cannot enter a trigger");
            Set<String> allowed=from.type().startsWith("condition.")?Set.of("true","false","error"):Set.of("next","error");
            if(!allowed.contains(edge.port())) errors.add("Invalid port on "+edge.from()+": "+edge.port());
            if(!ports.add(edge.from()+"\u0000"+edge.port())) errors.add("Duplicate output port: "+edge.from()+"/"+edge.port());
        }
        return List.copyOf(errors);
    }
    public RunResult run(Flow flow, ActionHost host, Cancellation cancellation) {
        Objects.requireNonNull(host); Objects.requireNonNull(cancellation);
        List<RunResult.TraceEntry> trace=new ArrayList<>(); Map<String,String> vars=new LinkedHashMap<>();
        List<String> errors=validate(flow);
        if(!errors.isEmpty()) return result(FAILED,trace,vars,"INVALID_GRAPH",String.join("; ",errors));
        Map<String,Node> nodes=new HashMap<>(); Map<String,Map<String,String>> routes=new HashMap<>();
        for(Node n:flow.nodes()) nodes.put(n.id(),n);
        for(Edge e:flow.edges()) routes.computeIfAbsent(e.from(),k->new HashMap<>()).put(e.port(),e.to());
        long started=System.nanoTime(); String current=flow.startId(); int steps=0;
        while(current!=null) {
            if(cancelled(cancellation)) return result(CANCELLED,trace,vars,"CANCELLED","Run cancelled");
            if(expired(started)) return result(TIMED_OUT,trace,vars,"TIMEOUT","Run time budget exceeded");
            if(steps++>=maxSteps) return result(STEP_LIMIT,trace,vars,"STEP_LIMIT","Run step budget exceeded");
            Node node=nodes.get(current); String port="next"; String message="OK";
            try {
                Map<String,String> expanded=new LinkedHashMap<>();
                for(Map.Entry<String,String> entry:node.config().entrySet()) expanded.put(entry.getKey(),expand(entry.getValue(),vars));
                node=new Node(node.id(),node.type(),expanded);
                validateConfig(node,false);
                switch(node.type()) {
                    case "trigger.manual": case "trigger.periodic": break;
                    case "condition.variable.equals": port=Objects.equals(vars.get(required(node,"name")),required(node,"value"))?"true":"false"; break;
                    case "condition.battery.above": port=host.batteryPercent()>number(node,"threshold",0,100)?"true":"false"; break;
                    case "action.variable.set": vars.put(required(node,"name"),required(node,"value")); break;
                    case "action.ocr.read":
                        OcrConfig ocr=OcrConfig.parse(node.config());
                        String recognized=host.readText(node.config(),cancellation);
                        if(cancelled(cancellation)) return result(CANCELLED,trace,vars,"CANCELLED","Cancelled during OCR");
                        if(expired(started)) return result(TIMED_OUT,trace,vars,"TIMEOUT","Timed out during OCR");
                        if(recognized==null) throw new ActionException(ActionException.EXECUTION_FAILED,"OCR returned no result");
                        vars.put(ocr.outputVariable(),recognized);
                        message="Recognized "+recognized.length()+" characters";
                        break;
                    case "action.log": message=required(node,"message"); host.execute(node.type(),node.config(),Collections.unmodifiableMap(vars)); break;
                    case "action.delay":
                        long ms=number(node,"milliseconds",0,60000); long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);
                        while(System.nanoTime()<until) {
                            if(cancelled(cancellation)) return result(CANCELLED,trace,vars,"CANCELLED","Cancelled during delay");
                            if(expired(started)) return result(TIMED_OUT,trace,vars,"TIMEOUT","Timed out during delay");
                            try { Thread.sleep(Math.max(1,Math.min(25,TimeUnit.NANOSECONDS.toMillis(until-System.nanoTime())))); }
                            catch(InterruptedException ex) { Thread.currentThread().interrupt(); return result(CANCELLED,trace,vars,"CANCELLED","Worker interrupted"); }
                        }
                        break;
                    default: host.execute(node.type(),node.config(),Collections.unmodifiableMap(vars));
                        if(node.type().equals("action.wifi.panel"))message="User controls opened; Wi-Fi state not changed or verified";
                }
            } catch(ActionException ex) {
                // A host may report an error while a stop or deadline becomes observable.
                // The run budget wins, so an error route cannot start more side effects.
                if(cancelled(cancellation)) return result(CANCELLED,trace,vars,"CANCELLED","Run cancelled during action");
                if(expired(started)) return result(TIMED_OUT,trace,vars,"TIMEOUT","Run time budget exceeded during action");
                port="error"; message=ex.code()+": "+ex.getMessage();
                vars.put("error.code",ex.code()); vars.put("error.message",Objects.toString(ex.getMessage(),""));
                trace.add(new RunResult.TraceEntry(node.id(),node.type(),port,message));
                String recovery=routes.getOrDefault(current,Map.of()).get("error");
                if(recovery==null) return result(FAILED,trace,vars,ex.code(),ex.getMessage());
                current=recovery; continue;
            } catch(RuntimeException ex) {
                trace.add(new RunResult.TraceEntry(node.id(),node.type(),"error",ex.toString()));
                return result(FAILED,trace,vars,"EXECUTION_FAILED",ex.toString());
            }
            trace.add(new RunResult.TraceEntry(node.id(),node.type(),port,message));
            if(cancelled(cancellation)) return result(CANCELLED,trace,vars,"CANCELLED","Run cancelled");
            if(expired(started)) return result(TIMED_OUT,trace,vars,"TIMEOUT","Run time budget exceeded");
            current=routes.getOrDefault(current,Map.of()).get(port);
        }
        return result(COMPLETED,trace,vars,null,"Completed");
    }
    private boolean expired(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)>=timeoutMillis; }
    private static String expand(String value,Map<String,String> variables) throws ActionException {
        Matcher matcher=VARIABLE.matcher(value); StringBuffer output=new StringBuffer();
        while(matcher.find()) {
            String name=matcher.group(1);
            if(!variables.containsKey(name)) throw new ActionException(ActionException.INVALID_CONFIG,"Undefined variable: "+name);
            matcher.appendReplacement(output,Matcher.quoteReplacement(variables.get(name)));
        }
        matcher.appendTail(output); return output.toString();
    }
    private static void validateConfig(Node n,boolean allowTemplate) throws ActionException {
        switch(n.type()) {
            case "action.ocr.read": OcrConfig.validate(n.config(),allowTemplate); break;
            case "trigger.periodic": number(n,"intervalMinutes",15,10080); break;
            case "condition.variable.equals": case "action.variable.set": required(n,"name"); required(n,"value"); break;
            case "action.log": required(n,"message"); break;
            case "action.notification": required(n,"title"); required(n,"message"); break;
            case "condition.battery.above": configNumber(n,"threshold",0,100,allowTemplate); break;
            case "action.brightness": configNumber(n,"percent",0,100,allowTemplate); break;
            case "action.delay": configNumber(n,"milliseconds",0,60000,allowTemplate); break;
            default: break;
        }
    }
    private static void configNumber(Node n,String key,int min,int max,boolean allowTemplate) throws ActionException {
        String value=required(n,key);
        if(allowTemplate && VARIABLE.matcher(value).find()) return;
        number(n,key,min,max);
    }
    private static boolean cancelled(Cancellation c) { return c.isCancelled() || Thread.currentThread().isInterrupted(); }
    private static String required(Node n,String key) throws ActionException {
        String value=n.config().get(key);
        if(value==null || (key.equals("name") && value.trim().isEmpty())) throw new ActionException("INVALID_CONFIG","Missing "+key+" on "+n.id());
        return value;
    }
    private static int number(Node n,String key,int min,int max) throws ActionException {
        try { int value=Integer.parseInt(required(n,key)); if(value<min || value>max) throw new NumberFormatException(); return value; }
        catch(NumberFormatException e) { throw new ActionException("INVALID_CONFIG",key+" must be between "+min+" and "+max); }
    }
    private static RunResult result(RunResult.Status s,List<RunResult.TraceEntry> t,Map<String,String> v,String code,String message) { return new RunResult(s,t,v,code,message); }
}
