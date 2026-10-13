package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Locale;
import org.json.JSONObject;

/** OTA local exclusivamente para testes; NÃO anuncia atualizações de firmware. */
public final class DeskTestOta implements AutoCloseable {
    private final SharedPreferences prefs;
    private ServerSocket server;
    private Thread worker;
    private volatile boolean active;

    public DeskTestOta(Context ctx){
        prefs=ctx.getApplicationContext().getSharedPreferences("hub",Context.MODE_PRIVATE);
    }
    static String key(SharedPreferences prefs){
        String saved=prefs.getString("desk_pair_token","");
        if(saved.matches("[0-9a-f]{32}"))return saved;
        byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);
        StringBuilder b=new StringBuilder();
        for(byte value:bytes)b.append(String.format(Locale.ROOT,"%02x",value&255));
        saved=b.toString();prefs.edit().putString("desk_pair_token",saved).apply();
        return saved;
    }
    static String localIp(){
        try{
            Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();
            String fallback=null;
            while(nets.hasMoreElements()){
                NetworkInterface net=nets.nextElement();
                if(!net.isUp()||net.isLoopback())continue;
                Enumeration<InetAddress> addresses=net.getInetAddresses();
                while(addresses.hasMoreElements()){
                    InetAddress a=addresses.nextElement();
                    if(!(a instanceof Inet4Address)||a.isLoopbackAddress())continue;
                    if(net.getName().startsWith("wlan"))return a.getHostAddress();
                    if(a.isSiteLocalAddress())fallback=a.getHostAddress();
                }
            }
            return fallback;
        }catch(Exception e){return null;}
    }
    static String pairingUrl(SharedPreferences prefs){
        String ip=localIp();
        return ip==null?null:"http://"+ip+":8766/lumi/ota/?pair="+key(prefs);
    }
    public void start() throws Exception {
        server=new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress("0.0.0.0",8766));
        active=true;
        worker=new Thread(()->{
            while(active){
                try(Socket client=server.accept()){reply(client);}
                catch(Exception e){if(active)prefs.edit().putString("desk_gateway_state",
                    "Falha HTTP de teste").apply();}
            }
        },"LumiDeskOta");
        worker.setDaemon(true);worker.start();
    }
    private void reply(Socket socket){
        try{
            socket.setSoTimeout(2500);
            BufferedReader input=new BufferedReader(new InputStreamReader(
                socket.getInputStream(),StandardCharsets.US_ASCII));
            String req=input.readLine();
            for(int i=0;i<64;i++){
                String h=input.readLine();if(h==null||h.isEmpty())break;
            }
            boolean allowed=req!=null
                && (req.startsWith("GET /lumi/ota/")||req.startsWith("POST /lumi/ota/"))
                && req.contains("pair="+key(prefs)+" ");
            String ip=localIp();
            if(ip==null)allowed=false;
            String body=allowed?new JSONObject()
                .put("websocket",new JSONObject().put("url",
                    "ws://"+ip+":8765/xiaozhi/v1/")
                    .put("token",key(prefs)).put("version",1)).toString()
                :"{\"error\":\"Acesso recusado\"}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            String headers="HTTP/1.1 "+(allowed?"200 OK":"403 Forbidden")+
                "\r\nContent-Type: application/json\r\nContent-Length: "+
                bytes.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
            OutputStream out=socket.getOutputStream();
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);out.flush();
        }catch(Exception ignored){}
    }
    @Override public void close(){
        active=false;
        try{if(server!=null)server.close();}catch(Exception ignored){}
    }
}
