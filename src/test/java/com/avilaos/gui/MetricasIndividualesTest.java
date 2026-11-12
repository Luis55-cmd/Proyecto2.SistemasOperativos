package com.avilaos.gui;

import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.simulation.SimulationController;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** Casos calculados por ciclo, incluidos los datos visibles en la copia. */
public class MetricasIndividualesTest {
    private static void check(boolean ok, String mensaje) {
        if (!ok) throw new AssertionError(mensaje);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var c = SimulationController.getInstance();
            c.reset();
            var pc = c.createComputer("CPU", 128, SchedulingPolicyType.FCFS);
            c.createProcess("Primero", pc.getId(), ProcessType.CPU_BOUND, 2, 2, 1, 100);
            var segundo = c.createProcess("Segundo", pc.getId(), ProcessType.CPU_BOUND, 3, 3, 1, 100);
            c.stepSimulation(); c.stepSimulation(); c.stepSimulation();
            check(segundo.getReadyWaitTicks() == 2, "FCFS: segundo espera dos ciclos, después ejecuta");
            check(segundo.getBlockedWaitTicks() == 0 && segundo.getSemaphoreWaitTicks() == 0, "FCFS: sin bloqueos");

            c.reset();
            pc = c.createComputer("IO", 128, SchedulingPolicyType.FCFS);
            var io = c.createProcess("E/S", pc.getId(), ProcessType.IO_BOUND, 5, 5, 1, 100, 1, 3);
            c.stepSimulation();
            check(io.getState() == ProcessState.BLOQUEADO && io.getBlockedWaitTicks() == 0, "E/S: bloqueo empieza tras ejecutar");
            c.stepSimulation(); c.stepSimulation(); c.stepSimulation();
            check(io.getBlockedWaitTicks() == 3 && io.getSemaphoreWaitTicks() == 0, "E/S: tres ciclos bloqueado, ninguno en semáforos");

            c.reset();
            pc = c.createComputer("Buffer", 128, SchedulingPolicyType.FCFS);
            var b = c.createBuffer(pc.getId(), 2, 0);
            var consumer = c.createProcess("Consumidor", pc.getId(), ProcessType.CONSUMER, 10, 10, 1, 100,
                    String.valueOf(b.getId()), 1, 1);
            c.stepSimulation();
            check(consumer.getBlockReason() == BlockReason.WAITING_EMPTY_BUFFER, "Consumidor espera buffer vacío");
            c.stepSimulation(); c.stepSimulation(); c.stepSimulation();
            check(consumer.getBlockedWaitTicks() == 3 && consumer.getSemaphoreWaitTicks() == 3, "Semáforo: tres ciclos de espera");
            var snapshot = c.snapshot().processes.get(0).process();
            check(snapshot.semaphoreWaitTicks() == 3 && snapshot.blockedWaitTicks() == 3, "Snapshot conserva contadores");
            var panel = new PanelMetricasCopia(c);
            panel.actualizarMetricas();
            JTabbedPane tabs = (JTabbedPane) CreacionComputadoresGuiTest.buscar(panel, JTabbedPane.class, null);
            check(tabs.getTabCount() == 3 && tabs.getSelectedIndex() == 0, "Tres pestañas, computadores por defecto");
            JTable equipos = (JTable) CreacionComputadoresGuiTest.buscar((java.awt.Container) tabs.getComponentAt(0), JTable.class, null);
            JTable procesos = (JTable) CreacionComputadoresGuiTest.buscar((java.awt.Container) tabs.getComponentAt(1), JTable.class, null);
            JTable buffers = (JTable) CreacionComputadoresGuiTest.buscar((java.awt.Container) tabs.getComponentAt(2), JTable.class, null);
            check(equipos.getValueAt(0, 7).equals(3) && equipos.getValueAt(0, 8).equals(3), "Totales por computador");
            check(procesos.getValueAt(0, 5).equals(3) && procesos.getValueAt(0, 6).equals(3), "Tiempos por proceso");
            check(buffers.getValueAt(0, 4).toString().replace(',', '.').equals("3.0"), "Promedio por buffer");
            c.reset(); panel.actualizarMetricas();
            check(procesos.getRowCount() == 0 && buffers.getRowCount() == 0, "Reinicio limpia tablas");

            // El productor se ejecuta primero en cada tick; aun así se cuenta el último
            // ciclo de espera del consumidor remoto cuando recibe signal ese mismo tick.
            var host = c.createComputer("Productor primero", 128, SchedulingPolicyType.FCFS);
            var remote = c.createComputer("Consumidor después", 128, SchedulingPolicyType.FCFS);
            b = c.createBuffer(host.getId(), 2, 0);
            consumer = c.createProcess("Remoto", remote.getId(), ProcessType.CONSUMER, 10, 10, 1, 100,
                    String.valueOf(b.getId()), 1, 1);
            c.stepSimulation();
            check(consumer.getBlockReason() == BlockReason.WAITING_EMPTY_BUFFER, "Consumidor remoto bloqueado");
            c.createProcess("Productor", host.getId(), ProcessType.PRODUCER, 10, 10, 1, 100,
                    String.valueOf(b.getId()), 1, 1);
            boolean despertado = false;
            for (int i = 0; i < 20 && !despertado; i++) {
                int antes = consumer.getSemaphoreWaitTicks();
                c.stepSimulation();
                check(consumer.getSemaphoreWaitTicks() == antes + 1, "Cuenta cada tick previo a signal remoto");
                despertado = consumer.getBlockReason() != BlockReason.WAITING_EMPTY_BUFFER;
            }
            check(despertado, "Productor despierta al consumidor remoto");
            c.reset();
            System.out.println("PASS MetricasIndividualesTest");
        });
    }
}
