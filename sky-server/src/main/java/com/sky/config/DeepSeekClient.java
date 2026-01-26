package com.sky.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DeepSeekClient {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.api-url}")
    private String apiUrl;

    @Value("${deepseek.timeout.connect:10}")
    private int connectTimeoutSeconds;

    @Value("${deepseek.timeout.write:10}")
    private int writeTimeoutSeconds;

    @Value("${deepseek.timeout.read:30}")
    private int readTimeoutSeconds;

    private OkHttpClient client;

    @PostConstruct
    public void init() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    public String chat(String systemPrompt, String userPrompt) {
        JSONObject root = new JSONObject();
        root.put("model", "deepseek-chat");

        JSONArray messages = new JSONArray();
        messages.add(createMessage("system", systemPrompt));
        messages.add(createMessage("user", userPrompt));
        root.put("messages", messages);

        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");
        root.put("response_format", responseFormat);

        RequestBody body = RequestBody.create(
                root.toJSONString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new RuntimeException("AI接口异常: " + response);
            String result = response.body().string();
            return parseAiContent(result);
        } catch (IOException e) {
            log.error("AI 交互失败", e);
            return null;
        }
    }

    private JSONObject createMessage(String role, String content) {
        JSONObject msg = new JSONObject();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private String parseAiContent(String jsonResponse) {
        return JSON.parseObject(jsonResponse)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }
}

