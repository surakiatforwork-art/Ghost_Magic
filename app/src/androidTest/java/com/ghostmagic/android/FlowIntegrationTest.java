package com.ghostmagic.android;

import android.test.ActivityInstrumentationTestCase2;
import android.view.*;
import android.widget.*;
import android.app.NotificationManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import com.ghostmagic.core.*;
import org.json.*;
import java.util.*;

/** Runs against the real Android JSON, storage, permission and battery APIs. */
public final class FlowIntegrationTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public FlowIntegrationTest() { super(MainActivity.class); }
    public void testRoundTripPreservesGraphAndNodePositions() throws Exception {
        FlowDocument original=FlowDocument.demo();
        FlowDocument copy=FlowDocument.parse(original.json().toString());
        assertEquals(original.id,copy.id);assertEquals(original.nodes,copy.nodes);assertEquals(original.edges,copy.edges);
        for(String id:original.positions.keySet())assertTrue(Arrays.equals(original.positions.get(id),copy.positions.get(id)));
        assertTrue(new Engine().validate(copy.flow()).isEmpty());
    }
    public void testUnknownCapabilitiesAndDanglingEdgesRejected() throws Exception {
        JSONObject unsupported=FlowDocument.demo().json();unsupported.getJSONArray("nodes").getJSONObject(1).put("type","action.legacy.wifi");
        try { FlowDocument.parse(unsupported.toString());fail("Unknown action accepted"); } catch(IllegalArgumentException expected) {}
        JSONObject dangling=FlowDocument.demo().json();dangling.getJSONArray("edges").getJSONObject(0).put("to","missing");
        try { FlowDocument.parse(dangling.toString());fail("Dangling edge accepted"); } catch(JSONException expected) {}
    }
    public void testAtomicStorageRetainsIndependentFlowsAndDelete() throws Exception {
        FlowStore store=new FlowStore(getInstrumentation().getTargetContext());FlowDocument d=FlowDocument.demo();d.name="Integration test "+d.id;
        Set<String> before=new HashSet<>();for(FlowDocument f:store.load())before.add(f.id);
        try {
            store.save(d);d.name="Updated "+d.id;d.revision=2;store.save(d);
            FlowDocument loaded=store.load().stream().filter(f->f.id.equals(d.id)).findFirst().orElseThrow();
            assertEquals(2,loaded.revision);assertEquals(d.name,loaded.name);assertEquals(d.edges,loaded.edges);
        } finally { store.delete(d.id); }
        Set<String> after=new HashSet<>();for(FlowDocument f:store.load())after.add(f.id);assertEquals(before,after);
    }
    public void testRealBatteryAdapterExecutesExactlyOneBranch() throws Exception {
        MainActivity activity=getActivity();AndroidActionHost host=new AndroidActionHost(activity);int battery=host.batteryPercent();assertTrue(battery>=0&&battery<=100);
        RunResult result=new Engine().run(FlowDocument.demo().flow(),host,Cancellation.NEVER);
        assertEquals(RunResult.Status.COMPLETED,result.status());assertEquals(3,result.trace().size());assertEquals("action.log",result.trace().get(2).type());
        assertTrue(result.trace().get(2).message().contains(battery>30?"above":"below"));
    }
    public void testNotificationPermissionDeniedRoutesErrorThenGrantPosts() throws Exception {
        MainActivity activity=getActivity();AndroidActionHost host=new AndroidActionHost(activity);
        NotificationManager nm=activity.getSystemService(NotificationManager.class);
        FlowDocument d=new FlowDocument();Node start=d.add("trigger.manual"), notification=d.add("action.notification"), recovery=d.add("action.log");
        d.nodes.set(2,new Node(recovery.id(),recovery.type(),Map.of("message","Blocked: ${error.code}")));
        d.connect(start.id(),"next",notification.id());d.connect(notification.id(),"error",recovery.id());
        try {
            nm.cancelAll();
            if(Build.VERSION.SDK_INT>=33){
                // AppOps blocks delivery without killing the instrumented process, unlike pm revoke.
                shell("appops set com.ghostmagic.android POST_NOTIFICATION ignore");
                RunResult denied=new Engine().run(d.flow(),host,Cancellation.NEVER);
                assertEquals(RunResult.Status.COMPLETED,denied.status());assertEquals("PERMISSION_REQUIRED",denied.variables().get("error.code"));
                assertEquals("Blocked: PERMISSION_REQUIRED",denied.trace().get(2).message());assertEquals(0,nm.getActiveNotifications().length);
                shell("pm grant com.ghostmagic.android android.permission.POST_NOTIFICATIONS");
                shell("appops set com.ghostmagic.android POST_NOTIFICATION allow");
            }
            RunResult posted=new Engine().run(d.flow(),host,Cancellation.NEVER);assertEquals(RunResult.Status.COMPLETED,posted.status());
            assertEquals(2,posted.trace().size());
            long deadline=System.currentTimeMillis()+3000;
            while(nm.getActiveNotifications().length==0&&System.currentTimeMillis()<deadline)Thread.sleep(25);
            assertEquals(1,nm.getActiveNotifications().length);
        } finally {nm.cancelAll();if(Build.VERSION.SDK_INT>=33)shell("appops set com.ghostmagic.android POST_NOTIFICATION default");}
    }
    public void testBrightnessPermissionAndActualWriteAfterGrant() throws Exception {
        MainActivity activity=getActivity();AndroidActionHost host=new AndroidActionHost(activity);
        int original=Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,100);
        int mode=Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0);
        try {
            shell("appops set com.ghostmagic.android WRITE_SETTINGS deny");
            assertFalse(Settings.System.canWrite(activity));
            try{host.execute("action.brightness",Map.of("percent","60"),Map.of());fail("Missing special access accepted");}
            catch(ActionException expected){assertEquals(ActionException.PERMISSION_REQUIRED,expected.code());}
            shell("appops set com.ghostmagic.android WRITE_SETTINGS allow");
            shell("settings put system screen_brightness_mode 0");
            host.execute("action.brightness",Map.of("percent","60"),Map.of());
            assertEquals(153,Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS));
            shell("settings put system screen_brightness_mode 1");
            try{host.execute("action.brightness",Map.of("percent","60"),Map.of());fail("Adaptive brightness silently overridden");}
            catch(ActionException expected){assertEquals(ActionException.UNSUPPORTED,expected.code());}
        } finally {
            shell("settings put system screen_brightness "+original);shell("settings put system screen_brightness_mode "+mode);
            shell("appops set com.ghostmagic.android WRITE_SETTINGS default");
        }
    }
    public void testOversizedDocumentAndUnknownVersionRejected() throws Exception {
        byte[] oversized=new byte[1024*1024+1];
        try{FlowStore.readLimited(new java.io.ByteArrayInputStream(oversized));fail("Oversized file accepted");}catch(java.io.IOException expected){}
        JSONObject doc=FlowDocument.demo().json().put("schemaVersion",999);
        try{FlowDocument.parse(doc.toString());fail("Unknown schema accepted");}catch(JSONException expected){}
    }
    private String shell(String command) throws Exception {
        try(ParcelFileDescriptor fd=getInstrumentation().getUiAutomation().executeShellCommand(command);
            java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");
        }
    }
    public void testHomeCanOpenGraphAndRunDemoThroughUi() throws Throwable {
        MainActivity activity=getActivity();getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{Button open=findButton(activity.getWindow().getDecorView(),"Open flow");assertNotNull(open);open.performClick();});
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{Button run=findButton(activity.getWindow().getDecorView(),"Run");assertNotNull(run);run.performClick();});
        long end=System.currentTimeMillis()+5000;boolean completed=false;
        while(System.currentTimeMillis()<end&&!completed){Thread.sleep(50);getInstrumentation().waitForIdleSync();android.app.UiAutomation ui=getInstrumentation().getUiAutomation();android.view.accessibility.AccessibilityNodeInfo root=ui.getRootInActiveWindow();completed=root!=null&&!root.findAccessibilityNodeInfosByText("COMPLETED").isEmpty();}
        assertTrue("Execution did not complete through UI",completed);
    }
    private Button findButton(View root,String title) {
        if(root instanceof Button&&(((Button)root).getText().toString().equals(title)||title.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())))return (Button)root;
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){Button found=findButton(g.getChildAt(i),title);if(found!=null)return found;}}
        return null;
    }
}
