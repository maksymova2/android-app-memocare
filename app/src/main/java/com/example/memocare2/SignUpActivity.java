package com.example.memocare2;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SignUpActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilSurname, tilEmail, tilPassword, tilAge;
    private TextInputEditText etName, etSurname, etEmail, etPassword, etAge;
    private CheckBox cbConsent;
    private Button btnCreate;

    private static final String BASE_URL = "https://nms-backend-kr6f.onrender.com";

    private static final String PREFS = "memocare_session";
    private static final String KEY_PATIENT_ID = "patient_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        tilName = findViewById(R.id.tilName);
        tilSurname = findViewById(R.id.tilSurname);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilAge = findViewById(R.id.tilAge);

        etName = findViewById(R.id.etName);
        etSurname = findViewById(R.id.etSurname);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etAge = findViewById(R.id.etAge);

        cbConsent = findViewById(R.id.cbConsent);
        btnCreate = findViewById(R.id.btnCreateAccount);

        btnCreate.setEnabled(false);

        TextWatcher watcher = new SimpleWatcher(this::updateButtonEnabled);
        etName.addTextChangedListener(watcher);
        etSurname.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
        etAge.addTextChangedListener(watcher);
        cbConsent.setOnCheckedChangeListener((b, c) -> updateButtonEnabled());
        updateButtonEnabled();

        btnCreate.setOnClickListener(v -> {
            clearErrors();

            String name = textOf(etName);
            String surname = textOf(etSurname);
            String email = textOf(etEmail);
            String pass = textOf(etPassword);
            String ageStr = textOf(etAge);

            boolean ok = true;

            if (TextUtils.isEmpty(name)) { tilName.setError("Enter name"); ok = false; }
            if (TextUtils.isEmpty(surname)) { tilSurname.setError("Enter surname"); ok = false; }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { tilEmail.setError("Enter valid email"); ok = false; }
            if (pass.length() < 6) { tilPassword.setError("Min 6 chars"); ok = false; }

            int age = -1;
            if (TextUtils.isEmpty(ageStr)) {
                tilAge.setError("Enter age");
                ok = false;
            } else if (!TextUtils.isDigitsOnly(ageStr)) {
                tilAge.setError("Use numbers only");
                ok = false;
            } else {
                try { age = Integer.parseInt(ageStr); }
                catch (Exception e) { tilAge.setError("Use numbers only"); ok = false; }
            }

            if (!cbConsent.isChecked()) {
                Toast.makeText(SignUpActivity.this, "Accept consent", Toast.LENGTH_SHORT).show();
                ok = false;
            }

            if (!ok) return;

            btnCreate.setEnabled(false);

            final String fName = name;
            final String fSurname = surname;
            final String fEmail = email;
            final String fPass = pass;
            final int fAge = age;

            new Thread(() -> sendSignupRequest(fName, fSurname, fEmail, fPass, fAge)).start();
        });
    }

    private void sendSignupRequest(String name, String surname, String email, String pass, int age) {
        boolean success = false;
        String errorMsg = "Connection error";

        try {
            URL url = new URL(BASE_URL + "/accounts");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", pass);
            body.put("role", "patient");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);

            JSONObject resp = new JSONObject(sb.toString());
            boolean respSuccess = resp.optBoolean("success", false);

            if (respSuccess) success = true;
            else errorMsg = resp.optString("error", "Account already exists");

            conn.disconnect();

        } catch (Exception e) {
            errorMsg = "Error: " + e.getMessage();
        }

        boolean finalSuccess = success;
        String finalErrorMsg = errorMsg;

        runOnUiThread(() -> {
            btnCreate.setEnabled(true);

            if (finalSuccess) {

                String patientId = cap(name) + cap(surname);
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_PATIENT_ID, patientId)
                        .apply();

                Toast.makeText(SignUpActivity.this,
                        "Account created. Please fill your profile.",
                        Toast.LENGTH_LONG).show();

                Intent i = new Intent(SignUpActivity.this, ProfileActivity.class);
                i.putExtra("from", "signup");


                i.putExtra("prefill_name", name);
                i.putExtra("prefill_surname", surname);
                i.putExtra("prefill_age", String.valueOf(age));

                i.putExtra("patient_id", patientId);

                startActivity(i);
                finish();
            } else {
                Toast.makeText(SignUpActivity.this, finalErrorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateButtonEnabled() {
        String name = textOf(etName);
        String surname = textOf(etSurname);
        String email = textOf(etEmail);
        String pass = textOf(etPassword);
        String ageStr = textOf(etAge);

        boolean looksValid = !TextUtils.isEmpty(name)
                && !TextUtils.isEmpty(surname)
                && Patterns.EMAIL_ADDRESS.matcher(email).matches()
                && pass.length() >= 6
                && !TextUtils.isEmpty(ageStr)
                && TextUtils.isDigitsOnly(ageStr)
                && cbConsent.isChecked();

        btnCreate.setEnabled(looksValid);
    }

    private void clearErrors() {
        tilName.setError(null);
        tilSurname.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilAge.setError(null);
    }

    private String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private String cap(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;
        SimpleWatcher(Runnable onChange) { this.onChange = onChange; }
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) { onChange.run(); }
        public void afterTextChanged(Editable s) {}
    }
}
