package com.dimalab.storymodengine.common.scripting.parser;

import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.diagnostics.Severity;
import com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic;
import com.dimalab.storymodengine.common.scripting.lexer.Token;
import com.dimalab.storymodengine.common.scripting.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hand-written recursive-descent parser — one method per grammar production in the design doc.
 * Like {@code SmeLexer}, this never throws on malformed input in normal operation: a syntax error is
 * recorded as an {@code SME1xx} diagnostic and the parser performs panic-mode recovery (skip to the
 * next token that plausibly starts a new declaration/statement, or the enclosing {@code }}), so one
 * broken dialogue doesn't prevent every other declaration in the same file from being parsed and
 * reported on independently.
 *
 * <p><b>{@code dialogue} body shape</b>: bare entries (no {@code node} label) accumulate into an
 * implicit {@code "start"} node; a bare {@code node <id>} label (no braces — a switch marker, not
 * {@code node <id> { ... }}) starts a new named node that subsequent entries accumulate into, until
 * the next label or the closing brace. This lets one dialogue mix a short unlabeled opening with
 * labeled continuation nodes reached via {@code jump}, exactly the shape the design doc's own
 * {@code prologue.sme} example uses.
 */
public final class SmeParser {

    private static final Set<String> TOP_DECL_KEYWORDS = Set.of("var", "dialogue", "quest", "trigger", "sequence", "include", "play", "npc", "shop");
    private static final Set<String> STMT_KEYWORDS = Set.of(
            "set", "var", "if", "raycast", "wait", "jump", "call", "return", "end",
            "start", "play", "dialogue", "cinematic");

    private final String file;
    private final List<Token> tokens;
    private final List<SmeDiagnostic> diagnostics = new ArrayList<>();
    private int idx;

    private SmeParser(String file, List<Token> tokens) {
        this.file = file;
        this.tokens = tokens;
    }

    public static ParseResult parse(String file, List<Token> tokens) {
        SmeParser parser = new SmeParser(file, tokens);
        List<StoryNode> stories = new ArrayList<>();
        while (!parser.check(TokenType.EOF)) {
            List<MetadataTag> tags = parser.parseMetaTags();
            if (parser.checkIdent("story")) {
                stories.add(parser.parseStory(tags));
            } else {
                parser.error(parser.peek(), "SME100", "Expected 'story' at file scope");
                parser.advance();
            }
        }
        return new ParseResult(stories, parser.diagnostics);
    }

    // ---------- story / top-level ----------

    private StoryNode parseStory(List<MetadataTag> tags) {
        SourcePos pos = peekPos();
        advance(); // "story"
        String id = expectAnyIdent("story id");
        expect(TokenType.LBRACE, "'{'");
        List<SmeNode> decls = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            List<MetadataTag> declTags = parseMetaTags();
            SmeNode decl = parseTopDecl(declTags);
            if (decl != null) {
                decls.add(decl);
            }
        }
        expect(TokenType.RBRACE, "'}'");
        return new StoryNode(pos, id, tags, decls);
    }

    private List<MetadataTag> parseMetaTags() {
        List<MetadataTag> tags = new ArrayList<>();
        while (check(TokenType.AT)) {
            SourcePos pos = peekPos();
            advance();
            String name = expectAnyIdent("metadata tag name");
            List<Object> args = new ArrayList<>();
            if (match(TokenType.LPAREN)) {
                if (!check(TokenType.RPAREN)) {
                    args.add(parseMetaArg());
                    while (match(TokenType.COLON) || false) {
                        // metadata args are simply space/nothing-separated literals; no separator token
                        break;
                    }
                    while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
                        args.add(parseMetaArg());
                    }
                }
                expect(TokenType.RPAREN, "')'");
            }
            tags.add(new MetadataTag(pos, name, args));
        }
        return tags;
    }

    private Object parseMetaArg() {
        Token t = peek();
        return switch (t.type()) {
            case STRING -> { advance(); yield t.value(); }
            case INT -> { advance(); yield t.value(); }
            case DOUBLE -> { advance(); yield t.value(); }
            case IDENT -> { advance(); yield t.text(); }
            default -> {
                error(t, "SME101", "Expected a metadata tag argument");
                advance();
                yield "";
            }
        };
    }

    private SmeNode parseTopDecl(List<MetadataTag> tags) {
        Token t = peek();
        if (t.type() != TokenType.IDENT) {
            error(t, "SME102", "Expected a declaration (var/dialogue/quest/trigger/sequence/include)");
            panicRecover(TOP_DECL_KEYWORDS);
            return null;
        }
        return switch (t.text()) {
            case "var" -> parseVarDecl();
            case "dialogue" -> parseDialogueDecl(tags);
            case "quest" -> parseQuestDecl(tags);
            case "trigger" -> parseTriggerDecl(tags);
            case "sequence" -> parseSequenceDecl(tags);
            case "include" -> parseIncludeDecl();
            case "npc" -> parseNpcDecl(tags);
            case "shop" -> parseShopDecl(tags);
            default -> {
                error(t, "SME102", "Expected a declaration (var/dialogue/quest/trigger/sequence/include/npc/shop), found '" + t.text() + "'");
                panicRecover(TOP_DECL_KEYWORDS);
                yield null;
            }
        };
    }

    private IncludeNode parseIncludeDecl() {
        SourcePos pos = peekPos();
        advance(); // "include"
        String path = expectString("include path");
        return new IncludeNode(pos, path);
    }

    // ---------- variables ----------

    private VarDeclNode parseVarDecl() {
        SourcePos pos = peekPos();
        advance(); // "var"
        String name = expectAnyIdent("variable name");
        expect(TokenType.EQ, "'='");
        LiteralExprNode initial = parseLiteral();
        return new VarDeclNode(pos, name, initial);
    }

    private LiteralExprNode parseLiteral() {
        Token t = peek();
        return switch (t.type()) {
            case STRING -> { advance(); yield new LiteralExprNode(t.pos(), SmeValueType.STRING, t.value()); }
            case INT -> { advance(); yield new LiteralExprNode(t.pos(), SmeValueType.INT, t.value()); }
            case DOUBLE -> { advance(); yield new LiteralExprNode(t.pos(), SmeValueType.DOUBLE, t.value()); }
            case IDENT -> {
                if (t.text().equals("true") || t.text().equals("false")) {
                    advance();
                    yield new LiteralExprNode(t.pos(), SmeValueType.BOOL, Boolean.valueOf(t.text()));
                }
                error(t, "SME103", "Expected a literal value (bool/int/double/string)");
                advance();
                yield new LiteralExprNode(t.pos(), SmeValueType.BOOL, Boolean.FALSE);
            }
            default -> {
                error(t, "SME103", "Expected a literal value (bool/int/double/string)");
                advance();
                yield new LiteralExprNode(t.pos(), SmeValueType.BOOL, Boolean.FALSE);
            }
        };
    }

    /**
     * {@code '-' (INT | DOUBLE)} — a signed numeric literal, not a general unary-minus operator: the
     * grammar has no binary arithmetic at all (no {@code +}/{@code -} outside {@code +=}/{@code -=}),
     * so there is nothing for a standalone minus to disambiguate against. Negating at parse time
     * (rather than introducing a runtime {@code UnaryOp.NEG}) keeps every downstream consumer of
     * {@link LiteralExprNode} — compiler, coercion, self-tests — unaware anything changed.
     */
    private LiteralExprNode parseSignedNumberLiteral() {
        SourcePos pos = peekPos();
        advance(); // '-'
        Token t = peek();
        if (t.type() == TokenType.INT) {
            advance();
            return new LiteralExprNode(pos, SmeValueType.INT, -(Long) t.value());
        }
        if (t.type() == TokenType.DOUBLE) {
            advance();
            return new LiteralExprNode(pos, SmeValueType.DOUBLE, -(Double) t.value());
        }
        error(t, "SME103", "Expected a number after '-'");
        return new LiteralExprNode(pos, SmeValueType.BOOL, Boolean.FALSE);
    }

    // ---------- expressions ----------

    private ExprNode parseExpr() {
        return parseOr();
    }

    private ExprNode parseOr() {
        ExprNode left = parseAnd();
        while (check(TokenType.OR)) {
            SourcePos pos = peekPos();
            advance();
            ExprNode right = parseAnd();
            left = new BinaryExprNode(pos, BinaryOp.OR, left, right);
        }
        return left;
    }

    private ExprNode parseAnd() {
        ExprNode left = parseNot();
        while (check(TokenType.AND)) {
            SourcePos pos = peekPos();
            advance();
            ExprNode right = parseNot();
            left = new BinaryExprNode(pos, BinaryOp.AND, left, right);
        }
        return left;
    }

    private ExprNode parseNot() {
        if (check(TokenType.NOT)) {
            SourcePos pos = peekPos();
            advance();
            return new UnaryExprNode(pos, UnaryOp.NOT, parseCmp());
        }
        return parseCmp();
    }

    private ExprNode parseCmp() {
        ExprNode left = parsePrimary();
        BinaryOp op = switch (peek().type()) {
            case EQEQ -> BinaryOp.EQ;
            case NEQ -> BinaryOp.NEQ;
            case GT -> BinaryOp.GT;
            case LT -> BinaryOp.LT;
            case GTE -> BinaryOp.GTE;
            case LTE -> BinaryOp.LTE;
            default -> null;
        };
        if (op != null) {
            SourcePos pos = peekPos();
            advance();
            ExprNode right = parsePrimary();
            return new BinaryExprNode(pos, op, left, right);
        }
        return left;
    }

    private ExprNode parsePrimary() {
        if (match(TokenType.LPAREN)) {
            ExprNode inner = parseExpr();
            expect(TokenType.RPAREN, "')'");
            return inner;
        }
        if (check(TokenType.MINUS)) {
            return parseSignedNumberLiteral();
        }
        Token t = peek();
        if (t.type() == TokenType.STRING || t.type() == TokenType.INT || t.type() == TokenType.DOUBLE) {
            return parseLiteral();
        }
        if (t.type() == TokenType.IDENT) {
            if (t.text().equals("true") || t.text().equals("false")) {
                return parseLiteral();
            }
            advance();
            return new VarRefExprNode(t.pos(), t.text());
        }
        error(t, "SME104", "Expected an expression");
        advance();
        return new LiteralExprNode(t.pos(), SmeValueType.BOOL, Boolean.FALSE);
    }

    // ---------- blocks / statements ----------

    private List<StmtNode> parseBlock() {
        expect(TokenType.LBRACE, "'{'");
        List<StmtNode> stmts = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            StmtNode stmt = parseStmt();
            if (stmt != null) {
                stmts.add(stmt);
            }
        }
        expect(TokenType.RBRACE, "'}'");
        return stmts;
    }

    private StmtNode parseStmt() {
        Token t = peek();
        if (t.type() != TokenType.IDENT) {
            error(t, "SME105", "Expected a statement");
            panicRecoverStmt();
            return null;
        }
        return switch (t.text()) {
            case "set" -> parseSetStmt();
            case "var" -> parseVarDecl();
            case "if" -> peekIsIdentPrefix(1, "raycast.") ? parseRaycastSugar() : parseIfStmt();
            case "raycast" -> parseRaycastBlock();
            case "wait" -> peekIsIdent(1, "event") ? parseWaitEventStmt() : parseWaitStmt();
            case "jump" -> { SourcePos p = t.pos(); advance(); yield new JumpStmtNode(p, expectAnyIdent("jump target")); }
            case "call" -> parseCallStmt();
            case "return" -> { advance(); yield new ReturnStmtNode(t.pos()); }
            case "end" -> { advance(); yield new EndStmtNode(t.pos()); }
            case "start" -> parseStartStmt();
            case "play" -> parsePlayStmt();
            case "dialogue" -> { SourcePos p = t.pos(); advance(); yield new StartDialogueStmtNode(p, expectAnyIdent("dialogue id")); }
            case "cinematic" -> { SourcePos p = t.pos(); advance(); yield new PlayCinematicStmtNode(p, expectAnyIdent("cutscene id")); }
            default -> parseActionCallStmt();
        };
    }

    private SetStmtNode parseSetStmt() {
        SourcePos pos = peekPos();
        advance(); // "set"
        String varName = expectAnyIdent("variable name");
        SetOp op = switch (peek().type()) {
            case EQ -> SetOp.ASSIGN;
            case PLUS_EQ -> SetOp.ADD_ASSIGN;
            case MINUS_EQ -> SetOp.SUB_ASSIGN;
            default -> {
                error(peek(), "SME106", "Expected '=', '+=' or '-=' in a set statement");
                yield SetOp.ASSIGN;
            }
        };
        if (peek().type() == TokenType.EQ || peek().type() == TokenType.PLUS_EQ || peek().type() == TokenType.MINUS_EQ) {
            advance();
        }
        ExprNode value = parseExpr();
        return new SetStmtNode(pos, varName, op, value);
    }

    private IfStmtNode parseIfStmt() {
        SourcePos pos = peekPos();
        advance(); // "if"
        expect(TokenType.LPAREN, "'('");
        ExprNode cond = parseExpr();
        expect(TokenType.RPAREN, "')'");
        List<StmtNode> thenBlock = parseBlock();
        List<ElseIfBranch> elseIfs = new ArrayList<>();
        while (checkIdent("elseif")) {
            advance();
            expect(TokenType.LPAREN, "'('");
            ExprNode elseIfCond = parseExpr();
            expect(TokenType.RPAREN, "')'");
            elseIfs.add(new ElseIfBranch(elseIfCond, parseBlock()));
        }
        List<StmtNode> elseBlock = List.of();
        if (checkIdent("else")) {
            advance();
            elseBlock = parseBlock();
        }
        return new IfStmtNode(pos, cond, thenBlock, elseIfs, elseBlock);
    }

    private WaitStmtNode parseWaitStmt() {
        SourcePos pos = peekPos();
        advance(); // "wait"
        Token t = peek();
        int ticks;
        if (t.type() == TokenType.DURATION) {
            ticks = (Integer) t.value();
            advance();
        } else if (t.type() == TokenType.INT) {
            ticks = (int) (long) (Long) t.value();
            advance();
        } else {
            error(t, "SME107", "Expected a tick count or duration (e.g. '20' or '2s') after 'wait'");
            ticks = 0;
        }
        return new WaitStmtNode(pos, ticks);
    }

    private WaitEventStmtNode parseWaitEventStmt() {
        SourcePos pos = peekPos();
        advance(); // "wait"
        advance(); // "event"
        return new WaitEventStmtNode(pos, expectAnyIdent("event name"));
    }

    private CallStmtNode parseCallStmt() {
        SourcePos pos = peekPos();
        advance(); // "call"
        String id = expectAnyIdent("sequence id");
        List<ExprNode> args = parseArgListOptional();
        return new CallStmtNode(pos, id, args);
    }

    private StmtNode parseStartStmt() {
        SourcePos pos = peekPos();
        advance(); // "start"
        if (checkIdent("quest")) {
            advance();
            return new StartQuestStmtNode(pos, expectAnyIdent("quest id"));
        }
        if (checkIdent("dialogue")) {
            advance();
            return new StartDialogueStmtNode(pos, expectAnyIdent("dialogue id"));
        }
        error(peek(), "SME108", "Expected 'quest' or 'dialogue' after 'start'");
        panicRecoverStmt();
        return new EndStmtNode(pos);
    }

    private StmtNode parsePlayStmt() {
        SourcePos pos = peekPos();
        advance(); // "play"
        if (checkIdent("cinematic")) {
            advance();
            return new PlayCinematicStmtNode(pos, expectAnyIdent("cutscene id"));
        }
        error(peek(), "SME109", "Expected 'cinematic' after 'play'");
        panicRecoverStmt();
        return new EndStmtNode(pos);
    }

    private ActionCallStmtNode parseActionCallStmt() {
        SourcePos pos = peekPos();
        String name = expectAnyIdent("command name");
        // A bare 'player' argument immediately after the command name is the implicit self-target
        // sentinel — dropped here rather than carried as an argument (see ActionCallCompiler).
        if (checkIdent("player")) {
            advance();
        }
        List<ExprNode> args = parseArgListOptional();
        return new ActionCallStmtNode(pos, name, args);
    }

    /**
     * There is no explicit argument-list terminator or statement separator in this grammar — {@code
     * isArgStart} alone can't tell "one more argument to this call" from "the next statement," since
     * an unreserved bareword (an item id, a clip name, or another command's own name) is exactly the
     * same shape either way. Every real {@code .sme} file already puts one statement per line (see
     * {@code prologue.sme}), so a source-line boundary is the one signal available that isn't itself
     * ambiguous: stop the list once the next token starts a new line. Found by two consecutive plain
     * commands on their own lines being silently merged into one call with a wildly wrong argument
     * count (a fresh command's own name and args swallowed as if they belonged to the previous call).
     */
    private List<ExprNode> parseArgListOptional() {
        List<ExprNode> args = new ArrayList<>();
        while (isArgStart(peek()) && sameLineAsPreviousToken()) {
            args.add(parseArg());
        }
        return args;
    }

    private boolean sameLineAsPreviousToken() {
        return idx > 0 && tokens.get(idx - 1).pos().line() == peek().pos().line();
    }

    private boolean isArgStart(Token t) {
        return t.type() == TokenType.STRING || t.type() == TokenType.INT || t.type() == TokenType.DOUBLE
                || t.type() == TokenType.MINUS
                || (t.type() == TokenType.IDENT && !STMT_KEYWORDS.contains(t.text()) && !t.text().equals("when"));
    }

    private ExprNode parseArg() {
        Token t = peek();
        if (t.type() == TokenType.MINUS) {
            return parseSignedNumberLiteral();
        }
        if (t.type() == TokenType.STRING || t.type() == TokenType.INT || t.type() == TokenType.DOUBLE) {
            return parseLiteral();
        }
        if (t.text().equals("true") || t.text().equals("false")) {
            return parseLiteral();
        }
        advance();
        return new RefExprNode(t.pos(), t.text());
    }

    /**
     * {@code if raycast.entity { } else { }} — the lexer already folds {@code raycast.entity} into
     * one {@code IDENT} token (a dot immediately followed by a letter always continues the same
     * identifier, see {@code SmeLexer#lexIdent}), so this reads as a single token, not "raycast"
     * plus a separate "." plus "entity".
     */
    private RaycastIfStmtNode parseRaycastSugar() {
        SourcePos pos = peekPos();
        advance(); // "if"
        Token raycastToken = advance(); // "raycast.<target>"
        RaycastTarget target = targetFromSuffix(raycastToken.text());
        List<StmtNode> thenBlock = parseBlock();
        List<StmtNode> elseBlock = List.of();
        if (checkIdent("else")) {
            advance();
            elseBlock = parseBlock();
        }
        return new RaycastIfStmtNode(pos, target, 20, false, thenBlock, elseBlock);
    }

    private RaycastTarget targetFromSuffix(String text) {
        int dot = text.indexOf('.');
        String suffix = dot >= 0 ? text.substring(dot + 1) : text;
        return switch (suffix) {
            case "entity" -> RaycastTarget.ENTITY;
            case "block" -> RaycastTarget.BLOCK;
            default -> RaycastTarget.ANY;
        };
    }

    private RaycastIfStmtNode parseRaycastBlock() {
        SourcePos pos = peekPos();
        advance(); // "raycast"
        expect(TokenType.LBRACE, "'{'");
        int distance = 20;
        RaycastTarget target = RaycastTarget.ANY;
        boolean living = false;
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            if (checkIdent("distance")) {
                advance();
                distance = (int) (long) (Long) expect(TokenType.INT, "distance value").value();
            } else if (checkIdent("entities")) {
                advance();
                target = RaycastTarget.ENTITY;
            } else if (checkIdent("blocks")) {
                advance();
                target = RaycastTarget.BLOCK;
            } else if (checkIdent("any")) {
                advance();
                target = RaycastTarget.ANY;
            } else if (checkIdent("living")) {
                advance();
                living = true;
            } else {
                error(peek(), "SME110", "Expected a raycast option (distance/entities/blocks/any/living)");
                advance();
            }
        }
        expect(TokenType.RBRACE, "'}'");
        List<StmtNode> thenBlock = parseBlock();
        List<StmtNode> elseBlock = List.of();
        if (checkIdent("else")) {
            advance();
            elseBlock = parseBlock();
        }
        return new RaycastIfStmtNode(pos, target, distance, living, thenBlock, elseBlock);
    }

    // ---------- dialogue ----------

    private DialogueDeclNode parseDialogueDecl(List<MetadataTag> tags) {
        SourcePos declPos = peekPos();
        advance(); // "dialogue"
        String id = expectAnyIdent("dialogue id");
        expect(TokenType.LBRACE, "'{'");

        List<DialogueNodeNode> nodes = new ArrayList<>();
        String currentId = "start";
        SourcePos currentPos = declPos;
        boolean currentIsSynthetic = true;
        List<DialogueEntryNode> currentEntries = new ArrayList<>();

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            if (checkIdent("node")) {
                if (!currentEntries.isEmpty() || !currentIsSynthetic) {
                    nodes.add(new DialogueNodeNode(currentPos, currentId, currentEntries));
                }
                advance();
                currentPos = peekPos();
                currentId = expectAnyIdent("node id");
                currentEntries = new ArrayList<>();
                currentIsSynthetic = false;
            } else {
                DialogueEntryNode entry = parseDialogueEntry();
                if (entry != null) {
                    currentEntries.add(entry);
                }
            }
        }
        if (!currentEntries.isEmpty() || !currentIsSynthetic) {
            nodes.add(new DialogueNodeNode(currentPos, currentId, currentEntries));
        }
        expect(TokenType.RBRACE, "'}'");
        if (nodes.isEmpty()) {
            error(declPos, "SME111", "Dialogue '" + id + "' has no content");
        }
        return new DialogueDeclNode(declPos, id, tags, nodes);
    }

    private DialogueEntryNode parseDialogueEntry() {
        Token t = peek();
        if (t.type() == TokenType.LBRACE) {
            return new ActionEntryNode(t.pos(), parseBlock());
        }
        if (t.type() != TokenType.IDENT) {
            error(t, "SME112", "Expected a dialogue line, choice, gate, jump, end, or action block");
            panicRecoverStmt();
            return null;
        }
        return switch (t.text()) {
            case "choice" -> parseChoiceGroup();
            case "gate" -> { advance(); expect(TokenType.LPAREN, "'('"); ExprNode cond = parseExpr(); expect(TokenType.RPAREN, "')'"); yield new GateEntryNode(t.pos(), cond); }
            case "jump" -> { advance(); yield new JumpEntryNode(t.pos(), expectAnyIdent("jump target")); }
            case "end" -> { advance(); yield new EndEntryNode(t.pos()); }
            default -> {
                // A bare "Speaker: text" line.
                if (peekIs(1, TokenType.COLON)) {
                    advance(); // speaker
                    advance(); // ':'
                    yield new LineEntryNode(t.pos(), t.text(), expectString("line text"));
                }
                error(t, "SME112", "Expected a dialogue line ('Speaker: \"text\"'), choice, gate, jump, or end");
                panicRecoverStmt();
                yield null;
            }
        };
    }

    private ChoiceGroupNode parseChoiceGroup() {
        SourcePos pos = peekPos();
        advance(); // "choice"
        expect(TokenType.LBRACE, "'{'");
        List<ChoiceNode> options = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            options.add(parseChoiceOption());
        }
        expect(TokenType.RBRACE, "'}'");
        return new ChoiceGroupNode(pos, options);
    }

    private ChoiceNode parseChoiceOption() {
        SourcePos pos = peekPos();
        String text = expectString("choice text");
        ExprNode condition = null;
        if (checkIdent("when")) {
            advance();
            expect(TokenType.LPAREN, "'('");
            condition = parseExpr();
            expect(TokenType.RPAREN, "')'");
        }
        List<StmtNode> body = parseBlock();
        return new ChoiceNode(pos, text, condition, body);
    }

    // ---------- npc ----------

    private static final Set<String> NPC_BEHAVIOR_KEYWORDS = Set.of("passive", "hostile", "stationary");

    private NpcDeclNode parseNpcDecl(List<MetadataTag> tags) {
        SourcePos pos = peekPos();
        advance(); // "npc"
        String name = expectString("npc name");
        expect(TokenType.LBRACE, "'{'");

        String model = null;
        String skin = null;
        String behavior = null;
        String displayName = null;
        List<NpcAttributeSpec> attributes = new ArrayList<>();
        double x = 0, y = 0, z = 0;
        boolean sawPos = false;
        List<StmtNode> onInteract = List.of();
        List<StmtNode> onShiftInteract = List.of();

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            if (checkIdent("model")) {
                advance();
                model = expectString("npc model");
            } else if (checkIdent("skin")) {
                advance();
                skin = expectString("npc skin owner");
            } else if (checkIdent("displayName")) {
                advance();
                displayName = expectString("npc display name");
            } else if (checkIdent("attribute")) {
                advance();
                String attrId = expectString("attribute id");
                double attrValue = expectSignedNumber("attribute value");
                attributes.add(new NpcAttributeSpec(attrId, attrValue));
            } else if (checkIdent("behavior")) {
                advance();
                behavior = expectAnyIdent("npc behavior (passive/hostile/stationary)");
                if (!NPC_BEHAVIOR_KEYWORDS.contains(behavior)) {
                    error(pos, "SME122", "Unknown npc behavior '" + behavior + "'");
                }
            } else if (checkIdent("pos")) {
                advance();
                x = expectSignedNumber("npc pos x");
                y = expectSignedNumber("npc pos y");
                z = expectSignedNumber("npc pos z");
                sawPos = true;
            } else if (checkIdent("onInteract")) {
                advance();
                onInteract = parseBlock();
            } else if (checkIdent("onShiftInteract")) {
                advance();
                onShiftInteract = parseBlock();
            } else {
                error(peek(), "SME123", "Expected model/skin/behavior/displayName/attribute/pos/onInteract/onShiftInteract");
                panicRecoverStmt();
            }
        }
        expect(TokenType.RBRACE, "'}'");
        if (model == null) {
            error(pos, "SME124", "npc '" + name + "' has no 'model'");
        }
        if (!sawPos) {
            error(pos, "SME124", "npc '" + name + "' has no 'pos'");
        }
        return new NpcDeclNode(pos, name, tags, model, skin, behavior, displayName, attributes, x, y, z, onInteract, onShiftInteract);
    }

    /**
     * {@code shop "<name>" { currency "<itemId>" item "<itemId>" price <n> [count <n>] ... }} — flat
     * repeated-field body, same shape {@code parseNpcDecl}'s {@code attribute} lines already use.
     */
    private ShopDeclNode parseShopDecl(List<MetadataTag> tags) {
        SourcePos pos = peekPos();
        advance(); // "shop"
        String name = expectString("shop name");
        expect(TokenType.LBRACE, "'{'");

        String currencyItemId = null;
        List<ShopItemSpec> items = new ArrayList<>();

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            if (checkIdent("currency")) {
                advance();
                currencyItemId = expectString("shop currency item id");
            } else if (checkIdent("item")) {
                advance();
                String itemId = expectString("shop item id");
                if (!checkIdent("price")) {
                    error(peek(), "SME126", "shop item '" + itemId + "' has no 'price'");
                    panicRecoverStmt();
                    continue;
                }
                advance();
                int price = (int) (long) (Long) expect(TokenType.INT, "shop item price").value();
                int count = 1;
                if (checkIdent("count")) {
                    advance();
                    count = (int) (long) (Long) expect(TokenType.INT, "shop item count").value();
                }
                items.add(new ShopItemSpec(itemId, price, count));
            } else {
                error(peek(), "SME125", "Expected currency/item");
                panicRecoverStmt();
            }
        }
        expect(TokenType.RBRACE, "'}'");
        if (currencyItemId == null) {
            error(pos, "SME124", "shop '" + name + "' has no 'currency'");
        }
        return new ShopDeclNode(pos, name, tags, currencyItemId, items);
    }

    /** {@code ('-')? (INT | DOUBLE)} as a raw {@code double} — for a declaration's plain numeric fields (not an {@link ExprNode}). */
    private double expectSignedNumber(String what) {
        boolean negative = match(TokenType.MINUS);
        Token t = peek();
        double value;
        if (t.type() == TokenType.DOUBLE) {
            value = (Double) t.value();
            advance();
        } else if (t.type() == TokenType.INT) {
            value = (double) (long) (Long) t.value();
            advance();
        } else {
            error(t, "SME121", "Expected " + what + " (a number)");
            return 0.0;
        }
        return negative ? -value : value;
    }

    // ---------- quest ----------

    private QuestDeclNode parseQuestDecl(List<MetadataTag> tags) {
        SourcePos pos = peekPos();
        advance(); // "quest"
        String id = expectAnyIdent("quest id");
        expect(TokenType.LBRACE, "'{'");

        String title = null;
        String description = null;
        List<String> prerequisites = new ArrayList<>();
        List<ObjectiveNode> objectives = new ArrayList<>();
        List<RewardNode> rewards = new ArrayList<>();

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            if (checkIdent("title")) {
                advance();
                title = expectString("quest title");
            } else if (checkIdent("description")) {
                advance();
                description = expectString("quest description");
            } else if (checkIdent("prerequisite")) {
                advance();
                prerequisites.add(expectAnyIdent("prerequisite quest id"));
            } else if (checkIdent("objective")) {
                objectives.add(parseObjective());
            } else if (checkIdent("reward")) {
                advance();
                expect(TokenType.LBRACE, "'{'");
                while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                    RewardNode reward = parseReward();
                    if (reward != null) {
                        rewards.add(reward);
                    }
                }
                expect(TokenType.RBRACE, "'}'");
            } else {
                error(peek(), "SME113", "Expected title/description/prerequisite/objective/reward");
                panicRecoverStmt();
            }
        }
        expect(TokenType.RBRACE, "'}'");
        return new QuestDeclNode(pos, id, tags, title, description, prerequisites, objectives, rewards);
    }

    private static final Set<String> OBJECTIVE_KINDS = Set.of(
            "kill", "collect", "talk_to", "interact", "dialogue", "quest", "find_entity");

    private ObjectiveNode parseObjective() {
        SourcePos pos = peekPos();
        advance(); // "objective"
        String kind = expectAnyIdent("objective kind (kill/collect/talk_to/interact/dialogue/quest/find_entity)");
        if (!OBJECTIVE_KINDS.contains(kind)) {
            error(pos, "SME114", "Unknown objective kind '" + kind + "'");
        }
        String targetId = expectAnyIdent("objective target id");
        Integer count = null;
        if (check(TokenType.INT)) {
            count = (int) (long) (Long) peek().value();
            advance();
        }
        return new ObjectiveNode(pos, kind, targetId, count);
    }

    private RewardNode parseReward() {
        Token t = peek();
        if (t.type() != TokenType.IDENT) {
            error(t, "SME115", "Expected item/xp/command/unlock_quest");
            advance();
            return null;
        }
        return switch (t.text()) {
            case "item" -> {
                advance();
                String itemId = expectAnyIdent("item id");
                int count = (int) (long) (Long) expect(TokenType.INT, "item count").value();
                yield new ItemRewardNode(t.pos(), itemId, count);
            }
            case "xp" -> {
                advance();
                int amount = (int) (long) (Long) expect(TokenType.INT, "xp amount").value();
                yield new XpRewardNode(t.pos(), amount);
            }
            case "command" -> {
                advance();
                yield new CommandRewardNode(t.pos(), expectString("command string"));
            }
            case "unlock_quest" -> {
                advance();
                yield new UnlockQuestRewardNode(t.pos(), expectAnyIdent("quest id"));
            }
            default -> {
                error(t, "SME115", "Expected item/xp/command/unlock_quest, found '" + t.text() + "'");
                advance();
                yield null;
            }
        };
    }

    // ---------- trigger ----------

    private TriggerDeclNode parseTriggerDecl(List<MetadataTag> tags) {
        SourcePos pos = peekPos();
        advance(); // "trigger"
        String id = expectAnyIdent("trigger id");
        expect(TokenType.LBRACE, "'{'");

        expectIdentKeyword("when");
        TriggerConditionNode when = parseTriggerWhen();

        TriggerPolicyKind policy = TriggerPolicyKind.REPEAT;
        if (checkIdent("once")) {
            advance();
            policy = TriggerPolicyKind.ONCE;
        } else if (checkIdent("repeat")) {
            advance();
        }
        boolean persistent = false;
        if (checkIdent("persistent")) {
            advance();
            persistent = true;
        }

        expectIdentKeyword("run");
        List<StmtNode> runBody = parseBlock();
        expect(TokenType.RBRACE, "'}'");
        return new TriggerDeclNode(pos, id, tags, when, policy, persistent, runBody);
    }

    private TriggerConditionNode parseTriggerWhen() {
        Token t = peek();
        if (checkIdent("player")) {
            SourcePos pos = t.pos();
            advance();
            expectIdentKeyword("enters");
            return new WhenEntersNode(pos, expectString("zone name"));
        }
        if (checkIdent("time")) {
            SourcePos pos = t.pos();
            advance();
            int dayTime = (int) (long) (Long) expect(TokenType.INT, "time value").value();
            return new WhenTimeNode(pos, dayTime);
        }
        if (checkIdent("event")) {
            SourcePos pos = t.pos();
            advance();
            return new WhenEventNode(pos, expectAnyIdent("event name"));
        }
        error(t, "SME116", "Expected 'player enters', 'time', or 'event' after 'when'");
        return new WhenTimeNode(t.pos(), 0);
    }

    // ---------- sequence ----------

    private SequenceDeclNode parseSequenceDecl(List<MetadataTag> tags) {
        SourcePos pos = peekPos();
        advance(); // "sequence"
        String id = null;
        if (check(TokenType.IDENT)) {
            id = expectAnyIdent("sequence id");
        }
        return new SequenceDeclNode(pos, id, tags, parseBlock());
    }

    // ---------- token helpers ----------

    private Token peek() {
        return tokens.get(idx);
    }

    private boolean peekIs(int ahead, TokenType type) {
        int i = idx + ahead;
        return i < tokens.size() && tokens.get(i).type() == type;
    }

    private boolean peekIsIdent(int ahead, String text) {
        int i = idx + ahead;
        return i < tokens.size() && tokens.get(i).type() == TokenType.IDENT && tokens.get(i).text().equals(text);
    }

    private boolean peekIsIdentPrefix(int ahead, String prefix) {
        int i = idx + ahead;
        return i < tokens.size() && tokens.get(i).type() == TokenType.IDENT && tokens.get(i).text().startsWith(prefix);
    }

    private SourcePos peekPos() {
        return peek().pos();
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean checkIdent(String keyword) {
        Token t = peek();
        return t.type() == TokenType.IDENT && t.text().equals(keyword);
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private Token advance() {
        Token t = tokens.get(idx);
        if (idx < tokens.size() - 1) {
            idx++;
        }
        return t;
    }

    private Token expect(TokenType type, String what) {
        if (check(type)) {
            return advance();
        }
        error(peek(), "SME117", "Expected " + what + ", found '" + peek().text() + "'");
        return peek();
    }

    private void expectIdentKeyword(String keyword) {
        if (checkIdent(keyword)) {
            advance();
        } else {
            error(peek(), "SME118", "Expected '" + keyword + "', found '" + peek().text() + "'");
        }
    }

    private String expectAnyIdent(String what) {
        if (check(TokenType.IDENT)) {
            return advance().text();
        }
        error(peek(), "SME119", "Expected " + what + ", found '" + peek().text() + "'");
        return "<error>";
    }

    private String expectString(String what) {
        if (check(TokenType.STRING)) {
            return (String) advance().value();
        }
        error(peek(), "SME120", "Expected " + what + " (a string literal), found '" + peek().text() + "'");
        return "";
    }

    private void error(Token at, String code, String message) {
        error(at.pos(), code, message);
    }

    private void error(SourcePos at, String code, String message) {
        diagnostics.add(new SmeDiagnostic(Severity.ERROR, at, code, message));
    }

    /** Skips tokens until one of {@code keywords} is found at the current brace depth, or the enclosing {@code '}'}/EOF. */
    private void panicRecover(Set<String> keywords) {
        int depth = 0;
        while (!check(TokenType.EOF)) {
            Token t = peek();
            if (depth == 0 && t.type() == TokenType.IDENT && keywords.contains(t.text())) {
                return;
            }
            if (depth == 0 && t.type() == TokenType.RBRACE) {
                return;
            }
            if (t.type() == TokenType.LBRACE) {
                depth++;
            } else if (t.type() == TokenType.RBRACE) {
                depth--;
            }
            advance();
        }
    }

    private void panicRecoverStmt() {
        panicRecover(STMT_KEYWORDS);
    }
}
