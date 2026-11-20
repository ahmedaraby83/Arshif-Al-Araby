package com.ahmedaraby.archive;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class ArchiveFileProvider extends ContentProvider {
    public static Uri uri(Context context, File file) {
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName()+".files")
                .appendPath(Uri.encode(file.getName())).build();
    }
    private File resolve(Uri uri) throws FileNotFoundException {
        String name=Uri.decode(uri.getLastPathSegment());
        if(name==null || name.contains("/") || name.contains("..")) throw new FileNotFoundException();
        File f=new File(getContext().getFilesDir(),"docs/"+name);
        if(!f.exists()) throw new FileNotFoundException();
        return f;
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){String n=uri.getLastPathSegment();String e=android.webkit.MimeTypeMap.getFileExtensionFromUrl(n);String t=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);return t==null?"application/octet-stream":t;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(resolve(uri),mode!=null&&mode.contains("w")?ParcelFileDescriptor.MODE_READ_WRITE:ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{File f=resolve(uri);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName(),f.length()});return c;}catch(Exception e){return null;}
    }
    @Override public int delete(Uri u,String s,String[] a){return 0;}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
    @Override public Uri insert(Uri u,ContentValues v){return null;}
}
