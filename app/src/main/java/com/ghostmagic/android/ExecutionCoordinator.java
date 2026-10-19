package com.ghostmagic.android;

import android.content.Context;
import com.ghostmagic.core.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One active execution per process, shared by the visible editor and JobService. */
final class ExecutionCoordinator {
    static final Object LOCK=new Object();
    private static String activeFlow;
    private static AtomicBoolean activeCancellation;
    static boolean isBusy(){synchronized(LOCK){return activeFlow!=null;}}
    static void cancel(String flowId){synchronized(LOCK){if(Objects.equals(activeFlow,flowId)&&activeCancellation!=null)activeCancellation.set(true);}}
    static void cancelCurrent(){synchronized(LOCK){if(activeCancellation!=null)activeCancellation.set(true);}}
    interface Admission { void check() throws Exception; }
    static RunResult execute(Context context,FlowDocument flow,String source,AtomicBoolean cancel,ActionHost host,Admission admission){
        try{return executeChecked(context,flow,source,cancel,host,admission);}
        catch(RuntimeException e){return failed("JOURNAL_FAILED","Run could not be durably recorded: "+e.getMessage());}
    }
    private static RunResult executeChecked(Context context,FlowDocument flow,String source,AtomicBoolean cancel,ActionHost host,Admission admission){
        RuntimeDatabase db=RuntimeDatabase.get(context);String runId;
        synchronized(LOCK){
            if(cancel.get())return new RunResult(RunResult.Status.CANCELLED,List.of(),Map.of(),"CANCELLED","Stopped before execution");
            android.database.sqlite.SQLiteDatabase transaction=db.getWritableDatabase();transaction.beginTransaction();
            try{
                try{admission.check();}catch(Exception e){return failed("SCHEDULE_INACTIVE",e.getMessage());}
                runId=db.begin(flow,source);
                if(activeFlow!=null){
                    RunResult busy=new RunResult(RunResult.Status.SKIPPED,List.of(),Map.of(),"BUSY","Another flow is running. This occurrence was skipped and will not be replayed.");
                    db.finish(runId,busy);transaction.setTransactionSuccessful();return busy;
                }
                transaction.setTransactionSuccessful();
            }finally{transaction.endTransaction();}
            activeFlow=flow.id;activeCancellation=cancel;
        }
        try{
            RunResult result;
            try{result=new Engine().run(flow.flow(),host,cancel::get);}catch(RuntimeException e){result=failed("EXECUTION_FAILED",e.toString());}
            db.finish(runId,result);return result;
        }finally{synchronized(LOCK){activeFlow=null;activeCancellation=null;}}
    }
    static RunResult failed(String code,String message){return new RunResult(RunResult.Status.FAILED,List.of(),Map.of(),code,Objects.toString(message,"Unknown error"));}
}
