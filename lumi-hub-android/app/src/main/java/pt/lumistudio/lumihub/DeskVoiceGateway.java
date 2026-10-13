package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

/**
 * Ponte áudio de teste V12, na rede local.
 * Apenas envia uma amostra gravada em Opus; não é ainda um ASR nem um LLM.
 */
public final class DeskVoiceGateway implements AutoCloseable {
    private final SharedPreferences prefs;
    private final Context context;
    private final Set<WebSocket> authorized=ConcurrentHashMap.newKeySet();
    private final Set<WebSocket> played=ConcurrentHashMap.newKeySet();
    private final Set<WebSocket> interrupted=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private WebSocketServer webSocket;
    private DeskTestOta ota;

    public DeskVoiceGateway(Context context){
        this.context=context.getApplicationContext();
        prefs=this.context.getSharedPreferences("hub",Context.MODE_PRIVATE);
    }
    private void status(String value){
        prefs.edit().putString("desk_gateway_state",value).apply();
    }
    public void start(){
        if(!running.compareAndSet(false,true))return;
        final String token=DeskTestOta.key(prefs);
        try{
            ota=new DeskTestOta(context);
            ota.start();
            webSocket=new WebSocketServer(new InetSocketAddress("0.0.0.0",8765)){
                @Override public void onStart(){
                    status("Ponte de voz disponível na rede local");
                }
                @Override public void onOpen(WebSocket client,ClientHandshake h){
                    if(!"/xiaozhi/v1/".equals(h.getResourceDescriptor())
                        || !("Bearer "+token).equals(h.getFieldValue("Authorization"))){
                        client.close(1008,"Emparelhamento obrigatório");
                        return;
                    }
                    authorized.add(client);status("LUMI DESK ligada · à espera de hello");
                }
                @Override public void onMessage(WebSocket client,String text){
                    if(!authorized.contains(client))return;
                    try{
                        JSONObject request=new JSONObject(text);
                        String type=request.optString("type","");
                        if("hello".equals(type)){
                            client.send(new JSONObject().put("type","hello")
                                .put("transport","websocket")
                                .put("session_id",UUID.randomUUID().toString())
                                .put("audio_params",new JSONObject()
                                    .put("format","opus").put("sample_rate",24000)
                                    .put("channels",1).put("frame_duration",60))
                                .toString());
                            status("Handshake XiaoZhi concluído");
                        }else if("listen".equals(type)
                            && "start".equals(request.optString("state"))
                            && played.add(client)){
                            new Thread(()->playGreeting(client),"LumiOpusDemo").start();
                        }else if("abort".equals(type)){
                            interrupted.add(client);
                        }
                    }catch(Exception e){status("JSON de teste inválido");}
                }
                @Override public void onMessage(WebSocket client,ByteBuffer data){
                    // Áudio recebido, mas ASR ainda não implementado. Não guardar.
                }
                @Override public void onClose(WebSocket client,int code,String reason,
                    boolean remote){
                    authorized.remove(client);played.remove(client);
                    interrupted.add(client);
                    if(running.get())status("Dispositivo desligado do teste");
                }
                @Override public void onError(WebSocket client,Exception error){
                    if(running.get())status("Falha na ponte WebSocket");
                }
            };
            webSocket.setReuseAddr(true);
            webSocket.start();
        }catch(Exception error){
            status("Não foi possível abrir 8765/8766");
            close();
        }
    }
    private void playGreeting(WebSocket client){
        status("A enviar amostra da Raquel PT-PT para a DESK");
        int count=0;
        try(DataInputStream packets=new DataInputStream(
            context.getAssets().open("lumi-raquel-24k-60ms.pkt"))){
            client.send(new JSONObject().put("type","tts")
                .put("state","start").toString());
            client.send(new JSONObject().put("type","tts")
                .put("state","sentence_start")
                .put("text","Olá, sou a Lumi. Estou pronta para conversar contigo.")
                .toString());
            while(running.get()&&client.isOpen()&&!interrupted.contains(client)){
                int n;
                try{n=packets.readUnsignedShort();}
                catch(java.io.EOFException finished){break;}
                if(n<1||n>8192)throw new IllegalStateException("Opus inválido");
                byte[] frame=new byte[n];packets.readFully(frame);
                client.send(frame);count++;SystemClock.sleep(60);
            }
            if(client.isOpen())client.send(new JSONObject()
                .put("type","tts").put("state","stop").toString());
            status("Amostra transmitida: "+count+" pacotes de áudio");
        }catch(Exception error){
            status("Falta áudio de teste ou ligação interrompida");
            try{if(client.isOpen())client.send(new JSONObject()
                .put("type","tts").put("state","stop").toString());}
            catch(Exception ignored){}
        }
    }
    @Override public void close(){
        running.set(false);
        for(WebSocket socket:authorized){
            try{socket.close(1001);}catch(Exception ignored){}
        }
        authorized.clear();played.clear();interrupted.clear();
        try{if(ota!=null)ota.close();}catch(Exception ignored){}
        try{if(webSocket!=null)webSocket.stop(1000);}catch(Exception ignored){}
        status("Ponte local parada");
    }
}
