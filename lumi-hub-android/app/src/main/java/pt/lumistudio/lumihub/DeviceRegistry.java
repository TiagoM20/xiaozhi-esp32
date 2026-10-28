package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** V15: only user-added devices. Discovery is not proof of controllability. */
final class DeviceRegistry {
    static final String[] ROOMS={"Sala","Cozinha","Quarto","Escritório","Exterior","Outra divisão"};
    static final class Device {
        final String id,name,room,kind,connector,host,mac;
        final boolean favorite;
        Device(String id,String name,String room,String kind,String connector,String host,String mac,boolean favorite){
            this.id=id;this.name=name;this.room=room;this.kind=kind;
            this.connector=connector;this.host=host;this.mac=mac;this.favorite=favorite;
        }
        JSONObject json() throws Exception{
            return new JSONObject().put("id",id).put("name",name).put("room",room)
                .put("kind",kind).put("connector",connector).put("host",host)
                .put("mac",mac).put("favorite",favorite);
        }
        static Device parse(JSONObject j){
            return new Device(j.optString("id"),j.optString("name"),j.optString("room","Outra divisão"),
                j.optString("kind","other"),j.optString("connector","discovery"),
                j.optString("host"),j.optString("mac"),j.optBoolean("favorite",false));
        }
        boolean controlsTv(){return "lg".equals(connector) && "tv".equals(kind);}
        String description(){
            if(controlsTv())return host.isEmpty()?"LG webOS · falta IP":"LG webOS · emparelhamento por verificar";
            if("discovery".equals(connector))return "Descoberto na rede · sem controlo integrado";
            return "Integração ainda não disponível";
        }
        String icon(){return "tv".equals(kind)?"tv":"camera".equals(kind)?"camera":
            "speaker".equals(kind)?"music":"plug".equals(kind)?"plug":"plus";}
    }
    private static SharedPreferences pref(Context ctx){return ctx.getApplicationContext().getSharedPreferences("lumi_devices_v15",Context.MODE_PRIVATE);}
    private static String clean(String value,int max){
        if(value==null)return "";
        String text=value.trim().replaceAll("[\\p{Cntrl}]"," ");
        return text.length()>max?text.substring(0,max):text;
    }
    static boolean validIp(String value){
        if(value==null||!value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))return false;
        for(String part:value.split("\\."))if(Integer.parseInt(part)>255)return false;
        return true;
    }
    static boolean validMac(String value){return value==null||value.isEmpty()||value.matches("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}");}
    private static List<Device> load(Context ctx){
        List<Device> all=new ArrayList<>();
        try{
            JSONArray list=new JSONArray(pref(ctx).getString("devices","[]"));
            for(int i=0;i<list.length() && i<100;i++){
                JSONObject json=list.optJSONObject(i);
                if(json!=null){Device d=Device.parse(json);if(!d.id.isEmpty())all.add(d);}
            }
        }catch(Exception ignored){}
        return all;
    }
    private static void save(Context ctx,List<Device> list){
        JSONArray arr=new JSONArray();
        try{for(Device d:list)arr.put(d.json());}catch(Exception e){throw new IllegalStateException("Registo inválido",e);}
        if(!pref(ctx).edit().putString("devices",arr.toString()).commit())
            throw new IllegalStateException("Falha a guardar dispositivos");
    }
    /** One-time migration only if the old TV IP exists; never prepopulate placeholders. */
    static synchronized List<Device> all(Context ctx){
        SharedPreferences p=pref(ctx);
        if(!p.getBoolean("legacy_tv_checked",false)){
            String ip=ctx.getSharedPreferences("hub",Context.MODE_PRIVATE).getString("tv_ip","");
            if(validIp(ip)){
                List<Device> list=load(ctx);
                if(list.size()<100){
                    list.add(new Device("lg-tv","TV da Sala","Sala","tv","lg",ip,
                        ctx.getSharedPreferences("hub",Context.MODE_PRIVATE).getString("tv_mac",""),true));
                    save(ctx,list);
                }
            }
            p.edit().putBoolean("legacy_tv_checked",true).commit();
        }
        return load(ctx);
    }
    static synchronized Device byId(Context ctx,String id){
        for(Device d:all(ctx))if(d.id.equals(id))return d;
        return null;
    }
    static synchronized boolean hasLg(Context ctx){
        for(Device d:all(ctx))if(d.controlsTv())return true;
        return false;
    }
    static synchronized Device registerLg(Context ctx,String name,String room,String ip,String mac){
        ip=clean(ip,45);mac=clean(mac,17).toUpperCase(Locale.ROOT);
        if(!validIp(ip)||!validMac(mac))throw new IllegalArgumentException("IP ou MAC inválido");
        List<Device> all=all(ctx);
        Device old=byId(ctx,"lg-tv");
        Device d=new Device("lg-tv",clean(name,60).isEmpty()?"TV LG":clean(name,60),
            clean(room,50),"tv","lg",ip,mac,old==null || old.favorite);
        if(old!=null)all.removeIf(x->x.id.equals("lg-tv"));
        if(all.size()>=100)throw new IllegalStateException("Limite de dispositivos atingido");
        all.add(d);
        save(ctx,all);
        SharedPreferences p=ctx.getSharedPreferences("hub",Context.MODE_PRIVATE);
        SharedPreferences.Editor e=p.edit().putString("tv_ip",ip).putString("tv_mac",mac);
        if(!ip.equals(p.getString("tv_ip","")))e.remove("lg_client_key").remove("lg_cert_pin").remove("lg_last_status");
        e.apply();
        return d;
    }
    static synchronized Device addDiscovered(Context ctx,String name,String host,String kind){
        if(!validIp(host))throw new IllegalArgumentException("Endereço local não reconhecido");
        List<Device> all=all(ctx);
        for(Device d:all)if(d.host.equals(host))return d;
        if(all.size()>=100)throw new IllegalStateException("Limite de dispositivos atingido");
        Device d=new Device(UUID.randomUUID().toString(),clean(name,60),"Outra divisão",
            clean(kind,30),"discovery",host,"",false);
        all.add(d);save(ctx,all);return d;
    }
    static synchronized void update(Context ctx,String id,String name,String room,boolean favorite){
        List<Device> list=all(ctx);boolean found=false;
        for(int i=0;i<list.size();i++){
            Device d=list.get(i);
            if(!d.id.equals(id))continue;
            String n=clean(name,60);if(n.isEmpty())throw new IllegalArgumentException("Nome obrigatório");
            list.set(i,new Device(d.id,n,clean(room,40),d.kind,d.connector,d.host,d.mac,favorite));
            found=true;break;
        }
        if(!found)throw new IllegalArgumentException("Dispositivo inexistente");
        save(ctx,list);
    }
    static synchronized void remove(Context ctx,String id){
        List<Device> list=all(ctx);list.removeIf(d->d.id.equals(id));save(ctx,list);
    }
}
