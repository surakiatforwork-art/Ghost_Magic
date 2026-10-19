package com.ghostmagic.android;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class FlowStore {
    private final File folder;
    final List<String> loadErrors=new ArrayList<>();
    FlowStore(Context c) { folder=new File(c.getFilesDir(),"flows"); if(!folder.isDirectory()&&!folder.mkdirs()) throw new IllegalStateException("Cannot create flow storage"); }
    private File file(String id) { UUID.fromString(id); return new File(folder,id+".json"); }
    FlowDocument get(String id) throws Exception {
        synchronized(ExecutionCoordinator.LOCK){try(InputStream in=new AtomicFile(file(id)).openRead()){ return FlowDocument.parse(readLimited(in)); }}
    }
    List<FlowDocument> load() {
        synchronized(ExecutionCoordinator.LOCK){
        loadErrors.clear(); List<FlowDocument> docs=new ArrayList<>();
        File[] files=folder.listFiles((dir,name)->name.endsWith(".json"));
        if(files!=null) for(File f:files) try(InputStream in=new AtomicFile(f).openRead()) { docs.add(FlowDocument.parse(readLimited(in))); }
        catch(Exception e) { loadErrors.add(f.getName()+": "+e.getMessage()); }
        docs.sort(Comparator.comparing(d->d.name)); return docs;}
    }
    void save(FlowDocument d) throws Exception {
        synchronized(ExecutionCoordinator.LOCK){
        List<String> errors=new com.ghostmagic.core.Engine().validate(d.flow());
        if(!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n",errors));
        AtomicFile af=new AtomicFile(file(d.id)); FileOutputStream out=null;
        try { out=af.startWrite(); out.write(d.json().toString(2).getBytes(StandardCharsets.UTF_8)); af.finishWrite(out); }
        catch(Exception e) { if(out!=null) af.failWrite(out); throw e; }}
    }
    void delete(String id) { synchronized(ExecutionCoordinator.LOCK){new AtomicFile(file(id)).delete();} }
    static String readLimited(InputStream in) throws IOException {
        return new String(readLimitedBytes(in),StandardCharsets.UTF_8);
    }
    static byte[] readLimitedBytes(InputStream in) throws IOException {
        if(in==null) throw new IOException("Document could not be opened");
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n;
        while((n=in.read(buf))!=-1) { if(out.size()+n>1024*1024) throw new IOException("Flow file exceeds 1 MiB"); out.write(buf,0,n); }
        return out.toByteArray();
    }
}
