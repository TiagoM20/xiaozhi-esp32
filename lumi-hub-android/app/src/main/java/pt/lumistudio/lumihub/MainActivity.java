package pt.lumistudio.lumihub;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextClock;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 18, 31);
    private static final int PANEL = Color.rgb(24, 32, 48);
    private static final int PANEL_BRIGHT = Color.rgb(34, 44, 62);
    private static final int WHITE = Color.rgb(245, 248, 255);
    private static final int MUTED = Color.rgb(162, 175, 195);
    private static final int CYAN = Color.rgb(114, 228, 239);
    private static final int GOLD = Color.rgb(255, 187, 119);
    private static final int VIOLET = Color.rgb(174, 154, 251);
    private static final int GREEN = Color.rgb(139, 223, 173);
    private static final int STROKE = Color.rgb(52, 64, 85);
    private static final Locale PT = new Locale("pt", "PT");

    private final String[] navItems = {"Estação", "Dispositivos", "Memórias", "Atualizações"};
    private String page = "Estação";
    private String activeDevice = "tv";
    private LinearLayout stage;
    private MemoryDb memories;
    private static final int PICK_MEMORY_JSON = 9441;
    private final android.os.Handler weatherHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            if ("Estação".equals(page)) {
                RebordosaWeather.refreshIfNeeded(MainActivity.this, () -> {
                    if (!isFinishing() && "Estação".equals(page)) draw();
                });
            }
            weatherHandler.postDelayed(this, 5L * 60L * 1000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        memories = new MemoryDb(this);
        seedStarterMemories();
        draw();
    }
    @Override protected void onResume() {
        super.onResume();
        if (stage != null) draw();
        weatherHandler.removeCallbacks(weatherTick);
        weatherHandler.postDelayed(weatherTick, 5L * 60L * 1000L);
    }
    @Override protected void onPause() {
        weatherHandler.removeCallbacks(weatherTick);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (memories != null) memories.close();
        super.onDestroy();
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }
    private GradientDrawable fill(int color, int radius) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color);
        b.setCornerRadius(dp(radius));
        return b;
    }
    private GradientDrawable borderFill(int color, int radius, int stroke) {
        GradientDrawable b = fill(color, radius);
        b.setStroke(dp(1), stroke);
        return b;
    }
    private TextView label(String message, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(message);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setIncludeFontPadding(true);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        v.setLineSpacing(dp(3), 1.03f);
        return v;
    }
    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private LinearLayout horizontal() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        parent.addView(child, p);
    }
    private void addWeighted(LinearLayout parent, View child, int weight, int marginStart) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        p.leftMargin = dp(marginStart);
        parent.addView(child, p);
    }
    private LinearLayout panel() {
        LinearLayout v = vertical();
        v.setPadding(dp(18), dp(18), dp(18), dp(18));
        v.setBackground(borderFill(PANEL, 19, STROKE));
        return v;
    }
    private LinearLayout detail() {
        LinearLayout v = panel();
        v.setPadding(dp(21), dp(20), dp(21), dp(21));
        return v;
    }
    private Button button(String caption, boolean accented, Runnable action) {
        Button v = new Button(this);
        v.setAllCaps(false);
        v.setText(caption);
        v.setTextColor(accented ? BG : WHITE);
        v.setTextSize(14);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        v.setBackground(borderFill(accented ? CYAN : PANEL_BRIGHT, 12,
            accented ? CYAN : STROKE));
        v.setMinHeight(dp(44));
        v.setPadding(dp(8), dp(10), dp(8), dp(10));
        v.setOnClickListener(w -> action.run());
        return v;
    }
    private void notice(String title, String content) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(content)
            .setPositiveButton("OK", null).show();
    }
    private String date(String format) {
        return new SimpleDateFormat(format, PT).format(new Date());
    }
    private boolean landscape() {
        return getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
    }
    private void seedStarterMemories() {
        SharedPreferences p = getSharedPreferences("hub", MODE_PRIVATE);
        if (p.getBoolean("starter_memories_v7", false)) return;
        // Nunca guardar dados privados do agregado no codigo publico.
        memories.saveIfMissing("Idioma e estilo", "A LUMI deve conversar naturalmente em portugues de Portugal, com respostas claras e humanas.");
        memories.saveIfMissing("Historias favoritas", "Gostamos de historias interativas com Sonic, Mario e Crash Bandicoot, escolhas e personagens recorrentes.");
        memories.saveIfMissing("Privacidade", "A LUMI deve pedir autorizacao antes de guardar ou partilhar informacao pessoal sensivel.");
        p.edit().putBoolean("starter_memories_v7", true).apply();
    }

    private void chooseMemoryFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
            new String[]{"application/json", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, PICK_MEMORY_JSON);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MEMORY_JSON || resultCode != RESULT_OK || data == null) return;
        try (InputStream input = getContentResolver().openInputStream(data.getData());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (input == null) throw new Exception("Ficheiro inacessivel.");
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) > 0) {
                if (out.size() + n > 128 * 1024) throw new Exception("Ficheiro demasiado grande.");
                out.write(buffer, 0, n);
            }
            String json = new String(out.toByteArray(), StandardCharsets.UTF_8);
            JSONArray entries = json.trim().startsWith("[")?
                new JSONArray(json):new JSONObject(json).getJSONArray("memories");
            if (entries.length() > 100) throw new Exception("Maximo de 100 memorias.");
            int inserted = 0;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.optJSONObject(i);
                if (item == null) continue;
                String title = item.optString("title", "").trim();
                String detail = item.optString("detail", "").trim();
                if (title.isEmpty() || title.length() > 100 || detail.length() > 1500) continue;
                if (memories.saveIfMissing(title, detail)) inserted++;
            }
            draw();
            notice("Memorias importadas", inserted + " novas memorias guardadas no tablet. Ainda nao ligadas as conversas da LUMI DESK.");
        } catch (Exception error) {
            notice("Importacao de memorias", "Nao foi possivel importar: " + error.getMessage());
        }
    }

    private void draw() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout workspace = horizontal();
        workspace.setGravity(Gravity.TOP);
        root.addView(workspace, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (landscape()) addSidebar(workspace);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        workspace.addView(scroll, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        stage = vertical();
        stage.setPadding(dp(landscape()?18:16), dp(landscape()?13:16),
            dp(landscape()?18:16), dp(24));
        scroll.addView(stage);
        if ("Dispositivos".equals(page)) drawDevices();
        else if ("Memórias".equals(page)) drawMemories();
        else if ("Atualizações".equals(page)) drawUpdates();
        else drawStation();
        if (!landscape()) bottomNavigation(root);
    }

    private void addSidebar(LinearLayout workspace) {
        LinearLayout sidebar = vertical();
        sidebar.setBackgroundColor(Color.rgb(17, 25, 42));
        sidebar.setPadding(dp(8), dp(22), dp(8), dp(10));
        TextView logo = label("◈", 32, CYAN, true);
        logo.setGravity(Gravity.CENTER);
        sidebar.addView(logo);
        TextView lumi = label("LUMI", 12, WHITE, true);
        lumi.setGravity(Gravity.CENTER);
        add(sidebar, lumi, 4);
        String[] symbols = {"⌂", "▦", "◉", "↧"};
        for (int i = 0; i < navItems.length; i++) {
            final String name = navItems[i];
            boolean selected = name.equals(page);
            LinearLayout option = vertical();
            option.setGravity(Gravity.CENTER);
            option.setPadding(dp(2), dp(12), dp(2), dp(12));
            option.setBackground(fill(selected?PANEL_BRIGHT:Color.rgb(17,25,42), 14));
            TextView symbol = label(symbols[i], 22, selected?CYAN:MUTED, true);
            symbol.setGravity(Gravity.CENTER);
            option.addView(symbol);
            TextView caption = label(name, 11, selected?WHITE:MUTED, selected);
            caption.setGravity(Gravity.CENTER);
            option.addView(caption);
            option.setOnClickListener(v -> { page = name; draw(); });
            add(sidebar, option, 15);
        }
        workspace.addView(sidebar, new LinearLayout.LayoutParams(
            dp(110), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void bottomNavigation(LinearLayout root) {
        LinearLayout bar = horizontal();
        bar.setBackgroundColor(Color.rgb(16, 24, 39));
        bar.setPadding(dp(10), dp(9), dp(10), dp(13));
        for (String name : navItems) {
            boolean selected = name.equals(page);
            TextView nav = label(name, landscape() ? 14 : 12,
                selected ? CYAN : MUTED, selected);
            nav.setGravity(Gravity.CENTER);
            nav.setPadding(dp(4), dp(13), dp(4), dp(13));
            nav.setBackground(fill(selected ? PANEL_BRIGHT : Color.rgb(16,24,39), 13));
            nav.setOnClickListener(v -> { page = name; draw(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, dp(50), 1f);
            p.setMargins(dp(3),0,dp(3),0);
            bar.addView(nav,p);
        }
        root.addView(bar);
    }
    private void heading(String title, String subtitle) {
        LinearLayout v = horizontal();
        LinearLayout texts = vertical();
        texts.addView(label(title, 25, WHITE, true));
        add(texts, label(subtitle, 13, MUTED, false), 5);
        addWeighted(v, texts, 1, 0);
        add(stage, v, 3);
    }
    private void sectionTitle(String name, String description) {
        add(stage, label(name, 20, WHITE, true), 24);
        if(description!=null&&!description.isEmpty())
            add(stage,label(description,13,MUTED,false),5);
    }
    private String serviceText() {
        return serviceRunning() ? "Central em execução" : "Central parada";
    }
    private boolean serviceRunning() {
        return getSharedPreferences("hub",MODE_PRIVATE).getBoolean("service_running", false);
    }
    private void switchService() {
        if(serviceRunning()) {
            stopService(new Intent(this,HubService.class));
            getSharedPreferences("hub",MODE_PRIVATE).edit().putBoolean("service_running",false).apply();
        } else {
            try {
                if(Build.VERSION.SDK_INT>=26)
                    startForegroundService(new Intent(this,HubService.class));
                else startService(new Intent(this,HubService.class));
            } catch(Exception e) {
                notice("Serviço Android","Não foi possível iniciar: "+e.getMessage());
            }
        }
        draw();
    }
    private int battery() {
        Intent i = registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(i==null)return -1;
        int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        return level>=0&&scale>0?(int)(level*100f/scale):-1;
    }
    private boolean charging() {
        Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(i==null)return false;
        int st=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        return st==BatteryManager.BATTERY_STATUS_CHARGING
            || st==BatteryManager.BATTERY_STATUS_FULL;
    }
    private String savedIp() {
        return getSharedPreferences("hub",MODE_PRIVATE).getString("tv_ip","");
    }
    private String lastTv() {
        return getSharedPreferences("hub",MODE_PRIVATE)
            .getString("lg_last_status","Por verificar");
    }
    private void drawStation() {
        RebordosaWeather.refreshIfNeeded(this, () -> {
            if (!isFinishing() && "Estação".equals(page)) draw();
        });
        LinearLayout top = horizontal();
        LinearLayout headings = vertical();
        headings.addView(label("LUMI  /  SMART HOME", 12, CYAN, true));
        add(headings, label("Estação", 28, WHITE, true), 3);
        add(headings, label(date("EEEE, d 'de' MMMM"), 12, MUTED, false), 4);
        addWeighted(top, headings, 1, 0);
        top.addView(label(serviceRunning() ? "●  ONLINE" : "●  PARADA", 12,
            serviceRunning() ? GREEN : GOLD, true));
        add(stage, top, 3);

        LinearLayout overview = landscape() ? horizontal() : vertical();
        LinearLayout clock = panel();
        GradientDrawable clockGradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(42,54,92),Color.rgb(25,36,62),Color.rgb(19,37,58)});
        clockGradient.setCornerRadius(dp(20));
        clock.setBackground(clockGradient);
        clock.addView(label("BEM-VINDOS A CASA",12,CYAN,true));
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(WHITE);
        time.setTextSize(landscape()?42:46);
        time.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        add(clock,time,7);
        add(clock,label("Rebordosa · Paredes",12,MUTED,false),5);
        addOverview(overview,clock,0);

        LinearLayout weather = panel();
        RebordosaWeather.State current = RebordosaWeather.read(this);
        weather.addView(label("METEOROLOGIA  ·  EXTERIOR",12,CYAN,true));
        LinearLayout weatherRow = horizontal();
        TextView symbol = label(current.icon,38,GOLD,true);
        addWeighted(weatherRow,symbol,1,0);
        weatherRow.addView(label(current.temperature,33,WHITE,true));
        add(weather,weatherRow,9);
        add(weather,label(current.condition,14,WHITE,true),7);
        add(weather,label("Humidade " + current.humidity,13,MUTED,false),4);
        add(weather,label(current.updated,10,MUTED,false),7);
        addOverview(overview,weather,10);

        LinearLayout status=panel();
        status.addView(label("ESTADO DA CENTRAL",12,VIOLET,true));
        add(status,label("TAB 15   " + battery() + "%",21,WHITE,true),14);
        add(status,label(charging()?"Em carregamento":"A funcionar com bateria",
            12,MUTED,false),5);
        add(status,label("XiaoZhi MCP: "+mcpStatus(),12,
            mcpStatus().startsWith("Ligado")?GREEN:GOLD,false),9);
        add(status,button("Configurar voz",false,this::mcpSettings),10);
        addOverview(overview,status,10);
        add(stage,overview,16);

        sectionTitle("A tua casa","Dispositivos, entretenimento e acessos rápidos.");
        GridLayout grid=new GridLayout(this);
        int cols=landscape()?3:2;
        grid.setColumnCount(cols);
        addTile(grid,0,cols,"TV da Sala",savedIp().isEmpty()?
            "LG webOS · por configurar":"LG webOS · controlo disponível","▣",CYAN,"tv");
        addTile(grid,1,cols,"LUMI DESK","Assistente · voz e expressões","◈",VIOLET,"lumi");
        addTile(grid,2,cols,"Câmara Varanda","IPC-TA22C-G · vídeo por integrar","◉",GREEN,"camera");
        addTile(grid,3,cols,"Spotify","Abrir música e playlists","♫",GREEN,"spotify");
        addTile(grid,4,cols,"Telemóvel Leonardo","Localizar através da Google","⌖",GOLD,"phone_leonardo");
        addTile(grid,5,cols,"Aspirador","Alfawise · por configurar","⌁",MUTED,"vacuum");
        addTile(grid,6,cols,"AC da Sala","Climatização · por configurar","❄",CYAN,"ac");
        addTile(grid,7,cols,"Tab 15","Central · bateria " + battery() + "%","▤",VIOLET,"tablet");
        addTile(grid,8,cols,"Tomada Wi-Fi","Gestão da carga · futuramente","⚡",MUTED,"plug");
        add(stage,grid,12);

        LinearLayout quick=panel();
        quick.addView(label("CONTROLOS RÁPIDOS  /  TV DA SALA",12,MUTED,true));
        LinearLayout commands=horizontal();
        addWeighted(commands,button("Vol. +",false,
            ()->tvCommand("ssap://audio/volumeUp",false)),1,0);
        addWeighted(commands,button("Vol. −",false,
            ()->tvCommand("ssap://audio/volumeDown",false)),1,7);
        addWeighted(commands,button("YouTube",false,
            ()->tvCommand("ssap://system.launcher/launch",false)),1,7);
        addWeighted(commands,button("Desligar",true,()->new AlertDialog.Builder(this)
            .setTitle("Desligar TV da Sala?")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Desligar",(dialog,which)->
                tvCommand("ssap://system/turnOff",false)).show()),1,7);
        add(quick,commands,12);
        add(stage,quick,14);
    }

    private void addOverview(LinearLayout row, View tile, int margin) {
        if (landscape()) addWeighted(row,tile,1,margin);
        else add(row,tile,margin);
    }

    private String mcpStatus() {
        return getSharedPreferences("hub",MODE_PRIVATE)
            .getString("mcp_state","Por configurar");
    }

    private void mcpSettings() {
        SharedPreferences p = getSharedPreferences("hub",MODE_PRIVATE);
        LinearLayout form = vertical();
        form.setPadding(dp(18),dp(5),dp(18),dp(5));
        form.addView(label("Na consola XiaoZhi, abre o agente da LUMI DESK e copia o endereço MCP (wss://api.xiaozhi.me/mcp/?token=...).",14,MUTED,false));
        EditText entry = new EditText(this);
        entry.setSingleLine(true);
        entry.setHint(p.getString("mcp_endpoint","").isEmpty() ?
            "Cola aqui o endereço MCP" : "Ligação configurada · cola aqui para substituir");
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(form,entry,11);
        add(form,label("O token fica no armazenamento privado do Tab 15. Não o publiques no GitHub.",12,MUTED,false),7);
        new AlertDialog.Builder(this).setTitle("LUMI DESK · Voz e MCP")
            .setView(form)
            .setNeutralButton("Remover ligação",(dialog,which)->{
                p.edit().remove("mcp_endpoint")
                    .putString("mcp_state","Por configurar").apply();
                restartMcpService();
            })
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(dialog,which)->{
                String url=entry.getText().toString().trim();
                if(url.isEmpty())return;
                if(!XiaozhiMcpBridge.validEndpoint(url)) {
                    notice("Endereço MCP inválido",
                        "Utiliza o endereço wss://api.xiaozhi.me/mcp/?token=... do teu agente.");
                    return;
                }
                p.edit().putString("mcp_endpoint",url)
                    .putString("mcp_state","A iniciar ligação").apply();
                restartMcpService();
            }).show();
    }

    private void restartMcpService() {
        if(serviceRunning()) {
            Intent service = new Intent(this,HubService.class);
            stopService(service);
            try {
                if(Build.VERSION.SDK_INT>=26)startForegroundService(service);
                else startService(service);
            } catch(Exception error) {
                notice("Serviço Android",error.getMessage());
            }
        }
        draw();
    }

    private void addTile(GridLayout grid,int index,int columns,String name,
                         String detailText,String symbol,int accent,String device) {
        LinearLayout tile=vertical();
        GradientDrawable gradient=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(33,45,67),Color.rgb(23,31,47)});
        gradient.setCornerRadius(dp(18));
        gradient.setStroke(dp(1),STROKE);
        tile.setBackground(gradient);
        tile.setPadding(dp(16),dp(13),dp(13),dp(15));
        LinearLayout upper=horizontal();
        TextView icon=label(symbol,27,accent,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(fill(Color.rgb(43,55,76),13));
        icon.setPadding(dp(7),dp(4),dp(7),dp(4));
        upper.addView(icon, new LinearLayout.LayoutParams(dp(55),dp(47)));
        TextView arrow=label("↗",18,MUTED,false);
        arrow.setGravity(Gravity.RIGHT);
        addWeighted(upper,arrow,1,9);
        tile.addView(upper);
        add(tile,label(name,16,WHITE,true),9);
        add(tile,label(detailText,12,MUTED,false),5);
        tile.setOnClickListener(v->{
            activeDevice=device;
            page="Dispositivos";
            draw();
        });
        GridLayout.LayoutParams p=new GridLayout.LayoutParams();
        p.width=0;
        p.height=ViewGroup.LayoutParams.MATCH_PARENT;
        p.columnSpec=GridLayout.spec(index%columns,1f);
        p.rowSpec=GridLayout.spec(index/columns,1f);
        p.setMargins(dp(4),dp(4),dp(4),dp(4));
        grid.addView(tile,p);
    }

    private void drawDevices() {
        heading("Dispositivos","A tua casa e os serviços que utilizas, num único painel.");
        sectionTitle("Casa e LUMI","Seleciona um equipamento para consultar as opções.");
        GridLayout grid=new GridLayout(this);
        int col=landscape()?3:2;
        grid.setColumnCount(col);
        String[] keys={"tv","lumi","tablet","camera","vacuum","ac","plug",
            "phone_deolinda","phone_tiago","phone_leonardo","spotify"};
        String[] names={"TV da Sala","LUMI DESK","Tab 15","Câmara Varanda",
            "Aspirador","AC da Sala","Tomada Wi-Fi","Telemóvel Deolinda",
            "Telemóvel Tiago","Telemóvel Leonardo","Spotify"};
        String[] descriptions={"LG webOS","Assistente ESP32-S3","Central Android",
            "IPC-TA22C-G","Alfawise","Por configurar","Por instalar",
            "Find Hub Google","Find Hub Google","Find Hub Google","Música"};
        String[] symbols={"▣","◈","▤","◉","⌁","❄","⚡","⌖","⌖","⌖","♫"};
        int[] accents={CYAN,VIOLET,GREEN,GREEN,MUTED,CYAN,MUTED,
            GOLD,GOLD,GOLD,GREEN};
        for(int i=0;i<keys.length;i++){
            final String key=keys[i];
            LinearLayout tile=vertical();
            boolean active=key.equals(activeDevice);
            tile.setBackground(borderFill(active?PANEL_BRIGHT:PANEL,16,
                active?accents[i]:STROKE));
            tile.setPadding(dp(13),dp(13),dp(11),dp(14));
            tile.addView(label(symbols[i],22,accents[i],true));
            add(tile,label(names[i],14,WHITE,true),8);
            add(tile,label(descriptions[i],11,MUTED,false),3);
            tile.setOnClickListener(v->{activeDevice=key;draw();});
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
            lp.width=0;
            lp.height=ViewGroup.LayoutParams.MATCH_PARENT;
            lp.columnSpec=GridLayout.spec(i%col,1f);
            lp.rowSpec=GridLayout.spec(i/col,1f);
            lp.setMargins(dp(3),dp(5),dp(3),dp(5));
            grid.addView(tile,lp);
        }
        add(stage,grid,12);
        if("tv".equals(activeDevice)) tvDetails();
        else if("lumi".equals(activeDevice)) lumiDetails();
        else if("tablet".equals(activeDevice)) tabletDetails();
        else if("camera".equals(activeDevice)) cameraDetails();
        else if("phone_deolinda".equals(activeDevice)) phoneDetails("Deolinda");
        else if("phone_tiago".equals(activeDevice)) phoneDetails("Tiago");
        else if("phone_leonardo".equals(activeDevice)) phoneDetails("Leonardo");
        else if("spotify".equals(activeDevice)) spotifyDetails();
        else if("vacuum".equals(activeDevice)) placeholderDetails("Aspirador Alfawise",
            "É necessário confirmar o modelo exato e a interface de controlo. Os comandos não estão ainda ligados.");
        else if("ac".equals(activeDevice)) placeholderDetails("Ar condicionado da Sala",
            "É necessário identificar a marca e o método de controlo (Wi-Fi ou infravermelhos).");
        else placeholderDetails("Tomada inteligente",
            "A tomada ainda não foi instalada. A gestão da carga do tablet continua desativada.");
    }

    private void phoneDetails(String owner) {
        LinearLayout c=detail();
        c.addView(label("Telemóvel " + owner,22,WHITE,true));
        add(c,label("LOCALIZAÇÃO EXTERNA  ·  GOOGLE FIND HUB",12,CYAN,true),8);
        add(c,label("Abre o localizador oficial da Google. Depois de iniciares sessão com uma conta autorizada, seleciona o telemóvel e usa «Reproduzir som». O Hub não obtém a localização nem faz tocar o equipamento diretamente.",13,MUTED,false),13);
        add(c,button("Localizar / fazer tocar (Google)",true,()->openFindHub(owner)),15);
        if("Leonardo".equals(owner)) add(c,label("A conta e as permissões familiares têm de permitir encontrar o telemóvel do Leonardo. Não é necessário guardar passwords no Hub.",12,MUTED,false),10);
        add(stage,c,16);
    }

    private void openFindHub(String owner) {
        new AlertDialog.Builder(this)
            .setTitle("Localizar telemóvel " + owner)
            .setMessage("O Google Find Hub vai abrir fora do LUMI Hub. Seleciona o telemóvel certo na conta Google autorizada e toca em «Reproduzir som». Este botão não executa diretamente o toque.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Abrir Google Find Hub",(dialog,which)->
                openExternal("https://www.google.com/android/find/"))
            .show();
    }

    private void cameraDetails() {
        LinearLayout c=detail();
        c.addView(label("Câmara da Varanda",22,WHITE,true));
        add(c,label("IPC-TA22C-G  ·  VISUALIZAÇÃO POR INTEGRAR",12,GOLD,true),8);
        add(c,label("A visualização ao vivo dentro do Hub ainda não está ligada. Se esta câmara estiver configurada na Imou Life, podes abrir essa aplicação para aceder ao vídeo.",13,MUTED,false),12);
        add(c,button("Abrir Imou Life",true,()->
            openAppOrWeb("com.mm.android.smartlifeiot",
                "https://play.google.com/store/apps/details?id=com.mm.android.smartlifeiot")),14);
        add(c,label("Numa futura versão poderemos testar vídeo integrado por RTSP/ONVIF, caso o modelo e as definições da câmara o permitam. Nunca introduzas a palavra-passe da câmara num link público.",12,MUTED,false),10);
        add(stage,c,16);
    }

    private void spotifyDetails() {
        LinearLayout c=detail();
        c.addView(label("Spotify",23,WHITE,true));
        add(c,label("MÚSICA  ·  APLICAÇÃO EXTERNA",12,GREEN,true),7);
        add(c,label("Abre o Spotify instalado no tablet. Se não estiver disponível, abre a versão Web para entrares na tua conta e escolheres música.",13,MUTED,false),12);
        add(c,button("Abrir Spotify",true,()->
            openAppOrWeb("com.spotify.music","https://open.spotify.com/")),12);
        add(c,label("A reprodução, os dispositivos Spotify Connect e as playlists ainda não são controlados diretamente pela LUMI.",12,MUTED,false),10);
        add(stage,c,16);
    }

    private void openAppOrWeb(String packageName,String fallback) {
        try {
            Intent launcher=getPackageManager().getLaunchIntentForPackage(packageName);
            if(launcher!=null){startActivity(launcher);return;}
        }catch(Exception ignored){}
        openExternal(fallback);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }catch(Exception error) {
            notice("Abrir serviço","Não foi possível abrir a aplicação ou página: "+error.getMessage());
        }
    }

    private void placeholderDetails(String title,String explanation) {
        LinearLayout c=detail();
        c.addView(label(title,20,WHITE,true));
        add(c,label("POR CONFIGURAR",12,GOLD,true),9);
        add(c,label(explanation,14,MUTED,false),10);
        add(stage,c,16);
    }

    private void tvDetails() {
        LinearLayout card=detail();
        card.addView(label("TV da Sala",23,WHITE,true));
        add(card,label("LG webOS • Ligação local segura",13,CYAN,true),6);
        add(card,label("IP: "+(savedIp().isEmpty()?"não definido":savedIp())
            +"  ·  Estado: "+lastTv(),12,MUTED,false),11);
        add(card,button("Configurar IP, MAC e ligação",false,this::tvSettings),12);
        LinearLayout r=horizontal();
        addWeighted(r,button("Emparelhar",true,()->tvCommand(null,true)),1,0);
        addWeighted(r,button("Verificar ligação",false,this::tvCheck),1,8);
        add(card,r,9);
        add(card,label("CONTROLO",12,MUTED,true),18);
        LinearLayout vol=horizontal();
        addWeighted(vol,button("Volume +",false,
            ()->tvCommand("ssap://audio/volumeUp",false)),1,0);
        addWeighted(vol,button("Volume −",false,
            ()->tvCommand("ssap://audio/volumeDown",false)),1,8);
        add(card,vol,8);
        LinearLayout media=horizontal();
        addWeighted(media,button("Silenciar",false,
            ()->tvCommand("ssap://audio/setMute",false)),1,0);
        addWeighted(media,button("YouTube",false,
            ()->tvCommand("ssap://system.launcher/launch",false)),1,8);
        add(card,media,8);
        LinearLayout power=horizontal();
        addWeighted(power,button("Ligar TV  ·  WOL",true,this::tvWake),1,0);
        addWeighted(power,button("Desligar TV",false,()->new AlertDialog.Builder(this)
            .setTitle("Desligar TV da Sala?")
            .setMessage("Enviar comando de desligar à LG?")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Desligar",(d,w)->
                tvCommand("ssap://system/turnOff",false)).show()),1,8);
        add(card,power,9);
        add(card,label("Para ligar por Wake-on-LAN, indica o MAC da TV e ativa a opção "
            +"de ligar pela rede nas definições da LG. O envio não garante que a TV acorde.",12,MUTED,false),13);
        add(stage,card,16);
    }
    private void tvSettings() {
        SharedPreferences p=getSharedPreferences("hub",MODE_PRIVATE);
        LinearLayout form=vertical();
        form.setPadding(dp(20),dp(8),dp(20),dp(4));
        EditText ip=new EditText(this);
        ip.setSingleLine(true);
        ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        ip.setHint("IP da LG (ex.: 192.168.1.123)");
        ip.setText(p.getString("tv_ip",""));
        form.addView(ip);
        EditText mac=new EditText(this);
        mac.setSingleLine(true);
        mac.setHint("MAC da TV (AA:BB:CC:DD:EE:FF)");
        mac.setText(p.getString("tv_mac",""));
        add(form,mac,11);
        add(form,label("O endereço IP e o MAC ficam guardados apenas no Tab 15.",12,
            Color.rgb(91,96,110),false),7);
        new AlertDialog.Builder(this).setTitle("TV da Sala • Rede")
            .setView(form).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                String ipVal=ip.getText().toString().trim();
                String macVal=mac.getText().toString().trim().toUpperCase(Locale.ROOT);
                if(!validIpv4(ipVal)) {
                    notice("IP inválido","Introduz um endereço IPv4 válido.");
                    return;
                }
                if(!macVal.isEmpty()&&!validMac(macVal)) {
                    notice("MAC inválido","Usa seis pares hexadecimais, por exemplo AA:BB:CC:DD:EE:FF.");
                    return;
                }
                String oldIp=p.getString("tv_ip","");
                SharedPreferences.Editor edit=p.edit()
                    .putString("tv_ip",ipVal).putString("tv_mac",macVal);
                if(!ipVal.equals(oldIp)){
                    edit.remove("lg_client_key").remove("lg_cert_pin").remove("lg_last_status");
                }
                edit.apply();
                draw();
            }).show();
    }
    private boolean validIpv4(String host) {
        if(!host.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))return false;
        for(String octet:host.split("\\."))if(Integer.parseInt(octet)>255)return false;
        return true;
    }
    private boolean validMac(String mac) {
        return mac.matches("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");
    }
    private void tvWake() {
        SharedPreferences p=getSharedPreferences("hub",MODE_PRIVATE);
        String mac=p.getString("tv_mac","");
        if(!validMac(mac)){
            notice("Configurar MAC","Guarda primeiro o endereço MAC da TV da Sala nas definições do dispositivo.");
            return;
        }
        WakeOnLan.send(mac,p.getString("tv_ip",""),message->
            runOnUiThread(()->notice("Ligar TV • Wake-on-LAN",message)));
    }
    private void tvCheck() {
        String ip=savedIp();
        if(!validIpv4(ip)){tvSettings();return;}
        Toast.makeText(this,"A verificar serviço webOS...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            boolean reachable=false;
            try(Socket s=new Socket()){
                s.connect(new InetSocketAddress(ip,3001),2000);
                reachable=true;
            }catch(Exception ignored){}
            final boolean ok=reachable;
            String msg=ok?"Serviço webOS acessível pela rede.":"Serviço não acessível agora. "
                +"A TV pode estar desligada ou a rede indisponível.";
            getSharedPreferences("hub",MODE_PRIVATE).edit()
                .putString("lg_last_status",msg).apply();
            runOnUiThread(()->notice("TV da Sala • Diagnóstico",msg));
        },"LumiTvCheck").start();
    }
    private void tvCommand(String action,boolean pair) {
        SharedPreferences p=getSharedPreferences("hub",MODE_PRIVATE);
        String ip=p.getString("tv_ip","");
        if(!validIpv4(ip)){tvSettings();return;}
        String pin=p.getString("lg_cert_pin","");
        if(pin.isEmpty()){
            Toast.makeText(this,"A ler certificado LG...",Toast.LENGTH_SHORT).show();
            WebOsController.inspectCertificate(ip,(fingerprint,error)->{
                runOnUiThread(()->{
                    if(error!=null){
                        notice("Certificado LG", "Falha WSS 3001: "+error);
                        return;
                    }
                    new AlertDialog.Builder(this).setTitle("Confiar na TV da Sala?")
                        .setMessage("Confirma que "+ip+" pertence à tua LG.\n\nSHA-256: "
                            +fingerprint+"\n\nO certificado será associado à TV.")
                        .setNegativeButton("Cancelar",null)
                        .setPositiveButton("Confiar",(d,w)->{
                            p.edit().putString("lg_cert_pin",fingerprint).apply();
                            tvCommandDirect(ip,action,pair);
                        }).show();
                });
            });
        }else tvCommandDirect(ip,action,pair);
    }
    private void tvCommandDirect(String ip,String action,boolean pair) {
        SharedPreferences p=getSharedPreferences("hub",MODE_PRIVATE);
        Toast.makeText(this,pair?"Aceita o emparelhamento na LG...":
            "A enviar comando à TV da Sala...",Toast.LENGTH_SHORT).show();
        WebOsController.request(ip,p.getString("lg_client_key",""),
            p.getString("lg_cert_pin",""),action,pair,(message,key)->
                runOnUiThread(()->{
                    SharedPreferences.Editor editor=p.edit()
                        .putString("lg_last_status",message);
                    if(key!=null&&!key.isEmpty())editor.putString("lg_client_key",key);
                    editor.apply();
                    notice("TV da Sala",message);
                }));
    }
    private void lumiDetails() {
        LinearLayout c=detail();
        c.addView(label("LUMI DESK",23,WHITE,true));
        add(c,label("ESP32-S3 • Assistente de secretária",13,VIOLET,true),7);
        add(c,label("Estado: não verificado — a ligação direta entre o ESP32 e o tablet "
            +"ainda não existe.",13,MUTED,false),10);
        add(c,label("EXPRESSÕES",12,MUTED,true),18);
        GridLayout g=new GridLayout(this);
        g.setColumnCount(3);
        String[] icons={"☺","♥","☾","!","★","◡"};
        String[] moods={"Feliz","Amor","Sonolenta","Surpresa","Confiante","Triste"};
        for(int i=0;i<moods.length;i++){
            final String mood=moods[i];
            LinearLayout b=vertical();
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(7),dp(13),dp(7),dp(13));
            b.setBackground(borderFill(PANEL_BRIGHT,13,STROKE));
            TextView symbol=label(icons[i],22,CYAN,true);
            symbol.setGravity(Gravity.CENTER);
            b.addView(symbol);
            TextView name=label(mood,12,WHITE,true);
            name.setGravity(Gravity.CENTER);
            b.addView(name);
            b.setOnClickListener(v->notice(mood,"Pré-visualização apenas. "
                +"O firmware da LUMI DESK ainda precisa de um canal de comandos "
                +"para mudar os GIFs remotamente."));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
            lp.columnSpec=GridLayout.spec(i%3,1f);
            lp.rowSpec=GridLayout.spec(i/3,1f);
            lp.width=0;
            lp.setMargins(dp(3),dp(3),dp(3),dp(3));
            g.addView(b,lp);
        }
        add(c,g,12);
        add(c,label("COMANDOS POR VOZ · XIAOZHI MCP",12,VIOLET,true),16);
        add(c,label("Estado: "+mcpStatus(),13,MUTED,false),6);
        add(c,button("Configurar ligação de voz",true,this::mcpSettings),10);
        add(c,label("O Hub partilha comandos da TV com o agente XiaoZhi por MCP. É necessário configurar o endereço MCP e manter a central ativa. A comunicação direta com os GIFs do ESP32 ainda está por implementar.",12,MUTED,false),10);
        add(c,button("Consultar diagnósticos e logs do Hub",false,this::diagnostics),14);
        add(c,label("Os logs internos do ESP32 e o controlo remoto dos GIFs só "
            +"ficarão disponíveis depois da integração da LUMI DESK.",12,MUTED,false),10);
        add(stage,c,16);
    }
    private void diagnostics() {
        SharedPreferences p=getSharedPreferences("hub",MODE_PRIVATE);
        long updated=p.getLong("last_update",0);
        String last=updated==0?"sem dados":new SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",PT).format(new Date(updated));
        notice("Diagnóstico LUMI Hub",
            "Serviço Android: "+serviceText()
            +"\nBateria: "+battery()+"%"
            +"\nCarregamento: "+(charging()?"sim":"não")
            +"\nÚltima leitura: "+last
            +"\nTV da Sala: "+lastTv()
            +"\nLUMI DESK: sem ligação de diagnóstico"
            +"\nLogs internos ESP32: não disponíveis");
    }
    private void tabletDetails() {
        LinearLayout c=detail();
        c.addView(label("Blackview Tab 15",23,WHITE,true));
        add(c,label("Central LUMI Hub • Android",13,GREEN,true),7);
        add(c,label(battery()+"%",44,WHITE,true),11);
        add(c,label(charging()?"A carregar":"A funcionar com bateria",14,MUTED,false),4);
        add(c,label(serviceText(),16,serviceRunning()?GREEN:GOLD,true),17);
        add(c,button(serviceRunning()?"Parar central":"Iniciar central",
            true,this::switchService),10);
        add(c,button("Ver logs e diagnóstico",false,this::diagnostics),8);
        add(c,label("Gestão futura da bateria: ligar a 40%, desligar a 80%. "
            +"Sem tomada inteligente, a aplicação apenas monitoriza.",13,MUTED,false),14);
        add(stage,c,16);
    }

    private void drawMemories() {
        heading("Memórias","Informações que decides guardar no Tab 15.");
        LinearLayout c=detail();
        c.addView(label(memories.count()+" memórias guardadas",20,WHITE,true));
        add(c,label("Estas memórias estão guardadas localmente. Ainda não são "
            +"partilhadas com o cérebro da LUMI DESK.",13,MUTED,false),8);
        add(c,button("+ Guardar memória",true,this::newMemory),13);
        add(c,button("Importar memórias (ficheiro JSON)",false,this::chooseMemoryFile),8);
        add(stage,c,18);
        List<MemoryDb.Item> list=memories.all();
        if(list.isEmpty()) {
            LinearLayout blank=panel();
            blank.addView(label("Ainda não tens memórias guardadas.",15,MUTED,false));
            add(stage,blank,12);
        }
        for(MemoryDb.Item item:list){
            LinearLayout tile=panel();
            tile.addView(label(item.title,18,WHITE,true));
            add(tile,label(item.detail,14,MUTED,false),7);
            add(tile,button("Eliminar",false,()->new AlertDialog.Builder(this)
                .setTitle("Eliminar memória?")
                .setMessage("Apagar "+item.title+" definitivamente?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Eliminar",(d,w)->{
                    memories.remove(item.id);draw();
                }).show()),11);
            add(stage,tile,10);
        }
    }
    private void newMemory() {
        LinearLayout form=vertical();
        form.setPadding(dp(20),dp(8),dp(20),dp(4));
        EditText title=new EditText(this);
        title.setHint("Título");
        title.setSingleLine(true);
        form.addView(title);
        EditText detail=new EditText(this);
        detail.setHint("O que deve ser recordado?");
        detail.setMinLines(3);
        add(form,detail,10);
        new AlertDialog.Builder(this).setTitle("Nova memória")
            .setView(form)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                if(title.getText().toString().trim().isEmpty()){
                    notice("Título obrigatório","Indica um título para guardar a memória.");
                    return;
                }
                memories.save(title.getText().toString(),detail.getText().toString());
                draw();
            }).show();
    }

    private void drawUpdates() {
        heading("Atualizações","Versões oficiais do LUMI Hub disponíveis no GitHub.");
        LinearLayout c=panel();
        c.addView(label("Versão instalada: "+versionName(),18,WHITE,true));
        add(c,label("Só as versões Android assinadas são apresentadas aqui. "
            +"O firmware da LUMI DESK não é instalado por esta opção.",13,MUTED,false),9);
        add(c,button("Procurar atualizações",true,this::loadReleases),14);
        add(stage,c,16);
        LinearLayout list=vertical();
        list.setId(17331);
        add(stage,list,12);
    }
    private String versionName() {
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){return "desconhecida";}
    }
    private void loadReleases() {
        LinearLayout holder=findViewById(17331);
        if(holder==null)return;
        holder.removeAllViews();
        add(holder,label("A consultar o GitHub...",13,MUTED,false),10);
        new Thread(()->{
            JSONArray releases=null;
            String problem=null;
            try{
                HttpURLConnection con=(HttpURLConnection)new URL(
                    "https://api.github.com/repos/TiagoM20/xiaozhi-esp32/releases?per_page=40"
                ).openConnection();
                con.setRequestProperty("Accept","application/vnd.github+json");
                con.setRequestProperty("User-Agent","LUMI-Hub-Android");
                con.setConnectTimeout(7000);
                con.setReadTimeout(10000);
                try{
                    if(con.getResponseCode()!=200)
                        throw new Exception("HTTP "+con.getResponseCode());
                    StringBuilder b=new StringBuilder();
                    try(BufferedReader rd=new BufferedReader(
                        new InputStreamReader(con.getInputStream(),"UTF-8"))){
                        String line;
                        while((line=rd.readLine())!=null)b.append(line);
                    }
                    releases=new JSONArray(b.toString());
                }finally{con.disconnect();}
            }catch(Exception ex){problem=ex.getMessage();}
            final JSONArray result=releases;
            final String error=problem;
            runOnUiThread(()->{
                if(!"Atualizações".equals(page))return;
                LinearLayout area=findViewById(17331);
                if(area==null)return;
                area.removeAllViews();
                if(error!=null){add(area,label("Erro: "+error,14,GOLD,false),6);return;}
                int shown=0;
                for(int i=0;i<result.length();i++){
                    JSONObject rel=result.optJSONObject(i);
                    if(rel==null||rel.optBoolean("draft"))continue;
                    String tag=rel.optString("tag_name","");
                    if(!tag.startsWith("lumi-hub-v"))continue;
                    String apkUrl="";
                    JSONArray assets=rel.optJSONArray("assets");
                    if(assets!=null)for(int j=0;j<assets.length();j++){
                        JSONObject asset=assets.optJSONObject(j);
                        if(asset!=null&&asset.optString("name","").endsWith(".apk")){
                            apkUrl=asset.optString("browser_download_url","");
                            break;
                        }
                    }
                    LinearLayout tile=panel();
                    tile.addView(label(rel.optString("name",tag),17,WHITE,true));
                    add(tile,label(tag,12,CYAN,false),5);
                    add(tile,label(rel.optString("body","Sem descrição"),12,MUTED,false),7);
                    if(!apkUrl.isEmpty()){
                        String link=apkUrl;
                        add(tile,button("Descarregar APK",true,()->downloadApk(link,tag)),11);
                    }
                    add(area,tile,10);
                    shown++;
                }
                if(shown==0)add(area,label("Sem versões Android publicadas.",14,MUTED,false),10);
            });
        },"LumiHubReleases").start();
    }
    private void downloadApk(String link,String tag) {
        if(!link.startsWith("https://github.com/TiagoM20/xiaozhi-esp32/releases/download/")){
            notice("URL recusado","O APK não pertence ao repositório autorizado.");
            return;
        }
        try{
            DownloadManager.Request req=new DownloadManager.Request(Uri.parse(link));
            req.setTitle("LUMI Hub "+tag);
            req.setDescription("Atualização oficial do LUMI Hub");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                "LUMI-Hub-"+tag+".apk");
            DownloadManager manager=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(req);
            notice("Transferência iniciada","Abre Transferências quando concluir e confirma "
                +"a instalação. Não desinstales a versão anterior.");
        }catch(Exception ex){notice("Erro no download",ex.getMessage());}
    }
}
