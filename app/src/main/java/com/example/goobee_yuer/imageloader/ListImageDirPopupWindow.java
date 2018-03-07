package com.example.goobee_yuer.imageloader;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.example.goobee_yuer.imageloader.bean.FolderBean;
import com.example.goobee_yuer.imageloader.util.ImageLoader;

import java.util.List;

/**
 * Created by Goobee_yuer on 2018/3/6.
 */

public class ListImageDirPopupWindow extends PopupWindow {
    private int mWidth;
    private int mHeight;
    private View mConvertView;
    private ListView mListView;
    private List<FolderBean> mDatas;
    //ListView的接口
    public interface OnDirSelectedListener {
        void onSeleted(FolderBean folderBean);
    }
    public OnDirSelectedListener mListener;

    public void setOnDirSelectedListener(OnDirSelectedListener listener) {
        mListener = listener;
    }

    public ListImageDirPopupWindow(Context context, List<FolderBean> datas) {
        calWidthAndHeigth(context);
        mConvertView = LayoutInflater.from(context).inflate(R.layout.popup_main,null);
        mDatas = datas;
        //设置布局
        setContentView(mConvertView);
        setWidth(mWidth);
        setHeight(mHeight);
        setFocusable(true);
        setTouchable(true);
        setOutsideTouchable(true); //设置外边可以触
        setBackgroundDrawable(new BitmapDrawable());
        //点击外部让它消失
        setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE){
                    dismiss();
                    return true;
                }
                return false;
            }
        });

        initViews(context);
        initEvent();
    }

    private void initViews(Context context) {
        //加载ListView
        mListView = (ListView) mConvertView.findViewById(R.id.id_list_dir);
        mListView.setAdapter(new ListDirAdapter(context,mDatas));


    }

    private void initEvent() {
        //设置listview的触摸事件
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mListener != null){
                    mListener.onSeleted(mDatas.get(position));
                }
            }
        });
    }

    /**
     * 计算popupWindow的宽度和高度
     * @param context
     */
    private void calWidthAndHeigth(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(outMetrics);

        //设置屏幕的宽度高度
        mWidth = outMetrics.widthPixels;
        mHeight = (int) (outMetrics.heightPixels * 0.7);

    }

    private class ListDirAdapter extends ArrayAdapter<FolderBean>{
        private LayoutInflater mInflater;

        public ListDirAdapter(@NonNull Context context, @NonNull List<FolderBean> objects) {
            super(context, 0, objects);
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null){
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.item_popup_main,parent,false);
                holder.mImageView = (ImageView) convertView.findViewById(R.id.id_dir_item_image);
                holder.mDirName = (TextView) convertView.findViewById(R.id.id_dir_item_name);
                holder.mDirCount = (TextView) convertView.findViewById(R.id.id_dir_item_count);
                convertView.setTag(holder); //设置配置，下次调用可以直接使用
            }else {
                holder = (ViewHolder) convertView.getTag();
            }
            FolderBean bean = getItem(position);
            //重置一下 避免混乱
            holder.mImageView.setImageResource(R.drawable.pictures_no);
            ImageLoader.getInstance().loadImage(bean.getFirstImgPath(),holder.mImageView);

            holder.mDirName.setText(bean.getName());
            holder.mDirCount.setText(bean.getCount() + "");

            return convertView;
        }

        private class ViewHolder{
            ImageView mImageView;
            TextView mDirName;
            TextView mDirCount;
        }
    }
}
