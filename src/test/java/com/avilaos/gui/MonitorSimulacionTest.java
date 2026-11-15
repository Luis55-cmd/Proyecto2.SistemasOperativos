package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** El menú debe abrir las vistas vigentes sin mantener las antiguas como páginas. */
public class MonitorSimulacionTest {
    private static void check(boolean ok, String mensaje) { if (!ok) throw new AssertionError(mensaje); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var controller = SimulationController.getInstance();
            controller.reset();
            var panel = new PanelPrincipal();
            var simulacion = (JButton) CreacionComputadoresGuiTest.buscar(panel, JButton.class, "05   Simulación");
            var metricas = (JButton) CreacionComputadoresGuiTest.buscar(panel, JButton.class, "06   Métricas");
            check(simulacion != null && metricas != null, "Entradas principales del menú");
            check(CreacionComputadoresGuiTest.buscar(panel, JButton.class, "07   Nueva vista") == null, "Menú sin duplicado de simulación");
            check(CreacionComputadoresGuiTest.buscar(panel, JButton.class, "08   Métricas copia") == null, "Menú sin duplicado de métricas");
            simulacion.doClick();
            check(panel.getDestinoActual() == PanelPrincipal.Destino.SIMULACION, "Navegación a simulación");
            check(CreacionComputadoresGuiTest.buscar(panel, VistaSimulacionAlternativa.class, null) != null, "Vista de simulación vigente");
            check(CreacionComputadoresGuiTest.buscar(panel, MonitorSimulacion.class, null) == null, "No se construye el monitor anterior");
            metricas.doClick();
            check(panel.getDestinoActual() == PanelPrincipal.Destino.METRICAS, "Navegación a métricas");
            var vista = (PanelMetricasCopia) CreacionComputadoresGuiTest.buscar(panel, PanelMetricasCopia.class, null);
            check(vista != null, "Vista de métricas vigente");
            var tabs = (JTabbedPane) CreacionComputadoresGuiTest.buscar(vista, JTabbedPane.class, null);
            check(tabs != null && tabs.getTabCount() == 3 && tabs.getTitleAt(0).equals("Computadores"), "Métricas con tres secciones");
            check(CreacionComputadoresGuiTest.buscar(panel, PanelMetricas.class, null) == null, "No se construye la vista anterior de métricas");
            controller.reset();
            System.out.println("PASS: navegación a las dos vistas vigentes");
        });
        System.exit(0);
    }
}
