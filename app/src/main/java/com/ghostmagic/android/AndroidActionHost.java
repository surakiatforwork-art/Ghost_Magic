package com.ghostmagic.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import com.ghostmagic.core.*;
import java.util.Map;
import java.util.concurrent.*;

final class AndroidActionHost implements ActionHost {
    private final MainActivity activity;
    private final Context context;
    AndroidActionHost(Context context) { this.context=context.getApplicationContext();this.activity=context instanceof MainActivity?(MainActivity)context:null; }
    @Override public String readText(Map<String,String> config, Cancellation cancellation) throws ActionException { return new OcrReader(context).read(config,cancellation); }
    @Override public int batteryPercent() throws ActionException {
        Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery==null) throw new ActionException(ActionException.UNSUPPORTED,"Battery state unavailable");
        int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1), scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        if(level<0||scale<=0) throw new ActionException(ActionException.UNSUPPORTED,"Device did not report a battery percentage");
        return Math.round(100f*level/scale);
    }
    @Override public void execute(String type, Map<String,String> config, Map<String,String> variables) throws ActionException {
        try {
            switch(type) {
                case "action.log": break;
                case "action.notification":
                    NotificationManager nm=context.getSystemService(NotificationManager.class);
                    if((Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)||!nm.areNotificationsEnabled())
                        throw new ActionException(ActionException.PERMISSION_REQUIRED,"Enable notifications in Permissions, then run again");
                    NotificationChannel channel=new NotificationChannel("flow_results","Flow notifications",NotificationManager.IMPORTANCE_DEFAULT);
                    nm.createNotificationChannel(channel);
                    if(nm.getNotificationChannel("flow_results").getImportance()==NotificationManager.IMPORTANCE_NONE)
                        throw new ActionException(ActionException.PERMISSION_REQUIRED,"The Flow notifications channel is disabled");
                    Intent open=new Intent(context,MainActivity.class);
                    PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                    nm.notify((int)(System.nanoTime()&0x7fffffff),new Notification.Builder(context,"flow_results")
                        .setSmallIcon(R.drawable.ic_ghost).setContentTitle(required(config,"title"))
                        .setContentText(required(config,"message")).setStyle(new Notification.BigTextStyle().bigText(required(config,"message")))
                        .setContentIntent(pi).setAutoCancel(true).build());
                    break;
                case "action.brightness":
                    if(!Settings.System.canWrite(context)) throw new ActionException(ActionException.PERMISSION_REQUIRED,"Grant Modify system settings from Permissions");
                    if(Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0)!=Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        throw new ActionException(ActionException.UNSUPPORTED,"Turn off adaptive brightness first; this action does not change that preference");
                    int percent;
                    try { percent=Integer.parseInt(required(config,"percent")); } catch(NumberFormatException e) { throw new ActionException(ActionException.INVALID_CONFIG,"Brightness must be a whole percentage"); }
                    if(percent<0||percent>100) throw new ActionException(ActionException.INVALID_CONFIG,"Brightness must be 0–100");
                    if(!Settings.System.putInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,Math.max(1,Math.round(percent*255f/100))))
                        throw new ActionException(ActionException.EXECUTION_FAILED,"Android rejected the brightness change");
                    break;
                case "action.wifi.panel":
                    if(activity==null||!activity.isVisibleForActions()) throw new ActionException(ActionException.UNSUPPORTED,"Wi-Fi controls require the visible editor");
                    CompletableFuture<Void> opened=new CompletableFuture<>();
                    activity.runOnUiThread(()->{
                        try {
                            if(opened.isDone())return;
                            if(activity==null||!activity.isVisibleForActions()) throw new IllegalStateException("Editor is no longer visible");
                            Intent panel=new Intent(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS);
                            activity.startActivity(panel); opened.complete(null);
                        } catch(Exception e) { opened.completeExceptionally(e); }
                    });
                    try { opened.get(3,TimeUnit.SECONDS); }
                    catch(InterruptedException e) { opened.cancel(false);Thread.currentThread().interrupt(); throw new ActionException(ActionException.EXECUTION_FAILED,"Cancelled while opening controls"); }
                    catch(Exception e) { opened.cancel(false);throw new ActionException(ActionException.UNSUPPORTED,"Wi-Fi controls could not be opened: "+e.getMessage()); }
                    break;
                default: throw new ActionException(ActionException.UNSUPPORTED,"No Android adapter for "+type);
            }
        } catch(SecurityException e) { throw new ActionException(ActionException.PERMISSION_REQUIRED,e.getMessage()); }
        catch(IllegalArgumentException e) { throw new ActionException(ActionException.INVALID_CONFIG,e.getMessage()); }
    }
    private String required(Map<String,String> c,String k) throws ActionException { String s=c.get(k); if(s==null) throw new ActionException(ActionException.INVALID_CONFIG,"Missing "+k); return s; }
}
