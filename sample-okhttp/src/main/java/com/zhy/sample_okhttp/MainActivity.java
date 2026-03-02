package com.zhy.sample_okhttp;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.BitmapCallback;
import com.zhy.http.okhttp.callback.FileCallBack;
import com.zhy.http.okhttp.callback.GenericsCallback;
import com.zhy.http.okhttp.callback.StringCallback;
import com.zhy.http.okhttp.cookie.CookieJarImpl;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.CookieJar;
import okhttp3.MediaType;
import okhttp3.Request;
import java.io.IOException;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity
{

    // 任意可访问的后台 API，原 192.168.31.242 已经不可用，这里作为演示可以换成自己的服务
    private final String mBaseUrl = "http://example.com/"; // placeholder, not used by HTTP3 tests

    private static final String TAG = "MainActivity";

    private TextView mTv;
    private TextView http3Result;
    private ImageView mImageView;
    private ProgressBar mProgressBar;


    public class MyStringCallback extends StringCallback
    {
        @Override
        public void onBefore(Request request, int id)
        {
            setTitle("loading...");
        }

        @Override
        public void onAfter(int id)
        {
            setTitle("Sample-okHttp");
        }

        @Override
        public void onError(Call call, Exception e, int id)
        {
            e.printStackTrace();
            mTv.setText("onError:" + e.getMessage());
        }

        @Override
        public void onResponse(String response, int id)
        {
            Log.e(TAG, "onResponse：complete");
            mTv.setText("onResponse:" + response);

            switch (id)
            {
                case 100:
                    Toast.makeText(MainActivity.this, "http", Toast.LENGTH_SHORT).show();
                    break;
                case 101:
                    Toast.makeText(MainActivity.this, "https", Toast.LENGTH_SHORT).show();
                    break;
            }
        }

        @Override
        public void inProgress(float progress, long total, int id)
        {
            Log.e(TAG, "inProgress:" + progress);
            mProgressBar.setProgress((int) (100 * progress));
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mTv = findViewById(R.id.id_textview);
        mImageView = findViewById(R.id.id_imageview);
        mProgressBar = findViewById(R.id.id_progress);
        mProgressBar.setMax(100);
        // http3 result view
        // http3 result view
        TextView http3Result = findViewById(R.id.http3_result);
        this.http3Result = http3Result;

        // 如果需要，可以在启动时立即执行一次 HTTP/3 测试
        // runHttp3Test();
    }

    public void getHtml(View view)
    {
        String url = "http://www.zhiyun-tech.com/App/Rider-M/changelog-zh.txt";
        url="http://www.391k.com/api/xapi.ashx/info.json?key=bd_hyrzjjfb4modhj&size=10&page=1";
        OkHttpUtils
                .get()
                .url(url)
                .id(100)
                .build()
                .execute(new MyStringCallback());
    }


    public void postString(View view)
    {
        String url = mBaseUrl + "user!postString";
        OkHttpUtils
                .postString()
                .url(url)
                .mediaType(MediaType.parse("application/json; charset=utf-8"))
                .content(new Gson().toJson(new User("zhy", "123")))
                .build()
                .execute(new MyStringCallback());

    }

    public void postFile(View view)
    {
        File file = new File(Environment.getExternalStorageDirectory(), "messenger_01.png");
        if (!file.exists())
        {
            Toast.makeText(MainActivity.this, "文件不存在，请修改文件路径", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = mBaseUrl + "user!postFile";
        OkHttpUtils
                .postFile()
                .url(url)
                .file(file)
                .build()
                .execute(new MyStringCallback());


    }

    public void getUser(View view)
    {
        String url = mBaseUrl + "user!getUser";
        OkHttpUtils
                .post()//
                .url(url)//
                .addParams("username", "hyman")//
                .addParams("password", "123")//
                .build()//
                .execute(new GenericsCallback<User>(new JsonGenericsSerializator())
                {
                    @Override
                    public void onError(Call call, Exception e, int id)
                    {
                        mTv.setText("onError:" + e.getMessage());
                    }

                    @Override
                    public void onResponse(User response, int id)
                    {
                        mTv.setText("onResponse:" + response.username);
                    }
                });
    }


    public void getUsers(View view)
    {
        Map<String, String> params = new HashMap<>();
        params.put("name", "zhy");
        String url = mBaseUrl + "user!getUsers";
        OkHttpUtils//
                .post()//
                .url(url)//
//                .params(params)//
                .build()//
                .execute(new ListUserCallback()//
                {
                    @Override
                    public void onError(Call call, Exception e, int id)
                    {
                        mTv.setText("onError:" + e.getMessage());
                    }

                    @Override
                    public void onResponse(List<User> response, int id)
                    {
                        mTv.setText("onResponse:" + response);
                    }
                });
    }


    public void getHttpsHtml(View view)
    {
        String url = "https://kyfw.12306.cn/otn/";

//                "https://12
//        url =3.125.219.144:8443/mobileConnect/MobileConnect/authLogin.action?systemid=100009&mobile=13260284063&pipe=2&reqtime=1422986580048&ispin=2";
        OkHttpUtils
                .get()//
                .url(url)//
                .id(101)
                .build()//
                .execute(new MyStringCallback());

    }

    public void getImage(View view)
    {
        mTv.setText("");
        String url = "http://images.csdn.net/20150817/1.jpg";
        OkHttpUtils
                .get()//
                .url(url)//
                .tag(this)//
                .build()//
                .connTimeOut(20000)
                .readTimeOut(20000)
                .writeTimeOut(20000)
                .execute(new BitmapCallback()
                {
                    @Override
                    public void onError(Call call, Exception e, int id)
                    {
                        mTv.setText("onError:" + e.getMessage());
                    }

                    @Override
                    public void onResponse(Bitmap bitmap, int id)
                    {
                        Log.e("TAG", "onResponse：complete");
                        mImageView.setImageBitmap(bitmap);
                    }
                });
    }


    public void uploadFile(View view)
    {

        File file = new File(Environment.getExternalStorageDirectory(), "messenger_01.png");
        if (!file.exists())
        {
            Toast.makeText(MainActivity.this, "文件不存在，请修改文件路径", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("username", "张鸿洋");
        params.put("password", "123");

        Map<String, String> headers = new HashMap<>();
        headers.put("APP-Key", "APP-Secret222");
        headers.put("APP-Secret", "APP-Secret111");

        String url = mBaseUrl + "user!uploadFile";

        OkHttpUtils.post()//
                .addFile("mFile", "messenger_01.png", file)//
                .url(url)//
                .params(params)//
                .headers(headers)//
                .build()//
                .execute(new MyStringCallback());
    }


    public void multiFileUpload(View view)
    {
        File file = new File(Environment.getExternalStorageDirectory(), "messenger_01.png");
        File file2 = new File(Environment.getExternalStorageDirectory(), "test1#.txt");
        if (!file.exists())
        {
            Toast.makeText(MainActivity.this, "文件不存在，请修改文件路径", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("username", "张鸿洋");
        params.put("password", "123");

        String url = mBaseUrl + "user!uploadFile";
        OkHttpUtils.post()//
                .addFile("mFile", "messenger_01.png", file)//
                .addFile("mFile", "test1.txt", file2)//
                .url(url)
                .params(params)//
                .build()//
                .execute(new MyStringCallback());
    }


    public void downloadFile(View view)
    {
        String url = "https://github.com/hongyangAndroid/okhttp-utils/blob/master/okhttputils-2_4_1.jar?raw=true";
        OkHttpUtils//
                .get()//
                .url(url)//
                .build()//
                .execute(new FileCallBack(Environment.getExternalStorageDirectory().getAbsolutePath(), "gson-2.2.1.jar")//
                {

                    @Override
                    public void onBefore(Request request, int id)
                    {
                    }

                    @Override
                    public void inProgress(float progress, long total, int id)
                    {
                        mProgressBar.setProgress((int) (100 * progress));
                        Log.e(TAG, "inProgress :" + (int) (100 * progress));
                    }

                    @Override
                    public void onError(Call call, Exception e, int id)
                    {
                        Log.e(TAG, "onError :" + e.getMessage());
                    }

                    @Override
                    public void onResponse(File file, int id)
                    {
                        Log.e(TAG, "onResponse :" + file.getAbsolutePath());
                    }
                });
    }


    public void otherRequestDemo(View view)
    {
        //also can use delete ,head , patch
        /*
        OkHttpUtils
                .put()//
                .url("http://11111.com")
                .requestBody
                        ("may be something")//
                .build()//
                .execute(new MyStringCallback());



        OkHttpUtils
                .head()//
                .url(url)
                .addParams("name", "zhy")
                .build()
                .execute();

       */


    }

    public void clearSession(View view)
    {
        CookieJar cookieJar = OkHttpUtils.getInstance().getOkHttpClient().cookieJar();
        if (cookieJar instanceof CookieJarImpl)
        {
            ((CookieJarImpl) cookieJar).getCookieStore().removeAll();
        }
    }

    public void http3Test(View view) {
        runHttp3Test();
    }

    // 额外测试：使用 httpbin.org 的 GET 接口，可以观察是否走 Cronet (HTTP/3)
    public void getTest(View view) {
        runGetTest();
    }

    // 额外测试：使用 httpbin.org 的 POST 接口
    public void postTest(View view) {
        runPostTest();
    }

    /** 测试 /api/jskc（分页查询） */
    public void postJskcTest(View view) {
        runPostJskcTest();
    }

    /** 测试 /Fender/BarCode（条码查询） */
    public void postBarCodeTest(View view) {
        runPostBarCodeTest();
    }

    /**
     * 将结果写入 UI，自动在前面加上当前协议（如果能够获取）。
     */
    private void setResultWithProtocol(String prefix, okhttp3.Response resp, String body) {
        String proto = resp != null ? resp.protocol().toString() : "?";
        String text = prefix + " (协议=" + proto + ")\n" + body;
        if (http3Result != null) {
            runOnUiThread(() -> http3Result.setText(text));
        }
    }

    private void runGetTest() {
        String url = "https://caraya.g127.com:9008/api/Token/NoAuth";
        OkHttpUtils.get()
                .url(url)
                .build()
                .execute(new com.zhy.http.okhttp.callback.Callback<String>() {
                    @Override
                    public String parseNetworkResponse(okhttp3.Response response, int id) throws Exception {
                        String body = response.body() != null ? response.body().string() : "";
                        setResultWithProtocol("GET 成功", response, body);
                        return body;
                    }

                    @Override
                    public void onError(Call call, Exception e, int id) {
                        Log.e(TAG, "GET test failed", e);
                        String msg = "GET 错误: " + e.getMessage();
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        if (http3Result != null) http3Result.setText(msg);
                    }

                    @Override
                    public void onResponse(String response, int id) {
                        // already handled in parseNetworkResponse
                    }
                });
    }

    private void runPostTest() {
        // 保留原有测试入口，复用 Jskc 逻辑
        runPostJskcTest();
    }

    /**
     * POST https://caraya.g127.com:9008/api/jskc
     * 以 JSON body 发送分页参数（.NET Web API 标准做法）。
     */
    private void runPostJskcTest() {
        final String url = "https://caraya.g127.com:9008/api/jskc";

        showResult("[Jskc] 正在请求...");

        OkHttpUtils.post()
                .url(url)
                .addParams("page", "2")
                .addParams("limit", "3")
                .build()
                .execute(new com.zhy.http.okhttp.callback.Callback<String>() {
                    @Override
                    public String parseNetworkResponse(okhttp3.Response response, int id) throws Exception {
                        String body = response.body() != null ? response.body().string() : "(empty body)";
                        Log.d(TAG, "[Jskc] HTTP " + response.code() + " proto=" + response.protocol());
                        Log.d(TAG, "[Jskc] body=" + body);
                        setResultWithProtocol("[Jskc] HTTP " + response.code(), response, body);
                        return body;
                    }

                    @Override
                    public void onError(Call call, Exception e, int id) {
                        Log.e(TAG, "[Jskc] POST failed", e);
                        showResult("[Jskc] 错误: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(String response, int id) { /* handled above */ }
                });
    }

    /**
     * POST https://caraya.g127.com:9008/Fender/BarCode
     */
    private void runPostBarCodeTest() {
        final String url = "https://caraya.g127.com:9008/Fender/BarCode";
        // 测试条码，根据实际接口文档修改字段名/值
        final String json = "{\"barCode\":\"TEST001\",\"page\":1,\"limit\":10}";

        showResult("[BarCode] 正在请求...");
        Log.d(TAG, "POST " + url + " body=" + json);

        OkHttpUtils.post()
                .url(url)
                .addParams("PageIndex", "1")
                .addParams("PageSize", "10")
                .build()
                .execute(new com.zhy.http.okhttp.callback.Callback<String>() {
                    @Override
                    public String parseNetworkResponse(okhttp3.Response response, int id) throws Exception {
                        String body = response.body() != null ? response.body().string() : "(empty body)";
                        Log.d(TAG, "[BarCode] HTTP " + response.code() + " proto=" + response.protocol());
                        Log.d(TAG, "[BarCode] body=" + body);
                        setResultWithProtocol("[BarCode] HTTP " + response.code(), response, body);
                        return body;
                    }

                    @Override
                    public void onError(Call call, Exception e, int id) {
                        Log.e(TAG, "[BarCode] POST failed", e);
                        showResult("[BarCode] 错误: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(String response, int id) { /* handled above */ }
                });
    }

    /** 线程安全地将文字写入结果 TextView。 */
    private void showResult(String text) {
        if (http3Result != null) {
            runOnUiThread(() -> http3Result.setText(text));
        }
    }

    private void runHttp3Test() {
        // 使用公用 HTTP/3 测试端点，允许任何人访问
        // Cloudflare 提供的示例页面会通过 HTTP/3 返回 200
        String testUrl = "https://cloudflare-quic.com/";

        OkHttpUtils.get()
                .url(testUrl)
                .build()
                .execute(new com.zhy.http.okhttp.callback.Callback<Void>() {
                    @Override
                    public Void parseNetworkResponse(okhttp3.Response response, int id) throws Exception {
                        okhttp3.Protocol proto = response.protocol();
                        String msg = "protocol=" + proto;
                        if (proto == okhttp3.Protocol.HTTP_3) {
                            msg += " (HTTP/3 negotiated)";
                        } else {
                            msg += " (fallback)";
                        }
                        Log.i("HTTP3Example", msg);
                        if (http3Result != null) {
                            final String finalMsg = msg;
                            runOnUiThread(() -> http3Result.setText(finalMsg));
                        }
                        return null; // no body needed
                    }

                    @Override
                    public void onError(okhttp3.Call call, Exception e, int id) {
                        Log.e("HTTP3Example", "request failed", e);
                        if (http3Result != null) {
                            runOnUiThread(() -> http3Result.setText("error: " + e.getMessage()));
                        }
                    }

                    @Override
                    public void onResponse(Void response, int id) {
                        // nothing to do here; protocol handled earlier
                    }
                });
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        OkHttpUtils.getInstance().cancelTag(this);
    }
}
