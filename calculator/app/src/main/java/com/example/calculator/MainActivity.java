package com.example.calculator;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView display;
    private double num1 = 0, num2 = 0;
    private String operator = "";
    private boolean isNewOperation = true;
    private boolean hasDecimal = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        display = findViewById(R.id.tvDisplay);

        int[] numberButtons = {
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };

        for (int id : numberButtons) {
            Button btn = findViewById(id);
            btn.setOnClickListener(v -> appendNumber(((Button) v).getText().toString()));
        }

        findViewById(R.id.btnDecimal).setOnClickListener(v -> appendDecimal());
        findViewById(R.id.btnClear).setOnClickListener(v -> clearAll());
        findViewById(R.id.btnDelete).setOnClickListener(v -> deleteLast());
        findViewById(R.id.btnEquals).setOnClickListener(v -> calculate());
        findViewById(R.id.btnPercent).setOnClickListener(v -> applyPercent());

        int[] operatorButtons = {R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide};
        for (int id : operatorButtons) {
            Button btn = findViewById(id);
            btn.setOnClickListener(v -> setOperator(((Button) v).getText().toString()));
        }
    }

    private void appendNumber(String digit) {
        if (isNewOperation) {
            display.setText("");
            isNewOperation = false;
        }
        String current = display.getText().toString();
        if (current.equals("0")) current = "";
        display.setText(current + digit);
    }

    private void appendDecimal() {
        if (isNewOperation) {
            display.setText("0.");
            isNewOperation = false;
            hasDecimal = true;
            return;
        }
        if (!hasDecimal) {
            display.append(".");
            hasDecimal = true;
        }
    }

    private void clearAll() {
        display.setText("0");
        num1 = 0;
        num2 = 0;
        operator = "";
        isNewOperation = true;
        hasDecimal = false;
    }

    private void deleteLast() {
        String current = display.getText().toString();
        if (current.length() > 1) {
            display.setText(current.substring(0, current.length() - 1));
        } else {
            display.setText("0");
        }
    }

    private void setOperator(String op) {
        try {
            num1 = Double.parseDouble(display.getText().toString());
        } catch (NumberFormatException e) {
            num1 = 0;
        }
        operator = op;
        isNewOperation = true;
        hasDecimal = false;
    }

    private void calculate() {
        try {
            num2 = Double.parseDouble(display.getText().toString());
        } catch (NumberFormatException e) {
            num2 = 0;
        }

        double result = 0;
        boolean valid = true;

        switch (operator) {
            case "+":
                result = num1 + num2;
                break;
            case "\u00D7":
                result = num1 * num2;
                break;
            case "-":
                result = num1 - num2;
                break;
            case "\u00F7":
                if (num2 != 0) {
                    result = num1 / num2;
                } else {
                    display.setText("Erreur");
                    valid = false;
                }
                break;
            default:
                valid = false;
        }

        if (valid) {
            if (result == (long) result) {
                display.setText(String.valueOf((long) result));
            } else {
                display.setText(String.valueOf(result));
            }
            num1 = result;
            isNewOperation = true;
            hasDecimal = false;
            operator = "";
        }
    }

    private void applyPercent() {
        try {
            double current = Double.parseDouble(display.getText().toString());
            display.setText(String.valueOf(current / 100));
            hasDecimal = true;
        } catch (NumberFormatException e) {
            display.setText("0");
        }
    }
}
