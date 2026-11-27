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
            if (controller.getComputers().size() != 1) throw new AssertionError("Debe existir un computador");
            var equipo = controller.getComputers().get(0);
            if (!equipo.getName().equals("Avila Norte") || equipo.getRam().getTotalSize() != 128
                    || equipo.getPolitica() != SchedulingPolicyType.EDF)
                throw new AssertionError("Nombre, RAM o política incorrectos");
            try {
                controller.createComputer("AVILA NORTE", 128, SchedulingPolicyType.FCFS);
                throw new AssertionError("Controlador aceptó duplicado directo");
            } catch (IllegalArgumentException expected) {
                if (!expected.getMessage().equals("Asegúrate de usar un nombre único")) throw new AssertionError("Mensaje");
            }
            try {
                var field = PanelPrincipal.class.getDeclaredField("resumenNombre");
                field.setAccessible(true);
                var resumen = (JLabel) field.get(panel);
                if (!resumen.getText().contains("Avila Norte")) throw new AssertionError("Resumen no actualizado");
                panel.mostrarPanel(PanelPrincipal.Destino.MENU);
                panel.mostrarPanel(PanelPrincipal.Destino.COMPUTADOR);
                if (!resumen.getText().contains("Avila Norte")) throw new AssertionError("Resumen perdido al navegar");
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            if (controller.getCurrentCycle() != 0 || controller.isRunning()) throw new AssertionError("Reloj arrancó");
            System.out.println("PASS: creación de un computador, validación, resumen y reloj detenido");
        });
    }
}
