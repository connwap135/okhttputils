package com.zhy.http.okhttp.callback;

/**
 * Created by JimGong on 2016/6/23.
 */
@SuppressWarnings("ALL")
public interface IGenericsSerializator {
    <T> T transform(String response, Class<T> classOfT);
}
