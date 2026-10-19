package com.ghostmagic.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.ghostmagic.core.*;
import com.ghostmagic.core.Flow;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.text.DateFormat;
import com.ghostmagic.core.legacy.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff10151e,PANEL=0xff202a38,INK=0xffedf4fc,MUTED=0xffa7b5c8,ACCENT=0xff70e4c5;
    private FlowStore store;
    private List<FlowDocument> documents=new ArrayList<>();
    private FlowDocument current;
    private LinearLayout root;
    private GraphView graph;
    private boolean dirty=false, visible=false, running=false;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private Future<?> task;
    private TextView runText;
    private AlertDialog runDialog;
    private RunResult lastResult;
    private String lastFlowName="";
    private String exportText;
    private String legacyExportId;
    private ProgressDialog importProgress;
    private AlertDialog legacyLibraryDialog;
    private String ocrPickerFlow,ocrPickerNode;
    private Bundle ocrPickerConfig,ocrPickerRoutes;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new FlowStore(this); documents=store.load();
        List<String> scheduleErrors=new ScheduleController(this).restoreMissing();
        if(!scheduleErrors.isEmpty())toast("Some schedules were disabled: "+String.join("\n",scheduleErrors));
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);
        if(documents.isEmpty()&&store.loadErrors.isEmpty()) {
            FlowDocument demo=FlowDocument.demo(); try { store.save(demo);documents.add(demo); } catch(Exception e) { error(e); }
        }
        if(state!=null) {
            exportText=state.getString("exportText");
            legacyExportId=state.getString("legacyExportId");
            ocrPickerFlow=state.getString("ocrPickerFlow");ocrPickerNode=state.getString("ocrPickerNode");
            ocrPickerConfig=state.getBundle("ocrPickerConfig");ocrPickerRoutes=state.getBundle("ocrPickerRoutes");
            String draft=state.getString("draft");
            if(draft!=null) try { current=FlowDocument.parse(draft);dirty=state.getBoolean("dirty"); } catch(Exception e) { toast("Could not restore editor: "+e.getMessage()); }
        }
        if(current!=null) editor(); else home();
        if(!store.loadErrors.isEmpty()) new AlertDialog.Builder(this).setTitle("Some saved flows could not be read").setMessage(String.join("\n",store.loadErrors)).setPositiveButton("OK",null).show();
    }
    @Override protected void onResume() { super.onResume();visible=true; }
    @Override protected void onPause() { visible=false;super.onPause(); }
    boolean isVisibleForActions() { return visible&&!isFinishing()&&!isDestroyed(); }
    @Override protected void onStop() { super.onStop(); if(running)cancelled.set(true); }
    @Override protected void onDestroy() { cancelled.set(true);if(task!=null)task.cancel(true);worker.shutdownNow();if(importProgress!=null&&importProgress.isShowing())importProgress.dismiss();super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("exportText",exportText);
        out.putString("legacyExportId",legacyExportId);
        out.putString("ocrPickerFlow",ocrPickerFlow);out.putString("ocrPickerNode",ocrPickerNode);
        out.putBundle("ocrPickerConfig",ocrPickerConfig);out.putBundle("ocrPickerRoutes",ocrPickerRoutes);
        if(current!=null)try { out.putString("draft",current.json().toString());out.putBoolean("dirty",dirty); } catch(Exception ignored) {}
    }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private TextView text(String s,int size,int color) { TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t; }
    private GradientDrawable shape(int color) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private Button button(String title,Runnable action) { Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(ACCENT);b.setBackground(shape(PANEL));b.setPadding(dp(16),0,dp(16),0);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b; }
    private void screen(String eyebrow,String title,String sub) {
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),0,dp(18),0);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                view.setPadding(dp(18)+bars.left,bars.top+dp(10),dp(18)+bars.right,bars.bottom+dp(8));
            }else view.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop()+dp(10),dp(18)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()+dp(8));
            return insets;
        });
        setContentView(root);root.requestApplyInsets();
        TextView e=text(eyebrow,10,ACCENT);e.setLetterSpacing(.16f);root.addView(e);
        TextView heading=text(title,27,INK);heading.setTypeface(null,Typeface.BOLD);root.addView(heading);
        root.addView(text(sub,12,MUTED));
    }
    private void toolbar(String[] labels,Runnable[] actions) {
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);
        for(int i=0;i<labels.length;i++) { Button b=button(labels[i],actions[i]); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.setMarginEnd(dp(6));row.addView(b,lp); }
        sc.addView(row);root.addView(sc);
    }
    private void home() {
        current=null;dirty=false;
        screen("GHOST MAGIC / EXPERIMENTAL 0.4","Your automations","Manual and periodic flows · OCR Text Reader\nAndroid may delay scheduled work. Physical-device validation pending.");
        toolbar(new String[]{"New flow","Import","Legacy","History","Capabilities","Permissions"},new Runnable[]{this::newFlow,this::importFlow,this::legacyLibrary,this::history,this::capabilities,this::permissions});
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();
        TextView intro=text("Build a graph. Follow every decision.",17,INK);list.addView(intro);
        for(FlowDocument d:documents) {
            LinearLayout card=column();card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(shape(PANEL));
            RuntimeDatabase.Schedule schedule=RuntimeDatabase.get(this).schedule(d.id);
            String state=schedule!=null&&schedule.enabled()?"every "+schedule.intervalMinutes()+" min (flexible)":"schedule off";
            card.addView(text(d.name,19,INK));card.addView(text(d.nodes.size()+" nodes  ·  revision "+d.revision+"  ·  "+state,12,MUTED));
            card.addView(button("Open flow",()->{ try { current=FlowDocument.parse(d.json().toString());dirty=false;editor(); } catch(Exception e) { error(e); } }));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);list.addView(card,lp);
        }
        if(documents.isEmpty())list.addView(text("No flows yet. Create one to begin.",16,MUTED));
        if(lastResult!=null)list.addView(button("Last run: "+lastFlowName+" · "+lastResult.status(),()->showResult(lastResult)));
        list.addView(text("Legacy .flows can be archived and inspected; executable conversion is still in development. Event triggers, exact alarms and scripts remain future work.",12,MUTED));
        scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void newFlow() {
        EditText name=new EditText(this);name.setHint("Flow name");name.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Create a flow").setView(name).setPositiveButton("Create",(d,w)->{
            String value=name.getText().toString().trim();if(value.isEmpty()||value.length()>200){toast("Enter a name with 1–200 characters");return;}
            current=new FlowDocument();current.name=value;current.add("trigger.manual");dirty=true;editor();
        }).setNegativeButton("Cancel",null).show();
    }
    private void editor() {
        screen("FLOW STUDIO / "+(current.node(current.startId).type().equals("trigger.periodic")?"PERIODIC":"MANUAL"),current.name,"Tap nodes to configure. Drag to arrange. Schedule controls background execution.");
        toolbar(new String[]{"Flows","Add node","Save","Run","Schedule","History","Nodes","Fit","1:1","Export","Rename","Delete"},new Runnable[]{()->leave(this::home),this::addNode,()->save(true),this::runFlow,this::schedule,this::history,this::nodeList,()->graph.fit(root.getWidth()-root.getPaddingLeft()-root.getPaddingRight()),()->graph.zoom(1),this::exportFlow,this::rename,this::deleteFlow});
        TextView hint=text("Amber = trigger   ·   Violet = condition   ·   Mint = action\nAn unconnected output ends that execution path.",11,MUTED);root.addView(hint);
        ScrollView vertical=new ScrollView(this);HorizontalScrollView horizontal=new HorizontalScrollView(this);
        graph=new GraphView(this,current,new GraphView.Listener(){ public void edit(Node n){editNode(n);}public void moved(){dirty=true;} });
        horizontal.addView(graph);vertical.addView(horizontal);root.addView(vertical,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(text("Local experimental runtime · 1 run at a time · 1,000 steps / 120 s limit",10,MUTED));
    }
    private void addNode() {
        List<Catalog.Capability> choices=new ArrayList<>();for(Catalog.Capability c:Catalog.ALL)if(!c.type().startsWith("trigger"))choices.add(c);
        String[] labels=choices.stream().map(Catalog.Capability::name).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Add capability").setItems(labels,(d,i)->{
            Node previous=current.nodes.get(current.nodes.size()-1), n=current.add(choices.get(i).type());
            boolean hasNext=current.edges.stream().anyMatch(e->e.from().equals(previous.id())&&e.port().equals("next"));
            if(!previous.type().startsWith("condition")&&!hasNext)current.connect(previous.id(),"next",n.id());
            dirty=true;editor();editNode(n);
        }).setNegativeButton("Cancel",null).show();
    }
    private void nodeList(){
        String[] names=new String[current.nodes.size()];
        for(int i=0;i<names.length;i++)names[i]=(i+1)+" · "+Catalog.title(current.nodes.get(i).type());
        new AlertDialog.Builder(this).setTitle("Edit nodes").setItems(names,(d,i)->editNode(current.nodes.get(i))).setNegativeButton("Close",null).show();
    }
    private void editNode(Node node) { editNode(node,null,null); }
    private void editNode(Node node,Bundle draftFields,Bundle draftRoutes) {
        Catalog.Capability cap=Catalog.find(node.type());LinearLayout box=column();box.setPadding(dp(20),dp(4),dp(20),dp(8));
        box.addView(text(cap.description(),13,MUTED));Map<String,EditText> fields=new LinkedHashMap<>();
        for(String key:cap.fields()) { box.addView(text(key,12,ACCENT));EditText input=new EditText(this);input.setText(draftFields==null?node.config().getOrDefault(key,""):draftFields.getString(key,""));input.setTextSize(15);input.setTextColor(INK);input.setSelectAllOnFocus(true);box.addView(input);fields.put(key,input); }
        box.addView(text("Output routes",16,INK));
        String[] ports=node.type().startsWith("condition")?new String[]{"true","false","error"}:new String[]{"next","error"};
        List<Node> targets=new ArrayList<>();for(Node n:current.nodes)if(!n.type().startsWith("trigger"))targets.add(n);
        List<String> targetLabels=new ArrayList<>();targetLabels.add("End this path");
        for(Node n:targets)targetLabels.add((current.nodes.indexOf(n)+1)+" · "+Catalog.title(n.type()));
        Map<String,Spinner> selectors=new LinkedHashMap<>();
        for(String port:ports) {
            box.addView(text(port.toUpperCase(Locale.ROOT),11,ACCENT));Spinner spinner=new Spinner(this);
            spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,targetLabels));
            for(Edge edge:current.edges)if(edge.from().equals(node.id())&&edge.port().equals(port))for(int i=0;i<targets.size();i++)if(targets.get(i).id().equals(edge.to()))spinner.setSelection(i+1);
            if(draftRoutes!=null){String target=draftRoutes.getString(port,"");spinner.setSelection(0);for(int i=0;i<targets.size();i++)if(targets.get(i).id().equals(target))spinner.setSelection(i+1);}
            box.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));selectors.put(port,spinner);
        }
        box.addView(text("Use ${variable} in configuration. Missing variables become a routable error. Self-links and loops stop at the execution budget.",11,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(Catalog.title(node.type())).setView(scroll).setPositiveButton("Apply",null).setNegativeButton("Cancel",null)
            .setNeutralButton(node.type().startsWith("trigger")?"":"Remove",null).create();
        if(node.type().equals("action.ocr.read")) {
            box.addView(button("Choose OCR image",()->{
                ocrPickerFlow=current.id;ocrPickerNode=node.id();ocrPickerConfig=new Bundle();ocrPickerRoutes=new Bundle();
                fields.forEach((k,input)->ocrPickerConfig.putString(k,input.getText().toString()));
                selectors.forEach((port,selector)->{int i=selector.getSelectedItemPosition();ocrPickerRoutes.putString(port,i==0?"":targets.get(i-1).id());});
                Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try{startActivityForResult(picker,104);dialog.dismiss();}catch(Exception ex){ocrPickerFlow=null;ocrPickerNode=null;ocrPickerConfig=null;ocrPickerRoutes=null;error(ex);}
            }));
            box.addView(button("Screen OCR access",this::ocrAccess));
        }
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
                Map<String,String> config=new LinkedHashMap<>();fields.forEach((k,input)->config.put(k,input.getText().toString()));
                Node replacement=new Node(node.id(),node.type(),config);int index=current.nodes.indexOf(node);current.nodes.set(index,replacement);
                List<String> errors=new Engine().validate(current.flow());
                if(!errors.isEmpty()){current.nodes.set(index,node);toast(String.join("\n",errors));return;}
                selectors.forEach((port,selector)->{int i=selector.getSelectedItemPosition();current.connect(node.id(),port,i==0?null:targets.get(i-1).id());});
                dirty=true;dialog.dismiss();editor();
            });
            if(!node.type().startsWith("trigger"))dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(btn->{current.remove(node.id());dirty=true;dialog.dismiss();editor();});
        });dialog.show();
    }
    private boolean save(boolean notify) {
        int previousRevision=current.revision;
        boolean committed=false;
        synchronized(ExecutionCoordinator.LOCK){try {
            RuntimeDatabase.Schedule row=RuntimeDatabase.get(this).schedule(current.id);
            if(row!=null&&row.enabled()){
                List<String> blocked=ScheduleController.backgroundErrors(current);
                if(!blocked.isEmpty())throw new IllegalArgumentException("Disable the schedule before saving a foreground-only flow.\n"+String.join("\n",blocked));
            }
            if(dirty&&documents.stream().anyMatch(d->d.id.equals(current.id)))current.revision++;
            store.save(current);committed=true;dirty=false;documents=store.load();
            try{new ScheduleController(this).refresh(current);}catch(Exception e){new ScheduleController(this).disable(current.id);toast("Flow saved, schedule disabled: "+e.getMessage());}
            if(notify)toast("Flow saved");return true;
        }catch(Exception e){if(!committed)current.revision=previousRevision;error(e);return false;}}
    }
    private void rename() {
        EditText input=new EditText(this);input.setText(current.name);input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Rename flow").setView(input).setPositiveButton("Apply",(d,w)->{
            String name=input.getText().toString().trim();if(name.isEmpty()||name.length()>200){toast("Enter a name with 1–200 characters");return;}current.name=name;dirty=true;editor();
        }).setNegativeButton("Cancel",null).show();
    }
    private void leave(Runnable next) {
        if(!dirty){next.run();return;}
        new AlertDialog.Builder(this).setTitle("Save changes?").setMessage("This flow has unsaved edits.")
            .setPositiveButton("Save",(d,w)->{if(save(false))next.run();}).setNegativeButton("Discard",(d,w)->next.run()).setNeutralButton("Keep editing",null).show();
    }
    private void deleteFlow() {
        new AlertDialog.Builder(this).setTitle("Delete this flow?").setMessage(current.name+"\nIts schedule will be disabled. Run history is kept.").setPositiveButton("Delete",(d,w)->{new ScheduleController(this).disable(current.id);store.delete(current.id);documents=store.load();home();}).setNegativeButton("Cancel",null).show();
    }
    private void schedule(){
        RuntimeDatabase.Schedule row=RuntimeDatabase.get(this).schedule(current.id);
        Node trigger=current.node(current.startId);
        LinearLayout box=column();box.setPadding(dp(20),dp(4),dp(20),dp(8));
        box.addView(text(row!=null&&row.enabled()?"Enabled · every "+row.intervalMinutes()+" minutes":"Disabled",16,ACCENT));
        box.addView(text("Android runs periodic work when resources and quotas allow. Minimum 15 minutes; not an exact clock time. Scheduling survives normal reboot. Force-stop prevents delivery until you open the app again.",13,MUTED));
        box.addView(text("Interval in minutes (15–10080)",12,ACCENT));
        EditText interval=new EditText(this);interval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        interval.setText(trigger.type().equals("trigger.periodic")?trigger.config().get("intervalMinutes"):"15");box.addView(interval);
        box.addView(text("Only the saved revision runs. Imports stay inactive. A stopped or interrupted occurrence is not automatically replayed. Wi-Fi panel actions require a manual flow.",12,MUTED));
        Button manual=button("Use manual trigger",()->{});box.addView(manual);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Periodic schedule").setView(scroll).setPositiveButton(row!=null&&row.enabled()?"Update":"Enable",null)
            .setNeutralButton("Disable",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
                int minutes;
                try{minutes=Integer.parseInt(interval.getText().toString());if(minutes<15||minutes>10080)throw new NumberFormatException();}
                catch(NumberFormatException e){toast("Choose 15–10080 minutes");return;}
                int index=current.nodes.indexOf(trigger);boolean wasDirty=dirty;
                current.nodes.set(index,new Node(trigger.id(),"trigger.periodic",Map.of("intervalMinutes",""+minutes)));dirty=true;
                List<String> errors=ScheduleController.backgroundErrors(current);
                if(!errors.isEmpty()){current.nodes.set(index,trigger);dirty=wasDirty;toast(String.join("\n",errors));return;}
                if(!save(false)){current.nodes.set(index,trigger);dirty=wasDirty;return;}
                try{
                    RuntimeDatabase.Schedule saved=RuntimeDatabase.get(this).schedule(current.id);
                    if(saved==null||!saved.enabled())new ScheduleController(this).enable(current);
                    toast("Periodic schedule enabled; Android may delay each occurrence");dialog.dismiss();editor();
                }catch(Exception e){dialog.dismiss();editor();error(e);}
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(b->{new ScheduleController(this).disable(current.id);dialog.dismiss();editor();toast("Schedule disabled; active run asked to stop");});
            manual.setOnClickListener(b->{new ScheduleController(this).disable(current.id);current.nodes.set(current.nodes.indexOf(trigger),new Node(trigger.id(),"trigger.manual",Map.of()));dirty=true;if(save(false)){dialog.dismiss();editor();}});
        });dialog.show();
    }
    private void history(){
        LinearLayout list=column();list.setPadding(dp(20),dp(8),dp(20),dp(8));
        list.addView(text("Latest 100 runs · stored on this device\nInterrupted runs may have partial effects and are never replayed automatically.",12,MUTED));
        if(ExecutionCoordinator.isBusy())list.addView(button("Stop active run",()->{ExecutionCoordinator.cancelCurrent();toast("Stop requested");}));
        List<RuntimeDatabase.Run> runs=RuntimeDatabase.get(this).runs();
        if(runs.isEmpty())list.addView(text("No run history yet.",16,INK));
        DateFormat format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT);
        for(RuntimeDatabase.Run run:runs){
            list.addView(button(run.name()+" · "+run.status(),()->historyDetail(run)));
            list.addView(text(format.format(new Date(run.started()))+" · "+run.source()+" · rev "+run.revision(),11,MUTED));
        }
        ScrollView sc=new ScrollView(this);sc.addView(list);new AlertDialog.Builder(this).setTitle("Run history").setView(sc).setPositiveButton("Close",null).setNeutralButton("Refresh",(d,w)->history()).show();
    }
    private void historyDetail(RuntimeDatabase.Run run){
        StringBuilder body=new StringBuilder(run.status()+" · "+run.source()+" · revision "+run.revision()+"\n"+Objects.toString(run.message(),"Execution in progress")+"\n\n");
        if(run.errorCode()!=null)body.append(run.errorCode()).append("\n\n");
        try{
            org.json.JSONArray trace=new org.json.JSONArray(run.trace()==null?"[]":run.trace());
            for(int i=0;i<trace.length();i++){org.json.JSONObject e=trace.getJSONObject(i);body.append(i+1).append(". ").append(Catalog.title(e.getString("type"))).append(" → ").append(e.getString("port")).append("\n").append(e.getString("message")).append("\n\n");}
            if(run.variables()!=null)body.append("Variables\n").append(new org.json.JSONObject(run.variables()).toString(2));
        }catch(Exception e){body.append("Stored trace could not be read: ").append(e.getMessage());}
        TextView t=text(body.toString(),13,INK);t.setTextIsSelectable(true);t.setPadding(dp(20),dp(8),dp(20),dp(8));ScrollView sc=new ScrollView(this);sc.addView(t);
        new AlertDialog.Builder(this).setTitle(run.name()).setView(sc).setPositiveButton("Close",null).show();
    }
    private void runFlow() {
        if(running||ExecutionCoordinator.isBusy()){toast("A flow is already running; check History");return;}
        if(!save(false))return;
        FlowDocument snapshot;try{snapshot=FlowDocument.parse(current.json().toString());}catch(Exception e){error(e);return;}
        lastFlowName=current.name;cancelled.set(false);running=true;
        runText=text("Executing "+current.name+"…\n\nThe run stops when this app is no longer visible. Wi-Fi panel actions are user-assisted; opening the panel is not a Wi-Fi state change.",14,INK);runText.setPadding(dp(20),dp(12),dp(20),dp(12));
        ScrollView traceScroll=new ScrollView(this);traceScroll.addView(runText);
        runDialog=new AlertDialog.Builder(this).setTitle("Execution trace").setView(traceScroll).setNegativeButton("Stop",null).setCancelable(false).create();runDialog.show();
        runDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{cancelled.set(true);runText.setText("Stopping…");});
        task=worker.submit(()->{
            RunResult result=ExecutionCoordinator.execute(this,snapshot,"manual",cancelled,new AndroidActionHost(this),()->{});
            runOnUiThread(()->{running=false;lastResult=result;if(isDestroyed()||isFinishing())return;runText.setText(formatResult(result));runDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("Close");runDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->runDialog.dismiss());});
        });
    }
    private String formatResult(RunResult result) {
        StringBuilder out=new StringBuilder(result.status()+"\n"+result.message()+"\n\n");int i=0;
        for(RunResult.TraceEntry e:result.trace())out.append(++i).append(". ").append(Catalog.title(e.type())).append(" → ").append(e.port()).append("\n").append(e.message()).append("\n\n");
        out.append("Variables\n");result.variables().forEach((k,v)->out.append(k).append(" = ").append(v).append('\n'));
        return out.toString();
    }
    private void showResult(RunResult result) { TextView t=text(formatResult(result),13,INK);t.setPadding(dp(20),dp(8),dp(20),dp(8));t.setTextIsSelectable(true);ScrollView sc=new ScrollView(this);sc.addView(t);new AlertDialog.Builder(this).setTitle(lastFlowName).setView(sc).setPositiveButton("Close",null).show(); }
    private void importFlow() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,100);
    }
    private void importDocument(Uri uri){
        ProgressDialog progress=new ProgressDialog(this);importProgress=progress;progress.setMessage("Reading and validating document…");progress.setCancelable(false);progress.show();
        String name=sourceName(uri);
        task=worker.submit(()->{
            try{
                byte[] bytes;try(InputStream in=getContentResolver().openInputStream(uri)){bytes=FlowStore.readLimitedBytes(in);}
                String prefix=new String(bytes,0,Math.min(bytes.length,64),StandardCharsets.UTF_8).replace("\uFEFF","").trim();
                if(prefix.startsWith("{")){
                    FlowDocument imported=FlowDocument.parse(new String(bytes,StandardCharsets.UTF_8));
                    imported.id=UUID.randomUUID().toString();imported.name=imported.name.length()>189?imported.name.substring(0,189)+" (imported)":imported.name+" (imported)";
                    imported.revision=1;store.save(imported);
                    runOnUiThread(()->{if(isDestroyed()||isFinishing())return;progress.dismiss();documents=store.load();current=imported;dirty=false;editor();toast("Imported; no schedule activated");});
                }else{
                    LegacyStore.Archive archived=new LegacyStore(this).save(bytes,name);
                    runOnUiThread(()->{if(isDestroyed()||isFinishing())return;progress.dismiss();showLegacy(archived);});
                }
            }catch(Exception e){runOnUiThread(()->{if(isDestroyed()||isFinishing())return;progress.dismiss();error(e);});}
        });
    }
    private String sourceName(Uri uri){
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}return "Imported legacy export.flows";
    }
    private void legacyLibrary(){
        List<LegacyStore.Summary> archives=new LegacyStore(this).list();LinearLayout list=column();list.setPadding(dp(20),dp(8),dp(20),dp(8));
        list.addView(text("Read-only legacy archives. Originals are retained unchanged. Importing does not create runnable flows or active schedules.",13,MUTED));
        if(archives.isEmpty())list.addView(text("Use Import to select an Automagic .flows export.",16,INK));
        for(LegacyStore.Summary s:archives){list.addView(button(s.sourceName(),()->loadLegacy(s.id())));String detail=s.flows()<0?"Metadata unavailable; original retained":s.flows()+" flows · "+s.errors()+" structural errors";list.addView(text(s.bytes()+" bytes · "+detail+"\nSHA-256 "+s.id().substring(0,16)+"…",11,MUTED));}
        if(legacyLibraryDialog!=null&&legacyLibraryDialog.isShowing())legacyLibraryDialog.dismiss();
        ScrollView sc=new ScrollView(this);sc.addView(list);legacyLibraryDialog=new AlertDialog.Builder(this).setTitle("Legacy library").setView(sc).setPositiveButton("Close",null).setNeutralButton("Refresh",(dialog,which)->legacyLibrary()).show();
    }
    private void loadLegacy(String id){
        task=worker.submit(()->{try{LegacyStore.Archive archive=new LegacyStore(this).read(id);runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())showLegacy(archive);});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())error(e);});}});
    }
    private void showLegacy(LegacyStore.Archive archive){
        LegacyDocument d=archive.document();LinearLayout list=column();list.setPadding(dp(20),dp(8),dp(20),dp(8));
        list.addView(text("REVIEW ONLY · NOT EXECUTABLE",13,ACCENT));
        list.addView(text(archive.summary().sourceName()+"\n"+d.flows().size()+" flows · "+d.definitions().size()+" shared objects\n"+d.count(LegacyDocument.Severity.ERROR)+" errors · "+d.count(LegacyDocument.Severity.WARNING)+" review warnings",14,INK));
        list.addView(text("Original enabled flags are informational. No action or schedule was activated. Partial analogues require manual configuration and semantic verification.",12,MUTED));
        list.addView(button("Migration report",()->plainText("Legacy migration report",LegacyStore.report(archive))));
        list.addView(button("Inspect XML tree",()->inspectLegacyElement(d.root())));
        for(LegacyDocument.LegacyFlow flow:d.flows().subList(0,Math.min(100,d.flows().size())))list.addView(button("Flow: "+flow.name(),()->inspectLegacyFlow(d,flow)));
        if(d.flows().size()>100)list.addView(text("First 100 flows shown; export the report for the complete inventory.",12,MUTED));
        list.addView(button("Export original .flows",()->legacyExport(archive.summary().id(),false)));
        list.addView(button("Export report",()->legacyExport(archive.summary().id(),true)));
        Button remove=button("Remove archive",()->{});list.addView(remove);
        ScrollView sc=new ScrollView(this);sc.addView(list);AlertDialog review=new AlertDialog.Builder(this).setTitle("Legacy review").setView(sc).setPositiveButton("Close",null).show();
        remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Remove this local archive?").setMessage("Export the original first if you need another copy. Runnable flows and schedules are unaffected.").setPositiveButton("Remove",(dialog,which)->{new LegacyStore(this).delete(archive.summary().id());review.dismiss();if(legacyLibraryDialog!=null&&legacyLibraryDialog.isShowing())legacyLibrary();toast("Archive removed from library");}).setNegativeButton("Cancel",null).show());
    }
    private void inspectLegacyFlow(LegacyDocument d,LegacyDocument.LegacyFlow flow){
        LinearLayout list=column();list.setPadding(dp(20),dp(8),dp(20),dp(8));
        list.addView(text("Read-only graph inventory\nPolicy: "+flow.policy()+" · original enabled: "+flow.originallyEnabled()+"\nNo executable translation produced.",13,MUTED));
        for(LegacyDocument.Container c:flow.containers().subList(0,Math.min(200,flow.containers().size()))){
            list.addView(button(c.id()+" · "+c.kind()+" · "+c.references(),()->inspectLegacyElement(c.element())));
            for(String ref:c.references()){
                List<LegacyDocument.Definition> definitions=d.resolve(c.kind(),ref);
                if(definitions.isEmpty())list.addView(text("Missing shared object: "+ref,12,0xfff58e95));
                for(LegacyDocument.Definition def:definitions)list.addView(button(ref+" → "+def.type(),()->inspectLegacyElement(def.element())));
            }
        }
        list.addView(text("Connections",17,ACCENT));
        for(LegacyDocument.Connection edge:flow.connections().subList(0,Math.min(200,flow.connections().size())))list.addView(button(edge.from()+" —"+edge.type()+"→ "+edge.to(),()->inspectLegacyElement(edge.element())));
        if(flow.containers().size()>200||flow.connections().size()>200)list.addView(text("Preview limited to 200 containers/connections. Export the report for complete data.",12,MUTED));
        ScrollView sc=new ScrollView(this);sc.addView(list);new AlertDialog.Builder(this).setTitle(flow.name()).setView(sc).setPositiveButton("Close",null).show();
    }
    private void inspectLegacyElement(LegacyDocument.Element element){
        LinearLayout list=column();list.setPadding(dp(20),dp(8),dp(20),dp(8));
        list.addView(text("Line "+element.line()+"\nAttributes: "+preview(element.attributes().toString(),8000),12,ACCENT));
        TextView content=text(preview(element.text(),16000),13,INK);content.setTextIsSelectable(true);list.addView(content);
        for(LegacyDocument.Element child:element.children().subList(0,Math.min(100,element.children().size())))list.addView(button(child.name()+" · line "+child.line(),()->inspectLegacyElement(child)));
        if(element.children().size()>100)list.addView(text("First 100 child elements shown. Original archive retains every element.",12,MUTED));
        ScrollView sc=new ScrollView(this);sc.addView(list);new AlertDialog.Builder(this).setTitle("XML: "+element.name()).setView(sc).setPositiveButton("Close",null).show();
    }
    private String preview(String value,int limit){return value.length()<=limit?value:value.substring(0,limit)+"\n[Preview truncated; original archive retains full content]";}
    private void plainText(String title,String content){TextView t=text(preview(content,100000),13,INK);t.setTextIsSelectable(true);t.setPadding(dp(20),dp(8),dp(20),dp(8));ScrollView sc=new ScrollView(this);sc.addView(t);new AlertDialog.Builder(this).setTitle(title).setView(sc).setPositiveButton("Close",null).show();}
    private void legacyExport(String id,boolean report){
        legacyExportId=id;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(report?"text/plain":"application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,report?"ghostmagic-legacy-report.txt":"automagic-original.flows");startActivityForResult(intent,report?103:102);
    }
    private void exportFlow() {
        if(!save(false))return;
        try { exportText=current.json().toString(2);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,current.name.replaceAll("[^\\p{L}\\p{N}._ -]","_")+".ghostflow.json");startActivityForResult(i,101); } catch(Exception e){error(e);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==104){
            String flow=ocrPickerFlow,nodeId=ocrPickerNode;Bundle config=ocrPickerConfig,routes=ocrPickerRoutes;
            ocrPickerFlow=null;ocrPickerNode=null;ocrPickerConfig=null;ocrPickerRoutes=null;
            if(current==null||!current.id.equals(flow)||nodeId==null||config==null)return;
            Node node=current.nodes.stream().filter(n->n.id().equals(nodeId)).findFirst().orElse(null);if(node==null)return;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null){
                Uri image=data.getData();
                try{if(!"content".equals(image.getScheme()))throw new IllegalArgumentException("Choose a content image");
                    getContentResolver().takePersistableUriPermission(image,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    config.putString("source","image");config.putString("imageUri",image.toString());
                }catch(Exception e){toast("Image access could not be retained: "+e.getMessage());}
            }
            editNode(node,config,routes);return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try {
            if(request==100) {
                importDocument(data.getData());
            } else if(request==101&&exportText!=null) {
                try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("Cannot open destination");out.write(exportText.getBytes(StandardCharsets.UTF_8));}exportText=null;toast("Flow exported");
            } else if((request==102||request==103)&&legacyExportId!=null){
                String id=legacyExportId;Uri destination=data.getData();legacyExportId=null;
                task=worker.submit(()->{try{LegacyStore legacy=new LegacyStore(this);byte[] bytes=request==102?legacy.original(id):LegacyStore.report(legacy.read(id)).getBytes(StandardCharsets.UTF_8);try(OutputStream out=getContentResolver().openOutputStream(destination,"wt")){if(out==null)throw new IOException("Cannot open destination");out.write(bytes);}runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())toast("Legacy document exported");});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())error(e);});}});
            }
        } catch(Exception e){error(e);}
    }
    private void ocrAccess(){
        new AlertDialog.Builder(this).setTitle("Screen OCR access")
            .setMessage(getString(R.string.ocr_accessibility_description)+"\n\nScreen capture requires Android 11 or newer. Locked or protected screens may reject capture. Image OCR works without this access.")
            .setPositiveButton("Open Accessibility settings",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNegativeButton("Close",null).show();
    }
    private void permissions() {
        NotificationManager nm=getSystemService(NotificationManager.class);
        String notification=nm.areNotificationsEnabled()?"Granted":"Missing";
        String brightness=Settings.System.canWrite(this)?"Granted":"Missing";
        LinearLayout access=column();access.setPadding(dp(20),0,dp(20),0);access.addView(button("Screen OCR access",this::ocrAccess));
        new AlertDialog.Builder(this).setTitle("Capability permissions").setView(access).setMessage("Notifications: "+notification+"\nModify system settings: "+brightness+"\n\nOnly grant access for capabilities you choose. Runtime checks report missing access on the affected node.")
            .setPositiveButton("Notifications",(d,w)->{
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},200);
                else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            }).setNeutralButton("Brightness access",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Close",null).show();
    }
    private void capabilities() {
        LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),dp(8));
        box.addView(text("Implemented adapters",20,INK));
        for(Catalog.Capability c:Catalog.ALL){box.addView(text(c.name(),16,ACCENT));box.addView(text(c.description(),13,MUTED));}
        box.addView(text("Planned, not implemented",20,INK));
        box.addView(text("Legacy .flows archive review is available in Legacy. Executable migration, event triggers, exact alarms, broader accessibility actions, notification listener, HTTP, files, Bluetooth control and optional privileged bridges need further work.",13,MUTED));
        ScrollView sc=new ScrollView(this);sc.addView(box);new AlertDialog.Builder(this).setTitle("Compatibility catalog").setView(sc).setPositiveButton("Close",null).show();
    }
    @Override public void onBackPressed(){if(running){cancelled.set(true);return;}if(current!=null)leave(this::home);else super.onBackPressed();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("Could not complete action").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
}
