package com.example.memocare2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProfileActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://nms-backend-kr6f.onrender.com";

    private static final String PREFS = "memocare_session";
    private static final String KEY_PATIENT_ID = "patient_id";

    private static final String KEY_USER_EMAIL = "user_email";

    private EditText etFirstName, etLastName, etAge, etDoctorId, etNotes;
    private RadioGroup rgGender;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName  = findViewById(R.id.etLastName);
        etAge       = findViewById(R.id.etAge);
        etDoctorId  = findViewById(R.id.etDoctorId);
        etNotes     = findViewById(R.id.etNotes);
        rgGender    = findViewById(R.id.rgGender);
        btnSave     = findViewById(R.id.btnSaveProfile);

        String emailFromIntent = getIntent().getStringExtra("user_email");
        if (emailFromIntent == null) emailFromIntent = "";
        emailFromIntent = emailFromIntent.trim();
        if (!emailFromIntent.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit().putString(KEY_USER_EMAIL, emailFromIntent).apply();
        }

        String preName = getIntent().getStringExtra("prefill_name");
        String preSur  = getIntent().getStringExtra("prefill_surname");
        String preAge  = getIntent().getStringExtra("prefill_age");

        if (preName != null) etFirstName.setText(preName);
        if (preSur  != null) etLastName.setText(preSur);
        if (preAge  != null) etAge.setText(preAge);

        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String firstName = safe(etFirstName.getText().toString());
        String lastName  = safe(etLastName.getText().toString());
        String ageStr    = safe(etAge.getText().toString());
        String doctorId  = safe(etDoctorId.getText().toString());
        String notes     = safe(etNotes.getText().toString());
        String gender    = getSelectedGender();

        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName)
                || TextUtils.isEmpty(ageStr) || TextUtils.isEmpty(gender)
                || TextUtils.isEmpty(doctorId)) {
            Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!TextUtils.isDigitsOnly(ageStr)) {
            Toast.makeText(this, "Age must be a number", Toast.LENGTH_SHORT).show();
            return;
        }

        int age = Integer.parseInt(ageStr);
        btnSave.setEnabled(false);

        // build patientId exactly like backend
        String patientId = cap(firstName) + cap(lastName);

        new Thread(() ->
                upsertPatient(patientId, firstName, lastName, age, gender.toLowerCase(), doctorId, notes)
        ).start();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String getSelectedGender() {
        int id = rgGender.getCheckedRadioButtonId();
        if (id == -1) return "";
        RadioButton rb = findViewById(id);
        return rb.getText().toString().trim();
    }

    private String cap(String s) {
        if (s == null || s.trim().isEmpty()) return "";
        s = s.trim();
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private void upsertPatient(String patientId, String firstName, String lastName, int age,
                               String gender, String doctorId, String notes) {

        boolean success = false;
        String errorMsg = "Error saving profile";

        try {

            URL urlPut = new URL(BASE_URL + "/patients/" + patientId);
            HttpURLConnection connPut = (HttpURLConnection) urlPut.openConnection();
            connPut.setRequestMethod("PUT");
            connPut.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connPut.setDoOutput(true);
            connPut.setConnectTimeout(15000);
            connPut.setReadTimeout(15000);

            JSONObject bodyPut = new JSONObject();
            bodyPut.put("firstName", firstName);
            bodyPut.put("lastName", lastName);
            bodyPut.put("age", age);
            bodyPut.put("gender", gender);
            bodyPut.put("notes", notes);
            bodyPut.put("doctor_id", doctorId);

            try (OutputStream os = connPut.getOutputStream()) {
                os.write(bodyPut.toString().getBytes("UTF-8"));
            }

            int codePut = connPut.getResponseCode();
            readAll((codePut >= 200 && codePut < 300) ? connPut.getInputStream() : connPut.getErrorStream());
            connPut.disconnect();

            if (codePut >= 200 && codePut < 300) {
                success = true;
            } else if (codePut == 404) {

                URL urlPost = new URL(BASE_URL + "/patients");
                HttpURLConnection connPost = (HttpURLConnection) urlPost.openConnection();
                connPost.setRequestMethod("POST");
                connPost.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connPost.setDoOutput(true);
                connPost.setConnectTimeout(15000);
                connPost.setReadTimeout(15000);

                JSONObject bodyPost = new JSONObject();
                bodyPost.put("first_name", firstName);
                bodyPost.put("last_name", lastName);
                bodyPost.put("age", age);
                bodyPost.put("gender", gender);
                bodyPost.put("doctor_id", doctorId);
                bodyPost.put("notes", notes);

                try (OutputStream os = connPost.getOutputStream()) {
                    os.write(bodyPost.toString().getBytes("UTF-8"));
                }

                int codePost = connPost.getResponseCode();
                readAll((codePost >= 200 && codePost < 300) ? connPost.getInputStream() : connPost.getErrorStream());
                connPost.disconnect();

                if (codePost >= 200 && codePost < 300) {
                    success = true;
                } else {
                    errorMsg = "HTTP " + codePost;
                }
            } else {
                errorMsg = "HTTP " + codePut;
            }

        } catch (Exception e) {
            errorMsg = "Network error: " + e.getMessage();
        }

        boolean finalSuccess = success;
        String finalError = errorMsg;

        runOnUiThread(() -> {
            btnSave.setEnabled(true);

            if (finalSuccess) {
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                prefs.edit().putString(KEY_PATIENT_ID, patientId).apply();

                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
                goToDashboard();
            } else {
                Toast.makeText(this, finalError, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private void goToDashboard() {
        Intent i = new Intent(this, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
