package pt.lumistudio.lumihub;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public final class WakeOnLan {
    public interface Listener { void onResult(String result); }
    private WakeOnLan() {}

    public static void send(String mac, String ip, Listener callback) {
        new Thread(() -> {
            try {
                String[] parts = mac.split(":");
                if (parts.length != 6) throw new Exception("Endereço MAC inválido");
                byte[] address = new byte[6];
                for(int i=0;i<6;i++)address[i] = (byte)Integer.parseInt(parts[i],16);
                byte[] magic = new byte[102];
                for(int i=0;i<6;i++)magic[i]=(byte)0xFF;
                for(int i=6;i<102;i++)magic[i]=address[(i-6)%6];
                try(DatagramSocket socket=new DatagramSocket()) {
                    socket.setBroadcast(true);
                    // Global broadcast is useful on simple home networks.
                    socket.send(new DatagramPacket(magic,magic.length,
                        InetAddress.getByName("255.255.255.255"),9));
                    // Additional directed packet where the destination is known.
                    if(ip!=null&&!ip.isEmpty())
                        socket.send(new DatagramPacket(magic,magic.length,
                            InetAddress.getByName(ip),9));
                }
                callback.onResult("Pacote Wake-on-LAN enviado. Aguarda alguns segundos. "
                    +"Se a TV não ligar, verifica nas definições LG a opção de "
                    +"ligar pela rede. O resultado ainda não foi confirmado pela TV.");
            } catch(Exception ex) {
                callback.onResult("Não foi possível enviar Wake-on-LAN: "
                    + ex.getClass().getSimpleName()+": "+ex.getMessage());
            }
        },"LumiWakeOnLan").start();
    }
}
