package com.example.goobee_yuer.imageloader.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static android.content.ContentValues.TAG;


/**
 * 图片加载类
 * 使用单例模式
 * Created by Goobee_yuer on 2018/3/6.
 */

public class ImageLoader {
    private static ImageLoader mInstance;
    /**
     * 图像缓存的核心对象
     * @String 图片路径
     * @Bitmap 图片
     */
    private LruCache<String,Bitmap> mLruCache;
    /**
     * 线程池
     * 默认线程数
     */
    private ExecutorService mThreadPool;
    private static final int DEFAULT_THREAD_COUNT = 1;
    /**
     * 队列的调用方式
     */
    private Type mType = Type.LIFO;
    public enum Type{
        FIFO,LIFO;
    }
    /**
     * 任务队列
     * 链表
     */
    private LinkedList<Runnable> mTaskQueue;
    /**
     * 后台轮询线程
     */
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;
    /**
     * UI线程中的Handler
     * 获取图片成功后通过handler发送消息图片回调显示bitmap
     */
    private Handler mUIHandler;

    private Semaphore mSemaphorePoolThreadHandler = new Semaphore(0);

    private Semaphore mSemaphoreThreadPool;

    private ImageLoader(int threadCount,Type type){
        init(threadCount,type);
    }

    /**
     * 初始化操作
     * @param threadCount 线程数量
     * @param type          调用模式
     */
    private void init(int threadCount, Type type) {
        //后台轮询线程
        mPoolThread = new Thread(){
            @Override
            public void run() {
                Looper.prepare();
                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        //线程池取出一个任务进行执行
                        mThreadPool.execute(getTask());
                        try {
                            //需要请求---在完成一个后会释放
                            mSemaphoreThreadPool.acquire();
                        } catch (Exception e) {
                        }
                    }
                };
                //释放一个信号量
                mSemaphorePoolThreadHandler.release();
                Looper.loop(); //不断循环
            };
        };
        mPoolThread.start();
        //获取我们应用的最大可以用内存 我们实际用作缓存的大小支取八分之一
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory/8;
        mLruCache = new LruCache<String,Bitmap>(cacheMemory){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes()*value.getHeight();
            }
        };
        //创建线程池
        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mTaskQueue = new LinkedList<Runnable>();
        mType = type;
        mSemaphoreThreadPool = new Semaphore(threadCount);
    }

    //DCL--单例模式
    public static ImageLoader getInstance(){
        if (mInstance == null){  //没做同步处理，提高效率
            synchronized (ImageLoader.class){
                if(mInstance == null){
                    mInstance = new ImageLoader(DEFAULT_THREAD_COUNT,Type.LIFO);
                }
            }
        }
        return mInstance;
    }
    public static ImageLoader getInstance(int threadCount, Type type){
        if (mInstance == null){  //没做同步处理，提高效率
            synchronized (ImageLoader.class){
                if(mInstance == null){
                    mInstance = new ImageLoader(threadCount,type);
                }
            }
        }
        return mInstance;
    }

    /**
     * 根据path为imageview设置图片
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView){
        imageView.setTag(path); //绑定
        if (mUIHandler == null){
            mUIHandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    //获取得到图片，为imageview回调设置图片
                    ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
                    Bitmap bm = holder.bitmap;
                    ImageView imageview = holder.imageView;
                    String path = holder.path;
                    //根据之前设定的tag与现在实际的path进行比较，因为view的复用，可能会造成错乱。
                    if (imageview.getTag().toString().equals(path)){
                        imageview.setImageBitmap(bm);
                    }
                }
            };
        }
        //根据path在LRU缓存中获取图片
        Bitmap bm = getBitmapFromLruCache(path);
        if (bm != null){
            refreshBitmap(bm, imageView, path);
        }else {
            addTasks(new Runnable() {
                @Override
                public void run() {
                    //加载图片
                    //图片压缩1、获取图片需要显示的大小
                    ImageSize imageSize = getImageViewSize(imageView);
                    //2、压缩图片
                    Bitmap bm = decodeSampledBitmapFromPath(path,imageSize.width,imageSize.height);
                    //3、图片加入到缓存
                    addBitmapToLruCache(path,bm);
                    //4、回调显示
                    refreshBitmap(bm, imageView, path);
                    //完成一个是放一个信号
                    mSemaphoreThreadPool.release();
                }
            });
        }



    }

    /**
     * 发送信息，包括图片等通知handler回调更新
     * @param bm
     * @param imageView
     * @param path
     */
    private void refreshBitmap(Bitmap bm, ImageView imageView, String path) {
        Message message = Message.obtain();
        ImgBeanHolder holder = new ImgBeanHolder();
        holder.bitmap = bm;
        holder.imageView = imageView;
        holder.path = path;
        message.obj = holder;
        mUIHandler.sendMessage(message);
    }

    /**
     * 将图片加入LruCache缓存
     * @param path
     * @param bm
     */
    private void addBitmapToLruCache(String path, Bitmap bm) {
        if(getBitmapFromLruCache(path) == null){
            if (bm != null){
                mLruCache.put(path,bm);
            }
        }
    }

    /**
     * 根据图片需要显示的宽和高进行压缩。
     * @param path
     * @param width
     * @param height
     * @return
     */
    private Bitmap decodeSampledBitmapFromPath(String path, int width, int height) {
        //获取图片的实际大小，并不加载到内存中
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;  //只解析图片不加载到内存
        BitmapFactory.decodeFile(path,options);  //已经可以获取到实际的宽和高了

        options.inSampleSize = caculateInSampleSize(options,width,height);
        //使用获取到的inSampleSize再次解析图片
        options.inJustDecodeBounds = false;  //需要将图片加载到内存
        Bitmap bitmap = BitmapFactory.decodeFile(path,options);
        return bitmap;
    }

    /**
     * 根据需求的宽和高以及实际的宽和高计算samplesize  具体情况可以具体分析
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private int caculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int width = options.outWidth;
        int height = options.outHeight;

        int inSampleSize = 1;
        if (width > reqWidth || height > reqHeight){
            int widthRadio = Math.round(width*1.0f/reqWidth);
            int heightRadio = Math.round(height*1.0f/reqHeight);
            inSampleSize = Math.max(widthRadio,heightRadio);
        }
        return inSampleSize;
    }

    /**
     * 根据imageview获取适当的压缩的宽和高
     * @param imageView
     * @return
     */
    private ImageSize getImageViewSize(ImageView imageView) {

        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
        ImageSize imageSize = new ImageSize();
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        int width = imageView.getWidth();  //获取imageview的实际宽度
        if (width <= 0){
            width = lp.width;
        }
        if (width <= 0){
//            width = imageView.getMaxWidth();
            width = getImageViewFieldValue(imageView,"mMaxWidth");   //检查最大值
        }
        if (width <= 0){
            width = displayMetrics.widthPixels;
        }

        int height = imageView.getHeight();  //获取imageview的实际高度
        if (height <= 0){
            height = lp.height;
        }
        if (height <= 0){
//            height = imageView.getMaxHeight();
            height = getImageViewFieldValue(imageView,"mMaxHeight"); //检查最大值
        }
        if (height <= 0){
            height = displayMetrics.heightPixels;
        }
        imageSize.width = width;
        imageSize.height = height;
        return imageSize;
    }

    /**
     * 通过反射获取imageview的某个属性值
     * @param object
     * @param fieldName
     * @return
     */
    private static int getImageViewFieldValue(Object object,String fieldName){
        int value = 0;
        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);

            int fieldValue = field.getInt(object);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE){
                value = fieldValue;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    /**
     * 增加task到链表并发出通知，通知后台线程去请求线程池开启任务
     * @param runnable
     */
    private synchronized void addTasks(Runnable runnable){
        mTaskQueue.add(runnable);
        //发送通知
        try {
            if (mPoolThreadHandler == null){
                mSemaphorePoolThreadHandler.acquire();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    /**
     * 从队列中取出一个方法
     * @return
     */
    private Runnable getTask(){
        if (mType == Type.FIFO){
            return mTaskQueue.removeFirst();
        }else if (mType == Type.LIFO){
            return mTaskQueue.removeLast();
        }
        return null;
    }
    /**
     *根据key获取缓存图片
     * @param key
     * @return
     */
    private Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);
    }

    /**
     *保证path--bitmap--imageview一一对应
     */
    private class ImgBeanHolder{
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }
    private class ImageSize{
        int width;
        int height;
    }
}
