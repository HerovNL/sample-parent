package com.sample.common;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;


public class HttpClientManager {
    /**
     * Max connections in the pool
     */
    private static final int     MAX_TOTAL            = 128;
    /**
     * Max connections per route (IP:Port)
     */
    private static final int     MAX_PER_ROUTE        = 8;
    /**
     * Parameter for disable/enable Nagle algorithm: For data n has 500 bytes, data n+1 has 1 bytes, if tcpNoDelay=true,
     * data will be sent in 2 packets. Otherwise data may be merged after buffering. For interaction web application,
     * we suggest tcpNoDelay=true. In some scenario when sending large data traffic of 1 message, we suggest
     * tcpNoDelay=false and use sendFile() primitive which has zero copy.
     */
    private static final boolean TCP_NO_DELAY         = true;
    /**
     * Parameter for enable/disable port be reused by other process immediately after this process be killed.
     */
    private static final boolean SOCKET_REUSE_ADDRESS = false;
    /**
     * Timeout(s) for close TCP, it timeout not closed, RST message will be used to close connection.
     */
    private static final int     SOCKET_LINGER        = 60;
    /**
     * Parameter for enable/disable TCP heart-beat packets sending to keep the connection.
     */
    private static final boolean SOCKET_KEEP_ALIVE    = true;

    /**
     * Default timeout for getting connection from pool (ms)
     */
    private static final int CONNECTION_REQUEST_TIMEOUT = 500;
    /**
     * Default timeout for connect to route (ms)
     */
    private static final int CONNECTION_TIMEOUT         = 20000;
    /**
     * Default timeout for waiting response (ms),
     * such as configure 2000ms when average 1000ms delay from request sent to response received
     */
    private static final int SOCKET_TIMEOUT             = 20000;


    /**
     * Create registry for public (any sites will be trusted)
     */
    public static Registry<ConnectionSocketFactory> createNonCheckRegistry() throws GeneralSecurityException {
        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
        SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(sslContext, new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslFactory)
                .build();
        return registry;
    }

    /**
     * Create registry for sites included by trust store
     *
     * @param trustStoreType JKS(Default value),JCEKS(Recommended value),PKCS12,BKS,UBER
     * @param trustStorePath
     * @param password       password to enter trust store
     */
    public static Registry<ConnectionSocketFactory> createRegistry4Cer(String trustStoreType, String trustStorePath,
            String password) throws GeneralSecurityException, IOException {
        try (InputStream stream = new FileInputStream(trustStorePath)) {
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            trustStore.load(stream, password.toCharArray());
            SSLContext sslContext = SSLContexts.custom()
                                               .loadTrustMaterial(trustStore, new TrustSelfSignedStrategy())
                                               .build();
            SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(sslContext);
            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslFactory)
                    .build();
            return registry;
        }
    }

    /**
     * Create registry for sites trusted by system, or included by trust store
     *
     * @param trustStoreType JKS(Default value),JCEKS(Recommended value),PKCS12,BKS,UBER
     * @param trustStorePath
     * @param password       password to enter trust store
     */
    public static Registry<ConnectionSocketFactory> createRegistry4SysOrCer(String trustStoreType, String trustStorePath,
            String password) throws GeneralSecurityException, IOException {
        try (InputStream stream = new FileInputStream(trustStorePath)) {
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            trustStore.load(stream, password.toCharArray());
            SSLContext sslContext = SSLContexts.custom()
                                               .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                                               .loadKeyMaterial(trustStore, password.toCharArray())
                                               .build();
            SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(sslContext, new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });
            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslFactory)
                    .build();
            return registry;
        }
    }

    /**
     * Create registry for internet public sites
     *
     * @return registry will be used to create pool
     */
    public static Registry<ConnectionSocketFactory> createRegistry4Sys() {
        SSLContext sslContext = SSLContexts.createSystemDefault();
        SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(sslContext, new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslFactory)
                .build();
        return registry;
    }


    /**
     * @param registry       Connection socket factory
     * @param maxTotal       Max total connections in the pool
     * @param maxPerRoute    Max connections per route
     * @param tcpNoDelay     If true, data will be sent immediately without using socket buffer
     * @param soReuseAddress If true, when socket closed by current process, its port can be reused by other process
     *                       even not released
     * @param socketTimeout  Timeout for waiting data received
     * @param soLinger       If true, when closing socket, all data will be sent or wait this timeout (seconds)
     * @param soKeepAlive    If true, client will send idle packet to check server alive
     * @return Connection pool
     */
    public static PoolingHttpClientConnectionManager createPool(Registry<ConnectionSocketFactory> registry,
            int maxTotal, int maxPerRoute, boolean tcpNoDelay, boolean soReuseAddress, int socketTimeout,
            int soLinger, boolean soKeepAlive) {
        PoolingHttpClientConnectionManager manager = registry == null ? new PoolingHttpClientConnectionManager()
                : new PoolingHttpClientConnectionManager(registry);
        manager.setMaxTotal(maxTotal);
        manager.setDefaultMaxPerRoute(maxPerRoute);
        // For some route using specified amount to override default, use: manager.setMaxPerRoute(route,max);
        SocketConfig config = SocketConfig.custom()
                                          .setTcpNoDelay(tcpNoDelay)
                                          .setSoReuseAddress(soReuseAddress)
                                          .setSoTimeout(socketTimeout)
                                          .setSoLinger(soLinger)
                                          .setSoKeepAlive(soKeepAlive)
                                          .build();
        manager.setDefaultSocketConfig(config);
        // For some host using specified configure, use: manager.setConnectionConfig(host,connectionConfig);
        return manager;
    }

    /**
     * Create connection pool with default parameters
     */
    public static PoolingHttpClientConnectionManager createDefaultPool(Registry<ConnectionSocketFactory> registry) {
        return createPool(registry, MAX_TOTAL, MAX_PER_ROUTE,
                          TCP_NO_DELAY, SOCKET_REUSE_ADDRESS, SOCKET_TIMEOUT, SOCKET_LINGER, SOCKET_KEEP_ALIVE);
    }


    /**
     * Create request config by default parameters
     */
    public static RequestConfig createRequestConfig() {
        return RequestConfig.custom()
                            .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                            .setConnectTimeout(CONNECTION_TIMEOUT)
                            .setSocketTimeout(SOCKET_TIMEOUT).build();
    }

    /**
     * Create http client
     */
    public static HttpClient createHttpClient(PoolingHttpClientConnectionManager manager, RequestConfig requestConfig) {
        return HttpClients.custom()
                          .setConnectionManager(manager)
                          .setDefaultRequestConfig(requestConfig)
                          .build();
    }

}
