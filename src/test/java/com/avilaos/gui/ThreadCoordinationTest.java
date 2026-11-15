package com.avilaos.gui;

import com.avilaos.concurrency.StateAccess;
import com.avilaos.concurrency.GuiCommands;
import com.avilaos.simulation.SimulationController;
import com.avilaos.core.kernel.GlobalClock;
import com.avilaos.core.model.SchedulingPolicyType;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/** Intercalados forzados con barreras; no depende de leer logs ni de ganar una carrera. */
public class ThreadCoordinationTest {
    private static final SimulationController ctrl=SimulationController.getInstance();
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private static void await(Semaphore signal) throws Exception {
        check(signal.tryAcquire(3,TimeUnit.SECONDS),"Timeout de barrera");
    }
    private static Thread thread(Runnable work) {
        Thread t=new Thread(work,"Test-worker"); t.setDaemon(true); t.start(); return t;
    }
    public static void main(String[] args) throws Exception {
        String test=args.length==0?"edt":args[0]; ctrl.reset();
        switch(test) {
            case "edt" -> edt();
            case "exclusion" -> exclusion();
            case "snapshot" -> snapshot();
            case "pause" -> pause();
            case "session" -> session();
            case "failure" -> failure();
            case "history" -> history();
            case "clock" -> clock();
            case "log" -> log();
            default -> throw new AssertionError(test);
        }
        System.out.println("PASS ThreadCoordinationTest "+test);
    }
    private static void edt() throws Exception {
        Semaphore held=new Semaphore(0), release=new Semaphore(0), done=new Semaphore(0), pulse=new Semaphore(0);
        thread(() -> {StateAccess.enter(); try {held.release(); release.acquireUninterruptibly();} finally {StateAccess.exit();}});
        await(held);
        boolean[] correct={false};
        try {
            long start=System.nanoTime();
            SwingUtilities.invokeLater(() -> {
                GuiRequest.model(ctrl,() -> {check(!SwingUtilities.isEventDispatchThread(),"Trabajo en EDT"); return 17;},
                    value -> {correct[0]=SwingUtilities.isEventDispatchThread() && value==17; done.release();},error -> done.release());
                SwingUtilities.invokeLater(pulse::release);
            });
            await(pulse); // El permiso sigue retenido: el EDT debe atender este evento igualmente.
            System.out.println("Pulso EDT con permiso retenido: "+((System.nanoTime()-start)/1_000_000.0)+" ms");
            check(!done.tryAcquire(100,TimeUnit.MILLISECONDS),"Comando entró sin permiso");
        } finally {release.release();}
        await(done); check(correct[0],"Resultado fuera de EDT / tarea fallida");
    }
    private static void exclusion() throws Exception {
        Semaphore attempted=new Semaphore(0),entered=new Semaphore(0);
        StateAccess.enter();
        try {
            thread(() -> {attempted.release(); StateAccess.enter(); try {entered.release();} finally {StateAccess.exit();}});
            await(attempted);
            check(!entered.tryAcquire(150,TimeUnit.MILLISECONDS),"Dos propietarios simultáneos");
            StateAccess.enter(); StateAccess.exit(); // Reentrada no libera el permiso exterior.
            check(!entered.tryAcquire(100,TimeUnit.MILLISECONDS),"Reentrada liberó antes de tiempo");
        } finally {StateAccess.exit();}
        await(entered);
    }
    private static void snapshot() {
        var before=ctrl.snapshot();
        ctrl.createComputer("Uno",128,SchedulingPolicyType.FCFS);
        var first=ctrl.snapshot();
        ctrl.createComputer("Dos",256,SchedulingPolicyType.EDF);
        check(before.computers.size()==0,"Snapshot anterior cambió");
        check(first.computers.size()==1,"Snapshot intermedio cambió/no se publicó");
        check(ctrl.snapshot().computers.size()==2,"Falta publicación");
        ctrl.setComputerPolicy(1,SchedulingPolicyType.PRIORITY_PREEMPTIVE);
        check(first.computers.get(0).getPolitica()==SchedulingPolicyType.FCFS,"Objeto vivo en snapshot");
    }
    private static void pause() throws Exception {
        GlobalClock clock=GlobalClock.getInstance();
        Semaphore tick=new Semaphore(0),release=new Semaphore(0),paused=new Semaphore(0);
        clock.addSubscriber(c -> {tick.release(); release.acquireUninterruptibly();});
        clock.start(); await(tick);
        try {
            thread(() -> {clock.pause(); paused.release();});
            check(!paused.tryAcquire(150,TimeUnit.MILLISECONDS),"Pausa confirmó durante tick incompleto");
        } finally {release.release();}
        await(paused); int cycle=clock.getCycleCounter();
        check(clock.isPaused(),"No pausó");
        Thread.sleep(50); check(clock.getCycleCounter()==cycle,"Avanzó estando pausado");
        clock.reset();
        clock.step(); check(clock.getCycleCounter()==1,"Paso tras reset");
    }
    private static void session() throws Exception {
        Semaphore queued=new Semaphore(0),done=new Semaphore(0);
        boolean[] rejected={false};
        StateAccess.enter();
        try {
            SwingUtilities.invokeAndWait(() -> {
                GuiRequest.model(ctrl,() -> ctrl.createComputer("Viejo",128,SchedulingPolicyType.FCFS),
                    c -> done.release(),error -> {rejected[0]=true;done.release();}); queued.release();
            });
            await(queued); ctrl.reset();
        } finally {StateAccess.exit();}
        await(done);check(rejected[0] && ctrl.snapshot().computers.size()==0,"Comando viejo contaminó nueva sesión");
    }
    private static void log() throws Exception {
        boolean[] stale={false},correct={false};
        ctrl.addLogListener(text -> {
            if(text.contains("ANTERIOR")) stale[0]=true;
            if(text.equals("[Ciclo 9] ACTUAL")) correct[0]=SwingUtilities.isEventDispatchThread();
        });
        SwingUtilities.invokeAndWait(() -> {
            ctrl.logEvent("ANTERIOR");ctrl.reset();
            ctrl.setCurrentCycle(9);ctrl.logEvent("ACTUAL");ctrl.setCurrentCycle(12);
        });
        SwingUtilities.invokeAndWait(() -> {});
        check(!stale[0],"Log de sesión anterior se mostró");
        check(correct[0],"Timestamp cambió al pintar / callback fuera de EDT");
    }
    private static void history() {
        ctrl.createComputer("Historia",128,SchedulingPolicyType.FCFS);
        for(int i=0;i<5;i++) ctrl.stepSimulation();
        var view=ctrl.snapshot();
        check(view.history.size()==6,"Se perdieron muestras sin refrescar GUI");
        for(int i=0;i<6;i++) check(view.history.get(i).cycle()==i,"Orden del historial");
        ctrl.reset();check(ctrl.snapshot().history.size()==1,"Historial mezcló sesiones");
    }
    private static void clock() throws Exception {
        var clock=GlobalClock.getInstance();
        var field=GlobalClock.class.getDeclaredField("clockThread");field.setAccessible(true);
        Thread first=null;
        for(int i=0;i<5;i++) {
            clock.start();clock.pause();
            Thread current=(Thread)field.get(clock);
            if(first==null) first=current;
            check(first==current,"Se creó otro trabajador de reloj");
            clock.reset();
        }
        check(first!=null && first.isDaemon(),"Reloj no es daemon");
    }
    private static void failure() throws Exception {
        Semaphore done=new Semaphore(0); boolean[] rejected={false};
        GuiRequest.model(ctrl,() -> {throw new IllegalArgumentException("Esperado");},
            value -> done.release(),error -> {rejected[0]=true;done.release();});
        await(done);check(rejected[0],"Error no comunicado");
        GuiRequest.model(ctrl,() -> ctrl.createComputer("Después",128,SchedulingPolicyType.FCFS).getId(),
            value -> done.release(),error -> done.release());
        await(done);check(ctrl.snapshot().computers.size()==1,"Excepción perdió permiso/trabajador");
    }
}
