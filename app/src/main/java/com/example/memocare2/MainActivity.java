package com.example.memocare2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private CheckBox cbConsent;
    private Button btnPrivacy, btnStart;

    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_USER_EMAIL = "user_email";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        if (isLoggedIn()) {
            goToDashboard();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        cbConsent  = findViewById(R.id.cbConsent);
        btnPrivacy = findViewById(R.id.btnPrivacy);
        btnStart   = findViewById(R.id.btnStart);

        btnStart.setEnabled(cbConsent.isChecked());
        cbConsent.setOnCheckedChangeListener((buttonView, isChecked) -> btnStart.setEnabled(isChecked));

        btnPrivacy.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Privacy Policy")
                        .setMessage("We store your questionnaire, a short audio sample, and test results locally during the assessment. You can delete them at any time.")
                        .setPositiveButton("OK", null)
                        .show()
        );

        btnStart.setOnClickListener(v -> {
            if (!cbConsent.isChecked()) {
                Toast.makeText(this, "Please accept the consent first", Toast.LENGTH_SHORT).show();
                return;
            }


            Intent i = new Intent(MainActivity.this, AuthChoiceActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isLoggedIn()) {
            goToDashboard();
        }
    }

    private boolean isLoggedIn() {
        SharedPreferences session = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);
        String email = session.getString(KEY_USER_EMAIL, "");
        if (email == null) email = "";
        return !email.trim().isEmpty();
    }

    private void goToDashboard() {
        Intent i = new Intent(MainActivity.this, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
