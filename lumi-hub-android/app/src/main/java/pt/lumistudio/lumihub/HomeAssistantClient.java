package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ligação autenticada à REST API oficial HA; nunca usa a ponte de voz. */
final class HomeAssistantClient {
    static final String PREFS="lumi_home_assistant";
    static final String SECRET="home_assistant_token";
    static final String[] DOMAINS={"light","switch","media_player","vacuum","fan"};
    private static final int MAX_RESPONSE=1024*1024;

    static final class Entity {
        final String id,name,state,domain;
        final boolean available;
        Entity(String id,String name,String state){
            this.id=id;this.name=name;this.state=state;
            this.domain=id.substring(0,id.indexOf('.'));
            this.available=!"unknown".equals(state)&&!"unavailable".equals(state);
        }
        String stateLabel(){
            if(!available)return "Indisponível";
            switch(state){
                case "on": return "Ligado";
                case "off": return "Desligado";
                case "playing": return "Em reprodução";
                case "paused": return "Em pausa";
                case "cleaning": return "A limpar";
                case "docked": return "Na base";
                case "idle": return "Em repouso";
                default:return state;
            }
        }
    }

    static String base(Context ctx){
        return ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString("ha_base_url","");
    }
    static boolean isConfigured(Context ctx){
        return !base(ctx).isEmpty()&&!LumiSecrets.get(ctx,SECRET).isEmpty();
    }

    static String validateBase(String raw){
        String value=raw==null?"":raw.trim();
        if(value.isEmpty())throw new IllegalArgumentException("Indica o endereço do Home Assistant.");
        try{
            URL url=new URL(value);
            String host=url.getHost().toLowerCase(Locale.ROOT);
            String protocol=url.getProtocol().toLowerCase(Locale.ROOT);
            if(host.isEmpty()||url.getUserInfo()!=null||url.getRef()!=null||url.getQuery()!=null
                || !(url.getPath().isEmpty()||"/".equals(url.getPath())))
                throw new IllegalArgumentException("Indica apenas o endereço base, sem caminhos ou credenciais.");
            if(!"https".equals(protocol)&&!"http".equals(protocol))
                throw new IllegalArgumentException("Utiliza http:// ou https://.");
            if("http".equals(protocol)&&!isPrivateHost(host))
                throw new IllegalArgumentException("HTTP sem encriptação só é permitido em endereços locais. Usa HTTPS fora de casa.");
            if(url.getPort()>65535||url.getPort()==0)
                throw new IllegalArgumentException("Porta inválida.");
            return protocol+"://"+host+(url.getPort()>0?":"+url.getPort():"");
        }catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("Endereço do Home Assistant inválido.");}
    }

    static boolean isPrivateHost(String host){
        if("localhost".equals(host)||host.endsWith(".local")||"127.0.0.1".equals(host))return true;
        String[] parts=host.split("\\.");
        if(parts.length!=4)return false;
        int[] octets=new int[4];
        try{
            for(int i=0;i<4;i++){
                if(!parts[i].matches("[0-9]{1,3}"))return false;
                octets[i]=Integer.parseInt(parts[i]);
                if(octets[i]>255)return false;
            }
        }catch(Exception e){return false;}
        return octets[0]==10||(octets[0]==172&&octets[1]>=16&&octets[1]<=31)
            ||(octets[0]==192&&octets[1]==168)
            ||(octets[0]==169&&octets[1]==254)
            ||(octets[0]==127);
    }

    static void store(Context context,String baseUrl,String token) throws Exception {
        String normalized=validateBase(baseUrl);
        String current=LumiSecrets.get(context,SECRET);
        String actual=token==null||token.trim().isEmpty()?current:token.trim();
        if(actual.isEmpty())throw new IllegalArgumentException("Indica o token de acesso do Home Assistant.");
        LumiSecrets.put(context,SECRET,actual);
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString("ha_base_url",normalized).apply();
    }
    static void clear(Context ctx) throws Exception {
        LumiSecrets.put(ctx,SECRET,"");
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String request(String base,String token,String method,String endpoint,
                                  JSONObject body) throws Exception {
        String urlBase=validateBase(base);
        if(token==null||token.isEmpty())throw new IllegalArgumentException("Token não configurado.");
        HttpURLConnection conn=(HttpURLConnection)new URL(urlBase+endpoint).openConnection();
        conn.setInstanceFollowRedirects(false); // impedir redirecionamento do bearer token
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8500);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization","Bearer "+token);
        conn.setRequestProperty("Accept","application/json");
        try {
            if(body!=null){
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json");
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            }
            int status=conn.getResponseCode();
            if(status==401||status==403)throw new IllegalStateException(
                "Autorização recusada. Verifica o token e as permissões no Home Assistant.");
            if(status>=300&&status<400)throw new IllegalStateException(
                "Redirecionamento não permitido. Utiliza diretamente o endereço do Home Assistant.");
            if(status<200||status>=300)throw new IllegalStateException(
                "Home Assistant devolveu HTTP "+status+".");
            try(InputStream in=conn.getInputStream()){
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                byte[] tmp=new byte[4096];
                int n;
                while((n=in.read(tmp))!=-1){
                    if(buffer.size()+n>MAX_RESPONSE)
                        throw new IllegalStateException("Resposta demasiado grande para a app.");
                    buffer.write(tmp,0,n);
                }
                return new String(buffer.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{conn.disconnect();}
    }

    static void test(String base,String token) throws Exception {
        JSONObject ping=new JSONObject(request(base,token,"GET","/api/",null));
        if(!ping.optString("message","").toLowerCase(Locale.ROOT).contains("api running"))
            throw new IllegalStateException("O servidor não confirmou a API Home Assistant.");
    }

    static List<Entity> states(String base,String token) throws Exception {
        JSONArray array=new JSONArray(request(base,token,"GET","/api/states",null));
        List<Entity> result=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            JSONObject item=array.optJSONObject(i);
            if(item==null)continue;
            String id=item.optString("entity_id","");
            if(!allowedEntity(id))continue;
            String state=item.optString("state","unknown");
            String display=item.optJSONObject("attributes")==null?"":item.optJSONObject("attributes")
                .optString("friendly_name","");
            if(display.trim().isEmpty())display=id;
            result.add(new Entity(id,display,state));
        }
        return result;
    }

    static boolean allowedEntity(String id){
        if(id==null||!id.matches("^[a-z_]+\\.[a-z0-9_]+$"))return false;
        for(String domain:DOMAINS)if(id.startsWith(domain+"."))return true;
        return false;
    }

    static void command(String base,String token,Entity e,String service) throws Exception {
        if(e==null||!e.available||!allowedEntity(e.id))
            throw new IllegalArgumentException("O equipamento não está disponível.");
        final boolean allowed;
        switch(e.domain){
            case "light":
            case "switch":
            case "fan": allowed="turn_on".equals(service)||"turn_off".equals(service);break;
            case "media_player": allowed="turn_on".equals(service)||"turn_off".equals(service)
                ||"media_play_pause".equals(service);break;
            case "vacuum": allowed="start".equals(service)||"return_to_base".equals(service)
                ||"stop".equals(service);break;
            default: allowed=false;
        }
        if(!allowed)throw new IllegalArgumentException("Ação não permitida para este equipamento.");
        request(base,token,"POST","/api/services/"+e.domain+"/"+service,
            new JSONObject().put("entity_id",e.id));
    }

    /** Divisões atribuídas manualmente; nunca inferir a localização do aparelho. */
    static final String[] ROOMS={"Sala","Cozinha","Quartos","Outra divisão"};
    static String room(Context ctx,String entityId){
        if(!allowedEntity(entityId))return "";
        return ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString("room_"+entityId,"");
    }
    static void setRoom(Context ctx,String entityId,String room){
        if(!allowedEntity(entityId))throw new IllegalArgumentException("Entidade inválida.");
        boolean valid=room==null||room.isEmpty();
        for(String r:ROOMS)if(r.equals(room))valid=true;
        if(!valid)throw new IllegalArgumentException("Divisão inválida.");
        SharedPreferences.Editor edit=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit();
        if(room==null||room.isEmpty())edit.remove("room_"+entityId);
        else edit.putString("room_"+entityId,room);
        edit.apply();
    }

    static String token(Context ctx){return LumiSecrets.get(ctx,SECRET);}
    private HomeAssistantClient(){}
}
