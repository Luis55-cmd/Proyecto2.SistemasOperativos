package com.avilaos.gui;

import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.simulation.SimulationController;
import javax.swing.*;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/** Comprueba el formulario real y las solicitudes enviadas al controlador. */
public class CreacionProcesosDiscoTest {
    private static Object campo(Object objeto, String nombre) throws Exception {
        Field f = objeto.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(objeto);
    }
    private static void check(boolean ok, String mensaje) {
        if (!ok) throw new AssertionError(mensaje);
    }
    private static void esperar(BooleanSupplier condicion) throws Exception {
        long limite = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < limite) {
            final boolean[] listo = {false};
            SwingUtilities.invokeAndWait(() -> listo[0] = condicion.getAsBoolean());
            if (listo[0]) return;
            Thread.sleep(10);
        }
        throw new AssertionError("La interfaz no completó la solicitud");
    }
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        final EditorProceso[] editor = new EditorProceso[1];
        SwingUtilities.invokeAndWait(() -> editor[0] = new EditorProceso(() -> {}));
        var e = editor[0];
        var crear = (JButton) campo(e, "botonCrear");
        var crearPc = (JButton) campo(e, "crearComputador");
        var tipo = (JComboBox<?>) campo(e, "comboTipoProceso");
        var archivos = (JComboBox<?>) campo(e, "archivosLectura");
        var nombre = (JTextField) campo(e, "campoNombre");
        var mensaje = (JLabel) campo(e, "mensaje");
        SwingUtilities.invokeAndWait(() -> {
            check(!crear.isVisible() && crearPc.isVisible(), "sin computador solo se ofrece crearlo");
            check(tipo.getItemCount() == 2, "hay dos tipos de proceso");
        });
        var pc = c.createComputer("C1", 128, SchedulingPolicyType.FCFS);
        SwingUtilities.invokeAndWait(() -> { e.actualizarListas(); check(crear.isVisible() && !crearPc.isVisible(), "con computador se habilita creación"); tipo.setSelectedIndex(1); });
        esperar(() -> mensaje.getText().contains("No hay archivos"));
        SwingUtilities.invokeAndWait(() -> { nombre.setText("sin archivo"); e.generarPCB(); check(mensaje.getText().contains("Selecciona un archivo"), "rechaza lectura sin archivo"); });
        c.getFileSystemManager().createFile("/lectura", 3, "admin", FilePermission.PUBLIC, "test", 1);
        SwingUtilities.invokeAndWait(() -> { tipo.setSelectedIndex(0); tipo.setSelectedIndex(1); });
        esperar(() -> archivos.getItemCount() == 1);
        SwingUtilities.invokeAndWait(() -> { nombre.setText("lector"); e.generarPCB(); });
        esperar(() -> mensaje.getText().contains("creado:"));
        var lector = pc.getKernel().getProcessList().get(0);
        check(lector.getTotalInstructions() == 2 && lector.getAssignedMemory() == 2, "lectura utiliza dos instrucciones y dos de RAM");
        c.getDisk().setHeadPosition(30);
        c.stepSimulation();
        check(lector.isDiskRequestIssued() && !lector.isTerminated(), "lectura espera al disco");
        for (int i = 0; i < 100 && !lector.isTerminated(); i++) c.stepSimulation();
        check(lector.isTerminated() && c.getDisk().getCompletedRequestsCount() == 1, "lectura termina tras disco");
        SwingUtilities.invokeAndWait(() -> { tipo.setSelectedIndex(0); nombre.setText("cpu"); e.generarPCB(); });
        esperar(() -> mensaje.getText().contains("Proceso 'cpu' creado"));
        var cpu = pc.getKernel().getProcessList().get(1);
        check(cpu.getTotalInstructions() == 10 && cpu.getAssignedMemory() == 10, "CPU conserva sus instrucciones");
        c.reset();
        System.out.println("PASS CreacionProcesosDiscoTest");
    }
}
