package com.example.memocare2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private Button btnQuestionnaire, btnSpeechTest, btnCognitive,
            btnResults, btnSupport, btnReview, btnLogout;

    private String lifestyleScore = "";
    private ActivityResultLauncher<Intent> lifestyleLauncher;

    private static final String STATE_LIFESTYLE_SCORE = "state_lifestyle_score";

    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_USER_EMAIL = "user_email";

    private static final String PREFS_RESULTS_BASE = "memocare_results_";


    private static final String PREFS_APP_OLD = "memocare_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tvWelcome        = findViewById(R.id.tvWelcome);
        btnQuestionnaire = findViewById(R.id.btnQuestionnaire);
        btnSpeechTest    = findViewById(R.id.btnSpeechTest);
        btnCognitive     = findViewById(R.id.btnCognitive);
        btnResults       = findViewById(R.id.btnResults);
        btnSupport       = findViewById(R.id.btnSupport);
        btnReview        = findViewById(R.id.btnReview);
        btnLogout        = findViewById(R.id.btnLogout);

        tvWelcome.setText("Welcome to MemoCare! ");

        if (savedInstanceState != null) {
            lifestyleScore = savedInstanceState.getString(STATE_LIFESTYLE_SCORE, "");
        }

        if (lifestyleScore != null && !lifestyleScore.isEmpty()) {
            btnQuestionnaire.setText("Lifestyle Questionnaire");
        } else {
            btnQuestionnaire.setText("Lifestyle Questionnaire");
        }

        lifestyleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {

                        lifestyleScore = "";
                        if (result.getData() != null) {
                            lifestyleScore = result.getData().getStringExtra("lifestyle_ai_score");
                            if (lifestyleScore == null) lifestyleScore = "";
                        }


                        btnQuestionnaire.setText("Lifestyle Questionnaire");

                        if (!lifestyleScore.isEmpty()) {
                            Toast.makeText(this, "Lifestyle done. Score: " + lifestyleScore, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Lifestyle saved ", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        btnQuestionnaire.setOnClickListener(v -> {
            Intent i = new Intent(this, LifestyleQuestionnaireActivity.class);
            lifestyleLauncher.launch(i);
        });

        btnSpeechTest.setOnClickListener(v ->
                startActivity(new Intent(this, SpeechTestActivity.class)));

        btnCognitive.setOnClickListener(v ->
                startActivity(new Intent(this, MiniCognitiveTasksActivity.class)));

        btnResults.setOnClickListener(v ->
                startActivity(new Intent(this, ResultsActivity.class)));

        btnSupport.setOnClickListener(v ->
                startActivity(new Intent(this, SupportActivity.class)));

        btnReview.setOnClickListener(v ->
                startActivity(new Intent(this, ReviewActivity.class)));

        btnLogout.setOnClickListener(v -> doLogoutAndClearResults());
    }

    private void doLogoutAndClearResults() {


        SharedPreferences session = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);
        String email = session.getString(KEY_USER_EMAIL, "");
        if (email == null) email = "";
        email = email.trim();


        if (!email.isEmpty()) {
            String safeEmail = email.toLowerCase().replaceAll("[^a-z0-9_@.]", "_");
            String lifeName = PREFS_RESULTS_BASE + safeEmail;

            SharedPreferences userResults = getSharedPreferences(lifeName, MODE_PRIVATE);
            userResults.edit().clear().apply();

            try { deleteSharedPreferences(lifeName); } catch (Exception ignored) {}
        }


        SharedPreferences old = getSharedPreferences(PREFS_APP_OLD, MODE_PRIVATE);
        old.edit().clear().apply();
        try { deleteSharedPreferences(PREFS_APP_OLD); } catch (Exception ignored) {}


        session.edit().clear().apply();
        try { deleteSharedPreferences(PREFS_SESSION); } catch (Exception ignored) {}


        lifestyleScore = "";
        btnQuestionnaire.setText("Lifestyle Questionnaire");

        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();


        Intent i = new Intent(this, AuthChoiceActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_LIFESTYLE_SCORE, lifestyleScore);
    }
}
