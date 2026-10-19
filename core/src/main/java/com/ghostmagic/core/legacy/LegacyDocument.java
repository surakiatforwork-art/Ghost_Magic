package com.ghostmagic.core.legacy;

import java.util.*;

/** Read-only observations. This model cannot be passed to the execution engine. */
public record LegacyDocument(String version,List<Definition> definitions,List<LegacyFlow> flows,List<Issue> issues,int elementCount,Element root) {
    public LegacyDocument{definitions=List.copyOf(definitions);flows=List.copyOf(flows);issues=List.copyOf(issues);}
    public enum Severity{ERROR,WARNING,INFO}
    public record Issue(Severity severity,String code,int line,String message){}
    public record Element(String name,Map<String,String> attributes,String text,List<Element> children,int line){
        public Element{attributes=Map.copyOf(attributes);children=List.copyOf(children);}
        public List<Element> named(String name){List<Element> found=new ArrayList<>();for(Element e:children)if(e.name.equals(name))found.add(e);return List.copyOf(found);}
        public String value(String name){List<Element> values=named(name);return values.isEmpty()?"":values.get(0).text.trim();}
    }
    public record Definition(String kind,String type,String name,Element element){}
    public record Container(String id,String kind,List<String> references,Element element){public Container{references=List.copyOf(references);}}
    public record Connection(String from,String to,String type,Element element){}
    public record LegacyFlow(String name,String policy,String originallyEnabled,List<Container> containers,List<Connection> connections,Element element){
        public LegacyFlow{containers=List.copyOf(containers);connections=List.copyOf(connections);}
    }
    public List<Definition> resolve(String kind,String name){List<Definition> found=new ArrayList<>();for(Definition d:definitions)if(d.kind.equals(kind)&&d.name.equals(name))found.add(d);return List.copyOf(found);}
    public long count(Severity severity){return issues.stream().filter(i->i.severity==severity).count();}
    public static String analogue(String kind,String type){
        if(kind.equals("trigger")&&type.equals("periodic_timer_inexact"))return "Periodic trigger: timing and configuration require manual review";
        if(kind.equals("condition")&&type.equals("battery_level"))return "Battery above: only a strict greater-than percentage subset";
        if(kind.equals("action"))switch(type){
            case "write_to_log":return "Log message: run-local trace only";
            case "sleep":return "Wait: bounded, cancellable delay; no durable sleep";
            case "notification_status_bar":return "Show notification: simple own-app notification subset";
            case "set_screen_brightness":return "Set brightness: manual mode and special access required";
            case "set_wifi_state":return "Open Wi-Fi controls: user-assisted, not a direct toggle";
        }
        return "No implemented analogue assessed";
    }
    public String report(){
        StringBuilder out=new StringBuilder("LEGACY REVIEW — NOT EXECUTABLE\nOriginal XML is archived unchanged. No native flow or active schedule is created.\nConfiguration and execution-policy parity have not been verified.\n\n");
        out.append("Export version: ").append(version).append("\nDefinitions: ").append(definitions.size()).append("\nFlows: ").append(flows.size()).append("\nXML elements: ").append(elementCount).append("\n\n");
        for(LegacyFlow f:flows){
            out.append("FLOW: ").append(f.name).append("\nOriginal enabled: ").append(f.originallyEnabled).append(" (not activated)\nPolicy: ").append(f.policy).append("\n");
            for(Container c:f.containers){out.append("  ").append(c.id).append(" [").append(c.kind).append("] ").append(c.references).append("\n");}
            for(Connection c:f.connections)out.append("  ").append(c.from).append(" —").append(c.type).append("→ ").append(c.to).append("\n");
            out.append('\n');
        }
        out.append("DEFINITIONS / PARTIAL ANALOGUES\n");
        for(Definition d:definitions)out.append(d.kind).append(" · ").append(d.name).append(" · ").append(d.type).append(" (line ").append(d.element.line).append(")\n  ").append(analogue(d.kind,d.type)).append("\n");
        out.append("\nDIAGNOSTICS\n");
        for(Issue i:issues)out.append(i.severity).append(" ").append(i.code).append(" @ line ").append(i.line).append(": ").append(i.message).append('\n');
        return out.toString();
    }
}
