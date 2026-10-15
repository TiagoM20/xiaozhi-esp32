package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Protótipo BYOK: Azure Speech STT/TTS (Raquel), OpenAI Chat Completions.
 * Apenas chamadas HTTPS, sem registo/armazenamento do áudio em disco.
 */
final class LumiCloudVoice {
    private LumiCloudVoice(){}
    static String region(SharedPreferences p){
        String value=p.getString("lumi_azure_region","westeurope").toLowerCase().trim();
        if(!value.matches("[a-z0-9]{3,25}"))throw new IllegalArgumentException("Região Azure inválida");
        return value;
    }
    private static byte[] post(String endpoint,String contentType,byte[] data,
                               String authHeader,String authValue,String outputFormat)
                               throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        if(!(c instanceof HttpsURLConnection))throw new IOException("HTTPS obrigatório");
        c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(12000);
        c.setReadTimeout(45000);c.setRequestProperty("Content-Type",contentType);
        c.setRequestProperty("User-Agent","LumiHubVoice/12.2");
        c.setRequestProperty(authHeader,authValue);
        if(outputFormat!=null)c.setRequestProperty("X-Microsoft-OutputFormat",outputFormat);
        c.setRequestProperty("Accept","*/*");
        c.setFixedLengthStreamingMode(data.length);
        try{
            try(OutputStream o=c.getOutputStream()){o.write(data);}
            int code=c.getResponseCode();
            if(code!=200)throw new IOException("Serviço devolveu HTTP "+code);
            try(InputStream in=c.getInputStream();
                ByteArrayOutputStream result=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))>=0){
                    if(n>0){if(result.size()+n>6_000_000)throw new IOException("Áudio demasiado longo");
                        result.write(buf,0,n);}
                }
                return result.toByteArray();
            }
        }finally{c.disconnect();}
    }
    static String transcribe(Context ctx,SharedPreferences prefs,byte[] rawPcm)throws Exception{
        String key=LumiSecrets.get(ctx,"azure");
        if(key.isEmpty())throw new IOException("Configura a chave Azure Speech");
        String endpoint="https://"+region(prefs)+
            ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"+
            "?language=pt-PT&format=simple";
        byte[] response=post(endpoint,"audio/wav; codecs=audio/pcm; samplerate=16000",
            LumiOpus.wav(rawPcm),"Ocp-Apim-Subscription-Key",key,null);
        JSONObject result=new JSONObject(new String(response,StandardCharsets.UTF_8));
        if(!"Success".equals(result.optString("RecognitionStatus")))
            throw new IOException("A fala não foi reconhecida");
        return result.optString("DisplayText","").trim();
    }
    static String respond(Context ctx,SharedPreferences p,String question) throws Exception {
        String key=LumiSecrets.get(ctx,"openai");
        if(key.isEmpty())throw new IOException("Configura a chave de IA");
        JSONArray messages=new JSONArray()
            .put(new JSONObject().put("role","system").put("content",
                "És a LUMI, assistente familiar em Portugal. Responde sempre " +
                "em português europeu natural (pt-PT), de forma calorosa mas prática. " +
                "Evita expressões brasileiras e respostas longas. Não inventes memórias " +
                "da família. Esta é uma conversa de voz; responde em uma a três frases."))
            .put(new JSONObject().put("role","user").put("content",question));
        String model=p.getString("lumi_openai_model","gpt-4.1-mini").trim();
        if(!model.matches("[a-zA-Z0-9_.-]{3,64}"))throw new IOException("Modelo inválido");
        JSONObject payload=new JSONObject().put("model",model)
            .put("store",false).put("max_completion_tokens",240)
            .put("messages",messages);
        byte[] response=post("https://api.openai.com/v1/chat/completions",
            "application/json",payload.toString().getBytes(StandardCharsets.UTF_8),
            "Authorization","Bearer "+key,null);
        JSONObject root=new JSONObject(new String(response,StandardCharsets.UTF_8));
        return root.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content","").trim();
    }
    static byte[] speak(Context ctx,SharedPreferences p,String text)throws Exception{
        String key=LumiSecrets.get(ctx,"azure");
        if(key.isEmpty())throw new IOException("Configura Azure Speech");
        String safe=text.length()>600?text.substring(0,600):text;
        safe=safe.replace("&","&amp;").replace("<","&lt;")
            .replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
        String ssml="<speak version=\"1.0\" xml:lang=\"pt-PT\"><voice name=\""+
            "pt-PT-RaquelNeural\">"+safe+"</voice></speak>";
        byte[] response=post("https://"+region(p)+
            ".tts.speech.microsoft.com/cognitiveservices/v1",
            "application/ssml+xml; charset=utf-8",
            ssml.getBytes(StandardCharsets.UTF_8),
            "Ocp-Apim-Subscription-Key",key,"raw-24khz-16bit-mono-pcm");
        if(response.length<2400)throw new IOException("Raquel não devolveu áudio suficiente");
        return response;
    }
}