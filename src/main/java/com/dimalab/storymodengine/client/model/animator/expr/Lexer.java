package com.dimalab.storymodengine.client.model.animator.expr;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written scanner for the animation expression grammar — small and fixed enough (no
 * user-defined operators, 4 precedence levels) that a generated or table-driven lexer would be
 * overhead rather than clarity.
 */
final class Lexer {

    enum TokenType {
        NUMBER, IDENT, DOT,
        PLUS, MINUS, STAR, SLASH,
        BANG, AMP_AMP, PIPE_PIPE,
        EQ_EQ, BANG_EQ, LT, GT, LE, GE,
        QUESTION, COLON, COMMA,
        LPAREN, RPAREN,
        EOF
    }

    record Token(TokenType type, String text, float number, int pos) {
        static Token of(TokenType type, String text, int pos) {
            return new Token(type, text, 0f, pos);
        }
    }

    private Lexer() {
    }

    static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int length = source.length();
        while (i < length) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            switch (c) {
                case '+' -> { tokens.add(Token.of(TokenType.PLUS, "+", start)); i++; }
                case '-' -> { tokens.add(Token.of(TokenType.MINUS, "-", start)); i++; }
                case '*' -> { tokens.add(Token.of(TokenType.STAR, "*", start)); i++; }
                case '/' -> { tokens.add(Token.of(TokenType.SLASH, "/", start)); i++; }
                case '.' -> { tokens.add(Token.of(TokenType.DOT, ".", start)); i++; }
                case '?' -> { tokens.add(Token.of(TokenType.QUESTION, "?", start)); i++; }
                case ':' -> { tokens.add(Token.of(TokenType.COLON, ":", start)); i++; }
                case ',' -> { tokens.add(Token.of(TokenType.COMMA, ",", start)); i++; }
                case '(' -> { tokens.add(Token.of(TokenType.LPAREN, "(", start)); i++; }
                case ')' -> { tokens.add(Token.of(TokenType.RPAREN, ")", start)); i++; }
                case '!' -> {
                    if (i + 1 < length && source.charAt(i + 1) == '=') {
                        tokens.add(Token.of(TokenType.BANG_EQ, "!=", start));
                        i += 2;
                    } else {
                        tokens.add(Token.of(TokenType.BANG, "!", start));
                        i++;
                    }
                }
                case '=' -> {
                    if (i + 1 < length && source.charAt(i + 1) == '=') {
                        tokens.add(Token.of(TokenType.EQ_EQ, "==", start));
                        i += 2;
                    } else {
                        throw new ExpressionSyntaxException("unexpected '=' at " + start + " (did you mean '=='?)");
                    }
                }
                case '<' -> {
                    if (i + 1 < length && source.charAt(i + 1) == '=') {
                        tokens.add(Token.of(TokenType.LE, "<=", start));
                        i += 2;
                    } else {
                        tokens.add(Token.of(TokenType.LT, "<", start));
                        i++;
                    }
                }
                case '>' -> {
                    if (i + 1 < length && source.charAt(i + 1) == '=') {
                        tokens.add(Token.of(TokenType.GE, ">=", start));
                        i += 2;
                    } else {
                        tokens.add(Token.of(TokenType.GT, ">", start));
                        i++;
                    }
                }
                case '&' -> {
                    if (i + 1 < length && source.charAt(i + 1) == '&') {
                        tokens.add(Token.of(TokenType.AMP_AMP, "&&", start));
                        i += 2;
                    } else {
                        throw new ExpressionSyntaxException("unexpected '&' at " + start + " (did you mean '&&'?)");
                    }
                }
                case '|' -> {
                    if (i + 1 < length && source.charAt(i + 1) == '|') {
                        tokens.add(Token.of(TokenType.PIPE_PIPE, "||", start));
                        i += 2;
                    } else {
                        throw new ExpressionSyntaxException("unexpected '|' at " + start + " (did you mean '||'?)");
                    }
                }
                default -> {
                    if (Character.isDigit(c)) {
                        while (i < length && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) {
                            i++;
                        }
                        String text = source.substring(start, i);
                        tokens.add(new Token(TokenType.NUMBER, text, Float.parseFloat(text), start));
                    } else if (Character.isLetter(c) || c == '_') {
                        while (i < length && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) {
                            i++;
                        }
                        tokens.add(Token.of(TokenType.IDENT, source.substring(start, i), start));
                    } else {
                        throw new ExpressionSyntaxException("unexpected character '" + c + "' at " + start);
                    }
                }
            }
        }
        tokens.add(Token.of(TokenType.EOF, "", length));
        return tokens;
    }
}
