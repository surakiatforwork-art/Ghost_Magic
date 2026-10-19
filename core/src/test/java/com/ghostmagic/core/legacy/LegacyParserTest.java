package com.ghostmagic.core.legacy;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import static org.junit.Assert.*;

public class LegacyParserTest {
    private LegacyDocument parse(String xml)throws IOException{return new LegacyParser().parse(xml.getBytes(StandardCharsets.UTF_8));}
    private String fixture(){return """
        <?xml version="1.0" encoding="UTF-8"?>
        <data version="1.37.0">
          <trigger type="periodic_timer_inexact"><name>Timer</name><interval>123</interval></trigger>
          <condition type="battery_level"><name>Battery</name><futureSetting vendor="x">keep me</futureSetting></condition>
          <action type="write_to_log"><name>Log</name><text><![CDATA[Hello <world> & ${legacy_var}]]></text></action>
          <action type="plugin.unknown"><name>Unknown</name><opaque><value>42</value></opaque></action>
          <widget type="custom"><opaque>preserved</opaque></widget>
          <flow type="flow"><name>Example</name><enabled>true</enabled><executionPolicy>QUEUE</executionPolicy>
            <triggercontainer id="start" x="1" y="2"><trigger>Timer</trigger></triggercontainer>
            <conditioncontainer id="check">Battery</conditioncontainer>
            <actioncontainer id="yes">Log</actioncontainer>
            <actioncontainer id="no"><action>Unknown</action></actioncontainer>
            <connection from="start" to="check" type="NORMAL"/>
            <connection from="check" to="yes" type="TRUE"/>
            <connection from="check" to="no" type="FALSE"/>
            <connection from="yes" to="no" type="EXCEPTION"/>
            <futureFlowField><nested attr="retained"/></futureFlowField>
          </flow>
        </data>
        """;}
    @Test public void sharedObjectsBranchesAndUnknownXmlRemainInspectable()throws Exception{
        LegacyDocument d=parse(fixture());assertEquals(4,d.definitions().size());assertEquals(1,d.flows().size());
        LegacyDocument.LegacyFlow f=d.flows().get(0);assertEquals("QUEUE",f.policy());assertEquals("true",f.originallyEnabled());assertEquals(4,f.containers().size());assertEquals(4,f.connections().size());
        assertEquals("FALSE",f.connections().get(2).type());assertEquals("Unknown",f.containers().get(3).references().get(0));
        assertEquals("Hello <world> & ${legacy_var}",d.resolve("action","Log").get(0).element().value("text"));
        assertEquals("preserved",d.root().named("widget").get(0).value("opaque"));assertTrue(d.report().contains("NOT EXECUTABLE"));assertEquals(0,d.count(LegacyDocument.Severity.ERROR));
        assertTrue(d.issues().stream().anyMatch(i->i.code().equals("PRESERVED_ELEMENT")&&i.message().contains("futureFlowField")));
    }
    @Test public void namesResolveOnlyWithinTheirObjectKind()throws Exception{
        LegacyDocument d=parse(fixture().replace("<name>Log</name>","<name>Battery</name>").replace(">Log</actioncontainer>",">Battery</actioncontainer>"));
        assertEquals(1,d.resolve("action","Battery").size());assertEquals(1,d.resolve("condition","Battery").size());assertEquals(0,d.count(LegacyDocument.Severity.ERROR));
    }
    @Test public void duplicateDefinitionsDoNotResolveArbitrarily()throws Exception{
        String extra="<action type='sleep'><name>Log</name></action>";LegacyDocument d=parse(fixture().replace("</data>",extra+"</data>"));
        assertEquals(2,d.resolve("action","Log").size());assertTrue(d.issues().stream().anyMatch(i->i.code().equals("AMBIGUOUS_REFERENCE")));assertTrue(d.issues().stream().filter(i->i.code().equals("DUPLICATE_DEFINITION")).allMatch(i->i.line()>0));
    }
    @Test public void missingObjectsAndEdgesAreReportedWithSourceLines()throws Exception{
        LegacyDocument d=parse(fixture().replace(">Log</actioncontainer>",">Absent</actioncontainer>").replace("to=\"no\"","to=\"missing\""));
        assertTrue(d.issues().stream().anyMatch(i->i.code().equals("DANGLING_REFERENCE")&&i.line()>0));assertTrue(d.issues().stream().anyMatch(i->i.code().equals("DANGLING_CONNECTION")&&i.line()>0));
    }
    @Test public void multipleTriggersFanOutAndUnknownEdgesAreNeverFlattened()throws Exception{
        LegacyDocument d=parse(fixture().replace("<trigger>Timer</trigger>","<trigger>Timer</trigger><trigger>Timer</trigger>").replace("</flow>","<connection from='check' to='no' type='TRUE'/><connection from='no' to='yes' type='CUSTOM'/></flow>"));
        assertEquals(2,d.flows().get(0).containers().get(0).references().size());assertEquals(6,d.flows().get(0).connections().size());
        for(String code:new String[]{"MULTI_REFERENCE","FAN_OUT","UNVERIFIED_CONNECTION"})assertTrue(d.issues().stream().anyMatch(i->i.code().equals(code)));
    }
    @Test public void internalAndExternalDtdsAreRejectedBeforeExpansion()throws Exception{
        for(String declaration:new String[]{"<!DOCTYPE data [<!ENTITY x 'expanded'>]>","<!DOCTYPE data SYSTEM 'file:///not-read'>","<!DOCTYPE data [<!ENTITY x SYSTEM 'https://example.invalid/never-fetch'>]>"}){
            try{parse(declaration+"<data version='1.37.0'/>");fail("DTD accepted");}catch(IOException expected){assertTrue(expected.getMessage().contains("DTD"));}
        }
    }
    @Test public void dtdTextInsideCdataAndCommentsIsNotAnActualDeclaration()throws Exception{
        LegacyDocument d=parse("<data version='1.37.0'><!-- <!DOCTYPE html> --><action type='write_to_log'><name>HTML</name><text><![CDATA[<!DOCTYPE html>]]></text></action></data>");
        assertEquals("<!DOCTYPE html>",d.definitions().get(0).element().value("text"));
    }
    @Test public void utf16InputIsReadAccordingToXmlEncoding()throws Exception{
        String xml="<?xml version='1.0' encoding='UTF-16'?><data version='1.37.0'><flow type='flow'><name>ทดสอบ</name></flow></data>";
        LegacyDocument d=new LegacyParser().parse(xml.getBytes(StandardCharsets.UTF_16));assertEquals("ทดสอบ",d.flows().get(0).name());
    }
    @Test public void oversizedDeepAndMalformedDocumentsFailClosed()throws Exception{
        try{new LegacyParser().parse(new byte[LegacyParser.MAX_BYTES+1]);fail();}catch(IOException expected){}
        try{parse("<data>"+"<x>".repeat(65)+"</x>".repeat(65)+"</data>");fail();}catch(IOException expected){}
        try{parse("<data><flow></data>");fail();}catch(IOException expected){}
        try{parse("<other/>");fail();}catch(IOException expected){}
    }
    @Test public void unknownVersionsAreRetainedForReview()throws Exception{
        LegacyDocument d=parse(fixture().replace("1.37.0","9.0"));assertEquals("9.0",d.version());assertTrue(d.issues().stream().anyMatch(i->i.code().equals("UNVERIFIED_VERSION")));
    }
}
