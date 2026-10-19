package com.ghostmagic.android;

import android.app.job.*;
import android.os.*;
import android.util.Log;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded deferrable work; no background activities, hidden APIs, or foreground-service exemption. */
public final class FlowJobService extends JobService {
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final ConcurrentHashMap<Integer,Running> jobs=new ConcurrentHashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final class Running { final AtomicBoolean cancelled=new AtomicBoolean();volatile boolean stopped; }
    @Override public boolean onStartJob(JobParameters params){
        Running run=new Running();Running previous=jobs.put(params.getJobId(),run);
        if(previous!=null){previous.stopped=true;previous.cancelled.set(true);}
        workers.submit(()->{
            String flowId=params.getExtras().getString("flowId");
            try{
                FlowDocument flow=new FlowStore(this).get(flowId);
                ScheduleController schedules=new ScheduleController(this);
                com.ghostmagic.core.RunResult result=ExecutionCoordinator.execute(this,flow,"periodic",run.cancelled,new AndroidActionHost(this),
                    ()->schedules.claimCurrent(flow,params.getJobId(),params.getExtras().getLong("generation")));
                if("JOURNAL_FAILED".equals(result.errorCode()))Log.e("GhostMagicJob",result.message());
            }catch(Exception e){
                Log.e("GhostMagicJob","Scheduled flow could not be loaded",e);
                if(flowId!=null)try{
                    synchronized(ExecutionCoordinator.LOCK){
                        RuntimeDatabase db=RuntimeDatabase.get(this);RuntimeDatabase.Schedule row=db.schedule(flowId);
                        if(row!=null){String id=db.begin(flowId,"Unavailable flow "+flowId.substring(0,8),row.revision(),"periodic");db.finish(id,ExecutionCoordinator.failed("FLOW_UNAVAILABLE",e.getMessage()));}
                        new ScheduleController(this).disable(flowId);
                    }
                }catch(Exception journalError){Log.e("GhostMagicJob","Could not record failed schedule",journalError);}
            }finally{
                main.post(()->{if(jobs.remove(params.getJobId(),run)&&!run.stopped)jobFinished(params,false);});
            }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){
        Running run=jobs.remove(params.getJobId());if(run!=null){run.stopped=true;run.cancelled.set(true);}
        // Do not replay interrupted side effects. The next periodic occurrence remains scheduled.
        return false;
    }
    @Override public void onDestroy(){for(Running run:jobs.values()){run.stopped=true;run.cancelled.set(true);}jobs.clear();workers.shutdownNow();super.onDestroy();}
}
