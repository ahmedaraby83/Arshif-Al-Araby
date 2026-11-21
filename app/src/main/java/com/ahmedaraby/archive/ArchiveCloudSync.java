package com.ahmedaraby.archive;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

final class ArchiveCloudSync {
    private static final String FOLDER="vnd.android.document/directory";
    static int sync(Context context,ArchiveDb db,Uri tree) throws Exception {
        Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        Uri archive=directory(context,tree,root,"أرشيف العربي");
        List<ArchiveDb.Group> groups=db.groupTree();Map<String,ArchiveDb.Group> byName=new HashMap<>();
        Map<String,Uri> paths=new HashMap<>();for(ArchiveDb.Group g:groups)byName.put(g.name,g);
        for(ArchiveDb.Group g:groups)resolve(context,tree,archive,g.name,byName,paths,new HashSet<>());
        int count=0;
        for(ArchiveDb.Item item:db.search("","")){
            File source=new File(new File(context.getFilesDir(),"docs"),item.fileName);
            if(!source.isFile())throw new IOException("ملف مفقود: "+item.title);
            Uri parent=paths.get(item.groupName);if(parent==null)throw new IOException("مجموعة مفقودة: "+item.groupName);
            String suffix=extension(item.mime);String title=item.title.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]","_").trim();
            if(title.length()>85)title=title.substring(0,85);
            String fileName=(title.isEmpty()?"مستند":title)+"-"+item.backupKey.substring(0,8)+suffix;
            if(find(context,tree,parent,fileName,null)!=null)continue;
            Uri target=DocumentsContract.createDocument(context.getContentResolver(),parent,item.mime==null?"application/octet-stream":item.mime,fileName);
            if(target==null)throw new IOException("تعذر إنشاء ملف في المجلد المحدد");
            try(InputStream in=new FileInputStream(source);OutputStream out=context.getContentResolver().openOutputStream(target,"wt")){
                if(out==null)throw new IOException("تعذر الكتابة إلى التخزين السحابي");copy(in,out);
            }catch(Exception error){try{DocumentsContract.deleteDocument(context.getContentResolver(),target);}catch(Exception ignored){}throw error;}
            count++;
        }
        // Keep two complete recovery snapshots. Publish a new one before removing older ones.
        String snapshot="أرشيف-العربي-استعادة-"+System.currentTimeMillis()+".zip";
        Uri target=DocumentsContract.createDocument(context.getContentResolver(),archive,"application/zip",snapshot);
        if(target==null)throw new IOException("تعذر إنشاء ملف الاستعادة");
        try(OutputStream out=context.getContentResolver().openOutputStream(target,"wt")){
            if(out==null)throw new IOException("تعذر حفظ ملف الاستعادة");ArchiveBackup.write(context,db,out);
        }catch(Exception error){try{DocumentsContract.deleteDocument(context.getContentResolver(),target);}catch(Exception ignored){}throw error;}
        cleanupSnapshots(context,tree,archive);
        return count;
    }
    private static Uri resolve(Context c,Uri tree,Uri root,String name,Map<String,ArchiveDb.Group> groups,Map<String,Uri> paths,Set<String> seen)throws Exception{
        if(paths.containsKey(name))return paths.get(name);
        if(!seen.add(name))throw new IOException("تسلسل المجموعات غير صالح");
        ArchiveDb.Group g=groups.get(name);if(g==null)throw new IOException("مجموعة مفقودة");
        Uri parent=g.parent==null?root:resolve(c,tree,root,g.parent,groups,paths,seen);
        Uri uri=directory(c,tree,parent,name);paths.put(name,uri);seen.remove(name);return uri;
    }
    private static Uri directory(Context c,Uri tree,Uri parent,String name)throws Exception{
        Uri existing=find(c,tree,parent,name,FOLDER);if(existing!=null)return existing;
        Uri created=DocumentsContract.createDocument(c.getContentResolver(),parent,FOLDER,name);
        if(created==null)throw new IOException("لا يدعم هذا المكان إنشاء المجلدات");return created;
    }
    private static Uri find(Context c,Uri tree,Uri parent,String name,String mime)throws Exception{
        String id=DocumentsContract.getDocumentId(parent);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        try(Cursor cursor=c.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(cursor==null)throw new IOException("لا يمكن قراءة مجلد التخزين");
            while(cursor.moveToNext())if(name.equals(cursor.getString(1))&&(mime==null||mime.equals(cursor.getString(2))))
                return DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0));
        }
        return null;
    }
    private static void cleanupSnapshots(Context c,Uri tree,Uri parent){
        List<String> names=new ArrayList<>();Map<String,Uri> documents=new HashMap<>();
        try(Cursor cursor=c.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent)),new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor==null)return;
            while(cursor.moveToNext()){
                String name=cursor.getString(1);if(!name.startsWith("أرشيف-العربي-استعادة-")||!name.endsWith(".zip"))continue;
                names.add(name);documents.put(name,DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)));
            }
        }catch(Exception ignored){return;}
        Collections.sort(names,Collections.reverseOrder());
        for(int i=2;i<names.size();i++)try{DocumentsContract.deleteDocument(c.getContentResolver(),documents.get(names.get(i)));}catch(Exception ignored){}
    }
    private static String extension(String mime){if("application/pdf".equals(mime))return ".pdf";if("image/png".equals(mime))return ".png";if("image/webp".equals(mime))return ".webp";return mime!=null&&mime.startsWith("image/")?".jpg":".bin";}
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
}
