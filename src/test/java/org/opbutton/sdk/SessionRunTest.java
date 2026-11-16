package org.opbutton.sdk;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.*;

public class SessionRunTest {
    @Test public void retriesAndApprovalPollKeepRunIdentityButNextSdkGetsAnother() throws Exception {
        List<Request> requests = new ArrayList<>();
        List<String> runs = new ArrayList<>();
        OkHttpClient transport = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            requests.add(request);
            if (request.body() != null) {
                Buffer body = new Buffer();
                request.body().writeTo(body);
                try { runs.add(new JSONObject(body.readUtf8()).getString("run_id")); }
                catch (Exception e) { throw new IOException(e); }
            }
            if (requests.size() == 1) throw new IOException("Connection dropped after request");
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                    .message("OK").body(ResponseBody.create("{\"request_id\":\"request\",\"status\":\"pending\"}",
                            MediaType.get("application/json"))).build();
        }).build();
        OpButtonHttpClient first = new OpButtonHttpClient("https://opbutton.invalid", "test", transport);
        OpButtonHttpClient next = new OpButtonHttpClient("https://opbutton.invalid", "test", transport);
        try {
            try {
                first.connect("phone", "Phone", Collections.emptyMap());
                fail("Expected simulated network failure");
            } catch (OpButtonException expected) { assertTrue(expected.isNetworkError()); }
            first.connect("phone", "Phone", Collections.emptyMap());
            first.pollStatus("request");
            next.connect("phone", "Phone", Collections.emptyMap());
            assertEquals(runs.get(0), runs.get(1));
            assertEquals(runs.get(0), requests.get(2).url().queryParameter("run_id"));
            assertNotEquals(runs.get(0), runs.get(2));
            for (String run : runs) assertEquals(run, UUID.fromString(run).toString());
            for (Request request : requests) assertEquals("test", request.header("X-Api-Token"));
        } finally { first.shutdown(); next.shutdown(); }
    }
}
