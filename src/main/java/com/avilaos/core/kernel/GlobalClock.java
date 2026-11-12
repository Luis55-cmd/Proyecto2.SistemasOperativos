package com.avilaos.core.kernel;

import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.concurrency.StateAccess;
import java.util.concurrent.Semaphore;


/** Un único hilo de reloj reutilizado. Tick y comandos comparten el permiso de estado.
 * Dormir/esperar nunca retiene ese permiso. Pausa confirma el fin del tick en curso.
 */
public class GlobalClock implements Runnable {
    private static final GlobalClock INSTANCE = new GlobalClock();
    private final MyLinkedList<IClockTickable> subscribers = new MyLinkedList<>();
    private final Semaphore wake = new Semaphore(0);
    private volatile int cycleCounter;
    private volatile int cycleDurationMs = 1000;
    private volatile boolean running, paused;
    private Thread clockThread;
    private GlobalClock() { }
    public static GlobalClock getInstance() { return INSTANCE; }
    public void start() {
        StateAccess.enter();
        try {
            running=true; paused=false;
            if (clockThread==null) {
                clockThread=new Thread(this,"AvilaOS-GlobalClock");
                clockThread.setDaemon(true); clockThread.start();
            }
            wake.release();
        } finally { StateAccess.exit(); }
    }
    public void pause() {
        StateAccess.enter();
        try { paused=true; wake.release(); } finally { StateAccess.exit(); }
    }
    public void resume() { start(); }
    public void stop() {
        StateAccess.enter();
        try { running=false; paused=false; wake.release(); } finally { StateAccess.exit(); }
    }
    public void step() {
        StateAccess.enter();
        try {
            if(running && !paused) throw new IllegalStateException("Pausa el reloj antes del paso manual");
            tickLocked();
        } finally { StateAccess.exit(); }
    }
    private void tickLocked() {
        int cycle=++cycleCounter;
        // Copia evita cambios en suscripciones durante callbacks; todos bajo el mismo permiso.
        IClockTickable[] copy=new IClockTickable[subscribers.size()];
        for(int i=0;i<copy.length;i++) copy[i]=subscribers.get(i);
        for(IClockTickable subscriber:copy) subscriber.tick(cycle);
    }
    @Override public void run() {
    while(true) {
        boolean active;
        StateAccess.enter();
        try {
            wake.drainPermits();
            active = running && !paused;
            if(active) tickLocked();
        } catch (RuntimeException ex) {
            paused = true; active = false;
            System.err.println("[GlobalClock] Pausado por error: " + ex.getMessage());
        } finally { 
            StateAccess.exit(); 
        }
        
        try {
            if(active) {
                Thread.sleep(cycleDurationMs);
            } else {
                wake.acquire();
            }
        } catch(InterruptedException ex) { 
            Thread.currentThread().interrupt(); 
            return; 
        }
    }
}
    public void addSubscriber(IClockTickable subscriber) {
        StateAccess.enter();
        try { if(subscriber!=null && !subscribers.contains(subscriber)) subscribers.add(subscriber); }
        finally { StateAccess.exit(); }
    }
    public void removeSubscriber(IClockTickable subscriber) {
        StateAccess.enter();
        try { if(subscriber!=null) subscribers.remove(subscriber); }
        finally { StateAccess.exit(); }
    }
    public void reset() {
        StateAccess.enter();
        try {
            running=false; paused=false; cycleCounter=0;
            while(!subscribers.isEmpty()) subscribers.deleteFirst();
            wake.release();
        } finally { StateAccess.exit(); }
    }
    public int getCycleCounter() { return cycleCounter; }
    public void setCycleCounter(int cycle) {
        StateAccess.enter();
        try { cycleCounter=Math.max(0,cycle); } finally { StateAccess.exit(); }
    }
    public int getCycleDurationMs() { return cycleDurationMs; }
    public void setCycleDurationMs(int ms) {
        StateAccess.enter();
        try { if(ms>0) cycleDurationMs=ms; } finally { StateAccess.exit(); }
    }
    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public MyLinkedList<IClockTickable> getSubscribers() {
        StateAccess.enter();
        try {
            MyLinkedList<IClockTickable> copy=new MyLinkedList<>();
            for(int i=0;i<subscribers.size();i++) copy.add(subscribers.get(i));
            return copy;
        } finally { StateAccess.exit(); }
    }
}
