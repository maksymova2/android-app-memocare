package com.example.memocare2;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class ReviewActivity extends AppCompatActivity {

    private EditText etRating, etReviewText;
    private Button btnSend;

    private static final String REVIEW_URL =
            "https://nms-backend-kr6f.onrender.com/reviews";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        etRating = findViewById(R.id.etRating);
        etReviewText = findViewById(R.id.etReviewText);
        btnSend = findViewById(R.id.btnSendReview);

        btnSend.setOnClickListener(v -> sendReview());
    }

    private void sendReview() {
        String ratingStr = etRating.getText() == null ? "" : etRating.getText().toString().trim();
        String review = etReviewText.getText() == null ? "" : etReviewText.getText().toString().trim();

        if (TextUtils.isEmpty(ratingStr) || TextUtils.isEmpty(review)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        int rating;
        try {

            double tmp = Double.parseDouble(ratingStr);
            rating = (int) Math.round(tmp);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Rating must be a number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rating < 1 || rating > 5) {
            Toast.makeText(this, "Rating must be between 1 and 5", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("rating", rating);
            body.put("review", review);
        } catch (JSONException e) {
            Toast.makeText(this, "Error creating request", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSend.setEnabled(false);

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                REVIEW_URL,
                body,
                response -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(this, "Review sent", Toast.LENGTH_LONG).show();
                    finish();
                },
                error -> {
                    btnSend.setEnabled(true);

                    String msg = "Failed to send review";

                    if (error.networkResponse != null) {
                        int code = error.networkResponse.statusCode;

                        String data = "";
                        try {
                            if (error.networkResponse.data != null) {
                                data = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            }
                        } catch (Exception ignored) { }

                        if (data == null) data = "";
                        data = data.trim();

                        if (!data.isEmpty()) {
                            msg = "HTTP " + code + ": " + data;
                        } else {
                            msg = "HTTP " + code + ": (no response body)";
                        }

                    } else if (error instanceof TimeoutError) {
                        msg = "Timeout. Backend might be sleeping (Render). Try again.";
                    } else if (error.getMessage() != null) {
                        msg = error.getMessage();
                    }

                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
        );

        request.setRetryPolicy(new DefaultRetryPolicy(
                25000,
                0,
                1.0f
        ));

        queue.add(request);
    }
}
