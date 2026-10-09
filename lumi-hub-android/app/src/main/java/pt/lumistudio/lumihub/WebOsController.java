package pt.lumistudio.lumihub;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebOsController {
    public interface Listener { void onMessage(String message, String clientKey); }
    private WebOsController() {}

    public static void request(String ip, String savedKey, String action,
                               boolean pairOnly, Listener listener) {
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean done = new AtomicBoolean(false);
        String endpoint = "ws://" + ip + ":3000";
        try {
            WebSocketClient client = new WebSocketClient(new URI(endpoint)) {
                private int nextId = 1;
                private boolean registered = false;
                private String key = savedKey;

                private void finish(String message) {
                    if (!done.compareAndSet(false, true)) return;
                    String currentKey = key;
                    main.post(() -> listener.onMessage(message, currentKey));
                    close();
                }
                @Override public void onOpen(ServerHandshake h) {
                    try {
                        JSONObject payload = new JSONObject();
                        payload.put("forcePairing", false);
                        payload.put("pairingType", "PROMPT");
                        payload.put("manifest", new JSONObject()
                            .put("manifestVersion", 1)
                            .put("appVersion", "1.0")
                            .put("signed", new JSONObject()
                                .put("created", "2026-10-09")
                                .put("appId", "pt.lumistudio.lumihub")
                                .put("vendorId", "LUMI Studio")
                                .put("localizedAppNames", new JSONObject().put("", "LUMI Hub"))
                                .put("permissions", new JSONArray()
                                    .put("TEST_SECURE").put("CONTROL_AUDIO")
                                    .put("CONTROL_POWER").put("LAUNCH").put("READ_INSTALLED_APPS")))
                            .put("permissions", new JSONArray()
                                .put("CONTROL_AUDIO").put("CONTROL_POWER").put("LAUNCH")));
                        if (key != null && !key.isEmpty()) payload.put("client-key", key);
                        send(new JSONObject().put("id", "reg")
                            .put("type", "register").put("payload", payload).toString());
                    } catch (Exception e) { finish("Erro de emparelhamento: " + e.getMessage()); }
                }
                @Override public void onMessage(String text) {
                    try {
                        JSONObject j = new JSONObject(text);
                        String type = j.optString("type");
                        String id = j.optString("id");
                        if ("error".equals(type)) {
                            finish("A TV recusou: " + j.optString("error", "sem detalhe")); return;
                        }
                        if ("reg".equals(id) && "registered".equals(type)) {
                            registered = true;
                            String received = j.optJSONObject("payload") == null ? "" :
                                j.optJSONObject("payload").optString("client-key");
                            if (!received.isEmpty()) key = received;
                            if (pairOnly) { finish("Emparelhamento concluído. Podes controlar a TV."); return; }
                            JSONObject command = new JSONObject();
                            command.put("id", "cmd"); command.put("type", "request"); command.put("uri", action);
                            if ("ssap://system.launcher/launch".equals(action))
                                command.put("payload", new JSONObject().put("id", "youtube.leanback.v4"));
                            if ("ssap://audio/setMute".equals(action))
                                command.put("payload", new JSONObject().put("mute", true));
                            send(command.toString()); return;
                        }
                        if ("cmd".equals(id)) {
                            JSONObject payload = j.optJSONObject("payload");
                            if (payload != null && !payload.optBoolean("returnValue", true))
                                finish("A TV recusou o comando: " + payload.toString());
                            else finish("Comando enviado à televisão.");
                        }
                    } catch (Exception e) { finish("Resposta inesperada da TV: " + e.getMessage()); }
                }
                @Override public void onClose(int code, String reason, boolean remote) {
                    if (!done.get()) finish("Ligação à TV encerrada (" + code + "). " + reason);
                }
                @Override public void onError(Exception e) {
                    finish("Erro de ligação LG: " + e.getMessage());
                }
            };
            client.connect();
            main.postDelayed(() -> {
                if (done.compareAndSet(false, true)) {
                    client.close();
                    listener.onMessage("Tempo esgotado. Verifica que a TV está ligada e aceita o emparelhamento.", null);
                }
            }, 12000);
        } catch (Exception e) {
            main.post(() -> listener.onMessage("Erro: " + e.getMessage(), null));
        }
    }
}
