# 应用混淆规则（release 构建）
#
# 本项目为纯 Compose 应用：没有反射、没有 JNI、没有序列化框架。
# AndroidX / Compose 依赖自带 consumer 规则，已经覆盖运行时需要保留的类，
# 因此这里不额外添加 keep 规则，避免写出无效或过宽的白名单。
#
# 后续如果引入 Gson / Retrofit / Room / KSP 等依赖，请按其文档补充对应规则。

# 保留源码文件名与行号，让线上崩溃堆栈仍能对应到具体代码行
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
