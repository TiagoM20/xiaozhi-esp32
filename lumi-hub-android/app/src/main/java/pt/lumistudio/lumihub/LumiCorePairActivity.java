package pt.lumistudio.lumihub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Emparelhamento Windows apenas após autorização expressa no tablet. */
public final class LumiCorePairActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status;
    private TextView networkReport;
    private LinearLayout root;
    private TextView text(String message,int size,int color){
        TextView t=new TextView(this);t.setText(message);t.setTextColor(color);
        t.setTextSize(size);t.setPadding(7,10,7,10);return t;
    }
    private void button(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        root.addView(b);b.setOnClickListener(v->action.run());
    }
    private void restartCentral(){
        Intent intent=new Intent(this,HubService.class);
        // Requer onStartCommand no serviço existente para aplicar a nova opção.
        // Não usar stopService(): interromperia a ponte de áudio da DESK.
        boolean required=prefs.getBoolean("lumi_core_sync_enabled",false)||
            prefs.getBoolean("desk_gateway_enabled",false)||
            prefs.getBoolean("service_running",false)||
            !prefs.getString("mcp_endpoint","").isEmpty();
        if(required){
            try{
                if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);
                else startService(intent);
            }catch(Exception e){status.setText("Não foi possível iniciar a central.");}
        }
    }
    private void refresh(){
        String ip=LumiCoreSyncServer.localIp();
        boolean active=prefs.getBoolean("lumi_core_sync_enabled",false);
        boolean listening=prefs.getBoolean("lumi_core_sync_running",false);
        String error=prefs.getString("lumi_core_sync_error","");
        status.setText("Ligação Windows: "+(active?"autorizada":"desativada")+
            "\nServidor TCP 8770: "+(listening?"ATIVO":active?"NÃO INICIADO":"desligado")+
            (error.isEmpty()?"":" · "+error)+
            "\nEndereço do tablet: "+(ip==null?"Sem IP Wi-Fi":ip)+
            "\nPorta local: 8770"+
            "\nChave de emparelhamento: "+(active?"gerada neste tablet":"inativa"));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("hub",MODE_PRIVATE);
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(22,22,22,35);root.setBackgroundColor(Color.rgb(16,25,42));
        sc.addView(root);setContentView(sc);
        root.addView(text("LUMI CORE · Windows",23,Color.WHITE));
        root.addView(text("O PC configura o HUB por Wi-Fi. O tablet mantém os serviços "+
            "ativos quando o Windows está desligado. As chaves cloud são enviadas cifradas "+
            "e ficam no Android Keystore.",14,0xffbfcadd));
        status=text("",14,0xffa9e6e0);root.addView(status);
        networkReport=text("",13,0xffbfcadd);root.addView(networkReport);
        button("Ativar ligação ao Windows",()->{
            prefs.edit().putBoolean("lumi_core_sync_enabled",true).commit();
            LumiCoreSyncServer.pairingKey(this);
            restartCentral();refresh();
            status.postDelayed(this::refresh,1200);
        });
        button("Diagnosticar ligação CORE sem PC",()->{
            networkReport.setText("A testar a rede local do tablet...");
            new Thread(()->{
                String report=LumiCoreNetworkDiagnostics.report(
                    getApplicationContext());
                runOnUiThread(()->{
                    if(!isFinishing())networkReport.setText(report);
                });
            },"LumiCoreNetworkTest").start();
        });
        button("Copiar diagnóstico (sem chaves)",()->{
            String report=networkReport.getText().toString();
            if(report.isEmpty()){
                networkReport.setText("Executa primeiro o diagnóstico.");
                return;
            }
            ClipboardManager clipboard=(ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(
                ClipData.newPlainText("LUMI CORE diagnóstico sem credenciais",report));
        });
        button("Copiar código privado de emparelhamento",()->{
            if(!prefs.getBoolean("lumi_core_sync_enabled",false)){
                status.setText("Ativa primeiro a ligação Windows.");return;
            }
            String secret=LumiCoreSyncServer.pairingKey(this);
            ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("LUMI CORE chave privada",secret));
            refresh();
            root.addView(text("Código copiado. Cola-o apenas na aplicação LUMI CORE do PC; "+
                "não partilhes capturas com este código.",12,0xffbfcadd));
        });
        button("Mostrar código para introduzir no Windows",()->{
            if(!prefs.getBoolean("lumi_core_sync_enabled",false))return;
            String key=LumiCoreSyncServer.pairingKey(this);
            new AlertDialog.Builder(this).setTitle("Código privado (não partilhar)")
                .setMessage(key)
                .setPositiveButton("Fechar",null).show();
        });
        button("Revogar acesso dos PCs anteriores",()->{
            new AlertDialog.Builder(this).setTitle("Revogar emparelhamentos?")
                .setMessage("Todos os PCs terão de emparelhar novamente com novo código.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Revogar",(d,w)->{
                    LumiCoreSyncServer.rotatePairingKey(this);
                    restartCentral();refresh();
                }).show();
        });
        button("Desativar ligação ao Windows",()->{
            prefs.edit().putBoolean("lumi_core_sync_enabled",false).commit();
            restartCentral();refresh();
        });
        button("Voltar",this::finish);
        refresh();
    }
}
