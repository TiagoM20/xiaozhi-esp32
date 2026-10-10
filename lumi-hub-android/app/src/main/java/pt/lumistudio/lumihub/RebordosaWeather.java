package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Meteorologia exterior de Rebordosa, Paredes. Dados Open-Meteo, sem localização do utilizador. */
public final class RebordosaWeather {
    private static final String API = "https://api.open-meteo.com/v1/forecast"
        + "?latitude=41.22405&longitude=-8.40669"
        + "&current=temperature_2m,relative_humidity_2m,weather_code,is_day"
        + "&timezone=Europe%2FLisbon";
    private static final long CACHE_MS = 15L * 60L * 1000L;
    private static final long ERROR_RETRY_MS = 3L * 60L * 1000L;
    private static volatile boolean fetching = false;

    private RebordosaWeather() {}

    public static final class State {
        public final String temperature;
        public final String humidity;
        public final String condition;
        public final String icon;
        public final String updated;
        public final boolean available;

        private State(String temperature, String humidity, String condition,
                      String icon, String updated, boolean available) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.condition = condition;
            this.icon = icon;
            this.updated = updated;
            this.available = available;
        }
    }

    public static State read(Context context) {
        SharedPreferences p = context.getSharedPreferences("lumi_weather", Context.MODE_PRIVATE);
        long success = p.getLong("success", 0);
        if (success == 0) return new State("--°", "--%", "A obter meteorologia", "☁",
            "Dados exteriores · Open-Meteo", false);
        long age = System.currentTimeMillis() - success;
        int code = p.getInt("code", -1);
        int day = p.getInt("day", 1);
        String time = new java.text.SimpleDateFormat("HH:mm",
            new Locale("pt", "PT")).format(new java.util.Date(success));
        String updated = age > 45L * 60L * 1000L
            ? "Últimos dados às " + time + " · podem estar desatualizados"
            : "Atualizado às " + time + " · Open-Meteo";
        return new State(
            String.format(Locale.forLanguageTag("pt-PT"), "%.0f°", p.getFloat("temp", 0f)),
            p.getInt("humidity", 0) + "%",
            condition(code),
            symbol(code, day == 1),
            updated,
            true
        );
    }

    public static void refreshIfNeeded(Context context, Runnable onChanged) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("lumi_weather", Context.MODE_PRIVATE);
        long lastAttempt = prefs.getLong("attempt", 0);
        long lastSuccess = prefs.getLong("success", 0);
        long now = System.currentTimeMillis();
        long minDelay = lastSuccess > 0 ? CACHE_MS : ERROR_RETRY_MS;
        if (now - lastAttempt < minDelay || fetching) return;
        fetching = true;
        prefs.edit().putLong("attempt", now).apply();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "LUMI-Hub-Android/0.8");
                if (connection.getResponseCode() != 200)
                    throw new Exception("HTTP " + connection.getResponseCode());
                StringBuilder data = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (data.length() > 24000) throw new Exception("Resposta demasiado grande");
                        data.append(line);
                    }
                }
                JSONObject current = new JSONObject(data.toString()).getJSONObject("current");
                double temp = current.getDouble("temperature_2m");
                int humidity = current.getInt("relative_humidity_2m");
                int code = current.getInt("weather_code");
                int day = current.optInt("is_day", 1);
                if (temp < -90 || temp > 65 || humidity < 0 || humidity > 100)
                    throw new Exception("Dados meteorológicos inválidos");
                prefs.edit().putFloat("temp", (float)temp)
                    .putInt("humidity", humidity).putInt("code", code)
                    .putInt("day", day).putLong("success", System.currentTimeMillis()).apply();
                new Handler(Looper.getMainLooper()).post(onChanged);
            } catch (Exception ignored) {
                // Não substituir dados reais por valores inventados em caso de falha.
            } finally {
                if (connection != null) connection.disconnect();
                fetching = false;
            }
        }, "LumiRebordosaWeather").start();
    }

    private static String condition(int code) {
        if (code == 0) return "Céu limpo";
        if (code == 1 || code == 2) return "Pouco nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Nevoeiro";
        if (code >= 51 && code <= 57) return "Chuviscos";
        if (code >= 61 && code <= 67) return "Chuva";
        if (code >= 71 && code <= 77) return "Neve";
        if (code >= 80 && code <= 82) return "Aguaceiros";
        if (code >= 85 && code <= 86) return "Aguaceiros de neve";
        if (code >= 95) return "Trovoada";
        return "Condições exteriores";
    }

    private static String symbol(int code, boolean day) {
        if (code == 0) return day ? "☀" : "☾";
        if (code == 1 || code == 2) return day ? "⛅" : "☁";
        if (code == 3 || code == 45 || code == 48) return "☁";
        if (code >= 71 && code <= 77 || code >= 85 && code <= 86) return "❄";
        if (code >= 95) return "⚡";
        return "☂";
    }
}
