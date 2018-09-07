package com.sample.common;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.IOException;
import java.security.GeneralSecurityException;


public class TestCommon {
    public void testHttpClientManager() {
        try {
            Registry<ConnectionSocketFactory> registry = HttpClientManager.createRegistry4Sys();
//                    "JKS",
//                    "c:/tomcat9/client/client.keystore", "123456");
            try (PoolingHttpClientConnectionManager pool1 = HttpClientManager.createDefaultPool(registry);) {
                HttpClient client1 = HttpClientManager.createHttpClient(pool1, HttpClientManager.createRequestConfig());
                //testHttpsRequest(client1, "https://localhost:8443/index.jsp");
                testHttpsRequest(client1, "https://www.baidu.com");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
//        try {
//            Registry<ConnectionSocketFactory> registry = HttpClientManager.createDefaultRegistry();
//            try (PoolingHttpClientConnectionManager pool2 = HttpClientManager.createDefaultPool(registry);) {
//                HttpClient client2 = HttpClientManager.createHttpClient(pool2, HttpClientManager.createRequestConfig());
//                testHttpsRequest(client2, "https://localhost:8443/index.jsp");
//                testHttpsRequest(client2, "https://www.baidu.com");
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        try {
//            Registry<ConnectionSocketFactory> registry = HttpClientManager.createNonCheckRegistry();
//            try (PoolingHttpClientConnectionManager pool3 = HttpClientManager.createDefaultPool(registry);) {
//                HttpClient client3 = HttpClientManager.createHttpClient(pool3, HttpClientManager.createRequestConfig());
//                testHttpsRequest(client3, "https://localhost:8443/index.jsp");
//                testHttpsRequest(client3, "https://www.baidu.com");
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    private boolean testHttpsRequest(HttpClient client, String url) {
        System.out.println("url:"+url);
        HttpGet      httpGet    = new HttpGet(url);
        try{
            HttpResponse response   = client.execute(httpGet);
            int          statusCode = response.getStatusLine().getStatusCode();
            System.out.println("StatusCode=>"+statusCode);
            return statusCode >= 200 && statusCode < 300;
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        (new TestCommon()).testHttpClientManager();
    }
}
