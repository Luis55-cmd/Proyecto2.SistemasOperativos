package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.*;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartPanel;
import org.jfree.data.xy.XYDataset;

/** Contratos de medición con reloj manual: no dependen del tiempo real. */
public class MetricasTest {
    private static void check(boolean valor, String mensaje) {
        if (!valor) throw new AssertionError(mensaje);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var c = SimulationController.getInstance();
            c.reset();
            for (int i = 0; i < 5; i++) com.avilaos.core.kernel.GlobalClock.getInstance().step();
            var pc = c.createComputer("Tarde", 128, SchedulingPolicyType.FCFS);
            var panel = new PanelMetricas(c);
            var p = c.createProcess("A", pc.getId(), ProcessType.CPU_BOUND, 2, 16, 1, 20);
            check(p.getArrivalCycle() == 5, "LLEGADA: ciclo real de creación");
            c.stepSimulation();
            panel.actualizarMetricas();
            var chart = (ChartPanel) CreacionComputadoresGuiTest.buscar(panel, ChartPanel.class, null);
            XYDataset datos = chart.getChart().getXYPlot().getDataset();
            check(pc.getCiclosActivos() == 1 && pc.getCiclosObservados() == 1, "ACTIVIDAD: un tick activo observado");
            check(datos.getYValue(0, datos.getItemCount(0)-1) == 100.0, "UTILIZACION: no penalizar ciclos anteriores a creación");
            check(datos.getXValue(0, datos.getItemCount(0)-1) == 6.0, "EJE: ciclo simulado");
            panel.actualizarMetricas();
            check(datos.getItemCount(0) == 2, "GRAFICO: refrescar no duplica tick");
            c.stepSimulation();
            panel.actualizarMetricas();
            check(pc.getProcesosCompletados() == 1 && pc.getDeadlinesCumplidos() == 1, "FINAL: completar registra métricas");
            check(pc.getTotalTiempoRespuesta() == 1, "RESPUESTA: primer despacho menos llegada");
            pc.getKernel().terminateProcess(pc, p);
            check(pc.getProcesosFinalizados() == 1, "IDEMPOTENCIA: terminar dos veces no duplica");
            c.createComputer("Otro", 128, SchedulingPolicyType.FCFS);
            panel.actualizarMetricas();
            check(datos.getSeriesCount() == 2 && datos.getItemCount(0) == 3, "HISTORIA: agregar conserva puntos");
            var abortado = c.createProcess("Vence", pc.getId(), ProcessType.CPU_BOUND, 5, 16, 1, 1);
            c.stepSimulation();
            check(abortado.isTerminated(), "DEADLINE: proceso abortado");
            check(pc.getProcesosFinalizados() == 2 && pc.getProcesosCompletados() == 1 && pc.getDeadlinesCumplidos() == 1,
                    "ABORTO: no cuenta como completado ni deadline cumplido");
            panel.actualizarMetricas();
            c.reset();
            panel.actualizarMetricas();
            check(datos.getSeriesCount() == 0, "RESET: limpia gráfico");
            System.out.println("PASS MetricasTest");
        });
    }
}
