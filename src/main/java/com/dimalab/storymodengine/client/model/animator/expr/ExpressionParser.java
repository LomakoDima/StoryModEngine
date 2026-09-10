package com.dimalab.storymodengine.client.model.animator.expr;

import java.util.List;

/**
 * Recursive-descent parser for the animation expression grammar. Recursive-descent, not a Pratt
 * parser, because the grammar is fixed at 4 binary-precedence levels with no user-defined operators —
 * a hand-rolled table would be more machinery to review, not less.
 *
 * <p>Grammar, lowest to highest precedence:
 * <pre>
 * expr           := ternary
 * ternary        := logicalOr ( '?' expr ':' expr )?
 * logicalOr      := logicalAnd ( '||' logicalAnd )*
 * logicalAnd     := equality ( '&&' equality )*
 * equality       := relational ( ('==' | '!=') relational )*
 * relational     := additive ( ('<' | '>' | '<=' | '>=') additive )*
 * additive       := multiplicative ( ('+' | '-') multiplicative )*
 * multiplicative := unary ( ('*' | '/') unary )*
 * unary          := ('!' | '-')? primary
 * primary        := NUMBER | 'true' | 'false' | 'pi' | IDENT '(' args? ')' | IDENT '.' IDENT | IDENT | '(' expr ')'
 * args           := expr (',' expr)*
 * </pre>
 * No assignment production — every use site of an {@link AnimationExpression} is read-only.
 *
 * <p>{@code query}/{@code math} are "receivers" in HollowEngine's own sense — reachable without their
 * namespace prefix, matching the ported presets' own expression text (e.g. {@code is_alive}, not
 * {@code query.is_alive}; {@code clamp(...)}, not {@code math.clamp(...)}). A bareword function call
 * (the {@code IDENT '(' args? ')'} production) is always a {@code math.*} function — see {@link
 * CompiledExpression#evalFunction} for the full set; a bareword with no parens and no {@code '.'} is
 * always an implicit {@code query.<name>} lookup — see {@link ExprNode.NamespaceAccess}.
 */
final class ExpressionParser {

    private final List<Lexer.Token> tokens;
    private int pos;

    private ExpressionParser(List<Lexer.Token> tokens) {
        this.tokens = tokens;
    }

    static ExprNode parse(String source) {
        ExpressionParser parser = new ExpressionParser(Lexer.tokenize(source));
        ExprNode node = parser.expr();
        parser.expect(Lexer.TokenType.EOF, "end of expression");
        return node;
    }

    private ExprNode expr() {
        return ternary();
    }

    private ExprNode ternary() {
        ExprNode condition = logicalOr();
        if (check(Lexer.TokenType.QUESTION)) {
            advance();
            ExprNode ifTrue = expr();
            expect(Lexer.TokenType.COLON, "':' in ternary expression");
            ExprNode ifFalse = expr();
            return new ExprNode.Ternary(condition, ifTrue, ifFalse);
        }
        return condition;
    }

    private ExprNode logicalOr() {
        ExprNode left = logicalAnd();
        while (check(Lexer.TokenType.PIPE_PIPE)) {
            advance();
            left = new ExprNode.Binary("||", left, logicalAnd());
        }
        return left;
    }

    private ExprNode logicalAnd() {
        ExprNode left = equality();
        while (check(Lexer.TokenType.AMP_AMP)) {
            advance();
            left = new ExprNode.Binary("&&", left, equality());
        }
        return left;
    }

    private ExprNode equality() {
        ExprNode left = relational();
        while (check(Lexer.TokenType.EQ_EQ) || check(Lexer.TokenType.BANG_EQ)) {
            String op = advance().text();
            left = new ExprNode.Binary(op, left, relational());
        }
        return left;
    }

    private ExprNode relational() {
        ExprNode left = additive();
        while (check(Lexer.TokenType.LT) || check(Lexer.TokenType.GT)
                || check(Lexer.TokenType.LE) || check(Lexer.TokenType.GE)) {
            String op = advance().text();
            left = new ExprNode.Binary(op, left, additive());
        }
        return left;
    }

    private ExprNode additive() {
        ExprNode left = multiplicative();
        while (check(Lexer.TokenType.PLUS) || check(Lexer.TokenType.MINUS)) {
            String op = advance().text();
            left = new ExprNode.Binary(op, left, multiplicative());
        }
        return left;
    }

    private ExprNode multiplicative() {
        ExprNode left = unary();
        while (check(Lexer.TokenType.STAR) || check(Lexer.TokenType.SLASH)) {
            String op = advance().text();
            left = new ExprNode.Binary(op, left, unary());
        }
        return left;
    }

    private ExprNode unary() {
        if (check(Lexer.TokenType.BANG)) {
            advance();
            return new ExprNode.Unary('!', unary());
        }
        if (check(Lexer.TokenType.MINUS)) {
            advance();
            return new ExprNode.Unary('-', unary());
        }
        return primary();
    }

    private ExprNode primary() {
        Lexer.Token token = peek();
        switch (token.type()) {
            case NUMBER -> {
                advance();
                return new ExprNode.NumberLiteral(token.number());
            }
            case IDENT -> {
                advance();
                if (token.text().equals("true")) {
                    return new ExprNode.NumberLiteral(1f);
                }
                if (token.text().equals("false")) {
                    return new ExprNode.NumberLiteral(0f);
                }
                // math.pi reachable unqualified as a plain constant (HollowEngine's own convention —
                // math/query namespaces are "receivers," reachable without their prefix) — a property,
                // not a function, so it's a literal here rather than a case in evalFunction.
                if (token.text().equals("pi")) {
                    return new ExprNode.NumberLiteral((float) Math.PI);
                }
                if (check(Lexer.TokenType.LPAREN)) {
                    return functionCall(token.text());
                }
                if (check(Lexer.TokenType.DOT)) {
                    advance();
                    Lexer.Token keyToken = expect(Lexer.TokenType.IDENT, "identifier after '.'");
                    return new ExprNode.NamespaceAccess(token.text(), keyToken.text());
                }
                // A bare identifier with no namespace prefix is an implicit query.* lookup — matches
                // HollowEngine's own "query is reachable unqualified" convention, and is what every
                // ported HE preset's expression text already assumes (e.g. "is_alive", not
                // "query.is_alive"). Previously this built NamespaceAccess(token.text(), null), which
                // CompiledExpression.evalNamespace always resolves to 0 — silently breaking every bare
                // reference until each one was hand-prefixed with "query." one at a time.
                return new ExprNode.NamespaceAccess("query", token.text());
            }
            case LPAREN -> {
                advance();
                ExprNode inner = expr();
                expect(Lexer.TokenType.RPAREN, "')'");
                return inner;
            }
            default -> throw new ExpressionSyntaxException("unexpected token '" + token.text() + "' at " + token.pos());
        }
    }

    /** Called with the opening {@code '('} still unconsumed — parses {@code name(arg, arg, ...)}, empty argument list allowed. */
    private ExprNode functionCall(String name) {
        advance(); // '('
        List<ExprNode> args = new java.util.ArrayList<>();
        if (!check(Lexer.TokenType.RPAREN)) {
            args.add(expr());
            while (check(Lexer.TokenType.COMMA)) {
                advance();
                args.add(expr());
            }
        }
        expect(Lexer.TokenType.RPAREN, "')'");
        return new ExprNode.FunctionCall(name, args);
    }

    private Lexer.Token peek() {
        return tokens.get(pos);
    }

    private boolean check(Lexer.TokenType type) {
        return peek().type() == type;
    }

    private Lexer.Token advance() {
        Lexer.Token token = tokens.get(pos);
        if (token.type() != Lexer.TokenType.EOF) {
            pos++;
        }
        return token;
    }

    private Lexer.Token expect(Lexer.TokenType type, String description) {
        if (!check(type)) {
            throw new ExpressionSyntaxException("expected " + description + " but found '" + peek().text() + "' at " + peek().pos());
        }
        return advance();
    }
}
