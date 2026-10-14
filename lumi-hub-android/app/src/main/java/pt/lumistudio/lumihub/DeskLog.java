package pt.lumistudio.lumihub;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Registo local privado. Apenas eventos e transcrições, nunca áudio ou credenciais. */
final class DeskLog {
    private static final String FILE="lumi_desk_events.log";
    private static final long MAX_SIZE=500_000;
    private DeskLog(){}
    private static File file(Context c){return new File(c.getApplicationContext().getFilesDir(),FILE);}
    static synchronized void event(Context c,String kind,String detail){
        write(c,kind,detail,false);
    }
    static synchronized void conversation(Context c,String kind,String detail){
        if(!c.getSharedPreferences("hub",Context.MODE_PRIVATE)
            .getBoolean("desk_log_conversations",true))return;
        write(c,kind,detail,true);
    }
    private static void write(Context c,String kind,String value,boolean privateMessage){
        try{
            String safe=(value==null?"":value).replace('\r',' ').replace('\n',' ');
            safe=safe.replaceAll("(?i)Bearer\\s+[^ ]+","Bearer [OCULTO]")
                .replaceAll("(?i)sk-[a-z0-9_-]{10,}","[CHAVE OCULTA]")
                .replaceAll("(?i)(token|pair)=[^& ]+","$1=[OCULTO]");
            if(safe.length()>1800)safe=safe.substring(0,1800)+"…";
            String stamp=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new Date());
            File f=file(c);
            if(f.exists()&&f.length()>MAX_SIZE){
                String old=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);
                int cut=Math.max(0,old.length()-210_000);
                int next=old.indexOf('\n',cut);
                if(next>=0)cut=next+1;
                try(FileOutputStream out=new FileOutputStream(f,false)){
                    out.write(old.substring(cut).getBytes(StandardCharsets.UTF_8));
                }
            }
            String line=stamp+" | "+kind+" | "+safe+"\n";
            try(FileOutputStream out=new FileOutputStream(f,true)){
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }catch(Exception ignored){}
    }
    static synchronized String contents(Context c){
        try{
            File f=file(c);return f.exists()?
                new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):
                "Ainda não existem eventos registados.\n";
        }catch(Exception e){return "Falha ao ler o histórico.\n";}
    }
    static synchronized void clear(Context c){
        File f=file(c);
        if(f.exists())f.delete();
    }
}
