package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Ligacao MCP externa ao agente XiaoZhi.
 * O endpoint e respetivo token sao introduzidos APENAS no tablet.
 * Os comandos TV usam o controlador TLS/pinning ja validado.
 */
public final class XiaozhiMcpBridge {
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebSocketClient client;
    private boolean stopped = true;

    public XiaozhiMcpBridge(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("hub", Context.MODE_PRIVATE);
    }

    public static boolean validEndpoint(String value) {
        try {
            URI url = new URI(value);
            return "wss".equalsIgnoreCase(url.getScheme())
                && "api.xiaozhi.me".equalsIgnoreCase(url.getHost())
                && "/mcp/".equals(url.getPath())
                && url.getRawQuery() != null
                && url.getRawQuery().contains("token=");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void state(String value) {
        prefs.edit().putString("mcp_state", value).apply();
    }

    public void start() {
        stopped = false;
        connect();
    }

    public void stop() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        if (client != null) {
            client.close();
            client = null;
        }
        state("Desligado");
    }

    private void reconnect() {
        if (!stopped) handler.postDelayed(this::connect, 20000);
    }

    private void connect() {
        if (stopped) return;
        String url = prefs.getString("mcp_endpoint", "");
        if (!validEndpoint(url)) {
            state("Por configurar");
            return;
        }
        state("A ligar ao XiaoZhi...");
        try {
            WebSocketClient ws = new WebSocketClient(new URI(url)) {
                @Override public void onOpen(ServerHandshake handshake) {
                    state("Ligado ao XiaoZhi");
                }
                @Override public void onMessage(String payload) {
                    try {
                        JSONObject request = new JSONObject(payload);
                        String method = request.optString("method", "");
                        if (!request.has("id")) return;
                        Object id = request.get("id");
                        if ("initialize".equals(method)) {
                            JSONObject result = new JSONObject()
                                .put("protocolVersion", "2024-11-05")
                                .put("capabilities", new JSONObject().put("tools", new JSONObject()))
                                .put("serverInfo", new JSONObject()
                                    .put("name", "LUMI-Hub").put("version", "0.7.0"));
                            reply(this, id, result);
                        } else if ("ping".equals(method)) {
                            reply(this, id, new JSONObject());
                        } else if ("tools/list".equals(method)) {
                            reply(this, id, new JSONObject().put("tools", tools()));
                        } else if ("tools/call".equals(method)) {
                            JSONObject params = request.optJSONObject("params");
                            String tool = params == null ? "" : params.optString("name", "");
                            execute(this, id, tool);
                        } else {
                            error(this, id, -32601, "Metodo MCP indisponivel");
                        }
                    } catch (Exception ignored) {
                        // Nunca guardar mensagens ou tokens da conversa em logs.
                    }
                }
                @Override public void onClose(int code, String reason, boolean remote) {
                    if (!stopped) {
                        state("Desligado; a tentar novamente");
                        reconnect();
                    }
                }
                @Override public void onError(Exception error) {
                    if (!stopped) state("Falha de ligacao MCP");
                }
            };
            client = ws;
            ws.setConnectionLostTimeout(30);
            ws.connect();
        } catch (Exception error) {
            state("Falha na configuracao MCP");
            reconnect();
        }
    }

    private static JSONArray tools() throws Exception {
        JSONArray list = new JSONArray();
        list.put(tool("home.tv_desligar", "Desliga a TV da Sala por webOS."));
        list.put(tool("home.tv_ligar", "Liga a TV da Sala por Wake-on-LAN, quando configurado."));
        list.put(tool("home.tv_volume_mais", "Aumenta o volume da TV da Sala."));
        list.put(tool("home.tv_volume_menos", "Reduz o volume da TV da Sala."));
        list.put(tool("home.tv_silenciar", "Silencia a TV da Sala."));
        list.put(tool("home.tv_youtube", "Abre o YouTube na TV da Sala."));
        list.put(tool("home.tv_estado", "Consulta o ultimo diagnostico conhecido da TV da Sala, nao o estado em tempo real."));
        list.put(tool("home.hub_estado", "Consulta o estado da central LUMI Hub e bateria do tablet."));
        return list;
    }

    private static JSONObject tool(String name, String description) throws Exception {
        return new JSONObject()
            .put("name", name)
            .put("description", description)
            .put("inputSchema", new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("additionalProperties", false));
    }

    private static void reply(WebSocketClient ws, Object id, JSONObject result) {
        try {
            if (ws.isOpen()) ws.send(new JSONObject()
                .put("jsonrpc", "2.0").put("id", id).put("result", result).toString());
        } catch (Exception ignored) {}
    }

    private static void error(WebSocketClient ws, Object id, int code, String message) {
        try {
            if (ws.isOpen()) ws.send(new JSONObject()
                .put("jsonrpc", "2.0").put("id", id)
                .put("error", new JSONObject().put("code", code)
                    .put("message", message)).toString());
        } catch (Exception ignored) {}
    }

    private static void result(WebSocketClient ws, Object id, String message, boolean failed) {
        try {
            JSONObject body = new JSONObject().put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", message)))
                .put("isError", failed);
            reply(ws, id, body);
        } catch (Exception ignored) {}
    }

    private void execute(WebSocketClient ws, Object id, String tool) {
        if ("home.hub_estado".equals(tool)) {
            int battery = prefs.getInt("battery", -1);
            result(ws, id, "LUMI Hub ativo. Bateria do Tab 15: " +
                (battery >= 0 ? battery + "%" : "por verificar") + ".", false);
            return;
        }
        if ("home.tv_estado".equals(tool)) {
            result(ws, id, "Ultimo diagnostico da TV da Sala: " +
                prefs.getString("lg_last_status", "Nao verificado") +
                ". Isto nao confirma se esta ligada agora.", false);
            return;
        }
        if ("home.tv_ligar".equals(tool)) {
            String mac = prefs.getString("tv_mac", "");
            if (!mac.matches("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")) {
                result(ws, id, "Configura primeiro o MAC da TV no LUMI Hub.", true);
                return;
            }
            WakeOnLan.send(mac, prefs.getString("tv_ip", ""), message ->
                result(ws, id, message, message.startsWith("Não foi possível")));
            return;
        }
        String action;
        switch (tool) {
            case "home.tv_desligar": action = "ssap://system/turnOff"; break;
            case "home.tv_volume_mais": action = "ssap://audio/volumeUp"; break;
            case "home.tv_volume_menos": action = "ssap://audio/volumeDown"; break;
            case "home.tv_silenciar": action = "ssap://audio/setMute"; break;
            case "home.tv_youtube": action = "ssap://system.launcher/launch"; break;
            default:
                error(ws, id, -32601, "Ferramenta desconhecida");
                return;
        }
        String ip = prefs.getString("tv_ip", "");
        String pin = prefs.getString("lg_cert_pin", "");
        if (ip.isEmpty() || pin.isEmpty() || prefs.getString("lg_client_key", "").isEmpty()) {
            result(ws, id, "Configura e emparelha primeiro a TV da Sala na app LUMI Hub.", true);
            return;
        }
        WebOsController.request(ip, prefs.getString("lg_client_key", ""), pin,
            action, false, (message, key) -> {
                SharedPreferences.Editor edit = prefs.edit().putString("lg_last_status", message);
                if (key != null && !key.isEmpty()) edit.putString("lg_client_key", key);
                edit.apply();
                result(ws, id, message, !message.startsWith("Comando confirmado"));
            });
    }
}
