package com.zhy.http.okhttp.request;

import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.builder.PostFormBuilder;
import com.zhy.http.okhttp.callback.Callback;

import java.io.UnsupportedEncodingException;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Created by zhy on 15/12/14.
 */
@SuppressWarnings("ALL")
public class PostFormRequest extends OkHttpRequest {
    private List<PostFormBuilder.FileInput> files;

    public PostFormRequest(String url, Object tag, Map<String, String> params, Map<String, String> headers, List<PostFormBuilder.FileInput> files, int id) {
        super(url, tag, params, headers, id);
        this.files = files;
    }

    @Override
    protected RequestBody buildRequestBody() {
        if (files == null || files.isEmpty()) {
            FormBody.Builder builder = new FormBody.Builder();
            addParams(builder);
            return builder.build();
        } else {
            MultipartBody.Builder builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);
            addParams(builder);

            for (int i = 0; i < files.size(); i++) {
                PostFormBuilder.FileInput fileInput = files.get(i);
                RequestBody fileBody = RequestBody.create(fileInput.file, MediaType.parse(guessMimeType(fileInput.filename)));
                builder.addFormDataPart(fileInput.key, fileInput.filename, fileBody);
            }
            return builder.build();
        }
    }

    @Override
    protected RequestBody wrapRequestBody(RequestBody requestBody, final Callback callback) {
        if (callback == null) return requestBody;
        return new CountingRequestBody(requestBody, (bytesWritten, contentLength) -> OkHttpUtils.getInstance().getDelivery().execute(() -> callback.inProgress(bytesWritten * 1.0f / contentLength, contentLength, id)));
    }

    @Override
    protected Request buildRequest(RequestBody requestBody) {
        return builder.post(requestBody).build();
    }

    private String guessMimeType(String path) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String contentTypeFor = null;
        try {
            contentTypeFor = fileNameMap.getContentTypeFor(URLEncoder.encode(path, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (contentTypeFor == null) {
            contentTypeFor = "application/octet-stream";
        }
        return contentTypeFor;
    }

    private void addParams(MultipartBody.Builder builder) {
        if (params != null && !params.isEmpty()) {
            for (String key : params.keySet()) {
                String value = params.get(key);
                assert value != null;
                builder.addPart(Headers.of("Content-Disposition", "form-data; name=\"" + key + "\""),
                        RequestBody.create(value, null));
            }
        }
    }

    private void addParams(FormBody.Builder builder) {
        if (params != null) {
            for (String key : params.keySet()) {
                String value = params.get(key);
                assert value != null;
                builder.add(key, value);
            }
        }
    }

}
