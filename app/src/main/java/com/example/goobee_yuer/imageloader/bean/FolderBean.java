package com.example.goobee_yuer.imageloader.bean;

/**
 * Created by Goobee_yuer on 2018/3/6.
 */

public class FolderBean {
    /**
     * 当前文件加的属性
     * dir:文件夹路径
     * firstImgPath：第一张图片路径
     * name：文件夹名字---可以在设置文件夹路径的时候同时获取name
     * count：当前文件夹图片总数量
     */
    private String dir;
    private String firstImgPath;
    private String name;
    private int count;

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
        int lastIndexOf = this.dir.lastIndexOf("/");
        //以最后一个斜杠分开文件夹名字
        this.name = this.dir.substring(lastIndexOf);
    }

    public String getFirstImgPath() {
        return firstImgPath;
    }

    public void setFirstImgPath(String firstImgPath) {
        this.firstImgPath = firstImgPath;
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
