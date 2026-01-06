package com.example.memocare2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private CheckBox cbConsent;
    private Button btnLogin;

    private static final String BASE_URL = "https://nms-backend-kr6f.onrender.com";

    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_USER_EMAIL = "user_email";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbConsent = findViewById(R.id.cbConsent);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            clearErrors();

            String email = textOf(etEmail).trim();
            String pass = textOf(etPassword);

            boolean ok = true;

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Enter valid email");
                ok = false;
            }

            if (pass.length() < 6) {
                tilPassword.setError("Min 6 chars");
                ok = false;
            }

            if (!cbConsent.isChecked()) {
                Toast.makeText(LoginActivity.this, "Accept consent", Toast.LENGTH_SHORT).show();
                ok = false;
            }

            if (!ok) return;

            btnLogin.setEnabled(false);
            loginWithBackend(email, pass);
        });
    }

    private void loginWithBackend(String email, String password) {
        final String fEmail = email;
        final String fPass = password;

        new Thread(() -> {
            boolean success = false;
            String errorMsg = "Network error";

            try {
                URL url = new URL(BASE_URL + "/accounts/login");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject body = new JSONObject();
                body.put("email", fEmail);
                body.put("password", fPass);

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
                boolean valid = resp.optBoolean("valid", false);

                if (valid) {
                    success = true;
                } else {
                    errorMsg = resp.optString("error", "Invalid credentials");
                }

                conn.disconnect();

            } catch (Exception e) {
                errorMsg = "Error: " + e.getMessage();
            }

            boolean finalSuccess = success;
            String finalErrorMsg = errorMsg;

            runOnUiThread(() -> {
                btnLogin.setEnabled(true);

                if (finalSuccess) {
                    SharedPreferences prefs = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);
                    prefs.edit().putString(KEY_USER_EMAIL, fEmail.trim()).apply();

                    Intent i = new Intent(LoginActivity.this, ProfileActivity.class);
                    i.putExtra("from", "login");
                    i.putExtra("user_email", fEmail.trim());
                    startActivity(i);
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, finalErrorMsg, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
