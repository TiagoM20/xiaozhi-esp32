package pt.lumistudio.lumihub;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Histórias e personagens são ficheiros de trabalho LOCAIS, nunca sincronizados
 * automaticamente com o agente XiaoZhi. Mantém separada a MemoryDb existente.
 */
final class LumiLibraryDb extends SQLiteOpenHelper {
    static final class Entry {
        final long id;
        final String kind;
        final String title;
        final String content;
        Entry(long id,String kind,String title,String content){
            this.id=id;this.kind=kind;this.title=title;this.content=content;
        }
    }
    LumiLibraryDb(Context context){
        super(context,"lumi_library.db",null,1);
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE library (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "kind TEXT NOT NULL CHECK(kind IN ('Historia','Personagem'))," +
            "title TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        // Migrações futuras apenas aditivas.
    }
    long save(String kind,String title,String content){
        if(!"Historia".equals(kind)&&!"Personagem".equals(kind))
            throw new IllegalArgumentException("Tipo desconhecido.");
        if(title==null||title.trim().isEmpty())
            throw new IllegalArgumentException("Título obrigatório.");
        ContentValues row=new ContentValues();
        row.put("kind",kind);
        row.put("title",title.trim());
        row.put("content",content==null?"":content.trim());
        row.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("library",null,row);
    }
    List<Entry> all(String kind){
        ArrayList<Entry> list=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().query("library",
                new String[]{"id","kind","title","content"},
                "kind=?",new String[]{kind},null,null,"created_at DESC, id DESC")){
            while(cursor.moveToNext())list.add(new Entry(cursor.getLong(0),
                cursor.getString(1),cursor.getString(2),cursor.getString(3)));
        }
        return list;
    }
    void remove(long id){
        getWritableDatabase().delete("library","id=?",new String[]{Long.toString(id)});
    }
}