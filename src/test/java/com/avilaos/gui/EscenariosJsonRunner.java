package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.*;
import javax.swing.SwingUtilities;
import java.nio.file.*;
import java.io.*;

/** Ejecuta archivos por la misma función que Cargar Estado; no replica su parser. */
public class EscenariosJsonRunner {
    private static final SimulationController c = SimulationController.getInstance();
    private static PrintWriter log;
    private static MonitorSimulacion monitor;
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    public static void main(String[] args) throws Exception {
        Path entrada = Path.of(args.length > 0 ? args[0] : "escenarios");
        Path salida = Path.of(args.length > 1 ? args[1] : "/tmp/avilaos-escenarios");
        Files.createDirectories(salida);
        edt(() -> {
            monitor = new MonitorSimulacion(c, () -> {});
            c.addLogListener(message -> { if (log != null) log.println("[LOG ciclo " + c.getCurrentCycle() + "] " + message); });
        });
        String[] nombres = {"01-cpu-fcfs-rr", "02-admision-ram", "03-entrada-salida",
                "04-productor-consumidor-remoto", "05-prioridad-primer-despacho", "06-prioridad-apropiacion", "07-semaforos-rr"};
        try (var resumen = new PrintWriter(Files.newBufferedWriter(salida.resolve("resultado.txt")))) {
            for (String nombre : nombres) {
                if (args.length > 2 && !nombre.startsWith(args[2])) continue;
                log = new PrintWriter(Files.newBufferedWriter(salida.resolve(nombre + ".log")));
                edt(() -> {
                    if (!monitor.cargarEstado(entrada.resolve(nombre + ".json").toFile())) throw new AssertionError("CARGA: archivo rechazado");
                    if (c.getComputers().size() != 2) throw new AssertionError("CARGA: dos computadores");
                    if (!nombre.startsWith("04") && !nombre.startsWith("07") && c.getBuffers().size() != 0) throw new AssertionError("VACIO: arreglo vacío no crea buffers");
                    if (nombre.startsWith("07")) {
                        for (int i = 0; i < 2; i++)
                            if (c.getComputers().get(i).getQuantum() != 1)
                                throw new AssertionError("CARGA_QUANTUM: cada computador debe cargar quantum 1");
                    }
                    snapshot();
                });
                edt(() -> {});
                boolean[] bloqueo = {false}, red = {false}, mutexWait = {false}, retained = {false};
                String[] primero = {""};
                for (int tick = 1; tick <= 80; tick++) {
                    final int t = tick;
                    edt(() -> {
                        c.stepSimulation();
                        if (t == 1 && c.getComputers().get(0).getCpu().getPCB() != null)
                            primero[0] = c.getComputers().get(0).getCpu().getPCB().getName();
                        var ps = c.getAllProcesses();
                        for (int i = 0; i < ps.size(); i++) {
                            bloqueo[0] |= ps.get(i).getState() == ProcessState.BLOQUEADO;
                            red[0] |= ps.get(i).getNetworkDelayRemaining() > 0;
                        }
                        if (nombre.startsWith("07")) {
                            int owners = 0, slots = 0;
                            for (int i = 0; i < ps.size(); i++) {
                                var p = ps.get(i);
                                owners += p.holdsBufferMutex() ? 1 : 0;
                                slots += p.getReservedBufferSlot() != null ? 1 : 0;
                                slots += p.getPendingBufferSignal() != null ? 1 : 0;
                                mutexWait[0] |= p.getBlockReason() == BlockReason.WAITING_MUTEX;
                                retained[0] |= p.getState() == ProcessState.LISTO && p.holdsBufferMutex();
                            }
                            var b = c.getBuffers().get(0);
                            if (owners + b.getMutex().availablePermits() != 1) throw new AssertionError("MUTEX: conservación por tick");
                            if (slots + b.getVacios().availablePermits() + b.getLlenos().availablePermits() != 2)
                                throw new AssertionError("PERMISOS: conservación por tick");
                        }
                        snapshot();
                    });
                    edt(() -> {});
                }
                String[] resultado = {""};
                edt(() -> {
                    boolean fin = true;
                    var ps = c.getAllProcesses();
                    for (int i = 0; i < ps.size(); i++)
                        fin &= ps.get(i).isTerminated() && (ps.get(i).getRemainingTime() <= 0
                                || ps.get(i).getRequiredElements() > 0 && ps.get(i).getProcessedElements() >= ps.get(i).getRequiredElements());
                    boolean ram = c.getComputers().get(0).getMemoryManager().getUsedMemory() == ((nombre.startsWith("04") || nombre.startsWith("07")) ? 2 : 0)
                            && c.getComputers().get(1).getMemoryManager().getUsedMemory() == 0;
                    boolean ok = fin && ram;
                    if (nombre.startsWith("03")) ok &= bloqueo[0];
                    if (nombre.startsWith("04")) {
                        var b = c.getBuffers().get(0);
                        ok &= bloqueo[0] && red[0] && b.getProduced() == 3 && b.getComsumed() == 3 && b.getDatos().isEmpty();
                    }
                    if (nombre.startsWith("07")) {
                        var b = c.getBuffers().get(0);
                        ok &= mutexWait[0] && retained[0] && b.getProduced() == 4 && b.getComsumed() == 4
                                && b.getDatos().isEmpty() && b.getMutex().availablePermits() == 1
                                && b.getVacios().availablePermits() == 2 && b.getLlenos().availablePermits() == 0;
                        for (int i = 0; i < ps.size(); i++) {
                            var p = ps.get(i);
                            int expected = p.getType() == ProcessType.PRODUCER ? 14 : 24;
                            ok &= p.getProgramCounter() == expected && p.getMemoryAddressRegister() == expected - 1
                                    && p.getPermitBufferId() == -1;
                        }
                        if (!ok) throw new AssertionError("PROTOCOLO: fin=" + fin + " RAM=" + ram
                                + " mutexWait=" + mutexWait[0] + " RR-retiene=" + retained[0]);
                    }
                    if (nombre.startsWith("05") || nombre.startsWith("06")) ok &= "Alta".equals(primero[0]);
                    resultado[0] = (ok ? "PASS " : "FAIL ") + nombre + " | fin=" + fin + " RAM=" + ram
                            + " bloqueo=" + bloqueo[0] + " red=" + red[0] + " primerCPU=" + primero[0];
                    if (!monitor.guardarEstado(salida.resolve(nombre + "-final.json").toFile())) throw new AssertionError("GUARDADO");
                });
                edt(() -> {});
                log.close(); log = null;
                resumen.println(resultado[0]); System.out.println(resultado[0]);
            }
        }
    }
    private static void snapshot() {
        log.println("[ESTADO ciclo " + c.getCurrentCycle() + "]");
        var pcs = c.getComputers();
        for (int i = 0; i < pcs.size(); i++) {
            var pc = pcs.get(i);
            log.println(pc.getName() + " RAM=" + pc.getMemoryManager().getUsedMemory() + "/" + pc.getMemoryManager().getTotalMemory()
                    + " nuevos=" + pc.getKernel().getNewQueue() + " listos=" + pc.getKernel().getReadyQueue()
                    + " bloqueados=" + pc.getKernel().getBlockedQueue());
        }
        var ps = c.getAllProcesses();
        for (int i = 0; i < ps.size(); i++) {
            var p = ps.get(i);
            log.println(p + " etapa=" + p.getSyncStage() + " red=" + p.getNetworkDelayRemaining()
                    + " elementos=" + p.getProcessedElements() + " motivo=" + p.getBlockReason() + " mutex=" + p.holdsBufferMutex()
                    + " reserva=" + p.getReservedBufferSlot() + " signalPendiente=" + p.getPendingBufferSignal());
        }
        var bs = c.getBuffers();
        for (int i = 0; i < bs.size(); i++) {
            var b = bs.get(i);
            log.println("buffer=" + b.getId() + " ocupados=" + b.getDatos().size() + "/" + b.getStorage()
                    + " producidos=" + b.getProduced() + " consumidos=" + b.getComsumed()
                    + " mutex=" + b.getMutex().availablePermits() + " llenos=" + b.getLlenos().availablePermits()
                    + " vacios=" + b.getVacios().availablePermits());
        }
    }
}
