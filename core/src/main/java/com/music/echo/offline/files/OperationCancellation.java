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
        CURRENT.set(token);
        return token;
    }

    void finish(Token token) {
        if (token == null) return;
        active.remove(token);
        if (CURRENT.get() == token) CURRENT.remove();
    }

    void cancelActive() {
        for (Token token : active) token.cancel();
    }

    static boolean isCancelled() {
        Token token = CURRENT.get();
        return token != null && token.isCancelled();
    }

    static void checkCurrent() throws IOException {
        if (isCancelled()) throw new IOException("Operation cancelled");
    }

    static final class Token {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        void cancel() { cancelled.set(true); }
        boolean isCancelled() { return cancelled.get(); }
    }
}
