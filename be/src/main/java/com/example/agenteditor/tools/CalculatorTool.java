package com.example.agenteditor.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.regex.Pattern;

/**
 * Tool that evaluates simple arithmetic expressions (numbers and +, -, *, /).
 */
public class CalculatorTool {

    private static final Pattern SAFE_EXPRESSION = Pattern.compile("^[\\d\\s+\\-*/().]+$");

    @Tool("Evaluate a simple arithmetic expression (e.g. 2 + 3 * 4). Only numbers and + - * / ( ) allowed.")
    public String calculate(@P("Arithmetic expression") String expression) {
        if (expression == null || expression.isBlank()) {
            return "Empty expression";
        }
        String trimmed = expression.trim();
        if (!SAFE_EXPRESSION.matcher(trimmed).matches()) {
            return "Invalid characters in expression. Use only numbers and + - * / ( )";
        }
        try {
            double result = evaluate(trimmed);
            return String.valueOf(result);
        } catch (ArithmeticException e) {
            return "Error: " + e.getMessage();
        }
    }

    private static double evaluate(String expr) {
        return new Object() {
            int i = 0;

            double parse() {
                double x = parseTerm();
                for (; ; ) {
                    skipSpaces();
                    if (i >= expr.length()) break;
                    char c = expr.charAt(i);
                    if (c == '+') {
                        i++;
                        x += parseTerm();
                    } else if (c == '-') {
                        i++;
                        x -= parseTerm();
                    } else break;
                }
                return x;
            }

            double parseTerm() {
                double x = parseFactor();
                for (; ; ) {
                    skipSpaces();
                    if (i >= expr.length()) break;
                    char c = expr.charAt(i);
                    if (c == '*') {
                        i++;
                        x *= parseFactor();
                    } else if (c == '/') {
                        i++;
                        double d = parseFactor();
                        if (d == 0) throw new ArithmeticException("division by zero");
                        x /= d;
                    } else break;
                }
                return x;
            }

            double parseFactor() {
                skipSpaces();
                if (i >= expr.length()) throw new ArithmeticException("unexpected end of expression");
                char c = expr.charAt(i);
                if (c == '(') {
                    i++;
                    double x = parse();
                    skipSpaces();
                    if (i >= expr.length() || expr.charAt(i) != ')') throw new ArithmeticException("missing ')'");
                    i++;
                    return x;
                }
                if (c == '+' || c == '-') {
                    i++;
                    return (c == '-' ? -1 : 1) * parseFactor();
                }
                int start = i;
                while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) i++;
                if (start == i) throw new ArithmeticException("expected number");
                return Double.parseDouble(expr.substring(start, i));
            }

            void skipSpaces() {
                while (i < expr.length() && Character.isWhitespace(expr.charAt(i))) i++;
            }
        }.parse();
    }
}
