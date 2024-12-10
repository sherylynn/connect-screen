package com.gitee.connect_screen.shizuku;
import android.os.IBinder;

interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String fetchLogs() = 2;

    String dumpsysInput() = 3;

    IBinder getPhysicalDisplayToken(long physicalDisplayId) = 4;

    long[] getPhysicalDisplayIds() = 5;

    String getDynamicDisplayInfo(long displayId) = 7;

    void changeTo120() = 8;

    void tryChangeDisplayConfig() = 9;
}