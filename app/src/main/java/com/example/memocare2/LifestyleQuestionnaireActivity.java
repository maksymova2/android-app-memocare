package com.example.memocare2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class LifestyleQuestionnaireActivity extends AppCompatActivity {

    private EditText etAge, etWeight;
    private RadioGroup rgGender, rgHand, rgSmoking, rgEducation, rgSleepQuality,
            rgDiet, rgActivity, rgDiabetic, rgFamily;

    private Button btnSubmitLifestyle, btnBackLifestyle;

    private static final String AI_BASE_URL = "https://questionare.onrender.com";

    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_USER_EMAIL = "user_email";

    private static final String PREFS_RESULTS_BASE = "memocare_results_";

    private static final String KEY_LIFE_DONE = "life_done";
    private static final String KEY_LIFE_AGE = "life_age";
    private static final String KEY_LIFE_WEIGHT = "life_weight";
    private static final String KEY_LIFE_GENDER = "life_gender";
    private static final String KEY_LIFE_HAND = "life_hand";
    private static final String KEY_LIFE_SMOKING = "life_smoking";
    private static final String KEY_LIFE_EDU = "life_edu";
    private static final String KEY_LIFE_SLEEP = "life_sleep";
    private static final String KEY_LIFE_DIET = "life_diet";
    private static final String KEY_LIFE_ACTIVITY = "life_activity";
    private static final String KEY_LIFE_DIABETIC = "life_diabetic";
    private static final String KEY_LIFE_FAMILY = "life_family";
    private static final String KEY_LIFE_AI_SCORE = "life_ai_score";

    private String userEmail = "";

    private static final String[] START_PATHS = new String[]{
            "/startassment",
            "/startassessment",
            "/start_assessment",
            "/start_assesment"
    };

    private static final String[] PREFIXES = new String[]{
            "",
            "/startassment",
            "/startassessment",
            "/start_assessment",
            "/start_assesment"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lifestyle_questionnaire);

        SharedPreferences session = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);
        userEmail = session.getString(KEY_USER_EMAIL, "");
        if (userEmail == null) userEmail = "";
        userEmail = userEmail.trim();

        etAge = findViewById(R.id.etLifeAge);
        etWeight = findViewById(R.id.etLifeWeight);

        rgGender = findViewById(R.id.rgLifeGender);
        rgHand = findViewById(R.id.rgLifeHand);
        rgSmoking = findViewById(R.id.rgLifeSmoking);
        rgEducation = findViewById(R.id.rgLifeEducation);
        rgSleepQuality = findViewById(R.id.rgLifeSleepQuality);
        rgDiet = findViewById(R.id.rgLifeDiet);
        rgActivity = findViewById(R.id.rgLifeActivity);
        rgDiabetic = findViewById(R.id.rgLifeDiabetic);
        rgFamily = findViewById(R.id.rgLifeFamilyDementia);

        btnSubmitLifestyle = findViewById(R.id.btnSubmitLifestyle);
        btnBackLifestyle = findViewById(R.id.btnBackLifestyle);

        btnSubmitLifestyle.setOnClickListener(v -> {

            if (TextUtils.isEmpty(userEmail)) {
                Toast.makeText(this, "No logged-in user found. Please login again.", Toast.LENGTH_LONG).show();
                return;
            }

            String ageStr = safe(etAge.getText().toString());
            String weightStr = safe(etWeight.getText().toString()).replace(",", ".");

            // ✅ tag if exists, otherwise map from UI text (safe fallback)
            String gender = getSelectedRadioValueMapped(rgGender);
            String hand = getSelectedRadioValueMapped(rgHand);
            String smoking = getSelectedRadioValueMapped(rgSmoking);
            String education = getSelectedRadioValueMapped(rgEducation);
            String sleep = getSelectedRadioValueMapped(rgSleepQuality);
            String diet = getSelectedRadioValueMapped(rgDiet);
            String activity = getSelectedRadioValueMapped(rgActivity);
            String diabetic = getSelectedRadioValueMapped(rgDiabetic);
            String family = getSelectedRadioValueMapped(rgFamily);

            if (TextUtils.isEmpty(ageStr) || TextUtils.isEmpty(weightStr)
                    || gender == null || hand == null || smoking == null || education == null
                    || sleep == null || diet == null || activity == null
                    || diabetic == null || family == null) {

                Toast.makeText(this, "Please answer all questions", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!TextUtils.isDigitsOnly(ageStr)) {
                Toast.makeText(this, "Age must be a number", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidWeight(weightStr)) {
                Toast.makeText(this, "Weight must be a number", Toast.LENGTH_SHORT).show();
                return;
            }

            saveLifestyleToPrefsForUser(userEmail, ageStr, weightStr, gender, hand, smoking, education,
                    sleep, diet, activity, diabetic, family);

            btnSubmitLifestyle.setEnabled(false);

            sendLifestyleToNewBackend(ageStr, gender, weightStr, hand, smoking, education,
                    sleep, diet, activity, diabetic, family);
        });

        btnBackLifestyle.setOnClickListener(v -> finish());
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    // ✅ Uses tag if present; if not present, maps UI text to backend EXACT options
    private String getSelectedRadioValueMapped(RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == -1) return null;

        RadioButton rb = findViewById(id);

        Object tag = rb.getTag();
        if (tag != null && !tag.toString().trim().isEmpty()) {
            return tag.toString().trim();
        }

        String ui = rb.getText() == null ? "" : rb.getText().toString().trim();
        if (ui.isEmpty()) return null;

        String low = ui.toLowerCase();
        int gid = group.getId();

        // Gender
        if (gid == R.id.rgLifeGender) {
            if (low.contains("male")) return "Male";
            if (low.contains("female")) return "Female";
        }

        // Hand
        if (gid == R.id.rgLifeHand) {
            if (low.contains("left")) return "Left";
            if (low.contains("right")) return "Right";
        }

        // Smoking
        if (gid == R.id.rgLifeSmoking) {
            if (low.contains("never")) return "Never Smoked";
            if (low.contains("former") || low.contains("quit")) return "Former Smoker";
            if (low.contains("current")) return "Current Smoker";
        }

        // Education
        if (gid == R.id.rgLifeEducation) {
            if (low.contains("no")) return "No School";
            if (low.contains("primary")) return "Primary School";
            if (low.contains("secondary")) return "Secondary School";
            if (low.contains("diploma") || low.contains("degree") || low.contains("college") || low.contains("university"))
                return "Diploma/Degree";
        }

        // Sleep
        if (gid == R.id.rgLifeSleepQuality) {
            if (low.contains("poor")) return "Poor";
            if (low.contains("good")) return "Good";
        }

        // Diet
        if (gid == R.id.rgLifeDiet) {
            if (low.contains("mediterranean")) return "Mediterranean Diet";
            if (low.contains("balanced")) return "Balanced Diet";
            if (low.contains("low")) return "Low-Carb Diet";
        }

        // Activity
        if (gid == R.id.rgLifeActivity) {
            if (low.contains("sedentary")) return "Sedentary";
            if (low.contains("light") || low.contains("mild")) return "Mild Activity";
            if (low.contains("moderate")) return "Moderate Activity";
        }

        // Diabetic
        if (gid == R.id.rgLifeDiabetic) {
            if (low.startsWith("yes") || low.contains("yes")) return "1";
            if (low.startsWith("no") || low.contains("no")) return "0";
        }

        // Family history
        if (gid == R.id.rgLifeFamilyDementia) {
            if (low.startsWith("yes") || low.contains("yes")) return "Yes";
            // "No / Not sure" -> backend only supports "No"
            if (low.startsWith("no") || low.contains("not sure") || low.contains("no")) return "No";
        }

        // If we got here -> not mappable
        return null;
    }

    private boolean isValidWeight(String s) {
        if (TextUtils.isEmpty(s)) return false;
        s = s.replace(",", ".");
        try {
            Double.parseDouble(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SharedPreferences getUserPrefs(String email) {
        if (email == null) email = "";
        email = email.trim();
        if (email.isEmpty()) return null;

        String safeEmail = email.toLowerCase().replaceAll("[^a-z0-9_@.]", "_");
        return getSharedPreferences(PREFS_RESULTS_BASE + safeEmail, MODE_PRIVATE);
    }

    private void saveLifestyleToPrefsForUser(String email,
                                             String ageStr, String weightStr,
                                             String gender, String hand, String smoking, String education,
                                             String sleepQuality, String diet, String activity,
                                             String diabetic, String family) {

        SharedPreferences prefs = getUserPrefs(email);
        if (prefs == null) return;

        prefs.edit()
                .putBoolean(KEY_LIFE_DONE, true)
                .putString(KEY_LIFE_AGE, ageStr)
                .putString(KEY_LIFE_WEIGHT, weightStr)
                .putString(KEY_LIFE_GENDER, gender)
                .putString(KEY_LIFE_HAND, hand)
                .putString(KEY_LIFE_SMOKING, smoking)
                .putString(KEY_LIFE_EDU, education)
                .putString(KEY_LIFE_SLEEP, sleepQuality)
                .putString(KEY_LIFE_DIET, diet)
                .putString(KEY_LIFE_ACTIVITY, activity)
                .putString(KEY_LIFE_DIABETIC, diabetic)
                .putString(KEY_LIFE_FAMILY, family)
                .putString(KEY_LIFE_AI_SCORE, "")
                .apply();
    }

    private void sendLifestyleToNewBackend(String age,
                                           String gender,
                                           String weightKg,
                                           String hand,
                                           String smoking,
                                           String education,
                                           String sleepQuality,
                                           String diet,
                                           String activity,
                                           String diabetic,
                                           String familyHistory) {

        new Thread(() -> {
            String score = "";
            String errorMsg = "";

            try {
                String startResp = null;
                String usedStartPath = null;

                for (String p : START_PATHS) {
                    HttpResult rr = httpGetWithCode(AI_BASE_URL + p);
                    Log.d("LifestyleAI", "TRY start: " + (AI_BASE_URL + p) + " -> " + rr.code);
                    if (rr.code >= 200 && rr.code < 300) {
                        startResp = rr.body;
                        usedStartPath = p;
                        break;
                    }
                }

                if (TextUtils.isEmpty(startResp)) {
                    throw new Exception("All /start_* endpoints failed. Backend route name is different.");
                }

                String id = extractId(startResp);
                if (TextUtils.isEmpty(id)) {
                    throw new Exception("Could not parse assessment id. Response: " + startResp);
                }

                setWithPrefixes("set_age", id, age);
                setWithPrefixes("set_gender", id, gender);
                setWithPrefixes("set_weight", id, weightKg);
                setWithPrefixes("set_dominant_hand", id, hand);
                setWithPrefixes("set_smoking_status", id, smoking);
                setWithPrefixes("set_education", id, education);
                setWithPrefixes("set_sleep", id, sleepQuality);
                setWithPrefixes("set_diet", id, diet);
                setWithPrefixes("set_physical_activity", id, activity);
                setWithPrefixes("set_diabetic", id, diabetic);
                setWithPrefixes("set_family_history", id, familyHistory);

                String evalResp = evalWithPrefixes(id);
                score = extractScore(evalResp);
                if (score == null) score = "";
                score = score.trim();

                Log.d("LifestyleAI", "usedStartPath=" + usedStartPath);
                Log.d("LifestyleAI", "startResp=" + startResp);
                Log.d("LifestyleAI", "id=" + id);
                Log.d("LifestyleAI", "evalResp=" + evalResp);

            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            String finalScore = score;
            String finalError = errorMsg;

            runOnUiThread(() -> {
                btnSubmitLifestyle.setEnabled(true);

                if (!TextUtils.isEmpty(finalScore)) {
                    SharedPreferences prefs = getUserPrefs(userEmail);
                    if (prefs != null) {
                        prefs.edit().putString(KEY_LIFE_AI_SCORE, finalScore).apply();
                    }

                    Toast.makeText(this, "Lifestyle saved (AI score: " + finalScore + ")", Toast.LENGTH_LONG).show();
                    Intent data = new Intent();
                    data.putExtra("lifestyle_ai_score", finalScore);
                    setResult(RESULT_OK, data);
                    finish();
                } else {
                    String msg = (finalError == null) ? "" : finalError;
                    if (msg.length() > 180) msg = msg.substring(0, 180);
                    Toast.makeText(this,
                            "Lifestyle saved ✅ (AI unavailable): " + msg,
                            Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                }
            });

        }).start();
    }

    private void setWithPrefixes(String setterName, String id, String value) throws Exception {
        String encodedId = encodePath(id);
        String encodedVal = encodePath(value);

        HttpResult last = null;
        for (String pre : PREFIXES) {
            String full = AI_BASE_URL + pre + "/" + setterName + "/" + encodedId + "/" + encodedVal;
            HttpResult r = httpGetWithCode(full);
            Log.d("LifestyleAI", "TRY " + setterName + ": " + full + " -> " + r.code);
            last = r;
            if (r.code >= 200 && r.code < 300) return;
        }

        throw new Exception(setterName + " failed. Last: HTTP " + (last == null ? "?" : last.code)
                + " " + (last == null ? "" : last.body));
    }

    private String evalWithPrefixes(String id) throws Exception {
        String encodedId = encodePath(id);

        HttpResult last = null;
        for (String pre : PREFIXES) {
            String full = AI_BASE_URL + pre + "/start_eval/" + encodedId;
            HttpResult r = httpGetWithCode(full);
            Log.d("LifestyleAI", "TRY eval: " + full + " -> " + r.code);
            last = r;
            if (r.code >= 200 && r.code < 300) return r.body;
        }

        throw new Exception("start_eval failed. Last: HTTP " + (last == null ? "?" : last.code)
                + " " + (last == null ? "" : last.body));
    }

    private String encodePath(String s) throws Exception {
        String out = URLEncoder.encode(s, "UTF-8");
        out = out.replace("+", "%20");
        return out;
    }

    private static class HttpResult {
        int code;
        String body;
        HttpResult(int c, String b) { code = c; body = b; }
    }

    private HttpResult httpGetWithCode(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        InputStream is = null;

        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(60000);

            int code = conn.getResponseCode();
            is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(is);

            return new HttpResult(code, body);

        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private String extractId(String response) {
        if (response == null) return "";

        try {
            JSONObject obj = new JSONObject(response);
            String id = obj.optString("id", "");
            if (TextUtils.isEmpty(id)) id = obj.optString("ID", "");
            if (TextUtils.isEmpty(id)) id = obj.optString("assessment_id", "");
            if (!TextUtils.isEmpty(id)) return id.trim();
        } catch (Exception ignored) {}

        String s = response.trim();
        if (s.matches("^[a-zA-Z0-9_-]+$")) return s;

        String digits = s.replaceAll("[^0-9]", "");
        return digits.trim();
    }

    private String extractScore(String response) {
        if (response == null) return "";

        try {
            JSONObject obj = new JSONObject(response);
            String sc = obj.optString("score", "");
            if (TextUtils.isEmpty(sc)) sc = obj.optString("result", "");
            if (TextUtils.isEmpty(sc)) sc = obj.optString("prediction", "");
            if (!TextUtils.isEmpty(sc)) return sc.trim();
        } catch (Exception ignored) {}

        String s = response.trim();
        s = s.replaceAll("[^0-9.\\-]", "");
        return s.trim();
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
