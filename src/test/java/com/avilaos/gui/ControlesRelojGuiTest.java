package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import com.avilaos.core.kernel.GlobalClock;
import javax.swing.*;

/** Prueba real del reloj sin computadores. Las esperas ocurren fuera del EDT. */
public class ControlesRelojGuiTest {
    private static MonitorSimulacion monitor;
    private static JButton boton(String texto) {
        return (JButton) CreacionComputadoresGuiTest.buscar(monitor, JButton.class, texto);
    }
    public static void main(String[] args) throws Exception {
        var controller = SimulationController.getInstance();
        controller.reset(); controller.setCycleDurationMs(1000);
        try {
            SwingUtilities.invokeAndWait(() -> {
                monitor = new MonitorSimulacion(controller, () -> {}); monitor.actualizarVista();
                if (boton("Pausar").isEnabled() || !boton("+1 tick").isEnabled()) throw new AssertionError("Inicial");
                var combo = (JComboBox<?>) CreacionComputadoresGuiTest.buscar(monitor, JComboBox.class, null);
                combo.setSelectedItem(100);
                if (controller.getCycleDurationMs() != 1000 || !boton("Aplicar").isEnabled()) throw new AssertionError("Aplicó sin clic");
                monitor.actualizarVista();
                if (!combo.getSelectedItem().equals(100)) throw new AssertionError("Borró selección pendiente");
                boton("Aplicar").doClick();
            });
            GuiTestDrain.await();
            SwingUtilities.invokeAndWait(() -> {
                monitor.actualizarVista();
                if (controller.getCycleDurationMs() != 100 || controller.isRunning() || controller.getCurrentCycle() != 0) throw new AssertionError("Aplicar");
                boton("+1 tick").doClick();
            });
            GuiTestDrain.await();
            SwingUtilities.invokeAndWait(() -> {
                monitor.actualizarVista();
                if (controller.getCurrentCycle() != 1) throw new AssertionError("Paso");
                boton("Iniciar").doClick();
            });
            GuiTestDrain.await();
            SwingUtilities.invokeAndWait(() -> {
                monitor.actualizarVista();
                if (boton("+1 tick").isEnabled() || boton("Iniciar").isEnabled()) throw new AssertionError("Activa");
            });
            Thread.sleep(250);
            SwingUtilities.invokeAndWait(() -> boton("Pausar").doClick());
            Thread.sleep(150); // Deja completar cualquier tick en tránsito.
            int pausado = controller.getCurrentCycle();
            if (pausado <= 1) throw new AssertionError("No avanza sin computadores");
            Thread.sleep(200);
            if (controller.getCurrentCycle() != pausado) throw new AssertionError("No pausó");
            SwingUtilities.invokeAndWait(() -> {
                monitor.actualizarVista();
                if (!boton("Reanudar").isEnabled()) throw new AssertionError("Reanudar");
                boton("+1 tick").doClick();
            });
            GuiTestDrain.await();
            SwingUtilities.invokeAndWait(() -> {
                monitor.actualizarVista();
                if (controller.getCurrentCycle() != pausado + 1) throw new AssertionError("Paso pausado");
                boton("Reanudar").doClick();
            });
            Thread.sleep(250);
            if (controller.getCurrentCycle() <= pausado + 1) throw new AssertionError("No reanudó");
            System.out.println("PASS: aplicar explícito, paso detenido/pausado, avance sin computadores, pausa y reanudación");
        } finally { GlobalClock.getInstance().stop(); }
    }
}
