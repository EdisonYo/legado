# 获取更多信息，请参阅 http://developer.android.com/guide/developing/tools/proguard.html
# ProGuard按照顺序执行，不可更改：压缩代码(Shrinking)→优化字节码(Optimization)→混淆(Obfuscation)
# 规则排序：全局设定→类设定→警告抑制

# =====================全局设定=====================
# 禁用混淆
-dontobfuscate

# 保留字节码属性
-keepattributes *Annotation*, InnerClasses, Signature, LineNumberTable

# 移除对Log类方法的调用，删除调试日志
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# ===================保护第三方库===================
# Hutool
-keep class cn.hutool.crypto.** { *; }
-keep class !cn.hutool.core.util.RuntimeUtil,
            !cn.hutool.core.util.ClassLoaderUtil,
            !cn.hutool.core.util.ReflectUtil,
            !cn.hutool.core.util.SerializeUtil,
            !cn.hutool.core.util.ClassUtil,
            cn.hutool.core.codec.**,
            cn.hutool.core.util.** { *; }
-dontwarn cn.hutool.**

# OkHttp
-keep class okhttp3.* { *; }

# Okio
-keep class okio.* { *; }

# JsonPath
-keep class com.jayway.jsonpath.* { *; }

# Jsoup
-keep class org.jsoup.** { *; }

# Xpath（SPI动态加载）
-keep class * implements org.seimicrawler.xpath.core.AxisSelector { *; }
-keep class * implements org.seimicrawler.xpath.core.NodeTest { *; }
-keep class * implements org.seimicrawler.xpath.core.Function { *; }

# GSYVideoPlayer
-keep class com.shuyu.gsyvideoplayer.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.**

# Cronet（证书固定相关）
-keepclassmembers class org.chromium.net.X509Util {
    *** sDefaultTrustManager;
    *** sTestTrustManager;
}

# ExoPlayer（反射设置UA，保证该私有变量不被混淆）
-keepclassmembers class androidx.media3.datasource.cache.CacheDataSource$Factory {
    *** upstreamDataSourceFactory;
}

# Sora Editor
# TM4E（语法高亮）
-keep class org.eclipse.tm4e.** { *; }
# Joni（正则引擎）
-keep class org.joni.** { *; }

# ====================保护特定类====================

# legado
-keep class * extends io.legado.app.help.JsExtensions { *; }
-keep class io.legado.app.api.ReturnData { *; }
-keep class **.data.entities.** { *; }
-keep class **.help.http.CookieStore { *; }
-keep class **.help.CacheManager { *; }
-keep class **.help.http.StrResponse { *; }

# 视图相关（反射调用）
-keep class androidx.appcompat.view.menu.MenuBuilder {
    *** setOptionalIconsVisible(...);
    *** getNonActionItems();
}
-keepnames class androidx.appcompat.view.menu.SubMenuBuilder
-keepclassmembers class androidx.appcompat.widget.Toolbar {
    *** mNavButtonView;
}
-keep public class * extends android.view.View {
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, java.lang.Boolean);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 文件提供器（反射构造）
-keep class androidx.documentfile.provider.TreeDocumentFile {
    <init>(...);
}

# LiveEventBus（反射访问私有字段）
-keepclassmembers class androidx.lifecycle.LiveData {
    *** mObservers;
    *** mActiveCount;
}
-keepclassmembers class androidx.arch.core.internal.SafeIterableMap {
    *** size();
    *** putIfAbsent(...);
}

# ========忽略警告（避免因可选依赖导致编译中断）========

# JSpecify 注解（JSR-305注解库，编译时检查用，运行时不需要）
-dontwarn org.jspecify.annotations.NullMarked

# Markdown扩展（删除线）
#-dontwarn org.commonmark.ext.gfm.**
