package com.dimalab.storymodengine.common.scripting.lexer;

import com.dimalab.storymodengine.common.scripting.ast.SourcePos;
import com.dimalab.storymodengine.common.scripting.diagnostics.Severity;
import com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written, single-pass tokenizer — no external lexer-generator dependency, matching this
 * codebase's own "every DSL here is hand-rolled" convention (Flow/Dialogue/Quest/Trigger/Cinematic
 * are all fluent Java builders, nothing code-generated anywhere in the project).
 *
 * <p>Never throws on malformed input: an unterminated string or an unrecognized character is
 * recorded as an {@code SME0xx} diagnostic and lexing <em>resyncs</em> at the next whitespace, so
 * one bad line doesn't swallow every other diagnostic in the file — the collect-don't-abort
 * discipline this whole subsystem uses at every stage (see {@code DiagnosticReporter}).
 */
public final class SmeLexer {

    private final String file;
    private final String src;
    private final int len;
    private int pos;
    private int line = 1;
    private int col = 1;

    private final List<Token> tokens = new ArrayList<>();
    private final List<SmeDiagnostic> diagnostics = new ArrayList<>();

    private SmeLexer(String file, String src) {
        this.file = file;
        this.src = src;
        this.len = src.length();
    }

    public static LexResult lex(String file, String src) {
        SmeLexer lexer = new SmeLexer(file, src);
        lexer.run();
        return new LexResult(lexer.tokens, lexer.diagnostics);
    }

    private void run() {
        while (true) {
            skipWhitespaceAndComments();
            if (pos >= len) {
                tokens.add(new Token(TokenType.EOF, "", null, here()));
                return;
            }
            SourcePos start = here();
            char c = src.charAt(pos);

            if (isIdentStart(c)) {
                lexIdent(start);
            } else if (Character.isDigit(c)) {
                lexNumber(start);
            } else if (c == '"') {
                lexString(start);
            } else {
                lexSymbol(start, c);
            }
        }
    }

    private void skipWhitespaceAndComments() {
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == '\n') {
                advance();
            } else if (Character.isWhitespace(c)) {
                advance();
            } else if (c == '/' && peek(1) == '/') {
                while (pos < len && src.charAt(pos) != '\n') {
                    advance();
                }
            } else if (c == '/' && peek(1) == '*') {
                SourcePos start = here();
                advance();
                advance();
                boolean closed = false;
                while (pos < len) {
                    if (src.charAt(pos) == '*' && peek(1) == '/') {
                        advance();
                        advance();
                        closed = true;
                        break;
                    }
                    advance();
                }
                if (!closed) {
                    error(start, "SME001", "Unterminated block comment");
                }
            } else {
                return;
            }
        }
    }

    private void lexIdent(SourcePos start) {
        StringBuilder sb = new StringBuilder();
        sb.append(src.charAt(pos));
        advance();
        while (pos < len && isIdentPart(src.charAt(pos))) {
            sb.append(src.charAt(pos));
            advance();
        }

        boolean dotted = false;
        while (pos < len && src.charAt(pos) == '.' && isIdentStart(peek(1))) {
            dotted = true;
            sb.append('.');
            advance();
            sb.append(src.charAt(pos));
            advance();
            while (pos < len && isIdentPart(src.charAt(pos))) {
                sb.append(src.charAt(pos));
                advance();
            }
        }
        if (!dotted && pos < len && src.charAt(pos) == ':' && (isIdentStart(peek(1)) || Character.isDigit(peek(1)))) {
            sb.append(':');
            advance();
            while (pos < len && (isIdentPart(src.charAt(pos)) || src.charAt(pos) == ':' || src.charAt(pos) == '/')) {
                sb.append(src.charAt(pos));
                advance();
            }
        }

        tokens.add(new Token(TokenType.IDENT, sb.toString(), null, start));
    }

    private void lexNumber(SourcePos start) {
        StringBuilder sb = new StringBuilder();
        while (pos < len && Character.isDigit(src.charAt(pos))) {
            sb.append(src.charAt(pos));
            advance();
        }

        if (pos < len && src.charAt(pos) == '.' && Character.isDigit(peek(1))) {
            sb.append('.');
            advance();
            while (pos < len && Character.isDigit(src.charAt(pos))) {
                sb.append(src.charAt(pos));
                advance();
            }
            tokens.add(new Token(TokenType.DOUBLE, sb.toString(), Double.parseDouble(sb.toString()), start));
            return;
        }

        if (pos < len && isDurationSuffix(src.charAt(pos)) && !isIdentPart(peek(1))) {
            char suffix = src.charAt(pos);
            advance();
            long amount = Long.parseLong(sb.toString());
            int ticks = (int) (amount * switch (suffix) {
                case 's' -> 20;
                case 'm' -> 1200;
                default -> 1; // 't' — raw ticks
            });
            tokens.add(new Token(TokenType.DURATION, sb.toString() + suffix, ticks, start));
            return;
        }

        if (pos < len && isIdentPart(src.charAt(pos))) {
            // A digit run directly followed by an unrecognized letter — not a valid number or
            // duration suffix. Record and recover by treating what's consumed so far as the token.
            error(start, "SME002", "Invalid number literal near '" + sb + src.charAt(pos) + "'");
        }
        tokens.add(new Token(TokenType.INT, sb.toString(), Long.parseLong(sb.toString()), start));
    }

    private void lexString(SourcePos start) {
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        boolean closed = false;
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == '"') {
                advance();
                closed = true;
                break;
            }
            if (c == '\n') {
                break; // never consume across a raw newline — unterminated
            }
            if (c == '\\' && pos + 1 < len) {
                char next = src.charAt(pos + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); advance(); advance(); }
                    case '\\' -> { sb.append('\\'); advance(); advance(); }
                    case 'n' -> { sb.append('\n'); advance(); advance(); }
                    case 't' -> { sb.append('\t'); advance(); advance(); }
                    default -> {
                        error(here(), "SME003", "Unknown escape sequence '\\" + next + "'");
                        advance();
                        advance();
                    }
                }
            } else {
                sb.append(c);
                advance();
            }
        }
        if (!closed) {
            error(start, "SME004", "Unterminated string literal");
        }
        tokens.add(new Token(TokenType.STRING, sb.toString(), sb.toString(), start));
    }

    private void lexSymbol(SourcePos start, char c) {
        switch (c) {
            case '{' -> emit(start, TokenType.LBRACE, "{");
            case '}' -> emit(start, TokenType.RBRACE, "}");
            case '(' -> emit(start, TokenType.LPAREN, "(");
            case ')' -> emit(start, TokenType.RPAREN, ")");
            case ':' -> emit(start, TokenType.COLON, ":");
            case '@' -> emit(start, TokenType.AT, "@");
            case '!' -> {
                if (peek(1) == '=') {
                    advance();
                    emit(start, TokenType.NEQ, "!=");
                } else {
                    emit(start, TokenType.NOT, "!");
                }
            }
            case '=' -> {
                if (peek(1) == '=') {
                    advance();
                    emit(start, TokenType.EQEQ, "==");
                } else {
                    emit(start, TokenType.EQ, "=");
                }
            }
            case '+' -> {
                if (peek(1) == '=') {
                    advance();
                    emit(start, TokenType.PLUS_EQ, "+=");
                } else {
                    advance();
                    error(start, "SME005", "Unexpected character '+' — only '+=' is valid here");
                }
            }
            case '-' -> {
                if (peek(1) == '=') {
                    advance();
                    emit(start, TokenType.MINUS_EQ, "-=");
                } else {
                    // A signed numeric literal (coordinates are routinely negative) — not a general
                    // subtraction operator, since the grammar has no binary arithmetic at all; the
                    // parser only accepts this immediately before an INT/DOUBLE literal.
                    emit(start, TokenType.MINUS, "-");
                }
            }
            case '>' -> {
                if (peek(1) == '=') {
                    advance();
                    emit(start, TokenType.GTE, ">=");
                } else {
                    emit(start, TokenType.GT, ">");
                }
            }
            case '<' -> {
                if (peek(1) == '=') {
                    advance();
                    emit(start, TokenType.LTE, "<=");
                } else {
                    emit(start, TokenType.LT, "<");
                }
            }
            case '&' -> {
                if (peek(1) == '&') {
                    advance();
                    emit(start, TokenType.AND, "&&");
                } else {
                    advance();
                    error(start, "SME005", "Unexpected character '&' — did you mean '&&'?");
                }
            }
            case '|' -> {
                if (peek(1) == '|') {
                    advance();
                    emit(start, TokenType.OR, "||");
                } else {
                    advance();
                    error(start, "SME005", "Unexpected character '|' — did you mean '||'?");
                }
            }
            default -> {
                advance();
                error(start, "SME005", "Unexpected character '" + c + "'");
            }
        }
    }

    private void emit(SourcePos start, TokenType type, String text) {
        advance();
        tokens.add(new Token(type, text, null, start));
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isDurationSuffix(char c) {
        return c == 's' || c == 't' || c == 'm';
    }

    private char peek(int ahead) {
        int p = pos + ahead;
        return p < len ? src.charAt(p) : '\0';
    }

    private void advance() {
        if (pos >= len) {
            return;
        }
        if (src.charAt(pos) == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        pos++;
    }

    private SourcePos here() {
        return new SourcePos(file, line, col);
    }

    private void error(SourcePos at, String code, String message) {
        diagnostics.add(new SmeDiagnostic(Severity.ERROR, at, code, message));
    }
}
