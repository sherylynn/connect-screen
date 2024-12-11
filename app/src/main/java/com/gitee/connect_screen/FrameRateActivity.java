package com.gitee.connect_screen;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.TextView;

public class FrameRateActivity extends Activity {
    private static final String EXTRA_DISPLAY_ID = "display_id";

    public static void startFrameRateActivity(Context context, int displayId) {
        Intent intent = new Intent(context, FrameRateActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_DISPLAY_ID, displayId);
        
        // 创建 ActivityOptions 并设置目标显示器
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        
        // 使用 options 启动活动
        context.startActivity(intent, options.toBundle());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(LayoutParams.FLAG_FULLSCREEN, LayoutParams.FLAG_FULLSCREEN);
        
        // 设置120Hz刷新率 (新方法)
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.preferredDisplayModeId = findDisplayModeByRefreshRate(120);
        window.setAttributes(layoutParams);
        
        // 简单的布局显示当前帧率
        TextView textView = new TextView(this);
        textView.setText("已设置120Hz刷新率");
        setContentView(textView);
    }

    // 根据刷新率查找对应的显示模式
    private int findDisplayModeByRefreshRate(float targetRefreshRate) {
        Display display = getDisplay();
        Display.Mode[] modes = display.getSupportedModes();
        
        // 寻找最接近目标刷新率的模式
        Display.Mode bestMode = null;
        float minDiff = Float.MAX_VALUE;
        
        for (Display.Mode mode : modes) {
            float diff = Math.abs(mode.getRefreshRate() - targetRefreshRate);
            if (diff < minDiff) {
                minDiff = diff;
                bestMode = mode;
            }
        }
        
        return bestMode != null ? bestMode.getModeId() : 0;
    }
} 