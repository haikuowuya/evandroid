package resthttp;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.jdeferred.Deferred;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import com.facebook.stetho.okhttp.StethoInterceptor;

import resthttp.execption.ClientInputException;
import resthttp.execption.ServerException;

public class RestfulClient {
    private static OkHttpClient httpClient;

    private static FailCallback<Object> serverFailedHandler;

    public static OkHttpClient getInstance() {
        if (null == httpClient) {
            httpClient = new OkHttpClient();
            httpClient.networkInterceptors().add(new StethoInterceptor());

            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            CookieHandler.setDefault(cookieManager);
            httpClient.setCookieHandler(cookieManager);
        }
        return httpClient;
    }

    public static void registerServerFailedHandler(FailCallback<Object> handler) {
        serverFailedHandler = handler;
    }

    public static FailCallback<Object> getServerFailedHandler() {
        if (null == serverFailedHandler) {
            return serverFailedHandler = new FailCallback<Object>() {
                public void onFail(Object obj) {
                    if (!(obj instanceof ServerException)) {
                        return;
                    }
                    ((ServerException) obj).printStackTrace();
                }
            };
        }
        return serverFailedHandler;
    }

    private static Promise<Response, Object, Void> callByRequest(final Request request) {
        final Deferred<Response, Object, Void> deferred = new DeferredObject<>();
        Promise<Response, Object, Void> promise = deferred.promise();
        getInstance().newCall(request).enqueue(new Callback() {
            public void onFailure(Request request, IOException e) {
                deferred.reject(e);
            }

            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    deferred.resolve(response);
                    return;
                }

                int statusCode = response.code();
                if (statusCode >= 400 && statusCode < 500) {
                    deferred.reject(new ClientInputException("Client parameters invalid", response));
                    return;
                }

                deferred.reject(new ServerException("Server problems", response));
            }
        });

        //System default handle for server error
        promise.fail(getServerFailedHandler());

        return promise;
    }

    public static Promise<Response, Object, Void> promiseApiCall(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        return callByRequest(request);
    }

    public static Promise<Response, Object, Void> promiseApiCall(String method, String url, RequestBody body) {
        Request.Builder request = new Request.Builder();

        method = method.toUpperCase();
        switch (method) {
            case "POST":
                request.post(body);
                break;
            case "PUT":
                request.put(body);
                break;
            case "DELETE":
                request.delete(body);
                break;
        }
        return callByRequest(request.build());
    }

    public static Promise<Response, Object, Void>  promiseApiCall(final Request request) {
        return callByRequest(request);
    }
}
