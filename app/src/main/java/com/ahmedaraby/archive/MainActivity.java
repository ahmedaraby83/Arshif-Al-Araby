package com.ahmedaraby.archive;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.app.KeyguardManager;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import android.webkit.*;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.*;

public class MainActivity extends Activity {
    static final int PICK_FILE=10,PICK_BACKUP_FOLDER=11,PICK_RESTORE=12,PICK_SYNC_FOLDER=13,PICK_MULTIPLE_IMAGES=14,CAPTURE_SCAN=15,UNLOCK_APP=16;
    ArchiveDb db; WebView web; Uri pendingUri,backupFolder,cameraUri; ArrayList<Uri> pendingUris=new ArrayList<>(); boolean unlocked=false;
    private final ExecutorService fileJobs=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(0xff10182b);getWindow().setNavigationBarColor(0xff10182b);
        getWindow().getDecorView().setSystemUiVisibility(0);
        db=new ArchiveDb(this);backupFolder=loadBackupFolder();web=new WebView(this);web.setBackgroundColor(0xff10182b);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setDefaultTextEncodingName("UTF-8");
        web.addJavascriptInterface(new Bridge(),"Archive");web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){sendData("","");if(getPreferences(0).getBoolean("syncPending",false))queueFolderSync();}});web.setWebChromeClient(new WebChromeClient());
        setContentView(web);web.loadUrl("file:///android_asset/index.html");
    }

    public class Bridge {
        @JavascriptInterface public void unlockApp(){runOnUiThread(()->requestUnlock());}
        @JavascriptInterface public void addDocument(){runOnUiThread(()->pickFile());}
        @JavascriptInterface public void scanDocument(){runOnUiThread(()->startScanSeries(true));}
        @JavascriptInterface public void addGroup(){runOnUiThread(()->newGroup(null));}
        @JavascriptInterface public void addChild(String parent){runOnUiThread(()->newGroup(parent));}
        @JavascriptInterface public void renameGroup(String name){runOnUiThread(()->renameGroupDialog(name));}
        @JavascriptInterface public void backup(){runOnUiThread(()->chooseBackupFile());}
        @JavascriptInterface public void restore(){runOnUiThread(()->chooseRestoreFile());}
        @JavascriptInterface public void connectFolder(){runOnUiThread(()->chooseSyncFolder());}
        @JavascriptInterface public void refresh(String q,String group){runOnUiThread(()->sendData(q,group));}
        @JavascriptInterface public void open(long id){runOnUiThread(()->findAndOpen(id,false));}
        @JavascriptInterface public void share(long id){runOnUiThread(()->findAndOpen(id,true));}
        @JavascriptInterface public void move(long id){runOnUiThread(()->moveDialog(id));}
        @JavascriptInterface public void delete(long id){runOnUiThread(()->confirmDelete(id));}
        @JavascriptInterface public void exit(){runOnUiThread(()->finish());}
        @JavascriptInterface public String groups(){return groupsJson();}
    }

    String groupsJson(){JSONArray a=new JSONArray();try{for(ArchiveDb.Group g:db.groupTree()){JSONObject o=new JSONObject();o.put("name",g.name);o.put("parent",g.parent==null?JSONObject.NULL:g.parent);a.put(o);}}catch(Exception ignored){}return a.toString();}
    void sendData(String q,String group){
        try{JSONArray a=new JSONArray(),all=new JSONArray();for(ArchiveDb.Item i:db.search("","")){JSONObject o=new JSONObject();o.put("id",i.id);o.put("title",i.title);o.put("group",i.groupName);o.put("notes",i.notes==null?"":i.notes);o.put("tags",i.tags==null?"":i.tags);o.put("mime",i.mime==null?"":i.mime);o.put("date",i.createdAt);all.put(o);}for(ArchiveDb.Item i:db.search(q==null?"":q,group==null?"":group)){JSONObject o=new JSONObject();o.put("id",i.id);o.put("title",i.title);o.put("group",i.groupName);o.put("notes",i.notes==null?"":i.notes);o.put("tags",i.tags==null?"":i.tags);o.put("mime",i.mime==null?"":i.mime);o.put("date",i.createdAt);a.put(o);}String js="window.renderArchive("+JSONObject.quote(groupsJson())+","+a.toString()+","+all.toString()+")";web.evaluateJavascript(js,null);}catch(Exception e){toast("تعذر عرض المستندات");}
    }
    ArchiveDb.Item find(long id){for(ArchiveDb.Item i:db.search("",""))if(i.id==id)return i;return null;}
    void findAndOpen(long id,boolean share){ArchiveDb.Item i=find(id);if(i!=null)openFile(i,share);}
    void moveDialog(long id){
        ArchiveDb.Item item=find(id);if(item==null){toast("الملف غير موجود");return;}
        java.util.List<ArchiveDb.Group> tree=db.groupTree();java.util.List<String> destinations=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();
        for(ArchiveDb.Group g:tree){if(g.name.equals(item.groupName))continue;destinations.add(g.name);String path=g.name,parent=g.parent;java.util.Set<String> seen=new java.util.HashSet<>();seen.add(g.name);while(parent!=null&&!parent.isEmpty()&&seen.add(parent)){path=parent+" / "+path;String next=null;for(ArchiveDb.Group ancestor:tree)if(ancestor.name.equals(parent)){next=ancestor.parent;break;}parent=next;}labels.add(path);}
        if(destinations.isEmpty()){toast("أنشئ مجموعة أخرى لنقل الملف إليها");return;}
        final int[] choice={-1};AlertDialog dialog=new AlertDialog.Builder(this).setTitle("نقل «"+item.title+"»").setSingleChoiceItems(labels.toArray(new String[0]),-1,(d,w)->{choice[0]=w;((AlertDialog)d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}).setPositiveButton("نقل",(d,w)->{if(choice[0]<0)return;String destination=destinations.get(choice[0]);if(db.move(id,destination)){web.evaluateJavascript("reload()",null);toast("تم النقل إلى «"+destination+"»");queueFolderSync();}else toast("تعذر نقل الملف؛ حاول مرة أخرى");}).setNegativeButton("إلغاء",null).create();dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);});dialog.show();
    }
    void confirmDelete(long id){ArchiveDb.Item i=find(id);if(i==null)return;new AlertDialog.Builder(this).setTitle("حذف المستند؟").setMessage("سيتم حذف «"+i.title+"» من الهاتف. ستبقى النسخ القديمة على التخزين السحابي.").setPositiveButton("حذف",(d,w)->{new File(getFilesDir(),"docs/"+i.fileName).delete();db.delete(i.id);sendData("","");toast("تم حذف المستند");queueFolderSync();}).setNegativeButton("إلغاء",null).show();}

    void requestUnlock(){
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(km!=null&&km.isDeviceSecure()){
            Intent i=km.createConfirmDeviceCredentialIntent("فتح أرشيف العربي","استخدم البصمة أو رمز PIN أو كلمة مرور الهاتف");
            if(i!=null){startActivityForResult(i,UNLOCK_APP);return;}
        }
        unlocked=true;
    }
    void pickFile(){String[] options={"ملف PDF أو صورة واحدة","عدة صور","مسح مستند بالكاميرا"};new AlertDialog.Builder(this).setTitle("إضافة مستند").setItems(options,(d,w)->{if(w==0)pickSingle();else if(w==1)pickMultiple();else startScanSeries(true);}).setNegativeButton("إلغاء",null).show();}
    void pickSingle(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/*"});startActivityForResult(i,PICK_FILE);}
    void pickMultiple(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(Intent.createChooser(i,"اختر عدة صور"),PICK_MULTIPLE_IMAGES);}
    void startScanSeries(boolean reset){if(reset)pendingUris.clear();try{File f=new File(getFilesDir(),"docs/scan_"+System.currentTimeMillis()+".jpg");f.getParentFile().mkdirs();cameraUri=ArchiveFileProvider.uri(this,f);Intent i=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,cameraUri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);startActivityForResult(i,CAPTURE_SCAN);}catch(Exception e){toast("تعذر فتح الكاميرا");}}
    void afterScan(){pendingUris.add(cameraUri);new AlertDialog.Builder(this).setTitle("تم تصوير الصفحة").setMessage("عدد الصفحات: "+pendingUris.size()).setPositiveButton("تصوير صفحة أخرى",(d,w)->startScanSeries(false)).setNegativeButton("إنهاء وحفظ",(d,w)->showMultiImageChoice()).setNeutralButton("إلغاء",null).show();}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);
        if(r==UNLOCK_APP){if(result==RESULT_OK){unlocked=true;if(web!=null)web.evaluateJavascript("window.unlockArchive&&window.unlockArchive()",null);}return;}
        if(result!=RESULT_OK)return;
        if(r==CAPTURE_SCAN){afterScan();return;}
        if(data==null)return;
        if(r==PICK_FILE){pendingUri=data.getData();if(pendingUri!=null)showAddDialog();}
        else if(r==PICK_MULTIPLE_IMAGES){pendingUris.clear();ClipData c=data.getClipData();if(c!=null)for(int x=0;x<c.getItemCount();x++)pendingUris.add(c.getItemAt(x).getUri());else if(data.getData()!=null)pendingUris.add(data.getData());if(pendingUris.size()==1){pendingUri=pendingUris.get(0);showAddDialog();}else if(!pendingUris.isEmpty())showMultiImageChoice();}
        else if(r==PICK_BACKUP_FOLDER&&data.getData()!=null)writeBackupFile(data.getData());
        else if(r==PICK_RESTORE&&data.getData()!=null)confirmRestore(data.getData());
        else if(r==PICK_SYNC_FOLDER&&data.getData()!=null){backupFolder=data.getData();try{getContentResolver().takePersistableUriPermission(backupFolder,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(SecurityException e){toast("تعذر حفظ إذن المجلد");return;}getPreferences(0).edit().putString("backup",backupFolder.toString()).putBoolean("syncPending",true).apply();queueFolderSync();}}
    void showMultiImageChoice(){if(pendingUris.isEmpty())return;new AlertDialog.Builder(this).setTitle("حفظ "+pendingUris.size()+" صفحة").setItems(new String[]{"دمج في PDF واحد","حفظ كصور منفصلة"},(d,w)->showMultiDetails(w==0)).setNegativeButton("إلغاء",null).show();}
    void showMultiDetails(boolean pdf){LinearLayout b=new LinearLayout(this);b.setPadding(36,8,36,0);b.setOrientation(LinearLayout.VERTICAL);b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);EditText t=new EditText(this);t.setHint("اسم المستند");t.setText(pdf?"مستند ممسوح":"صور ممسوحة");Spinner g=new Spinner(this);g.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,db.groups()));EditText n=new EditText(this);n.setHint("ملاحظات");EditText k=new EditText(this);k.setHint("كلمات مفتاحية");for(View v:new View[]{t,g,n,k})b.addView(v);new AlertDialog.Builder(this).setTitle(pdf?"إنشاء PDF":"حفظ الصور").setView(b).setPositiveButton("حفظ",(d,w)->fileJobs.execute(()->{try{int count=pdf?savePdf(pendingUris,t.getText().toString(),g.getSelectedItem().toString(),n.getText().toString(),k.getText().toString()):saveImages(pendingUris,t.getText().toString(),g.getSelectedItem().toString(),n.getText().toString(),k.getText().toString());runOnUiThread(()->{sendData("","");toast("تم حفظ "+count+" ملف");queueFolderSync();});}catch(Exception e){runOnUiThread(()->toast("تعذر الحفظ: "+e.getMessage()));}})).setNegativeButton("إلغاء",null).show();}
    int saveImages(List<Uri> us,String title,String group,String notes,String tags)throws Exception{int c=0;for(int x=0;x<us.size();x++){Uri u=us.get(x);String ext=extension(displayName(u));if(ext.isEmpty())ext=".jpg";String name=System.currentTimeMillis()+"_"+x+ext;File f=new File(getFilesDir(),"docs/"+name);f.getParentFile().mkdirs();copy(getContentResolver().openInputStream(u),new FileOutputStream(f));db.add(title+" "+(x+1),group,notes,tags,name,"image/jpeg");c++;}return c;}
    int savePdf(List<Uri> us,String title,String group,String notes,String tags)throws Exception{String name=System.currentTimeMillis()+"_scan.pdf";File f=new File(getFilesDir(),"docs/"+name);PdfDocument p=new PdfDocument();try{int no=1;for(Uri u:us){Bitmap bm;try(InputStream in=getContentResolver().openInputStream(u)){bm=BitmapFactory.decodeStream(in);}if(bm==null)continue;int W=1240,H=1754,m=48;float z=Math.min((W-2f*m)/bm.getWidth(),(H-2f*m)/bm.getHeight());int w=Math.round(bm.getWidth()*z),h=Math.round(bm.getHeight()*z);PdfDocument.Page pg=p.startPage(new PdfDocument.PageInfo.Builder(W,H,no++).create());pg.getCanvas().drawColor(Color.WHITE);pg.getCanvas().drawBitmap(bm,null,new Rect((W-w)/2,(H-h)/2,(W+w)/2,(H+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));p.finishPage(pg);bm.recycle();}try(OutputStream o=new FileOutputStream(f)){p.writeTo(o);}}finally{p.close();}db.add(title,group,notes,tags,name,"application/pdf");return 1;}
    void showAddDialog(){
        LinearLayout box=new LinearLayout(this);box.setPadding(36,8,36,0);box.setOrientation(LinearLayout.VERTICAL);box.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        EditText title=new EditText(this);title.setHint("اسم المستند");title.setText(baseName(displayName(pendingUri)));Spinner groups=new Spinner(this);groups.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,db.groups()));EditText notes=new EditText(this);notes.setHint("ملاحظات");EditText tags=new EditText(this);tags.setHint("كلمات مفتاحية");for(android.view.View v:new android.view.View[]{title,groups,notes,tags})box.addView(v,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("إضافة مستند جديد").setView(box).setPositiveButton("حفظ",(d,w)->{if(title.getText().toString().trim().isEmpty()){toast("اكتب اسم المستند");return;}try{String original=displayName(pendingUri),safe=System.currentTimeMillis()+"_"+original;File dst=new File(getFilesDir(),"docs/"+safe);dst.getParentFile().mkdirs();copy(getContentResolver().openInputStream(pendingUri),new FileOutputStream(dst));db.add(title.getText().toString().trim(),groups.getSelectedItem().toString(),notes.getText().toString(),tags.getText().toString(),safe,getContentResolver().getType(pendingUri));sendData("","");toast("تم حفظ المستند بنجاح");queueFolderSync();}catch(Exception e){toast("تعذر حفظ الملف");}}).setNegativeButton("إلغاء",null).show();
    }
    void newGroup(String parent){EditText x=new EditText(this);x.setHint(parent==null?"اسم المجموعة":"اسم الفرع");x.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);String title=parent==null?"مجموعة جديدة":"فرع داخل «"+parent+"»";new AlertDialog.Builder(this).setTitle(title).setView(x).setPositiveButton("إضافة",(d,w)->{String n=x.getText().toString().trim();if(n.isEmpty())return;if(db.addGroup(n,parent)){sendData("","");toast(parent==null?"تمت إضافة المجموعة":"تمت إضافة الفرع");queueFolderSync();}else toast("هذا الاسم مستخدم بالفعل");}).setNegativeButton("إلغاء",null).show();}
    void renameGroupDialog(String oldName){EditText x=new EditText(this);x.setText(oldName);x.setSelectAllOnFocus(true);x.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);new AlertDialog.Builder(this).setTitle("إعادة التسمية").setView(x).setPositiveButton("حفظ",(d,w)->{String n=x.getText().toString().trim();if(n.isEmpty()||n.equals(oldName))return;if(db.renameGroup(oldName,n)){sendData("","");toast("تم تغيير الاسم");queueFolderSync();}else toast("هذا الاسم مستخدم بالفعل");}).setNegativeButton("إلغاء",null).show();}
    void openFile(ArchiveDb.Item i,boolean share){File f=new File(getFilesDir(),"docs/"+i.fileName);Intent x=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW);Uri u=ArchiveFileProvider.uri(this,f);if(share){x.setType(i.mime);x.putExtra(Intent.EXTRA_STREAM,u);}else x.setDataAndType(u,i.mime);x.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(share?Intent.createChooser(x,"مشاركة المستند"):x);}catch(Exception e){toast("لا يوجد تطبيق مناسب لفتح الملف");}}
    void chooseBackupFile(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"أرشيف-العربي-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmm",java.util.Locale.US).format(new java.util.Date())+".zip");startActivityForResult(i,PICK_BACKUP_FOLDER);}
    void chooseRestoreFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");startActivityForResult(i,PICK_RESTORE);}
    void chooseSyncFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_SYNC_FOLDER);}
    Uri loadBackupFolder(){String s=getPreferences(0).getString("backup",null);return s==null?null:Uri.parse(s);}
    void writeBackupFile(Uri uri){toast("جارٍ إنشاء النسخة الاحتياطية");fileJobs.execute(()->{try{OutputStream out=getContentResolver().openOutputStream(uri,"wt");if(out==null)throw new IOException("لا يمكن الكتابة في المكان المحدد");int count=ArchiveBackup.write(this,db,out);runOnUiThread(()->toast("تم حفظ نسخة كاملة: "+count+" ملف"));}catch(Exception e){try{DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}runOnUiThread(()->toast("فشل النسخ: "+e.getMessage()));}});}
    void confirmRestore(Uri uri){new AlertDialog.Builder(this).setTitle("استعادة الأرشيف؟").setMessage("سيُتحقق من الملفات ثم تُضاف إلى الأرشيف الحالي دون حذف ملفاته. اختر نسخة ZIP أنشأها أرشيف العربي.").setPositiveButton("استعادة",(dialog,which)->{toast("جارٍ فحص النسخة واستعادة الملفات");fileJobs.execute(()->{try{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException("تعذر فتح النسخة");ArchiveBackup.Result result=ArchiveBackup.restore(this,db,in);runOnUiThread(()->{sendData("","");toast("اكتملت الاستعادة: "+result.added+" من "+result.total+" ملف");queueFolderSync();});}catch(Exception e){runOnUiThread(()->toast("تعذرت الاستعادة: "+e.getMessage()));}});}).setNegativeButton("إلغاء",null).show();}
    void queueFolderSync(){getPreferences(0).edit().putBoolean("syncPending",true).apply();if(backupFolder==null)return;Uri folder=backupFolder;fileJobs.execute(()->{try{int count=ArchiveCloudSync.sync(this,db,folder);getPreferences(0).edit().putBoolean("syncPending",false).apply();runOnUiThread(()->toast("تمت مزامنة "+count+" ملف في مجلد الأرشيف"));}catch(Exception error){runOnUiThread(()->toast("المزامنة لم تكتمل: "+error.getMessage()));}});}
    @Override protected void onDestroy(){if(web!=null)web.destroy();fileJobs.shutdown();db.close();super.onDestroy();}
    String displayName(Uri u){String n="document";Cursor c=getContentResolver().query(u,null,null,null,null);if(c!=null){int k=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(c.moveToFirst()&&k>=0)n=c.getString(k);c.close();}return n;}
    String baseName(String s){int p=s.lastIndexOf('.');return p>0?s.substring(0,p):s;}String extension(String s){int p=s.lastIndexOf('.');return p>=0?s.substring(p):"";}
    void copy(InputStream in,OutputStream out)throws IOException{try(InputStream a=in;OutputStream b=out){byte[] buf=new byte[8192];int n;while((n=a.read(buf))>0)b.write(buf,0,n);}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){web.evaluateJavascript("window.handleBack && window.handleBack()",null);}
}
