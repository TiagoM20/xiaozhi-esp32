package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.util.Base64;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * LUMI CORE: API exclusivamente LAN para gestão Windows.
 * AES-256-GCM ponta a ponta com chave de emparelhamento de 256 bits. HTTP transporta
 * apenas envelopes cifrados; nunca chaves cloud, MCP ou conversas em claro.
 * O utilizador precisa de ativar explicitamente a ligação no tablet.
 */
public final class LumiCoreSyncServer implements AutoCloseable {
    private static final String AAD="LUMI-CORE-SYNC-V1";
    private static final int PORT=8770;
    private static final SecureRandom RNG=new SecureRandom();
    private final Context context;
    private final SharedPreferences prefs;
    private final HashSet<String> nonces=new HashSet<>();
    private volatile boolean running=false;
    private ServerSocket server;
    private Thread worker;

    LumiCoreSyncServer(Context context){
        this.context=context.getApplicationContext();
        prefs=this.context.getSharedPreferences("hub",Context.MODE_PRIVATE);
    }
    static synchronized String pairingKey(Context context){
        SharedPreferences p=context.getApplicationContext().getSharedPreferences("hub",Context.MODE_PRIVATE);
        String existing=p.getString("core_windows_pair_key","");
        if(existing.matches("[0-9a-f]{64}"))return existing;
        byte[] value=new byte[32];RNG.nextBytes(value);
        StringBuilder s=new StringBuilder();
        for(byte b:value)s.append(String.format(Locale.ROOT,"%02x",b&255));
        String key=s.toString();
        p.edit().putString("core_windows_pair_key",key).commit();
        return key;
    }
    static synchronized void rotatePairingKey(Context context){
        context.getApplicationContext().getSharedPreferences("hub",Context.MODE_PRIVATE)
            .edit().remove("core_windows_pair_key").commit();
        pairingKey(context);
    }
    static String localIp(){return DeskTestOta.localIp();}
    void start() throws Exception{
        if(running)return;
        pairingKey(context);
        server=new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress("0.0.0.0",PORT),6);
        running=true;
        DeskLog.event(context,"CORE","Servidor Windows ativo na porta 8770");
        worker=new Thread(()->{
            while(running){
                try{
                    Socket socket=server.accept();
                    new Thread(()->{
                        try(Socket client=socket){serve(client);}
                        catch(Exception ignored){}
                    },"LumiCoreClient").start();
                }catch(Exception e){if(running)DeskLog.event(context,"CORE","Falha de comunicação LAN");}
            }
        },"LumiCoreListener");
        worker.setDaemon(true);worker.start();
    }
    private static String readLine(BufferedReader reader,int limit)throws Exception{
        String s=reader.readLine();
        if(s==null||s.length()>limit)throw new Exception("Pedido inválido");
        return s;
    }
    private void serve(Socket socket){
        try{
            socket.setSoTimeout(6000);
            // Only private LAN peers should be able to access the endpoint.
            if(!socket.getInetAddress().isSiteLocalAddress())return;
            InputStream raw=socket.getInputStream();
            BufferedReader in=new BufferedReader(new InputStreamReader(raw,StandardCharsets.US_ASCII));
            String request=readLine(in,2048);
            int bytes=0;
            String line;
            while(!(line=readLine(in,4096)).isEmpty()){
                if(line.regionMatches(true,0,"Content-Length:",0,15))
                    bytes=Integer.parseInt(line.substring(15).trim());
            }
            if(!request.startsWith("POST /lumi-core/v1 HTTP/1."))return;
            if(bytes<2||bytes>262144)return;
            // BufferedReader may prefetch request bytes: read the JSON body as chars.
            char[] chars=new char[bytes];int count=0;
            while(count<bytes){
                int n=in.read(chars,count,bytes-count);
                if(n<0)throw new Exception("Pedido incompleto");
                count+=n;
            }
            JSONObject envelope=new JSONObject(new String(chars));
            String nonce=envelope.getString("n");
            byte[] iv=Base64.decode(nonce,Base64.NO_WRAP);
            if(iv.length!=12)throw new Exception("IV inválido");
            synchronized(nonces){
                if(!nonces.add(nonce))throw new Exception("Pedido repetido");
                if(nonces.size()>1000)nonces.clear();
            }
            String secret=pairingKey(context);
            byte[] key=hex(secret);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
            byte[] plain=cipher.doFinal(Base64.decode(envelope.getString("d"),Base64.NO_WRAP));
            JSONObject payload=new JSONObject(new String(plain,StandardCharsets.UTF_8));
            long time=payload.optLong("ts",0);
            if(Math.abs(System.currentTimeMillis()/1000L-time)>300)
                throw new Exception("Relógios dessincronizados");
            String cmd=payload.optString("cmd");
            JSONObject response=new JSONObject().put("ok",true).put("cmd",cmd).put("request",nonce);
            if("status".equals(cmd))response.put("data",getStatus());
            else if("logs".equals(cmd)){
                String logs=DeskLog.contents(context);
                if(logs.length()>90000)logs=logs.substring(logs.length()-90000);
                response.put("data",new JSONObject().put("text",logs));
            }else if("configure".equals(cmd)){
                JSONObject config=payload.getJSONObject("data");
                configure(config);
                response.put("data",new JSONObject().put("message","Configuração aplicada no Blackview"));
                DeskLog.event(context,"CORE","Configuração atualizada pelo Windows");
            }else throw new Exception("Comando desconhecido");
            byte[] responseIv=new byte[12];RNG.nextBytes(responseIv);
            Cipher out=Cipher.getInstance("AES/GCM/NoPadding");
            out.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,responseIv));
            out.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=out.doFinal(response.toString().getBytes(StandardCharsets.UTF_8));
            JSONObject reply=new JSONObject()
                .put("n",Base64.encodeToString(responseIv,Base64.NO_WRAP))
                .put("d",Base64.encodeToString(encrypted,Base64.NO_WRAP));
            byte[] body=reply.toString().getBytes(StandardCharsets.UTF_8);
            String headers="HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                +"Content-Length: "+body.length+"\r\nCache-Control: no-store\r\n"
                +"Connection: close\r\n\r\n";
            OutputStream stream=socket.getOutputStream();
            stream.write(headers.getBytes(StandardCharsets.US_ASCII));
            stream.write(body);stream.flush();
        }catch(Exception e){
            // Authentication failures are deliberately not returned to unauthenticated peers.
            try{
                byte[] body="{\"error\":\"Pedido recusado\"}".getBytes(StandardCharsets.UTF_8);
                OutputStream out=socket.getOutputStream();
                out.write(("HTTP/1.1 403 Forbidden\r\nContent-Length: "+body.length+
                    "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(body);out.flush();
            }catch(Exception ignored){}
        }
    }
    private JSONObject getStatus()throws Exception{
        String version="?";
        try{
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            version=info.versionName;
        }catch(Exception ignored){}
        JSONObject state=new JSONObject();
        state.put("version",version);
        state.put("tablet_ip",localIp()==null?"":localIp());
        state.put("bridge_state",prefs.getString("desk_gateway_state","Por verificar"));
        state.put("bridge_enabled",prefs.getBoolean("desk_gateway_enabled",false));
        state.put("central_enabled",prefs.getBoolean("service_running",false));
        state.put("azure_configured",!LumiSecrets.get(context,"azure").isEmpty());
        state.put("openai_configured",!LumiSecrets.get(context,"openai").isEmpty());
        state.put("azure_region",prefs.getString("lumi_azure_region","westeurope"));
        state.put("openai_model",prefs.getString("lumi_openai_model","gpt-4.1-mini"));
        state.put("tv_ip",prefs.getString("tv_ip",""));
        state.put("tv_mac",prefs.getString("tv_mac",""));
        state.put("theme",prefs.getInt("hub_theme",0));
        state.put("mcp_configured",!prefs.getString("mcp_endpoint","").isEmpty());
        return state;
    }
    private void configure(JSONObject update)throws Exception{
        // Validate all values first; absent fields mean keep the tablet's current value.
        if(update.has("azure_region")&&!update.getString("azure_region").matches("[a-z0-9]{3,25}"))
            throw new Exception("Região inválida");
        if(update.has("openai_model")&&!update.getString("openai_model").matches("[a-zA-Z0-9_.-]{3,64}"))
            throw new Exception("Modelo inválido");
        if(update.has("tv_ip")&&!update.getString("tv_ip").isEmpty()&&
                !update.getString("tv_ip").matches("([0-9]{1,3}\\.){3}[0-9]{1,3}"))
            throw new Exception("IP inválido");
        if(update.has("tv_mac")&&!update.getString("tv_mac").isEmpty()&&
                !update.getString("tv_mac").matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))
            throw new Exception("MAC inválido");
        if(update.has("theme")&&(update.getInt("theme")<0||update.getInt("theme")>=HubThemes.NAMES.length))
            throw new Exception("Tema inválido");
        String azure=update.optString("azure_key","");
        String ai=update.optString("openai_key","");
        if(azure.length()>2048||ai.length()>2048)throw new Exception("Chave inválida");
        if(!azure.isEmpty())LumiSecrets.put(context,"azure",azure);
        if(!ai.isEmpty())LumiSecrets.put(context,"openai",ai);
        SharedPreferences.Editor edit=prefs.edit();
        if(update.has("azure_region"))edit.putString("lumi_azure_region",update.getString("azure_region"));
        if(update.has("openai_model"))edit.putString("lumi_openai_model",update.getString("openai_model"));
        if(update.has("tv_ip")){
            String ip=update.getString("tv_ip");
            if(!ip.equals(prefs.getString("tv_ip","")))
                edit.remove("lg_client_key").remove("lg_cert_pin").remove("lg_last_status");
            edit.putString("tv_ip",ip);
        }
        if(update.has("tv_mac"))edit.putString("tv_mac",update.getString("tv_mac").toUpperCase(Locale.ROOT));
        if(update.has("theme"))edit.putInt("hub_theme",update.getInt("theme"));
        edit.commit();
    }
    private static byte[] hex(String s){
        byte[] b=new byte[s.length()/2];
        for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return b;
    }
    @Override public void close(){
        running=false;
        try{if(server!=null)server.close();}catch(Exception ignored){}
        DeskLog.event(context,"CORE","Servidor Windows parado");
    }
}
