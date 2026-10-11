package pt.lumistudio.lumihub;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/** Comandos locais reais, não um substituto da conversação XiaoZhi. */
public final class LumiTabletActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int RECOGNIZE = 681;
    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private LinearLayout transcript;
    private TextView status;
    private EditText input;
    private TextToSpeech tts;
    private boolean voiceReady = false;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    private TextView text(String s, int size, int tint) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(tint);
        t.setPadding(12,10,12,10);return t;
    }
    private Button button(String name, Runnable action) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(name);
        b.setOnClickListener(v->action.run());return b;
    }
    private void add(LinearLayout panel, android.view.View v) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin=9;panel.addView(v,p);
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(17,16,17,16);root.setBackgroundColor(Color.rgb(13,22,40));
        setContentView(root);
        add(root,text("LUMI no tablet",24,Color.WHITE));
        add(root,text("Assistente local para comandos da casa. Não é ainda " +
            "o cérebro conversacional do XiaoZhi. O reconhecimento de voz " +
            "do Android pode necessitar de Internet.",13,Color.rgb(175,205,231)));
        status=text("A carregar voz PT-PT...",13,Color.rgb(130,231,220));add(root,status);
        ScrollView scroll=new ScrollView(this);transcript=new LinearLayout(this);
        transcript.setOrientation(1);scroll.addView(transcript);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        input=new EditText(this);input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);input.setSingleLine(true);
        input.setHint("Pergunta à LUMI ou pede um comando");
        add(root,input);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);
        actions.addView(button("Enviar",()->{
            String s=input.getText().toString().trim();
            if(!s.isEmpty()){input.setText("");process(s);}
        }),new LinearLayout.LayoutParams(0,54,1f));
        actions.addView(button("Falar",this::recognize),new LinearLayout.LayoutParams(0,54,1f));
        add(root,actions);
        LinearLayout more=new LinearLayout(this);more.setOrientation(0);
        more.addView(button("Escolher voz",()->startActivity(
            new Intent(this,OfflineVoiceLabActivity.class))),
            new LinearLayout.LayoutParams(0,52,1f));
        more.addView(button("Fechar",this::finish),
            new LinearLayout.LayoutParams(0,52,1f));
        add(root,more);
        answer("Olá! Podes pedir para aumentar o volume da televisão, " +
            "abrir o Spotify, consultar a meteorologia ou localizar um telemóvel.",false);
        tts=new TextToSpeech(this,this);
    }
    @Override public void onInit(int result) {
        voiceReady=false;
        if(result!=TextToSpeech.SUCCESS || tts==null){
            status.setText("Sem síntese disponível. Os comandos por texto continuam ativos.");
            return;
        }
        SharedPreferences p=getSharedPreferences("hub",MODE_PRIVATE);
        String selected=p.getString("ptpt_voice_name","");
        String engine=p.getString("ptpt_voice_engine","");
        if(selected.isEmpty() || !engine.equals(tts.getDefaultEngine())) {
            status.setText("Escolhe uma voz PT-PT. Nenhuma voz é escolhida automaticamente.");
            return;
        }
        if(tts.getVoices()!=null)for(Voice voice:tts.getVoices()) {
            if(voice.getName().equals(selected) &&
                    "pt".equals(voice.getLocale().getLanguage()) &&
                    "PT".equalsIgnoreCase(voice.getLocale().getCountry()) &&
                    tts.setVoice(voice)==TextToSpeech.SUCCESS) {
                tts.setSpeechRate(0.96f);voiceReady=true;
                status.setText("Voz escolhida: "+voice.getName());
                return;
            }
        }
        status.setText("A voz guardada não está disponível. Escolhe outra.");
    }
    @Override protected void onResume(){
        super.onResume();
        if(tts!=null)onInit(TextToSpeech.SUCCESS);
    }
    private void answer(String message,boolean speak) {
        TextView view=text("LUMI: "+message,15,Color.WHITE);
        view.setBackgroundColor(Color.rgb(30,46,77));add(transcript,view);
        if(speak && voiceReady && tts!=null)tts.speak(
            message,TextToSpeech.QUEUE_FLUSH,null,"lumi_local");
    }
    private void recognize() {
        try {
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-PT");
            i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Fala com a LUMI");
            startActivityForResult(i,RECOGNIZE);
        }catch(Exception e) {
            answer("O reconhecimento de fala não está disponível. " +
                "Escreve o pedido no campo de texto.",false);
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==RECOGNIZE && result==RESULT_OK && data!=null){
            ArrayList<String> out=data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
            if(out!=null&&!out.isEmpty())process(out.get(0));
        }
    }
    private String normalize(String s){
        return Normalizer.normalize(s.toLowerCase(PT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","");
    }
    private void process(String original){
        add(transcript,text("TU: "+original,15,Color.rgb(181,211,246)));
        String s=normalize(original);
        if(s.contains("youtube")){tv("ssap://system.launcher/launch");return;}
        if(s.contains("volume")||s.contains("televis")||s.contains(" tv ")||
            s.startsWith("tv ")||s.contains("silenc")) {
            if(s.contains("aument")||s.contains("mais")||s.contains("sobe"))
                tv("ssap://audio/volumeUp");
            else if(s.contains("baix")||s.contains("reduz")||s.contains("menos"))
                tv("ssap://audio/volumeDown");
            else if(s.contains("silenc"))tv("ssap://audio/setMute");
            else if(s.contains("deslig"))tv("ssap://system/turnOff");
            else answer("Posso controlar o volume, silenciar, desligar " +
                "e abrir o YouTube na televisão.",true);
            return;
        }
        if(s.contains("spotify")||s.contains("musica")){
            answer("Vou abrir o Spotify.",true);
            launch("com.spotify.music","https://open.spotify.com/");return;
        }
        if(s.contains("localiz")||s.contains("telemovel")){
            answer("Vou abrir o Google Find Hub. Escolhe o telemóvel " +
                "e seleciona «Reproduzir som».",true);
            launch("com.google.android.apps.adm",
                "https://www.google.com/android/find/");return;
        }
        if(s.contains("meteorologia")||s.contains("tempo")||s.contains("temperatura")){
            RebordosaWeather.State w=RebordosaWeather.read(this);
            answer(w.available?"Em Rebordosa, a temperatura exterior é "+
                w.temperature+", com "+w.humidity+" de humidade. "+
                w.condition+". "+w.updated+".":"Não tenho dados de meteorologia atualizados.",true);
            return;
        }
        if(s.contains("bateria")){
            int b=getSharedPreferences("hub",MODE_PRIVATE).getInt("battery",-1);
            answer(b<0?"Não tenho leitura atual da bateria.":
                "O tablet está com "+b+" por cento de bateria.",true);return;
        }
        if(s.contains("lumi desk")||s.contains("onde estas")){
            answer("Estou aqui no tablet. Fazer a LUMI DESK física responder " +
                "«Olá, estou aqui» exige a ligação de áudio ao ESP32, " +
                "que ainda não está configurada.",true);return;
        }
        if(s.contains("ola")||s.contains("bom dia")){
            answer("Olá! Estou pronta para ajudar com a casa.",true);return;
        }
        answer("Este assistente local ainda não conversa livremente. " +
            "Experimenta um comando doméstico; a conversa completa permanece na LUMI DESK.",true);
    }
    private void tv(String action){
        SharedPreferences p=getSharedPreferences("hub",MODE_PRIVATE);
        String ip=p.getString("tv_ip",""),pin=p.getString("lg_cert_pin","");
        String key=p.getString("lg_client_key","");
        if(ip.isEmpty()||pin.isEmpty()||key.isEmpty()){
            answer("Configura e emparelha primeiro a TV em Dispositivos → TV da Sala.",true);
            return;
        }
        answer("Vou enviar o comando à TV.",false);
        WebOsController.request(ip,key,pin,action,false,(response,newKey)->{
            SharedPreferences.Editor edit=p.edit().putString("lg_last_status",response);
            if(newKey!=null&&!newKey.isEmpty())edit.putString("lg_client_key",newKey);
            edit.apply();
            handler.post(()->answer(response,true));
        });
    }
    private void launch(String pkg,String url){
        try{
            Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
            startActivity(launch!=null?launch:
                new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        }catch(Exception e){answer("Não consegui abrir o serviço.",false);}
    }
    @Override protected void onDestroy(){
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        super.onDestroy();
    }
}
