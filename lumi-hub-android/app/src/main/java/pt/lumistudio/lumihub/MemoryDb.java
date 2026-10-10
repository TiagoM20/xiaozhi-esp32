package pt.lumistudio.lumihub;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import java.util.ArrayList;
import java.util.List;

public final class MemoryDb extends SQLiteOpenHelper {
    public static final class Item {
        public final long id;
        public final String title;
        public final String detail;
        public final long createdAt;
        Item(long id, String title, String detail, long createdAt) {
            this.id = id;
            this.title = title;
            this.detail = detail;
            this.createdAt = createdAt;
        }
    }

    public MemoryDb(Context context) {
        super(context, "lumi_memories.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "title TEXT NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future versions will use additive migrations, without deleting memories.
    }

    public long save(String title, String detail) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("Título vazio");
        ContentValues values = new ContentValues();
        values.put("title", title.trim());
        values.put("detail", detail == null ? "" : detail.trim());
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("memories", null, values);
    }

    /** Insere apenas se o mesmo titulo e conteudo ainda nao existirem. */
    public boolean saveIfMissing(String title, String detail) {
        if (title == null || title.trim().isEmpty()) return false;
        if (detail == null) detail = "";
        String t = title.trim();
        String d = detail.trim();
        try (Cursor cursor = getReadableDatabase().rawQuery(
            "SELECT 1 FROM memories WHERE title=? AND detail=? LIMIT 1",
            new String[]{t, d})) {
            if (cursor.moveToFirst()) return false;
        }
        save(t, d);
        return true;
    }

    public List<Item> all() {
        ArrayList<Item> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, title, detail, created_at FROM memories ORDER BY created_at DESC, id DESC",
                null)) {
            while (cursor.moveToNext()) {
                result.add(new Item(cursor.getLong(0), cursor.getString(1),
                    cursor.getString(2), cursor.getLong(3)));
            }
        }
        return result;
    }

    public void remove(long id) {
        getWritableDatabase().delete("memories", "id = ?", new String[]{Long.toString(id)});
    }

    public int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memories", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
