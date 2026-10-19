package com.ghostmagic.android;

import android.app.job.JobScheduler;
import android.test.ActivityInstrumentationTestCase2;
import com.ghostmagic.core.legacy.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LegacyIntegrationTest extends ActivityInstrumentationTestCase2<MainActivity>{
    public LegacyIntegrationTest(){super(MainActivity.class);}
    private byte[] fixture()throws Exception{try(InputStream in=getInstrumentation().getContext().getAssets().open("synthetic-legacy.flows")){return FlowStore.readLimitedBytes(in);}}
    public void testAndroidParserAndArchiveDoNotCreateFlowsOrSchedules()throws Exception{
        MainActivity a=getActivity();LegacyStore store=new LegacyStore(a);Set<String> before=new HashSet<>();for(FlowDocument f:new FlowStore(a).load())before.add(f.id);
        int schedules=RuntimeDatabase.get(a).schedules().size(),jobs=a.getSystemService(JobScheduler.class).getAllPendingJobs().size();byte[] original=fixture();LegacyStore.Archive archive=store.save(original,"synthetic-legacy.flows");
        try{
            assertEquals(1,archive.document().flows().size());assertEquals(4,archive.document().definitions().size());assertEquals("true",archive.document().flows().get(0).originallyEnabled());
            assertEquals(4,archive.document().flows().get(0).connections().size());assertEquals("FALSE",archive.document().flows().get(0).connections().get(2).type());
            assertTrue(archive.document().resolve("action","Write log").get(0).element().value("text").contains("ทดสอบ <xml> & ${legacy_variable}"));
            assertEquals(0,archive.document().count(LegacyDocument.Severity.ERROR));assertTrue(archive.document().report().contains("NOT EXECUTABLE"));
            assertTrue(Arrays.equals(original,store.original(archive.summary().id())));
            assertTrue(LegacyStore.report(archive).contains(archive.summary().id()));
            Set<String> after=new HashSet<>();for(FlowDocument f:new FlowStore(a).load())after.add(f.id);assertEquals(before,after);
            assertEquals(schedules,RuntimeDatabase.get(a).schedules().size());assertEquals(jobs,a.getSystemService(JobScheduler.class).getAllPendingJobs().size());
        }finally{store.delete(archive.summary().id());}
    }
    public void testUtf16CommentsAndLineEndingsArePreservedByteForByte()throws Exception{
        LegacyStore store=new LegacyStore(getActivity());byte[] original=("<?xml version='1.0' encoding='UTF-16'?>\r\n<!--keep this comment-->\r\n<data version='1.38.0'><flow type='flow'><name>ไทย</name><enabled>true</enabled></flow></data>").getBytes(StandardCharsets.UTF_16);
        LegacyStore.Archive archive=store.save(original,"../../display-name.flows");
        try{assertEquals("ไทย",store.read(archive.summary().id()).document().flows().get(0).name());assertTrue(Arrays.equals(original,store.original(archive.summary().id())));assertEquals(LegacyStore.hash(original),archive.summary().id());}
        finally{store.delete(archive.summary().id());}
    }
    public void testRepeatedImportDeduplicatesWithoutOverwritingIdentity()throws Exception{
        LegacyStore store=new LegacyStore(getActivity());byte[] original=fixture();int before=store.list().size();LegacyStore.Archive first=store.save(original,"first.flows");
        try{LegacyStore.Archive second=store.save(original,"second.flows");assertEquals(first.summary().id(),second.summary().id());assertEquals("first.flows",second.summary().sourceName());assertEquals(before+1,store.list().size());}
        finally{store.delete(first.summary().id());}
    }
    public void testDtdAndMalformedXmlCannotEnterArchive()throws Exception{
        LegacyStore store=new LegacyStore(getActivity());int before=store.list().size();
        for(String xml:new String[]{"<!DOCTYPE data [<!ENTITY x 'expanded'>]><data version='1.37.0'/>","<!DOCTYPE data SYSTEM 'file:///not-read'><data/>","<data><flow></data>"}){
            try{store.save(xml.getBytes(StandardCharsets.UTF_8),"rejected.flows");fail("Unsafe or malformed XML archived");}catch(IOException expected){}
        }assertEquals(before,store.list().size());
    }
    public void testChangedArchiveIsNotSilentlyReadOrOverwritten()throws Exception{
        MainActivity a=getActivity();LegacyStore store=new LegacyStore(a);byte[] original=fixture();LegacyStore.Archive archive=store.save(original,"integrity.flows");File source=new File(a.getFilesDir(),"legacy/"+archive.summary().id()+".flows");
        try{
            byte[] changed=Arrays.copyOf(original,original.length+1);changed[changed.length-1]=' ';
            try(OutputStream out=new FileOutputStream(source)){out.write(changed);}
            try{store.read(archive.summary().id());fail("Changed source accepted");}catch(IOException expected){assertTrue(expected.getMessage().contains("checksum"));}
            try{store.save(original,"replacement.flows");fail("Changed source silently overwritten");}catch(IOException expected){}
            try(InputStream in=new FileInputStream(source)){assertTrue(Arrays.equals(changed,FlowStore.readLimitedBytes(in)));}
        }finally{store.delete(archive.summary().id());}
    }
    public void testMissingMetadataStillLeavesRecoverableOriginal()throws Exception{
        MainActivity a=getActivity();LegacyStore store=new LegacyStore(a);byte[] original=fixture();LegacyStore.Archive archive=store.save(original,"metadata.flows");
        try{assertTrue(new File(a.getFilesDir(),"legacy/"+archive.summary().id()+".json").delete());LegacyStore.Archive recovered=store.read(archive.summary().id());assertTrue(recovered.summary().sourceName().startsWith("Recovered archive"));assertEquals(1,recovered.document().flows().size());assertTrue(Arrays.equals(original,store.original(archive.summary().id())));}
        finally{store.delete(archive.summary().id());}
    }
}
