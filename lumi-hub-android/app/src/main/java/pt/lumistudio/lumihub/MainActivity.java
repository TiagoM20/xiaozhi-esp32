package pt.lumistudio.lumihub;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.Manifest;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.ImageView;
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
    private static int BG = Color.rgb(12, 18, 31);
    private static int PANEL = Color.rgb(24, 32, 48);
    private static int PANEL_BRIGHT = Color.rgb(34, 44, 62);
    private static int WHITE = Color.rgb(245, 248, 255);
    private static int MUTED = Color.rgb(162, 175, 195);
    private static int CYAN = Color.rgb(114, 228, 239);
    private static int GOLD = Color.rgb(255, 187, 119);
    private static int VIOLET = Color.rgb(174, 154, 251);
    private static int GREEN = Color.rgb(139, 223, 173);
    private static int STROKE = Color.rgb(52, 64, 85);
    private static final Locale PT = new Locale("pt", "PT");

    private final String[] navItems = {"Casa", "Dispositivos", "Lumi"};
    private String page = "Casa";
    private String activeDevice = "tv";
    private LinearLayout stage;
    private TextView tvFeedback;
    private HubThemes.Palette currentPalette=HubThemes.get(0);
    private int themeId=0;
    private String updatesTab="Versões";
    private static final int CAMERA_REQUEST=405;
    private static final int ADMIN_REQUEST=406;
    private MemoryDb memories;
    private LumiLibraryDb library;
    private String libraryTab = "Histórias";
    private static final int PICK_MEMORY_JSON = 9441;
    private static final int PICK_FLOORPLAN = 9442;
    private final android.os.Handler weatherHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            if ("Casa".equals(page)) {
                RebordosaWeather.refreshIfNeeded(MainActivity.this, () -> {
                    if (!isFinishing() && "Casa".equals(page)) draw();
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
        library = new LumiLibraryDb(this);
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
        if (library != null) library.close();
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
        if(requestCode==PICK_FLOORPLAN && resultCode==RESULT_OK && data!=null){
            Uri chosen=data.getData();
            if(chosen!=null){
                try{
                    getContentResolver().takePersistableUriPermission(chosen,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getSharedPreferences("hub",MODE_PRIVATE).edit()
                        .putString("house_floorplan_uri",chosen.toString()).apply();
                    page="Planta";draw();
                }catch(Exception error){
                    notice("Planta da casa","Não foi possível guardar o acesso à imagem.");
                }
            }
            return;
        }
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

    private void applyTheme() {
        int index=getSharedPreferences("hub",MODE_PRIVATE).getInt("hub_theme",0);
        themeId=HubThemes.bounded(index);
        currentPalette=HubThemes.get(themeId);
        BG=currentPalette.background;
        PANEL=currentPalette.panel;
        PANEL_BRIGHT=currentPalette.bright;
        WHITE=currentPalette.text;
        MUTED=currentPalette.muted;
        CYAN=currentPalette.accent;
        GOLD=currentPalette.gold;
        VIOLET=currentPalette.violet;
        GREEN=currentPalette.green;
        STROKE=currentPalette.stroke;
        getWindow().setStatusBarColor(currentPalette.nav);
        getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=26){
            int flags=(themeId==2 || themeId==5 || themeId==7 || themeId==9)?
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }
    private void selectHubTheme(int selected) {
        int index=HubThemes.bounded(selected);
        getSharedPreferences("hub",MODE_PRIVATE).edit().putInt("hub_theme",index).apply();
        updatesTab="Temas";
        draw();
    }

    private void draw() {
        applyTheme();
        tvFeedback=null;
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        setContentView(root);

        // Modo mural: navbar estreita em cima, a largura total para os tiles.
        if (landscape()) topNavigation(root);
        else portraitHeader(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        stage = vertical();
        stage.setPadding(dp(landscape()?17:14), dp(landscape()?12:13),
            dp(landscape()?17:14), dp(20));
        scroll.addView(stage);
        if ("Dispositivos".equals(page)) drawDevices();
        else if ("Lumi".equals(page)) drawLumi();
        else if ("Biblioteca".equals(page)) drawLibrary();
        else if ("Pessoas".equals(page)) drawPeople();
        else if ("Planta".equals(page)) drawFloorplan();
        else if ("Definições".equals(page)) drawSettings();
        else if ("Atualizações".equals(page)) drawUpdates();
        else drawStation();
        if (!landscape()) bottomNavigation(root);
    }

    private void portraitHeader(LinearLayout root) {
        LinearLayout bar=horizontal();
        bar.setPadding(dp(17),dp(5),dp(14),dp(5));
        bar.setBackgroundColor(currentPalette.nav);
        TextView brand=label("⌂  LUMI HUB",18,WHITE,true);
        brand.setOnClickListener(v->{page="Casa";draw();});
        addWeighted(bar,brand,1,0);
        bar.addView(button("⚙  Definições",false,()->{
            page="Definições";draw();
        }));
        root.addView(bar,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(58)));
    }

    private void topNavigation(LinearLayout root) {
        LinearLayout bar=horizontal();
        bar.setPadding(dp(18),dp(7),dp(15),dp(7));
        bar.setBackgroundColor(currentPalette.nav);
        TextView brand=label("⌂   LUMI HUB",18,WHITE,true);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setOnClickListener(v->{page="Casa";draw();});
        addWeighted(bar,brand,1,0);
        for(String name:navItems) {
            final String target=name;
            boolean active=name.equals(page);
            TextView tab=label(name,12,
                active?WHITE:MUTED,active);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(12),dp(11),dp(12),dp(11));
            tab.setBackground(fill(active?currentPalette.activeTab:
                currentPalette.nav,9));
            tab.setOnClickListener(v->{page=target;draw();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,dp(44));
            lp.leftMargin=dp(5);
            bar.addView(tab,lp);
        }
        bar.addView(button("⚙",false,()->{page="Definições";draw();}));
        root.addView(bar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(58)));
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
        String[] symbols = {"⌂", "▦", "◉"};
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
        bar.setBackgroundColor(currentPalette.nav);
        bar.setPadding(dp(10), dp(9), dp(10), dp(13));
        for (String name : navItems) {
            boolean selected = name.equals(page);
            TextView nav = label(name, landscape() ? 14 : 12,
                selected ? CYAN : MUTED, selected);
            nav.setGravity(Gravity.CENTER);
            nav.setPadding(dp(4), dp(13), dp(4), dp(13));
            nav.setBackground(fill(selected ? PANEL_BRIGHT : currentPalette.nav, 13));
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
        // A meteorologia actualiza o cartao sem alterar a configuracao.
        RebordosaWeather.refreshIfNeeded(this, () -> {
            if (!isFinishing() && "Casa".equals(page)) draw();
        });
        LinearLayout topControls=horizontal();
        addWeighted(topControls,new TextView(this),1,0);
        topControls.addView(button("Apagar ecrã",false,this::screenOff));
        add(stage,topControls,1);

        // Layout tipo painel mural: hora+meteorologia à esquerda, estado à direita.
        LinearLayout top = landscape()?horizontal():vertical();
        top.setGravity(Gravity.TOP);
        LinearLayout hero=vertical();
        GradientDrawable banner=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{currentPalette.bannerA,currentPalette.bannerB,currentPalette.bannerC});
        banner.setCornerRadius(dp(13));
        hero.setBackground(banner);
        hero.setPadding(dp(22),dp(18),dp(20),dp(16));
        hero.setMinimumHeight(dp(187));
        hero.addView(label("A TUA CASA",13,themeId==2?currentPalette.tileText:Color.rgb(198,223,255),true));

        LinearLayout info=horizontal();
        LinearLayout clockGroup=vertical();
        TextClock time=new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(landscape()?53:49);
        time.setTextColor(themeId==2?currentPalette.tileText:WHITE);
        time.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        clockGroup.addView(time);
        add(clockGroup,label(date("EEEE, d 'de' MMMM"),13,
            themeId==2?currentPalette.tileText:WHITE,false),1);
        addWeighted(info,clockGroup,1,0);

        RebordosaWeather.State current=RebordosaWeather.read(this);
        LinearLayout climate=vertical();
        climate.setGravity(Gravity.RIGHT);
        TextView symbol=label(current.icon,landscape()?33:30,
            themeId==2?currentPalette.tileText:WHITE,true);
        symbol.setGravity(Gravity.RIGHT);
        climate.addView(symbol);
        TextView degrees=label(current.temperature,26,
            themeId==2?currentPalette.tileText:WHITE,true);
        degrees.setGravity(Gravity.RIGHT);
        climate.addView(degrees);
        TextView text=label(current.condition,11,
            themeId==2?currentPalette.tileText:WHITE,false);
        text.setGravity(Gravity.RIGHT);
        climate.addView(text);
        info.addView(climate);
        add(hero,info,12);
        add(hero,label("HUMIDADE EXTERIOR   " + current.humidity
            + "     ·     " + current.updated,11,
            themeId==2?currentPalette.tileText:Color.rgb(218,232,248),false),13);
        addOverview(top,hero,0);

        LinearLayout noticePanel=vertical();
        noticePanel.setPadding(dp(20),dp(16),dp(18),dp(16));
        noticePanel.setMinimumHeight(dp(187));
        noticePanel.setBackground(borderFill(currentPalette.statusPanel,13,STROKE));
        noticePanel.addView(label("ESTADO DA CASA",13,WHITE,true));
        add(noticePanel,label("●   LUMI DESK    ·    " + mcpStatus(),
            12,mcpStatus().startsWith("Ligado")?GREEN:GOLD,false),12);
        add(noticePanel,label("●   TV DA SALA    ·    " +
            (savedIp().isEmpty()?"Por configurar":"Ligação por verificar"),
            12,savedIp().isEmpty()?GOLD:CYAN,false),8);
        add(noticePanel,label("●   " + android.os.Build.MODEL + "    ·    " + battery() + "% de bateria",
            12,GREEN,false),8);
        add(noticePanel,label("Equipamentos: abre os cartões para controlar.",12,MUTED,false),10);
        addOverview(top,noticePanel,11);
        add(stage,top,12);

        LinearLayout heading=horizontal();
        LinearLayout words=vertical();
        words.addView(label("EQUIPAMENTOS FAVORITOS",20,WHITE,true));
        add(words,label("O essencial, sem menus técnicos",12,MUTED,false),3);
        addWeighted(heading,words,1,0);
        heading.addView(button("Ver todos",false,()->{page="Dispositivos";draw();}));
        add(stage,heading,17);

        GridLayout grid=new GridLayout(this);
        int cols=landscape()?5:2;
        grid.setColumnCount(cols);
        String[] favorites={"tv","vacuum","camera_sala","camera","ac","spotify"};
        int index=0;
        for(String key:favorites) {
            for(HomeDevices.Device device:HomeDevices.ALL) {
                if(key.equals(device.key)) {
                    addHomeTile(grid,device,index++,cols);
                    break;
                }
            }
        }
        add(stage,grid,11);

        LinearLayout quick=horizontal();
        addWeighted(quick,visualShortcut("Pessoas da casa","Telemóveis e família",
            "people",7,()->{page="Pessoas";draw();}),1,0);
        addWeighted(quick,visualShortcut("Planta da casa","Vista por divisão",
            "floorplan",8,()->{page="Planta";draw();}),1,10);
        add(stage,quick,18);
    }

    private LinearLayout visualShortcut(String name,String subtitle,String icon,
                                      int gradientIndex,Runnable action) {
        LinearLayout result=vertical();
        result.setPadding(dp(17),dp(16),dp(14),dp(16));
        GradientDrawable backdrop=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            HubThemes.tileGradient(themeId,gradientIndex));
        backdrop.setCornerRadius(dp(19));
        backdrop.setStroke(dp(1),currentPalette.tileBorder);
        result.setBackground(backdrop);
        result.addView(new HomeIconView(this,icon,currentPalette.tileIcon),
            new LinearLayout.LayoutParams(dp(42),dp(42)));
        add(result,label(name,17,currentPalette.tileText,true),8);
        add(result,label(subtitle,12,currentPalette.tileText,false),4);
        result.setMinimumHeight(dp(142));
        result.setOnClickListener(v->action.run());
        return result;
    }

    private void addOverview(LinearLayout row, View tile, int margin) {
        if (landscape()) addWeighted(row,tile,1,margin);
        else add(row,tile,margin);
    }

    private String deviceDescription(HomeDevices.Device d) {
        switch(d.key) {
            case "tv": return savedIp().isEmpty() ? "Por configurar" : "Ligação por verificar";
            case "lumi": return "MCP · " + mcpStatus();
            case "tablet": return "Bateria " + battery() + "%";
            case "camera": return "Vídeo por integrar";
            case "camera_sala": return "Imagem local · ao tocar";
            case "vacuum": return "Por configurar";
            case "phone_deolinda":
            case "phone_tiago":
            case "phone_leonardo": return "Localizar via Google";
            case "spotify": return "Abrir música";
            case "ac": return "Por configurar";
            case "plug": return "Por instalar";
            case "more": return "Futuros dispositivos";
            default: return d.subtitle;
        }
    }

    private int deviceAccent(HomeDevices.Device d) {
        switch(d.key) {
            case "lumi": return VIOLET;
            case "phone_deolinda":
            case "phone_tiago":
            case "phone_leonardo": return GOLD;
            case "vacuum":
            case "more": return CYAN;
            case "spotify": return GREEN;
            default: return Color.rgb(173,217,255);
        }
    }

    private void addHomeTile(GridLayout grid, HomeDevices.Device item,
                             int index,int columns) {
        LinearLayout tile=vertical();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(5),dp(9),dp(5),dp(8));
        GradientDrawable gradient=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            HubThemes.tileGradient(themeId,index));
        gradient.setCornerRadius(dp(11));
        gradient.setStroke(dp("Dispositivos".equals(page) && item.key.equals(activeDevice)?2:1),
            "Dispositivos".equals(page) && item.key.equals(activeDevice)
                ?CYAN:currentPalette.tileBorder);
        tile.setBackground(gradient);

        HomeIconView icon=new HomeIconView(this,item.icon,currentPalette.tileIcon);
        tile.addView(icon,new LinearLayout.LayoutParams(dp(47),dp(47)));
        TextView name=label(item.name,13,currentPalette.tileText,true);
        name.setMaxLines(2);
        name.setGravity(Gravity.CENTER);
        add(tile,name,6);
        TextView status=label(deviceDescription(item),11,
            themeId==2?currentPalette.tileText:deviceAccent(item),false);
        status.setMaxLines(2);
        status.setGravity(Gravity.CENTER);
        add(tile,status,3);
        tile.setOnClickListener(v->{
            activeDevice=item.key;
            page="Dispositivos";
            draw();
        });
        GridLayout.LayoutParams params=new GridLayout.LayoutParams();
        params.width=0;
        params.height=dp(landscape()?128:125);
        params.columnSpec=GridLayout.spec(index%columns,1f);
        params.rowSpec=GridLayout.spec(index/columns,1f);
        params.setMargins(dp(4),dp(4),dp(4),dp(4));
        grid.addView(tile,params);
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
        add(form,label("O token fica no armazenamento privado deste dispositivo. Não o publiques no GitHub.",12,MUTED,false),7);
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


    private void drawLumi() {
        heading("Lumi","O essencial para a tua assistente.");
        LinearLayout info=panel();
        info.addView(label("LUMI DESK  ·  ESP32-S3",20,WHITE,true));
        add(info,label("Raquel PT-PT · Agente XiaoZhi",14,CYAN,true),7);
        add(info,label("A personalidade e a voz reais são configuradas no XiaoZhi. "
            +"O tablet não altera a conta sem uma integração autorizada.",13,MUTED,false),7);
        add(info,button("Configurar voz e agente no XiaoZhi",true,
            ()->openExternal("https://xiaozhi.me/console/agents")),11);
        add(stage,info,13);

        LinearLayout personality=panel();
        personality.addView(label("Personalidade da LUMI",18,WHITE,true));
        add(personality,label("Prepara as instruções aqui e aplica-as na consola oficial.",13,MUTED,false),6);
        add(personality,button("Editar rascunho de personalidade",false,
            this::editXiaoZhiPersonality),9);
        add(stage,personality,12);

        LinearLayout contents=panel();
        contents.addView(label("Histórias e memórias",18,WHITE,true));
        add(contents,label("Histórias, personagens e memórias locais destinadas à DESK.",13,MUTED,false),6);
        add(contents,button("Gerir conteúdos da LUMI",false,()->{
            page="Biblioteca";draw();
        }),10);
        add(contents,button("Preparar contexto para XiaoZhi",false,
            this::copyLumiContextToXiaoZhi),8);
        add(stage,contents,12);

        LinearLayout maintain=panel();
        maintain.addView(label("Firmware e manutenção",18,WHITE,true));
        add(maintain,label("Backups e recuperação no gestor Windows opcional. "
            +"A wake word «Lumi» continua pendente.",13,MUTED,false),7);
        add(maintain,button("Ferramentas de manutenção",false,this::showDeskMaintenance),9);
        add(stage,maintain,12);

    }

    private void showDeskMaintenance() {
        new AlertDialog.Builder(this)
            .setTitle("Manutenção LUMI DESK")
            .setMessage("O LUMI CORE mantém utilidade como ferramenta Windows de manutenção: "
                +"backups integrais, recuperação e instalação de firmware com um clique.\n\n"
                +"O PC não precisa de ficar ligado para usar a LUMI.\n\n"
                +"Não instales versões experimentais de ativação "
                +"enquanto aguardamos o modelo português treinado «Lumi». "
                +"Mantém a cópia integral de segurança.")
            .setNegativeButton("Fechar",null)
            .setPositiveButton("Ver gestores oficiais",(d,w)->openExternal(
                "https://github.com/TiagoM20/xiaozhi-esp32/releases"))
            .show();
    }


    private void drawLibrary() {
        heading("Conteúdos da LUMI","Histórias, personagens e memórias locais.");
        LinearLayout tabs=horizontal();
        for(String tab:new String[]{"Histórias","Personagens","Memórias"}) {
            addWeighted(tabs,button(tab,tab.equals(libraryTab),()->{
                libraryTab=tab;draw();
            }),1,"Histórias".equals(tab)?0:5);
        }
        add(stage,tabs,12);
        add(stage,button("‹  Voltar à LUMI DESK",false,()->{
            page="Lumi";draw();
        }),8);
        if("Memórias".equals(libraryTab)){
            drawMemories();
            return;
        }
        final String kind="Histórias".equals(libraryTab)?"Historia":"Personagem";
        LinearLayout card=panel();
        card.addView(label("Biblioteca privada do tablet",17,WHITE,true));
        add(card,label("Estas histórias e personagens não são sincronizadas "
            +"com a conta XiaoZhi. Podes copiar o texto para a consola oficial.",13,MUTED,false),6);
        add(card,button("+ Criar "+("Historia".equals(kind)?"história":"personagem"),
            true,()->createLibraryEntry(kind)),10);
        add(stage,card,14);
        List<LumiLibraryDb.Entry> contents=library.all(kind);
        if(contents.isEmpty()){
            add(stage,label("Ainda não tens "+
                ("Historia".equals(kind)?"histórias":"personagens")+" guardadas.",
                14,MUTED,false),13);
        }
        for(LumiLibraryDb.Entry entry:contents){
            LinearLayout tile=panel();
            tile.addView(label(entry.title,18,WHITE,true));
            TextView excerpt=label(entry.content.isEmpty()?"Sem texto":entry.content,
                13,MUTED,false);
            excerpt.setMaxLines(5);
            add(tile,excerpt,8);
            add(tile,button("Copiar texto",false,()->{
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText(entry.title,
                    entry.title+"\n\n"+entry.content));
                notice("Copiado","Texto pronto a colar na consola XiaoZhi.");
            }),9);
            add(tile,button("Eliminar",false,()->new AlertDialog.Builder(this)
                .setTitle("Eliminar "+entry.title+"?")
                .setMessage("A eliminação do rascunho local não pode ser anulada.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Eliminar",(d,w)->{
                    library.remove(entry.id);draw();
                }).show()),7);
            add(stage,tile,12);
        }
    }

    private void createLibraryEntry(String kind) {
        LinearLayout form=vertical();
        form.setPadding(dp(18),dp(10),dp(18),dp(6));
        EditText title=new EditText(this);
        title.setSingleLine(true);title.setHint("Título");
        form.addView(title);
        EditText content=new EditText(this);
        content.setMinLines(4);
        content.setHint("Texto, contexto ou detalhes da personagem...");
        add(form,content,8);
        new AlertDialog.Builder(this)
            .setTitle("Nova "+("Historia".equals(kind)?"história":"personagem"))
            .setView(form)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                if(title.getText().toString().trim().isEmpty()){
                    notice("Título em falta","Indica um título antes de guardar.");
                    return;
                }
                library.save(kind,title.getText().toString(),
                    content.getText().toString());
                draw();
            }).show();
    }


    private void showTabletStatus() {
        notice("Este dispositivo",
            android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL
            +" · Bateria: "+battery()+"% · "+serviceText());
    }

    private void drawPeople() {
        heading("Pessoas da casa","Telemóveis e localização voluntária.");
        LinearLayout intro=panel();
        intro.addView(label("As pessoas da casa",20,WHITE,true));
        add(intro,label("A localização não é recolhida pelo HUB. "
            +"O Google Find Hub abre separadamente na conta autorizada. "
            +"O Home Assistant poderá disponibilizar estados mais tarde.",13,MUTED,false),8);
        add(stage,intro,12);
        String[] defaults={"Deolinda","Tiago","Leonardo"};
        for(int i=0;i<defaults.length;i++){
            final int index=i;
            SharedPreferences prefs=getSharedPreferences("hub",MODE_PRIVATE);
            String name=prefs.getString("person_name_"+i,defaults[i]);
            LinearLayout card=panel();
            card.addView(label("●  "+name,20,WHITE,true));
            add(card,label("Telemóvel · localização externa",13,MUTED,false),6);
            add(card,button("Localizar pelo Google",false,()->openFindHub(name)),9);
            add(card,button("Editar nome",false,()->{
                EditText edit=new EditText(this);
                edit.setSingleLine(true);
                edit.setText(name);
                new AlertDialog.Builder(this).setTitle("Nome da pessoa")
                    .setView(edit).setNegativeButton("Cancelar",null)
                    .setPositiveButton("Guardar",(d,w)->{
                        String value=edit.getText().toString().trim();
                        if(!value.isEmpty() && value.length()<=60){
                            prefs.edit().putString("person_name_"+index,value).apply();
                            draw();
                        }
                    }).show();
            }),7);
            add(stage,card,10);
        }
        SharedPreferences preferences=getSharedPreferences("hub",MODE_PRIVATE);
        JSONArray others;
        try{others=new JSONArray(preferences.getString("extra_house_people","[]"));}
        catch(Exception error){others=new JSONArray();}
        for(int i=0;i<others.length();i++){
            final int index=i;
            String name=others.optString(i,"");
            if(name.isEmpty())continue;
            LinearLayout card=panel();
            card.addView(label("●  "+name,20,WHITE,true));
            add(card,label("Telemóvel · localização externa",13,MUTED,false),6);
            add(card,button("Localizar pelo Google",false,()->openFindHub(name)),9);
            add(card,button("Remover pessoa",false,()->new AlertDialog.Builder(this)
                .setTitle("Remover "+name+" do HUB?")
                .setMessage("Apaga apenas o registo local. Não altera a conta Google.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Remover",(d,w)->{
                    try{
                        JSONArray people=new JSONArray(preferences.getString(
                            "extra_house_people","[]"));
                        JSONArray remaining=new JSONArray();
                        for(int j=0;j<people.length();j++)
                            if(j!=index)remaining.put(people.optString(j));
                        preferences.edit().putString("extra_house_people",
                            remaining.toString()).apply();
                        draw();
                    }catch(Exception ignored){}
                }).show()),7);
            add(stage,card,11);
        }
        add(stage,button("+ Adicionar pessoa",true,()->{
            EditText name=new EditText(this);
            name.setSingleLine(true);name.setHint("Nome");
            new AlertDialog.Builder(this).setTitle("Adicionar pessoa à casa")
                .setMessage("A localização é aberta no Google Find Hub com autorização.")
                .setView(name).setNegativeButton("Cancelar",null)
                .setPositiveButton("Adicionar",(d,w)->{
                    String value=name.getText().toString().trim();
                    if(value.isEmpty() || value.length()>60)return;
                    try{
                        JSONArray people=new JSONArray(preferences.getString(
                            "extra_house_people","[]"));
                        if(people.length()>=12)return;
                        people.put(value);
                        preferences.edit().putString("extra_house_people",
                            people.toString()).apply();
                        draw();
                    }catch(Exception ignored){}
                }).show();
        }),13);
    }

    private void drawFloorplan() {
        heading("Planta da casa","Vista gráfica das divisões.");
        LinearLayout panel=panel();
        panel.addView(label("Planta da casa",19,WHITE,true));
        String image=getSharedPreferences("hub",MODE_PRIVATE)
            .getString("house_floorplan_uri","");
        if(!image.isEmpty()){
            ImageView imageView=new ImageView(this);
            imageView.setAdjustViewBounds(true);
            imageView.setMaxHeight(dp(landscape()?500:360));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try{imageView.setImageURI(Uri.parse(image));}
            catch(Exception error){notice("Planta da casa","Imagem inacessível.");}
            add(panel,imageView,8);
        }
        add(panel,label("Quando existir uma planta da tua casa e ligação ao Home Assistant, "
            +"poderemos sobrepor comandos reais de equipamentos por divisão. "
            +"Ainda não existe qualquer localização ou controlo fictício.",13,MUTED,false),9);
        LinearLayout rooms=horizontal();
        for(String room:new String[]{"Sala","Cozinha","Quartos"}){
            TextView view=label(room,14,currentPalette.tileText,true);
            view.setGravity(Gravity.CENTER);
            view.setPadding(dp(8),dp(28),dp(8),dp(28));
            view.setBackground(borderFill(currentPalette.tileA,13,currentPalette.tileBorder));
            addWeighted(rooms,view,1,6);
        }
        if(image.isEmpty())add(panel,rooms,13);
        add(panel,button(image.isEmpty()?"Escolher imagem da planta":
            "Substituir planta",true,this::selectFloorplan),13);
        add(stage,panel,12);
    }

    private void selectFloorplan(){
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(picker,PICK_FLOORPLAN);
    }


    private void copyLumiContextToXiaoZhi(){
        StringBuilder context=new StringBuilder();
        context.append("Contexto opcional LUMI DESK (português de Portugal).\n");
        context.append("Estes dados foram guardados localmente pelo utilizador.\n");
        for(MemoryDb.Item item:memories.all()){
            context.append("\nMemória: ").append(item.title).append("\n")
                .append(item.detail).append("\n");
            if(context.length()>9000)break;
        }
        for(String kind:new String[]{"Historia","Personagem"}){
            for(LumiLibraryDb.Entry entry:library.all(kind)){
                context.append("\n").append(kind).append(": ")
                    .append(entry.title).append("\n")
                    .append(entry.content).append("\n");
                if(context.length()>9000)break;
            }
        }
        if(context.length()>11000)context.setLength(11000);
        final String text=context.toString();
        new AlertDialog.Builder(this)
            .setTitle("Enviar conteúdos para o XiaoZhi?")
            .setMessage("Será copiado texto para a área de transferência do Android "
                +"e aberta a consola XiaoZhi. Nada é enviado automaticamente. "
                +"Revê os dados antes de os colares no agente.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Copiar e abrir",(d,w)->{
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("Contexto LUMI",text));
                openExternal("https://xiaozhi.me/console/agents");
            }).show();
    }

    private void editXiaoZhiPersonality() {
        SharedPreferences pref=getSharedPreferences("hub",MODE_PRIVATE);
        EditText draft=new EditText(this);
        draft.setMinLines(6);
        draft.setText(pref.getString("xiaozhi_personality_draft",""));
        draft.setHint("Personalidade e instruções da LUMI...");
        new AlertDialog.Builder(this)
            .setTitle("Personalidade · rascunho")
            .setMessage("A edição local não altera a conta XiaoZhi.")
            .setView(draft)
            .setNegativeButton("Cancelar",null)
            .setNeutralButton("Copiar e abrir XiaoZhi",(d,w)->{
                String value=draft.getText().toString();
                pref.edit().putString("xiaozhi_personality_draft",value).apply();
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("Personalidade LUMI",value));
                openExternal("https://xiaozhi.me/console/agents");
            })
            .setPositiveButton("Guardar rascunho",(d,w)->{
                pref.edit().putString("xiaozhi_personality_draft",
                    draft.getText().toString()).apply();
                notice("Guardado","Texto local, não sincronizado com XiaoZhi.");
            }).show();
    }

    private void drawSettings() {
        heading("Definições","Ligações, atualizações e manutenção.");
        LinearLayout updates=panel();
        updates.addView(label("Atualizações do HUB",18,WHITE,true));
        add(updates,label("Versões assinadas, testes e histórico.",13,MUTED,false),6);
        add(updates,button("Consultar atualizações",true,()->{
            page="Atualizações";updatesTab="Versões";draw();
        }),10);
        add(stage,updates,12);
        LinearLayout look=panel();
        look.addView(label("Aparência",18,WHITE,true));
        add(look,button("Escolher tema",false,()->{
            page="Atualizações";updatesTab="Temas";draw();
        }),10);
        add(stage,look,12);
        LinearLayout home=panel();
        home.addView(label("Home Assistant",18,WHITE,true));
        add(home,label("Integração em preparação. "
            +"Apenas os dispositivos já ligados têm controlo real.",13,MUTED,false),7);
        add(home,button("Documentação oficial",false,()->openExternal(
            "https://www.home-assistant.io/integrations/")),9);
        add(stage,home,12);
        LinearLayout advanced=panel();
        advanced.addView(label("Ferramentas e dispositivos",18,WHITE,true));
        add(advanced,button("Estado deste dispositivo",false,this::showTabletStatus),8);
        add(advanced,button("Manutenção da DESK (Windows)",false,this::showDeskMaintenance),7);
        // Ponte antiga removida da navegação principal; firmware e backups
        // continuam no gestor CORE Windows, independente do HUB Android.
        add(advanced,button("Histórico técnico da ponte (local)",false,()->
            startActivity(new Intent(this,DeskLogActivity.class))),7);
        add(stage,advanced,12);
    }

    private void drawDevices() {
        heading("Dispositivos","Todos os equipamentos, incluindo os que ainda vais configurar.");
        drawSelectedDevice();

        sectionTitle("A MINHA CASA","Os mesmos equipamentos que encontras na Home.");
        GridLayout grid=new GridLayout(this);
        int cols=landscape()?5:2;
        grid.setColumnCount(cols);
        int index=0;
        for(HomeDevices.Device item:HomeDevices.ALL){
            if(item.key.startsWith("phone_") || "lumi".equals(item.key)
                || "tablet".equals(item.key)) continue;
            addHomeTile(grid,item,index++,cols);
        }
        add(stage,grid,11);
    }

    private void drawSelectedDevice() {
        if("tv".equals(activeDevice)) tvDetails();
        else if("lumi".equals(activeDevice)) drawLumi();
        else if("tablet".equals(activeDevice)) tabletDetails();
        else if("camera".equals(activeDevice)) cameraDetails();
        else if("camera_sala".equals(activeDevice)) cameraRoomDetails();
        else if("phone_deolinda".equals(activeDevice)) phoneDetails("Deolinda");
        else if("phone_tiago".equals(activeDevice)) phoneDetails("Tiago");
        else if("phone_leonardo".equals(activeDevice)) phoneDetails("Leonardo");
        else if("spotify".equals(activeDevice)) spotifyDetails();
        else if("vacuum".equals(activeDevice)) placeholderDetails("Aspirador Alfawise",
            "Equipamento reservado na LUMI. O controlo fica por configurar quando confirmarmos o modelo exato e o protocolo suportado.");
        else if("ac".equals(activeDevice)) placeholderDetails("Ar condicionado da Sala",
            "A integração depende da marca e do protocolo Wi-Fi ou infravermelhos. Mantemos o equipamento no painel.");
        else if("plug".equals(activeDevice)) placeholderDetails("Tomada inteligente",
            "Ainda não foi instalada. A gestão automática depende de uma tomada integrada.");
        else placeholderDetails("Espaço para novos dispositivos",
            "A Home já tem um lugar reservado para os próximos equipamentos. Para integrar um novo dispositivo, teremos de confirmar o modelo e método de ligação.");
    }

    private void cameraRoomDetails() {
        LinearLayout card=detail();
        card.addView(label("Câmara da Sala",23,WHITE,true));
        add(card,label("CÂMARA DESTE DISPOSITIVO",13,CYAN,true),7);
        add(card,label("A imagem aparece apenas quando abres a pré-visualização. Não existe gravação, transmissão em rede nem acesso remoto à câmara.",13,MUTED,false),9);
        add(card,button("Escolher câmara frontal ou traseira",true,this::openRoomCamera),13);
        add(card,label("O Android poderá pedir autorização para utilizar a câmara. Esta autorização é diferente da Câmara da Varanda.",12,MUTED,false),10);
        add(stage,card,16);
    }

    private void openRoomCamera() {
        if(Build.VERSION.SDK_INT>=23 &&
           checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);
            return;
        }
        try {
            startActivity(new Intent(this,CameraRoomActivity.class));
        }catch(Exception error){
            notice("Câmara Sala","Não foi possível abrir a pré-visualização: "+error.getMessage());
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,
                                                       int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode!=CAMERA_REQUEST) return;
        if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED)
            openRoomCamera();
        else
            notice("Câmara Sala","O acesso à câmara não foi autorizado. Podes permitir nas definições do Android.");
    }

    private void screenOff() {
        ComponentName admin=new ComponentName(this,ScreenLockAdmin.class);
        DevicePolicyManager manager=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
        if(manager==null){
            notice("Ecrã do tablet","Esta versão do Android não disponibiliza gestão de ecrã.");
            return;
        }
        if(!manager.isAdminActive(admin)){
            new AlertDialog.Builder(this)
                .setTitle("Ativar botão Apagar ecrã")
                .setMessage("Para apagar realmente o ecrã, o Android exige autorização de Administrador do dispositivo. O Hub pede apenas permissão para bloquear o ecrã, sem aceder a palavras-passe nem apagar dados. Poderá ser necessário o PIN ao voltar a ligar. A autorização pode ser revogada nas definições do Android. Queres abrir a autorização?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Configurar",(dialog,which)->{
                    Intent request=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    request.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,admin);
                    request.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Permitir ao LUMI Hub apagar o ecrã deste dispositivo.");
                    startActivityForResult(request,ADMIN_REQUEST);
                }).show();
            return;
        }
        try {
            manager.lockNow();
        } catch(SecurityException error) {
            notice("Apagar ecrã","O Android não permitiu bloquear o ecrã. Verifica a autorização de administrador.");
        }
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
        addWeighted(power,button("Ligar TV · Wi-Fi",true,this::tvWake),1,0);
        addWeighted(power,button("Desligar TV",false,
            ()->tvCommand("ssap://system/turnOff",false)),1,8);
        add(card,power,9);
        tvFeedback=label("Última resposta LG: "+lastTv(),13,MUTED,false);
        add(card,tvFeedback,12);
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
        add(form,label("O endereço IP e o MAC ficam guardados apenas neste dispositivo.",12,
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
    private void tvStatus(String message) {
        if(tvFeedback!=null && tvFeedback.isAttachedToWindow())
            tvFeedback.setText("TV da Sala · "+message);
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }
    private void tvWake() {
        SharedPreferences p=getSharedPreferences("hub",MODE_PRIVATE);
        String mac=p.getString("tv_mac","");
        if(!validMac(mac)){
            tvStatus("MAC da TV em falta. Configura-o nas definições da TV da Sala.");
            return;
        }
        tvStatus("A enviar o sinal de ligar...");
        WakeOnLan.send(mac,p.getString("tv_ip",""),
            message->runOnUiThread(()->tvStatus(message)));
    }
    private void tvCheck() {
        String ip=savedIp();
        if(!validIpv4(ip)){tvSettings();return;}
        tvStatus("A verificar ligação webOS...");
        new Thread(()->{
            boolean reachable=false;
            try(Socket s=new Socket()){
                s.connect(new InetSocketAddress(ip,3001),2000);
                reachable=true;
            }catch(Exception ignored){}
            String msg=reachable?"Serviço webOS acessível.":
                "Serviço webOS não acessível. TV desligada ou sem rede.";
            getSharedPreferences("hub",MODE_PRIVATE).edit()
                .putString("lg_last_status",msg).apply();
            runOnUiThread(()->tvStatus(msg));
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
                        tvStatus("Falha WSS 3001: "+error);
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
        tvStatus(pair?"Aceita o emparelhamento na TV...":
            "A enviar comando...");
        WebOsController.request(ip,p.getString("lg_client_key",""),
            p.getString("lg_cert_pin",""),action,pair,(message,key)->
                runOnUiThread(()->{
                    SharedPreferences.Editor editor=p.edit()
                        .putString("lg_last_status",message);
                    if(key!=null&&!key.isEmpty())editor.putString("lg_client_key",key);
                    editor.apply();
                    tvStatus(message);
                }));
    }
    private void lumiDetails() {
        SharedPreferences prefs=getSharedPreferences("hub",MODE_PRIVATE);
        LinearLayout card=detail();
        card.addView(label("LUMI DESK",23,WHITE,true));
        add(card,label("Assistente de voz · ESP32-S3",13,VIOLET,false),5);
        String state=prefs.getString("desk_gateway_state","Ainda sem diagnóstico");
        add(card,label("Ponte de voz: "+state,13,MUTED,false),14);
        add(card,button("Configurações",true,()->
            startActivity(new Intent(this,LumiCoreActivity.class))),16);
        add(card,button("Diagnóstico e histórico",false,()->
            startActivity(new Intent(this,DeskLogActivity.class))),9);
        add(stage,card,16);
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
            +"\nLUMI DESK: "+p.getString("desk_gateway_state","Por testar") + ""
            +"\nLogs internos ESP32: não disponíveis");
    }
    private void tabletDetails() {
        LinearLayout c=detail();
        c.addView(label(android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL,23,WHITE,true));
        add(c,label("Central LUMI Hub • Android",13,GREEN,true),7);
        add(c,label(battery()+"%",44,WHITE,true),11);
        add(c,label(charging()?"A carregar":"A funcionar com bateria",14,MUTED,false),4);
        add(c,label(serviceText(),16,serviceRunning()?GREEN:GOLD,true),17);
        add(c,button(serviceRunning()?"Parar central":"Iniciar central",
            true,this::switchService),10);
        add(c,button("Ver logs e diagnóstico",false,this::diagnostics),8);
        add(c,button("Apagar ecrã deste dispositivo",false,this::screenOff),8);
        add(c,label("A gestão de energia depende das capacidades deste dispositivo.",13,MUTED,false),14);
        add(stage,c,16);
    }

    private void drawMemories() {
        if(!"Biblioteca".equals(page))
            heading("Memórias","Informações guardadas apenas neste dispositivo.");
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
        heading("Atualizações","Versões do HUB publicadas no GitHub, incluindo V12.");
        LinearLayout tabs=horizontal();
        addWeighted(tabs,button("Versões",updatesTab.equals("Versões"),()->{
            updatesTab="Versões";draw();
        }),1,0);
        addWeighted(tabs,button("Temas",updatesTab.equals("Temas"),()->{
            updatesTab="Temas";draw();
        }),1,9);
        add(stage,tabs,15);
        if("Temas".equals(updatesTab)) {
            drawThemeSelector();
            return;
        }
        LinearLayout c=panel();
        c.addView(label("Versão instalada: "+versionName(),18,WHITE,true));
        add(c,label("Versões estáveis e de testes assinadas, claramente identificadas. " +
            "O firmware da LUMI DESK não é instalado por esta opção.",13,MUTED,false),9);
        add(c,button("Procurar atualizações",true,this::loadReleases),14);
        add(c,button("Abrir versões no GitHub",false,()->openExternal(
            "https://github.com/TiagoM20/xiaozhi-esp32/releases")),8);
        add(stage,c,16);
        LinearLayout list=vertical();
        list.setId(17331);
        add(stage,list,12);
        stage.post(this::loadReleases);
    }

    private void drawThemeSelector() {
        LinearLayout intro=panel();
        intro.addView(label("Escolhe o ambiente da tua casa",20,WHITE,true));
        add(intro,label("Ao escolheres um tema, as cores da Casa e dos cartões "
            +"mudam de imediato. Fica guardado neste dispositivo.",
            13,MUTED,false),7);
        add(stage,intro,14);
        GridLayout grid=new GridLayout(this);
        int cols=landscape()?3:2;
        grid.setColumnCount(cols);
        for(int i=0;i<HubThemes.NAMES.length;i++) {
            final int index=i;
            HubThemes.Palette scheme=HubThemes.get(i);
            LinearLayout choice=vertical();
            choice.setGravity(Gravity.CENTER);
            choice.setPadding(dp(14),dp(15),dp(14),dp(14));
            choice.setBackground(borderFill(scheme.background,15,
                themeId==i?CYAN:scheme.stroke));
            LinearLayout swatches=horizontal();
            int[] samples={scheme.bannerA,scheme.tileA,scheme.tileB,scheme.accent};
            for(int swatch:samples) {
                View dot=new View(this);
                dot.setBackground(fill(swatch,9));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
                    dp(30),dp(27));
                p.setMargins(dp(3),0,dp(3),0);
                swatches.addView(dot,p);
            }
            choice.addView(swatches);
            TextView title=label(HubThemes.NAMES[i],16,scheme.text,true);
            title.setGravity(Gravity.CENTER);
            add(choice,title,13);
            TextView status=label(themeId==i?"●  Em utilização":"Tocar para aplicar",
                12,scheme.muted,false);
            status.setGravity(Gravity.CENTER);
            add(choice,status,7);
            choice.setOnClickListener(v->selectHubTheme(index));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
            lp.width=0;
            lp.height=dp(143);
            lp.columnSpec=GridLayout.spec(i%cols,1f);
            lp.rowSpec=GridLayout.spec(i/cols,1f);
            lp.setMargins(dp(4),dp(5),dp(4),dp(5));
            grid.addView(choice,lp);
        }
        add(stage,grid,13);
        add(stage,button("Voltar à Home",true,()->{
            page="Casa";draw();
        }),12);
    }

    private String versionName() {
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){return "desconhecida";}
    }
    private int[] versionNumbers(String version) {
        String cleaned=version.startsWith("lumi-hub-v")
            ?version.substring("lumi-hub-v".length()):version;
        // As tags de teste podem terminar em -rc1; o APK usa a versão 0.12.0.
        int suffix=cleaned.indexOf('-');
        if(suffix>=0)cleaned=cleaned.substring(0,suffix);
        String[] chunks=cleaned.split("[.]");
        int[] parts=new int[]{0,0,0};
        for(int i=0;i<Math.min(chunks.length,3);i++) {
            try {
                parts[i]=Integer.parseInt(chunks[i]);
                if(parts[i]<0) return new int[]{-1,-1,-1};
            } catch(NumberFormatException error) {return new int[]{-1,-1,-1};}
        }
        return parts;
    }

    private int compareVersions(String a,String b) {
        int[] x=versionNumbers(a);
        int[] y=versionNumbers(b);
        for(int i=0;i<3;i++) {
            if(x[i]!=y[i]) return Integer.compare(x[i],y[i]);
        }
        return 0;
    }

    private void loadReleases() {
        LinearLayout holder=findViewById(17331);
        if(holder==null)return;
        holder.removeAllViews();
        add(holder,label("A procurar versões Android...",13,MUTED,false),10);
        new Thread(()->{
            JSONArray releases=null;
            String problem=null;
            try{
                HttpURLConnection con=(HttpURLConnection)new URL(
                    "https://api.github.com/repos/TiagoM20/xiaozhi-esp32/releases?per_page=100"
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
                        while((line=rd.readLine())!=null) {
                            b.append(line);
                            if(b.length()>2000000)
                                throw new Exception("Resposta excessivamente grande");
                        }
                    }
                    JSONArray all=new JSONArray(b.toString());
                    JSONArray selected=new JSONArray();
                    for(int i=0;i<all.length();i++) {
                        JSONObject item=all.optJSONObject(i);
                        if(item==null||item.optBoolean("draft"))continue;
                        if(!item.optString("tag_name","").startsWith("lumi-hub-v"))continue;
                        if(releaseApkUrl(item).isEmpty())continue;
                        selected.put(item);
                    }
                    if(selected.length()==0)throw new Exception("Sem versões HUB na resposta");
                    releases=selected;
                    getSharedPreferences("hub",MODE_PRIVATE).edit()
                        .putString("hub_versions_cache",selected.toString()).apply();
                }finally{con.disconnect();}
            }catch(Exception ex){
                problem="Falha na consulta GitHub ("+ex.getClass().getSimpleName()+").";
                try {
                    String saved=getSharedPreferences("hub",MODE_PRIVATE)
                        .getString("hub_versions_cache","");
                    if(!saved.isEmpty())releases=new JSONArray(saved);
                }catch(Exception ignored){}
            }
            final JSONArray result=releases;
            final String error=problem;
            runOnUiThread(()->{
                if(!"Atualizações".equals(page) || !"Versões".equals(updatesTab))return;
                LinearLayout area=findViewById(17331);
                if(area==null)return;
                area.removeAllViews();
                if(error!=null){
                    add(area,label(error+(result==null
                        ?" Abre as versões oficiais no GitHub."
                        :" A apresentar a última lista guardada no tablet."),
                        13,GOLD,false),6);
                    add(area,button("Tentar novamente",false,this::loadReleases),9);
                    add(area,button("Ver versões oficiais no GitHub",false,()->
                        openExternal("https://github.com/TiagoM20/xiaozhi-esp32/releases")),9);
                    if(result==null)return;
                }
                if(result==null) return;
                java.util.ArrayList<JSONObject> versions=new java.util.ArrayList<>();
                for(int i=0;i<result.length();i++) {
                    JSONObject release=result.optJSONObject(i);
                    if(release==null || release.optBoolean("draft")) continue;
                    String tag=release.optString("tag_name","");
                    if(!tag.startsWith("lumi-hub-v"))continue;
                    versions.add(release);
                }
                versions.sort((left,right)->{
                    int numeric=compareVersions(right.optString("tag_name",""),
                        left.optString("tag_name",""));
                    if(numeric!=0)return numeric;
                    // Se existir a mesma versão estável e RC, mostrar primeiro a estável.
                    return Boolean.compare(left.optBoolean("prerelease"),
                        right.optBoolean("prerelease"));
                });
                String installed=versionName();
                if(!versions.isEmpty()){
                    JSONObject latest=versions.get(0);
                    String tag=latest.optString("tag_name","");
                    int comparison=compareVersions(tag,installed);
                    LinearLayout summary=panel();
                    if(comparison>0){
                        summary.addView(label(latest.optBoolean("prerelease")
                            ? "Nova versão de testes disponível"
                            : "Nova atualização disponível",19,GREEN,true));
                        add(summary,label("LUMI Hub "+tag.substring("lumi-hub-v".length())
                            +" · instalada "+installed,14,WHITE,false),8);
                        String link=releaseApkUrl(latest);
                        if(!link.isEmpty()){
                            add(summary,button(latest.optBoolean("prerelease")
                                ? "Descarregar versão de testes"
                                : "Descarregar atualização",true,
                                ()->downloadApk(link,tag)),12);
                        }else add(summary,label("O APK ainda não está disponível.",
                            12,GOLD,false),8);
                    }else{
                        summary.addView(label("Aplicação atualizada",18,GREEN,true));
                        add(summary,label("Versão instalada: "+installed,
                            13,MUTED,false),7);
                    }
                    add(area,summary,8);
                }
                int shown=0;
                for(JSONObject rel:versions) {
                    if(shown++>=10)break;
                    String tag=rel.optString("tag_name","");
                    String link=releaseApkUrl(rel);
                    LinearLayout tile=panel();
                    String name=rel.optString("name",tag);
                    tile.addView(label(name,16,WHITE,true));
                    String marker=(rel.optBoolean("prerelease")
                        ? " · Testes" : " · Estável")
                        +(compareVersions(tag,installed)==0?" · Instalada":"");
                    add(tile,label(tag+marker,12,CYAN,false),5);
                    add(tile,label(rel.optString("body","Sem descrição"),
                        12,MUTED,false),7);
                    if(!link.isEmpty() && compareVersions(tag,installed)!=0)
                        add(tile,button("Descarregar APK",false,
                            ()->downloadApk(link,tag)),11);
                    add(area,tile,10);
                }
                if(versions.isEmpty()){
                    add(area,label("A lista ainda não está disponível. "
                        +"As versões V12 encontram-se na página oficial do GitHub.",
                        14,MUTED,false),10);
                    add(area,button("Abrir versão HUB 12.8",false,()->openExternal(
                        "https://github.com/TiagoM20/xiaozhi-esp32/releases/tag/lumi-hub-v0.12.8")),9);
                }
            });
        },"LumiHubReleases").start();
    }

    private String releaseApkUrl(JSONObject release) {
        JSONArray assets=release.optJSONArray("assets");
        if(assets==null)return "";
        String tag=release.optString("tag_name","");
        for(int j=0;j<assets.length();j++){
            JSONObject asset=assets.optJSONObject(j);
            if(asset==null)continue;
            String name=asset.optString("name","");
            String url=asset.optString("browser_download_url","");
            if(name.endsWith(".apk")
                && url.startsWith("https://github.com/TiagoM20/xiaozhi-esp32/releases/download/")
                && url.contains("/"+tag+"/")) return url;
        }
        return "";
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
