package pt.lumistudio.lumihub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home Assistant real: configuração, estados e controlo em rede autenticada. */
public final class HomeAssistantActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private HubThemes.Palette palette;
    private LinearLayout root,entities;
    private TextView status;
    private String currentBase="",currentToken="";
    private boolean busy=false;
    private List<HomeAssistantClient.Entity> cache=new ArrayList<>();

    private int dp(int amount){
        return Math.round(getResources().getDisplayMetrics().density*amount);
    }
    private TextView label(String text,int size,int color,boolean strong){
        TextView view=new TextView(this);
        view.setText(text);view.setTextSize(size);view.setTextColor(color);
        if(strong)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return view;
    }
    private LinearLayout panel(){
        LinearLayout result=new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(17),dp(18),dp(17),dp(18));
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(palette.panel);shape.setCornerRadius(dp(18));
        shape.setStroke(dp(1),palette.stroke);result.setBackground(shape);
        return result;
    }
    private void add(LinearLayout parent,View child,int space){
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);
        layout.topMargin=dp(space);parent.addView(child,layout);
    }
    private Button button(String title,Runnable run){
        Button button=new Button(this);
        button.setAllCaps(false);button.setText(title);
        button.setMinHeight(dp(48));
        button.setTextColor(palette.text);
        button.setOnClickListener(v->{if(!busy)run.run();});
        return button;
    }
    private void message(String title,String text){
        if(!isFinishing())new AlertDialog.Builder(this).setTitle(title)
            .setMessage(text).setPositiveButton("OK",null).show();
    }
    private void ui(Runnable r){
        main.post(()->{if(!isFinishing()&&!isDestroyed())r.run();});
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        int theme=getSharedPreferences("hub",MODE_PRIVATE).getInt("hub_theme",0);
        palette=HubThemes.get(HubThemes.bounded(theme));
        currentBase=HomeAssistantClient.base(this);
        currentToken=HomeAssistantClient.token(this);
        render();
        if(HomeAssistantClient.isConfigured(this))refresh();
    }
    @Override protected void onDestroy(){
        worker.shutdownNow();
        super.onDestroy();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setPadding(dp(16),dp(18),dp(16),dp(28));
        scroll.addView(root);setContentView(scroll);
        add(root,label("HOME ASSISTANT",25,palette.text,true),0);
        add(root,label("Equipamentos da casa · ligação autenticada",13,palette.muted,false),5);
        LinearLayout config=panel();
        add(config,label("Ligação",18,palette.text,true),0);
        boolean configured=HomeAssistantClient.isConfigured(this);
        status=label(configured?"Configurado · estado por verificar":
            "Ainda não ligado",14,configured?palette.accent:palette.gold,true);
        add(config,status,7);
        add(config,label(configured?currentBase:
            "Indica o endereço do servidor e o token para começar.",
            13,palette.muted,false),7);
        add(config,button(configured?"Alterar ligação":"Ligar ao Home Assistant",
            this::showSetup),10);
        if(configured){
            add(config,button("Atualizar estados",this::refresh),7);
            add(config,button("Desligar desta conta",this::disconnect),7);
        }
        add(root,config,14);

        LinearLayout controls=panel();
        add(controls,label("Equipamentos ligados",18,palette.text,true),0);
        add(controls,label("Estados reais do Home Assistant. Os comandos só aparecem "
            +"para equipamentos que o servidor disponibiliza.",12,palette.muted,false),7);
        entities=new LinearLayout(this);entities.setOrientation(LinearLayout.VERTICAL);
        add(controls,entities,10);
        add(root,controls,14);
        showEntities();

        LinearLayout voice=panel();
        add(voice,label("Comandos por voz · LUMI DESK",18,palette.text,true),0);
        add(voice,label("O controlo do HUB já usa a API do Home Assistant. "
            +"Para dizer «Lumi, desliga a TV da sala», é necessária "
            +"uma ponte MCP entre a tua conta XiaoZhi e o servidor Home Assistant. "
            +"A DESK continua a falar diretamente com o XiaoZhi, sem PC ligado.",
            13,palette.muted,false),9);
        add(voice,button("Configurar ponte de voz (instruções)",this::voiceGuide),9);
        add(root,voice,14);
    }

    private void showSetup(){
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20),0,dp(20),0);
        EditText address=new EditText(this);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("http://homeassistant.local:8123");
        address.setText(currentBase);
        add(form,address,5);
        EditText token=new EditText(this);
        token.setSingleLine(true);
        token.setHint(currentToken.isEmpty()?"Token de acesso pessoal":"Token guardado (deixa vazio para manter)");
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(form,token,11);
        add(form,label("O token fica encriptado no Android Keystore. "
            +"HTTPS é obrigatório fora da rede local; nunca partilhes a chave.",
            12,palette.muted,false),9);
        new AlertDialog.Builder(this)
            .setTitle("Ligar ao Home Assistant")
            .setView(form).setNegativeButton("Cancelar",null)
            .setPositiveButton("Testar e guardar",(d,w)->{
                String base=address.getText().toString().trim();
                String input=token.getText().toString().trim();
                String effective=input.isEmpty()?currentToken:input;
                try{base=HomeAssistantClient.validateBase(base);}
                catch(Exception e){message("Endereço inválido",e.getMessage());return;}
                if(effective.isEmpty()){message("Token em falta",
                    "Cria um token de acesso no perfil do Home Assistant.");return;}
                final String approvedBase=base,approvedToken=effective,
                    freshToken=input;
                status.setText("A testar autenticação...");
                busy=true;
                worker.execute(()->{
                    try{
                        HomeAssistantClient.test(approvedBase,approvedToken);
                        HomeAssistantClient.store(this,approvedBase,freshToken);
                        ui(()->{
                            busy=false;
                            currentBase=approvedBase;
                            currentToken=approvedToken;
                            render();refresh();
                        });
                    }catch(Exception e){
                        ui(()->{
                            busy=false;
                            status.setText("Ligação não estabelecida.");
                            message("Falhou a ligação",e.getMessage());
                        });
                    }
                });
            }).show();
    }

    private void refresh(){
        if(busy || !HomeAssistantClient.isConfigured(this))return;
        busy=true;status.setText("A consultar o servidor...");
        final String base=HomeAssistantClient.base(this),
                     token=HomeAssistantClient.token(this);
        worker.execute(()->{
            try{
                List<HomeAssistantClient.Entity> found=HomeAssistantClient.states(base,token);
                ui(()->{
                    busy=false;cache=found;
                    status.setText("Ligado · "+found.size()+" equipamentos compatíveis");
                    showEntities();
                });
            }catch(Exception e){
                ui(()->{
                    busy=false;status.setText("Sem ligação · "+e.getMessage());
                    cache.clear();showEntities();
                });
            }
        });
    }

    private void showEntities(){
        if(entities==null)return;
        entities.removeAllViews();
        if(cache.isEmpty()){
            add(entities,label(HomeAssistantClient.isConfigured(this)?
                "Sem equipamentos disponíveis. Atualiza ou verifica as integrações e permissões.":
                "Liga primeiro o Home Assistant para descobrir os equipamentos.",
                13,palette.muted,false),7);
            return;
        }
        // A interface limita cartões; o servidor continua a ser a única fonte de estados.
        int shown=0;
        for(HomeAssistantClient.Entity entity:cache){
            if(shown++>=48)break;
            LinearLayout entry=panel();
            add(entry,label(entity.name,16,palette.text,true),0);
            add(entry,label(entity.id+" · "+entity.stateLabel(),12,
                entity.available?palette.muted:palette.gold,false),6);
            if(entity.available){
                LinearLayout actions=new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                String[] choices;
                if("vacuum".equals(entity.domain))
                    choices=new String[]{"Iniciar","Parar","Regressar à base"};
                else if("media_player".equals(entity.domain))
                    choices=new String[]{"Ligar","Desligar","Pausa/Reproduzir"};
                else choices=new String[]{"Ligar","Desligar"};
                for(String choice:choices){
                    String service;
                    switch(choice){
                        case "Iniciar":service="start";break;
                        case "Parar":service="stop";break;
                        case "Regressar à base":service="return_to_base";break;
                        case "Pausa/Reproduzir":service="media_play_pause";break;
                        case "Ligar":service="turn_on";break;
                        default:service="turn_off";
                    }
                    Button b=button(choice,()->action(entity,service));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1f);
                    actions.addView(b,lp);
                }
                add(entry,actions,9);
            }
            add(entities,entry,9);
        }
    }

    private void action(HomeAssistantClient.Entity e,String service){
        if(busy)return;
        if("vacuum".equals(e.domain)&&"start".equals(service)){
            new AlertDialog.Builder(this).setTitle("Iniciar aspirador?")
                .setMessage("Executar a ação em "+e.name+" através do Home Assistant?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Iniciar",(d,w)->executeCommand(e,service)).show();
        }else executeCommand(e,service);
    }
    private void executeCommand(HomeAssistantClient.Entity e,String service){
        if(busy)return;
        busy=true;status.setText("A enviar comando para "+e.name+"...");
        final String base=HomeAssistantClient.base(this),
                     token=HomeAssistantClient.token(this);
        worker.execute(()->{
            try{
                HomeAssistantClient.command(base,token,e,service);
                ui(()->{
                    busy=false;
                    status.setText("Comando aceite pelo servidor · a atualizar...");
                    refresh();
                });
            }catch(Exception error){
                ui(()->{
                    busy=false;
                    status.setText("Não foi possível executar o comando.");
                    message("Home Assistant",error.getMessage());
                });
            }
        });
    }

    private void disconnect(){
        new AlertDialog.Builder(this).setTitle("Desligar Home Assistant?")
            .setMessage("Apaga o endereço e token apenas deste dispositivo Android. "
                +"Não remove equipamentos no servidor.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Desligar",(d,w)->{
                try{HomeAssistantClient.clear(this);}
                catch(Exception e){message("Erro", "Não foi possível apagar a credencial.");return;}
                currentBase="";currentToken="";cache.clear();render();
            }).show();
    }
    private void openUrl(String link){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}
        catch(Exception e){message("Sem navegador","Abre o endereço num navegador.");}
    }
    private void voiceGuide(){
        new AlertDialog.Builder(this).setTitle("LUMI + Home Assistant por voz")
            .setMessage("1. Num servidor Home Assistant sempre ligado, instala e configura "
                +"a integração oficial Model Context Protocol Server.\n\n"
                +"2. No HACS, adiciona a integração comunitária "
                +"mac8005/xiaozhi-mcp-ha, confirma a compatibilidade e reinicia o HA.\n\n"
                +"3. Cria um token HA exclusivo e copia o endpoint MCP do teu agente "
                +"em xiaozhi.me/console/agents.\n\n"
                +"4. Configura ambos na integração XiaoZhi MCP no Home Assistant. "
                +"Expõe apenas as entidades pretendidas e testa primeiro os botões do HUB.\n\n"
                +"O comando de voz depende da ponte e de o HA estar ligado; "
                +"o HUB Android não substitui o servidor HA nem encaminha o áudio.")
            .setNegativeButton("Fechar",null)
            .setNeutralButton("Consola XiaoZhi",(d,w)->openUrl("https://xiaozhi.me/console/agents"))
            .setPositiveButton("Projeto da ponte MCP",(d,w)->openUrl(
                "https://github.com/mac8005/xiaozhi-mcp-ha")).show();
    }
}
