package com.example.memocare2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ResultsActivity extends AppCompatActivity {

    private TextView tvLifestyleSummary, tvSpeechSummary, tvCogSummary, tvPremiumResults;
    private Button btnPayUnlock, btnFinish;

    private ActivityResultLauncher<Intent> payLauncher;

    private static final String BASE_URL = "https://nms-backend-kr6f.onrender.com";

    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_PATIENT_ID = "patient_id";
    private static final String KEY_USER_EMAIL = "user_email";

    private static final String PREFS_RESULTS_BASE = "memocare_results_";


    private static final String KEY_LIFE_DONE = "life_done";
    private static final String KEY_LIFE_SMOKING = "life_smoking";
    private static final String KEY_LIFE_EDU = "life_edu";
    private static final String KEY_LIFE_SLEEP = "life_sleep";
    private static final String KEY_LIFE_DIET = "life_diet";
    private static final String KEY_LIFE_ACTIVITY = "life_activity";
    private static final String KEY_LIFE_DIABETIC = "life_diabetic";
    private static final String KEY_LIFE_FAMILY = "life_family";


    private static final String KEY_SPEECH_DONE = "speech_done";
    private static final String KEY_SPEECH_DURATION = "speech_duration_sec";
    private static final String KEY_SPEECH_TEXT = "speech_text";
    private static final String KEY_SPEECH_AI_SCORE = "speech_ai_score";


    private static final String KEY_COG_DONE = "cog_done";
    private static final String KEY_COG_MEMORY = "cog_memory_score";
    private static final String KEY_COG_WORDS_TOTAL = "cog_words_total";
    private static final String KEY_COG_HITS = "cog_hits";
    private static final String KEY_COG_MISSES = "cog_misses";
    private static final String KEY_COG_FALSE = "cog_false_taps";
    private static final String KEY_COG_AVG_RT = "cog_avg_rt_ms";

    private String patientId = "";
    private String userEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        tvLifestyleSummary = findViewById(R.id.tvLifestyleSummary);
        tvSpeechSummary    = findViewById(R.id.tvSpeechSummary);
        tvCogSummary       = findViewById(R.id.tvCogSummary);
        tvPremiumResults   = findViewById(R.id.tvPremiumResults);

        btnPayUnlock = findViewById(R.id.btnPayUnlock);
        btnFinish    = findViewById(R.id.btnFinish);

        SharedPreferences session = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);

        userEmail = session.getString(KEY_USER_EMAIL, "");
        if (userEmail == null) userEmail = "";
        userEmail = userEmail.trim();

        String fromIntent = getIntent().getStringExtra("patient_id");
        if (fromIntent != null && !fromIntent.trim().isEmpty()) {
            patientId = fromIntent.trim();
        } else {
            patientId = session.getString(KEY_PATIENT_ID, "");
            if (patientId == null) patientId = "";
            patientId = patientId.trim();
        }

        refreshFreeResults();

        tvPremiumResults.setVisibility(View.GONE);
        btnPayUnlock.setVisibility(View.VISIBLE);

        if (!patientId.isEmpty()) {
            new Thread(this::checkPaidStatus).start();
        }

        payLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (!patientId.isEmpty()) new Thread(this::markPaidAndUnlock).start();
                    } else {
                        Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        btnPayUnlock.setOnClickListener(v -> payLauncher.launch(new Intent(this, PaymentActivity.class)));
        btnFinish.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFreeResults();
    }

    private SharedPreferences getUserPrefs() {
        if (userEmail == null) userEmail = "";
        String email = userEmail.trim();
        if (email.isEmpty()) return null;
        String safeEmail = email.toLowerCase().replaceAll("[^a-z0-9_@.]", "_");
        return getSharedPreferences(PREFS_RESULTS_BASE + safeEmail, MODE_PRIVATE);
    }

    private void refreshFreeResults() {
        tvLifestyleSummary.setText(buildLifestyle());
        tvSpeechSummary.setText(buildSpeech());
        tvCogSummary.setText(buildCognitive());
    }

    private String buildSpeech() {
        SharedPreferences sp = getUserPrefs();
        if (sp == null) return "Speech analysis:\nNot completed yet.";

        boolean done = sp.getBoolean(KEY_SPEECH_DONE, false);
        if (!done) return "Speech analysis:\nNot completed yet.";

        int duration = sp.getInt(KEY_SPEECH_DURATION, 0);
        String text = sp.getString(KEY_SPEECH_TEXT, "");
        if (text == null) text = "";
        text = text.trim();

        String score = sp.getString(KEY_SPEECH_AI_SCORE, "");
        if (score == null) score = "";
        score = score.trim();

        String safeText = text.isEmpty() ? "(empty)" : text;
        if (safeText.length() > 180) safeText = safeText.substring(0, 180) + "...";

        return "Speech analysis:\n" +
                "• Duration: " + duration + "s\n";
    }

    private String buildCognitive() {
        SharedPreferences sp = getUserPrefs();
        if (sp == null) return "Cognitive test:\nNot completed yet.";

        boolean done = sp.getBoolean(KEY_COG_DONE, false);
        if (!done) return "Cognitive test:\nNot completed yet.";

        int totalWords = sp.getInt(KEY_COG_WORDS_TOTAL, 0);
        int memory = sp.getInt(KEY_COG_MEMORY, 0);
        int hits = sp.getInt(KEY_COG_HITS, 0);
        int misses = sp.getInt(KEY_COG_MISSES, 0);
        int falseTaps = sp.getInt(KEY_COG_FALSE, 0);
        long avgRt = sp.getLong(KEY_COG_AVG_RT, -1);

        String rtText = (avgRt < 0) ? "–" : (avgRt + " ms");

        return "Cognitive test:\n" +
                "• Memory: " + memory + "/" + totalWords + "\n" +
                "• Attention: Hits " + hits + ", Misses " + misses + ", False taps " + falseTaps + "\n" +
                "• Avg reaction: " + rtText;
    }

    private String buildLifestyle() {
        SharedPreferences prefs = getUserPrefs();
        if (prefs == null) return "Lifestyle questionnaire:\nNot completed yet.";

        boolean done = prefs.getBoolean(KEY_LIFE_DONE, false);
        if (!done) return "Lifestyle questionnaire:\nNot completed yet.";

        String smoking = n(prefs.getString(KEY_LIFE_SMOKING, ""));
        String sleep = n(prefs.getString(KEY_LIFE_SLEEP, ""));
        String activity = n(prefs.getString(KEY_LIFE_ACTIVITY, ""));
        String diabetic = n(prefs.getString(KEY_LIFE_DIABETIC, ""));
        String family = n(prefs.getString(KEY_LIFE_FAMILY, ""));
        String diet = n(prefs.getString(KEY_LIFE_DIET, ""));
        String edu = n(prefs.getString(KEY_LIFE_EDU, ""));

        int score = 0;

        if (starts(smoking, "Current smoker")) score += 2;
        else if (starts(smoking, "Former smoker")) score += 1;

        if (starts(sleep, "Poor")) score += 2;

        if (starts(activity, "Sedentary")) score += 2;
        else if (starts(activity, "Light")) score += 1;

        if (starts(diet, "Low-carb")) score += 1;

        if (starts(diabetic, "Yes")) score += 2;
        if (starts(family, "Yes")) score += 2;

        if (starts(diabetic, "Yes") && starts(activity, "Sedentary")) {
            return "Lifestyle questionnaire:\nDiabetes + low activity increases overall health risk. Try adding daily walking (even 20 minutes) and keeping regular meals to support blood sugar and long-term brain health.";
        }

        if (starts(diabetic, "Yes")) {
            return "Lifestyle questionnaire:\nDiabetes was reported, which can raise long-term health risk. Keeping good sleep, regular movement, and a balanced diet are strong protective steps.";
        }

        if (starts(family, "Yes") && starts(smoking, "Current smoker")) {
            return "Lifestyle questionnaire:\nFamily history plus current smoking is a higher-risk combination. The biggest improvement would be reducing/quit smoking and keeping consistent sleep + movement habits.";
        }

        if (starts(family, "Yes")) {
            return "Lifestyle questionnaire:\nA family history of dementia may increase risk, but lifestyle still matters. Prioritise sleep, regular activity, and avoiding smoking to support brain health.";
        }

        if (starts(smoking, "Current smoker") && starts(sleep, "Poor")) {
            return "Lifestyle questionnaire:\nSmoking and poor sleep together can negatively affect memory, attention and long-term health. Improving sleep routine and reducing smoking would make the biggest difference.";
        }

        if (starts(sleep, "Poor")) {
            return "Lifestyle questionnaire:\nSleep quality looks like the main concern. Poor sleep can affect focus and memory. Try a consistent bedtime/wake time and reduce screens/caffeine late in the day.";
        }

        if (starts(activity, "Sedentary") && starts(diet, "Balanced")) {
            return "Lifestyle questionnaire:\nYour diet looks okay, but activity is low. Increasing movement is the easiest win—regular walks or light workouts can improve mood, energy and cognitive health.";
        }

        if (starts(activity, "Sedentary")) {
            return "Lifestyle questionnaire:\nLow physical activity is the biggest risk factor here. Even small changes (stairs, short walks, stretching) can help over time.";
        }

        if (starts(smoking, "Former smoker") && starts(activity, "Moderate")) {
            return "Lifestyle questionnaire:\nGood progress—being a former smoker plus moderate activity is a positive combination. Keep sleep consistent and stay active to maintain a healthy baseline.";
        }

        if (starts(edu, "No formal") && score >= 3) {
            return "Lifestyle questionnaire:\nA few risk factors are present. The best approach is simple routine habits: daily movement, stable sleep, and avoiding smoking—small consistent steps help most.";
        }

        if (score <= 1) {
            return "Lifestyle questionnaire:\nYour answers suggest a healthy baseline with no strong red flags. Keep consistency: sleep, movement, balanced diet and avoiding smoking.";
        } else if (score <= 3) {
            return "Lifestyle questionnaire:\nMostly stable lifestyle, with a few areas to improve (sleep, activity or diet). Small changes can make a noticeable difference over time.";
        } else if (score <= 5) {
            return "Lifestyle questionnaire:\nYour answers show multiple risk factors. This doesn’t mean a condition is present—just that prevention matters. Start with one change (sleep or movement) and build up.";
        } else {
            return "Lifestyle questionnaire:\nSeveral risk factors are present together. Focus on prevention steps (sleep, activity, quitting smoking) and consider discussing risk factors with a healthcare professional if concerned.";
        }
    }

    private String n(String s) { return s == null ? "" : s.trim(); }
    private boolean starts(String full, String prefix) {
        if (full == null) return false;
        return full.trim().toLowerCase().startsWith(prefix.trim().toLowerCase());
    }

    private void checkPaidStatus() {
        try {
            URL url = new URL(BASE_URL + "/patients/" + patientId + "/paid_status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            String resp = readAll((code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream());
            conn.disconnect();

            if (code >= 200 && code < 300) {
                JSONObject obj = new JSONObject(resp);
                boolean paid = obj.optBoolean("paid", false);

                runOnUiThread(() -> {
                    if (paid) unlockPremium();
                    else {
                        tvPremiumResults.setVisibility(View.GONE);
                        btnPayUnlock.setVisibility(View.VISIBLE);
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void markPaidAndUnlock() {
        try {
            URL url = new URL(BASE_URL + "/patients/" + patientId + "/mark_paid");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write("{}".getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            String resp = readAll((code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream());
            conn.disconnect();

            runOnUiThread(() -> {
                if (code >= 200 && code < 300) {
                    unlockPremium();
                    Toast.makeText(this, "Payment saved ", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to save payment (HTTP " + code + "): " + resp, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            runOnUiThread(() ->
                    Toast.makeText(this, "Payment save failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }

    private void unlockPremium() {
        tvPremiumResults.setText(
                "Full AI Results are pending\n"
        );
        tvPremiumResults.setVisibility(View.VISIBLE);
        btnPayUnlock.setVisibility(View.GONE);
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
