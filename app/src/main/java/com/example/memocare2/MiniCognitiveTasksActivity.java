package com.example.memocare2;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class MiniCognitiveTasksActivity extends AppCompatActivity {

    private LinearLayout sectionEncode, sectionAttention, sectionRecall, sectionResult;
    private TextView tvTitle, tvSubtitle, tvWordBig;
    private TextView tvAttentionTimer, tvLetter;
    private TextView tvMemoryScore, tvAttentionScore, tvInterpretation;
    private EditText etRecall;
    private Button btnStartTest, btnTap, btnCheckRecall, btnDone, btnBackCognitive;

    private static final int WORD_COUNT = 8;
    private static final long SHOW_PER_WORD_MS = 1200;
    private static final long GAP_BETWEEN_WORDS_MS = 300;
    private static final long ATTENTION_TOTAL_MS = 20_000;
    private static final long LETTER_INTERVAL_MS = 800;
    private static final double TARGET_PROB = 0.25;

    private static final String PREFS_SESSION = "memocare_session";
    private static final String KEY_USER_EMAIL = "user_email";

    private static final String PREFS_RESULTS_BASE = "memocare_results_";

    private static final String KEY_COG_DONE = "cog_done";
    private static final String KEY_COG_MEMORY = "cog_memory_score";
    private static final String KEY_COG_WORDS_TOTAL = "cog_words_total";
    private static final String KEY_COG_HITS = "cog_hits";
    private static final String KEY_COG_MISSES = "cog_misses";
    private static final String KEY_COG_FALSE = "cog_false_taps";
    private static final String KEY_COG_AVG_RT = "cog_avg_rt_ms";

    private static final String[] WORD_POOL = new String[]{
            "APPLE","RIVER","GARDEN","MUSIC","ORANGE","CAMERA","MOUNTAIN","WINDOW",
            "PENCIL","FLOWER","SILVER","BRIDGE","COUNTRY","DOCTOR","FAMILY","SUMMER"
    };

    private final ArrayList<String> wordsToMemorize = new ArrayList<>();

    private CountDownTimer attentionTimer, perLetterTimer;
    private long currentLetterStart = 0L;
    private boolean targetActive = false;

    private int hits = 0;
    private int misses = 0;
    private int falseTaps = 0;
    private final ArrayList<Long> reactionTimes = new ArrayList<>();

    private final Random rnd = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mini_cognitive_tasks);

        bindViews();
        showOnly(sectionEncode);
        tvWordBig.setText("");

        btnStartTest.setOnClickListener(v -> startEncodingPhase());
    }

    private void bindViews() {
        sectionEncode    = findViewById(R.id.sectionEncode);
        sectionAttention = findViewById(R.id.sectionAttention);
        sectionRecall    = findViewById(R.id.sectionRecall);
        sectionResult    = findViewById(R.id.sectionResult);

        tvTitle   = findViewById(R.id.tvTitle);
        tvSubtitle= findViewById(R.id.tvSubtitle);
        tvWordBig = findViewById(R.id.tvWordBig);

        tvAttentionTimer = findViewById(R.id.tvAttentionTimer);
        tvLetter         = findViewById(R.id.tvLetter);

        tvMemoryScore    = findViewById(R.id.tvMemoryScore);
        tvAttentionScore = findViewById(R.id.tvAttentionScore);
        tvInterpretation = findViewById(R.id.tvInterpretation);

        etRecall = findViewById(R.id.etRecall);
        btnStartTest   = findViewById(R.id.btnStartMiniTest);
        btnTap         = findViewById(R.id.btnTap);
        btnCheckRecall = findViewById(R.id.btnCheckRecall);
        btnDone        = findViewById(R.id.btnDone);
        btnBackCognitive = findViewById(R.id.btnBackCognitive);

        btnDone.setOnClickListener(v -> finish());
        btnBackCognitive.setOnClickListener(v -> finish());
    }

    private void startEncodingPhase() {
        showOnly(sectionEncode);

        btnStartTest.setEnabled(false);
        btnStartTest.setAlpha(0.5f);

        wordsToMemorize.clear();
        Set<Integer> used = new HashSet<>();
        while (wordsToMemorize.size() < WORD_COUNT) {
            int idx = rnd.nextInt(WORD_POOL.length);
            if (used.add(idx)) wordsToMemorize.add(WORD_POOL[idx]);
        }

        new Thread(() -> {
            try {
                for (int i = 0; i < wordsToMemorize.size(); i++) {
                    final String w = wordsToMemorize.get(i);
                    runOnUiThread(() -> tvWordBig.setText(w));
                    Thread.sleep(SHOW_PER_WORD_MS);
                    if (i < wordsToMemorize.size() - 1) {
                        runOnUiThread(() -> tvWordBig.setText(" "));
                        Thread.sleep(GAP_BETWEEN_WORDS_MS);
                    }
                }
            } catch (InterruptedException ignored) {}
            runOnUiThread(this::startAttentionPhase);
        }).start();
    }

    private void startAttentionPhase() {
        showOnly(sectionAttention);

        hits = misses = falseTaps = 0;
        reactionTimes.clear();
        targetActive = false;

        attentionTimer = new CountDownTimer(ATTENTION_TOTAL_MS, 1000) {
            @Override public void onTick(long msLeft) {
                tvAttentionTimer.setText("Time: " + (msLeft / 1000) + "s");
            }
            @Override public void onFinish() {
                stopPerLetterTimer();
                if (targetActive) { misses++; targetActive = false; }
                startRecallPhase();
            }
        }.start();

        startPerLetterTimer();

        btnTap.setOnClickListener(v -> {
            if (targetActive) {
                long rt = System.currentTimeMillis() - currentLetterStart;
                hits++;
                reactionTimes.add(rt);
                targetActive = false;
            } else {
                falseTaps++;
            }
        });
    }

    private void startPerLetterTimer() {
        stopPerLetterTimer();
        perLetterTimer = new CountDownTimer(ATTENTION_TOTAL_MS, LETTER_INTERVAL_MS) {
            @Override public void onTick(long millisUntilFinished) {
                boolean makeTarget = rnd.nextDouble() < TARGET_PROB;
                char letter = makeTarget ? 'A' : randomNonA();
                tvLetter.setText(String.valueOf(letter));
                currentLetterStart = System.currentTimeMillis();

                if (makeTarget) {
                    targetActive = true;
                } else {
                    if (targetActive) misses++;
                    targetActive = false;
                }
            }
            @Override public void onFinish() {}
        }.start();
    }

    private void stopPerLetterTimer() {
        if (perLetterTimer != null) {
            perLetterTimer.cancel();
            perLetterTimer = null;
        }
    }

    private char randomNonA() {
        int base = 'A';
        int r;
        do { r = base + rnd.nextInt(26); } while (r == 'A');
        return (char) r;
    }

    private void startRecallPhase() {
        showOnly(sectionRecall);
        etRecall.setText("");

        btnCheckRecall.setOnClickListener(v -> {
            int memScore = scoreRecall(etRecall.getText().toString());
            showResults(memScore);
        });
    }

    private int scoreRecall(String userText) {
        if (userText == null) return 0;

        String[] parts = userText.toUpperCase(Locale.ROOT).split("[^A-Z]+");
        Set<String> entered = new HashSet<>(Arrays.asList(parts));
        entered.remove("");

        int score = 0;
        for (String w : wordsToMemorize) {
            if (entered.contains(w)) score++;
        }
        return score;
    }

    private void showResults(int memoryScore) {
        showOnly(sectionResult);

        long avg = -1;
        if (!reactionTimes.isEmpty()) {
            long sum = 0;
            for (Long rt : reactionTimes) sum += rt;
            avg = sum / reactionTimes.size();
        }

        tvMemoryScore.setText("Memory: " + memoryScore + "/" + WORD_COUNT);

        String attTxt = "Attention — Hits: " + hits +
                ", Misses: " + misses +
                ", False taps: " + falseTaps +
                ", Avg RT: " + (avg < 0 ? "–" : (avg + " ms"));
        tvAttentionScore.setText(attTxt);

        if (tvInterpretation != null) {
            tvInterpretation.setText("");
            tvInterpretation.setVisibility(View.GONE);
        }

        saveCognitiveForUser(memoryScore, avg);
    }

    private void saveCognitiveForUser(int memoryScore, long avgRt) {
        SharedPreferences session = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE);
        String email = session.getString(KEY_USER_EMAIL, "");
        if (email == null) email = "";
        email = email.trim();

        if (email.isEmpty()) return;

        String safeEmail = email.toLowerCase().replaceAll("[^a-z0-9_@.]", "_");
        SharedPreferences sp = getSharedPreferences(PREFS_RESULTS_BASE + safeEmail, MODE_PRIVATE);

        sp.edit()
                .putBoolean(KEY_COG_DONE, true)
                .putInt(KEY_COG_MEMORY, memoryScore)
                .putInt(KEY_COG_WORDS_TOTAL, WORD_COUNT)
                .putInt(KEY_COG_HITS, hits)
                .putInt(KEY_COG_MISSES, misses)
                .putInt(KEY_COG_FALSE, falseTaps)
                .putLong(KEY_COG_AVG_RT, avgRt)
                .apply();
    }

    private void showOnly(View section) {
        sectionEncode.setVisibility(section == sectionEncode ? View.VISIBLE : View.GONE);
        sectionAttention.setVisibility(section == sectionAttention ? View.VISIBLE : View.GONE);
        sectionRecall.setVisibility(section == sectionRecall ? View.VISIBLE : View.GONE);
        sectionResult.setVisibility(section == sectionResult ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (attentionTimer != null) attentionTimer.cancel();
        stopPerLetterTimer();
    }
}
