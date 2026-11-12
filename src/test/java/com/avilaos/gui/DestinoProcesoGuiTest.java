package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.SchedulingPolicyType;
import javax.swing.*;

/** Comprueba navegación sin destinos, actualización real y conservación del borrador. */
public class DestinoProcesoGuiTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var controller = SimulationController.getInstance();
            controller.reset();
            var panel = new PanelPrincipal();
            panel.mostrarPanel(PanelPrincipal.Destino.PROCESO);
            var editor = (EditorProceso) CreacionComputadoresGuiTest.buscar(panel, EditorProceso.class, null);
            var destino = (JComboBox<?>) CreacionComputadoresGuiTest.buscar(editor, JComboBox.class, null);
            var crear = (JButton) CreacionComputadoresGuiTest.buscar(editor, JButton.class, "Crea un computador primero →");
            if (destino.isEnabled() || !crear.isVisible() || editor.getComputadorDestinoId() != null) throw new AssertionError("Estado vacío");
            crear.doClick();
            if (panel.getDestinoActual() != PanelPrincipal.Destino.COMPUTADOR) throw new AssertionError("No navegó");
            controller.createComputer("Norte", 128, SchedulingPolicyType.FCFS);
            var sur = controller.createComputer("Sur", 256, SchedulingPolicyType.EDF);
            panel.mostrarPanel(PanelPrincipal.Destino.PROCESO);
            if (destino.getItemCount() != 3 || !destino.isEnabled() || crear.isVisible()) throw new AssertionError("Destinos reales");
            destino.setSelectedIndex(1);
            ((JTextField) CreacionComputadoresGuiTest.buscar(editor, JTextField.class, null)).setText("Mi proceso");

            panel.mostrarPanel(PanelPrincipal.Destino.COMPUTADOR);
            controller.createComputer("Este", 512, SchedulingPolicyType.FCFS);
            panel.mostrarPanel(PanelPrincipal.Destino.PROCESO);
            if (destino.getItemCount() != 4 || editor.getComputadorDestinoId() != sur.getId()) throw new AssertionError("No conserva ID");
            if (CreacionComputadoresGuiTest.buscar(editor, JTable.class, null) == null) throw new AssertionError("Falta la tabla de procesos");
            if (!((JTextField) CreacionComputadoresGuiTest.buscar(editor, JTextField.class, null)).getText().equals("Mi proceso")) throw new AssertionError("Borró nombre");
            if (controller.getCurrentCycle() != 0) throw new AssertionError("Avanzó reloj");
            System.out.println("PASS: sin destinos, navegación, lista dinámica, selección por ID y borrador conservado");
        });
    }
}
