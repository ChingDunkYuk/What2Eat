# What2Eat v1.0.0 R8 规则（有意保持最小集）
#
# 本项目无需额外 keep 规则，依据（v1.0.0 立项前已完成排查）：
# - Room 2.6.1 / Hilt 2.52 / Compose BOM 均自带 consumer proguard 规则，
#   实体、DAO、注入类全部被库方规则覆盖
# - WebView 店名抓取仅用 evaluateJavascript（字符串脚本 + 回调），
#   无 addJavascriptInterface、无 Class.forName 反射、无 @Keep
# - 枚举以 ordinal 存库（entries.getOrElse 解析），无反射路径
# - JSON 序列化用 Android 内置 org.json，非反射型库
# - 无 getIdentifier 等动态资源查找（资源收缩安全）
#
# 原则：不加多余 keep——过度 keep 会让 R8 收缩失效。
# 若 release 真机回归发现崩溃，先看 mapping.txt（app/build/outputs/mapping/release/）
# 定位被裁类后在此最小化补 keep。

# v1.1.0：JS 数据劫持接口（WebView addJavascriptInterface 回调是 R8 反射调用点，
# 类名与方法名混淆后 JS 侧 window.What2EatShopName 将找不到）
-keep class com.what2eat.data.share.ShopNameJavascriptInterface { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
