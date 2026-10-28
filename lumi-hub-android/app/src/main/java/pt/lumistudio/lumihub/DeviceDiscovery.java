package pt.lumistudio.lumihub;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Opt-in local discovery. Does not pair, sign in or claim device control. */
final class DeviceDiscovery {
    static final class Candidate {
        final String name,ip,kind,origin;
        Candidate(String name,String ip,String kind,String origin){
            this.name=name;this.ip=ip;this.kind=kind;this.origin=origin;
        }
    }
    interface Listener {
        void found(Candidate candidate);
        void complete(String message);
    }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> seen=new HashSet<>();
    private final List<NsdManager.DiscoveryListener> listeners=new ArrayList<>();
    private boolean finished=false;
    DeviceDiscovery(Context context){this.context=context.getApplicationContext();}
    void start(Listener callback){
        final NsdManager nsd=(NsdManager)context.getSystemService(Context.NSD_SERVICE);
        if(nsd!=null)for(String type:new String[]{"_webos._tcp.","_googlecast._tcp.","_hap._tcp."}){
            final String serviceType=type;
            NsdManager.DiscoveryListener dl=new NsdManager.DiscoveryListener(){
                @Override public void onDiscoveryStarted(String s){}
                @Override public void onStartDiscoveryFailed(String s,int code){try{nsd.stopServiceDiscovery(this);}catch(Exception ignored){}}
                @Override public void onStopDiscoveryFailed(String s,int code){}
                @Override public void onDiscoveryStopped(String s){}
                @Override public void onServiceLost(NsdServiceInfo service){}
                @Override public void onServiceFound(NsdServiceInfo info){
                    if(finished)return;
                    try{nsd.resolveService(info,new NsdManager.ResolveListener(){
                        @Override public void onResolveFailed(NsdServiceInfo info,int code){}
                        @Override public void onServiceResolved(NsdServiceInfo service){
                            if(service.getHost()==null)return;
                            String ip=service.getHost().getHostAddress();
                            publish(new Candidate(service.getServiceName(),ip,
                                serviceType.contains("webos")?"tv":"other","mDNS"),callback);
                        }
                    });}catch(Exception ignored){}
                }
            };
            try{nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,dl);listeners.add(dl);}
            catch(Exception ignored){}
        }
        new Thread(()->scanSsdp(callback),"LUMI-SSDP-Discovery").start();
        main.postDelayed(()->{
            finished=true;
            if(nsd!=null)for(NsdManager.DiscoveryListener l:listeners)
                try{nsd.stopServiceDiscovery(l);}catch(Exception ignored){}
            callback.complete("Pesquisa terminada. Nem todos os aparelhos permitem descoberta.");
        },7500);
    }
    private void publish(Candidate c,Listener listener){
        if(!DeviceRegistry.validIp(c.ip))return;
        main.post(()->{
            if(finished || !seen.add(c.ip))return;
            listener.found(c);
        });
    }
    private void scanSsdp(Listener callback){
        WifiManager.MulticastLock lock=null;
        try{
            WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(wifi!=null){lock=wifi.createMulticastLock("LUMI-Discovery");lock.setReferenceCounted(false);lock.acquire();}
            try(DatagramSocket socket=new DatagramSocket()){
                socket.setSoTimeout(650);
                byte[] query=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"
                    +"MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII);
                socket.send(new DatagramPacket(query,query.length,InetAddress.getByName("239.255.255.250"),1900));
                long end=System.currentTimeMillis()+5700;
                byte[] buffer=new byte[4096];
                while(System.currentTimeMillis()<end){
                    DatagramPacket p=new DatagramPacket(buffer,buffer.length);
                    try{socket.receive(p);}catch(java.net.SocketTimeoutException ignored){continue;}
                    String text=new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8);
                    String name="Dispositivo na rede";
                    String lower=text.toLowerCase(java.util.Locale.ROOT);
                    if(lower.contains("webos")||lower.contains("lg smart"))name="LG webOS";
                    else if(lower.contains("roku"))name="Roku";
                    else if(lower.contains("google cast")||lower.contains("chromecast"))name="Google Cast";
                    else if(lower.contains("sonos"))name="Sonos";
                    else if(lower.contains("samsung"))name="Samsung";
                    String ip=p.getAddress().getHostAddress();
                    publish(new Candidate(name,ip,name.contains("LG")?"tv":"other","SSDP"),callback);
                }
            }
        }catch(Exception ignored){}finally{if(lock!=null&&lock.isHeld())lock.release();}
    }
}
