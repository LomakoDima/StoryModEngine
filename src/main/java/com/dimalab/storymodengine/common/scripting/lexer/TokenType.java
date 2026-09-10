package com.dimalab.storymodengine.common.scripting.lexer;

/**
 * Deliberately no per-keyword token types ({@code STORY}/{@code IF}/{@code WAIT}/...) — every
 * keyword lexes as a plain {@link #IDENT}, and {@code SmeParser} recognizes one by comparing its
 * text (see {@code SmeParser#atKeyword}). This keeps the lexer's alphabet small and avoids a ~50-
 * constant enum that would need to grow every time the grammar gains one more keyword; a
 * hand-written recursive-descent parser loses nothing by checking text instead of an enum constant,
 * since every keyword position in this grammar is unambiguous context anyway (verified in the design
 * doc's grammar notes: no two statement/declaration kinds share a leading keyword).
 */
public enum TokenType {
    IDENT,
    STRING,
    INT,
    DOUBLE,
    DURATION,
    LBRACE, RBRACE,
    LPAREN, RPAREN,
    COLON,
    AT,
    EQ, PLUS_EQ, MINUS_EQ, MINUS,
    EQEQ, NEQ, GT, LT, GTE, LTE,
    AND, OR, NOT,
    EOF
}
