package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

/** Ponte local de voz: DEMO sem chaves, conversa Raquel PT-PT com Azure + OpenAI. */
public final class DeskVoiceGateway implements AutoCloseable {
    private final SharedPreferences prefs;
    private final Context context;
    private final Map<WebSocket,Session> sessions=new ConcurrentHashMap<>();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private WebSocketServer webSocket;
    private DeskTestOta ota;

    private static class Session {
        final AtomicBoolean busy=new AtomicBoolean(false);
        final ByteArrayOutputStream pcm=new ByteArrayOutputStream();
        LumiOpus.Decoder decoder;
        boolean listening, heard, closed;
        int frames, voice, silence;
        Session(){try{decoder=new LumiOpus.Decoder();}catch(Exception ignored){}}
        void start(){
            pcm.reset();listening=true;heard=false;frames=0;voice=0;silence=0;
        }
    }
    public DeskVoiceGateway(Context ctx){
        context=ctx.getApplicationContext();
        prefs=context.getSharedPreferences("hub",Context.MODE_PRIVATE);
    }
    private void status(String s){prefs.edit().putString("desk_gateway_state",s).apply();}
    private boolean configured(){
        return !LumiSecrets.get(context,"azure").isEmpty()
            && !LumiSecrets.get(context,"openai").isEmpty();
    }
    public void start(){
        if(!running.compareAndSet(false,true))return;
        final String token=DeskTestOta.key(prefs);
        try{
            ota=new DeskTestOta(context);ota.start();
            webSocket=new WebSocketServer(new InetSocketAddress("0.0.0.0",8765)){
                @Override public void onStart(){
                    status("Ponte local ativa · "+(configured()?"voz PT-PT":"DEMO"));
                }
                @Override public void onOpen(WebSocket c,ClientHandshake h){
                    if(!"/xiaozhi/v1/".equals(h.getResourceDescriptor())
                        || !("Bearer "+token).equals(h.getFieldValue("Authorization"))){
                        c.close(1008,"Emparelhamento obrigatório");return;
                    }
                    sessions.put(c,new Session());status("DESK ligada à ponte local");
                }
                @Override public void onMessage(WebSocket c,String msg){
                    Session s=sessions.get(c);if(s==null||s.closed)return;
                    try{
                        JSONObject j=new JSONObject(msg);
                        String type=j.optString("type");
                        if("hello".equals(type)){
                            c.send(new JSONObject().put("type","hello")
                                .put("transport","websocket")
                                .put("session_id",UUID.randomUUID().toString())
                                .put("audio_params",new JSONObject()
                                    .put("format","opus").put("sample_rate",24000)
                                    .put("channels",1).put("frame_duration",60))
                                .toString());
                            status("Handshake XiaoZhi concluído");
                        }else if("listen".equals(type)){
                            String state=j.optString("state");
                            if("start".equals(state)&&!s.busy.get()){
                                s.start();
                                if(configured())status("A ouvir em português de Portugal");
                                else if(s.busy.compareAndSet(false,true))
                                    new Thread(()->demo(c,s),"LumiDemo").start();
                            }else if("stop".equals(state)&&s.listening&&configured()){
                                complete(c,s);
                            }
                        }else if("abort".equals(type)){
                            s.listening=false;
                        }
                    }catch(Exception e){status("Mensagem de protocolo inválida");}
                }
                @Override public void onMessage(WebSocket c,ByteBuffer b){
                    Session s=sessions.get(c);
                    if(s==null||s.closed||!s.listening||s.busy.get()||!configured())return;
                    byte[] packet=new byte[b.remaining()];b.get(packet);
                    accumulate(c,s,packet);
                }
                @Override public void onClose(WebSocket c,int code,String reason,boolean remote){
                    Session s=sessions.remove(c);
                    if(s!=null){s.closed=true;s.listening=false;}
                    if(running.get())status("DESK desligada");
                }
                @Override public void onError(WebSocket c,Exception error){
                    if(running.get())status("Falha WebSocket");
                }
            };
            webSocket.setReuseAddr(true);webSocket.start();
        }catch(Exception e){status("Falha a abrir portas 8765 e 8766");close();}
    }
    private void accumulate(WebSocket c,Session s,byte[] packet){
        try{
            if(s.decoder==null)throw new Exception("Sem descodificador Opus");
            short[] decoded=s.decoder.decode(packet);
            long level=0;
            for(int i=0;i<decoded.length;i+=4)level+=Math.abs((int)decoded[i]);
            level/=Math.max(1,(decoded.length+3)/4);
            s.frames++;
            if(level>400){s.voice++;s.heard=true;s.silence=0;}
            else if(s.heard)s.silence++;
            LumiOpus.appendShorts(s.pcm,decoded);
            // VAD básico experimental. Máximo de 12 segundos por pedido.
            if((s.heard&&s.voice>=3&&s.silence>=18)||s.frames>=200)
                complete(c,s);
        }catch(Exception e){
            s.listening=false;status("Falha na descodificação da voz da DESK");
        }
    }
    private void complete(WebSocket c,Session s){
        if(!s.listening||!s.busy.compareAndSet(false,true))return;
        s.listening=false;
        byte[] pcm=s.pcm.toByteArray();s.pcm.reset();
        if(s.voice<3||pcm.length<6400){
            s.busy.set(false);
            status("Não foi detetado pedido de voz suficientemente longo");
            return;
        }
        new Thread(()->{
            try{
                if(s.closed||!c.isOpen())return;
                status("A transcrever voz PT-PT…");
                String heard=LumiCloudVoice.transcribe(context,prefs,pcm);
                if(heard.isEmpty())throw new Exception("Áudio sem palavras");
                status("Ouvido: "+heard.substring(0,Math.min(80,heard.length())));
                String answer=LumiCloudVoice.respond(context,prefs,heard);
                if(answer.isEmpty())throw new Exception("Resposta de IA vazia");
                status("A sintetizar Raquel Neural…");
                byte[] speech=LumiCloudVoice.speak(context,prefs,answer);
                if(!s.closed&&c.isOpen())playPcm(c,s,answer,speech);
            }catch(Exception e){
                status("Conversa PT-PT: "+e.getMessage());
            }finally{s.busy.set(false);}
        },"LumiDynamicConversation").start();
    }
    private void startSpeaking(WebSocket c,String text)throws Exception{
        c.send(new JSONObject().put("type","tts").put("state","start").toString());
        c.send(new JSONObject().put("type","tts").put("state","sentence_start")
            .put("text",text).toString());
    }
    private void stopSpeaking(WebSocket c)throws Exception{
        if(c.isOpen())c.send(new JSONObject().put("type","tts")
            .put("state","stop").toString());
    }
    private void playPcm(WebSocket c,Session s,String text,byte[] pcm)throws Exception{
        startSpeaking(c,text);SystemClock.sleep(120);
        int n=LumiOpus.sendPcm(pcm,packet->{
            if(s.closed||!running.get()||!c.isOpen())return false;
            c.send(packet);return true;
        });
        SystemClock.sleep(240);stopSpeaking(c);
        status("Raquel PT-PT: "+n+" pacotes transmitidos");
    }
    private void demo(WebSocket c,Session s){
        int count=0;
        try(DataInputStream input=new DataInputStream(context.getAssets()
            .open("lumi-raquel-24k-60ms.pkt"))){
            startSpeaking(c,"Olá, sou a Lumi. Estou pronta para conversar contigo.");
            SystemClock.sleep(120);
            long begin=SystemClock.elapsedRealtime();
            while(running.get()&&!s.closed&&c.isOpen()){
                int n;
                try{n=input.readUnsignedShort();}
                catch(java.io.EOFException eof){break;}
                if(n<1||n>8192)throw new Exception("Pacote Opus inválido");
                byte[] pkt=new byte[n];input.readFully(pkt);
                c.send(pkt);count++;
                long wait=begin+Math.max(0,count-2)*60L-SystemClock.elapsedRealtime();
                if(wait>0)SystemClock.sleep(Math.min(wait,110));
            }
            SystemClock.sleep(240);stopSpeaking(c);
            status("DEMO Raquel: "+count+" pacotes transmitidos");
        }catch(Exception e){status("DEMO interrompida");}
        finally{s.listening=false;s.busy.set(false);}
    }
    @Override public void close(){
        running.set(false);
        for(Map.Entry<WebSocket,Session> entry:sessions.entrySet()){
            entry.getValue().closed=true;
            try{entry.getKey().close(1001);}catch(Exception ignored){}
        }
        sessions.clear();
        try{if(ota!=null)ota.close();}catch(Exception ignored){}
        try{if(webSocket!=null)webSocket.stop(1000);}catch(Exception ignored){}
        status("Ponte local parada");
    }
}