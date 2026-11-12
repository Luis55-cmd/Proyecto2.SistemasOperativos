package com.avilaos.concurrency;

import java.util.concurrent.Semaphore;

/** Permiso real del modelo, independiente de los permisos de los PCB.
 * La reentrada evita que sobrecargas internas esperen su propio permiso.
 * Solo el propietario modifica depth. Publicación de snapshots antes de liberar.
 */
public final class StateAccess {
    private StateAccess() { }
    private static final Semaphore mutex = new Semaphore(1, true);
    private static volatile Thread owner;
    private static int depth;
    private static Runnable publisher;
    public static void enter() {
        if (owner == Thread.currentThread()) { depth++; return; }
        mutex.acquireUninterruptibly();
        owner = Thread.currentThread();
        depth = 1;
    }
    public static void exit() {
        if (owner != Thread.currentThread()) throw new IllegalStateException("Permiso ajeno");
        if (--depth != 0) return;
        depth = 1; // La publicación puede consultar funciones protegidas sin publicarse recursivamente.
        try { if (publisher != null) publisher.run(); }
        finally { depth = 0; owner = null; mutex.release(); }
    }
    public static void setPublisher(Runnable callback) {
        enter();
        try { publisher = callback; } finally { exit(); }
    }
    public static boolean isOwner() { return owner == Thread.currentThread(); }
}
