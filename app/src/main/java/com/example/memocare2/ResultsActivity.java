package com.example.memocare2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
import java.util.Locale;

public class ResultsActivity extends AppCompatActivity {

    private TextView tvLifestyleSummary, tvSpeechSummary, tvCogSummary;
    private TextView tvPremiumResults, tvNotCompletedHint;
    private Button btnPayUnlock, btnFinish;

    private ActivityResultLauncher<Intent> payLauncher;

    private static final String BASE_URL = "https://nms-backend-kr6f.onrender.com";

    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_PATIENT_ID = "patient_id";
    private static final String KEY_USER_EMAIL = "user_email";

    private static final String PREFS_RESULTS_BASE = "memocare_results_";

    private static final String KEY_LIFE_DONE = "life_done";
    private static final String KEY_LIFE_WEIGHT = "life_weight";
    private static final String KEY_LIFE_SMOKING = "life_smoking";
    private static final String KEY_LIFE_EDU = "life_edu";
    private static final String KEY_LIFE_SLEEP = "life_sleep";
    private static final String KEY_LIFE_DIET = "life_diet";
    private static final String KEY_LIFE_ACTIVITY = "life_activity";
    private static final String KEY_LIFE_DIABETIC = "life_diabetic";
    private static final String KEY_LIFE_FAMILY = "life_family";

    private static final String KEY_SPEECH_DONE = "speech_done";
    private static final String KEY_SPEECH_DURATION = "speech_duration_sec";

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
        tvSpeechSummary = findViewById(R.id.tvSpeechSummary);
        tvCogSummary = findViewById(R.id.tvCogSummary);
        tvPremiumResults = findViewById(R.id.tvPremiumResults);
        tvNotCompletedHint = findViewById(R.id.tvNotCompletedHint);

        btnPayUnlock = findViewById(R.id.btnPayUnlock);
        btnFinish = findViewById(R.id.btnFinish);

        SharedPreferences session = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);
        userEmail = n(session.getString(KEY_USER_EMAIL, ""));
        patientId = n(session.getString(KEY_PATIENT_ID, ""));

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
                        new Thread(this::markPaidAndUnlock).start();
                    }
                }
        );

        btnPayUnlock.setOnClickListener(v ->
                payLauncher.launch(new Intent(this, PaymentActivity.class)));

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
        String safe = email.toLowerCase().replaceAll("[^a-z0-9_@.]", "_");
        return getSharedPreferences(PREFS_RESULTS_BASE + safe, MODE_PRIVATE);
    }

    private void refreshFreeResults() {
        tvLifestyleSummary.setText(isLifeDone()
                ? "Lifestyle questionnaire:\nCompleted."
                : "Lifestyle questionnaire:\nNot completed yet.");

        tvSpeechSummary.setText(isSpeechDone()
                ? "Speech analysis:\nCompleted."
                : "Speech analysis:\nNot completed yet.");

        tvCogSummary.setText(isCogDone()
                ? "Cognitive test:\nCompleted."
                : "Cognitive test:\nNot completed yet.");

        if (!isLifeDone() && !isSpeechDone() && !isCogDone()) {
            tvNotCompletedHint.setVisibility(View.VISIBLE);
        } else {
            tvNotCompletedHint.setVisibility(View.GONE);
        }
    }

    private boolean isLifeDone() {
        SharedPreferences sp = getUserPrefs();
        return sp != null && sp.getBoolean(KEY_LIFE_DONE, false);
    }

    private boolean isSpeechDone() {
        SharedPreferences sp = getUserPrefs();
        return sp != null && sp.getBoolean(KEY_SPEECH_DONE, false);
    }

    private boolean isCogDone() {
        SharedPreferences sp = getUserPrefs();
        return sp != null && sp.getBoolean(KEY_COG_DONE, false);
    }

    private void checkPaidStatus() {
        try {
            URL url = new URL(BASE_URL + "/patients/" + patientId + "/paid_status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            InputStream is = conn.getResponseCode() >= 200 && conn.getResponseCode() < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            String resp = readAll(is);
            boolean paid = new JSONObject(resp).optBoolean("paid", false);

            runOnUiThread(() -> {
                if (paid) unlockPremium();
            });

        } catch (Exception ignored) {}
    }

    private void markPaidAndUnlock() {
        try {
            URL url = new URL(BASE_URL + "/patients/" + patientId + "/mark_paid");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write("{}".getBytes());
            }
            runOnUiThread(this::unlockPremium);
        } catch (Exception ignored) {}
    }

    private void unlockPremium() {
        String premium = buildPremiumText();

        if (premium.isEmpty()) {
            tvPremiumResults.setVisibility(View.GONE);
            return;
        }

        tvPremiumResults.setText(premium);
        tvPremiumResults.setVisibility(View.VISIBLE);
        btnPayUnlock.setVisibility(View.GONE);
        tvNotCompletedHint.setVisibility(View.GONE);
    }

    private String buildPremiumText() {
        StringBuilder sb = new StringBuilder();

        if (isLifeDone()) {
            String life = buildLifestylePremiumParagraph();
            if (!life.isEmpty()) {
                sb.append(life);
            }
        }

        if (isCogDone()) {
            String cog = buildCognitivePremiumParagraph();
            if (!cog.isEmpty()) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(cog);
            }
        }

        return sb.toString().trim();
    }

    private String buildLifestylePremiumParagraph() {
        SharedPreferences p = getUserPrefs();
        if (p == null) return "";

        String family = n(p.getString(KEY_LIFE_FAMILY, ""));
        String smoking = n(p.getString(KEY_LIFE_SMOKING, ""));
        String sleep = n(p.getString(KEY_LIFE_SLEEP, ""));
        String activity = n(p.getString(KEY_LIFE_ACTIVITY, ""));
        String diet = n(p.getString(KEY_LIFE_DIET, ""));
        String diabetic = n(p.getString(KEY_LIFE_DIABETIC, ""));
        String edu = n(p.getString(KEY_LIFE_EDU, ""));
        String weightStr = n(p.getString(KEY_LIFE_WEIGHT, ""));

        int weight = 0;
        try { weight = (int) Math.round(Double.parseDouble(weightStr.replace(",", "."))); } catch (Exception ignored) {}

        boolean familyYes = starts(family, "Yes");
        boolean currentSmoker = starts(smoking, "Current");
        boolean formerSmoker = starts(smoking, "Former");
        boolean poorSleep = starts(sleep, "Poor");
        boolean sedentary = starts(activity, "Sedentary");
        boolean diabeticYes = starts(diabetic, "Yes") || starts(diabetic, "1");
        boolean highWeight = weight >= 100;

        if (familyYes && currentSmoker && poorSleep && highWeight) {
            return "Several risk factors are present together, including family history, smoking, poor sleep, and higher body weight. This combination can increase long-term brain and cardiovascular risk. Focus first on sleep regularity and gradual weight reduction through daily walking and simple diet changes. Small consistent steps are more effective than strict plans.";
        }

        if (highWeight && sedentary && poorSleep) {
            return "Higher body weight combined with low activity and poor sleep may affect memory, attention, and long-term health. Improving sleep routine and adding daily movement (even 15 to 20 minutes) can support weight control and cognitive health.";
        }

        if (highWeight && diabeticYes) {
            return "Higher body weight together with diabetes can increase long-term health risks. Regular physical activity, stable meal timing, and gradual weight reduction may significantly support brain and metabolic health.";
        }

        if (highWeight && sedentary) {
            return "Higher body weight and low physical activity were reported. Increasing daily movement is the most effective first step. Regular walking, light workouts, and reducing long sitting periods can improve energy, mood, and cognitive performance.";
        }

        if (familyYes && currentSmoker) {
            return "Family history plus current smoking increases long-term risk. Reducing smoking and maintaining a healthy weight through regular movement and consistent sleep can strongly support brain health.";
        }

        if (familyYes) {
            return "A family history of dementia may increase risk, but lifestyle still matters. Prioritise sleep, regular activity, healthy weight management, and avoiding smoking to support long-term brain health.";
        }

        if (currentSmoker && poorSleep) {
            return "Smoking and poor sleep together can negatively affect memory and attention. Improving sleep routine and reducing smoking would make the biggest difference. Even small changes can have a positive impact over time.";
        }

        if (sedentary) {
            return "Low physical activity is the main area to improve. Increasing movement supports cognitive function, mood, and long-term brain health. Start with small daily goals and build consistency.";
        }

        if (poorSleep) {
            return "Sleep quality appears to be the main concern. Poor sleep can affect focus and memory. Keeping a regular sleep schedule and reducing late screen time can help improve cognitive performance.";
        }

        if (formerSmoker && !poorSleep && !sedentary && !highWeight) {
            return "Your lifestyle profile looks stable overall. Staying active, keeping sleep consistent, and maintaining a healthy weight will support long-term brain health.";
        }

        if (starts(edu, "No formal") && (poorSleep || sedentary || highWeight)) {
            return "A few risk factors are present. The best approach is building simple routines: regular sleep, daily movement, and healthy weight management. Small consistent changes usually work better than big short-term plans.";
        }

        if (starts(diet, "Balanced") && sedentary) {
            return "Your diet looks stable, but activity is low. Increasing movement is the easiest improvement. Regular walking or light workouts can support weight, mood, and cognitive health.";
        }

        if (starts(diet, "Low") && highWeight) {
            return "A low-carb style diet was reported, but higher body weight is still present. Consider focusing on consistency and portion control, plus daily activity, rather than strict restrictions. Long-term habits matter most.";
        }

        return "Your lifestyle profile suggests no major red flags. Maintaining regular sleep, physical activity, healthy weight, and a balanced diet will support long-term cognitive health.";
    }

    private String buildCognitivePremiumParagraph() {
        SharedPreferences p = getUserPrefs();
        if (p == null) return "";

        int memory = p.getInt(KEY_COG_MEMORY, 0);
        int total = p.getInt(KEY_COG_WORDS_TOTAL, 8);
        int hits = p.getInt(KEY_COG_HITS, 0);
        int misses = p.getInt(KEY_COG_MISSES, 0);
        int falseTaps = p.getInt(KEY_COG_FALSE, 0);
        long avgRt = p.getLong(KEY_COG_AVG_RT, -1);

        int targets = hits + misses;
        double hitRate = targets > 0 ? (hits * 100.0 / targets) : 0.0;

        String memLevel;
        if (memory <= 2) memLevel = "low";
        else if (memory <= 5) memLevel = "moderate";
        else memLevel = "good";

        String attLevel;
        boolean slow = avgRt >= 0 && avgRt > 800;
        boolean manyFalse = falseTaps >= 3;
        boolean lowAccuracy = targets > 0 && hitRate < 60.0;

        if (lowAccuracy || manyFalse) attLevel = "needs improvement";
        else if (slow) attLevel = "okay, but a bit slow";
        else attLevel = "good";

        StringBuilder sb = new StringBuilder();

        sb.append(String.format(Locale.ROOT,
                "Cognitive test summary: Memory %d/%d. Attention accuracy %.0f%% (Hits %d, Misses %d, False taps %d",
                memory, total, hitRate, hits, misses, falseTaps
        ));

        if (avgRt < 0) sb.append(", Avg reaction –).");
        else sb.append(String.format(Locale.ROOT, ", Avg reaction %d ms).", avgRt));

        if (memLevel.equals("low")) {
            sb.append(" Your memory score looks low for this short word task, which can happen with stress, distraction, or rushing. Try to slow down, focus on grouping words, and avoid multitasking.");
        } else if (memLevel.equals("moderate")) {
            sb.append(" Your memory score looks moderate. With a bit more focus and a steady pace, this could improve.");
        } else {
            sb.append(" Your memory score looks good. Keeping a steady pace and staying focused supports this result.");
        }

        if (attLevel.equals("needs improvement")) {
            sb.append(" Attention performance suggests more mistakes than expected. If you were distracted, tired, or tapping quickly, it can lower the score. Try repeating the test when rested and in a quiet place.");
        } else if (attLevel.equals("okay, but a bit slow")) {
            sb.append(" Attention accuracy looks fine, but reaction time is a bit slow. This can be normal if you are cautious. Practising short focus tasks can help speed up responses.");
        } else {
            sb.append(" Attention performance looks good with solid accuracy and control.");
        }

        sb.append(" Simple ways to improve: keep consistent sleep, take short daily walks, reduce distractions, and do small memory exercises (for example recalling a short shopping list or summarising a short text).");

        return sb.toString().trim();
    }

    private String n(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean starts(String full, String prefix) {
        if (full == null) return false;
        return full.trim().toLowerCase().startsWith(prefix.trim().toLowerCase());
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
