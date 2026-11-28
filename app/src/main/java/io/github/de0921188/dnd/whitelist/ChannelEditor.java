package io.github.de0921188.dnd.whitelist;

import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.*;

final class ChannelEditor {
    private final MainActivity ui;
    private final String key,pkg;
    private final int save;
    private final Set<String> chosen, draft=new HashSet<>(), expanded=new HashSet<>();
    private final Map<String,String> names=new LinkedHashMap<>(), parents=new HashMap<>(), states=new HashMap<>();
    private LinearLayout rows;
    private boolean restricted;
    ChannelEditor(MainActivity ui,String key,String pkg,int save,Set<String> chosen,List<String> ids,
                  List<String> labels,List<String> parent,List<String> state) {
        this.ui=ui; this.key=key; this.pkg=pkg; this.save=save; this.chosen=new HashSet<>(chosen);
        for(String opt:ui.options) if(opt.startsWith(key+"=")) draft.add(opt);
        restricted=ui.restricted(key);
        for(int i=0;i<ids.size();i++) {
            String id=ids.get(i); names.put(id,labels.get(i));
            String par=parent==null?null:parent.get(i);
            set(id,"parent",par==null?null:ChannelOptions.encode(par));
            if(par!=null) parents.put(id,par);
            if(state!=null) states.put(id,state.get(i));
        }
        for(String id:chosen) names.putIfAbsent(id,id);
    }
    void show(String focus) {
        LinearLayout content=column();
        Switch scope=new Switch(ui); scope.setChecked(restricted);
        scope.setText(restricted?R.string.only_selected:R.string.all_categories);
        content.addView(scope); rows=column(); content.addView(rows);
        scope.setOnCheckedChangeListener((v,on)->{restricted=on; scope.setText(on?R.string.only_selected:R.string.all_categories); render();});
        if(focus!=null) {
            names.putIfAbsent(focus,focus);
            if(parents.containsKey(focus)) expanded.add(parents.get(focus));
        }
        render();
        ScrollView scroll=new ScrollView(ui); scroll.addView(content);
        new AlertDialog.Builder(ui).setTitle(R.string.channels).setView(scroll)
                .setPositiveButton(save,(d,w)->{
                    if(!ui.canEdit()) return;
                    if(restricted) draft.add(key+"=selected"); else draft.remove(key+"=selected");
                    ui.options.removeIf(o->o.startsWith(key+"=")); ui.options.addAll(draft);
                    ui.updateChannels(key,restricted?chosen:Collections.emptySet());
                }).setNeutralButton(R.string.manual,(d,w)->manual()).setNegativeButton(R.string.cancel,null).show();
        if(focus!=null) details(focus);
    }
    private void manual() {
        EditText input=new EditText(ui); input.setText(String.join("\n",chosen)); input.setMinLines(3);
        new AlertDialog.Builder(ui).setTitle(R.string.manual).setView(input).setPositiveButton(R.string.confirm,(d,w)->{
            chosen.clear(); restricted=true;
            for(String id:input.getText().toString().split("\n")) if(!id.isEmpty()) {chosen.add(id); names.putIfAbsent(id,id);}
            show(null);
        }).setNegativeButton(R.string.cancel,(d,w)->show(null)).show();
    }
    private LinearLayout column() { LinearLayout box=new LinearLayout(ui); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private void render() {
        rows.removeAllViews();
        for(String id:names.keySet()) if(!names.containsKey(parents.get(id))) {
            row(id,false);
            if(parents.containsValue(id)) {
                Button group=new Button(ui); group.setText(R.string.conversations); rows.addView(group);
                group.setOnClickListener(v->{if(!expanded.add(id)) expanded.remove(id); render();});
                if(expanded.contains(id)) for(String child:names.keySet()) if(id.equals(parents.get(child))) row(child,true);
            }
        }
    }
    private void row(String id,boolean child) {
        LinearLayout line=new LinearLayout(ui); rows.addView(line);
        CheckBox check=new CheckBox(ui);
        boolean inherited=child && chosen.contains(parents.get(id));
        String label=names.get(id);
        if(Collections.frequency(names.values(),label)>1) label+="\n"+id;
        check.setText((child?"↳ ":"")+label+(inherited?" · "+ui.getString(R.string.inherit):""));
        check.setChecked(!restricted || inherited || chosen.contains(id)); check.setEnabled(restricted && !inherited);
        check.setOnCheckedChangeListener((b,on)->{if(on) chosen.add(id); else chosen.remove(id); render();});
        line.addView(check,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=new Button(ui); edit.setText("⋯"); edit.setContentDescription(label+" · "+ui.getString(R.string.more));
        edit.setOnClickListener(v->details(id)); line.addView(edit);
        String state=states.get(id);
        if(state!=null) {
            String[] s=state.split(",");
            if(s.length==3) {
                TextView text=new TextView(ui); text.setText(ui.getString(R.string.channel_state,s[0],
                    ui.getString("1".equals(s[1])?R.string.on:R.string.off),ui.getString("1".equals(s[2])?R.string.on:R.string.off)));
                rows.addView(text);
            }
        }
    }
    private void set(String id,String field,String value) {
        String prefix=ChannelOptions.prefix(key,field,id);
        draft.removeIf(o->o.startsWith(prefix));
        if(value!=null) draft.add(prefix+value);
    }
    private Spinner choice(LinearLayout box,String id,String field,int title,int... labels) {
        TextView name=new TextView(ui); name.setText(title); box.addView(name);
        Spinner spinner=new Spinner(ui);
        String[] items=Arrays.stream(labels).mapToObj(ui::getString).toArray(String[]::new);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(ui,android.R.layout.simple_spinner_dropdown_item,items);
        spinner.setAdapter(adapter);
        for(int i=1;i<labels.length;i++) if(draft.contains(ChannelOptions.prefix(key,field,id)+(i-1))) spinner.setSelection(i);
        box.addView(spinner); return spinner;
    }
    private void apply(String id,String field,Spinner control) {
        int pos=control.getSelectedItemPosition(); set(id,field,pos<=0?null:String.valueOf(pos-1));
    }
    private void details(String id) {
        LinearLayout box=column();
        Spinner mode=choice(box,id,"mode",R.string.channels,R.string.inherit,R.string.system_row,R.string.row_wake,R.string.row_edge,R.string.row_dark);
        Spinner sound=choice(box,id,"sound",R.string.allow_sound,R.string.inherit,R.string.off,R.string.on);
        Spinner vib=choice(box,id,"vibration",R.string.allow_vibration,R.string.inherit,R.string.off,R.string.on);
        new AlertDialog.Builder(ui).setTitle(names.get(id)).setView(box).setPositiveButton(R.string.confirm,(d,w)->{
            apply(id,"mode",mode); apply(id,"sound",sound); apply(id,"vibration",vib);
        }).setNeutralButton(R.string.app_notification_settings,(d,w)->{
            try {ui.startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,pkg).putExtra(Settings.EXTRA_CHANNEL_ID,id));}
            catch(android.content.ActivityNotFoundException e){Toast.makeText(ui,R.string.query_unavailable,Toast.LENGTH_LONG).show();}
        }).setNegativeButton(R.string.cancel,null).show();
    }
}
