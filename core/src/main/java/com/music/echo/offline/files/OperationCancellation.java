package com.music.echo.offline.files;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellation state owned by one activity and scoped to each running operation. */
final class OperationCancellation {
    private static final ThreadLocal<Token> CURRENT = new ThreadLocal<>();
    private final Set<Token> active = ConcurrentHashMap.newKeySet();

    Token begin() {
        Token token = new Token();
        active.add(token);
        token.previous = CURRENT.get();
        CURRENT.set(token);
        return token;
    }

    void finish(Token token) {
        if (token == null) return;
        active.remove(token);
        Token current = CURRENT.get();
        if (current == token) {
            current = token.previous;
        } else {
            for (Token cursor = current; cursor != null; cursor = cursor.previous) {
                if (cursor.previous == token) {
                    cursor.previous = token.previous;
                    break;
                }
            }
        }
        token.previous = null;
        if (current == null) CURRENT.remove();
        else CURRENT.set(current);
    }

    void cancelActive() {
        for (Token token : active) token.cancel();
    }

    static boolean isCancelled() {
        for (Token token = CURRENT.get(); token != null; token = token.previous) {
            if (token.isCancelled()) return true;
        }
        return false;
    }

    static void checkCurrent() throws IOException {
        if (isCancelled()) throw new IOException("Operation cancelled");
    }

    static final class Token {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private Token previous;
        void cancel() { cancelled.set(true); }
        boolean isCancelled() { return cancelled.get(); }
    }
}
