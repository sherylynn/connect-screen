# AI Notes: Recent Changes

## 版本基线
- 当前检出：`v1.3.3`（提交 `aaee189`）

## 功能背景
- 首页“模拟熄屏/真实熄屏”入口通过启动 `PureBlackActivity` 实现。模拟熄屏是以纯黑全屏 Activity 覆盖主屏，配合输入重定向；真实熄屏需要 Shizuku 用户服务调用 `SurfaceControl.setScreenPower(OFF)` 关闭主屏供电。

## 本次与上次改动

### 1) 新增“允许强行熄屏”设置开关
- 目的：在未投屏时（`State.lastSingleAppDisplay <= 0`）允许用户仍可触发熄屏（模拟或真实），避免按钮因无投屏而只能弹帮助。
- 影响文件：
  - `app/src/main/res/layout/fragment_settings.xml`
    - 新增 `CheckBox`：`@id/cbAllowForceScreenOff`，文案“允许在未投屏时强行熄屏（风险：可能影响操作）”。
  - `app/src/main/java/com/gitee/connect_screen/SettingsFragment.java`
    - 新增字段：`cbAllowForceScreenOff`
    - 新增方法：`setupAllowForceScreenOffCheckbox()`
    - SharedPreferences：`settings.allow_force_screen_off` 读写逻辑
  - `app/src/main/java/com/gitee/connect_screen/HomeFragment.java`
    - 修改“模拟/真实熄屏”按钮点击逻辑：当 `!allow_force_screen_off && lastSingleAppDisplay <= 0` 时仍显示帮助，否则允许进入 `PureBlackActivity`。
- 行为变化：未投屏状态下，只要开启开关，即可从首页触发熄屏流程。

### 2) 真实熄屏偶发退化为“纯黑覆盖”的竞态修复
- 现象：开启“使用真实熄屏”时，偶尔表现为仅启动纯黑 Activity（看似模拟熄屏），未真正关闭主屏供电。
- 根因：进入 `PureBlackActivity` 时 `use_real_screen_off == true`，但 `State.userService` 尚未绑定完成（为 null），导致真实熄屏调用条件不满足。
- 解决：在 `PureBlackActivity` 的 onCreate 内，如果 `useRealScreenOff == true` 且 `State.userService == null`，主动 `peek/bind` 用户服务，并 `postDelayed(300ms)` 重试 `powerOffScreen()`；否则直接调用。
- 影响文件：
  - `app/src/main/java/com/gitee/connect_screen/PureBlackActivity.java`
    - 引入 `rikka.shizuku.Shizuku` 绑定接口
    - 在 Shizuku 有权限的分支内，按上述逻辑绑定并延迟重试 `powerOffScreen()`
- 行为变化：真实熄屏触发更稳定，不再因服务初始化竞态退化为模拟。

## 关键逻辑速览
- 首页按钮：`HomeFragment#onCreateView` → `simulateScreenOffBtn.setOnClickListener`
  - 读取 `settings.use_real_screen_off` 决定按钮文案（真实/模拟）
  - 读取 `settings.allow_force_screen_off` 决定是否可在未投屏下放行
  - 启动 `PureBlackActivity`
- 熄屏实现：`PureBlackActivity`
  - 模拟熄屏：纯黑全屏覆盖 + 捕获输入并重定向到 `State.lastSingleAppDisplay`
  - 真实熄屏：Shizuku 用户服务 → `SurfaceControl.setScreenPower(OFF)`，音量键监听用于唤醒；onDestroy 恢复 `POWER_MODE_NORMAL`
  - 竞态修复：`userService == null` 时先绑定、后延迟重试

## 注意事项
- 真实熄屏要求：设备已安装并授权 Shizuku；`State.userService` 可绑定成功。
- 强行熄屏风险：未投屏时关闭主屏可能影响操作体验，需通过音量键或其它方式恢复。
- 代码风格：设置键统一保存在 `settings` SharedPreferences 中：
  - `use_real_screen_off`: 是否使用真实熄屏
  - `allow_force_screen_off`: 允许未投屏时强行熄屏

## 相关文件清单
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/java/com/gitee/connect_screen/SettingsFragment.java`
- `app/src/main/java/com/gitee/connect_screen/HomeFragment.java`
- `app/src/main/java/com/gitee/connect_screen/PureBlackActivity.java`
