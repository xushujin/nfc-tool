# ---------------------------------------------------------------------------
# NFC工具 R8 规则
#
# 说明：本应用不使用反射式框架（无 Room/Hilt/Gson 等），因此不需要为其保留类。
# 下面只保留两类「必须存在」的东西：
#   1) NFC 相关系统类由框架提供，不参与混淆；
#   2) 被 AndroidManifest 引用的入口类（AGP 会自动保留，这里显式声明以便阅读）。
# ---------------------------------------------------------------------------

# 应用入口
-keep class com.nfctool.app.NfcToolApplication { *; }
-keep class com.nfctool.app.MainActivity { *; }

# 枚举的 values()/valueOf() 在 R8 下可能被裁剪，保留枚举完整语义
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 关闭混淆日志中的源码文件名，减少无谓的信息泄露
-renamesourcefileattribute SourceFile
