package pt.lumistudio.lumihub;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

/**
 * Voz PT-PT: o Android NÃO expõe um campo gender em Voice.
 * O utilizador escuta e escolhe a voz. Persistir só nome do motor e da voz.
 * Esta escolha aplica-se à LUMI no tablet, não ao backend da DESK.
 */
public final class OfflineVoiceLabActivity extends Activity
        implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private TextView status;
    private LinearLayout choices;
    private final ArrayList<Voice> available = new ArrayList<>();
    private final String demo = "Olá. Sou a Lumi e estou a falar português de Portugal. " +
        "A televisão da sala está pronta para receber comandos. " +
        "Vamos ouvir o tom desta voz, sem alterar a LUMI DESK.";
    private boolean ready=false;

    private TextView txt(String value,int size) {
        TextView t=new TextView(this);
        t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);
        t.setPadding(8,8,8,8);
        return t;
    }
    private Button btn(String title,Runnable click) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);
        b.setOnClickListener(v->click.run());return b;
    }
    private void add(LinearLayout container,android.view.View view) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin=10;container.addView(view,p);
    }
    private void open(String url) {
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){status.setText("Não foi possível abrir a página.");}
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setBackgroundColor(Color.rgb(14,21,35));
        root.setPadding(24,24,24,30);scroll.addView(root);setContentView(scroll);
        add(root,txt("LUMI · Escolher voz PT-PT",23));
        add(root,txt("A voz do último teste foi masculina porque a primeira voz offline " +
            "era selecionada automaticamente. Agora podes experimentar cada voz PT-PT " +
            "e guardar a que preferires. O Android não identifica o género com segurança.",14));
        status=txt("A consultar as vozes do Tab 15...",14);add(root,status);
        choices=new LinearLayout(this);choices.setOrientation(1);add(root,choices);
        add(root,txt("Raquel Neural: teste feminino em português europeu, mas o áudio " +
            "de demonstração é pré-gravado. Ainda não é a voz de respostas em tempo real.",13));
        add(root,btn("Ouvir demonstração Raquel Neural",()->
            open("https://github.com/TiagoM20/xiaozhi-esp32/releases/download/" +
                "lumi-voice-ptpt-demo-v0.1/LUMI-Raquel-PTPT-Demo.mp3")));
        add(root,btn("Gerir vozes no Android",()->{
            try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}
            catch(Exception e){
                try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
                catch(Exception ignored){status.setText("Abre as Definições de texto para voz do Android.");}
            }
        }));
        add(root,btn("Fechar",this::finish));
        tts=new TextToSpeech(this,this);
    }
    @Override public void onInit(int code) {
        if(code!=TextToSpeech.SUCCESS||tts==null){
            status.setText("O motor de voz Android não arrancou.");return;
        }
        ready=true;available.clear();choices.removeAllViews();
        try{
            Set<Voice> options=tts.getVoices();
            if(options!=null)for(Voice v:options){
                if("pt".equals(v.getLocale().getLanguage()) &&
                    "PT".equalsIgnoreCase(v.getLocale().getCountry()))available.add(v);
            }
            available.sort(Comparator.comparing(Voice::isNetworkConnectionRequired)
                .thenComparing(Voice::getName));
            if(available.isEmpty()){
                status.setText("Não há vozes pt-PT instaladas neste motor. " +
                    "Instala uma voz PT-PT nas definições de texto para voz.");
                return;
            }
            String chosen=getSharedPreferences("hub",MODE_PRIVATE)
                .getString("ptpt_voice_name","");
            String engine=getSharedPreferences("hub",MODE_PRIVATE)
                .getString("ptpt_voice_engine","");
            status.setText("Motor: "+tts.getDefaultEngine()+" · "+available.size() +
                " voz(es) de Portugal disponíveis. Ouve-as e escolhe uma. " +
                "Algumas necessitam de Internet.");
            for(Voice voice:available) {
                boolean offline=!voice.isNetworkConnectionRequired();
                LinearLayout item=new LinearLayout(this);
                item.setOrientation(1);item.setPadding(12,11,12,11);
                item.setBackgroundColor(Color.rgb(30,46,72));
                String picked=voice.getName().equals(chosen) &&
                    tts.getDefaultEngine().equals(engine)?" [SELECIONADA]":"";
                add(item,txt(voice.getName()+picked,15));
                add(item,txt(offline?"Offline (segundo o motor Android)":
                    "Necessita de Internet",12));
                add(item,btn("Ouvir esta voz",()->testVoice(voice)));
                add(item,btn("Usar na LUMI do tablet",()->saveVoice(voice)));
                add(choices,item);
            }
        }catch(Exception ex){
            status.setText("Não foi possível listar as vozes: "+ex.getClass().getSimpleName());
        }
    }
    private void testVoice(Voice voice) {
        if(!ready||tts==null)return;
        if(tts.setVoice(voice)==TextToSpeech.SUCCESS) {
            tts.setSpeechRate(0.96f);
            int result=tts.speak(demo,TextToSpeech.QUEUE_FLUSH,null,"ptpt_test");
            status.setText(result==TextToSpeech.ERROR?
                "O motor rejeitou a reprodução desta voz.":
                "A experimentar: "+voice.getName());
        }else status.setText("Esta voz não pôde ser ativada.");
    }
    private void saveVoice(Voice voice) {
        if(!ready||tts==null)return;
        if(tts.setVoice(voice)!=TextToSpeech.SUCCESS){
            status.setText("Não foi possível selecionar esta voz.");return;
        }
        getSharedPreferences("hub",MODE_PRIVATE).edit()
            .putString("ptpt_voice_engine",tts.getDefaultEngine())
            .putString("ptpt_voice_name",voice.getName()).apply();
        status.setText("Voz guardada para a LUMI do tablet: "+voice.getName() +
            ". Não altera a LUMI DESK.");
        onInit(TextToSpeech.SUCCESS);
    }
    @Override protected void onPause(){if(tts!=null)tts.stop();super.onPause();}
    @Override protected void onDestroy(){
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}super.onDestroy();
    }
}
