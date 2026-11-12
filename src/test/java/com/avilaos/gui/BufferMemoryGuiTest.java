package com.avilaos.gui;
import javax.swing.*;
import java.awt.*;
import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.SchedulingPolicyType;

public class BufferMemoryGuiTest {
    private static Component buscar(Container root, String nombre) {
        for (Component c : root.getComponents()) {
            if (nombre.equals(c.getName())) return c;
            if (c instanceof Container child) {
                Component result = buscar(child, nombre);
                if (result != null) return result;
            }
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var c = SimulationController.getInstance(); c.reset();
            var pc = c.createComputer("Host", 128, SchedulingPolicyType.FCFS);
            var panel = new PanelPrincipal(); panel.mostrarPanel(PanelPrincipal.Destino.BUFFER);
            var capacidad = (JSpinner) buscar(panel, "capacidadBuffer");
            var ram = (JTextField) buscar(panel, "ramBuffer");
            if (ram.isEditable()) throw new AssertionError("LECTURA: RAM no editable");
            if (!ram.getText().equals("10")) throw new AssertionError("INICIAL: RAM 10");
            capacidad.setValue(23);
            if (!ram.getText().equals("23")) throw new AssertionError("PREVIEW: RAM sigue capacidad");
            ((JSpinner.DefaultEditor) capacidad.getEditor()).getTextField().setText("17");
            try { capacidad.commitEdit(); } catch (java.text.ParseException e) { throw new AssertionError(e); }
            if (!ram.getText().equals("17")) throw new AssertionError("TEXTO: RAM sigue edición escrita");
            ((JButton) CreacionComputadoresGuiTest.buscar(panel, JButton.class, "Crear y registrar Buffer")).doClick();
        });
        GuiTestDrain.await();
        SwingUtilities.invokeAndWait(() -> {
            var c=SimulationController.getInstance();
            var pc=c.getComputerById(1);
            if (c.getBuffers().size() != 1 || pc.getMemoryManager().getUsedMemory() != 17
                    || c.getBuffers().get(0).getSizeInMemory() != 17) throw new AssertionError("CREACION: RAM 17");
            System.out.println("PASS BufferMemoryGuiTest");
        });
    }
}
