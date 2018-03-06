package com.example.goobee_yuer.imageloader;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.goobee_yuer.imageloader.bean.FolderBean;
import com.example.goobee_yuer.imageloader.util.ImageLoader;

import java.io.File;
import java.io.FilenameFilter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    // 要申请的权限
    private String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public static final int DATA_LOAD = 0x110;
    private GridView mGridView;
    private List<String> mImgs;
    private ImageAdapter mImageAdapter;

    private RelativeLayout mBottomLy;
    private TextView mDirName;
    private TextView mDirCount;

    private File mCurrentDir;
    private int mMaxCount;

    private List<FolderBean> mFolderBeans = new ArrayList<FolderBean>();

    private ProgressDialog mProgressDialog;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case DATA_LOAD:
                    //关闭进度显示
                    mProgressDialog.dismiss();
                    //绑定数据到View中
                    data2View();
                    break;
                default:
                    break;
            }
        }
    };

    private void data2View() {
        if (mCurrentDir == null){
            Toast.makeText(this,"未扫描到任何图片！",Toast.LENGTH_SHORT).show();
            return;
        }
        //将数组包装成list返回
        mImgs = Arrays.asList(mCurrentDir.list());
        mImageAdapter = new ImageAdapter(this,mImgs,mCurrentDir.getAbsolutePath());
        mGridView.setAdapter(mImageAdapter);
        mDirCount.setText(mMaxCount + "");
        mDirName.setText(mCurrentDir.getName());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        //自检权限是否申请
        if (ContextCompat.checkSelfPermission(MainActivity.this,permissions[0]) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{permissions[0]},1);
        }else {
            initDatas();
        }

        initEvent();
    }

    private void initEvent() {

    }

    /**
     * 利用ContentProvider扫描手机所有图片
     */
    private void initDatas() {
        //先检查sd卡是否可用
        if (!Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)){
            Toast.makeText(this,"当前存储卡不可用！",Toast.LENGTH_SHORT).show();
            return;
        }
        mProgressDialog = ProgressDialog.show(this,null,"正在加载...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                Uri mImgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver cr = MainActivity.this.getContentResolver();
                // 只查询jpeg和png的图片
                Cursor cursor = cr.query(mImgUri, null,
                        MediaStore.Images.Media.MIME_TYPE + "=? or "
                                + MediaStore.Images.Media.MIME_TYPE + "=?",
                        new String[] { "image/jpeg", "image/png" },
                        MediaStore.Images.Media.DATE_MODIFIED);
                //避免重复扫描
                Set<String> mDirPaths = new HashSet<String>();
                while (cursor.moveToNext()) {
                    String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                    File parentFile = new File(path).getParentFile();
                    if(parentFile == null){
                        continue;
                    }
                    String dirPath = parentFile.getAbsolutePath();
                    FolderBean folderBean = null;
                    //判断是否已经扫描
                    if (mDirPaths.contains(dirPath)){
                        continue;
                    }else {
                        mDirPaths.add(dirPath);
                        folderBean = new FolderBean();
                        folderBean.setDir(dirPath);
                        folderBean.setFirstImgPath(path);
                    }
                    if (parentFile.list() == null) continue;
                    //过滤器过滤不是图片的文件
                    int picSize = parentFile.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            if (name.endsWith(".jpg")
                                    || name.endsWith(".jpeg")
                                    || name.endsWith(".png"))
                                return true;
                            return false;
                        }
                    }).length;
                    folderBean.setCount(picSize);
                    mFolderBeans.add(folderBean); //添加
                    if (picSize > mMaxCount){//mMaxCount在此处更新
                        mMaxCount = picSize;
                        mCurrentDir = parentFile;
                    }
                }
                cursor.close();
                //通知Handler扫描图片完成
                mHandler.sendEmptyMessage(DATA_LOAD);
            }
        }).start();
    }

    /**
     * 初始化所有控件
     */
    private void initView() {
        mGridView = (GridView) findViewById(R.id.id_gridView);
        mBottomLy = (RelativeLayout) findViewById(R.id.id_bottom_ly);
        mDirName = (TextView) findViewById(R.id.id_dir_name);
        mDirCount = (TextView) findViewById(R.id.id_dir_count);
    }
    private class ImageAdapter extends BaseAdapter{
        private String mDirPath;
        private List<String> mImgPaths;
        private LayoutInflater mInflater;
        public ImageAdapter(Context context, List<String> mDatas, String dirPath){
            this.mDirPath = dirPath;
            this.mImgPaths = mDatas;
            mInflater = LayoutInflater.from(context);
        }
        @Override
        public int getCount() {
            return mImgPaths.size();
        }

        @Override
        public Object getItem(int position) {
            return mImgPaths.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder = null;
            if (convertView == null){
                convertView = mInflater.inflate(R.layout.item_gridview,parent,false);
                viewHolder = new ViewHolder();
                viewHolder.mImg = (ImageView) convertView.findViewById(R.id.id_item_image);
                viewHolder.mSelect = (ImageButton) convertView.findViewById(R.id.id_item_select);
                convertView.setTag(viewHolder);
            }else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            //设置为无图片重置状态
            viewHolder.mImg.setImageResource(R.drawable.pictures_no);
            viewHolder.mSelect.setImageResource(R.drawable.picture_unselected);

            ImageLoader.getInstance(3, ImageLoader.Type.LIFO).loadImage(mDirPath + "/" + mImgPaths.get(position),viewHolder.mImg);
//            ImageLoader.getInstance().loadImage(mDirPath + "/" + mImgPaths.get(position),viewHolder.mImg);

            return convertView;
        }
        private class ViewHolder{
            ImageView mImg;
            ImageButton mSelect;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    initDatas();
                }else {
                    Toast.makeText(MainActivity.this,"You denied the permission", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }
}
