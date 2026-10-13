package pt.lumistudio.lumihub;

import android.app.Activity;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** V12: um único local de teste sem simular funções ainda não ligadas à DESK. */
public final class LumiCoreActivity extends Activity {
    private LinearLayout panel;
    private SharedPreferences prefs;
    private int textPrimary, textMuted, textAccent;

    private void label(String value,int size,int color){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(8,12,8,12);panel.addView(t);
    }
    private void button(String text,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);
        panel.addView(b);b.setOnClickListener(v->action.run());
    }
    private void toggleBridge(boolean on){
        prefs.edit().putBoolean("desk_gateway_enabled",on).apply();
        Intent service=new Intent(this,HubService.class);
        stopService(service);
        if(on||!prefs.getString("mcp_endpoint","").isEmpty()){
            try{
                if(Build.VERSION.SDK_INT>=26)startForegroundService(service);
                else startService(service);
            }catch(Exception ex){label("Não foi possível iniciar a central.",13,Color.RED);}
        }
        label(on?"Ponte pedida. Aguarda alguns segundos e consulta o estado.":
            "Ponte desligada.",13,textMuted);
    }
    private void showBridge(){
        label("Ponte: "+prefs.getString("desk_gateway_state","Aguarda ativação"),14,
            textAccent);
    }
    private void copyOta(){
        if(!prefs.getBoolean("desk_gateway_enabled",false)){
            label("Ativa primeiro a ponte de testes.",13,textMuted);return;
        }
        String url=DeskTestOta.pairingUrl(prefs);
        if(url==null){label("Não há IP Wi-Fi local disponível.",13,textMuted);return;}
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("LUMI OTA local",url));
        label("Endereço privado copiado. Não o publiques; contém a chave de emparelhamento.",
            13,textMuted);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("hub",MODE_PRIVATE);
        HubThemes.Palette palette=HubThemes.get(HubThemes.bounded(
            prefs.getInt("hub_theme",0)));
        textPrimary=palette.text;textMuted=palette.muted;textAccent=palette.accent;
        ScrollView scroll=new ScrollView(this);
        panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20,20,20,30);
        panel.setBackgroundColor(palette.background);
        scroll.addView(panel);setContentView(scroll);
        label("LUMI CORE · V12",24,textPrimary);
        label("Preparação da ligação entre memória, voz PT-PT e LUMI DESK.",15,textMuted);
        label("Estado MCP: "+prefs.getString("mcp_state","Por configurar"),14,
            textAccent);
        try(MemoryDb db=new MemoryDb(this)){
            label("Recordações locais: "+db.count(),16,textPrimary);
        }
        label("Já disponível: guardar e procurar memórias por MCP quando o agente usar " +
            "as ferramentas. A conversa e a voz na DESK continuam no XiaoZhi atual.",14,textMuted);
        button("Ouvir e escolher voz PT-PT no tablet",()->
            startActivity(new Intent(this,OfflineVoiceLabActivity.class)));
        button("Abrir assistente local do tablet",()->
            startActivity(new Intent(this,LumiTabletActivity.class)));
        label("Próxima ligação: ponte WebSocket Opus do tablet para a DESK, " +
            "testada antes de qualquer mudança do firmware.",13,textMuted);
        label("PONTE DE VOZ (EXPERIMENTAL)",16,textAccent);
        label("Este modo envia apenas uma frase previamente gravada em Opus para a " +
            "Waveshare, não responde ainda a perguntas. Só ativar quando a DESK " +
            "tiver um backup verificado e o endereço original do servidor guardado.",13,textMuted);
        button("Ativar ponte de testes no tablet",()->toggleBridge(true));
        button("Parar ponte de testes",()->toggleBridge(false));
        button("Copiar endereço OTA local para emparelhar",this::copyOta);
        button("Ver estado da ponte",this::showBridge);
        button("Voltar",this::finish);
    }
}
