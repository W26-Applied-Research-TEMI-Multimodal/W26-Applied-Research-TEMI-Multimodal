package ca.mohawk.temirobotconcierge.llm;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Gemini LLM Service - Handles communication with Google Gemini API
 * Generates tour guide responses based on location context
 */
public class GeminiLLMService {
    private static final String TAG = "GeminiLLMService";
    
    private static final String GEMINI_API_KEY = "AIzaSyC1fLlveeTo9Uth8ok3a5CnuPi3bRltods";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    
    private OkHttpClient httpClient;
    
    /**
     * Callback interface for receiving Gemini responses
     */
    public interface ResponseCallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public GeminiLLMService() {
        this.httpClient = new OkHttpClient();
    }
    
    /**
     * Generate a tour guide response based on location metadata
     * Runs asynchronously on a background thread
     * 
     * @param prompt The prompt to send to Gemini (built from location metadata)
     * @param callback Called when response arrives or error occurs
     */
    public void generateTourGuide(String prompt, ResponseCallback callback) {
        new Thread(() -> {
            try {
                // Build JSON request body
                String jsonRequest = buildRequestJson(prompt);
                Log.d(TAG, "Sending request to Gemini: " + jsonRequest);
                
                // Create HTTP POST request
                Request request = new Request.Builder()
                    .url(API_URL + "?key=" + GEMINI_API_KEY)
                    .post(RequestBody.create(jsonRequest, MediaType.get("application/json")))
                    .build();
                
                // Send request and get response
                Response response = httpClient.newCall(request).execute();
                
                if (response.isSuccessful()) {
                    // Parse response JSON
                    String responseBody = response.body().string();
                    Log.d(TAG, "Gemini response: " + responseBody);
                    
                    // Extract generated text from response
                    String generatedText = parseGeminiResponse(responseBody);
                    
                    // Call callback with result
                    callback.onSuccess(generatedText);
                } else {
                    String error = "API request failed: " + response.code();
                    Log.e(TAG, error);
                    callback.onError(error);
                }
                
                response.close();
                
            } catch (Exception e) {
                Log.e(TAG, "Error calling Gemini API: " + e.getMessage());
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
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
