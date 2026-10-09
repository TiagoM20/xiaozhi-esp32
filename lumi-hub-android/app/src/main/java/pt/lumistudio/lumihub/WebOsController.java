package pt.lumistudio.lumihub;

import android.os.Handler;
import android.os.Looper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class WebOsController {
    public interface Listener { void onMessage(String message, String clientKey); }
    public interface CertificateListener { void onCertificate(String fingerprint, String error); }
    private WebOsController() {}

    private static String fingerprint(X509Certificate cert) throws CertificateException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte value : digest) sb.append(String.format("%02X", value & 0xFF));
            return sb.toString();
        } catch (Exception ex) {
            throw new CertificateException("Falha a calcular assinatura SHA-256", ex);
        }
    }

    // Important: certificate inspection alone does NOT authenticate or register.
    // The user must explicitly accept the displayed fingerprint. Every subsequent
    // real WebSocket connection requires that same fingerprint (TOFU pin).
    private static SSLSocketFactory tlsFactory(final String pin) throws Exception {
        X509TrustManager trust = new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                throw new CertificateException("Autenticacao de cliente nao suportada");
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                if (chain == null || chain.length == 0)
                    throw new CertificateException("TV nao apresentou certificado TLS");
                String observed = fingerprint(chain[0]);
                if (pin != null && !pin.isEmpty() && !pin.equalsIgnoreCase(observed))
                    throw new CertificateException("Certificado da TV mudou; confirma novamente no Hub");
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trust}, null);
        return context.getSocketFactory();
    }

    public static void inspectCertificate(String ip, CertificateListener listener) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                SSLSocketFactory factory = tlsFactory("");
                try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
                    socket.connect(new InetSocketAddress(ip, 3001), 5000);
                    socket.setSoTimeout(5000);
                    socket.startHandshake();
                    X509Certificate cert = (X509Certificate) socket.getSession()
                        .getPeerCertificates()[0];
                    String sha = fingerprint(cert);
                    main.post(() -> listener.onCertificate(sha, null));
                }
            } catch (Exception ex) {
                String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                main.post(() -> listener.onCertificate(null, error));
            }
        }, "LumiLgCertProbe").start();
    }

    private static JSONObject registration(String key) throws Exception {
        JSONArray permissions = new JSONArray()
            .put("CONTROL_AUDIO")
            .put("CONTROL_POWER")
            .put("LAUNCH")
            .put("READ_INSTALLED_APPS")
            .put("READ_APP_STATUS");

        JSONObject manifest = new JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "1.0")
            .put("appId", "pt.lumistudio.lumihub")
            .put("vendorId", "LUMI Studio")
            .put("localizedAppNames", new JSONObject().put("", "LUMI Hub"))
            .put("permissions", permissions);
        // Do not claim an LG-signed identity or send a made-up signature.
        JSONObject payload = new JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", manifest);
        if (key != null && !key.isEmpty()) payload.put("client-key", key);
        return new JSONObject().put("id", "reg").put("type", "register")
            .put("payload", payload);
    }

    public static void request(String ip, String savedKey, String certificatePin,
                               String action, boolean pairOnly, Listener listener) {
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean done = new AtomicBoolean(false);
        if (certificatePin == null || certificatePin.isEmpty()) {
            main.post(() -> listener.onMessage("Primeiro confirma o certificado da TV.", null));
            return;
        }
        try {
            final WebSocketClient client = new WebSocketClient(new URI("wss://" + ip + ":3001")) {
                private String key = savedKey;
                private String stage = "a abrir WebSocket seguro";

                private void finish(String message) {
                    if (!done.compareAndSet(false, true)) return;
                    String currentKey = key;
                    main.post(() -> listener.onMessage(message, currentKey));
                    close();
                }

                @Override protected void onSetSSLParameters(SSLParameters params) {
                    // The self-signed LG certificate does not use the TV's IP
                    // as a DNS identity. The explicit SHA-256 pin above replaces
                    // hostname validation, and MUST NOT be disabled.
                    params.setEndpointIdentificationAlgorithm(null);
                }

                @Override public void onOpen(ServerHandshake response) {
                    stage = "registo na LG";
                    try {
                        send(registration(key).toString());
                    } catch (Exception ex) {
                        finish("Falha ao preparar registo LG: " + ex.getMessage());
                    }
                }

                @Override public void onMessage(String text) {
                    try {
                        JSONObject message = new JSONObject(text);
                        String type = message.optString("type");
                        String id = message.optString("id");
                        if ("error".equals(type)) {
                            finish("LG recusou operação: " + message.optString("error", text));
                            return;
                        }
                        if ("reg".equals(id) && "response".equals(type)) {
                            JSONObject payload = message.optJSONObject("payload");
                            if (payload != null && "PROMPT".equals(payload.optString("pairingType"))) {
                                stage = "à espera de confirmação no ecrã da TV";
                            }
                            return;
                        }
                        if ("registered".equals(type)) {
                            JSONObject payload = message.optJSONObject("payload");
                            if (payload != null) {
                                String newKey = payload.optString("client-key", "");
                                if (!newKey.isEmpty()) key = newKey;
                            }
                            if (key == null || key.isEmpty()) {
                                finish("Emparelhamento sem chave. Aceita o pedido na televisão.");
                                return;
                            }
                            if (pairOnly) {
                                finish("Emparelhamento LG concluído. Chave guardada no tablet.");
                                return;
                            }
                            stage = "a executar comando";
                            JSONObject cmd = new JSONObject()
                                .put("id", "cmd")
                                .put("type", "request")
                                .put("uri", action);
                            if ("ssap://system.launcher/launch".equals(action)) {
                                cmd.put("payload", new JSONObject().put("id", "youtube.leanback.v4"));
                            } else if ("ssap://audio/setMute".equals(action)) {
                                cmd.put("payload", new JSONObject().put("mute", true));
                            }
                            send(cmd.toString());
                            return;
                        }
                        if ("cmd".equals(id)) {
                            JSONObject payload = message.optJSONObject("payload");
                            if (payload != null && !payload.optBoolean("returnValue", true)) {
                                finish("Comando recusado pela TV: " + payload.toString());
                            } else {
                                finish("Comando confirmado pela TV.");
                            }
                        }
                    } catch (Exception ex) {
                        finish("Resposta LG inválida: " + ex.getClass().getSimpleName()
                            + ": " + ex.getMessage());
                    }
                }

                @Override public void onError(Exception ex) {
                    finish("Erro " + stage + ": " + ex.getClass().getSimpleName()
                        + ": " + ex.getMessage());
                }
                @Override public void onClose(int code, String reason, boolean remote) {
                    if (!done.get()) {
                        finish("Ligação segura encerrada " + stage + " (código " + code
                            + ", remoto=" + remote + "). "
                            + ((reason == null || reason.isEmpty()) ? "Sem detalhe." : reason));
                    }
                }
            };
            client.setSocketFactory(tlsFactory(certificatePin));
            client.setConnectionLostTimeout(0);
            client.connect();
            main.postDelayed(() -> {
                if (done.compareAndSet(false, true)) {
                    client.close();
                    listener.onMessage(
                        "Tempo esgotado (60s). Aceita o pedido no ecrã LG e repete o teste.",
                        null);
                }
            }, 60000);
        } catch (Exception ex) {
            String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            main.post(() -> listener.onMessage("Falha ao iniciar ligação segura: " + error, null));
        }
    }
}
