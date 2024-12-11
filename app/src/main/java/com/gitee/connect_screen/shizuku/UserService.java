package com.gitee.connect_screen.shizuku;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.util.Log;

import android.os.RemoteException;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.view.Display;
import android.view.SurfaceControl;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.job.AndroidVersions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
@SuppressLint({"PrivateApi", "SoonBlockedPrivateApi", "BlockedPrivateApi"})
@TargetApi(AndroidVersions.API_34_ANDROID_14)
public class UserService extends IUserService.Stub  {
    private static Method getPhysicalDisplayIdsMethod;
    private static Method getPhysicalDisplayTokenMethod;

    public UserService() {
        Log.i("UserService", "constructor");
    }


    private static final Class<?> DISPLAY_CONTROL_CLASS;

    static {
        Class<?> displayControlClass = null;
        try {
            Class<?> classLoaderFactoryClass = Class.forName("com.android.internal.os.ClassLoaderFactory");
            Method createClassLoaderMethod = classLoaderFactoryClass.getDeclaredMethod("createClassLoader", String.class, String.class, String.class,
                    ClassLoader.class, int.class, boolean.class, String.class);
            ClassLoader classLoader = (ClassLoader) createClassLoaderMethod.invoke(null, "/system/framework/services.jar", null, null,
                    ClassLoader.getSystemClassLoader(), 0, true, null);

            displayControlClass = classLoader.loadClass("com.android.server.display.DisplayControl");

            Method loadMethod = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
            loadMethod.setAccessible(true);
            loadMethod.invoke(Runtime.getRuntime(), displayControlClass, "android_servers");
        } catch (Throwable e) {
            Log.e("UserService", "Could not initialize DisplayControl", e);
            // Do not throw an exception here, the methods will fail when they are called
        }
        DISPLAY_CONTROL_CLASS = displayControlClass;
    }
    
    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        Log.i("UserService", "destroy");
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    public Object getDesiredDisplayModeSpecs(IBinder displayToken) throws RemoteException {
        try {
            Method method = SurfaceControl.class.getDeclaredMethod("getDesiredDisplayModeSpecs", IBinder.class);
            method.setAccessible(true);
            Object obj = null;
            try {
                obj = method.invoke(null, displayToken);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return obj;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    public String getDynamicDisplayInfo(long displayId) throws RemoteException {
        try {
            Method method = SurfaceControl.class.getDeclaredMethod("getDynamicDisplayInfo", long.class);
            method.setAccessible(true);
            Object obj = null;
            try {
                obj = method.invoke(null, displayId);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return String.valueOf(obj);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    private static Method getGetPhysicalDisplayTokenMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayTokenMethod == null) {
            getPhysicalDisplayTokenMethod = DISPLAY_CONTROL_CLASS.getMethod("getPhysicalDisplayToken", long.class);
        }
        return getPhysicalDisplayTokenMethod;
    }

    public IBinder getPhysicalDisplayToken(long physicalDisplayId) {
        Log.i("UserService", "getPhysicalDisplayToken " + physicalDisplayId);
        try {
            Method method = getGetPhysicalDisplayTokenMethod();
            return (IBinder) method.invoke(null, physicalDisplayId);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not invoke method", e);
            return null;
        }
    }

    private static Method getGetPhysicalDisplayIdsMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayIdsMethod == null) {
            getPhysicalDisplayIdsMethod = DISPLAY_CONTROL_CLASS.getMethod("getPhysicalDisplayIds");
        }
        return getPhysicalDisplayIdsMethod;
    }

    public long[] getPhysicalDisplayIds() {
        Log.i("UserService", "getPhysicalDisplayIds");
        try {
            Method method = getGetPhysicalDisplayIdsMethod();
            return (long[]) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not invoke method", e);
            return null;
        }
    }

    private void changeRenderTo120(Object refreshRateRanges ) {
        if (refreshRateRanges == null) {
            return;
        }
        Object render = null;
        try {
            Field renderField = refreshRateRanges.getClass().getDeclaredField("render");
            renderField.setAccessible(true);
            render = renderField.get(refreshRateRanges);
            Log.i("UserService", "got render: " + render);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not get render field", e);
        }
        if (render != null) {
            setMinMax(render);
        }

        Object physical = null;
        try {
            Field physicalField = refreshRateRanges.getClass().getDeclaredField("physical");
            physicalField.setAccessible(true);
            physical = physicalField.get(refreshRateRanges);
            Log.i("UserService", "got physical: " + physical);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not get physical field", e);
        }
        if (physical != null) {
            setMinMax(physical);
        }
    }

    private void setMinMax(Object render) {
        try {
            Field minField = render.getClass().getDeclaredField("min");
            minField.setAccessible(true);
            minField.setFloat(render, 90.0f);
            Log.i("UserService", "set min to 90: " + render);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not set min field", e);
        }
        try {
            Field maxField = render.getClass().getDeclaredField("max");
            maxField.setAccessible(true);
            maxField.setFloat(render, 121.0f);
            Log.i("UserService", "set max to 121: " + render);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not set max field", e);
        }
    }

    public void changeTo120() throws RemoteException {
        Log.i("UserService", "changeTo120");
        long[] physicalDisplayIds = getPhysicalDisplayIds();
        if (physicalDisplayIds.length == 1) {
            Log.i("UserService", "only 1 display");
            return;
        }
        IBinder physicalDisplayToken = getPhysicalDisplayToken(physicalDisplayIds[1]);
        Log.i("UserService", "got token: " + physicalDisplayToken);
        String dynamicDisplayInfo = getDynamicDisplayInfo(physicalDisplayIds[1]);
        Log.i("UserService", "got dynamic display info: " + dynamicDisplayInfo);
        Object desiredDisplayModeSpecs = getDesiredDisplayModeSpecs(physicalDisplayToken);
        Log.i("UserService", "got desired mode specs: " + desiredDisplayModeSpecs);
        try {
            Field defaultModeField = desiredDisplayModeSpecs.getClass().getDeclaredField("defaultMode");
            defaultModeField.setAccessible(true);
            defaultModeField.setInt(desiredDisplayModeSpecs, 1);
            Log.i("UserService", "set defaultMode to 2: " + desiredDisplayModeSpecs);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not set defaultMode field", e);
        }
        Object primaryRanges = null;
        try {
            Field primaryRangesField = desiredDisplayModeSpecs.getClass().getDeclaredField("primaryRanges");
            primaryRangesField.setAccessible(true);
            primaryRanges = primaryRangesField.get(desiredDisplayModeSpecs);
            Log.i("UserService", "got primary ranges: " + primaryRanges);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not get primary ranges", e);
        }
        changeRenderTo120(primaryRanges);
        Object appRequestRanges = null;
        try {
            Field appRequestRangesField = desiredDisplayModeSpecs.getClass().getDeclaredField("appRequestRanges");
            appRequestRangesField.setAccessible(true);
            appRequestRanges = appRequestRangesField.get(desiredDisplayModeSpecs);
            Log.i("UserService", "got app request ranges: " + appRequestRanges);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not get app request ranges", e);
        }
        changeRenderTo120(appRequestRanges);
        setDesiredDisplayModeSpecs(physicalDisplayToken, desiredDisplayModeSpecs);

        try {
            Method getBootDisplayModeSupportMethod = SurfaceControl.class.getDeclaredMethod("getBootDisplayModeSupport");
            getBootDisplayModeSupportMethod.setAccessible(true);
            boolean bootDisplayModeSupport = (boolean) getBootDisplayModeSupportMethod.invoke(null);
            Log.i("UserService", "bootDisplayModeSupport: " + bootDisplayModeSupport);
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not invoke getBootDisplayModeSupport", e);
        }
    }

    private void setDesiredDisplayModeSpecs(IBinder physicalDisplayToken, Object desiredDisplayModeSpecs) {
        Class surfaceControlClass = SurfaceControl.class;
        try {
            Class[] classes = surfaceControlClass.getDeclaredClasses();
            Class desiredDisplayModeSpecsClass = null;
            for (Class clazz : classes) {
                if (clazz.getSimpleName().equals("DesiredDisplayModeSpecs")) {
                    desiredDisplayModeSpecsClass = clazz;
                    break;
                }
            }
            if (desiredDisplayModeSpecsClass == null) {
                Log.e("UserService", "Could not find DesiredDisplayModeSpecs class");
                return;
            }
            Method setDesiredDisplayModeSpecsMethod = surfaceControlClass.getDeclaredMethod(
                "setDesiredDisplayModeSpecs",
                IBinder.class,
                desiredDisplayModeSpecsClass
            );
            Log.i("UserService", "got method: " + setDesiredDisplayModeSpecsMethod);
            setDesiredDisplayModeSpecsMethod.setAccessible(true);
            Log.i("UserService", "about to setDesiredDisplayModeSpecsMethod");
            setDesiredDisplayModeSpecsMethod.invoke(null, physicalDisplayToken, desiredDisplayModeSpecs);
            Log.i("UserService", "setDesiredDisplayModeSpecsMethod called");
        } catch (ReflectiveOperationException e) {
            Log.e("UserService", "Could not invoke setDesiredDisplayModeSpecs", e);
        }
    }

    @Override
    public String fetchLogs() throws RemoteException  {
        try {
            Process process = Runtime.getRuntime().exec("logcat -d -f /sdcard/Download/安卓屏连.log");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            reader.close();
            process.waitFor();

            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "logcat -d failed", e);
            throw new RemoteException("Failed to execute logcat -d: " + e.getMessage());
        }
    }

    @Override
    public String dumpsysInput() throws RemoteException {
        try {
            Process process = Runtime.getRuntime().exec("dumpsys input");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            reader.close();
            process.waitFor();
            
            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "dumpsysInput failed", e);
            throw new RemoteException("Failed to execute dumpsys input: " + e.getMessage());
        }
    }


    public void tryChangeDisplayConfig() throws RemoteException {
        Log.d("UserService", "tryChangeDisplayConfig invoke");
        
        try {
            Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Method getInstanceMethod = dmgClass.getDeclaredMethod("getInstance");
            Object dmg = getInstanceMethod.invoke(null);

//            Method setRefreshRateMethod = dmgClass.getDeclaredMethod("setRefreshRateSwitchingType", int.class);
//            setRefreshRateMethod.invoke(dmg, 2);
//
//            Method getRefreshRateMethod = dmgClass.getDeclaredMethod("getRefreshRateSwitchingType");
//            int type = (int) getRefreshRateMethod.invoke(dmg);
//            Log.d("UserService", "RefreshRateSwitchingType -> " + type);
//
            Constructor<?> dmConstructor = DisplayManager.class.getDeclaredConstructor(Context.class);
            DisplayManager displayManager = (DisplayManager) dmConstructor.newInstance(FakeContext.get());
//
//            Method getMatchContentMethod = DisplayManager.class.getDeclaredMethod("getMatchContentFrameRateUserPreference");
//            int matchContent = (int) getMatchContentMethod.invoke(displayManager);
//            Log.d("UserService", "matchContentFrameRateUserPreference -> " + matchContent);
//
//            Method getSystemModeMethod = dmgClass.getDeclaredMethod("getSystemPreferredDisplayMode", int.class);
//            Object systemMode = getSystemModeMethod.invoke(dmg, 0);
//            Log.d("UserService", "SystemPreferredDisplayMode -> " + systemMode);
//
//            Method getUserModeMethod = dmgClass.getDeclaredMethod("getUserPreferredDisplayMode", int.class);
//            Object userMode = getUserModeMethod.invoke(dmg, 0);
//            Log.d("UserService", "UserPreferredDisplayMode -> " + userMode);
//
//            Method getGlobalUserModeMethod = DisplayManager.class.getDeclaredMethod("getGlobalUserPreferredDisplayMode");
//            Object globalUserMode = getGlobalUserModeMethod.invoke(displayManager);
//            Log.d("UserService", "GlobalUserPreferredDisplayMode -> " + globalUserMode);
//
//            Method setShouldRespectMethod = dmgClass.getDeclaredMethod("setShouldAlwaysRespectAppRequestedMode", boolean.class);
//            setShouldRespectMethod.invoke(dmg, true);
//
//            Method getShouldRespectMethod = dmgClass.getDeclaredMethod("shouldAlwaysRespectAppRequestedMode");
//            boolean should = (boolean) getShouldRespectMethod.invoke(dmg);
//            Log.d("UserService", "shouldAlwaysRespectAppRequestedMode -> " + should);

            for (Display display : displayManager.getDisplays()) {
                if (display.getDisplayId() != 0) {
                    Log.d("UserService", "display -> " + display);
                    Log.d("UserService", "");
                }
            }

        } catch (Exception e) {
            Log.e("UserService", "tryChangeDisplayConfig failed", e);
            throw new RemoteException("Failed to change display config: " + e.getMessage());
        }
    }
    
}
