package com.liujian.reinforcelearning;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class SecondActivity extends Activity {

    public static final String TAG = "LJTAG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "SecondActivity onCreate");
        setContentView(R.layout.activity_second);
    }
}
