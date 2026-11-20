package com.ahmedaraby.archive;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

final class ArchiveBackup {
    private static final long MAX_FILE = 250L * 1024 * 1024;
    private static final long MAX_TOTAL = 2L * 1024 * 1024 * 1024;
    private static final int MAX_MANIFEST = 8 * 1024 * 1024;
    static final class Result { final int total, added; Result(int t,int a){total=t;added=a;} }
    private static final class Document {
        ArchiveDb.Item item; String entry, checksum; long size; File staged;
    }

    static int write(Context context,ArchiveDb db,OutputStream output) throws Exception {
        List<ArchiveDb.Item> items=db.search("","");
        if(items.size()>5000)throw new IOException("عدد الملفات أكبر من الحد المدعوم");
        JSONObject manifest=new JSONObject();manifest.put("format","arshif-al-araby");manifest.put("version",1);
        JSONArray groupData=new JSONArray(),fileData=new JSONArray();
        for(ArchiveDb.Group g:db.groupTree())groupData.put(new JSONObject().put("name",g.name).put("parent",g.parent==null?JSONObject.NULL:g.parent));
        manifest.put("groups",groupData);
        List<Document> documents=new ArrayList<>();long total=0;
        for(ArchiveDb.Item i:items){
            File source=new File(new File(context.getFilesDir(),"docs"),i.fileName);
            if(!source.isFile()||source.length()>MAX_FILE||source.length()+total>MAX_TOTAL)throw new IOException("ملف مفقود أو كبير جدًا: "+i.title);
            if(i.backupKey==null||!i.backupKey.matches("[0-9a-fA-F-]{36}"))throw new IOException("معرّف ملف غير صالح");
            Document d=new Document();d.item=i;d.entry="docs/"+i.backupKey.replace("-","")+".bin";d.size=source.length();d.checksum=checksum(source);documents.add(d);total+=d.size;
            fileData.put(new JSONObject().put("key",i.backupKey).put("entry",d.entry).put("sha256",d.checksum).put("size",d.size)
                .put("title",i.title).put("group",i.groupName).put("notes",i.notes==null?"":i.notes).put("tags",i.tags==null?"":i.tags)
                .put("mime",i.mime==null?"application/octet-stream":i.mime).put("createdAt",i.createdAt));
        }
        manifest.put("items",fileData);byte[] index=manifest.toString().getBytes("UTF-8");
        if(index.length>MAX_MANIFEST)throw new IOException("فهرس النسخة كبير جدًا");
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(index);zip.closeEntry();
            byte[] buffer=new byte[32768];
            for(Document d:documents){
                zip.putNextEntry(new ZipEntry(d.entry));
                try(InputStream in=new FileInputStream(new File(new File(context.getFilesDir(),"docs"),d.item.fileName))){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}
                zip.closeEntry();
            }
        }
        return documents.size();
    }

    static Result restore(Context context,ArchiveDb db,InputStream input) throws Exception {
        File staging=new File(context.getCacheDir(),"archive-restore-"+UUID.randomUUID());
        if(!staging.mkdirs())throw new IOException("تعذر تجهيز مساحة الاستعادة");
        List<File> installed=new ArrayList<>();
        try{
            List<ArchiveDb.Group> groups=new ArrayList<>();LinkedHashMap<String,Document> documents=new LinkedHashMap<>();
            Set<String> keys=new HashSet<>();long total=0;boolean manifestSeen=false;
            try(ZipInputStream zip=new ZipInputStream(input)){
                ZipEntry entry;byte[] buffer=new byte[32768];
                while((entry=zip.getNextEntry())!=null){
                    String name=entry.getName();
                    if(!manifestSeen){
                        if(!"manifest.json".equals(name))throw new IOException("ملف النسخة غير صالح");
                        ByteArrayOutputStream out=new ByteArrayOutputStream();int n;
                        while((n=zip.read(buffer))!=-1){if(out.size()+n>MAX_MANIFEST)throw new IOException("فهرس النسخة كبير جدًا");out.write(buffer,0,n);}
                        JSONObject manifest=new JSONObject(out.toString("UTF-8"));
                        if(!"arshif-al-araby".equals(manifest.optString("format"))||manifest.optInt("version")!=1)throw new IOException("صيغة النسخة غير مدعومة");
                        JSONArray groupArray=manifest.getJSONArray("groups"),itemArray=manifest.getJSONArray("items");
                        if(groupArray.length()>1000||itemArray.length()>5000)throw new IOException("نسخة كبيرة جدًا");
                        Map<String,String> parents=new HashMap<>();
                        for(int k=0;k<groupArray.length();k++){
                            JSONObject g=groupArray.getJSONObject(k);String group=g.getString("name"),parent=g.isNull("parent")?null:g.getString("parent");
                            if(!valid(group,160)||parent!=null&&!valid(parent,160)||parents.containsKey(group))throw new IOException("بيانات المجموعات غير صالحة");
                            parents.put(group,parent);groups.add(new ArchiveDb.Group(group,parent));
                        }
                        for(String group:parents.keySet()){
                            Set<String> seen=new HashSet<>();String cursor=group;
                            while(cursor!=null){if(!seen.add(cursor)||!parents.containsKey(cursor))throw new IOException("ترتيب المجموعات غير صالح");cursor=parents.get(cursor);}
                        }
                        for(int k=0;k<itemArray.length();k++){
                            JSONObject j=itemArray.getJSONObject(k);Document d=new Document();d.item=new ArchiveDb.Item();
                            d.item.backupKey=j.getString("key");d.entry=j.getString("entry");d.checksum=j.getString("sha256");d.size=j.getLong("size");
                            d.item.title=j.getString("title");d.item.groupName=j.getString("group");d.item.notes=j.optString("notes","");
                            d.item.tags=j.optString("tags","");d.item.mime=j.optString("mime","application/octet-stream");d.item.createdAt=j.getString("createdAt");
                            if(!d.item.backupKey.matches("[0-9a-fA-F-]{36}")||!d.entry.equals("docs/"+d.item.backupKey.replace("-","")+".bin")||
                                !d.checksum.matches("[0-9a-f]{64}")||d.size<0||d.size>MAX_FILE||d.size+total>MAX_TOTAL||
                                !valid(d.item.title,300)||!parents.containsKey(d.item.groupName)||!valid(d.item.createdAt,60)||
                                d.item.notes.length()>10000||d.item.tags.length()>2000||!valid(d.item.mime,160)||
                                !keys.add(d.item.backupKey)||documents.put(d.entry,d)!=null)throw new IOException("فهرس الملفات غير صالح");
                            total+=d.size;
                        }
                        manifestSeen=true;
                    }else{
                        Document d=documents.get(name);
                        if(d==null||d.staged!=null||entry.isDirectory())throw new IOException("محتوى النسخة غير متوقع");
                        d.staged=new File(staging,d.item.backupKey+".part");MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
                        try(OutputStream out=new FileOutputStream(d.staged)){int n;while((n=zip.read(buffer))!=-1){size+=n;if(size>d.size)throw new IOException("حجم الملف غير صحيح");digest.update(buffer,0,n);out.write(buffer,0,n);}}
                        if(size!=d.size||!hex(digest.digest()).equals(d.checksum))throw new IOException("النسخة تالفة أو غير مكتملة");
                    }
                    zip.closeEntry();
                }
            }
            if(!manifestSeen)throw new IOException("لم يُعثر على فهرس النسخة");
            Set<String> existing=new HashSet<>();for(ArchiveDb.Item i:db.search("",""))existing.add(i.backupKey);
            List<ArchiveDb.Item> importing=new ArrayList<>();File destination=new File(context.getFilesDir(),"docs");
            if(!destination.exists()&&!destination.mkdirs())throw new IOException("تعذر إنشاء مجلد الملفات");
            for(Document d:documents.values()){
                if(d.staged==null)throw new IOException("النسخة غير مكتملة");
                if(existing.contains(d.item.backupKey))continue;
                File local=new File(destination,"restored_"+UUID.randomUUID()+extension(d.item.mime));
                try(InputStream in=new FileInputStream(d.staged);OutputStream out=new FileOutputStream(local)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
                installed.add(local);d.item.fileName=local.getName();importing.add(d.item);
            }
            int added=db.restore(groups,importing);return new Result(documents.size(),added);
        }catch(Exception error){for(File f:installed)f.delete();throw error;}
        finally{File[] staged=staging.listFiles();if(staged!=null)for(File f:staged)f.delete();staging.delete();}
    }

    private static boolean valid(String s,int max){return s!=null&&!s.trim().isEmpty()&&s.length()<=max;}
    private static String extension(String mime){if("application/pdf".equals(mime))return ".pdf";if("image/png".equals(mime))return ".png";if("image/webp".equals(mime))return ".webp";return mime.startsWith("image/")?".jpg":".bin";}
    private static String checksum(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}return hex(digest.digest());}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder(bytes.length*2);for(byte b:bytes)out.append(String.format(Locale.US,"%02x",b&255));return out.toString();}
}
