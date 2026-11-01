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

/** Configuracao MCP do XiaoZhi online. Token guardado somente no Android Keystore. */
public final class McpSettingsActivity extends Activity {
    private SharedPreferences prefs;
    private TextView info;
    private TextView stored;
    private TextView text(String value,int size) {
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(size);v.setTextColor(Color.WHITE);
        v.setPadding(8,10,8,10);return v;
    }
    private void action(LinearLayout parent,String title,Runnable callback) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);
        parent.addView(b);b.setOnClickListener(v->callback.run());
    }
    private void updateStatus() {
        info.setText("MCP: "+prefs.getString("mcp_state","Por configurar"));
        stored.setText(XiaozhiMcpBridge.hasEndpoint(this)
            ? "Credencial MCP guardada no tablet (o token nao e apresentado)."
            : "Sem ligacao MCP guardada.");
    }
    private boolean refreshBridge() {
        try {
            Intent service=new Intent(this,HubService.class);
            service.setAction(HubService.ACTION_REFRESH_MCP);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(service);
            else startService(service);
            return true;
        }catch(Exception ex) {
            info.setText("Nao foi possivel iniciar o HUB; verifica as permissoes do Android.");
            return false;
        }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("hub",MODE_PRIVATE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(20,20,20,28);root.setBackgroundColor(0xff122033);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        scroll.addView(root);setContentView(scroll);
        root.addView(text("LUMI · Ligacao MCP ao XiaoZhi",23));
        root.addView(text("O MCP disponibiliza ao agente XiaoZhi as ferramentas autorizadas do HUB. Nao altera a voz nem o firmware da DESK.",14));
        info=text("",14);root.addView(info);
        stored=text("",13);root.addView(stored);
        EditText endpoint=new EditText(this);endpoint.setSingleLine(true);
        endpoint.setTextColor(Color.WHITE);endpoint.setHintTextColor(0xffabb4c5);
        endpoint.setHint("Colar endereco MCP privado do agente");
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(endpoint);
        action(root,"Guardar e ligar ao XiaoZhi",()->{
            String url=endpoint.getText().toString().trim();
            if(!XiaozhiMcpBridge.validEndpoint(url)) {
                info.setText("URL invalido: usa wss://api.xiaozhi.me/mcp/?token=...");return;
            }
            try {
                // Guardar cifrado antes de remover possivel valor legado, sem expor o token.
                LumiSecrets.put(this,"mcp_endpoint",url);
                prefs.edit().remove("mcp_endpoint")
                    .putString("mcp_state","A iniciar ligacao").apply();
                endpoint.setText("");
                if(refreshBridge())info.setText("Credencial guardada; a iniciar ponte MCP...");
                updateStatus();
                DeskLog.event(this,"MCP","Credencial MCP guardada no Keystore");
            }catch(Exception e){info.setText("Falha ao guardar credencial no Android Keystore.");}
        });
        action(root,"Religar ou testar a ponte MCP",()->{
            if(!XiaozhiMcpBridge.hasEndpoint(this)) {
                info.setText("Configura primeiro o URL MCP do agente.");return;
            }
            if(refreshBridge())info.setText("A solicitar nova ligacao MCP...");
        });
        action(root,"Atualizar estado",this::updateStatus);
        action(root,"Remover ligacao MCP",()->{
            new android.app.AlertDialog.Builder(this).setTitle("Remover ligacao MCP?")
                .setMessage("Remove a credencial encriptada e desliga apenas a ponte MCP.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Remover",(d,w)->{
                    try {
                        LumiSecrets.put(this,"mcp_endpoint","");
                        prefs.edit().remove("mcp_endpoint")
                            .putString("mcp_state","Por configurar").apply();
                        refreshBridge();
                        updateStatus();
                        DeskLog.event(this,"MCP","Credencial MCP removida");
                    }catch(Exception e) {
                        info.setText("Falha ao remover a credencial MCP.");
                    }
                }).show();
        });
        action(root,"Voltar",this::finish);
        updateStatus();
    }
    @Override protected void onResume(){super.onResume();if(info!=null)updateStatus();}
}
