package com.ghostmagic.android;

import com.ghostmagic.core.*;
import org.json.*;
import java.util.*;

final class FlowDocument {
    static final int FORMAT = 1;
    String id = UUID.randomUUID().toString(), name = "Untitled flow", startId;
    int revision = 1;
    final List<Node> nodes = new ArrayList<>();
    final List<Edge> edges = new ArrayList<>();
    final Map<String, float[]> positions = new HashMap<>();
    Flow flow() { return new Flow(id, name, startId, List.copyOf(nodes), List.copyOf(edges)); }
    Node node(String id) { for (Node n : nodes) if(n.id().equals(id)) return n; return null; }
    Node add(String type) {
        Catalog.Capability c = Catalog.find(type);
        Map<String,String> config = new LinkedHashMap<>();
        for (int i=0; i<c.fields().length; i++) config.put(c.fields()[i], c.defaults()[i]);
        Node n = new Node(UUID.randomUUID().toString(), type, config);
        nodes.add(n);
        positions.put(n.id(), new float[]{42, 50 + (nodes.size()-1)*150});
        if (type.startsWith("trigger.") && startId == null) startId = n.id();
        return n;
    }
    void connect(String from, String port, String to) {
        edges.removeIf(e -> e.from().equals(from) && e.port().equals(port));
        if (to != null) edges.add(new Edge(from, port, to));
    }
    void remove(String id) {
        nodes.removeIf(n -> n.id().equals(id));
        edges.removeIf(e -> e.from().equals(id) || e.to().equals(id));
        positions.remove(id);
        if (Objects.equals(startId,id)) startId = null;
    }
    JSONObject json() throws JSONException {
        JSONObject out = new JSONObject().put("format", "ghostmagic.flow").put("schemaVersion", FORMAT)
            .put("id", id).put("name", name).put("revision", revision).put("startId", startId);
        JSONArray ns = new JSONArray(), es = new JSONArray();
        for(Node n:nodes) {
            float[] p = positions.getOrDefault(n.id(),new float[]{42,50});
            ns.put(new JSONObject().put("id",n.id()).put("type",n.type()).put("config",new JSONObject(n.config()))
                .put("x",p[0]).put("y",p[1]));
        }
        for(Edge e:edges) es.put(new JSONObject().put("from",e.from()).put("port",e.port()).put("to",e.to()));
        return out.put("nodes",ns).put("edges",es);
    }
    static FlowDocument parse(String text) throws JSONException {
        JSONObject o = new JSONObject(text);
        if (!o.optString("format").equals("ghostmagic.flow") || o.optInt("schemaVersion") != FORMAT)
            throw new JSONException("Unsupported flow format. Legacy .flows import is not implemented yet.");
        FlowDocument d=new FlowDocument();
        d.id=o.getString("id"); UUID.fromString(d.id);
        d.name=o.getString("name"); if(d.name.trim().isEmpty() || d.name.length()>200) throw new JSONException("Invalid flow name");
        d.startId=o.getString("startId"); d.revision=o.getInt("revision");
        if(d.revision<1) throw new JSONException("Invalid revision");
        JSONArray ns=o.getJSONArray("nodes"), es=o.getJSONArray("edges");
        if(ns.length()>200 || es.length()>800) throw new JSONException("Flow exceeds the experimental editor's size limit");
        for(int i=0;i<ns.length();i++) {
            JSONObject n=ns.getJSONObject(i); String type=n.getString("type"); Catalog.find(type);
            JSONObject config=n.getJSONObject("config"); Map<String,String> values=new LinkedHashMap<>();
            Iterator<String> keys=config.keys();
            while(keys.hasNext()) { String k=keys.next(); Object value=config.get(k); if(!(value instanceof String)) throw new JSONException("Config values must be strings"); values.put(k,(String)value); }
            String nid=n.getString("id");
            d.nodes.add(new Node(nid,type,values));
            double x=n.getDouble("x"), y=n.getDouble("y");
            if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||y<0||x>10000||y>30000) throw new JSONException("Invalid node position");
            d.positions.put(nid,new float[]{(float)x,(float)y});
        }
        for(int i=0;i<es.length();i++) { JSONObject e=es.getJSONObject(i); d.edges.add(new Edge(e.getString("from"),e.getString("port"),e.getString("to"))); }
        List<String> errors=new Engine().validate(d.flow());
        if(!errors.isEmpty()) throw new JSONException(String.join("\n", errors));
        return d;
    }
    static FlowDocument demo() {
        FlowDocument d = new FlowDocument(); d.name="Battery-aware welcome";
        Node t=d.add("trigger.manual"), c=d.add("condition.battery.above"), yes=d.add("action.log"), no=d.add("action.log");
        d.nodes.set(2,new Node(yes.id(),yes.type(),Map.of("message","Battery is above 30%. Ready for automation.")));
        d.nodes.set(3,new Node(no.id(),no.type(),Map.of("message","Battery is 30% or below. Take the low-power branch.")));
        d.positions.put(c.id(),new float[]{42,220});
        d.positions.put(yes.id(),new float[]{20,410}); d.positions.put(no.id(),new float[]{330,410});
        d.connect(t.id(),"next",c.id()); d.connect(c.id(),"true",yes.id()); d.connect(c.id(),"false",no.id());
        return d;
    }
}
