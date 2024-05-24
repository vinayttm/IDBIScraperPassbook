package com.example.IDBIScraperPassbook.Repository;


import com.example.IDBIScraperPassbook.Utils.Config;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;

import okhttp3.*;

public class CheckUpiStatus {
    private static final String BASE_URL = Config.baseUrl;
    private OkHttpClient client = new OkHttpClient();

    public void checkUpiStatus(loginCallBack callback) {
        System.out.println("loginId " + Config.loginId);
        String apiURL = BASE_URL + "/GetUpiStatus?upiId=" + Config.loginId;
        Request request = new Request.Builder().url(apiURL).build();
        System.out.println("apiURL " + apiURL);
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.err.println("API Request Failed: " + e.getMessage());
                callback.onResult(true);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseData = response.body().string();
                        System.out.println("API responseData: " + responseData);
                        JsonObject output = new Gson().fromJson(responseData, JsonObject.class);
                        System.out.println("API Response: " + output);

                        if (output.has("Result") && output.get("Result").getAsInt() == 1) {
                            System.out.println("UPI Status: Active");
                            callback.onResult(true);
                        } else {
                            System.out.println("UPI Status: Inactive");
                            callback.onResult(false);
                        }
                    } else {
                        System.err.println("API Response Error: " + response.body().string());
                        callback.onResult(true);
                    }
                } catch (Exception ignored) {
                    callback.onResult(true);
                }
            }
        });
    }

    public interface loginCallBack {
        void onResult(boolean isSuccess);
    }
}


