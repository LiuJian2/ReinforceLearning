package com.liujian.reinforcelearning.app;

import android.app.Application;
import android.util.Log;

/**
 * Created by liujian03 on 2017/12/5.
 */

public class SourceApplication extends Application {

    public static final String TAG = "LJTAG";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Hello SourceApplication!");
    }
}
