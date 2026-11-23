package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import javax.swing.JButton;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

public class VelocidadDiscoGuiTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        try {
            SwingUtilities.invokeAndWait(() -> {
                var panel = new PanelPrincipal();
                panel.mostrarPanel(PanelPrincipal.Destino.DISCO);
                var boton = (JButton) CreacionComputadoresGuiTest.buscar(panel, JButton.class, "Aplicar velocidad");
                if (boton == null) throw new AssertionError("Falta control de velocidad");
                var valor = (JSpinner) CreacionComputadoresGuiTest.buscar(boton.getParent(), JSpinner.class, null);
                if (valor == null) throw new AssertionError("Falta entrada de velocidad");
                valor.setValue(7);
                boton.doClick();
            });
            GuiTestDrain.await();
            if (c.getDisk().getHeadSpeedPerTick() != 7) throw new AssertionError("La interfaz no aplicó la velocidad");
            System.out.println("PASS: control de velocidad conectado al disco");
        } finally { c.reset(); }
    }
}
