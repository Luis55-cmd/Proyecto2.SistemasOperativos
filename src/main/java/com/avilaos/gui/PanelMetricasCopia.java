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
import javax.swing.JTabbedPane;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Dimension;

/** Vista de métricas del sistema, procesos y buffers. */
public class PanelMetricasCopia extends JPanel {
    /** Métricas que se pueden graficar; el índice 0 (utilización) es la vista por defecto. */
    private static final String[] METRICAS = {
        "Utilización de CPU", "Procesos completados por tick", "Deadlines cumplidos", "Espera combinada anterior (promedio)"
    };
    private static final String[] TITULOS = {
        "Utilización acumulada del procesador", "Throughput acumulado por computador",
        "Tasa de éxito: deadlines cumplidos", "Espera combinada anterior por proceso completado"
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
   
    private final JLabel lblRendimientoGlobal = new JLabel("Resumen del sistema");
    private final JLabel lblTiempos = new JLabel("Sin procesos completados");
    private final JLabel lblDetalle = new JLabel(" ");
    private final DefaultTableModel tablaEquipos = new DefaultTableModel(
            new Object[]{"Computador", "CPU usada (%)", "Completados", "Completados/tick", "Respuesta promedio (ticks)", "Deadlines cumplidos", "Listo total (ticks)", "Bloqueado total (ticks)", "Semáforos total (ticks)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel tablaProcesos = new DefaultTableModel(
            new Object[]{"ID", "Proceso", "Computador", "Estado", "En listo (ticks)", "Bloqueado (ticks)", "En semáforos (ticks)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel tablaBuffers = new DefaultTableModel(
            new Object[]{"Buffer", "Computador anfitrión", "Elementos producidos", "Elementos consumidos", "Espera en semáforos promedio (ticks/proceso)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public PanelMetricasCopia(SimulationController controller) {
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
        JTabbedPane tablas = new JTabbedPane();
        tablas.addTab("Computadores", crearTabla(tablaEquipos));
        tablas.addTab("Procesos", crearTabla(tablaProcesos));
        tablas.addTab("Buffers", crearTabla(tablaBuffers));
        pnlInferior.add(tablas, BorderLayout.CENTER);
        lblDetalle.setText("Totales de todos los procesos · semáforos está incluido en bloqueado");
        pnlInferior.add(lblDetalle, BorderLayout.SOUTH);
        
        pnlInferior.setPreferredSize(new Dimension(800, 300));
        add(pnlInferior, BorderLayout.SOUTH);
    }

    private static JScrollPane crearTabla(DefaultTableModel modelo) {
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < tabla.getColumnCount(); i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(Math.max(100, tabla.getColumnName(i).length() * 8 + 25));
        }
        return new JScrollPane(tabla);
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
        long activosGlobal = 0, observadosGlobal = 0;
        tablaEquipos.setRowCount(0);
        tablaProcesos.setRowCount(0);
        tablaBuffers.setRowCount(0);
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
            int listos = 0, bloqueados = 0, semaforos = 0;
            for (int p = 0; p < view.processes.size(); p++) {
                var fila = view.processes.get(p);
                if (fila.computerId() != c.getId()) continue;
                listos += fila.process().readyWaitTicks();
                bloqueados += fila.process().blockedWaitTicks();
                semaforos += fila.process().semaphoreWaitTicks();
            }
            tablaEquipos.addRow(new Object[]{
                c.getName(), String.format("%.1f", utilizacion), c.getProcesosCompletados(),
                String.format("%.4f", c.getCiclosObservados() == 0 ? 0.0 : (double) c.getProcesosCompletados() / c.getCiclosObservados()),
                String.format("%.1f", c.getProcesosCompletados() == 0 ? 0.0 : (double) c.getTotalTiempoRespuesta() / c.getProcesosCompletados()),
                c.getDeadlinesCumplidos() + " de " + c.getProcesosFinalizados(), listos, bloqueados, semaforos
            });
            if (utilizacion > maxUtil) maxUtil = utilizacion;
            if (utilizacion < minUtil) minUtil = utilizacion;
            
            finalizadosGlobal += c.getProcesosFinalizados();
            procesosCompletadosGlobal += c.getProcesosCompletados();
            deadlinesCumplidosGlobal += c.getDeadlinesCumplidos();
            tiempoRespuestaTotalGlobal += c.getTotalTiempoRespuesta();
        }

        lastSample=cicloActual;
        // Calcular KPIs del Sistema
        double throughput = cicloActual == 0 ? 0.0 : (double) procesosCompletadosGlobal / cicloActual;
        double ratioDeadlines = finalizadosGlobal > 0
                ? ((double) deadlinesCumplidosGlobal / finalizadosGlobal) * 100 : 0.0;
        double equidad = (cantidadComputadores > 1 && maxUtil >= 0) ? (maxUtil - minUtil) : 0.0;
        double tiempoRespuestaPromedio = procesosCompletadosGlobal > 0 
                ? (double) tiempoRespuestaTotalGlobal / procesosCompletadosGlobal : 0.0;

        lblRendimientoGlobal.setText(String.format("RESUMEN   ·   CPU global: %.1f%%   ·   Completados: %d   ·   Completados/tick: %.4f   ·   Deadlines: %d de %d (%.1f%%)",
                observadosGlobal == 0 ? 0.0 : 100.0 * activosGlobal / observadosGlobal,
                procesosCompletadosGlobal, throughput, deadlinesCumplidosGlobal, finalizadosGlobal, ratioDeadlines));
        
        int participantes = 0, esperaSemaforos = 0;
        for (int i = 0; i < view.processes.size(); i++) {
            var fila = view.processes.get(i);
            var p = fila.process();
            tablaProcesos.addRow(new Object[]{p.id(), p.name(), fila.computerName(), fila.state(),
                    p.readyWaitTicks(), p.blockedWaitTicks(), p.semaphoreWaitTicks()});
            if (esProductorConsumidor(p)) {
                participantes++;
                esperaSemaforos += p.semaphoreWaitTicks();
            }
        }
        String promedioSemaforos = participantes == 0 ? "—" : String.format("%.1f", (double) esperaSemaforos / participantes);
        lblTiempos.setText(String.format("Respuesta promedio (completados): %.1f ticks   ·   Bloqueo en semáforos promedio: %s ticks/proceso   ·   Brecha de CPU: %.1f puntos",
                tiempoRespuestaPromedio, promedioSemaforos, equidad));

        // Actualizar KPIs de Buffers usando iteración estándar sobre tu MyLinkedList
        var buffers = view.buffers;
        for (int i = 0; i < buffers.size(); i++) {
            var b = buffers.get(i);
            String anfitrion = "ID " + b.getIdComp();
            for (int c = 0; c < computadores.size(); c++) {
                if (computadores.get(c).getId() == b.getIdComp()) anfitrion = computadores.get(c).getName();
            }
            int total = 0, cantidad = 0;
            for (int p = 0; p < view.processes.size(); p++) {
                var proceso = view.processes.get(p).process();
                if (esProductorConsumidor(proceso) && correspondeAlBuffer(proceso.targetBufferId(), b.getId())) {
                    cantidad++;
                    total += proceso.semaphoreWaitTicks();
                }
            }
            tablaBuffers.addRow(new Object[]{b.getId(), anfitrion, b.getProduced(), b.getComsumed(),
                    cantidad == 0 ? "—" : String.format("%.1f", (double) total / cantidad)});
        }
        lblDetalle.setText("Totales incluyen procesos activos y finalizados · semáforos está incluido en bloqueado · promedios de semáforos: productores y consumidores creados");
    }

    private static boolean esProductorConsumidor(com.avilaos.simulation.SimulationSnapshot.ProcessView p) {
        return p.type() == com.avilaos.core.model.ProcessType.PRODUCER || p.type() == com.avilaos.core.model.ProcessType.CONSUMER;
    }

    private static boolean correspondeAlBuffer(String id, int bufferId) {
        if (id == null) return false;
        try { return Integer.parseInt(id.replaceAll("[^0-9]", "")) == bufferId; }
        catch (NumberFormatException ex) { return false; }
    }
}
