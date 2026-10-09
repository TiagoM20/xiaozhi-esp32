package pt.lumistudio.lumihub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import org.json.JSONObject;
import org.json.JSONArray;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(36, 31, 68);
    private static final int MUTE = Color.rgb(115, 111, 138);
    private static final int PURPLE = Color.rgb(113, 79, 237);
    private static final int TEAL = Color.rgb(29, 171, 165);
    private static final int PAGE = Color.rgb(248, 247, 253);
    private MemoryDb db;
    private LinearLayout content;
    private String tab = "Painel";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(70, 50, 164));
        getWindow().setNavigationBarColor(PAGE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        db = new MemoryDb(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    @Override protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density*n+0.5f); }

    private GradientDrawable bg(int color, int corner) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(corner));
        return d;
    }

    private GradientDrawable outlined(int color, int corner, int stroke) {
        GradientDrawable d = bg(color, corner);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private void margin(View child, LinearLayout parent, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        parent.addView(child, p);
    }

    private LinearLayout stack() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout l = stack();
        l.setBackground(bg(Color.WHITE, 20));
        l.setPadding(dp(20), dp(18), dp(20), dp(18));
        l.setElevation(dp(2));
        return l;
    }

    private Button button(String caption, int color, boolean light, Runnable action) {
        Button b = new Button(this);
        b.setText(caption);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(light ? INK : Color.WHITE);
        b.setBackground(bg(color, 14));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAGE);
        content = stack();
        content.setPadding(dp(20), dp(18), dp(20), dp(32));
        scroll.addView(content);
        setContentView(scroll);
        header();
        navigation();
        if ("Updates".equals(tab)) showUpdates();
        else if ("LUMI".equals(tab)) showLumi();
        else if ("Dispositivos".equals(tab)) showDevices();
        else if ("Memórias".equals(tab)) showMemories();
        else if ("Ligações".equals(tab)) showConnections();
        else if ("Bateria".equals(tab)) showBattery();
        else showDashboard();
        footer();
    }

    private void header() {
        LinearLayout box = stack();
        GradientDrawable g = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(102, 67, 219), Color.rgb(45, 168, 182)});
        g.setCornerRadius(dp(26));
        box.setBackground(g);
        box.setPadding(dp(24), dp(23), dp(24), dp(24));

        TextView brand = text("LUMI  /  HUB", 13, Color.rgb(229, 227, 255), true);
        box.addView(brand);
        margin(text("A tua central inteligente", 26, Color.WHITE, true), box, 10);
        margin(text("Blackview Tab 15  •  versão 0.5  •  local e privado",
            13, Color.rgb(234, 238, 255), false), box, 9);
        margin(box, content, 2);
    }

    private void navigation() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] options = {"Painel", "LUMI", "Memórias", "Dispositivos", "Bateria", "Updates"};
        for (String option : options) {
            TextView t = text(option, 12, tab.equals(option) ? Color.WHITE : INK, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(4), dp(13), dp(4), dp(13));
            t.setBackground(bg(tab.equals(option) ? PURPLE : Color.WHITE, 12));
            t.setOnClickListener(v -> { tab = option; render(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (!option.equals(options[0])) p.leftMargin = dp(5);
            row.addView(t, p);
        }
        margin(row, content, 20);
    }

    private int batteryPercentage() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return -1;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level >= 0 && scale > 0 ? (int)(100f*level/scale) : -1;
    }

    private boolean isCharging() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return false;
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private boolean serviceActive() {
        SharedPreferences p = getSharedPreferences("hub", MODE_PRIVATE);
        return p.getBoolean("service_running", false);
    }

    private void showDashboard() {
        LinearLayout status = card();
        status.addView(text("ESTADO DA CENTRAL", 12, MUTE, true));
        margin(text(serviceActive() ? "Serviço ativado" : "Pronto para iniciar",
            22, serviceActive() ? TEAL : INK, true), status, 8);
        margin(text("A primeira versão acompanha a bateria e mantém as memórias no tablet. " +
            "A ligação à LUMI e à televisão ainda não está ativa.",
            13, MUTE, false), status, 8);
        margin(button(serviceActive() ? "Parar central" : "Iniciar central",
            serviceActive() ? Color.rgb(76, 75, 102) : PURPLE, false,
            this::toggleService), status, 14);
        margin(status, content, 21);

        LinearLayout stats = card();
        stats.addView(text("RESUMO", 12, MUTE, true));
        margin(text(db.count() + " memórias guardadas", 21, INK, true), stats, 10);
        margin(text("Bateria: " + batteryPercentage() + "%  •  " +
            (isCharging() ? "a carregar" : "sem carregar"), 16, INK, false), stats, 10);
        margin(stats, content, 13);

        LinearLayout next = card();
        next.addView(text("PRÓXIMOS PASSOS", 12, MUTE, true));
        margin(text("01  Emparelhar a LG webOS\n02  Ligar ao servidor XiaoZhi\n" +
            "03  Histórias com memória\n04  Tomada Wi-Fi (quando existir)",
            15, INK, false), next, 12);
        margin(next, content, 13);
    }

    private void toggleService() {
        if (serviceActive()) {
            stopService(new Intent(this, HubService.class));
            getSharedPreferences("hub", MODE_PRIVATE).edit()
                .putBoolean("service_running", false).apply();
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(new Intent(this, HubService.class));
                } else startService(new Intent(this, HubService.class));
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível iniciar: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            }
        }
        render();
    }

    private void showMemories() {
        LinearLayout info = card();
        info.addView(text("MEMÓRIA PESSOAL", 21, INK, true));
        margin(text("Apenas tu decides o que guardar. Os dados ficam na base SQLite " +
            "deste tablet. Ainda não são enviados para o servidor da LUMI.",
            13, MUTE, false), info, 10);
        margin(button("+ Guardar uma memória", PURPLE, false,
            this::addMemoryDialog), info, 15);
        margin(info, content, 20);
        List<MemoryDb.Item> items = db.all();
        if (items.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Ainda não existem memórias.", 16, INK, true));
            margin(text("Experimenta guardar uma preferência, personagem ou detalhe " +
                "de uma história.", 13, MUTE, false), empty, 6);
            margin(empty, content, 12);
        } else {
            for (MemoryDb.Item item : items) {
                LinearLayout c = card();
                c.addView(text(item.title, 17, INK, true));
                margin(text(item.detail, 14, MUTE, false), c, 6);
                String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "PT"))
                    .format(new Date(item.createdAt));
                margin(text(date, 11, MUTE, false), c, 8);
                margin(button("Eliminar memória", Color.rgb(242, 239, 250), true,
                    () -> confirmDelete(item)), c, 10);
                margin(c, content, 12);
            }
        }
    }

    private void addMemoryDialog() {
        LinearLayout l = stack();
        l.setPadding(dp(20), dp(10), dp(20), dp(3));
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Título (ex.: Personagem Luna)");
        l.addView(title);
        EditText detail = new EditText(this);
        detail.setHint("Informação que queres que a LUMI recorde");
        detail.setMinLines(3);
        l.addView(detail);
        new AlertDialog.Builder(this).setTitle("Nova memória").setView(l)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (dialog, which) -> {
                if (title.getText().toString().trim().isEmpty()) {
                    Toast.makeText(this, "A memória precisa de título", Toast.LENGTH_LONG).show();
                    return;
                }
                db.save(title.getText().toString(), detail.getText().toString());
                render();
            }).show();
    }

    private void confirmDelete(MemoryDb.Item item) {
        new AlertDialog.Builder(this).setTitle("Eliminar memória?")
            .setMessage("Apagar definitivamente \"" + item.title + "\" deste tablet?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (d, w) -> {
                db.remove(item.id);
                render();
            }).show();
    }

    private void showLumi() {
        LinearLayout status = card();
        status.addView(text("A MINHA LUMI", 23, INK, true));
        margin(text("Waveshare ESP32-S3 • Olá Lumi", 13, MUTE, false), status, 8);
        margin(text("Estado: NÃO VERIFICADO", 18, PURPLE, true), status, 14);
        margin(text("O tablet ainda não recebe sinais de presença do ESP32. Não mostramos ON/OFF inventado.",
            13, MUTE, false), status, 7);
        margin(button("Ver informações de ligação", PURPLE, false, () -> {
            tab = "Dispositivos"; render();
        }), status, 11);
        margin(status, content, 20);

        LinearLayout faces = card();
        faces.addView(text("EXPRESSÕES DA LUMI", 18, INK, true));
        margin(text("Escolhe uma expressão para pré-visualizar. Os comandos remotos serão ativados só quando houver ligação autenticada ao firmware.",
            13, MUTE, false), faces, 8);
        String[] moods = {"Feliz", "Apaixonada", "Sonolenta", "Surpresa", "Confiante", "Triste"};
        String[] symbols = {"☺", "♥", "☾", "!", "★", "◡"};
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(3);
        for (int i = 0; i < moods.length; i++) {
            final String mood = moods[i];
            LinearLayout choice = stack();
            choice.setGravity(Gravity.CENTER);
            choice.setPadding(dp(7), dp(11), dp(7), dp(11));
            choice.setBackground(outlined(Color.rgb(246, 243, 255), 15, Color.rgb(220, 211, 250)));
            TextView symbol = text(symbols[i], 24, PURPLE, true);
            symbol.setGravity(Gravity.CENTER);
            choice.addView(symbol);
            TextView name = text(mood, 12, INK, true);
            name.setGravity(Gravity.CENTER);
            choice.addView(name);
            choice.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Expressão: " + mood)
                .setMessage("Pré-visualização selecionada. Esta versão ainda não envia ordens para os GIFs do dispositivo.")
                .setPositiveButton("OK", null).show());
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = android.widget.GridLayout.spec(i % 3, 1f);
            lp.setMargins(dp(2), dp(4), dp(2), dp(4));
            grid.addView(choice, lp);
        }
        margin(grid, faces, 12);
        margin(faces, content, 13);

        LinearLayout tools = card();
        tools.addView(text("COMANDOS RÁPIDOS", 18, INK, true));
        margin(text("Sons, caras, animações e reinício remoto estarão disponíveis depois de implementarmos um canal seguro para o ESP32.",
            13, MUTE, false), tools, 9);
        margin(button("Consultar diagnóstico e logs", Color.rgb(232, 227, 250), true,
            this::showLogs), tools, 13);
        margin(tools, content, 13);
    }

    private void showLogs() {
        SharedPreferences p = getSharedPreferences("hub", MODE_PRIVATE);
        StringBuilder result = new StringBuilder();
        result.append("LUMI Hub • Registo local de diagnóstico\n");
        result.append("Serviço: ").append(serviceActive() ? "iniciado" : "parado").append("\n");
        result.append("Bateria: ").append(batteryPercentage()).append("%\n");
        result.append("Última leitura: ");
        long last = p.getLong("last_update", 0);
        result.append(last > 0
            ? new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", new Locale("pt", "PT")).format(new Date(last))
            : "sem registo");
        result.append("\n");
        result.append("Temperatura: ").append(p.getInt("temperature_tenths", 0) / 10.0).append(" °C\n");
        result.append("IP LG guardado: ").append(p.getString("tv_ip", "").isEmpty() ? "não" : "sim").append("\n");
        result.append("Ligação real à LUMI: ainda não configurada\n");
        result.append("Registos de áudio/serial do ESP32: indisponíveis nesta versão\n");
        new AlertDialog.Builder(this)
            .setTitle("Logs do LUMI Hub")
            .setMessage(result.toString())
            .setPositiveButton("Fechar", null)
            .show();
    }

    private void showDevices() {
        LinearLayout c = card();
        c.addView(text("OS MEUS DISPOSITIVOS", 22, INK, true));
        margin(text("Equipamentos da casa ligados ou por configurar no Hub.",
            13, MUTE, false), c, 8);
        margin(c, content, 20);

        connection("LG webOS • TV da sala", "Emparelhamento e comandos locais");
        tvDiagnosticCard();
        tvControlCard();
        connection("Alfawise • Aspirador", "A aguardar modelo exato para integrar");
        connection("AC da sala", "A aguardar modelo do comando Wi-Fi / infravermelhos");
        connection("LUMI ESP32-S3", "A preparar canal de controlo e logs");
        connection("Memória pessoal", "Base SQLite local ativa");
        connection("Tomada inteligente", "Ainda não instalada");
    }

    private void tvControlCard() {
        LinearLayout card = card();
        card.addView(text("COMANDO TV LG", 18, INK, true));
        margin(text("A TV deve estar ligada para o primeiro emparelhamento. Aceita o pedido no ecrã.",
            13, MUTE, false), card, 8);
        margin(button("Emparelhar TV", PURPLE, false, () -> tvSend(null, true)), card, 9);
        String[][] actions = {
            {"Aumentar volume", "ssap://audio/volumeUp"},
            {"Baixar volume", "ssap://audio/volumeDown"},
            {"Silenciar", "ssap://audio/setMute"},
            {"Abrir YouTube", "ssap://system.launcher/launch"},
            {"Desligar TV", "ssap://system/turnOff"}
        };
        for (String[] act : actions) {
            margin(button(act[0], Color.rgb(237, 233, 251), true, () -> {
                if ("ssap://system/turnOff".equals(act[1])) {
                    new AlertDialog.Builder(this).setTitle("Desligar televisão?")
                        .setMessage("Enviar comando de desligar à LG?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Desligar", (d,w) -> tvSend(act[1], false)).show();
                } else tvSend(act[1], false);
            }), card, 7);
        }
        margin(text("O botão Ligar TV (Wake-on-LAN) será implementado depois de confirmar o MAC e a configuração da TV.",
            12, MUTE, false), card, 10);
        margin(card, content, 12);
    }

    private void tvSend(String action, boolean pair) {
        SharedPreferences prefs = getSharedPreferences("hub", MODE_PRIVATE);
        String ip = prefs.getString("tv_ip", "");
        if (ip.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Primeiro guarda o IP")
                .setMessage("Em Dispositivos, introduz o IP da LG e testa a ligação.")
                .setPositiveButton("OK", null).show();
            return;
        }
        Toast.makeText(this, pair ? "A emparelhar com LG..." : "A enviar comando...", Toast.LENGTH_SHORT).show();
        WebOsController.request(ip, prefs.getString("lg_client_key", ""), action,
            pair, new WebOsController.Listener() {
                @Override public void onMessage(String msg, String key) {
                    runOnUiThread(() -> {
                        if (key != null && !key.isEmpty())
                            prefs.edit().putString("lg_client_key", key).apply();
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("TV LG")
                            .setMessage(msg)
                            .setPositiveButton("OK", null).show();
                    });
                }
            });
    }

    private void showUpdates() {
        LinearLayout c = card();
        c.addView(text("ATUALIZAÇÕES", 22, INK, true));
        margin(text("Versão instalada: " + getPackageVersion(), 14, PURPLE, true), c, 10);
        margin(text("Consulta as versões oficiais publicadas no GitHub e descarrega o APK. O Android pedirá confirmação antes de instalar.",
            13, MUTE, false), c, 8);
        margin(button("Procurar versões disponíveis", PURPLE, false, this::fetchHubReleases), c, 12);
        margin(c, content, 20);
        LinearLayout history = stack();
        history.setId(17331);
        margin(history, content, 6);
        LinearLayout info = card();
        info.addView(text("COMO FUNCIONAM AS ATUALIZAÇÕES", 14, INK, true));
        margin(text("O APK será guardado em Transferências. Toca na notificação da transferência para abri-lo e confirmar a instalação. Só são apresentadas versões Android identificadas como lumi-hub-v*. Nenhum firmware ESP32 é instalado por esta opção.",
            12, MUTE, false), info, 9);
        margin(info, content, 12);
    }

    private String getPackageVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { return "desconhecida"; }
    }

    private void fetchHubReleases() {
        LinearLayout container = findViewById(17331);
        if (container == null) return;
        container.removeAllViews();
        margin(text("A consultar GitHub...", 14, MUTE, false), container, 6);
        new Thread(() -> {
            JSONArray releases = null;
            String error = null;
            try {
                URL url = new URL("https://api.github.com/repos/TiagoM20/xiaozhi-esp32/releases?per_page=30");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "LUMI-Hub-Android");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                try {
                    if (connection.getResponseCode() != 200)
                        throw new Exception("GitHub HTTP " + connection.getResponseCode());
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                    }
                    releases = new JSONArray(sb.toString());
                } finally { connection.disconnect(); }
            } catch (Exception e) { error = e.getMessage(); }
            final JSONArray result = releases;
            final String err = error;
            runOnUiThread(() -> {
                if (!"Updates".equals(tab)) return;
                LinearLayout holder = findViewById(17331);
                if (holder == null) return;
                holder.removeAllViews();
                if (err != null) {
                    margin(text("Não foi possível consultar o GitHub: " + err, 14, MUTE, false), holder, 9);
                    return;
                }
                int shown = 0;
                for (int i = 0; i < result.length(); i++) {
                    JSONObject release = result.optJSONObject(i);
                    if (release == null || release.optBoolean("draft")) continue;
                    String tag = release.optString("tag_name", "");
                    if (!tag.startsWith("lumi-hub-v")) continue;
                    JSONArray assets = release.optJSONArray("assets");
                    String apkUrl = null;
                    if (assets != null) for (int k = 0; k < assets.length(); k++) {
                        JSONObject asset = assets.optJSONObject(k);
                        if (asset != null && asset.optString("name", "").endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url");
                            break;
                        }
                    }
                    LinearLayout card = card();
                    card.addView(text(release.optString("name", tag), 17, INK, true));
                    margin(text("Tag: " + tag + "\n" + release.optString("body", "Sem notas de versão."),
                        12, MUTE, false), card, 7);
                    if (apkUrl != null) {
                        String link = apkUrl;
                        margin(button("Descarregar APK", PURPLE, false, () -> downloadHubApk(link, tag)), card, 10);
                    } else {
                        margin(text("APK ainda não publicado para esta versão.", 12, MUTE, false), card, 7);
                    }
                    margin(card, holder, 10);
                    shown++;
                }
                if (shown == 0) margin(text("Ainda não existem versões Android publicadas. A primeira Release será publicada após a configuração da assinatura.", 14, MUTE, false), holder, 10);
            });
        }).start();
    }

    private void downloadHubApk(String url, String tag) {
        if (!url.startsWith("https://github.com/TiagoM20/xiaozhi-esp32/releases/download/")) {
            Toast.makeText(this, "Endereço de download não autorizado", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("LUMI Hub " + tag);
            request.setDescription("APK oficial do LUMI Hub");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LUMI-Hub-" + tag + ".apk");
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            new AlertDialog.Builder(this).setTitle("Download iniciado")
                .setMessage("O APK está a ser descarregado para Transferências. Quando terminar, abre a notificação e confirma a instalação. Não desinstales a versão anterior.")
                .setPositiveButton("OK", null).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Falha ao descarregar")
                .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    private void showConnections() {
        LinearLayout c = card();
        c.addView(text("LIGAÇÕES DA LUMI", 21, INK, true));
        margin(text("Os conectores ainda não foram ativados. Esta versão não " +
            "envia informações para fora do tablet.", 13, MUTE, false), c, 10);
        margin(c, content, 20);
        connection("LUMI ESP32-S3", "Ainda sem canal de estado/comandos; configurar na aba LUMI");
        connection("Televisão LG webOS", "Diagnóstico de rede e endereço disponíveis");
        tvDiagnosticCard();
        connection("Memória local", "Ativa • SQLite no Tab 15");
        connection("Tomada inteligente", "Não configurada (opcional)");
    }

    private void tvDiagnosticCard() {
        LinearLayout c = card();
        c.addView(text("LIGAÇÃO À TV LG", 17, INK, true));
        margin(text("Diagnóstico local. Não faz emparelhamento nem envia comandos à TV.",
            13, MUTE, false), c, 7);
        SharedPreferences p = getSharedPreferences("hub", MODE_PRIVATE);
        EditText ip = new EditText(this);
        ip.setSingleLine(true);
        ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        ip.setHint("IP da TV na rede (ex.: 192.168.1.100)");
        ip.setText(p.getString("tv_ip", ""));
        margin(ip, c, 9);
        margin(button("Guardar IP e testar ligação", PURPLE, false, () -> {
            final String host = ip.getText().toString().trim();
            if (!host.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
                Toast.makeText(this, "Indica um endereço IPv4 válido", Toast.LENGTH_LONG).show();
                return;
            }
            String[] octets = host.split("\\.");
            for (String octet : octets) {
                if (Integer.parseInt(octet) > 255) {
                    Toast.makeText(this, "IP inválido", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            p.edit().putString("tv_ip", host).apply();
            Toast.makeText(this, "A verificar ligação à TV...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                boolean found = false;
                for (int port : new int[]{3001, 3000}) {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), 1800);
                        found = true;
                        break;
                    } catch (Exception ignored) {}
                }
                final boolean reachable = found;
                runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(reachable ? "Serviço webOS encontrado" : "Sem resposta da TV")
                    .setMessage(reachable
                        ? "A TV aceita ligações de rede. O emparelhamento e os comandos serão adicionados posteriormente."
                        : "Não foi possível abrir as portas webOS 3000/3001. Verifica o IP, se a TV está ligada e se ambos estão na mesma rede.")
                    .setPositiveButton("OK", null)
                    .show());
            }).start();
        }), c, 10);
        margin(c, content, 12);
    }

    private void connection(String name, String state) {
        LinearLayout c = card();
        c.addView(text(name, 17, INK, true));
        margin(text(state, 13, MUTE, false), c, 5);
        margin(c, content, 12);
    }

    private void showBattery() {
        LinearLayout c = card();
        c.addView(text("BATERIA DO TAB 15", 12, MUTE, true));
        margin(text(batteryPercentage() + "%", 48, PURPLE, true), c, 8);
        margin(text(isCharging() ? "Carregador ligado" : "A funcionar com bateria",
            15, INK, false), c, 7);
        margin(c, content, 20);

        LinearLayout policy = card();
        policy.addView(text("POLÍTICA DE CARGA PLANEADA", 15, INK, true));
        margin(text("Ligar a 40%  •  Desligar a 80%", 20, INK, true), policy, 12);
        margin(text("Modo de demonstração: NÃO controla o carregador. " +
            "Será necessário emparelhar uma tomada Wi-Fi compatível. " +
            "O tablet mantém-se funcional sem essa tomada.",
            13, MUTE, false), policy, 8);
        margin(policy, content, 13);
    }

    private void footer() {
        TextView f = text("LUMI STUDIO  •  Central Android local  •  versão experimental", 11, MUTE, false);
        f.setGravity(Gravity.CENTER);
        margin(f, content, 27);
    }
}
