package com.ahmedaraby.archive;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class ArchiveDb extends SQLiteOpenHelper {
    public static class Item {
        long id; String title, groupName, notes, tags, fileName, mime, createdAt, backupKey;
        boolean favorite;
    }
    public static class Group { String name,parent; Group(String n,String p){name=n;parent=p;} }

    ArchiveDb(Context c) { super(c, "archive.db", null, 5); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups(name TEXT PRIMARY KEY,parent_name TEXT)");
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,group_name TEXT NOT NULL,notes TEXT,tags TEXT,file_name TEXT NOT NULL,mime TEXT,created_at TEXT NOT NULL,backup_key TEXT UNIQUE,favorite INTEGER NOT NULL DEFAULT 0,deleted INTEGER NOT NULL DEFAULT 0,sha256 TEXT)");
        for (String name : new String[]{"العمل","القرارات الوزارية","الشهادات","ملفات شخصية"}) {
            ContentValues v = new ContentValues(); v.put("name", name); db.insert("groups", null, v);
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2)db.execSQL("ALTER TABLE groups ADD COLUMN parent_name TEXT");
        if(oldVersion<3){
            db.execSQL("ALTER TABLE items ADD COLUMN backup_key TEXT");
            Cursor c=db.rawQuery("SELECT id FROM items WHERE backup_key IS NULL",null);
            try{while(c.moveToNext()){ContentValues v=new ContentValues();v.put("backup_key",java.util.UUID.randomUUID().toString());db.update("items",v,"id=?",new String[]{String.valueOf(c.getLong(0))});}}finally{c.close();}
            db.execSQL("CREATE UNIQUE INDEX items_backup_key ON items(backup_key)");
        }
        if(oldVersion<4){db.execSQL("ALTER TABLE items ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE items ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");}
        if(oldVersion<5)db.execSQL("ALTER TABLE items ADD COLUMN sha256 TEXT");
    }

    long add(String title, String group, String notes, String tags, String fileName, String mime) {
        ContentValues v = new ContentValues();
        v.put("title",title); v.put("group_name",group); v.put("notes",notes); v.put("tags",tags);
        v.put("file_name",fileName); v.put("mime",mime); v.put("created_at",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));v.put("backup_key",UUID.randomUUID().toString());
        return getWritableDatabase().insert("items",null,v);
    }
    boolean addGroup(String name,String parent) { ContentValues v=new ContentValues(); v.put("name",name);if(parent!=null&&!parent.isEmpty())v.put("parent_name",parent);return getWritableDatabase().insertWithOnConflict("groups",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1; }
    void addGroup(String name){addGroup(name,null);}
    boolean renameGroup(String oldName,String newName){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{Cursor c=d.rawQuery("SELECT 1 FROM groups WHERE name=?",new String[]{newName});boolean exists=c.moveToFirst();c.close();if(exists)return false;ContentValues g=new ContentValues();g.put("name",newName);d.update("groups",g,"name=?",new String[]{oldName});ContentValues child=new ContentValues();child.put("parent_name",newName);d.update("groups",child,"parent_name=?",new String[]{oldName});ContentValues item=new ContentValues();item.put("group_name",newName);d.update("items",item,"group_name=?",new String[]{oldName});d.setTransactionSuccessful();return true;}finally{d.endTransaction();}}
    List<String> groups() {
        List<String> out=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT name FROM groups ORDER BY name",null);
        while(c.moveToNext()) out.add(c.getString(0)); c.close(); return out;
    }
    List<Group> groupTree(){List<Group> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT name,parent_name FROM groups ORDER BY parent_name IS NOT NULL,parent_name,name",null);while(c.moveToNext())out.add(new Group(c.getString(0),c.isNull(1)?null:c.getString(1)));c.close();return out;}
    List<String> descendants(String root){List<String> all=new ArrayList<>();all.add(root);boolean changed=true;while(changed){changed=false;for(Group g:groupTree())if(g.parent!=null&&all.contains(g.parent)&&!all.contains(g.name)){all.add(g.name);changed=true;}}return all;}
    List<Item> search(String q, String group) {
        List<Item> out=new ArrayList<>(); String like="%"+q+"%";
        String sql="SELECT id,title,group_name,notes,tags,file_name,mime,created_at,backup_key,favorite FROM items WHERE (title LIKE ? OR notes LIKE ? OR tags LIKE ? OR file_name LIKE ?)";
        List<String> args=new ArrayList<>(Arrays.asList(like,like,like,like));
        if(group!=null && !group.isEmpty()){List<String> names=descendants(group);sql+=" AND group_name IN (";for(int x=0;x<names.size();x++){if(x>0)sql+=",";sql+="?";args.add(names.get(x));}sql+=")";} sql+=" ORDER BY id DESC";
        Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]));
        while(c.moveToNext()) { Item i=new Item(); i.id=c.getLong(0);i.title=c.getString(1);i.groupName=c.getString(2);i.notes=c.getString(3);i.tags=c.getString(4);i.fileName=c.getString(5);i.mime=c.getString(6);i.createdAt=c.getString(7);i.backupKey=c.getString(8);i.favorite=c.getInt(9)!=0;out.add(i); }
        c.close(); return out;
    }
    boolean toggleFavorite(long id){
        SQLiteDatabase d=getWritableDatabase();
        Cursor c=d.rawQuery("SELECT favorite FROM items WHERE id=?",new String[]{String.valueOf(id)});
        int current=0;
        try{if(c.moveToFirst())current=c.getInt(0);}finally{c.close();}
        ContentValues v=new ContentValues();
        v.put("favorite",current==0?1:0);
        return d.update("items",v,"id=?",new String[]{String.valueOf(id)})==1;
    }
    boolean edit(long id,String title,String notes,String tags,String group){
        ContentValues v=new ContentValues();
        v.put("title",title);v.put("notes",notes);v.put("tags",tags);v.put("group_name",group);
        return getWritableDatabase().update("items",v,"id=?",new String[]{String.valueOf(id)})==1;
    }
    boolean move(long id,String destination){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try{Cursor c=d.rawQuery("SELECT 1 FROM groups WHERE name=?",new String[]{destination});boolean exists;try{exists=c.moveToFirst();}finally{c.close();}if(!exists)return false;
            ContentValues v=new ContentValues();v.put("group_name",destination);boolean moved=d.update("items",v,"id=?",new String[]{String.valueOf(id)})==1;d.setTransactionSuccessful();return moved;
        }finally{d.endTransaction();}
    }
    void delete(long id){getWritableDatabase().delete("items","id=?",new String[]{String.valueOf(id)});}
    int restore(List<Group> groupsToRestore,List<Item> itemsToRestore){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();int added=0;
        try{
            for(Group g:groupsToRestore){ContentValues v=new ContentValues();v.put("name",g.name);v.put("parent_name",g.parent);d.insertWithOnConflict("groups",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
            for(Item i:itemsToRestore){ContentValues v=new ContentValues();v.put("title",i.title);v.put("group_name",i.groupName);v.put("notes",i.notes);v.put("tags",i.tags);v.put("file_name",i.fileName);v.put("mime",i.mime);v.put("created_at",i.createdAt);v.put("backup_key",i.backupKey);if(d.insertWithOnConflict("items",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)added++;}
            d.setTransactionSuccessful();return added;
        }finally{d.endTransaction();}
    }
}
