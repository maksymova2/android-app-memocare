package com.example.memocare2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class AuthChoiceActivity extends AppCompatActivity {

    private Button btnLogin, btnSignup;

    // session prefs
    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_USER_EMAIL = "user_email";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_choice);


        SharedPreferences session = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);
        String email = session.getString(KEY_USER_EMAIL, "");
        if (email != null && !email.trim().isEmpty()) {
            Intent i = new Intent(this, DashboardActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);

        btnLogin.setOnClickListener(v -> {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        btnSignup.setOnClickListener(v -> {
            Intent i = new Intent(this, SignUpActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // do nothing
    }
}
