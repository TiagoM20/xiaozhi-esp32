package pt.lumistudio.lumihub;

import android.app.Activity;
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

    private void label(String value,int size,int color){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(8,12,8,12);panel.addView(t);
    }
    private void button(String text,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);
        panel.addView(b);b.setOnClickListener(v->action.run());
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("hub",MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20,20,20,30);
        panel.setBackgroundColor(Color.rgb(14,21,35));
        scroll.addView(panel);setContentView(scroll);
        label("LUMI CORE · V12",24,Color.WHITE);
        label("Preparação da ligação entre memória, voz PT-PT e LUMI DESK.",15,Color.LTGRAY);
        label("Estado MCP: "+prefs.getString("mcp_state","Por configurar"),14,
            Color.rgb(130,231,220));
        try(MemoryDb db=new MemoryDb(this)){
            label("Recordações locais: "+db.count(),16,Color.WHITE);
        }
        label("Já disponível: guardar e procurar memórias por MCP quando o agente usar " +
            "as ferramentas. A conversa e a voz na DESK continuam no XiaoZhi atual.",14,Color.LTGRAY);
        button("Ouvir e escolher voz PT-PT no tablet",()->
            startActivity(new Intent(this,OfflineVoiceLabActivity.class)));
        button("Abrir assistente local do tablet",()->
            startActivity(new Intent(this,LumiTabletActivity.class)));
        label("Próxima ligação: ponte WebSocket Opus do tablet para a DESK, " +
            "testada antes de qualquer mudança do firmware.",13,Color.LTGRAY);
        button("Voltar",this::finish);
    }
}
