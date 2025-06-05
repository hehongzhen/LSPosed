-keep class de.robv.android.xposed.** {*;}
-keep class io.github.libinstalld.** {*;}
-keepattributes RuntimeVisibleAnnotations
-keep class android.** { *; }
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}
-keepclassmembers class org.lsposed.lspd.impl.LSPosedContext {
    public <methods>;
}
-keepclassmembers class org.lsposed.lspd.impl.LSPosedHookCallback {
    public <methods>;
}

-keep,allowoptimization,allowobfuscation @io.github.libinstalld.api.annotations.* class * {
    @io.github.libinstalld.api.annotations.BInvocation <methods>;
    @io.github.libinstalld.api.annotations.AInvocation <methods>;
}
-keepclassmembers class org.lsposed.lspd.impl.LSPosedBridge$NativeHooker {
    <init>(java.lang.reflect.Executable);
    callInstall(...);
}
-keepclassmembers class org.lsposed.lspd.impl.LSPosedBridge$HookerCallback {
    final *** beforeInvocation;
    final *** afterInvocation;
    HookerCallback(...);
}
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}
-repackageclasses dalvik.system2
-applymapping /Users/hehongzhen/Documents/Project/阿可/lsposed/lsposed/LSPosed/core/mapping.txt
-allowaccessmodification
-dontwarn org.slf4j.impl.StaticLoggerBinder
