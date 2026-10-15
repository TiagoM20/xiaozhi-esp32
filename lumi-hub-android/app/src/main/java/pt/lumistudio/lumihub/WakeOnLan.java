package pt.lumistudio.lumihub;

import android.os.Handler;
import android.os.Looper;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Liga TV LG via Wake-on-LAN. O resultado só é confirmado se a TV responder por webOS. */
public final class WakeOnLan {
    public interface Listener { void onResult(String result); }
    private WakeOnLan(){}

    public static void send(String mac,String ip,Listener callback){
        new Thread(()->{
            Handler ui=new Handler(Looper.getMainLooper());
            try{
                if(mac==null||!mac.matches("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
                    throw new IllegalArgumentException("MAC inválido");
                String[] parts=mac.split(":");byte[] address=new byte[6];
                for(int i=0;i<6;i++)address[i]=(byte)Integer.parseInt(parts[i],16);
                byte[] magic=new byte[102];
                for(int i=0;i<6;i++)magic[i]=(byte)0xff;
                for(int i=6;i<magic.length;i++)magic[i]=address[(i-6)%6];
                try(DatagramSocket socket=new DatagramSocket()){
                    socket.setBroadcast(true);
                    for(int port:new int[]{9,7})
                        socket.send(new DatagramPacket(magic,magic.length,
                            InetAddress.getByName("255.255.255.255"),port));
                    if(ip!=null&&!ip.isEmpty())
                        socket.send(new DatagramPacket(magic,magic.length,
                            InetAddress.getByName(ip),9));
                }
                ui.post(()->callback.onResult("Sinal de ligar enviado. A verificar resposta da LG…"));
                if(ip==null||ip.isEmpty())return;
                boolean reachable=false;
                for(int i=0;i<4;i++){
                    Thread.sleep(i==0?1500:3000);
                    try(Socket s=new Socket()){
                        s.connect(new InetSocketAddress(ip,3001),1200);
                        reachable=true;break;
                    }catch(Exception ignored){}
                }
                final boolean online=reachable;
                ui.post(()->callback.onResult(online ?
                    "LG webOS acessível após o sinal de ligar." :
                    "TV ainda sem resposta webOS. Confirma o MAC da ligação Wi-Fi " +
                    "e que o televisor permite Ligar por Wi-Fi. Bluetooth ativo " +
                    "não equivale a Wake-on-LAN."));
            }catch(Exception ex){
                ui.post(()->callback.onResult("Falha ao ligar TV por rede: "+
                    ex.getClass().getSimpleName()));
            }
        },"LumiTvWake").start();
    }
}
