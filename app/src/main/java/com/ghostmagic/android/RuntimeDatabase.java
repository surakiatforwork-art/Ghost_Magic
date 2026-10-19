package com.ghostmagic.android;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import com.ghostmagic.core.RunResult;
import org.json.*;
import java.util.*;

/** Durable run journal and schedule settings; separate from portable flow definitions. */
final class RuntimeDatabase extends SQLiteOpenHelper {
    private static RuntimeDatabase instance;
    private static final String OWNER=UUID.randomUUID().toString();
    static synchronized RuntimeDatabase get(Context c){if(instance==null)instance=new RuntimeDatabase(c.getApplicationContext());return instance;}
    private RuntimeDatabase(Context c){super(c,"runtime.db",null,2);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE schedules(job_id INTEGER PRIMARY KEY AUTOINCREMENT,flow_id TEXT UNIQUE NOT NULL,interval_minutes INTEGER NOT NULL,revision INTEGER NOT NULL,generation INTEGER NOT NULL,enabled INTEGER NOT NULL,last_wall INTEGER NOT NULL DEFAULT 0,last_elapsed INTEGER NOT NULL DEFAULT 0,last_boot INTEGER NOT NULL DEFAULT -1)");
        db.execSQL("CREATE TABLE runs(id TEXT PRIMARY KEY,flow_id TEXT NOT NULL,name TEXT NOT NULL,revision INTEGER NOT NULL,source TEXT NOT NULL,status TEXT NOT NULL,started INTEGER NOT NULL,ended INTEGER,message TEXT,error_code TEXT,trace TEXT,variables TEXT,owner TEXT NOT NULL)");
        db.execSQL("CREATE INDEX run_time ON runs(started DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion==1&&newVersion==2){db.execSQL("ALTER TABLE schedules ADD COLUMN last_wall INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE schedules ADD COLUMN last_elapsed INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE schedules ADD COLUMN last_boot INTEGER NOT NULL DEFAULT -1");}
        else throw new IllegalStateException("Unsupported runtime database migration");
    }
    @Override public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        ContentValues v=new ContentValues();v.put("status","INTERRUPTED");v.put("ended",System.currentTimeMillis());
        v.put("message","Previous process ended before a final result was recorded. Effects may already have occurred; not retried.");
        v.put("error_code","PROCESS_ENDED");
        db.update("runs",v,"status=? AND owner<>?",new String[]{"RUNNING",OWNER});
    }
    record Schedule(int jobId,String flowId,int intervalMinutes,int revision,long generation,boolean enabled,long lastWall,long lastElapsed,int lastBoot){}
    Schedule schedule(String flowId){
        try(Cursor c=getReadableDatabase().query("schedules",null,"flow_id=?",new String[]{flowId},null,null,null)){
            return c.moveToFirst()?schedule(c):null;
        }
    }
    List<Schedule> schedules(){
        List<Schedule> result=new ArrayList<>();try(Cursor c=getReadableDatabase().query("schedules",null,null,null,null,null,"job_id")){while(c.moveToNext())result.add(schedule(c));}return result;
    }
    private Schedule schedule(Cursor c){return new Schedule(c.getInt(c.getColumnIndexOrThrow("job_id")),s(c,"flow_id"),c.getInt(c.getColumnIndexOrThrow("interval_minutes")),c.getInt(c.getColumnIndexOrThrow("revision")),c.getLong(c.getColumnIndexOrThrow("generation")),c.getInt(c.getColumnIndexOrThrow("enabled"))!=0,c.getLong(c.getColumnIndexOrThrow("last_wall")),c.getLong(c.getColumnIndexOrThrow("last_elapsed")),c.getInt(c.getColumnIndexOrThrow("last_boot")));}
    Schedule prepareSchedule(FlowDocument flow,int interval){
        Schedule old=schedule(flow.id);ContentValues v=new ContentValues();v.put("flow_id",flow.id);v.put("interval_minutes",interval);v.put("revision",flow.revision);v.put("generation",old==null?1:old.generation()+1);v.put("enabled",0);
        // An explicit new/updated schedule starts a new occurrence window.
        v.put("last_wall",0);v.put("last_elapsed",0);v.put("last_boot",-1);
        if(old==null)getWritableDatabase().insertOrThrow("schedules",null,v);else getWritableDatabase().update("schedules",v,"flow_id=?",new String[]{flow.id});
        return schedule(flow.id);
    }
    void activate(Schedule schedule){ContentValues v=new ContentValues();v.put("enabled",1);getWritableDatabase().update("schedules",v,"job_id=? AND generation=?",new String[]{""+schedule.jobId(),""+schedule.generation()});}
    void disable(String flowId){getWritableDatabase().execSQL("UPDATE schedules SET enabled=0,generation=generation+1 WHERE flow_id=?",new Object[]{flowId});}
    void claim(Schedule row,long wall,long elapsed,int boot){ContentValues v=new ContentValues();v.put("last_wall",wall);v.put("last_elapsed",elapsed);v.put("last_boot",boot);int updated=getWritableDatabase().update("schedules",v,"job_id=? AND generation=? AND enabled=1",new String[]{""+row.jobId(),""+row.generation()});if(updated!=1)throw new IllegalStateException("Schedule changed before claim");}
    String begin(FlowDocument flow,String source){
        return begin(flow.id,flow.name,flow.revision,source);
    }
    String begin(String flowId,String name,int revision,String source){
        String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();
        v.put("id",id);v.put("flow_id",flowId);v.put("name",name);v.put("revision",revision);v.put("source",source);v.put("status","RUNNING");v.put("started",System.currentTimeMillis());v.put("owner",OWNER);
        getWritableDatabase().insertOrThrow("runs",null,v);return id;
    }
    void finish(String id,RunResult result){
        try{
            JSONArray trace=new JSONArray();for(RunResult.TraceEntry entry:result.trace())trace.put(new JSONObject().put("nodeId",entry.nodeId()).put("type",entry.type()).put("port",entry.port()).put("message",entry.message()));
            ContentValues v=new ContentValues();v.put("status",result.status().name());v.put("ended",System.currentTimeMillis());v.put("message",result.message());v.put("error_code",result.errorCode());v.put("trace",trace.toString());v.put("variables",new JSONObject(result.variables()).toString());
            getWritableDatabase().update("runs",v,"id=? AND owner=? AND status=?",new String[]{id,OWNER,"RUNNING"});
            // Bound storage without deleting running records.
            getWritableDatabase().execSQL("DELETE FROM runs WHERE status<>'RUNNING' AND id NOT IN (SELECT id FROM runs ORDER BY started DESC,rowid DESC LIMIT 100)");
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    record Run(String id,String flowId,String name,int revision,String source,String status,long started,long ended,String message,String errorCode,String trace,String variables){}
    List<Run> runs(){
        List<Run> result=new ArrayList<>();try(Cursor c=getReadableDatabase().query("runs",null,null,null,null,null,"started DESC,rowid DESC","100")){
            while(c.moveToNext())result.add(new Run(s(c,"id"),s(c,"flow_id"),s(c,"name"),c.getInt(c.getColumnIndexOrThrow("revision")),s(c,"source"),s(c,"status"),c.getLong(c.getColumnIndexOrThrow("started")),c.getLong(c.getColumnIndexOrThrow("ended")),s(c,"message"),s(c,"error_code"),s(c,"trace"),s(c,"variables")));
        }return result;
    }
    private String s(Cursor c,String column){return c.getString(c.getColumnIndexOrThrow(column));}
    void deleteFlowRecords(String flowId){getWritableDatabase().delete("runs","flow_id=?",new String[]{flowId});getWritableDatabase().delete("schedules","flow_id=?",new String[]{flowId});}
}
