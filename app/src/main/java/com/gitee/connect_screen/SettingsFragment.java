package com.gitee.connect_screen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.hardware.input.InputManager;
import android.hardware.display.DisplayManager;

import com.gitee.connect_screen.job.BindAllExternalInputToDisplay;
import com.gitee.connect_screen.shizuku.PermissionManager;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsFragment extends Fragment {
    private List<Display> displayList;
    private Spinner spinnerDisplays;
    private Spinner spinnerAutoScreenOffDisplay;
    private boolean skipAutoScreenOffSpinnerCallback = true;
    private Button btnBind;
    private RecyclerView rvExternalDevices;
    private RecyclerView rvInternalDevices;
    private CheckBox cbForceDesktop;
    private CheckBox cbForceResizable;
    private CheckBox cbEnableFreeform;
    private CheckBox cbEnableNonResizable;
    private CheckBox cbDisableScreenShareProtection;
    private CheckBox cbDisableUsbAudio;
    private CheckBox cbUseRealScreenOff;
    private CheckBox cbAllowForceScreenOff;
    private CheckBox cbMouseMiddleButtonSwitch;
    private CheckBox cbStayOnWhilePlugged;
    private CheckBox cbAutoScreenOffOnAppOpen;
    private View externalDeviceContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        cbForceDesktop = view.findViewById(R.id.cbForceDesktop);
        cbForceResizable = view.findViewById(R.id.cbForceResizable);
        cbEnableFreeform = view.findViewById(R.id.cbEnableFreeform);
        cbEnableNonResizable = view.findViewById(R.id.cbEnableNonResizable);
        cbDisableScreenShareProtection = view.findViewById(R.id.cbDisableScreenShareProtection);
        spinnerDisplays = view.findViewById(R.id.spinnerDisplays);
        btnBind = view.findViewById(R.id.btnBind);
        rvExternalDevices = view.findViewById(R.id.rvExternalDevices);
        rvInternalDevices = view.findViewById(R.id.rvInternalDevices);
        cbDisableUsbAudio = view.findViewById(R.id.cbDisableUsbAudio);
        cbUseRealScreenOff = view.findViewById(R.id.cbUseRealScreenOff);
        cbAllowForceScreenOff = view.findViewById(R.id.cbAllowForceScreenOff);
        cbMouseMiddleButtonSwitch = view.findViewById(R.id.cbMouseMiddleButtonSwitch);
        cbStayOnWhilePlugged = view.findViewById(R.id.cbStayOnWhilePlugged);
        cbAutoScreenOffOnAppOpen = view.findViewById(R.id.cbAutoScreenOffOnAppOpen);
        spinnerAutoScreenOffDisplay = view.findViewById(R.id.spinnerAutoScreenOffDisplay);
        externalDeviceContainer = view.findViewById(R.id.externalDeviceContainer);
        
        initializeDisplaySpinner();
        initializeAutoScreenOffDisplaySpinner();
        setupBindButton();
        setupDeviceLists();

        if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
            setupDisableScreenShareProtectionCheckbox();
            setupForceDesktopCheckbox();
            setupForceResizableCheckbox();
            setupEnableFreeformCheckbox();
            setupEnableNonResizableCheckbox();
            setupDisableUsbAudioCheckbox();
            setupUseRealScreenOffCheckbox();
            setupAllowForceScreenOffCheckbox();
            setupMouseMiddleButtonSwitchCheckbox();
            setupStayOnWhilePluggedCheckbox();
            setupAutoScreenOffOnAppOpenCheckbox();
        } else {
            cbDisableScreenShareProtection.setVisibility(View.GONE);
            cbForceDesktop.setVisibility(View.GONE);
            cbForceResizable.setVisibility(View.GONE);
            cbEnableFreeform.setVisibility(View.GONE);
            cbEnableNonResizable.setVisibility(View.GONE);
            cbDisableUsbAudio.setVisibility(View.GONE);
            cbStayOnWhilePlugged.setVisibility(View.GONE);
        }
        
        // 确保绑定屏幕自动熄屏选项始终可见
        spinnerAutoScreenOffDisplay.setVisibility(View.VISIBLE);
        
        return view;
    }

    private void initializeDisplaySpinner() {
        DisplayManager displayManager = (DisplayManager) requireContext().getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        displayList = Arrays.asList(displays);

        List<String> displayNames = new ArrayList<>();
        // 绑定输入设备不需要"不自动熄屏"选项，直接列出所有显示器
        for (Display display : displays) {
            displayNames.add("显示器 " + display.getDisplayId() + " (" + display.getName() + ")");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            displayNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDisplays.setAdapter(adapter);
    }

    private void initializeAutoScreenOffDisplaySpinner() {
        DisplayManager displayManager = (DisplayManager) requireContext().getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();

        SharedPreferences settings = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        String savedDisplayName = settings.getString("auto_screen_off_display_name", "");

        List<String> displayNames = new ArrayList<>();
        List<String> displayKeys = new ArrayList<>();

        displayNames.add("不自动熄屏");
        displayKeys.add("");

        boolean savedNameFound = false;
        for (Display display : displays) {
            displayNames.add(display.getName());
            displayKeys.add(display.getName());
            if (display.getName().equals(savedDisplayName)) {
                savedNameFound = true;
            }
        }

        if (!savedNameFound && !savedDisplayName.isEmpty()) {
            displayNames.add(savedDisplayName + " (未连接)");
            displayKeys.add(savedDisplayName);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            displayNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        skipAutoScreenOffSpinnerCallback = true;
        spinnerAutoScreenOffDisplay.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (skipAutoScreenOffSpinnerCallback) {
                    return;
                }
                String selectedKey = displayKeys.get(pos);
                settings.edit()
                        .putString("auto_screen_off_display_name", selectedKey)
                        .apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        spinnerAutoScreenOffDisplay.setAdapter(adapter);

        int position = 0;
        if (!savedDisplayName.isEmpty()) {
            for (int i = 0; i < displayKeys.size(); i++) {
                if (displayKeys.get(i).equals(savedDisplayName)) {
                    position = i;
                    break;
                }
            }
        }
        spinnerAutoScreenOffDisplay.setSelection(position);

        spinnerAutoScreenOffDisplay.post(() -> skipAutoScreenOffSpinnerCallback = false);
    }

    private void setupBindButton() {
        btnBind.setOnClickListener(v -> {
            if (!ShizukuUtils.hasShizukuStarted()) {
                Toast.makeText(requireContext(), "需要安装 shizuku", Toast.LENGTH_SHORT).show();
                return;
            }
            int selectedPosition = spinnerDisplays.getSelectedItemPosition();
            if (selectedPosition != -1 && selectedPosition < displayList.size()) {
                Display selectedDisplay = displayList.get(selectedPosition);
                State.startNewJob(new BindAllExternalInputToDisplay(selectedDisplay.getDisplayId()));
            }
        });
    }

    private void setupDeviceLists() {
        InputManager inputManager = (InputManager) requireContext().getSystemService(Context.INPUT_SERVICE);
        int[] deviceIds = inputManager.getInputDeviceIds();
        
        List<InputDevice> externalDevices = new ArrayList<>();
        List<InputDevice> internalDevices = new ArrayList<>();
        
        for (int deviceId : deviceIds) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null) {
                if (device.isExternal()) {
                    externalDevices.add(device);
                } else {
                    internalDevices.add(device);
                }
            }
        }

        externalDeviceContainer.setVisibility(externalDevices.isEmpty() ? View.GONE : View.VISIBLE);
        
        rvExternalDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvInternalDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        DeviceAdapter externalAdapter = new DeviceAdapter(externalDevices, this::showDeviceDetails);
        DeviceAdapter internalAdapter = new DeviceAdapter(internalDevices, this::showDeviceDetails);
        
        rvExternalDevices.setAdapter(externalAdapter);
        rvInternalDevices.setAdapter(internalAdapter);
    }

    private void showDeviceDetails(InputDevice device) {
        State.breadcrumbManager.pushBreadcrumb(device.getName(), () ->
        InputDeviceDetailFragment.newInstance(device.getId())
        );
    }

    private void setupForceDesktopCheckbox() {
        // 读取当前设置
        boolean isForceDesktop = Settings.Global.getInt(requireContext().getContentResolver(),
                "force_desktop_mode_on_external_displays", 0) == 1;
        cbForceDesktop.setChecked(isForceDesktop);
        // 检查是否为华为手机
        boolean isHuawei = Build.MANUFACTURER.toLowerCase().contains("huawei") ||
                          Build.BRAND.toLowerCase().contains("huawei") ||
                          Build.DEVICE.toLowerCase().contains("huawei");
        
        if (isHuawei) {
            // 华为手机上禁用此选项,因为可能导致问题
            cbForceDesktop.setVisibility(View.GONE);
        }

        cbForceDesktop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Settings.Global.putInt(requireContext().getContentResolver(),
                        "force_desktop_mode_on_external_displays", isChecked ? 1 : 0);
                        
                Settings.Global.putInt(requireContext().getContentResolver(),
                "force_desktop_mode_on_external_displays", isChecked ? 1 : 0);
            } catch (SecurityException e) {
                State.log("failed: " + e);
            }
        });
    }

    private void setupForceResizableCheckbox() {
        // 读取当前设置
        boolean isForceResizable = Settings.Global.getInt(requireContext().getContentResolver(),
                "force_resizable_activities", 0) == 1;
        cbForceResizable.setChecked(isForceResizable);

        cbForceResizable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Settings.Global.putInt(requireContext().getContentResolver(),
                        "force_resizable_activities", isChecked ? 1 : 0);
            } catch (SecurityException e) {
                State.log("failed: " + e);
            }
        });
    }

    private void setupEnableFreeformCheckbox() {
        boolean isEnableFreeform = Settings.Global.getInt(requireContext().getContentResolver(),
                "enable_freeform_support", 0) == 1;
        cbEnableFreeform.setChecked(isEnableFreeform);

        cbEnableFreeform.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Settings.Global.putInt(requireContext().getContentResolver(),
                        "enable_freeform_support", isChecked ? 1 : 0);
            } catch (SecurityException e) {
                State.log("failed: " + e);
            }
        });
    }

    private void setupEnableNonResizableCheckbox() {
        boolean isEnableNonResizable = Settings.Global.getInt(requireContext().getContentResolver(),
                "enable_non_resizable_multi_window", 0) == 1;
        cbEnableNonResizable.setChecked(isEnableNonResizable);

        cbEnableNonResizable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Settings.Global.putInt(requireContext().getContentResolver(),
                        "enable_non_resizable_multi_window", isChecked ? 1 : 0);
            } catch (SecurityException e) {
                State.log("failed: " + e);
            }
        });
    }

    private void setupDisableScreenShareProtectionCheckbox() {
        boolean isDisabled = Settings.Global.getInt(requireContext().getContentResolver(),
                "disable_screen_share_protections_for_apps_and_notifications", 0) == 1;
        cbDisableScreenShareProtection.setChecked(isDisabled);

        cbDisableScreenShareProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Settings.Global.putInt(requireContext().getContentResolver(),
                        "disable_screen_share_protections_for_apps_and_notifications", isChecked ? 1 : 0);
            } catch (SecurityException e) {
                State.log("failed: " + e);
            }
        });
    }

    private void setupDisableUsbAudioCheckbox() {
        // 从 SharedPreferences 读取保存的设置
        boolean isDisabled = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("usb_audio_disabled", false);
                
        cbDisableUsbAudio.setChecked(isDisabled);

        cbDisableUsbAudio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                // 更新系统设置
                Settings.Secure.putInt(requireContext().getContentResolver(),
                        "usb_audio_automatic_routing_disabled", isChecked ? 1 : 0);
                        
                // 保存到 SharedPreferences
                requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("usb_audio_disabled", isChecked)
                        .apply();
            } catch (SecurityException e) {
                State.log("failed: " + e);
            }
        });
    }

    private void setupUseRealScreenOffCheckbox() {
        // 从 SharedPreferences 读取保存的设置
        boolean useRealScreenOff = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("use_real_screen_off", false);
                
        cbUseRealScreenOff.setChecked(useRealScreenOff);

        cbUseRealScreenOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存到 SharedPreferences
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("use_real_screen_off", isChecked)
                    .apply();
        });
    }

    private void setupAllowForceScreenOffCheckbox() {
        boolean allowForce = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("allow_force_screen_off", false);
        cbAllowForceScreenOff.setChecked(allowForce);

        cbAllowForceScreenOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("allow_force_screen_off", isChecked)
                    .apply();
        });
    }

    private void setupStayOnWhilePluggedCheckbox() {
        // 读取当前设置
        boolean isStayOnWhilePlugged = Settings.Global.getInt(requireContext().getContentResolver(),
                "stay_on_while_plugged_in", 0) != 0;
        cbStayOnWhilePlugged.setChecked(isStayOnWhilePlugged);

        cbStayOnWhilePlugged.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                // 设置值为 7 表示在任何充电状态下都保持屏幕开启
                // (AC = 1, USB = 2, Wireless = 4, 1 + 2 + 4 = 7)
                Settings.Global.putInt(requireContext().getContentResolver(),
                        "stay_on_while_plugged_in", isChecked ? 7 : 0);
            } catch (SecurityException e) {
                State.log("failed: " + e);
            }
        });
    }

    private void setupMouseMiddleButtonSwitchCheckbox() {
        // 从 SharedPreferences 读取保存的设置
        boolean mouseMiddleButtonSwitch = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("mouse_middle_button_switch_display", false);
                
        cbMouseMiddleButtonSwitch.setChecked(mouseMiddleButtonSwitch);

        cbMouseMiddleButtonSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存到 SharedPreferences
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("mouse_middle_button_switch_display", isChecked)
                    .apply();
        });
    }

    private void setupAutoScreenOffOnAppOpenCheckbox() {
        // 从 SharedPreferences 读取保存的设置
        boolean autoScreenOffOnAppOpen = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("auto_screen_off_on_app_open", false);
                
        cbAutoScreenOffOnAppOpen.setChecked(autoScreenOffOnAppOpen);

        cbAutoScreenOffOnAppOpen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存到 SharedPreferences
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("auto_screen_off_on_app_open", isChecked)
                    .apply();
        });
    }
}