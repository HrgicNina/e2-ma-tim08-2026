package com.example.slagalica.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MyNumberGameService {

    private final Random random = new Random();

    public int generateTarget() {
        return 100 + random.nextInt(900);
    }

    public List<Integer> generateNumbers() {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            values.add(1 + random.nextInt(9));
        }

        int[] mids = {10, 15, 20};
        values.add(mids[random.nextInt(mids.length)]);

        int[] highs = {25, 50, 75, 100};
        values.add(highs[random.nextInt(highs.length)]);

        return values;
    }

    public EvalResult evaluate(String expression, List<Integer> availableNumbers) {
        if (expression == null || expression.trim().isEmpty()) {
            return EvalResult.empty();
        }

        List<String> tokens = tokenize(expression);
        if (tokens.isEmpty()) {
            return EvalResult.invalid();
        }

        if (!numbersUsageValid(tokens, availableNumbers)) {
            return EvalResult.invalid();
        }

        try {
            double value = evalTokens(tokens);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return EvalResult.invalid();
            }
            return EvalResult.ok(value);
        } catch (Exception ex) {
            return EvalResult.invalid();
        }
    }

    private List<String> tokenize(String expression) {
        List<String> out = new ArrayList<>();
        StringBuilder number = new StringBuilder();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (Character.isDigit(c)) {
                number.append(c);
                continue;
            }

            if (number.length() > 0) {
                out.add(number.toString());
                number.setLength(0);
            }

            if (c == '(' || c == ')' || c == '+' || c == '-' || c == '*' || c == '/') {
                out.add(String.valueOf(c));
            } else {
                return new ArrayList<>();
            }
        }

        if (number.length() > 0) {
            out.add(number.toString());
        }
        return out;
    }

    private boolean numbersUsageValid(List<String> tokens, List<Integer> availableNumbers) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer n : availableNumbers) {
            counts.put(n, counts.getOrDefault(n, 0) + 1);
        }

        for (String t : tokens) {
            if (isNumber(t)) {
                int val = Integer.parseInt(t);
                int left = counts.getOrDefault(val, 0);
                if (left <= 0) {
                    return false;
                }
                counts.put(val, left - 1);
            }
        }
        return true;
    }

    private double evalTokens(List<String> tokens) {
        List<String> postfix = toPostfix(tokens);
        ArrayDeque<Double> stack = new ArrayDeque<>();

        for (String token : postfix) {
            if (isNumber(token)) {
                stack.push(Double.parseDouble(token));
                continue;
            }

            double b = stack.pop();
            double a = stack.pop();
            switch (token) {
                case "+": stack.push(a + b); break;
                case "-": stack.push(a - b); break;
                case "*": stack.push(a * b); break;
                case "/":
                    if (Math.abs(b) < 1e-9) {
                        throw new IllegalStateException("Division by zero");
                    }
                    stack.push(a / b);
                    break;
                default:
                    throw new IllegalStateException("Bad op");
            }
        }

        return stack.pop();
    }

    private List<String> toPostfix(List<String> infix) {
        List<String> out = new ArrayList<>();
        ArrayDeque<String> ops = new ArrayDeque<>();

        for (String t : infix) {
            if (isNumber(t)) {
                out.add(t);
            } else if ("(".equals(t)) {
                ops.push(t);
            } else if (")".equals(t)) {
                while (!ops.isEmpty() && !"(".equals(ops.peek())) {
                    out.add(ops.pop());
                }
                if (ops.isEmpty() || !"(".equals(ops.peek())) {
                    throw new IllegalStateException("Bad parentheses");
                }
                ops.pop();
            } else {
                while (!ops.isEmpty() && precedence(ops.peek()) >= precedence(t)) {
                    out.add(ops.pop());
                }
                ops.push(t);
            }
        }

        while (!ops.isEmpty()) {
            String op = ops.pop();
            if ("(".equals(op) || ")".equals(op)) {
                throw new IllegalStateException("Bad parentheses");
            }
            out.add(op);
        }

        return out;
    }

    private int precedence(String op) {
        if ("*".equals(op) || "/".equals(op)) return 2;
        if ("+".equals(op) || "-".equals(op)) return 1;
        return 0;
    }

    private boolean isNumber(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return token.length() > 0;
    }

    public static class EvalResult {
        public final boolean valid;
        public final boolean empty;
        public final double value;

        private EvalResult(boolean valid, boolean empty, double value) {
            this.valid = valid;
            this.empty = empty;
            this.value = value;
        }

        public static EvalResult ok(double value) {
            return new EvalResult(true, false, value);
        }

        public static EvalResult invalid() {
            return new EvalResult(false, false, 0);
        }

        public static EvalResult empty() {
            return new EvalResult(false, true, 0);
        }
    }
}
