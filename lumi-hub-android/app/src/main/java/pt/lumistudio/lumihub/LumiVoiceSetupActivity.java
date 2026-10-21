package pt.lumistudio.lumihub;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Configuração voluntária dos serviços de fala e IA, apenas no tablet. */
public final class LumiVoiceSetupActivity extends Activity {
    private EditText region,azure,openai,model;
    private TextView status;
    private SharedPreferences p;
    private final AtomicBoolean voiceTestRunning=new AtomicBoolean(false);
    private TextView text(String content,int size){
        TextView t=new TextView(this);t.setText(content);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setPadding(12,12,12,12);return t;
    }
    private EditText input(LinearLayout l,String title,String hint,boolean secret){
        l.addView(text(title,15));EditText e=new EditText(this);
        e.setSingleLine(true);e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xffb4bfd0);e.setHint(hint);
        if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD);
        l.addView(e);return e;
    }
    private void btn(LinearLayout l,String label,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);
        l.addView(b);b.setOnClickListener(v->action.run());
    }
    /** Teste isolado do serviço Azure e da coluna do tablet, sem DESK nem OpenAI. */
    private void testRaquelOnTablet(){
        if(!voiceTestRunning.compareAndSet(false,true)){
            status.setText("O teste da Raquel já está a decorrer.");return;
        }
        if(LumiSecrets.get(this,"azure").isEmpty()){
            voiceTestRunning.set(false);
            status.setText("Guarda primeiro a chave Azure Speech.");return;
        }
        status.setText("A pedir áudio português de Portugal à Azure…");
        new Thread(()->{
            AudioTrack player=null;
            try{
                byte[] pcm=LumiCloudVoice.speak(this,p,
                    "Olá! Sou a Lumi e estou a falar português de Portugal. " +
                    "Esta é a minha voz Raquel, diretamente no tablet.");
                AudioFormat format=new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                AudioAttributes attrs=new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
                player=new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.length).build();
                if(player.getState()!=AudioTrack.STATE_INITIALIZED)
                    throw new IOException("A coluna não iniciou a reprodução.");
                int written=player.write(pcm,0,pcm.length);
                if(written!=pcm.length)throw new IOException("Áudio PCM incompleto.");
                player.play();
                SystemClock.sleep(Math.min(15000L,pcm.length/48L+600L));
                runOnUiThread(()->{
                    if(!isFinishing())status.setText(
                        "Teste Azure PT-PT concluído. Ouviste a Raquel no tablet?");
                });
            }catch(Exception error){
                String safe=error.getClass().getSimpleName()+": "+error.getMessage();
                runOnUiThread(()->{
                    if(!isFinishing())status.setText("Falha no teste PT-PT: "+safe);
                });
            }finally{
                if(player!=null){try{player.stop();}catch(Exception ignored){}
                    player.release();}
                voiceTestRunning.set(false);
            }
        },"LumiRaquelTabletTest").start();
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        p=getSharedPreferences("hub",MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);
        l.setPadding(20,20,20,32);l.setBackgroundColor(0xff122033);
        scroll.addView(l);setContentView(scroll);
        l.addView(text("LUMI · Conversação PT-PT",23));
        l.addView(text("Modo experimental: Azure Speech reconhece fala e dá voz à Raquel; " +
            "a IA responde com a tua chave OpenAI. Os pedidos são enviados a esses " +
            "serviços externos e podem ter custos. Nunca coloques chaves no GitHub.",14));
        region=input(l,"Região do Azure Speech","westeurope",false);
        region.setText(p.getString("lumi_azure_region","westeurope"));
        azure=input(l,"Chave Azure Speech (não é mostrada)","Deixar vazio para manter",true);
        openai=input(l,"Chave OpenAI API (não é mostrada)","Deixar vazio para manter",true);
        model=input(l,"Modelo de IA","gpt-4.1-mini",false);
        model.setText(p.getString("lumi_openai_model","gpt-4.1-mini"));
        l.addView(text("Azure: "+(LumiSecrets.get(this,"azure").isEmpty()?"por configurar":"configurado")+
            " · OpenAI: "+(LumiSecrets.get(this,"openai").isEmpty()?
                "por configurar":"configurado"),13));
        status=text("A DEMO continua disponível se não existirem credenciais.",13);
        l.addView(status);
        btn(l,"Testar Raquel PT-PT no tablet (só Azure)",this::testRaquelOnTablet);
        btn(l,"Guardar configuração",()->{
            try{
                String zone=region.getText().toString().trim().toLowerCase();
                if(!zone.matches("[a-z0-9]{3,25}"))throw new Exception("Região inválida");
                String modelName=model.getText().toString().trim();
                if(!modelName.matches("[a-zA-Z0-9_.-]{3,64}"))
                    throw new Exception("Modelo inválido");
                String a=azure.getText().toString().trim(),
                    b=openai.getText().toString().trim();
                if(!a.isEmpty())LumiSecrets.put(this,"azure",a);
                if(!b.isEmpty())LumiSecrets.put(this,"openai",b);
                p.edit().putString("lumi_azure_region",zone)
                    .putString("lumi_openai_model",modelName).apply();
                azure.setText("");openai.setText("");
                status.setText("Credenciais guardadas no Android Keystore. "+
                    "A próxima conversa na DESK utiliza os serviços configurados.");
            }catch(Exception e){status.setText("Não foi possível guardar: "+e.getMessage());}
        });
        btn(l,"Apagar credenciais dos serviços",()->{
            try{
                LumiSecrets.put(this,"azure","");
                LumiSecrets.put(this,"openai","");
                status.setText("Credenciais apagadas. Volta ao modo DEMO.");
            }catch(Exception e){status.setText("Falha ao apagar credenciais.");}
        });
        btn(l,"Voltar",this::finish);
    }
}