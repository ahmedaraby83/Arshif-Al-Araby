package com.ahmedaraby.archive;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.security.MessageDigest;
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
    static final int PICK_FILE=10,PICK_BACKUP_FOLDER=11,PICK_RESTORE=12,PICK_SYNC_FOLDER=13,PICK_MULTIPLE_IMAGES=14;
    ArchiveDb db; WebView web; Uri pendingUri,backupFolder; ArrayList<Uri> pendingUris=new ArrayList<>();
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
        @JavascriptInterface public void addDocument(){runOnUiThread(()->pickFile());}
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
        @JavascriptInterface public void move(long id){runOnUiThread(()->moveDialog(id));}
        @JavascriptInterface public void edit(long id){runOnUiThread(()->editDialog(id));}
        @JavascriptInterface public void favorite(long id){runOnUiThread(()->toggleFavorite(id));}
        @JavascriptInterface public void restoreTrash(long id){runOnUiThread(()->{db.trash(id,false);sendData("","");toast("تمت الاستعادة");});}
        @JavascriptInterface public void exit(){runOnUiThread(()->finish());}
        @JavascriptInterface public String groups(){return groupsJson();}
    }

    String groupsJson(){JSONArray a=new JSONArray();try{for(ArchiveDb.Group g:db.groupTree()){JSONObject o=new JSONObject();o.put("name",g.name);o.put("parent",g.parent==null?JSONObject.NULL:g.parent);a.put(o);}}catch(Exception ignored){}return a.toString();}
    void sendData(String q,String group){
        try{JSONArray a=new JSONArray(),all=new JSONArray();for(ArchiveDb.Item i:db.search("","")){JSONObject o=new JSONObject();o.put("id",i.id);o.put("title",i.title);o.put("group",i.groupName);o.put("notes",i.notes==null?"":i.notes);o.put("tags",i.tags==null?"":i.tags);o.put("mime",i.mime==null?"":i.mime);o.put("date",i.createdAt);o.put("favorite",i.favorite);all.put(o);}for(ArchiveDb.Item i:db.search(q==null?"":q,group==null?"":group)){JSONObject o=new JSONObject();o.put("id",i.id);o.put("title",i.title);o.put("group",i.groupName);o.put("notes",i.notes==null?"":i.notes);o.put("tags",i.tags==null?"":i.tags);o.put("mime",i.mime==null?"":i.mime);o.put("date",i.createdAt);o.put("favorite",i.favorite);a.put(o);}String js="window.renderArchive("+JSONObject.quote(groupsJson())+","+a.toString()+","+all.toString()+")";web.evaluateJavascript(js,null);}catch(Exception e){toast("تعذر عرض المستندات");}
    }
    ArchiveDb.Item find(long id){return db.find(id);}
    void findAndOpen(long id,boolean share){ArchiveDb.Item i=find(id);if(i!=null)openFile(i,share);}
    void moveDialog(long id){
        ArchiveDb.Item item=find(id);if(item==null){toast("الملف غير موجود");return;}
        java.util.List<ArchiveDb.Group> tree=db.groupTree();java.util.List<String> destinations=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();
        for(ArchiveDb.Group g:tree){if(g.name.equals(item.groupName))continue;destinations.add(g.name);String path=g.name,parent=g.parent;java.util.Set<String> seen=new java.util.HashSet<>();seen.add(g.name);while(parent!=null&&!parent.isEmpty()&&seen.add(parent)){path=parent+" / "+path;String next=null;for(ArchiveDb.Group ancestor:tree)if(ancestor.name.equals(parent)){next=ancestor.parent;break;}parent=next;}labels.add(path);}
        if(destinations.isEmpty()){toast("أنشئ مجموعة أخرى لنقل الملف إليها");return;}
        final int[] choice={-1};AlertDialog dialog=new AlertDialog.Builder(this).setTitle("نقل «"+item.title+"»").setSingleChoiceItems(labels.toArray(new String[0]),-1,(d,w)->{choice[0]=w;((AlertDialog)d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}).setPositiveButton("نقل",(d,w)->{if(choice[0]<0)return;String destination=destinations.get(choice[0]);if(db.move(id,destination)){web.evaluateJavascript("reload()",null);toast("تم النقل إلى «"+destination+"»");queueFolderSync();}else toast("تعذر نقل الملف؛ حاول مرة أخرى");}).setNegativeButton("إلغاء",null).create();dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);});dialog.show();
    }
    void confirmDelete(long id){ArchiveDb.Item i=find(id);if(i==null)return;new AlertDialog.Builder(this).setTitle("حذف المستند؟").setMessage("سيتم حذف «"+i.title+"» من الهاتف. ستبقى النسخ القديمة على التخزين السحابي.").setPositiveButton("حذف",(d,w)->{db.trash(i.id,true);sendData("","");toast("تم النقل إلى سلة المحذوفات");queueFolderSync();}).setNegativeButton("إلغاء",null).show();}

    void pickFile(){String[] options={"ملف PDF أو صورة واحدة","عدة صور"};new AlertDialog.Builder(this).setTitle("إضافة مستند").setItems(options,(d,w)->{if(w==0)pickSingleDocument();else pickMultipleImages();}).setNegativeButton("إلغاء",null).show();}
    void pickSingleDocument(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/*"});startActivityForResult(i,PICK_FILE);}
    void pickMultipleImages(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(Intent.createChooser(i,"اختر عدة صور"),PICK_MULTIPLE_IMAGES);}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null)return;
        if(r==PICK_FILE){pendingUri=data.getData();if(pendingUri!=null)showAddDialog();else toast("استخدم اختيار ملف للصور الكاملة؛ دعم تصوير متعدد الصفحات في تحديث لاحق");}
        else if(r==PICK_MULTIPLE_IMAGES){pendingUris.clear();ClipData c=data.getClipData();if(c!=null)for(int x=0;x<c.getItemCount();x++)pendingUris.add(c.getItemAt(x).getUri());else if(data.getData()!=null)pendingUris.add(data.getData());if(pendingUris.size()==1){pendingUri=pendingUris.get(0);showAddDialog();}else if(!pendingUris.isEmpty())showMultiImageChoice();}
        else if(r==PICK_BACKUP_FOLDER&&data.getData()!=null)writeBackupFile(data.getData());else if(r==PICK_RESTORE&&data.getData()!=null)confirmRestore(data.getData());else if(r==PICK_SYNC_FOLDER&&data.getData()!=null){backupFolder=data.getData();try{getContentResolver().takePersistableUriPermission(backupFolder,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception e){toast("تعذر حفظ إذن المجلد");return;}getPreferences(0).edit().putString("backup",backupFolder.toString()).putBoolean("syncPending",true).apply();queueFolderSync();}}
    void showMultiImageChoice(){new AlertDialog.Builder(this).setTitle("إضافة "+pendingUris.size()+" صور").setItems(new String[]{"دمج في PDF","حفظ كصور منفصلة"},(d,w)->showMultiDetails(w==0)).setNegativeButton("إلغاء",null).show();}
    void showMultiDetails(boolean pdf){LinearLayout b=formBox();EditText t=input(pdf?"اسم ملف PDF":"اسم الصور","مستند");Spinner g=groupSpinner();EditText n=input("ملاحظات","");EditText k=input("كلمات مفتاحية","");for(View v:new View[]{t,g,n,k})b.addView(v);new AlertDialog.Builder(this).setTitle(pdf?"دمج الصور":"حفظ الصور").setView(b).setPositiveButton("حفظ",(d,w)->fileJobs.execute(()->{try{int c=pdf?savePdf(pendingUris,t.getText().toString(),g.getSelectedItem().toString(),n.getText().toString(),k.getText().toString()):saveImages(pendingUris,t.getText().toString(),g.getSelectedItem().toString(),n.getText().toString(),k.getText().toString());runOnUiThread(()->{sendData("","");toast("تم حفظ "+c+" ملف");queueFolderSync();});}catch(Exception e){runOnUiThread(()->toast("فشل الحفظ: "+e.getMessage()));}})).setNegativeButton("إلغاء",null).show();}
    LinearLayout formBox(){LinearLayout b=new LinearLayout(this);b.setPadding(36,8,36,0);b.setOrientation(LinearLayout.VERTICAL);b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return b;}EditText input(String h,String v){EditText e=new EditText(this);e.setHint(h);e.setText(v);return e;}Spinner groupSpinner(){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,db.groups()));return s;}
    int saveImages(List<Uri> us,String title,String group,String notes,String tags)throws Exception{int c=0;for(int x=0;x<us.size();x++){Uri u=us.get(x);String name=System.currentTimeMillis()+"_"+x+extension(displayName(u));File f=new File(getFilesDir(),"docs/"+name);f.getParentFile().mkdirs();copy(getContentResolver().openInputStream(u),new FileOutputStream(f));String h=sha256(f);if(db.existsHash(h)){f.delete();continue;}db.add(title+" "+(x+1),group,notes,tags,name,getContentResolver().getType(u),h);c++;}return c;}
    int savePdf(List<Uri> us,String title,String group,String notes,String tags)throws Exception{String name=System.currentTimeMillis()+"_merged.pdf";File f=new File(getFilesDir(),"docs/"+name);f.getParentFile().mkdirs();PdfDocument p=new PdfDocument();try{int no=1;for(Uri u:us){Bitmap bm;try(InputStream in=getContentResolver().openInputStream(u)){bm=BitmapFactory.decodeStream(in);}if(bm==null)continue;int W=1240,H=1754,m=48;float z=Math.min((W-2f*m)/bm.getWidth(),(H-2f*m)/bm.getHeight());int w=Math.round(bm.getWidth()*z),h=Math.round(bm.getHeight()*z);PdfDocument.Page pg=p.startPage(new PdfDocument.PageInfo.Builder(W,H,no++).create());pg.getCanvas().drawColor(Color.WHITE);pg.getCanvas().drawBitmap(bm,null,new Rect((W-w)/2,(H-h)/2,(W+w)/2,(H+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));p.finishPage(pg);bm.recycle();}try(OutputStream o=new FileOutputStream(f)){p.writeTo(o);}}finally{p.close();}String h=sha256(f);if(db.existsHash(h)){f.delete();throw new IOException("هذا الملف موجود بالفعل");}db.add(title,group,notes,tags,name,"application/pdf",h);return 1;}
    void showAddDialog(){
        LinearLayout box=new LinearLayout(this);box.setPadding(36,8,36,0);box.setOrientation(LinearLayout.VERTICAL);box.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        EditText title=new EditText(this);title.setHint("اسم المستند");title.setText(baseName(displayName(pendingUri)));Spinner groups=new Spinner(this);groups.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,db.groups()));EditText notes=new EditText(this);notes.setHint("ملاحظات");EditText tags=new EditText(this);tags.setHint("كلمات مفتاحية");for(android.view.View v:new android.view.View[]{title,groups,notes,tags})box.addView(v,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("إضافة مستند جديد").setView(box).setPositiveButton("حفظ",(d,w)->{if(title.getText().toString().trim().isEmpty()){toast("اكتب اسم المستند");return;}try{String original=displayName(pendingUri),safe=System.currentTimeMillis()+"_"+original;File dst=new File(getFilesDir(),"docs/"+safe);dst.getParentFile().mkdirs();copy(getContentResolver().openInputStream(pendingUri),new FileOutputStream(dst));db.add(title.getText().toString().trim(),groups.getSelectedItem().toString(),notes.getText().toString(),tags.getText().toString(),safe,getContentResolver().getType(pendingUri));sendData("","");toast("تم حفظ المستند بنجاح");queueFolderSync();}catch(Exception e){toast("تعذر حفظ الملف");}}).setNegativeButton("إلغاء",null).show();
    }
    void newGroup(String parent){EditText x=new EditText(this);x.setHint(parent==null?"اسم المجموعة":"اسم الفرع");x.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);String title=parent==null?"مجموعة جديدة":"فرع داخل «"+parent+"»";new AlertDialog.Builder(this).setTitle(title).setView(x).setPositiveButton("إضافة",(d,w)->{String n=x.getText().toString().trim();if(n.isEmpty())return;if(db.addGroup(n,parent)){sendData("","");toast(parent==null?"تمت إضافة المجموعة":"تمت إضافة الفرع");queueFolderSync();}else toast("هذا الاسم مستخدم بالفعل");}).setNegativeButton("إلغاء",null).show();}
    void renameGroupDialog(String oldName){EditText x=new EditText(this);x.setText(oldName);x.setSelectAllOnFocus(true);x.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);new AlertDialog.Builder(this).setTitle("إعادة التسمية").setView(x).setPositiveButton("حفظ",(d,w)->{String n=x.getText().toString().trim();if(n.isEmpty()||n.equals(oldName))return;if(db.renameGroup(oldName,n)){sendData("","");toast("تم تغيير الاسم");queueFolderSync();}else toast("هذا الاسم مستخدم بالفعل");}).setNegativeButton("إلغاء",null).show();}
    void moveDialog(long id){ArchiveDb.Item i=find(id);if(i==null)return;String[] gs=db.groups().toArray(new String[0]);new AlertDialog.Builder(this).setTitle("نقل المستند").setItems(gs,(d,w)->{db.move(id,gs[w]);sendData("","");toast("تم النقل");}).show();}
    void editDialog(long id){ArchiveDb.Item i=find(id);if(i==null)return;LinearLayout b=formBox();EditText t=input("الاسم",i.title),n=input("الملاحظات",i.notes),k=input("الكلمات المفتاحية",i.tags);Spinner g=groupSpinner();int pos=db.groups().indexOf(i.groupName);if(pos>=0)g.setSelection(pos);for(View v:new View[]{t,g,n,k})b.addView(v);new AlertDialog.Builder(this).setTitle("تعديل المستند").setView(b).setPositiveButton("حفظ",(d,w)->{db.edit(id,t.getText().toString().trim(),g.getSelectedItem().toString(),n.getText().toString(),k.getText().toString());sendData("","");toast("تم الحفظ");}).setNegativeButton("إلغاء",null).show();}
    void toggleFavorite(long id){ArchiveDb.Item i=find(id);if(i==null)return;db.favorite(id,!i.favorite);sendData("","");toast(i.favorite?"أزيل من المفضلة":"أضيف إلى المفضلة");}
    String sha256(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)d.update(b,0,n);}StringBuilder x=new StringBuilder();for(byte b:d.digest())x.append(String.format(Locale.US,"%02x",b));return x.toString();}
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
