package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.SchedulingPolicyType;
import java.awt.*;
import javax.swing.*;

/** Prueba integrada del clic, registro real y tabla; ejecutable como main sin pantalla. */
public class CreacionComputadoresGuiTest {
    static Component buscar(Container raiz, Class<?> tipo, String texto) {
        for (Component c : raiz.getComponents()) {
            if (tipo.isInstance(c) && (texto == null || c instanceof AbstractButton b && texto.equals(b.getText()))) return c;
            if (c instanceof Container contenedor) {
                Component encontrado = buscar(contenedor, tipo, texto);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }
    private static PanelPrincipal panel;
    private static JButton crear;
    private static JTextField nombre;
    private static final SimulationController controller=SimulationController.getInstance();
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {

            controller.reset();
            panel = new PanelPrincipal();
            panel.mostrarPanel(PanelPrincipal.Destino.COMPUTADOR);
            crear = (JButton) buscar(panel, JButton.class, "Crear computador");
            nombre = (JTextField) buscar(panel, JTextField.class, null);
            crear.doClick();
            if (!controller.getComputers().isEmpty()) throw new AssertionError("Nombre vacío creó un computador");
            nombre.setText("  Avila Norte  ");
            ((JToggleButton) buscar(panel, JToggleButton.class, "EDF")).doClick();
            crear.doClick(); crear.doClick();
        });
        GuiTestDrain.await();
        SwingUtilities.invokeAndWait(() -> {
            if (controller.getComputers().size() != 1) throw new AssertionError("Aceptó duplicado");
            nombre.setText(" avila norte ");
            crear.doClick();
        });
        GuiTestDrain.await();
        SwingUtilities.invokeAndWait(() -> {
            if (controller.getComputers().size() != 1) throw new AssertionError("Aceptó variante de nombre");
            if (!(nombre.getBorder() instanceof javax.swing.border.LineBorder borde)
                    || !borde.getLineColor().equals(new Color(166, 44, 38))) throw new AssertionError("Falta borde rojo");
            try {
                controller.createComputer("AVILA NORTE", 128, SchedulingPolicyType.FCFS);
                throw new AssertionError("Controlador aceptó duplicado directo");
            } catch (IllegalArgumentException expected) {
                if (!expected.getMessage().equals("Asegúrate de usar un nombre único")) throw new AssertionError("Mensaje");
            }
            nombre.setText("Avila Sur");
            crear.doClick();
        });
        GuiTestDrain.await();
        SwingUtilities.invokeAndWait(() -> {
            if (controller.getComputers().size() != 2) throw new AssertionError("Faltan instancias");
            var a = controller.getComputers().get(0);
            var b = controller.getComputers().get(1);
            if (b.getId() != a.getId() + 1 || !a.getName().equals("Avila Norte")) throw new AssertionError("Identidad/nombre");
            if (a.getRam().getTotalSize() != 128 || a.getPolitica() != SchedulingPolicyType.EDF) throw new AssertionError("Configuración");
        });
        // invokeLater del controlador debe ejecutarse antes de consultar la tabla.
        SwingUtilities.invokeAndWait(() -> {

            var a = controller.getComputers().get(0);
            JTable tabla = (JTable) buscar(panel, JTable.class, null);
            if (tabla.getRowCount() != 2 || !tabla.getValueAt(0, 1).equals(a.getName())) throw new AssertionError("Tabla");
            panel.mostrarPanel(PanelPrincipal.Destino.MENU);
            panel.mostrarPanel(PanelPrincipal.Destino.COMPUTADOR);
            if (tabla.getRowCount() != 2) throw new AssertionError("Navegación");
            if (controller.getCurrentCycle() != 0 || controller.isRunning()) throw new AssertionError("Reloj arrancó");
            System.out.println("PASS: validación, dos instancias, nombre, RAM, política, tabla y reloj detenido");
        });
    }
}
