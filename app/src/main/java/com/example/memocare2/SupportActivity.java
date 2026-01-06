package com.example.memocare2;

import android.content.Intent;
import android.net.Uri;
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

public class SupportActivity extends AppCompatActivity {

    private EditText etTitle, etMessage;
    private Button btnSend, btnCallSupport;

    private static final String SUPPORT_URL =
            "https://nms-backend-kr6f.onrender.com/supportTickets";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);

        etTitle = findViewById(R.id.etSupportTitle);
        etMessage = findViewById(R.id.etSupportMessage);
        btnSend = findViewById(R.id.btnSendTicket);


        btnCallSupport = findViewById(R.id.btnCallSupport);

        btnSend.setOnClickListener(v -> sendTicket());


        btnCallSupport.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_DIAL);
            i.setData(Uri.parse("tel:+3530872737285"));
            startActivity(i);
        });
    }

    private void sendTicket() {
        String title = etTitle.getText() == null ? "" : etTitle.getText().toString().trim();
        String msg = etMessage.getText() == null ? "" : etMessage.getText().toString().trim();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(msg)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }


        String supportIssue = title + " - " + msg;

        JSONObject body = new JSONObject();
        try {
            body.put("supportIssue", supportIssue);
        } catch (JSONException e) {
            Toast.makeText(this, "Error creating request", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSend.setEnabled(false);

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                SUPPORT_URL,
                body,
                response -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(this, "Message sent ", Toast.LENGTH_LONG).show();
                    etTitle.setText("");
                    etMessage.setText("");
                },
                error -> {
                    btnSend.setEnabled(true);

                    String msgError = "Failed to send";

                    if (error.networkResponse != null) {
                        int code = error.networkResponse.statusCode;
                        String data = "";
                        try {
                            if (error.networkResponse.data != null) {
                                data = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            }
                        } catch (Exception ignored) {}

                        data = data == null ? "" : data.trim();
                        msgError = "HTTP " + code + ": " + (data.isEmpty() ? "(no body)" : data);

                    } else if (error instanceof TimeoutError) {
                        msgError = "Timeout. Backend might be sleeping (Render). Try again.";
                    } else if (error.getMessage() != null) {
                        msgError = error.getMessage();
                    }

                    Toast.makeText(this, msgError, Toast.LENGTH_LONG).show();
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
