package com.example.memocare2;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SpeechTestActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 100;
    private static final long RECORD_TIME_MS = 60_000;

    // session
    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_USER_EMAIL = "user_email";

    // per-user results
    private static final String PREFS_RESULTS_BASE = "memocare_results_";

    // speech keys
    private static final String KEY_SPEECH_DONE = "speech_done";
    private static final String KEY_SPEECH_DURATION = "speech_duration_sec";
    private static final String KEY_SPEECH_TEXT = "speech_text";
    private static final String KEY_SPEECH_AI_SCORE = "speech_ai_score";

    private TextView tvTimer;
    private Button btnStart, btnStop, btnBackSpeech;

    private SpeechRecognizer speechRecognizer;
    private android.content.Intent recognizerIntent;
    private CountDownTimer recordTimer;

    private boolean isRecording = false;
    private long recordStartTime = 0L;

    private String lastRecognisedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_test);

        tvTimer = findViewById(R.id.tvTimer);
        btnStart = findViewById(R.id.btnStartRecording);
        btnStop = findViewById(R.id.btnStopRecording);
        btnBackSpeech = findViewById(R.id.btnBackSpeech);

        tvTimer.setText("Time left: 60s");

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is not available on this device.", Toast.LENGTH_LONG).show();
            btnStart.setEnabled(false);
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new SimpleRecognitionListener());

            recognizerIntent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        }

        btnStart.setOnClickListener(v -> {
            if (isRecording) return;

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQ_RECORD_AUDIO
                );
            } else {
                startRecording();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (!isRecording) {
                Toast.makeText(this, "No recording in progress.", Toast.LENGTH_SHORT).show();
            } else {
                stopRecording();
            }
        });

        btnBackSpeech.setOnClickListener(v -> finish());
    }

    private SharedPreferences getUserPrefs() {
        SharedPreferences session = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);
        String email = session.getString(KEY_USER_EMAIL, "");
        if (email == null) email = "";
        email = email.trim();
        if (email.isEmpty()) return null;

        String safeEmail = email.toLowerCase().replaceAll("[^a-z0-9_@.]", "_");
        return getSharedPreferences(PREFS_RESULTS_BASE + safeEmail, MODE_PRIVATE);
    }

    private void startRecording() {
        if (speechRecognizer == null) {
            Toast.makeText(this, "Speech recogniser is not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        lastRecognisedText = "";
        isRecording = true;
        recordStartTime = System.currentTimeMillis();

        btnStart.setText("Recording...");
        tvTimer.setText("Time left: 60s");

        speechRecognizer.startListening(recognizerIntent);

        if (recordTimer != null) recordTimer.cancel();
        recordTimer = new CountDownTimer(RECORD_TIME_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                tvTimer.setText("Time left: " + sec + "s");
            }

            @Override
            public void onFinish() {
                if (isRecording) stopRecording();
            }
        }.start();

        Toast.makeText(this, "Recording started. Please speak in English.", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        if (!isRecording) return;

        isRecording = false;

        if (recordTimer != null) {
            recordTimer.cancel();
            recordTimer = null;
        }

        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
        }

        btnStart.setText("Start recording");
        tvTimer.setText("Time left: 60s");

        long durationSec = 60;
        if (recordStartTime > 0) {
            long diff = (System.currentTimeMillis() - recordStartTime) / 1000;
            if (diff > 0 && diff <= 120) durationSec = diff;
        }

        SharedPreferences sp = getUserPrefs();
        if (sp == null) {
            Toast.makeText(this, "No user session. Please login.", Toast.LENGTH_SHORT).show();
            return;
        }

        sp.edit()
                .putBoolean(KEY_SPEECH_DONE, true)
                .putInt(KEY_SPEECH_DURATION, (int) durationSec)
                .putString(KEY_SPEECH_TEXT, lastRecognisedText)
                .putString(KEY_SPEECH_AI_SCORE, "")
                .apply();

        Toast.makeText(this, "Speech recorded. Sending to AI...", Toast.LENGTH_SHORT).show();
        callAiEvaluate(lastRecognisedText);
    }

    private void callAiEvaluate(String transcript) {
        if (transcript == null) transcript = "";
        transcript = transcript.trim();

        if (transcript.isEmpty()) {
            Toast.makeText(this, "No speech text to analyze.", Toast.LENGTH_SHORT).show();
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build();

        // keep your current AI endpoint for speech
        String url = "https://flask-ai-firebase.onrender.com/evaluate";

        JSONObject json = new JSONObject();
        try {
            json.put("transcript", transcript);
        } catch (JSONException e) {
            Toast.makeText(this, "JSON error", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder().url(url).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(SpeechTestActivity.this, "AI request failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String resBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    runOnUiThread(() ->
                            Toast.makeText(SpeechTestActivity.this, "AI error: " + response.code(), Toast.LENGTH_LONG).show()
                    );
                    return;
                }

                try {
                    JSONObject obj = new JSONObject(resBody);
                    String score = obj.optString("score", "");
                    if (score == null) score = "";

                    SharedPreferences sp = getUserPrefs();
                    if (sp != null) sp.edit().putString(KEY_SPEECH_AI_SCORE, score).apply();

                    String finalScore = score;
                    runOnUiThread(() -> {
                        if (!finalScore.isEmpty()) {
                            Toast.makeText(SpeechTestActivity.this, "AI score received: " + finalScore, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(SpeechTestActivity.this, "AI returned no score.", Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (JSONException e) {
                    runOnUiThread(() ->
                            Toast.makeText(SpeechTestActivity.this, "Bad JSON from AI.", Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recordTimer != null) recordTimer.cancel();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Microphone permission is needed for this test.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class SimpleRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            // don’t spam user if they pressed stop (common)
            if (isRecording) stopRecording();
            Toast.makeText(SpeechTestActivity.this,
                    "Speech error: " + error + ". Please try again in a quiet place.",
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) lastRecognisedText = matches.get(0);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) lastRecognisedText = matches.get(0);
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    }
}
