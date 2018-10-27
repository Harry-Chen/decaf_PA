package decaf.frontend;

import decaf.Driver;
import decaf.error.MsgError;
import decaf.tree.Tree;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Parser extends Table {
    /**
     * Lexer.
     */
    private Lexer lexer;

    /**
     * Set lexer.
     *
     * @param lexer the lexer.
     */
    public void setLexer(Lexer lexer) {
        this.lexer = lexer;
    }

    /**
     * Set debug mode.
     *
     * @param debug turn on debug mode
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Lookahead token (can be eof/eos).
     */
    public int lookahead = eof;

    /**
     * Undefined token (when lexer fails).
     */
    private static final int undefined_token = -2;

    /**
     * Lexer will write this when a token is parsed.
     */
    public SemValue val = new SemValue();

    /**
     * Helper function.
     * Create a `MsgError` with `msg` and append it to driver.
     *
     * @param msg the message string.
     */
    private void issueError(String msg) {
        Driver.getDriver().issueError(new MsgError(lexer.getLocation(), msg));
    }

    /**
     * Error handler.
     */
    private void error() {
        syntaxError = true;
        issueError("syntax error");
    }

    /**
     * Indicates whether syntax error has occurred.
     * If so, skip all user defined action to avoid NullPointerException
     */
    private boolean syntaxError = false;

    /**
     * Whether to print debug message
     */
    private boolean debug;

    /**
     * Print debug message if debug mode is on.
     *
     * @param s message to print
     */
    private void d(String s) {
        if (debug) {
            System.err.println(s);
        }
    }

    /**
     * Lexer caller.
     *
     * @return the token parsed by the lexer. If `undefined_token` is returned, then lexer failed.
     */
    private int lex() {
        int token = undefined_token;
        try {
            token = lexer.yylex();
        } catch (Exception e) {
            issueError("lexer error: " + e.getMessage());
        }
        d(String.format("Lexer get: %s\n", name(token)));
        return token;
    }

    /**
     * Parse function for each non-terminal with error recovery.
     * NOTE: the current implementation is buggy and may throw NullPointerException.
     *
     * @param symbol the non-terminal to be passed.
     * @return the parsed value of `symbol` if parsing succeeded, otherwise `null`.
     */
    private SemValue parse(int symbol, Set<Integer> follow) {

        d(String.format("\nNow symbol %s, lookahead %s", name(symbol), name(lookahead)));

        // obtain the Begin and End set of current non-terminate symbol
        var begin = beginSet(symbol);
        var end = new HashSet<>(followSet(symbol));
        end.addAll(follow);

        d(String.format("Current begin set: %s", symbolSet(begin)));
        d(String.format("Current end set: %s", symbolSet(end)));

        if (!begin.contains(lookahead)) {
            d(String.format("Token %s not in Begin set, returning error", name(lookahead)));
            error();
            while (true) {
                if (begin.contains(lookahead)) {
                    d(String.format("Token %s in Begin set, let's continue", name(lookahead)));
                    break;
                } else if (end.contains(lookahead)) {
                    d(String.format("Token %s in End set, returning null", name(lookahead)));
                    return null;
                }
                d(String.format("Skipping token %s", name(lookahead)));
                lookahead = lex();
            }
        }

        var result = query(symbol, lookahead); // get production by lookahead symbol

        int actionId = result.getKey(); // get user-defined action

        List<Integer> right = result.getValue(); // right-hand side of production
        int length = right.size();
        SemValue[] params = new SemValue[length + 1];

        for (int i = 0; i < length; i++) { // parse right-hand side symbols one by one
            int term = right.get(i);
            if (isNonTerminal(term)) {
                d(String.format("Parsing non-terminal %s with follow set %s", name(term), symbolSet(end)));
                params[i + 1] = parse(term, end);
            } else {
                d(String.format("Matching token %s", name(term)));
                params[i + 1] = matchToken(term);
            }
//            params[i + 1] = isNonTerminal(term)
//                    ? parse(term, follow) // for non terminals: recursively parse it
//                    : matchToken(term) // for terminals: match token
//                    ;
        }

        params[0] = new SemValue(); // initialize return value
        if (!syntaxError) {
            act(actionId, params); // do user-defined action only if no syntax error has occurred
        }
        return params[0];
    }

    /**
     * Match if the lookahead token is the same as the expected token.
     *
     * @param expected the expected token.
     * @return same as `parse`.
     */
    private SemValue matchToken(int expected) {
        SemValue self = val;
        if (lookahead != expected) {
            error();
            return null;
        }

        lookahead = lex();
        return self;
    }

    /**
     * Explicit interface of the parser. Call it in `Driver` class!
     *
     * @return the program AST if successfully parsed, otherwise `null`.
     */
    public Tree.TopLevel parseFile() {
        lookahead = lex();
        SemValue r = parse(start, new HashSet<>());
        return r == null ? null : r.prog;
    }

    /**
     * Helper function. (For debugging)
     * Pretty print a symbol set.
     *
     * @param set symbol set.
     * @return formatted string for symbol set
     */
    private String symbolSet(Set<Integer> set) {
        StringBuilder buf = new StringBuilder();
        buf.append("{ ");
        for (Integer i : set) {
            buf.append(name(i));
            buf.append(" ");
        }
        buf.append("}");
        return buf.toString();
    }

    /**
     * Helper function. (For debugging)
     * Pretty print a symbol list.
     *
     * @param list symbol list.
     * @return formatted string for symbol list
     */
    private String symbolList(List<Integer> list) {
        StringBuilder buf = new StringBuilder();
        buf.append(" ");
        for (Integer i : list) {
            buf.append(name(i));
            buf.append(" ");
        }
        return buf.toString();
    }

    /**
     * Diagnose function. (For debugging)
     * Implement this by yourself on demand.
     */
    public void diagnose() {

    }

}
