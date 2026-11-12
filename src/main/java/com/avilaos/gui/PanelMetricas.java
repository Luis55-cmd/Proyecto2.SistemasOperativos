/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.gui;

/**
 *
 * @author rafr
 */
import com.avilaos.simulation.SimulationController;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import com.avilaos.simulation.SimulationSnapshot.Node;
import com.avilaos.simulation.SimulationSnapshot.Usage;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Dimension;

public class PanelMetricas extends JPanel {
    /** Métricas que se pueden graficar; el índice 0 (utilización) es la vista por defecto. */
    private static final String[] METRICAS = {
        "Utilización de CPU", "Throughput", "Tasa de éxito (deadlines)", "Tiempo de espera"
    };
    private static final String[] TITULOS = {
        "Utilización acumulada del procesador", "Throughput acumulado por computador",
        "Tasa de éxito: deadlines cumplidos", "Tiempo de espera promedio por proceso completado"
    };
    private static final String[] EJES = {
        "Utilización (%)", "Throughput (proc/tick)", "Tasa de éxito (%)", "Espera promedio (ticks)"
    };
    private static final double[] RANGO_MINIMO = {5.0, 0.05, 5.0, 1.0};

    private final SimulationController controller;
    private final XYSeriesCollection dataset;
    /** Una serie por computador para cada métrica; todas se alimentan siempre para no perder historial al alternar. */
    private XYSeries[][] seriesPorMetrica;
    private int metricaActiva = 0;
    private Node[] nodos;
    private long session=-1;
    private int lastSample=-1;
    private final JFreeChart chart;
    private final org.jfree.chart.axis.NumberAxis ejeUso;
    private final JComboBox<String> selectorMetrica = new JComboBox<>(METRICAS);
    private org.jfree.chart.renderer.xy.XYLineAndShapeRenderer renderer;
   
    private final JLabel lblRendimientoGlobal = new JLabel("Throughput: 0 | Deadlines: 0% | Equidad: 0%");
    private final JLabel lblTiempos = new JLabel("Tiempo Respuesta Promedio: 0 ms");
    private final DefaultTableModel tablaEquipos = new DefaultTableModel(
            new Object[]{"Computador", "CPU %", "Completados", "Proc/tick", "Respuesta (ticks)", "Espera (ticks)", "Deadline %"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JPanel panelBuffers = new JPanel(new GridLayout(0, 1));

    public PanelMetricas(SimulationController controller) {
        this.controller = controller;
        this.dataset = new XYSeriesCollection();
        this.seriesPorMetrica = new XYSeries[METRICAS.length][0];
        this.nodos = new Node[0];
        
        setLayout(new BorderLayout());
        
        // 1. Configurar Gráfico de JFreeChart
        chart = ChartFactory.createXYLineChart(
                TITULOS[0],
                "Ciclo global",
                EJES[0],
                dataset, org.jfree.chart.plot.PlotOrientation.VERTICAL,
                true, true, false
        );
        ejeUso = (org.jfree.chart.axis.NumberAxis) chart.getXYPlot().getRangeAxis();
        ejeUso.setRangeType(org.jfree.data.RangeType.POSITIVE);
        ejeUso.setAutoRangeIncludesZero(false);
        ejeUso.setAutoRangeStickyZero(false);
        ejeUso.setAutoRangeMinimumSize(RANGO_MINIMO[0]);
        ejeUso.setLowerMargin(0.08);
        ejeUso.setUpperMargin(0.08);
        ejeUso.setAutoRange(true);
        chart.getXYPlot().setSeriesRenderingOrder(org.jfree.chart.plot.SeriesRenderingOrder.FORWARD);
        chart.setBackgroundPaint(PanelPrincipal.PAPEL);
        chart.getXYPlot().setBackgroundPaint(PanelPrincipal.FONDO);
        renderer = (org.jfree.chart.renderer.xy.XYLineAndShapeRenderer) chart.getXYPlot().getRenderer();
        ChartPanel chartPanel = new ChartPanel(chart);
        add(chartPanel, BorderLayout.CENTER);

        // Selector: alterna la métrica que muestra el mismo gráfico.
        JPanel pnlSelector = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        pnlSelector.add(new JLabel("Métrica del gráfico:"));
        pnlSelector.add(selectorMetrica);
        selectorMetrica.addActionListener(e -> {
            int elegido = selectorMetrica.getSelectedIndex();
            if (elegido >= 0 && elegido != metricaActiva) {
                metricaActiva = elegido;
                aplicarMetricaActiva();
            }
        });
        add(pnlSelector, BorderLayout.NORTH);
        
        // 2. Configurar Panel de KPIs Inferior
        JPanel pnlInferior = new JPanel(new BorderLayout());
        pnlInferior.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel pnlGlobal = new JPanel(new GridLayout(2, 1, 5, 5));
        pnlGlobal.add(lblRendimientoGlobal);
        pnlGlobal.add(lblTiempos);
        
        pnlInferior.add(pnlGlobal, BorderLayout.NORTH);
        JPanel tablas = new JPanel(new GridLayout(2, 1, 0, 8));
        JTable tabla = new JTable(tablaEquipos);
        tabla.setRowHeight(24);
        tablas.add(new JScrollPane(tabla));
        tablas.add(new JScrollPane(panelBuffers));
        pnlInferior.add(tablas, BorderLayout.CENTER);
        
        pnlInferior.setPreferredSize(new Dimension(800, 270));
        add(pnlInferior, BorderLayout.SOUTH);
    }

    /** Valor de la métrica {@code m} para una muestra de un computador. */
    private static double valor(int m, Usage u) {
        return switch (m) {
            case 1 -> u.throughput();
            case 2 -> u.successRate();
            case 3 -> u.waitTime();
            default -> u.percent();
        };
    }

    /** Muestra en el único gráfico las series de la métrica activa y ajusta título y eje. */
    private void aplicarMetricaActiva() {
        dataset.removeAllSeries();
        java.awt.Color[] colores = {
            new java.awt.Color(52, 104, 75), new java.awt.Color(183, 108, 39),
            new java.awt.Color(62, 103, 150), new java.awt.Color(131, 78, 135)
        };
        for (int i = 0; i < seriesPorMetrica[metricaActiva].length; i++) {
            dataset.addSeries(seriesPorMetrica[metricaActiva][i]);
            renderer.setSeriesPaint(i, colores[i % colores.length]);
            renderer.setSeriesStroke(i, i % 2 == 0
                    ? new java.awt.BasicStroke(3.0f)
                    : new java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_BUTT,
                            java.awt.BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 6f}, 0f));
        }
        chart.setTitle(TITULOS[metricaActiva]);
        ejeUso.setLabel(EJES[metricaActiva]);
        ejeUso.setAutoRangeMinimumSize(RANGO_MINIMO[metricaActiva]);
        ejeUso.setAutoRange(true);
    }

    public void actualizarMetricas() {
        var view=controller.snapshot();
        if(session!=view.session) {
            nodos=new Node[0]; seriesPorMetrica=new XYSeries[METRICAS.length][0];
            dataset.removeAllSeries(); session=view.session; lastSample=-1;
        }
        int cicloActual = view.cycle;


        var computadores = view.computers;
        int cantidadComputadores = computadores.size();
        
        // Conservar las series de las mismas instancias al agregar computadores.
        boolean cambio = nodos.length != cantidadComputadores;
        for (int i = 0; !cambio && i < cantidadComputadores; i++) cambio = nodos[i].getId() != computadores.get(i).getId();
        if (cambio) {
            XYSeries[][] siguientes = new XYSeries[METRICAS.length][cantidadComputadores];
            Node[] actuales = new Node[cantidadComputadores];
            for (int i = 0; i < cantidadComputadores; i++) {
                actuales[i] = computadores.get(i);
                for (int m = 0; m < METRICAS.length; m++) {
                    for (int j = 0; j < nodos.length; j++) {
                        if (nodos[j].getId() == actuales[i].getId()) siguientes[m][i] = seriesPorMetrica[m][j];
                    }
                    if (siguientes[m][i] == null) {
                        siguientes[m][i] = new XYSeries(actuales[i].getName(), true, false);
                        siguientes[m][i].setMaximumItemCount(2000);
                    }
                }
            }
            nodos = actuales;
            seriesPorMetrica = siguientes;
            aplicarMetricaActiva();
        }

        int procesosCompletadosGlobal = 0;
        int deadlinesCumplidosGlobal = 0;
        int finalizadosGlobal = 0;
        int tiempoRespuestaTotalGlobal = 0;
        int tiempoEsperaTotalGlobal = 0;
        long activosGlobal = 0, observadosGlobal = 0;
        tablaEquipos.setRowCount(0);
        double maxUtil = -1.0;
        double minUtil = 101.0;
        

        
        // Actualizar datos de Computadores y Gráfico
        for (int i = 0; i < cantidadComputadores; i++) {
            var c = computadores.get(i);
            
            double utilizacion = c.getCiclosObservados() == 0 ? 0.0 : ((double) c.getCiclosActivos() / c.getCiclosObservados()) * 100.0;
            for(int h=0;h<view.history.size();h++) {
                var sample=view.history.get(h);
                if(sample.cycle()<lastSample && !cambio) continue;
                for(int u=0;u<sample.usage().size();u++) {
                    var usage=sample.usage().get(u);
                    if(usage.computerId()==c.getId()) {
                        for (int m = 0; m < METRICAS.length; m++) {
                            seriesPorMetrica[m][i].addOrUpdate((double)sample.cycle(), valor(m, usage));
                        }
                    }
                }
            }
            
            activosGlobal += c.getCiclosActivos();
            observadosGlobal += c.getCiclosObservados();
            tablaEquipos.addRow(new Object[]{
                c.getName(), String.format("%.1f", utilizacion), c.getProcesosCompletados(),
                String.format("%.4f", c.getCiclosObservados() == 0 ? 0.0 : (double) c.getProcesosCompletados() / c.getCiclosObservados()),
                String.format("%.1f", c.getProcesosCompletados() == 0 ? 0.0 : (double) c.getTotalTiempoRespuesta() / c.getProcesosCompletados()),
                String.format("%.1f", c.getProcesosCompletados() == 0 ? 0.0 : (double) c.getTotalTiempoEspera() / c.getProcesosCompletados()),
                String.format("%.1f", c.getProcesosFinalizados() == 0 ? 0.0 : 100.0 * c.getDeadlinesCumplidos() / c.getProcesosFinalizados())
            });
            if (utilizacion > maxUtil) maxUtil = utilizacion;
            if (utilizacion < minUtil) minUtil = utilizacion;
            
            finalizadosGlobal += c.getProcesosFinalizados();
            procesosCompletadosGlobal += c.getProcesosCompletados();
            deadlinesCumplidosGlobal += c.getDeadlinesCumplidos();
            tiempoRespuestaTotalGlobal += c.getTotalTiempoRespuesta();
            tiempoEsperaTotalGlobal += c.getTotalTiempoEspera();
        }

        lastSample=cicloActual;
        // Calcular KPIs del Sistema
        double throughput = cicloActual == 0 ? 0.0 : (double) procesosCompletadosGlobal / cicloActual;
        double ratioDeadlines = finalizadosGlobal > 0
                ? ((double) deadlinesCumplidosGlobal / finalizadosGlobal) * 100 : 0.0;
        double equidad = (cantidadComputadores > 1 && maxUtil >= 0) ? (maxUtil - minUtil) : 0.0;
        double tiempoRespuestaPromedio = procesosCompletadosGlobal > 0 
                ? (double) tiempoRespuestaTotalGlobal / procesosCompletadosGlobal : 0.0;
        double tiempoEsperaPromedio = procesosCompletadosGlobal > 0
                ? (double) tiempoEsperaTotalGlobal / procesosCompletadosGlobal : 0.0;

        lblRendimientoGlobal.setText(String.format("Throughput Global: %.4f proc/tick | Deadlines Cumplidos: %.1f%% | Equidad (Brecha Util): %.1f%%", 
                throughput, ratioDeadlines, equidad));
        
        lblTiempos.setText(String.format("Respuesta media (completados): %.1f ticks | Espera media (completados): %.1f ticks | CPU global: %.1f%%",
                tiempoRespuestaPromedio, tiempoEsperaPromedio,
                observadosGlobal == 0 ? 0.0 : 100.0 * activosGlobal / observadosGlobal));

        // Actualizar KPIs de Buffers usando iteración estándar sobre tu MyLinkedList
        panelBuffers.removeAll();
        var buffers = view.buffers;
        for (int i = 0; i < buffers.size(); i++) {
            var b = buffers.get(i);
            String stats = String.format("Buffer %d -> Producidos: %d | Consumidos: %d", 
                    b.getId(), b.getProduced(), b.getComsumed());
            panelBuffers.add(new JLabel(stats));
        }
        
        panelBuffers.revalidate();
        panelBuffers.repaint();
    }
}
