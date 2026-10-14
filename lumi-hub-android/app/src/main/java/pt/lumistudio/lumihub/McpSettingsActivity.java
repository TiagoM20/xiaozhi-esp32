package pt.lumistudio.lumihub;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Configuração MCP isolada, não confundir com a ligação de áudio local. */
public final class McpSettingsActivity extends Activity {
    private SharedPreferences prefs;
    private TextView info;
    private TextView text(String s,int size){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);
        v.setTextColor(Color.WHITE);v.setPadding(8,10,8,10);return v;
    }
    private void action(LinearLayout parent,String title,Runnable callback){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);
        parent.addView(b);b.setOnClickListener(v->callback.run());
    }
    private void restartService(){
        Intent service=new Intent(this,HubService.class);
        boolean running=prefs.getBoolean("service_running",false);
        if(running){
            stopService(service);
            try{
                if(Build.VERSION.SDK_INT>=26)startForegroundService(service);
                else startService(service);
            }catch(Exception e){info.setText("Não foi possível reiniciar o serviço.");}
        }
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs=getSharedPreferences("hub",MODE_PRIVATE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(20,20,20,28);root.setBackgroundColor(0xff122033);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        scroll.addView(root);setContentView(scroll);
        root.addView(text("LUMI · Integração MCP",23));
        root.addView(text("O MCP permite ao agente XiaoZhi aceder às ferramentas " +
            "autorizadas, como memórias e comandos da casa. Não configura a voz Raquel.",14));
        info=text("Estado: "+prefs.getString("mcp_state","Por configurar"),14);root.addView(info);
        EditText endpoint=new EditText(this);endpoint.setSingleLine(true);
        endpoint.setTextColor(Color.WHITE);endpoint.setHintTextColor(0xffabb4c5);
        endpoint.setHint("Colar novo endereço MCP privado");
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(endpoint);
        root.addView(text(prefs.getString("mcp_endpoint","").isEmpty()?
            "Sem endereço guardado.":"Existe um endereço guardado; não é apresentado por segurança.",13));
        action(root,"Guardar ligação MCP",()->{
            String url=endpoint.getText().toString().trim();
            if(!XiaozhiMcpBridge.validEndpoint(url)){
                info.setText("Endereço inválido. Usa o endereço MCP do teu agente.");return;
            }
            prefs.edit().putString("mcp_endpoint",url)
                .putString("mcp_state","A iniciar ligação").apply();
            endpoint.setText("");restartService();info.setText("Ligação guardada.");
            DeskLog.event(this,"MCP","Definição de ligação MCP atualizada");
        });
        action(root,"Remover ligação MCP",()->{
            new android.app.AlertDialog.Builder(this).setTitle("Remover ligação MCP?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Remover",(d,w)->{
                    prefs.edit().remove("mcp_endpoint").putString("mcp_state","Por configurar").apply();
                    restartService();info.setText("Ligação MCP removida.");
                    DeskLog.event(this,"MCP","Ligação MCP removida");
                }).show();
        });
        action(root,"Voltar",this::finish);
    }
}
