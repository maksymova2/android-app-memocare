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
                Toast.makeText(this, "Please login again.", Toast.LENGTH_LONG).show();
                return;
            }

            String ageStr = safe(etAge.getText().toString());
            String weightStr = safe(etWeight.getText().toString()).replace(",", ".");

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
            Toast.makeText(this, "Analyzing...", Toast.LENGTH_SHORT).show();

            sendLifestyleToNewBackend(ageStr, gender, weightStr, hand, smoking, education,
                    sleep, diet, activity, diabetic, family);
        });

        btnBackLifestyle.setOnClickListener(v -> finish());
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean isValidWeight(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SharedPreferences getUserPrefs(String email) {
        String safeEmail = email.toLowerCase().replaceAll("[^a-z0-9_@.]", "_");
        return getSharedPreferences(PREFS_RESULTS_BASE + safeEmail, MODE_PRIVATE);
    }

    private void saveLifestyleToPrefsForUser(String email, String ageStr, String weightStr,
                                             String gender, String hand, String smoking, String education,
                                             String sleepQuality, String diet, String activity,
                                             String diabetic, String family) {

        SharedPreferences prefs = getUserPrefs(email);

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

    private void sendLifestyleToNewBackend(String age, String gender, String weightKg,
                                           String hand, String smoking, String education,
                                           String sleepQuality, String diet, String activity,
                                           String diabetic, String familyHistory) {

        new Thread(() -> {
            String score = "";

            try {
                HttpResult startRes = httpGetWithCode(AI_BASE_URL + "/startassement");
                if (startRes.code != 200) throw new Exception();

                String id = startRes.body.replaceAll("[^0-9]", "");

                callSetter("set_age", id, age);
                callSetter("set_gender", id, gender);
                callSetter("set_weight", id, weightKg);
                callSetter("set_dominant_hand", id, hand);
                callSetter("set_smoking_status", id, smoking);
                callSetter("set_education", id, education);
                callSetter("set_sleep", id, sleepQuality);
                callSetter("set_diet", id, diet);
                callSetter("set_physical_activity", id, activity);
                callSetter("set_diabetic", id, diabetic);
                callSetter("set_family_history", id, familyHistory);

                HttpResult evalRes = httpGetWithCode(AI_BASE_URL + "/start_eval/" + encodePath(id));
                if (evalRes.code == 200) {
                    score = evalRes.body.replaceAll("[^0-9.\\-]", "");
                }

            } catch (Exception ignored) {}

            String finalScore = score;

            runOnUiThread(() -> {
                btnSubmitLifestyle.setEnabled(true);

                if (!TextUtils.isEmpty(finalScore)) {
                    SharedPreferences prefs = getUserPrefs(userEmail);
                    prefs.edit().putString(KEY_LIFE_AI_SCORE, finalScore).apply();
                }

                setResult(RESULT_OK);
                finish();
            });
        }).start();
    }

    private void callSetter(String setterName, String id, String value) throws Exception {
        String url = AI_BASE_URL + "/" + setterName + "/" + encodePath(id) + "/" + encodePath(value);
        HttpResult res = httpGetWithCode(url);
        if (res.code != 200) throw new Exception();
    }

    private String encodePath(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static class HttpResult {
        int code;
        String body;
        HttpResult(int c, String b) { code = c; body = b; }
    }

    private HttpResult httpGetWithCode(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();

        return new HttpResult(code, body);
    }

    private String readAll(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private String getSelectedRadioValueMapped(RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == -1) return null;
        RadioButton rb = findViewById(id);
        Object tag = rb.getTag();
        return tag == null ? null : tag.toString().trim();
    }
}
