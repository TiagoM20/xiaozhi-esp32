package pt.lumistudio.lumihub;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.Set;

/**
 * Diagnostico PT-PT do motor TTS Android, totalmente LOCAL quando a voz indicada
 * declara que nao necessita de rede. Nao altera servidor XiaoZhi nem voz do ESP32.
 */
public final class OfflineVoiceLabActivity extends Activity
        implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private TextView status;
    private Button speak;
    private Voice chosen;
    private boolean ready=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(14,21,35));
        root.setPadding(24,26,24,22);
        TextView heading=new TextView(this);
        heading.setText("LUMI DESK  ·  LABORATÓRIO OFFLINE");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        root.addView(heading);
        TextView explain=new TextView(this);
        explain.setText("Este teste procura uma voz portuguesa de Portugal que funcione sem Internet no motor de síntese do Tab 15. Não muda a voz atual da LUMI DESK.");
        explain.setTextColor(Color.rgb(188,203,224));
        explain.setTextSize(14);
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        margin.topMargin=17;root.addView(explain,margin);
        status=new TextView(this);
        status.setText("A verificar as vozes instaladas...");
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,0,1f);
        statusParams.topMargin=23;
        root.addView(status,statusParams);
        speak=new Button(this);
        speak.setAllCaps(false);
        speak.setText("Testar fala PT-PT offline");
        speak.setEnabled(false);
        speak.setOnClickListener(v->test());
        root.addView(speak);
        Button close=new Button(this);
        close.setAllCaps(false);
        close.setText("Fechar");
        close.setOnClickListener(v->finish());
        root.addView(close);
        setContentView(root);
        tts=new TextToSpeech(this,this);
    }

    @Override public void onInit(int code) {
        if(code!=TextToSpeech.SUCCESS || tts==null){
            status.setText("O motor de voz Android não iniciou. Não há teste offline disponível.");
            return;
        }
        try{
            Set<Voice> options=tts.getVoices();
            StringBuilder report=new StringBuilder();
            report.append("Motor TTS: ").append(tts.getDefaultEngine()).append("\n\n");
            if(options!=null) for(Voice voice:options){
                if(!"pt".equals(voice.getLocale().getLanguage()) ||
                        !"PT".equalsIgnoreCase(voice.getLocale().getCountry())) continue;
                boolean offline=!voice.isNetworkConnectionRequired();
                report.append("• ").append(voice.getName())
                    .append(offline?"  [OFFLINE]":"  [usa Internet]")
                    .append("\n");
                if(offline && chosen==null) chosen=voice;
            }
            if(chosen!=null){
                int result=tts.setVoice(chosen);
                if(result==TextToSpeech.SUCCESS){
                    tts.setSpeechRate(0.94f);
                    ready=true;
                    speak.setEnabled(true);
                    report.append("\nVoz offline selecionada: ").append(chosen.getName())
                        .append("\nPodes testá-la com o Wi-Fi desligado.");
                } else report.append("\nA voz offline encontrada não pôde ser ativada.");
            } else {
                report.append("\nNão foi encontrada nenhuma voz PT-PT marcada como offline.");
                report.append("\nInstala uma voz portuguesa europeia offline nas definições de síntese do Android ou testa um modelo Piper PT-PT separado.");
            }
            report.append("\n\nMesmo que este teste funcione, a integração com XiaoZhi e áudio Opus continua pendente.");
            status.setText(report.toString());
        }catch(Exception error){
            status.setText("Não foi possível consultar as vozes: " +
                error.getClass().getSimpleName());
        }
    }

    private void test() {
        if(!ready || tts==null || chosen==null)return;
        tts.speak("Olá. Sou a Lumi. Estamos a experimentar uma voz portuguesa "
            + "de Portugal completamente offline, aqui na nossa casa em Rebordosa. "
            + "Em breve poderei ajudar-vos com a televisão, as memórias e a música.",
            TextToSpeech.QUEUE_FLUSH,null,"lumi_offline_ptpt_demo");
    }

    @Override protected void onPause() {
        if(tts!=null)tts.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        super.onDestroy();
    }
}
