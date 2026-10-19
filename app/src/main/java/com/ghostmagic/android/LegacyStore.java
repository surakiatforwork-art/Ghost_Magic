package com.ghostmagic.android;

import android.content.Context;
import android.util.AtomicFile;
import com.ghostmagic.core.legacy.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Content-addressed, byte-exact source archives. No connection to the runnable FlowStore. */
final class LegacyStore {
    private static final Object LOCK=new Object();
    private final File folder;
    static final int MAX_ARCHIVES=50;
    LegacyStore(Context c){folder=new File(c.getFilesDir(),"legacy");if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException("Cannot create legacy archive storage");}
    record Summary(String id,String sourceName,long imported,int bytes,int flows,int errors){}
    record Archive(Summary summary,LegacyDocument document){}
    private File file(String id,String suffix){if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid archive identity");return new File(folder,id+suffix);}
    Archive save(byte[] original,String sourceName)throws Exception{
        LegacyDocument parsed=new LegacyParser().parse(original);String id=hash(original);
        synchronized(LOCK){
            File existing=file(id,".flows");
            if(existing.exists()){
                if(!Arrays.equals(original,original(id)))throw new IOException("Existing archive has changed; refusing to overwrite it");
                return archive(summary(id),parsed,original.length);
            }
            if(list().size()>=MAX_ARCHIVES)throw new IOException("Legacy library limit reached (50 archives). Export and remove an archive before importing another.");
            String name=sourceName==null||sourceName.trim().isEmpty()?"Legacy export.flows":sourceName;
            if(name.length()>200)name=name.substring(0,200);
            Summary summary=new Summary(id,name,System.currentTimeMillis(),original.length,parsed.flows().size(),(int)parsed.count(LegacyDocument.Severity.ERROR));
            // Source commits first. An interrupted metadata write still leaves a recoverable archive.
            write(new AtomicFile(existing),original);
            JSONObject meta=new JSONObject().put("schemaVersion",1).put("id",id).put("sourceName",name).put("imported",summary.imported()).put("bytes",summary.bytes()).put("flows",summary.flows()).put("errors",summary.errors());
            write(new AtomicFile(file(id,".json")),meta.toString(2).getBytes(StandardCharsets.UTF_8));
            return new Archive(summary,parsed);
        }
    }
    List<Summary> list(){synchronized(LOCK){
        List<Summary> result=new ArrayList<>();File[] sources=folder.listFiles((dir,name)->name.matches("[a-f0-9]{64}\\.flows"));
        if(sources!=null)for(File source:sources)result.add(summary(source.getName().substring(0,64)));
        result.sort(Comparator.comparingLong(Summary::imported).reversed());return result;
    }}
    private Summary summary(String id){
        synchronized(LOCK){
        try(InputStream in=new AtomicFile(file(id,".json")).openRead()){
            JSONObject o=new JSONObject(FlowStore.readLimited(in));if(o.getInt("schemaVersion")!=1||!o.getString("id").equals(id))throw new IOException("Unrecognized archive metadata");
            return new Summary(id,o.getString("sourceName"),o.getLong("imported"),o.getInt("bytes"),o.getInt("flows"),o.getInt("errors"));
        }catch(Exception e){return new Summary(id,"Recovered archive "+id.substring(0,8),(file(id,".flows")).lastModified(),(int)file(id,".flows").length(),-1,-1);}}
    }
    Archive read(String id)throws Exception{
        byte[] original=original(id);return archive(summary(id),new LegacyParser().parse(original),original.length);
    }
    private Archive archive(Summary summary,LegacyDocument document,int bytes){return new Archive(new Summary(summary.id(),summary.sourceName(),summary.imported(),bytes,document.flows().size(),(int)document.count(LegacyDocument.Severity.ERROR)),document);}
    static String report(Archive archive){return "GHOST MAGIC LEGACY REVIEW — NOT EXECUTABLE\nInspector profile: 1\nSource SHA-256: "+archive.summary().id()+"\nBytes: "+archive.summary().bytes()+"\nSource name: "+JSONObject.quote(archive.summary().sourceName())+"\n\n"+archive.document().report();}
    byte[] original(String id)throws Exception{synchronized(LOCK){
        byte[] bytes;try(InputStream in=new AtomicFile(file(id,".flows")).openRead()){bytes=FlowStore.readLimitedBytes(in);}
        if(!hash(bytes).equals(id))throw new IOException("Archive checksum changed. Source is retained, but this copy cannot be trusted.");return bytes;
    }}
    void delete(String id){synchronized(LOCK){new AtomicFile(file(id,".flows")).delete();new AtomicFile(file(id,".json")).delete();}}
    static String hash(byte[] bytes)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder out=new StringBuilder();for(byte b:digest){out.append(Character.forDigit((b>>>4)&15,16));out.append(Character.forDigit(b&15,16));}return out.toString();}
    private void write(AtomicFile file,byte[] bytes)throws Exception{
        FileOutputStream stream=null;try{stream=file.startWrite();stream.write(bytes);file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
    }
}
