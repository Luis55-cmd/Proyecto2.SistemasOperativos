package com.avilaos.gui;

import com.avilaos.concurrency.StateAccess;
import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.simulation.SimulationController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.lang.reflect.Field;

/**
 * Valida que cada una de las 4 colas tenga su propia tabla y presente los atributos del PCB correctamente.
 */
public class DetalleColasTablasTest {

    private static Object getField(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                var controller = SimulationController.getInstance();
                controller.reset();

                var c1 = controller.createComputer("Host-Alpha", 100, SchedulingPolicyType.FCFS);
                var monitor = new MonitorSimulacion(controller, () -> {});

                DefaultTableModel modeloNuevos = (DefaultTableModel) getField(monitor, "modeloNuevos");
                DefaultTableModel modeloListos = (DefaultTableModel) getField(monitor, "modeloListos");
                DefaultTableModel modeloBloqueados = (DefaultTableModel) getField(monitor, "modeloBloqueados");
                DefaultTableModel modeloTerminados = (DefaultTableModel) getField(monitor, "modeloTerminados");

                monitor.actualizarVista();

                if (modeloNuevos.getRowCount() != 0 || modeloListos.getRowCount() != 0
                        || modeloBloqueados.getRowCount() != 0 || modeloTerminados.getRowCount() != 0) {
                    throw new AssertionError("Las tablas deberían iniciar vacías");
                }

                // 1. Proceso listo (cabe en RAM)
                var pListo = controller.createProcess("P-Listo", c1.getId(), ProcessType.CPU_BOUND, 10, 40, 5, 200);
                monitor.actualizarVista();

                if (modeloListos.getRowCount() != 1) {
                    throw new AssertionError("La tabla de Listos debería tener 1 fila");
                }
                if (!"P-Listo".equals(modeloListos.getValueAt(0, 1))) {
                    throw new AssertionError("Nombre incorrecto en tabla Listos: " + modeloListos.getValueAt(0, 1));
                }
                if (!"CPU".equals(modeloListos.getValueAt(0, 2))) {
                    throw new AssertionError("Tipo incorrecto en tabla Listos: " + modeloListos.getValueAt(0, 2));
                }
                if (!Integer.valueOf(5).equals(modeloListos.getValueAt(0, 3))) {
                    throw new AssertionError("Prioridad incorrecta en tabla Listos: " + modeloListos.getValueAt(0, 3));
                }

                // 2. Proceso nuevo (no cabe en RAM restante libre: 100 - 40 = 60 libre, pide 80)
                var pNuevo = controller.createProcess("P-Nuevo", c1.getId(), ProcessType.IO_BOUND, 15, 80, 2, 300);
                monitor.actualizarVista();

                if (modeloNuevos.getRowCount() != 1) {
                    throw new AssertionError("La tabla de Nuevos debería tener 1 fila esperando RAM");
                }
                if (!"P-Nuevo".equals(modeloNuevos.getValueAt(0, 1))) {
                    throw new AssertionError("Nombre incorrecto en tabla Nuevos: " + modeloNuevos.getValueAt(0, 1));
                }
                if (!"E/S".equals(modeloNuevos.getValueAt(0, 2))) {
                    throw new AssertionError("Tipo incorrecto en tabla Nuevos: " + modeloNuevos.getValueAt(0, 2));
                }

                // 3. Proceso bloqueado
                StateAccess.enter();
                try {
                    pListo.setBlockReason(BlockReason.WAITING_IO);
                    c1.getKernel().getBlockedQueue().enqueue(pListo);
                    c1.getKernel().getReadyQueue().removeElement(pListo);
                } finally {
                    StateAccess.exit();
                }
                monitor.actualizarVista();

                if (modeloBloqueados.getRowCount() != 1) {
                    throw new AssertionError("La tabla de Bloqueados debería tener 1 fila");
                }
                if (!"Espera E/S".equals(modeloBloqueados.getValueAt(0, 3))) {
                    throw new AssertionError("Motivo de bloqueo incorrecto: " + modeloBloqueados.getValueAt(0, 3));
                }

                // 4. Proceso terminado
                StateAccess.enter();
                try {
                    c1.getKernel().getBlockedQueue().removeElement(pListo);
                    c1.getKernel().getEndedQueue().enqueue(pListo);
                } finally {
                    StateAccess.exit();
                }
                monitor.actualizarVista();

                if (modeloTerminados.getRowCount() != 1) {
                    throw new AssertionError("La tabla de Terminados debería tener 1 fila");
                }
                if (!"P-Listo".equals(modeloTerminados.getValueAt(0, 1))) {
                    throw new AssertionError("Nombre incorrecto en tabla Terminados");
                }

                System.out.println("PASS: DetalleColasTablasTest exitoso. Las 4 tablas muestran procesos con sus atributos PCB correctamente.");
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }
}
