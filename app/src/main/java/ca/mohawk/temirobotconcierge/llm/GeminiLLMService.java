package ca.mohawk.temirobotconcierge.llm;

import android.util.Log;

import okhttp3.OkHttpClient;

/**
 * Gemini LLM Service - Initializes connection to Google Gemini API
 */
public class GeminiLLMService {
    private static final String TAG = "GeminiLLMService";
    
    private static final String GEMINI_API_KEY = "AIzaSyC1fLlveeTo9Uth8ok3a5CnuPi3bRltods";
    private static final String MODEL = "gemini-1.5-flash";
    
    private OkHttpClient httpClient;
    
    public GeminiLLMService() {
        this.httpClient = new OkHttpClient();
    }
    
    public void shutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
        }
    }
}
