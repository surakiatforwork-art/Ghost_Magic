package com.ghostmagic.core.legacy;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;
import static com.ghostmagic.core.legacy.LegacyDocument.*;

/** Bounded SAX inspection, with DTDs/entities denied before their contents are processed. */
public final class LegacyParser {
    public static final int MAX_BYTES=1024*1024,MAX_DEPTH=64,MAX_ELEMENTS=10000;
    public LegacyDocument parse(byte[] bytes) throws IOException{
        if(bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("Legacy document must contain 1 byte to 1 MiB");
        Builder handler=new Builder();
        try{
            SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(true);factory.setValidating(false);
            XMLReader reader=factory.newSAXParser().getXMLReader();
            // LexicalHandler registration is mandatory: startDTD fails before entity declarations.
            reader.setProperty("http://xml.org/sax/properties/lexical-handler",handler);
            for(String feature:List.of("http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd"))try{reader.setFeature(feature,false);}catch(SAXNotRecognizedException|SAXNotSupportedException ignored){/* mandatory DTD rejection and resolver remain in force */}
            reader.setEntityResolver(handler);reader.setContentHandler(handler);reader.setErrorHandler(handler);
            reader.parse(new InputSource(new ByteArrayInputStream(bytes)));
        }catch(ParserConfigurationException|SAXException e){throw new IOException("Legacy XML rejected: "+e.getMessage(),e);}
        if(handler.root==null||!handler.root.name().equals("data")||!handler.root.attributes().getOrDefault("@namespace","").isEmpty())throw new IOException("Expected an unnamespaced Automagic <data> export");
        return inspect(handler.root,handler.elements);
    }
    private static final class Mutable {
        String name;Map<String,String> attrs=new LinkedHashMap<>();StringBuilder text=new StringBuilder();List<Element> children=new ArrayList<>();int line;
        Element freeze(){return new Element(name,attrs,text.toString(),children,line);}
    }
    private static final class Builder extends DefaultHandler2 {
        final Deque<Mutable> stack=new ArrayDeque<>();Locator locator;Element root;int elements;
        @Override public void setDocumentLocator(Locator locator){this.locator=locator;}
        @Override public void startDTD(String name,String publicId,String systemId)throws SAXException{throw new SAXException("DTD and entity declarations are not allowed");}
        @Override public InputSource resolveEntity(String publicId,String systemId)throws SAXException{throw new SAXException("External entities are not allowed");}
        @Override public InputSource resolveEntity(String name,String publicId,String base,String systemId)throws SAXException{throw new SAXException("External entities are not allowed");}
        @Override public void skippedEntity(String name)throws SAXException{throw new SAXException("Unresolved entity: "+name);}
        @Override public void startElement(String uri,String local,String qName,Attributes attributes)throws SAXException{
            if(stack.size()>=MAX_DEPTH||++elements>MAX_ELEMENTS||attributes.getLength()>64)throw new SAXException("XML complexity limit exceeded");
            Mutable e=new Mutable();e.name=uri.isEmpty()?qName:"{"+uri+"}"+local;e.line=locator==null?0:locator.getLineNumber();
            for(int i=0;i<attributes.getLength();i++)e.attrs.put(attributes.getQName(i),attributes.getValue(i));
            if(!uri.isEmpty())e.attrs.put("@namespace",uri);stack.push(e);
        }
        @Override public void characters(char[] ch,int start,int length){if(!stack.isEmpty())stack.peek().text.append(ch,start,length);}
        @Override public void endElement(String uri,String local,String qName){Element e=stack.pop().freeze();if(stack.isEmpty())root=e;else stack.peek().children.add(e);}
        @Override public void error(SAXParseException e)throws SAXException{throw e;}
        @Override public void fatalError(SAXParseException e)throws SAXException{throw e;}
    }
    private LegacyDocument inspect(Element root,int count){
        List<Definition> definitions=new ArrayList<>();List<LegacyFlow> flows=new ArrayList<>();List<Issue> issues=new ArrayList<>();
        String version=root.attributes().getOrDefault("version","");
        if(!Set.of("1.37.0","1.38.0").contains(version))issue(issues,Severity.WARNING,"UNVERIFIED_VERSION",root,"Export version is missing or outside the current reference set: "+version);
        Map<String,List<Definition>> names=new HashMap<>();
        for(Element e:root.children()){
            if(Set.of("trigger","condition","action").contains(e.name())){
                String name=e.value("name"),type=e.attributes().getOrDefault("type","");
                Definition d=new Definition(e.name(),type,name,e);definitions.add(d);names.computeIfAbsent(e.name()+"\u0000"+name,k->new ArrayList<>()).add(d);
                if(name.isEmpty()||type.isEmpty())issue(issues,Severity.ERROR,"MISSING_IDENTITY",e,"Shared "+e.name()+" requires name and type");
                if(e.named("name").size()>1)issue(issues,Severity.ERROR,"DUPLICATE_FIELD",e,"Multiple name elements are ambiguous");
            }else if(!e.name().equals("flow"))issue(issues,Severity.INFO,"PRESERVED_ELEMENT",e,"Top-level "+e.name()+" is retained in the original archive; no conversion is attempted");
        }
        for(List<Definition> duplicates:names.values())if(duplicates.size()>1)for(Definition d:duplicates)issue(issues,Severity.ERROR,"DUPLICATE_DEFINITION",d.element(),"Ambiguous "+d.kind()+" name: "+d.name());
        Set<String> flowNames=new HashSet<>();
        for(Element e:root.named("flow")){
            String name=e.value("name"),policy=e.value("executionPolicy"),enabled=e.value("enabled");
            if(name.isEmpty())issue(issues,Severity.ERROR,"MISSING_FLOW_NAME",e,"Flow has no name");
            if(!flowNames.add(name))issue(issues,Severity.WARNING,"DUPLICATE_FLOW_NAME",e,"Multiple flows share the name "+name);
            if(!e.attributes().getOrDefault("type","").equals("flow"))issue(issues,Severity.WARNING,"UNVERIFIED_FLOW_TYPE",e,"Flow type is outside the documented reference shape");
            if(!Set.of("PARALLEL","SKIP","QUEUE","STOP").contains(policy))issue(issues,Severity.WARNING,"UNVERIFIED_POLICY",e,"Unknown or missing execution policy: "+policy);
            else issue(issues,Severity.WARNING,"POLICY_REQUIRES_REVIEW",e,"Legacy "+policy+" semantics are not converted to the current runtime");
            List<Container> containers=new ArrayList<>();List<Connection> edges=new ArrayList<>();Map<String,Container> ids=new LinkedHashMap<>();
            for(Element child:e.children()){
                if(Set.of("triggercontainer","conditioncontainer","actioncontainer","notecontainer").contains(child.name())){
                    String kind=child.name().replace("container",""),id=child.attributes().getOrDefault("id","");List<String> refs=new ArrayList<>();
                    if(!kind.equals("note")){
                        for(Element ref:child.named(kind))refs.add(ref.text().trim());
                        String direct=child.text().trim();
                        if(!direct.isEmpty()){
                            if(!refs.isEmpty())issue(issues,Severity.ERROR,"AMBIGUOUS_REFERENCE",child,"Container mixes text and child references");
                            else refs.add(direct);
                        }
                        if(refs.isEmpty())issue(issues,Severity.ERROR,"MISSING_REFERENCE",child,"Container has no shared object reference");
                        for(String ref:refs){List<Definition> found=names.getOrDefault(kind+"\u0000"+ref,List.of());
                            if(found.isEmpty())issue(issues,Severity.ERROR,"DANGLING_REFERENCE",child,"Missing "+kind+": "+ref);
                            else if(found.size()>1)issue(issues,Severity.ERROR,"AMBIGUOUS_REFERENCE",child,"More than one "+kind+" is named "+ref);
                        }
                        if(refs.size()>1)issue(issues,Severity.WARNING,"MULTI_REFERENCE",child,"Multiple trigger/object references need semantic review");
                    }
                    Container c=new Container(id,kind,refs,child);containers.add(c);
                    if(id.isEmpty()||ids.put(id,c)!=null)issue(issues,Severity.ERROR,"DUPLICATE_OR_MISSING_CONTAINER_ID",child,"Container ID is empty or repeated: "+id);
                }else if(child.name().equals("connection"))edges.add(new Connection(child.attributes().getOrDefault("from",""),child.attributes().getOrDefault("to",""),child.attributes().getOrDefault("type",""),child));
                else if(!Set.of("name","description","group","enabled","lastExecutionStartTime","lastExecutionEndTime","executionPolicy","emergencyStopCount").contains(child.name()))issue(issues,Severity.INFO,"PRESERVED_ELEMENT",child,"Unknown Flow field retained: "+child.name());
            }
            Set<String> ports=new HashSet<>();
            for(Connection edge:edges){
                if(!ids.containsKey(edge.from())||!ids.containsKey(edge.to()))issue(issues,Severity.ERROR,"DANGLING_CONNECTION",edge.element(),"Connection references an absent container: "+edge.from()+" → "+edge.to());
                if(!Set.of("NORMAL","TRUE","FALSE","EXCEPTION","NOTE").contains(edge.type()))issue(issues,Severity.WARNING,"UNVERIFIED_CONNECTION",edge.element(),"Unknown connection type retained: "+edge.type());
                if(!ports.add(edge.from()+"\u0000"+edge.type()))issue(issues,Severity.WARNING,"FAN_OUT",edge.element(),"Multiple edges leave the same source/type; no single-edge conversion is attempted");
            }
            flows.add(new LegacyFlow(name,policy,enabled,containers,edges,e));
        }
        if(flows.isEmpty())issue(issues,Severity.WARNING,"NO_FLOWS",root,"Archive contains no top-level Flow");
        issue(issues,Severity.WARNING,"REVIEW_ONLY",root,"All nodes, action parameters and execution policies require review; no executable translation has been produced");
        return new LegacyDocument(version,definitions,flows,issues,count,root);
    }
    private static void issue(List<Issue> issues,Severity severity,String code,Element e,String message){issues.add(new Issue(severity,code,e.line(),message));}
}
