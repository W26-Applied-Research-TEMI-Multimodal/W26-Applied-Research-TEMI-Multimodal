package ca.mohawk.temirobotconcierge.llm;

import android.util.Log;
import ca.mohawk.temirobotconcierge.BuildConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Gemini LLM Service - Handles communication with Google Gemini API
 * Generates tour guide responses based on location context
 */
public class GeminiLLMService {
    private static final String TAG = "GeminiLLMService";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    private OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Callback interface for receiving Gemini responses
     */
    public interface ResponseCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public GeminiLLMService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Generate a tour guide response based on location metadata
     * Runs asynchronously on a background thread
     * 
     * @param prompt The prompt to send to Gemini (built from location metadata)
     * @param callback Called when response arrives or error occurs
     */
    public void generateTourGuide(String prompt, ResponseCallback callback) {
        final String key = BuildConfig.GEMINI_API_KEY;

        if (key == null || key.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Gemini API key not configured."));
            return;
        }

        final String jsonRequest = buildRequestJson(prompt);

        Request request = new Request.Builder()
                .url(API_URL + "?key=" + key)
                .post(RequestBody.create(jsonRequest, MediaType.get("application/json")))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Gemini API call failed: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        String errBody = "";
                        ResponseBody body = r.body();
                        if (body != null) {
                            try {
                                errBody = body.string();
                            } catch (Exception ignored) { }
                        }

                        String msg = "API request failed: " + r.code();
                        if (errBody != null && !errBody.isEmpty()) {
                            msg += " (error body received)";
                        }

                        Log.e(TAG, msg);
                        String finalMsg = msg;
                        mainHandler.post(() -> callback.onError(finalMsg));
                        return;
                    }

                    ResponseBody body = r.body();
                    if (body == null) {
                        mainHandler.post(() -> callback.onError("Empty response from Gemini."));
                        return;
                    }

                    String responseBody = body.string();
                    String generatedText = parseGeminiResponse(responseBody);

                    mainHandler.post(() -> callback.onSuccess(generatedText));
                } catch (Exception e) {
                    Log.e(TAG, "Error handling Gemini response: " + e.getMessage(), e);
                    mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
                }
            }
        });
    }
    
    /**
     * Build JSON request body for Gemini API
     * Format: {"contents": [{"parts": [{"text": "..."}]}]}
     */
    private String buildRequestJson(String prompt) {
        JsonObject request = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        request.add("contents", contents);
        
        return request.toString();
    }
    
    /**
     * Parse Gemini API response and extract generated text
     * Response format: {"candidates": [{"content": {"parts": [{"text": "..."}]}}]}
     */
    private String parseGeminiResponse(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");
            
            if (candidates != null && candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                
                if (parts != null && parts.size() > 0) {
                    JsonObject textPart = parts.get(0).getAsJsonObject();
                    return textPart.get("text").getAsString();
                }
            }
            
            return "No response from Gemini";
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Gemini response: " + e.getMessage());
            return "Error parsing response: " + e.getMessage();
        }
    }
    
    public void shutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
        }
    }
}
