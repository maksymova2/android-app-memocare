package com.example.memocare2;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

public class PaymentActivity extends AppCompatActivity {

    private EditText etCardNumber, etExpiry, etCvv;
    private Button btnPay;

    private static final String PRICE_TEXT = "€9.99";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        etCardNumber = findViewById(R.id.etCardNumber);
        etExpiry     = findViewById(R.id.etExpiry);
        etCvv        = findViewById(R.id.etCvv);
        btnPay       = findViewById(R.id.btnPayNow);

        showInfoDialog();

        btnPay.setOnClickListener(v -> tryPay());
    }

    private void showInfoDialog() {
        String msg =
                "Unlock Full AI Results for " + PRICE_TEXT + " .\n\n" +
                        "This gives you access to the full results on your account (premium speech + combined risk + recommendations). " +
                        "*** We do not store your card details ***";

        new AlertDialog.Builder(this)
                .setTitle("Payment info")
                .setMessage(msg)
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .show();
    }

    private void tryPay() {
        String card = cleanDigits(etCardNumber.getText().toString());
        String exp  = etExpiry.getText().toString().trim(); // MM/YY
        String cvv  = cleanDigits(etCvv.getText().toString());

        if (TextUtils.isEmpty(card) || TextUtils.isEmpty(exp) || TextUtils.isEmpty(cvv)) {
            Toast.makeText(this, "Please fill card details", Toast.LENGTH_SHORT).show();
            return;
        }

        if (card.length() < 13 || card.length() > 19) {
            Toast.makeText(this, "Card number looks wrong", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!luhnValid(card)) {
            Toast.makeText(this, "Invalid card number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cvv.length() != 3) {
            Toast.makeText(this, "CVV must be 3 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!expiryValid(exp)) {
            Toast.makeText(this, "Expiry must be MM/YY and in the future", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Payment successful ✅", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private String cleanDigits(String s) {
        if (s == null) return "";
        return s.replaceAll("[^0-9]", "");
    }

    private boolean expiryValid(String exp) {
        // expects MM/YY
        if (exp.length() != 5 || exp.charAt(2) != '/') return false;

        String mmStr = exp.substring(0, 2);
        String yyStr = exp.substring(3, 5);

        if (!TextUtils.isDigitsOnly(mmStr) || !TextUtils.isDigitsOnly(yyStr)) return false;

        int mm = Integer.parseInt(mmStr);
        int yy = Integer.parseInt(yyStr);

        if (mm < 1 || mm > 12) return false;


        int fullYear = 2000 + yy;

        Calendar now = Calendar.getInstance();
        int nowYear = now.get(Calendar.YEAR);
        int nowMonth = now.get(Calendar.MONTH) + 1; // 1-12

        return (fullYear > nowYear) || (fullYear == nowYear && mm >= nowMonth);
    }

    private boolean luhnValid(String number) {
        int sum = 0;
        boolean alternate = false;

        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }

        return (sum % 10 == 0);
    }
}
