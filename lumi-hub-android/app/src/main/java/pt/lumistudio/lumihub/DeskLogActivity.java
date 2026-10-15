package pt.lumistudio.lumihub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Histórico da DESK guardado no tablet, com exportação explícita. */
public final class DeskLogActivity extends Activity {
    private static final int EXPORT_TEXT=781;
    private TextView logView,summary;
    private SharedPreferences prefs;
    private TextView text(String s,int size){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);
        v.setTextColor(Color.WHITE);v.setPadding(8,10,8,10);return v;
    }
    private void action(LinearLayout parent,String title,Runnable callback){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);
        parent.addView(b);b.setOnClickListener(v->callback.run());
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("hub",MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,18,18,30);
        root.setBackgroundColor(0xff122033);scroll.addView(root);setContentView(scroll);
        root.addView(text("LUMI DESK · Diagnóstico",23));
        summary=text("",14);root.addView(summary);
        root.addView(text("O histórico inclui eventos da ponte HUB/DESK e, quando autorizado, " +
            "perguntas reconhecidas e respostas da LUMI. Não contém áudio nem pensamentos internos " +
            "do modelo. As sessões no XiaoZhi público não são recolhidas.",13));
        Switch toggle=new Switch(this);toggle.setText("Guardar texto das conversas");
        toggle.setTextColor(Color.WHITE);toggle.setPadding(10,14,10,14);
        toggle.setChecked(prefs.getBoolean("desk_log_conversations",true));
        toggle.setOnCheckedChangeListener((button,checked)->{
            prefs.edit().putBoolean("desk_log_conversations",checked).apply();
            DeskLog.event(this,"PRIVACIDADE",checked?
                "Registo de texto ativado":"Registo de texto desativado");
            refresh();
        });
        root.addView(toggle);
        action(root,"Atualizar histórico",this::refresh);
        action(root,"Exportar registo para ficheiro",()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE,"LUMI-DESK-Diagnostico.txt");
            startActivityForResult(i,EXPORT_TEXT);
        });
        action(root,"Apagar histórico",()->new AlertDialog.Builder(this)
            .setTitle("Apagar o histórico local?")
            .setMessage("Esta ação apaga eventos e textos guardados no tablet. Não apaga memórias.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Apagar",(dialog,which)->{DeskLog.clear(this);refresh();})
            .show());
        action(root,"Voltar",this::finish);
        logView=text("",12);logView.setTextIsSelectable(true);root.addView(logView);
        refresh();
    }
    private void refresh(){
        summary.setText("Central: "+(prefs.getBoolean("service_running",false)?"ativa":"parada")
            +" · Ponte: "+prefs.getString("desk_gateway_state","por iniciar")
            +"\nAzure: "+(LumiSecrets.get(this,"azure").isEmpty()?"por configurar":"configurado")
            +" · OpenAI: "+(LumiSecrets.get(this,"openai").isEmpty()?"por configurar":"configurado"));
        String content=DeskLog.contents(this);
        if(content.length()>25000)content="[… eventos anteriores disponíveis na exportação …]\n"
            +content.substring(content.length()-25000);
        logView.setText(content);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=EXPORT_TEXT||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
            if(out==null)throw new Exception("Ficheiro inacessível");
            out.write(DeskLog.contents(this).getBytes(StandardCharsets.UTF_8));
            new AlertDialog.Builder(this).setMessage("Histórico exportado. Revê o texto antes de partilhar.")
                .setPositiveButton("OK",null).show();
        }catch(Exception e){
            new AlertDialog.Builder(this).setMessage("Não foi possível exportar o histórico.")
                .setPositiveButton("OK",null).show();
        }
    }
}
