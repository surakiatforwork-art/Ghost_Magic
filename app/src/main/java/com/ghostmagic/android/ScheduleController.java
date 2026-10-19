package com.ghostmagic.android;

import android.app.job.*;
import android.content.*;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import com.ghostmagic.core.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class ScheduleController {
    private final Context context;
    private final RuntimeDatabase db;
    private final JobScheduler scheduler;
    ScheduleController(Context c){context=c.getApplicationContext();db=RuntimeDatabase.get(context);scheduler=context.getSystemService(JobScheduler.class);}
    static List<String> backgroundErrors(FlowDocument flow){
        List<String> errors=new ArrayList<>(new Engine().validate(flow.flow()));
        for(Node node:flow.nodes)if(node.type().equals("action.wifi.panel"))errors.add("Open Wi-Fi controls needs the visible editor; remove it or keep this flow manual.");
        return errors;
    }
    void enable(FlowDocument flow) {
        synchronized(ExecutionCoordinator.LOCK){
            Node start=flow.node(flow.startId);
            if(start==null||!start.type().equals("trigger.periodic"))throw new IllegalArgumentException("A periodic trigger is required");
            List<String> errors=backgroundErrors(flow);if(!errors.isEmpty())throw new IllegalArgumentException(String.join("\n",errors));
            int interval=Integer.parseInt(start.config().get("intervalMinutes"));
            ExecutionCoordinator.cancel(flow.id);
            RuntimeDatabase.Schedule row=db.prepareSchedule(flow,interval);
            try{
                register(row);
                db.activate(row);
            }catch(RuntimeException e){scheduler.cancel(row.jobId());throw e;}
        }
    }
    private void register(RuntimeDatabase.Schedule row){
        PersistableBundle extras=new PersistableBundle();extras.putString("flowId",row.flowId());extras.putLong("generation",row.generation());
        long interval=TimeUnit.MINUTES.toMillis(row.intervalMinutes());
        long flex=Math.max(JobInfo.getMinFlexMillis(),interval/20);
        JobInfo job=new JobInfo.Builder(row.jobId(),new ComponentName(context,FlowJobService.class)).setPeriodic(interval,flex).setPersisted(true).setExtras(extras).build();
        if(scheduler.schedule(job)!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("Android rejected the periodic schedule");
    }
    void disable(String flowId){synchronized(ExecutionCoordinator.LOCK){RuntimeDatabase.Schedule row=db.schedule(flowId);db.disable(flowId);ExecutionCoordinator.cancel(flowId);if(row!=null)scheduler.cancel(row.jobId());}}
    void refresh(FlowDocument flow){synchronized(ExecutionCoordinator.LOCK){RuntimeDatabase.Schedule row=db.schedule(flow.id);if(row!=null&&row.enabled()&&row.revision()!=flow.revision)enable(flow);}}
    List<String> restoreMissing(){
        List<String> errors=new ArrayList<>();
        synchronized(ExecutionCoordinator.LOCK){
            for(RuntimeDatabase.Schedule row:db.schedules())if(row.enabled())try{
                FlowDocument flow=new FlowStore(context).get(row.flowId());
                JobInfo pending=scheduler.getPendingJob(row.jobId());
                if(row.revision()!=flow.revision)enable(flow);
                else if(pending==null||pending.getExtras().getLong("generation")!=row.generation())register(row);
            }catch(Exception e){disable(row.flowId());errors.add(row.flowId()+": "+e.getMessage());}
            else scheduler.cancel(row.jobId());
        }return errors;
    }
    void requireCurrent(FlowDocument flow,int jobId,long generation){
        RuntimeDatabase.Schedule row=db.schedule(flow.id);
        if(row==null||!row.enabled()||row.jobId()!=jobId||row.generation()!=generation||row.revision()!=flow.revision)
            throw new IllegalStateException("Schedule was disabled, replaced, or references a different saved flow revision");
        Node start=flow.node(flow.startId);
        if(start==null||!start.type().equals("trigger.periodic")||Integer.parseInt(start.config().get("intervalMinutes"))!=row.intervalMinutes())throw new IllegalStateException("Time trigger changed");
        List<String> errors=backgroundErrors(flow);if(!errors.isEmpty())throw new IllegalStateException(String.join("\n",errors));
    }
    void claimCurrent(FlowDocument flow,int jobId,long generation){
        // Caller holds ExecutionCoordinator.LOCK through admission and durable run creation.
        requireCurrent(flow,jobId,generation);RuntimeDatabase.Schedule row=db.schedule(flow.id);
        long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();int boot=Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);
        long since=row.lastBoot()==boot&&elapsed>=row.lastElapsed()?elapsed-row.lastElapsed():wall-row.lastWall();
        if(row.lastWall()!=0&&since<TimeUnit.MINUTES.toMillis(row.intervalMinutes()))throw new IllegalStateException("Occurrence already attempted in this interval; not replayed");
        db.claim(row,wall,elapsed,boot);
    }
}
