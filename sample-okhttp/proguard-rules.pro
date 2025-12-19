# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/zhy/android/sdk/android-sdk-macosx/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
#shrink，测试后发现会将一些无效代码给移除，即没有被显示调用的代码，该选项 表示 不启用 shrink。
-dontshrink
#指定重新打包,所有包重命名,这个选项会进一步模糊包名，将包里的类混淆成n个再重新打包到一个个的package中
-flattenpackagehierarchy
#优化时允许访问并修改有修饰符的类和类的成员
-allowaccessmodification
#混淆前后的映射
-printmapping map.txt
#不跳过(混淆) jars中的 非public classes   默认选项
-dontskipnonpubliclibraryclassmembers
#忽略警告
-ignorewarnings
#指定代码的压缩级别
-optimizationpasses 5
#不使用大小写混合类名
-dontusemixedcaseclassnames
#不去忽略非公共的库类
-dontskipnonpubliclibraryclasses
#不启用优化  不优化输入的类文件
-dontoptimize
#不预校验
-dontpreverify
#混淆时是否记录日志
-verbose
#混淆时所采用的算法
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
#保护注解
-renamesourcefileattribute SourceFile
#保持源文件和行号的信息,用于混淆后定位错误位置
-keepattributes SourceFile,LineNumberTable
-keepattributes SourceFile,LineNumberTable
#保持含有Annotation字符串的 attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions,InnerClasses

-dontwarn org.apache.**
-dontwarn android.support.**

#基础配置
# 保持哪些类不被混淆
# 系统组件
-keep public class * extends android.app.Fragment
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
#-keep public class com.android.vending.licensing.ILicensingService
#如果有引用v4包可以添加下面这行
#-keep public class * extends android.support.v4.app.Fragment
#自定义View
-keep public class * extends android.view.View
# V4,V7
-keep class android.support.v4.**{ *; }
-keep class android.support.v7.**{ *; }
-keep class android.webkit.**{*;}
-keep interface android.support.v4.app.** { *; }
#保持 本化方法及其类声明
-keepclasseswithmembers class * {
    native <methods>;
}
#保持view的子类成员： getter setter
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}
#保持Activity的子类成员：参数为一个View类型的方法   如setContentView(View v)
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}
#保持枚举类的成员：values方法和valueOf  (每个enum 类都默认有这两个方法)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
#保持Parcelable的实现类和它的成员：类型为android.os.Parcelable$Creator 名字任意的 属性
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
#保持 任意包名.R类的类成员属性。  即保护R文件中的属性名不变
-keepclassmembers class **.R$* {
    public static <fields>;
}

#MPermission
-dontwarn com.zhy.m.**
-keep class com.zhy.m.** {*;}
-keep interface com.zhy.m.** { *; }
-keep class **$$PermissionProxy { *; }

#okio
-dontwarn okio.**
-keep class okio.**{*;}
-keep interface okio.**{*;}

#okhttputils
-dontwarn com.zhy.http.**
-keep class com.zhy.http.**{*;}
-keep interface com.zhy.http.**{*;}

#okhttp
-dontwarn okhttp3.**
# 修改过于宽泛的keep规则，只保留必要的类和接口
-keep interface okhttp3.** {
    *;
}

# 更精确的规则，只保留必要的类和方法
-keep class okhttp3.OkHttpClient
-keep class okhttp3.Request
-keep class okhttp3.Response
-keep class okhttp3.Call
-keep class okhttp3.Callback
-keep class okhttp3.Headers
-keep class okhttp3.MediaType
-keep class okhttp3.RequestBody
-keep class okhttp3.ResponseBody

-keep interface okhttp3.Call
-keep interface okhttp3.Callback
-keep interface okhttp3.Interceptor

# 保留常用的内部类和Builder模式
-keep class okhttp3.Request$Builder
-keep class okhttp3.Response$Builder

# 保留主要异常类
-keep class okhttp3.HttpUrl$Builder
-keep class okhttp3.HttpUrl
-keep class okhttp3.Protocol
-keep class okhttp3.ConnectionSpec

# 保留枚举类
-keep class okhttp3.Credentials
-keep class okhttp3.Cache
-keep class okhttp3.Dispatcher
-keep class okhttp3.FormBody
-keep class okhttp3.MultipartBody

# 保留方法签名
-keepclasseswithmembernames class okhttp3.** {
    public <init>(...);
}

-keepclassmembers class okhttp3.** {
    public *;
}
