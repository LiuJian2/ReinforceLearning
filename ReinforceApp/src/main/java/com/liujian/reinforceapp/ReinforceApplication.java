package com.liujian.reinforceapp;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.encrypt.Encrypt;
import com.encrypt.tools.EncryptUtils;
import com.encrypt.tools.RefInvoke;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.DexClassLoader;

/**
 * Created by liujian03 on 2017/12/5.
 */

public class ReinforceApplication extends Application {

    public static final String TAG = "TAG";

    private static final String APPKEY = "APPLICATION_CLASS_NAME";

    private String mSourceApkFilePath;

    private String mOdexPath;

    private String mLibPath;

    /**
     * Application最早被回调的方法
     * 在这里做读取dex释放apk文件的操作
     */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            releaseSourceApk();

            hookClassLoader();
        } catch (Exception e) {
            Log.i(TAG, "excption:" + Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onCreate() {
        Log.d(TAG, "Reinforce Application onCreate");
        // 如果源应用配置有Appliction对象，则替换为源应用Applicaiton，以便不影响源程序逻辑
        String appClassName = null;
        try {
            ApplicationInfo info = this.getPackageManager()
                    .getApplicationInfo(this.getPackageName(),
                            PackageManager.GET_META_DATA);
            Bundle bundle = info.metaData;
            if (bundle != null && bundle.containsKey(APPKEY)) {
                appClassName = bundle.getString(APPKEY);// className 是配置在ReinforceApp 的 AndroidManifest.xml文件中的。
            } else {
                Log.e(TAG, "have no application class name");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "error:" + Log.getStackTraceString(e));
        }
        //有值的话调用该Applicaiton

        Object currentActivityThread = RefInvoke.invokeStaticMethod(
                "android.app.ActivityThread", "currentActivityThread",
                new Class[]{}, new Object[]{});
        Object mBoundApplication = RefInvoke.getFieldOjbect(
                "android.app.ActivityThread", currentActivityThread,
                "mBoundApplication");
        Object loadedApkInfo = RefInvoke.getFieldOjbect(
                "android.app.ActivityThread$AppBindData",
                mBoundApplication, "info");

        hookLoadedApkResDir(loadedApkInfo);

        if (!TextUtils.isEmpty(appClassName)) {
            hookApplicationAndCallOnCreate(appClassName, currentActivityThread, mBoundApplication, loadedApkInfo);
        }
    }

    /**
     * 替换LoadedApk中的ClassLoader对象
     */
    private void hookClassLoader() {
        // 配置动态加载环境
        Object currentActivityThread = RefInvoke.invokeStaticMethod(
                "android.app.ActivityThread", "currentActivityThread",
                new Class[]{}, new Object[]{});// 获取主线程对象 http://blog.csdn.net/myarrow/article/details/14223493
        String packageName = this.getPackageName();//当前apk的包名
        // 获取包名对应的LoadedApk的弱引用
        ArrayMap mPackages = (ArrayMap) RefInvoke.getFieldOjbect(
                "android.app.ActivityThread", currentActivityThread,
                "mPackages");
        WeakReference wr = (WeakReference) mPackages.get(packageName);
        ClassLoader mParentClassLoader = (ClassLoader) RefInvoke.getFieldOjbect("android.app.LoadedApk", wr.get(), "mClassLoader");
        //创建被加壳apk的DexClassLoader对象 加载apk内的类和本地代码（c/c++代码）
        DexClassLoader dexLoader = new DexClassLoader(mSourceApkFilePath, mOdexPath,
                mLibPath, mParentClassLoader);
        // 把当前进程的DexClassLoader 设置成了被加壳apk的DexClassLoader
        RefInvoke.setFieldOjbect("android.app.LoadedApk", "mClassLoader", wr.get(), dexLoader);
        Log.d(TAG, "Current classloader:" + dexLoader);
        try {
            Class clazz = dexLoader.loadClass("com.liujian.reinforcelearning.MainActivity");
            Log.i(TAG, "Load class com.liujian.reinforcelearning.MainActivity success, class:" + clazz);
        } catch (Exception e) {
            Log.i("demo", "load activity with excption: " + Log.getStackTraceString(e));
        }
    }

    /**
     * 释放Dex中的SourceApk文件到指定的路径
     *
     * @throws IOException
     */
    private void releaseSourceApk() throws IOException {
        //创建两个文件夹payload_odex，payload_lib 私有的，可写的文件目录
        File odexDir = this.getDir("payload_odex", MODE_PRIVATE);
        File libsDir = this.getDir("payload_lib", MODE_PRIVATE);
        mOdexPath = odexDir.getAbsolutePath();
        mLibPath = libsDir.getAbsolutePath();
        mSourceApkFilePath = odexDir.getAbsolutePath() + "/payload.apk";
        File sourceApkFile = new File(mSourceApkFilePath);
        if (!sourceApkFile.exists()) {
            sourceApkFile.createNewFile();//在payload_odex文件夹内，创建payload.apk
            // 读取程序classes.dex文件
            byte[] dexdata = this.readDexFileFromApk();
            // 分离出解壳后的apk文件已用于动态加载
            this.splitPayLoadFromDex(dexdata);
        }
        Log.d(TAG, "Source apk path: " + mSourceApkFilePath + ", size: " + sourceApkFile.length());
    }

    /**
     * hook Application对象替换为SourceApp中的Application对象同时调用onCreate
     *
     * @param appClassName
     * @param currentActivityThread
     * @param mBoundApplication
     * @param loadedApkInfo
     */
    private void hookApplicationAndCallOnCreate(String appClassName, Object currentActivityThread, Object mBoundApplication, Object loadedApkInfo) {
        // 把当前进程的mApplication 设置成了null
        RefInvoke.setFieldOjbect("android.app.LoadedApk", "mApplication",
                loadedApkInfo, null);
        // 获取ActivityThread中的mInitialApplication
        Object oldApplication = RefInvoke.getFieldOjbect(
                "android.app.ActivityThread", currentActivityThread,
                "mInitialApplication");
        // http://www.codeceo.com/article/android-context.html
        ArrayList<Application> mAllApplications = (ArrayList<Application>) RefInvoke
                .getFieldOjbect("android.app.ActivityThread",
                        currentActivityThread, "mAllApplications");
        mAllApplications.remove(oldApplication);// 删除mInitialApplication

        ApplicationInfo appinfoInLoadedApk = (ApplicationInfo) RefInvoke
                .getFieldOjbect("android.app.LoadedApk", loadedApkInfo,
                        "mApplicationInfo");
        ApplicationInfo appinfoInAppBindData = (ApplicationInfo) RefInvoke
                .getFieldOjbect("android.app.ActivityThread$AppBindData",
                        mBoundApplication, "appInfo");
        appinfoInLoadedApk.className = appClassName;
        appinfoInAppBindData.className = appClassName;
        // 创建新的Application对象
        Application app = (Application) RefInvoke.invokeMethod(
                "android.app.LoadedApk", "makeApplication", loadedApkInfo,
                new Class[]{boolean.class, Instrumentation.class},
                new Object[]{false, null});//执行 makeApplication（false,null）
        // 通过反射把新的Application对象设置到ActivityThread
        RefInvoke.setFieldOjbect("android.app.ActivityThread",
                "mInitialApplication", currentActivityThread, app);
        ArrayMap mProviderMap = (ArrayMap) RefInvoke.getFieldOjbect(
                "android.app.ActivityThread", currentActivityThread,
                "mProviderMap");
        Iterator it = mProviderMap.values().iterator();
        while (it.hasNext()) {
            Object providerClientRecord = it.next();
            Object localProvider = RefInvoke.getFieldOjbect(
                    "android.app.ActivityThread$ProviderClientRecord",
                    providerClientRecord, "mLocalProvider");
            RefInvoke.setFieldOjbect("android.content.ContentProvider",
                    "mContext", localProvider, app);
        }

        Log.d(TAG, "source app: " + app);
        // Call Application onCreate
        app.onCreate();
    }

    // 这段代码非常重要, 它替换了LoadedApk中的mResDir为SourceApk的路径, 从而使得SourceApk中的资源文件可以被加载成功
    private void hookLoadedApkResDir(Object loadedApkInfo) {
        Log.e(TAG, "before hook mResDir: " + RefInvoke.getFieldOjbect("android.app.LoadedApk", loadedApkInfo, "mResDir"));
        RefInvoke.setFieldOjbect("android.app.LoadedApk", "mResDir", loadedApkInfo, mSourceApkFilePath);
        Log.e(TAG, "after hook mResDir: " + RefInvoke.getFieldOjbect("android.app.LoadedApk", loadedApkInfo, "mResDir"));
    }

    /**
     * 释放被加壳的apk文件，so文件
     *
     * @param apkdata
     * @throws IOException
     */
    private void splitPayLoadFromDex(byte[] apkdata) throws IOException {
        int ablen = apkdata.length;
        //取被加壳apk的长度   这里的长度取值，对应加壳时长度的赋值都可以做些简化
        byte[] dexlen = new byte[4];
        System.arraycopy(apkdata, ablen - 4, dexlen, 0, 4);
        ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
        DataInputStream in = new DataInputStream(bais);
        int readInt = in.readInt();
        Log.d(TAG, "Apk size: " + Integer.toHexString(readInt));
        byte[] newdex = new byte[readInt];
        //把被加壳apk内容拷贝到newdex中
        System.arraycopy(apkdata, ablen - 4 - readInt, newdex, 0, readInt);
        //这里应该加上对于apk的解密操作，若加壳是加密处理的话
        //对源程序Apk进行解密
        newdex = EncryptUtils.decrptByte(newdex);

        //写入apk文件
        File file = new File(mSourceApkFilePath);
        try {
            FileOutputStream localFileOutputStream = new FileOutputStream(file);
            localFileOutputStream.write(newdex);
            localFileOutputStream.close();
        } catch (IOException localIOException) {
            throw new RuntimeException(localIOException);
        }

        //分析被加壳的apk文件
        ZipInputStream localZipInputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(file)));
        while (true) {
            ZipEntry localZipEntry = localZipInputStream.getNextEntry();//不了解这个是否也遍历子目录，看样子应该是遍历的
            if (localZipEntry == null) {
                localZipInputStream.close();
                break;
            }
            //取出被加壳apk用到的so文件，放到 libPath中（data/data/包名/payload_lib)
            String name = localZipEntry.getName();
            if (name.startsWith("lib/") && name.endsWith(".so")) {
                File storeFile = new File(mLibPath + File.separator + name.substring(name.lastIndexOf('/')));
                storeFile.createNewFile();
                FileOutputStream fos = new FileOutputStream(storeFile);
                byte[] arrayOfByte = new byte[1024];
                while (true) {
                    int i = localZipInputStream.read(arrayOfByte);
                    if (i == -1)
                        break;
                    fos.write(arrayOfByte, 0, i);
                }
                fos.flush();
                fos.close();
            }
            localZipInputStream.closeEntry();
        }
        localZipInputStream.close();
    }

    /**
     * 从apk包里面获取dex文件内容（byte）
     *
     * @return
     * @throws IOException
     */
    private byte[] readDexFileFromApk() throws IOException {
        ByteArrayOutputStream dexByteArrayOutputStream = new ByteArrayOutputStream();
        ZipInputStream localZipInputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(
                        this.getApplicationInfo().sourceDir)));
        while (true) {
            ZipEntry localZipEntry = localZipInputStream.getNextEntry();
            if (localZipEntry == null) {
                localZipInputStream.close();
                break;
            }
            if (localZipEntry.getName().equals("classes.dex")) {
                byte[] arrayOfByte = new byte[1024];
                while (true) {
                    int i = localZipInputStream.read(arrayOfByte);
                    if (i == -1)
                        break;
                    dexByteArrayOutputStream.write(arrayOfByte, 0, i);
                }
            }
            localZipInputStream.closeEntry();
        }
        localZipInputStream.close();
        return dexByteArrayOutputStream.toByteArray();
    }
}
