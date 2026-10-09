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
        if ("Memórias".equals(tab)) showMemories();
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
        margin(text("Blackview Tab 15  •  versão 0.1  •  local e privado",
            13, Color.rgb(234, 238, 255), false), box, 9);
        margin(box, content, 2);
    }

    private void navigation() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] options = {"Painel", "Memórias", "Ligações", "Bateria"};
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

    private void showConnections() {
        LinearLayout c = card();
        c.addView(text("LIGAÇÕES DA LUMI", 21, INK, true));
        margin(text("Os conectores ainda não foram ativados. Esta versão não " +
            "envia informações para fora do tablet.", 13, MUTE, false), c, 10);
        margin(c, content, 20);
        connection("LUMI ESP32-S3", "A aguardar ligação MCP/XiaoZhi");
        connection("Televisão LG webOS", "Emparelhamento ainda não realizado");
        connection("Memória local", "Ativa • SQLite no Tab 15");
        connection("Tomada inteligente", "Não configurada (opcional)");
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
