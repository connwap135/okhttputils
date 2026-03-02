# okhttp-utils

>由于个人原因，现已停止维护。

对okhttp的封装类，okhttp见：[https://github.com/square/okhttp](https://github.com/square/okhttp).

当前仓库示例对应okhttp版本`5.2.0`.

## HTTP/3 (QUIC) 支持说明（Android）

`OkHttp 5.2.0`在 Android 上不能仅通过配置 `Protocol.HTTP_3` 直接获得可用的 HTTP/3 传输。
如果你需要在项目中启用 HTTP/3，推荐使用 Cronet 作为传输层桥接。

### 依赖配置（Gradle）

```gradle
implementation 'com.squareup.okhttp3:okhttp:5.2.0'
// 不要使用cronet-okhttp桥接库，它与OkHttp 5不兼容。
// 本项目提供了自定义的CronetInterceptor/CronetCallBridge，
// 直接依赖底层 Cronet 引擎即可：
implementation 'org.chromium.net:cronet-embedded:143.7445.0'
```

### 客户端启用示例（推荐：由 okhttputils 库统一接管）

在 `sample-okhttp` 应用中的主界面已添加一个 **HTTP3 Test** 按钮，点击即可发送示例请求并在 Logcat 中查看协商协议。


```java
OkHttpUtils.initClient(new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(new LoggerInterceptor("TAG"))
    .addInterceptor(new CronetInterceptor(
        Http3Engine.newBuilder(context).build(),
        cookieJar
    ))
);

// sample network call to verify HTTP/3 is illustrated in the sample app via
// the HTTP3 Test button; see MainActivity for implementation.
```

```java
// 白名单示例：支持任意端口、指定端口和通配符
Http3Engine cronetEngine = Http3Engine.newBuilder(context)
    .addHost("api.example.com")          // 443 或默认端口
    .addHost("api.example.com", 8443)    // 仅针对 8443
    .addHost("*.example.org")            // 通配域名，任意端口
    .build();

OkHttpUtils.initClient(new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(new LoggerInterceptor("TAG"))
        .addInterceptor(new CronetInterceptor(cronetEngine, cookieJar))
);
```

策略说明：

- `Http3Engine` 将包含在构造器中的主机作为“候选”。每个精确条目可指定端口，通配符
  条目仅匹配主机名，不区分端口。
- 构建时向 Cronet 注册 QUIC hint（主机+端口），确保第一次请求就尝试使用 HTTP/3；
  还会自动发送 HEAD 预热请求刷新 Alt-Svc 缓存。
- 非 HTTPS 或未命中规则的请求在拦截器中自动回退到普通 OkHttp。

### 关于 `CronetInterceptor`

- 它只是一个应用拦截器，判断 `Http3Engine.shouldUseCronet` 后决定是调用 Cronet 还是
  回退到 `chain.proceed()`。
- 日志采用中文格式，方便在 Logcat 观察流程（如“请求=… 是否走Cronet=true”）。
- 如果其他应用拦截器需要在网络前执行，将 `CronetInterceptor` 放在最末。

### 验证与测试

为了确保库按实际 API 正常工作，我们还提供了 Android 端的 instrumentation 测试。它们运行在模拟/真机上，
使用 `Http3Engine` 对域名匹配逻辑进行覆盖。命令如下：

```bash
./gradlew connectedAndroidTest -p okhttputils
```

测试结果可帮助你判断规则是否按预期执行，增强了库之于应用的可信度。

### 生产环境注意事项

- Cronet 路径会绕过 OkHttp 核心网络层的一部分能力（如缓存、重试、部分 network interceptor 行为）。
- WebSocket 不走 Cronet 传输桥接。
- 建议保留回退到普通 OkHttp 的策略，并在灰度环境验证协议命中、失败回退与证书配置行为。
- 注意不要动态改变 `Http3Engine` 规则——构建完成后规则不可变，需重新创建引擎。

## 用法

* Android Studio
	
	```
	compile 'com.zhy:okhttputils:2.6.2'
	```
	
* Eclipse
	
	下载最新jar:[okhttputils-2\_6\_2.jar](okhttputils-2_6_2.jar?raw=true)

	注：需要同时导入okhttp和okio的jar，下载见：[https://github.com/square/okhttp](https://github.com/square/okhttp).
	

## 目前对以下需求进行了封装
* 一般的get请求
* 一般的post请求
* 基于Http Post的文件上传（类似表单）
* 文件下载/加载图片
* 上传下载的进度回调
* 支持取消某个请求
* 支持自定义Callback
* 支持HEAD、DELETE、PATCH、PUT
* 支持session的保持
* 支持自签名网站https的访问，提供方法设置下证书就行

## 配置OkhttpClient

默认情况下，将直接使用okhttp默认的配置生成OkhttpClient，如果你有任何配置，记得在Application中调用`initClient`方法进行设置。

```java
public class MyApplication extends Application
{	
	@Override
    public void onCreate()
    {
        super.onCreate();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
//                .addInterceptor(new LoggerInterceptor("TAG"))
                  .connectTimeout(10000L, TimeUnit.MILLISECONDS)
                  .readTimeout(10000L, TimeUnit.MILLISECONDS)
                  //其他配置
                 .build();
                 
        OkHttpUtils.initClient(okHttpClient);

    }
}
```
别忘了在AndroidManifest中设置。

## 对于Cookie(包含Session)

对于cookie一样，直接通过cookiejar方法配置，参考上面的配置过程。

```
CookieJarImpl cookieJar = new CookieJarImpl(new PersistentCookieStore(getApplicationContext()));
OkHttpClient okHttpClient = new OkHttpClient.Builder()
          .cookieJar(cookieJar)
          //其他配置
         .build();
                 
OkHttpUtils.initClient(okHttpClient);
```
目前项目中包含：

* PersistentCookieStore //持久化cookie
* SerializableHttpCookie //持久化cookie
* MemoryCookieStore //cookie信息存在内存中

如果遇到问题，欢迎反馈，当然也可以自己实现CookieJar接口，编写cookie管理相关代码。

此外，对于持久化cookie还可以使用[https://github.com/franmontiel/PersistentCookieJar](https://github.com/franmontiel/PersistentCookieJar).

相当于框架中只是提供了几个实现类，你可以自行定制或者选择使用。

## 对于Log

初始化OkhttpClient时，通过设置拦截器实现，框架中提供了一个`LoggerInterceptor `，当然你可以自行实现一个Interceptor 。

```
 OkHttpClient okHttpClient = new OkHttpClient.Builder()
       .addInterceptor(new LoggerInterceptor("TAG"))
        //其他配置
        .build();
OkHttpUtils.initClient(okHttpClient);
```


## 对于Https

依然是通过配置即可，框架中提供了一个类`HttpsUtils`

* 设置可访问所有的https网站

```
HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory(null, null, null);
OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager)
         //其他配置
         .build();
OkHttpUtils.initClient(okHttpClient);
```

* 设置具体的证书

```
HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory(证书的inputstream, null, null);
OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager))
         //其他配置
         .build();
OkHttpUtils.initClient(okHttpClient);
```

* 双向认证

```
HttpsUtils.getSslSocketFactory(
	证书的inputstream, 
	本地证书的inputstream, 
	本地证书的密码)
```

同样的，框架中只是提供了几个实现类，你可以自行实现`SSLSocketFactory`，传入sslSocketFactory即可。

##其他用法示例

### GET请求

```java
String url = "http://www.csdn.net/";
OkHttpUtils
    .get()
    .url(url)
    .addParams("username", "hyman")
    .addParams("password", "123")
    .build()
    .execute(new StringCallback()
	        {
	            @Override
	            public void onError(Request request, Exception e)
	            {
	                
	            }
	
	            @Override
	            public void onResponse(String response)
	            {
	
	            }
	        });
```

### POST请求

```java
 OkHttpUtils
    .post()
    .url(url)
    .addParams("username", "hyman")
    .addParams("password", "123")
    .build()
    .execute(callback);

```

### Post JSON

```java
  OkHttpUtils
    .postString()
    .url(url)
    .content(new Gson().toJson(new User("zhy", "123")))
     .mediaType(MediaType.parse("application/json; charset=utf-8"))
    .build()
    .execute(new MyStringCallback());
```

提交一个Gson字符串到服务器端，注意：传递JSON的时候，不要通过addHeader去设置contentType，而使用`.mediaType(MediaType.parse("application/json; charset=utf-8"))`.。

### Post File

```java
 OkHttpUtils
	.postFile()
	.url(url)
	.file(file)
	.build()
	.execute(new MyStringCallback());
```
将文件作为请求体，发送到服务器。


### Post表单形式上传文件

```java
OkHttpUtils.post()//
    .addFile("mFile", "messenger_01.png", file)//
    .addFile("mFile", "test1.txt", file2)//
    .url(url)
    .params(params)//
    .headers(headers)//
    .build()//
    .execute(new MyStringCallback());
```

支持单个多个文件，`addFile`的第一个参数为文件的key，即类别表单中`<input type="file" name="mFile"/>`的name属性。

### 自定义CallBack

目前内部包含`StringCallBack`,`FileCallBack`,`BitmapCallback`，可以根据自己的需求去自定义Callback，例如希望回调User对象：

```java
public abstract class UserCallback extends Callback<User>
{
    @Override
    public User parseNetworkResponse(Response response) throws IOException
    {
        String string = response.body().string();
        User user = new Gson().fromJson(string, User.class);
        return user;
    }
}

 OkHttpUtils
    .get()//
    .url(url)//
    .addParams("username", "hyman")//
    .addParams("password", "123")//
    .build()//
    .execute(new UserCallback()
    {
        @Override
        public void onError(Request request, Exception e)
        {
            mTv.setText("onError:" + e.getMessage());
        }

        @Override
        public void onResponse(User response)
        {
            mTv.setText("onResponse:" + response.username);
        }
    });

```

通过`parseNetworkResponse `回调的response进行解析，该方法运行在子线程，所以可以进行任何耗时操作，详细参见sample。


### 下载文件

```java
 OkHttpUtils//
	.get()//
	.url(url)//
	.build()//
	.execute(new FileCallBack(Environment.getExternalStorageDirectory().getAbsolutePath(), "gson-2.2.1.jar")//
	{
	    @Override
	    public void inProgress(float progress)
	    {
	        mProgressBar.setProgress((int) (100 * progress));
	    }
	
	    @Override
	    public void onError(Request request, Exception e)
	    {
	        Log.e(TAG, "onError :" + e.getMessage());
	    }
	
	    @Override
	    public void onResponse(File file)
	    {
	        Log.e(TAG, "onResponse :" + file.getAbsolutePath());
	    }
	});
```

注意下载文件可以使用`FileCallback`，需要传入文件需要保存的文件夹以及文件名。


### 显示图片

```java
 OkHttpUtils
    .get()//
    .url(url)//
    .build()//
    .execute(new BitmapCallback()
    {
        @Override
        public void onError(Request request, Exception e)
        {
            mTv.setText("onError:" + e.getMessage());
        }

        @Override
        public void onResponse(Bitmap bitmap)
        {
            mImageView.setImageBitmap(bitmap);
        }
    });
```

显示图片，回调传入`BitmapCallback`即可。


### 上传下载的进度显示

```java
new Callback<T>()
{
    //...
    @Override
    public void inProgress(float progress)
    {
       //use progress: 0 ~ 1
    }
}
```

callback回调中有`inProgress `方法，直接复写即可。

### HEAD、DELETE、PUT、PATCH

```java

OkHttpUtils
     .put()//also can use delete() ,head() , patch()
     .requestBody(RequestBody.create(null, "may be something"))//
     .build()//
     .execute(new MyStringCallback());
```

如果需要requestBody，例如：PUT、PATCH，自行构造进行传入。



### 同步的请求

```
 Response response = OkHttpUtils
    .get()//
    .url(url)//
    .tag(this)//
    .build()//
    .execute();
```

execute方法不传入callback即为同步的请求，返回Response。


### 取消单个请求

```java
 RequestCall call = OkHttpUtils.get().url(url).build();
 call.cancel();
 
```

### 根据tag取消请求

目前对于支持的方法都添加了最后一个参数`Object tag`，取消则通过` OkHttpUtils.cancelTag(tag)`执行。

例如：在Activity中，当Activity销毁取消请求：

```
OkHttpUtils
    .get()//
    .url(url)//
    .tag(this)//
    .build()//

@Override
protected void onDestroy()
{
    super.onDestroy();
    //可以取消同一个tag的
    OkHttpUtils.cancelTag(this);//取消以Activity.this作为tag的请求
}
```
比如，当前Activity页面所有的请求以Activity对象作为tag，可以在onDestory里面统一取消。

## 混淆

```
#okhttputils
-dontwarn com.zhy.http.**
-keep class com.zhy.http.**{*;}


#okhttp
-dontwarn okhttp3.**
-keep class okhttp3.**{*;}


#okio
-dontwarn okio.**
-keep class okio.**{*;}


```






