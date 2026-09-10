package com.dimalab.storymodengine.common.scripting.lexer;

import com.dimalab.storymodengine.common.scripting.ast.SourcePos;

/**
 * One lexed token. {@code text} is always the raw lexeme (for {@link TokenType#STRING} this is the
 * *unescaped* content, not the source text including quotes). {@code value} is non-null only for
 * {@link TokenType#INT} ({@code Long}), {@link TokenType#DOUBLE} ({@code Double}), and {@link
 * TokenType#DURATION} (already-normalized {@code Integer} tick count) — the parser never re-parses
 * a numeric lexeme itself.
 */
public record Token(TokenType type, String text, Object value, SourcePos pos) {

    public boolean isIdent(String keyword) {
        return type == TokenType.IDENT && text.equals(keyword);
    }
}
