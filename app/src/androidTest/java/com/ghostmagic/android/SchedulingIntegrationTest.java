package com.ghostmagic.android;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.test.ActivityInstrumentationTestCase2;
import com.ghostmagic.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class SchedulingIntegrationTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public SchedulingIntegrationTest(){super(MainActivity.class);}
    private FlowDocument flow(){
        FlowDocument d=new FlowDocument();d.name="Schedule test "+d.id;Node start=d.add("trigger.periodic"),log=d.add("action.log");d.connect(start.id(),"next",log.id());return d;
    }
    private void cleanup(FlowDocument d){new ScheduleController(getInstrumentation().getTargetContext()).disable(d.id);new FlowStore(getInstrumentation().getTargetContext()).delete(d.id);RuntimeDatabase.get(getInstrumentation().getTargetContext()).deleteFlowRecords(d.id);}
    public void testPeriodicJobIsPersistedAndImportsStayInactive() throws Exception {
        MainActivity a=getActivity();FlowDocument d=flow();FlowStore store=new FlowStore(a);
        try{
            store.save(d);new ScheduleController(a).enable(d);RuntimeDatabase.Schedule row=RuntimeDatabase.get(a).schedule(d.id);
            assertTrue(row.enabled());JobInfo job=a.getSystemService(JobScheduler.class).getPendingJob(row.jobId());
            assertNotNull(job);assertTrue(job.isPersisted());assertEquals(TimeUnit.MINUTES.toMillis(15),job.getIntervalMillis());assertTrue(job.getFlexMillis()<job.getIntervalMillis());assertEquals(d.id,job.getExtras().getString("flowId"));assertEquals(row.generation(),job.getExtras().getLong("generation"));
            FlowDocument imported=FlowDocument.parse(d.json().toString());imported.id=UUID.randomUUID().toString();
            assertNull(RuntimeDatabase.get(a).schedule(imported.id));assertEquals("trigger.periodic",imported.node(imported.startId).type());
        }finally{cleanup(d);}
    }
    public void testStaleRevisionAndDisabledScheduleHaveNoEffects() throws Exception {
        MainActivity a=getActivity();FlowDocument d=flow();FlowStore store=new FlowStore(a);ScheduleController schedules=new ScheduleController(a);
        try{
            store.save(d);schedules.enable(d);RuntimeDatabase.Schedule old=RuntimeDatabase.get(a).schedule(d.id);
            d.revision++;store.save(d);schedules.refresh(d);RuntimeDatabase.Schedule fresh=RuntimeDatabase.get(a).schedule(d.id);assertTrue(fresh.generation()>old.generation());
            AtomicInteger effects=new AtomicInteger();ActionHost host=new ActionHost(){public void execute(String t,Map<String,String> c,Map<String,String> v){effects.incrementAndGet();}public int batteryPercent(){return 80;}};
            RunResult stale=ExecutionCoordinator.execute(a,d,"periodic",new AtomicBoolean(),host,()->schedules.requireCurrent(d,old.jobId(),old.generation()));
            assertEquals("SCHEDULE_INACTIVE",stale.errorCode());assertEquals(0,effects.get());
            schedules.disable(d.id);assertFalse(RuntimeDatabase.get(a).schedule(d.id).enabled());assertNull(a.getSystemService(JobScheduler.class).getPendingJob(fresh.jobId()));
            RunResult disabled=ExecutionCoordinator.execute(a,d,"periodic",new AtomicBoolean(),host,()->schedules.requireCurrent(d,fresh.jobId(),fresh.generation()));
            assertEquals("SCHEDULE_INACTIVE",disabled.errorCode());assertEquals(0,effects.get());
        }finally{cleanup(d);}
    }
    public void testForegroundOnlyNodeBlocksScheduleAndBackgroundAdapter() throws Exception {
        MainActivity a=getActivity();FlowDocument d=flow();d.add("action.wifi.panel");
        try{
            new FlowStore(a).save(d);assertFalse(ScheduleController.backgroundErrors(d).isEmpty());
            try{new ScheduleController(a).enable(d);fail("Foreground-only graph scheduled");}catch(IllegalArgumentException expected){}
            assertNull(RuntimeDatabase.get(a).schedule(d.id));
            try{new AndroidActionHost(a.getApplicationContext()).execute("action.wifi.panel",Map.of(),Map.of());fail("Activity opened from background");}catch(ActionException e){assertEquals(ActionException.UNSUPPORTED,e.code());}
        }finally{cleanup(d);}
    }
    public void testManualAndBackgroundShareGuardAndCancelledHistory() throws Exception {
        MainActivity a=getActivity();FlowDocument d=flow();Node wait=d.add("action.delay");
        d.nodes.set(2,new Node(wait.id(),wait.type(),Map.of("milliseconds","60000")));d.connect(d.startId,"next",wait.id());
        ExecutorService executor=Executors.newSingleThreadExecutor();AtomicBoolean cancel=new AtomicBoolean();
        try{
            Future<RunResult> first=executor.submit(()->ExecutionCoordinator.execute(a,d,"periodic",cancel,new AndroidActionHost(a.getApplicationContext()),()->{}));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!ExecutionCoordinator.isBusy()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(ExecutionCoordinator.isBusy());
            RunResult second=ExecutionCoordinator.execute(a,d,"manual",new AtomicBoolean(),new AndroidActionHost(a),()->{});assertEquals("BUSY",second.errorCode());assertEquals(RunResult.Status.SKIPPED,second.status());
            ExecutionCoordinator.cancel(d.id);assertEquals(RunResult.Status.CANCELLED,first.get(3,TimeUnit.SECONDS).status());assertFalse(ExecutionCoordinator.isBusy());
            List<RuntimeDatabase.Run> history=RuntimeDatabase.get(a).runs();
            assertTrue(history.stream().anyMatch(r->r.flowId().equals(d.id)&&r.status().equals("CANCELLED")));
            assertTrue(history.stream().anyMatch(r->r.flowId().equals(d.id)&&"BUSY".equals(r.errorCode())));
        }finally{cancel.set(true);executor.shutdownNow();executor.awaitTermination(3,TimeUnit.SECONDS);cleanup(d);}
    }
    public void testRepeatedDeliveryAndRestoredJobDoNotReplayOccurrence() throws Exception {
        MainActivity a=getActivity();FlowDocument d=flow();ScheduleController schedules=new ScheduleController(a);RuntimeDatabase db=RuntimeDatabase.get(a);
        AtomicInteger effects=new AtomicInteger();ActionHost host=new ActionHost(){public void execute(String t,Map<String,String> c,Map<String,String> v){effects.incrementAndGet();}public int batteryPercent(){return 80;}};
        try{
            new FlowStore(a).save(d);schedules.enable(d);RuntimeDatabase.Schedule row=db.schedule(d.id);
            RunResult first=ExecutionCoordinator.execute(a,d,"periodic",new AtomicBoolean(),host,()->schedules.claimCurrent(d,row.jobId(),row.generation()));
            assertEquals(RunResult.Status.COMPLETED,first.status());assertEquals(1,effects.get());long claimed=db.schedule(d.id).lastWall();assertTrue(claimed>0);
            RunResult duplicate=ExecutionCoordinator.execute(a,d,"periodic",new AtomicBoolean(),host,()->schedules.claimCurrent(d,row.jobId(),row.generation()));
            assertEquals("SCHEDULE_INACTIVE",duplicate.errorCode());assertEquals(1,effects.get());
            a.getSystemService(JobScheduler.class).cancel(row.jobId());assertTrue(schedules.restoreMissing().isEmpty());
            RuntimeDatabase.Schedule restored=db.schedule(d.id);assertEquals(row.generation(),restored.generation());assertEquals(claimed,restored.lastWall());
            RunResult afterRestore=ExecutionCoordinator.execute(a,d,"periodic",new AtomicBoolean(),host,()->schedules.claimCurrent(d,row.jobId(),row.generation()));
            assertEquals("SCHEDULE_INACTIVE",afterRestore.errorCode());assertEquals(1,effects.get());
            // Simulate an elapsed interval and a changed wall clock in the same boot.
            android.content.ContentValues clocks=new android.content.ContentValues();clocks.put("last_elapsed",android.os.SystemClock.elapsedRealtime()-TimeUnit.MINUTES.toMillis(16));clocks.put("last_wall",Long.MAX_VALUE);
            db.getWritableDatabase().update("schedules",clocks,"flow_id=?",new String[]{d.id});
            RunResult next=ExecutionCoordinator.execute(a,d,"periodic",new AtomicBoolean(),host,()->schedules.claimCurrent(d,row.jobId(),row.generation()));
            assertEquals(RunResult.Status.COMPLETED,next.status());assertEquals(2,effects.get());
        }finally{cleanup(d);}
    }
    public void testBusyScheduledOccurrenceIsReservedInsteadOfReplayed() throws Exception {
        MainActivity a=getActivity();FlowDocument first=flow(),second=flow();Node wait=first.add("action.delay");
        first.nodes.set(2,new Node(wait.id(),wait.type(),Map.of("milliseconds","60000")));first.connect(first.startId,"next",wait.id());
        AtomicBoolean cancel=new AtomicBoolean();AtomicInteger effects=new AtomicInteger();ExecutorService executor=Executors.newSingleThreadExecutor();ScheduleController schedules=new ScheduleController(a);
        ActionHost host=new ActionHost(){public void execute(String t,Map<String,String> c,Map<String,String> v){effects.incrementAndGet();}public int batteryPercent(){return 80;}};
        try{
            new FlowStore(a).save(second);schedules.enable(second);RuntimeDatabase.Schedule row=RuntimeDatabase.get(a).schedule(second.id);
            Future<RunResult> active=executor.submit(()->ExecutionCoordinator.execute(a,first,"manual",cancel,host,()->{}));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!ExecutionCoordinator.isBusy()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(ExecutionCoordinator.isBusy());
            RunResult busy=ExecutionCoordinator.execute(a,second,"periodic",new AtomicBoolean(),host,()->schedules.claimCurrent(second,row.jobId(),row.generation()));
            assertEquals(RunResult.Status.SKIPPED,busy.status());assertTrue(RuntimeDatabase.get(a).schedule(second.id).lastWall()>0);
            cancel.set(true);assertEquals(RunResult.Status.CANCELLED,active.get(3,TimeUnit.SECONDS).status());
            RunResult repeated=ExecutionCoordinator.execute(a,second,"periodic",new AtomicBoolean(),host,()->schedules.claimCurrent(second,row.jobId(),row.generation()));
            assertEquals("SCHEDULE_INACTIVE",repeated.errorCode());assertEquals(0,effects.get());
        }finally{cancel.set(true);executor.shutdownNow();executor.awaitTermination(3,TimeUnit.SECONDS);cleanup(first);cleanup(second);}
    }
}
