package com.music.echo.offline.files;

/** Owns the bounded worker scope and cancellation lifecycle for one Activity instance. */
final class ActivityTaskRuntime {
    final OperationCancellation cancellation = new OperationCancellation();
    private final TaskCoordinator.Scope scope = new TaskCoordinator.Scope();
    final TaskCoordinator.ScopedExecutor io = scope.executor(TaskCoordinator.Lane.IO);
    final TaskCoordinator.ScopedExecutor ops = scope.executor(TaskCoordinator.Lane.SERIAL);
    private boolean shutDown;

    synchronized void shutdown() {
        if (shutDown) return;
        shutDown = true;
        cancellation.shutDown();
        cancellation.cancelActive();
        LanTransfer.cancel();
        scope.close(true);
    }
}
