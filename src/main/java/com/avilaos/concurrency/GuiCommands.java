package com.avilaos.concurrency;

import com.avilaos.core.structures.MyQueue;
import java.util.concurrent.Semaphore;
import javax.swing.SwingUtilities;

/** Único trabajador: conserva orden de solicitudes sin bloquear el EDT.
 * Cola propia acotada; nunca espera espacio desde la GUI.
 * El permiso de cola se libera ANTES de ejecutar una operación del modelo.
 */
public final class GuiCommands {
    public interface Work<T> { T run() throws Exception; }
    public interface Result<T> { void accept(T value); }
    private static final GuiCommands INSTANCE = new GuiCommands();
    private final MyQueue<Runnable> queue = new MyQueue<>();
    private final Semaphore queueMutex = new Semaphore(1, true);
    private final Semaphore available = new Semaphore(0);
    private static final int LIMIT = 128;
    private GuiCommands() {
        Thread worker = new Thread(this::run, "AvilaOS-Commands");
        worker.setDaemon(true);
        worker.start();
    }
    public static <T> void submit(Work<T> work, Result<T> success, Result<Exception> failure) {
        INSTANCE.enqueue(() -> {
            try {
                T result = work.run();
                SwingUtilities.invokeLater(() -> success.accept(result));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> failure.accept(ex));
            }
        }, failure);
    }
    private void enqueue(Runnable task, Result<Exception> failure) {
        // tryAcquire evita incluso la espera breve por el mutex de cola en el EDT.
        if (!queueMutex.tryAcquire()) {
            SwingUtilities.invokeLater(() -> failure.accept(new IllegalStateException("Intenta nuevamente: cola ocupada")));
            return;
        }
        boolean accepted = false;
        try {
            if (queue.size() < LIMIT) { queue.enqueue(task); accepted = true; }
        } finally { queueMutex.release(); }
        if (accepted) available.release();
        else SwingUtilities.invokeLater(() -> failure.accept(new IllegalStateException("Demasiadas solicitudes pendientes")));
    }
    private void run() {
        while (true) {
            available.acquireUninterruptibly();
            Runnable task;
            queueMutex.acquireUninterruptibly();
            try { task = queue.dequeue(); } finally { queueMutex.release(); }
            task.run();
        }
    }
}
