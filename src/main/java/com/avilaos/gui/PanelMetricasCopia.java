package com.avilaos.gui;

import com.avilaos.simulation.SimulationController;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import com.avilaos.simulation.SimulationSnapshot.Node;
import com.avilaos.simulation.SimulationSnapshot.Usage;
import com.avilaos.disk.DiskSchedulingPolicyType;
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

/** Vista de métricas del sistema, procesos, computadores y disco. */
public class PanelMetricasCopia extends JPanel {
    /** Métricas que se pueden graficar; el índice 0 (utilización) es la vista por defecto. */
    private static final String[] METRICAS = {
        "Utilización de CPU",
        "Procesos completados por tick",
        "Deadlines cumplidos",
        "Espera combinada anterior (promedio)",
        "Suspensiones",
        "Reactivaciones",
        "Desplazamiento acumulado del cabezal por política",
        "Desplazamiento total del cabezal",
        "Tiempo promedio de espera E/S por política",
        "Tiempo de espera promedio E/S (Global)"
    };
    private static final String[] TITULOS = {
        "Utilización acumulada del procesador",
        "Throughput acumulado por computador",
        "Tasa de éxito: deadlines cumplidos",
        "Espera combinada anterior por proceso completado",
        "Acumulado histórico de suspensiones",
        "Acumulado histórico de reactivaciones",
        "Desplazamiento acumulado del cabezal de disco por política",
        "Desplazamiento total acumulado del cabezal de disco",
        "Tiempo promedio de espera en cola de E/S por política",
        "Tiempo promedio de espera global en cola de E/S del disco"
    };
    private static final String[] EJES = {
        "Utilización (%)",
        "Throughput (proc/tick)",
        "Tasa de éxito (%)",
        "Espera promedio (ticks)",
        "Total suspensiones",
        "Total reactivaciones",
        "Desplazamiento acumulado (bloques)",
        "Desplazamiento total (bloques)",
        "Tiempo promedio de espera (ticks)",
        "Tiempo promedio de espera (ticks)"
    };
    private static final double[] RANGO_MINIMO = {5.0, 0.05, 5.0, 1.0, 2.0, 2.0, 10.0, 10.0, 2.0, 2.0};

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

    // Series históricas del disco - Desplazamiento
    private final XYSeries seriesDiskTotal = new XYSeries("Desplazamiento Total", true, false);
    private final XYSeries seriesDiskFIFO = new XYSeries("FIFO", true, false);
    private final XYSeries seriesDiskSSTF = new XYSeries("SSTF", true, false);
    private final XYSeries seriesDiskSCAN = new XYSeries("SCAN", true, false);
    private final XYSeries seriesDiskCSCAN = new XYSeries("C-SCAN", true, false);
    private final XYSeries seriesDiskLOOK = new XYSeries("LOOK", true, false);
    private final XYSeries seriesDiskCLOOK = new XYSeries("C-LOOK", true, false);

    // Series históricas del disco - Tiempo de espera
    private final XYSeries seriesDiskWait = new XYSeries("Espera Promedio Global", true, false);
    private final XYSeries seriesWaitFIFO = new XYSeries("FIFO (espera)", true, false);
    private final XYSeries seriesWaitSSTF = new XYSeries("SSTF (espera)", true, false);
    private final XYSeries seriesWaitSCAN = new XYSeries("SCAN (espera)", true, false);
    private final XYSeries seriesWaitCSCAN = new XYSeries("C-SCAN (espera)", true, false);
    private final XYSeries seriesWaitLOOK = new XYSeries("LOOK (espera)", true, false);
    private final XYSeries seriesWaitCLOOK = new XYSeries("C-LOOK (espera)", true, false);
   
    private final JLabel lblRendimientoGlobal = new JLabel("Resumen del sistema");
    private final JLabel lblTiempos = new JLabel("Sin procesos completados");
    private final JLabel lblDisco = new JLabel("DISCO · Desplazamiento cabezal: 0 bloques · Espera prom. E/S: 0.0 ticks · Atendidas: 0");
    private final JLabel lblDetalle = new JLabel(" ");
    
    // TABLA EQUIPOS ACTUALIZADA CON SUSPENSIONES Y REACTIVACIONES
    private final DefaultTableModel tablaEquipos = new DefaultTableModel(
            new Object[]{"Computador", "CPU usada (%)", "Completados", "Completados/tick", "Respuesta promedio (ticks)", "Deadlines cumplidos", "Listo total (ticks)", "Bloqueado total (ticks)", "Semáforos total (ticks)", "Suspensiones", "Reactivaciones"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    
    // TABLA PROCESOS ACTUALIZADA CON SUSPENSIONES Y REACTIVACIONES
    private final DefaultTableModel tablaProcesos = new DefaultTableModel(
            new Object[]{"ID", "Proceso", "Computador", "Estado", "En listo (ticks)", "Bloqueado (ticks)", "En semáforos (ticks)", "Suspensiones", "Reactivaciones"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    // TABLA DE POLÍTICAS DE DISCO (incluye desplazamiento y espera promedio por política)
    private final DefaultTableModel tablaDisco = new DefaultTableModel(
            new Object[]{"Política de disco", "Solicitudes atendidas", "% del total", "Desplazamiento acumulado (bloques)", "Promedio bloques/solicitud", "Tiempo prom. espera (ticks)", "Estado actual"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public PanelMetricasCopia(SimulationController controller) {
        this.controller = controller;
        this.dataset = new XYSeriesCollection();
        this.seriesPorMetrica = new XYSeries[6][0];
        this.nodos = new Node[0];

        XYSeries[] allDiskSeries = {
            seriesDiskTotal, seriesDiskWait,
            seriesDiskFIFO, seriesDiskSSTF, seriesDiskSCAN, seriesDiskCSCAN, seriesDiskLOOK, seriesDiskCLOOK,
            seriesWaitFIFO, seriesWaitSSTF, seriesWaitSCAN, seriesWaitCSCAN, seriesWaitLOOK, seriesWaitCLOOK
        };
        for (XYSeries s : allDiskSeries) {
            s.setMaximumItemCount(2000);
        }
        
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
        
        JPanel pnlGlobal = new JPanel(new GridLayout(3, 1, 4, 4));
        pnlGlobal.add(lblRendimientoGlobal);
        pnlGlobal.add(lblTiempos);
        pnlGlobal.add(lblDisco);
        
        pnlInferior.add(pnlGlobal, BorderLayout.NORTH);
        JTabbedPane tablas = new JTabbedPane();
        tablas.addTab("Computadores", crearTabla(tablaEquipos));
        tablas.addTab("Procesos", crearTabla(tablaProcesos));
        tablas.addTab("Políticas de Disco", crearTabla(tablaDisco));
        pnlInferior.add(tablas, BorderLayout.CENTER);
        lblDetalle.setText("Totales de procesos y disco en tiempo real · desplazamiento y espera promedio por política");
        pnlInferior.add(lblDetalle, BorderLayout.SOUTH);
        
        pnlInferior.setPreferredSize(new Dimension(800, 320));
        add(pnlInferior, BorderLayout.SOUTH);
    }

    private static JScrollPane crearTabla(DefaultTableModel modelo) {
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < tabla.getColumnCount(); i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(Math.max(105, tabla.getColumnName(i).length() * 8 + 25));
        }
        return new JScrollPane(tabla);
    }

    /** Valor de la métrica {@code m} para una muestra de un computador. */
    private static double valor(int m, Usage u) {
        return switch (m) {
            case 1 -> u.throughput();
            case 2 -> u.successRate();
            case 3 -> u.waitTime();
            case 4 -> u.suspensions();
            case 5 -> u.reactivations();
            default -> u.percent();
        };
    }

    /** Muestra en el único gráfico las series de la métrica activa y ajusta título y eje. */
    private void aplicarMetricaActiva() {
        dataset.removeAllSeries();
        if (metricaActiva < 6) {
            java.awt.Color[] colores = {
                new java.awt.Color(52, 104, 75), new java.awt.Color(183, 108, 39),
                new java.awt.Color(62, 103, 150), new java.awt.Color(131, 78, 135)
            };
            if (seriesPorMetrica != null && metricaActiva < seriesPorMetrica.length) {
                for (int i = 0; i < seriesPorMetrica[metricaActiva].length; i++) {
                    dataset.addSeries(seriesPorMetrica[metricaActiva][i]);
                    renderer.setSeriesPaint(i, colores[i % colores.length]);
                    renderer.setSeriesStroke(i, i % 2 == 0
                            ? new java.awt.BasicStroke(3.0f)
                            : new java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_BUTT,
                                    java.awt.BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 6f}, 0f));
                }
            }
        } else if (metricaActiva == 6) {
            // Comparación de desplazamiento acumulado por política de disco
            dataset.addSeries(seriesDiskFIFO);
            dataset.addSeries(seriesDiskSSTF);
            dataset.addSeries(seriesDiskSCAN);
            dataset.addSeries(seriesDiskCSCAN);
            dataset.addSeries(seriesDiskLOOK);
            dataset.addSeries(seriesDiskCLOOK);
            dataset.addSeries(seriesDiskTotal);

            java.awt.Color[] coloresDisco = {
                new java.awt.Color(46, 125, 50),   // FIFO: Verde bosque
                new java.awt.Color(230, 81, 0),    // SSTF: Naranja
                new java.awt.Color(21, 101, 192),  // SCAN: Azul
                new java.awt.Color(106, 27, 154),  // C-SCAN: Púrpura
                new java.awt.Color(0, 131, 143),   // LOOK: Cian
                new java.awt.Color(93, 64, 55),    // C-LOOK: Marrón
                new java.awt.Color(183, 28, 28)    // Total: Carmesí
            };
            for (int i = 0; i < coloresDisco.length; i++) {
                renderer.setSeriesPaint(i, coloresDisco[i]);
                if (i == coloresDisco.length - 1) {
                    renderer.setSeriesStroke(i, new java.awt.BasicStroke(3.5f));
                } else {
                    renderer.setSeriesStroke(i, new java.awt.BasicStroke(2.5f));
                }
            }
        } else if (metricaActiva == 7) {
            // Desplazamiento total acumulado del cabezal
            dataset.addSeries(seriesDiskTotal);
            renderer.setSeriesPaint(0, new java.awt.Color(183, 28, 28));
            renderer.setSeriesStroke(0, new java.awt.BasicStroke(3.0f));
        } else if (metricaActiva == 8) {
            // Comparación de tiempo promedio de espera por política
            dataset.addSeries(seriesWaitFIFO);
            dataset.addSeries(seriesWaitSSTF);
            dataset.addSeries(seriesWaitSCAN);
            dataset.addSeries(seriesWaitCSCAN);
            dataset.addSeries(seriesWaitLOOK);
            dataset.addSeries(seriesWaitCLOOK);
            dataset.addSeries(seriesDiskWait);

            java.awt.Color[] coloresEspera = {
                new java.awt.Color(46, 125, 50),
                new java.awt.Color(230, 81, 0),
                new java.awt.Color(21, 101, 192),
                new java.awt.Color(106, 27, 154),
                new java.awt.Color(0, 131, 143),
                new java.awt.Color(93, 64, 55),
                new java.awt.Color(33, 33, 33)     // Global: Gris oscuro
            };
            for (int i = 0; i < coloresEspera.length; i++) {
                renderer.setSeriesPaint(i, coloresEspera[i]);
                renderer.setSeriesStroke(i, i == coloresEspera.length - 1
                        ? new java.awt.BasicStroke(3.5f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 6f}, 0f)
                        : new java.awt.BasicStroke(2.5f));
            }
        } else if (metricaActiva == 9) {
            // Tiempo promedio de espera global E/S
            dataset.addSeries(seriesDiskWait);
            renderer.setSeriesPaint(0, new java.awt.Color(21, 101, 192));
            renderer.setSeriesStroke(0, new java.awt.BasicStroke(3.0f));
        }

        chart.setTitle(TITULOS[metricaActiva]);
        ejeUso.setLabel(EJES[metricaActiva]);
        ejeUso.setAutoRangeMinimumSize(RANGO_MINIMO[metricaActiva]);
        ejeUso.setAutoRange(true);
    }

    public void actualizarMetricas() {
        var view = controller.snapshot();
        if(session != view.session) {
            nodos = new Node[0];
            seriesPorMetrica = new XYSeries[6][0];
            seriesDiskTotal.clear();
            seriesDiskWait.clear();
            seriesDiskFIFO.clear();
            seriesDiskSSTF.clear();
            seriesDiskSCAN.clear();
            seriesDiskCSCAN.clear();
            seriesDiskLOOK.clear();
            seriesDiskCLOOK.clear();
            seriesWaitFIFO.clear();
            seriesWaitSSTF.clear();
            seriesWaitSCAN.clear();
            seriesWaitCSCAN.clear();
            seriesWaitLOOK.clear();
            seriesWaitCLOOK.clear();
            dataset.removeAllSeries();
            session = view.session;
            lastSample = -1;
        }
        int cicloActual = view.cycle;

        var computadores = view.computers;
        int cantidadComputadores = computadores.size();
        
        // Conservar las series de las mismas instancias al agregar computadores.
        boolean cambio = nodos.length != cantidadComputadores;
        for (int i = 0; !cambio && i < cantidadComputadores; i++) cambio = nodos[i].getId() != computadores.get(i).getId();
        if (cambio) {
            XYSeries[][] siguientes = new XYSeries[6][cantidadComputadores];
            Node[] actuales = new Node[cantidadComputadores];
            for (int i = 0; i < cantidadComputadores; i++) {
                actuales[i] = computadores.get(i);
                for (int m = 0; m < 6; m++) {
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

        // Actualizar series desde el historial
        for(int h=0; h<view.history.size(); h++) {
            var sample = view.history.get(h);
            if(sample.cycle() < lastSample && !cambio) continue;
            for(int u=0; u<sample.usage().size(); u++) {
                var usage = sample.usage().get(u);
                for (int i = 0; i < cantidadComputadores; i++) {
                    if (usage.computerId() == computadores.get(i).getId()) {
                        for (int m = 0; m < 6; m++) {
                            seriesPorMetrica[m][i].addOrUpdate((double)sample.cycle(), valor(m, usage));
                        }
                    }
                }
            }
            if (sample.diskUsage() != null) {
                double x = (double) sample.cycle();
                var d = sample.diskUsage();
                seriesDiskTotal.addOrUpdate(x, (double) d.totalHeadMovement());
                seriesDiskWait.addOrUpdate(x, d.averageWaitTime());
                seriesDiskFIFO.addOrUpdate(x, (double) d.fifoMovement());
                seriesDiskSSTF.addOrUpdate(x, (double) d.sstfMovement());
                seriesDiskSCAN.addOrUpdate(x, (double) d.scanMovement());
                seriesDiskCSCAN.addOrUpdate(x, (double) d.cscanMovement());
                seriesDiskLOOK.addOrUpdate(x, (double) d.lookMovement());
                seriesDiskCLOOK.addOrUpdate(x, (double) d.clookMovement());

                seriesWaitFIFO.addOrUpdate(x, d.fifoAvgWait());
                seriesWaitSSTF.addOrUpdate(x, d.sstfAvgWait());
                seriesWaitSCAN.addOrUpdate(x, d.scanAvgWait());
                seriesWaitCSCAN.addOrUpdate(x, d.cscanAvgWait());
                seriesWaitLOOK.addOrUpdate(x, d.lookAvgWait());
                seriesWaitCLOOK.addOrUpdate(x, d.clookAvgWait());
            }
        }

        int procesosCompletadosGlobal = 0;
        int deadlinesCumplidosGlobal = 0;
        int finalizadosGlobal = 0;
        int tiempoRespuestaTotalGlobal = 0;
        long activosGlobal = 0, observadosGlobal = 0;
        tablaEquipos.setRowCount(0);
        tablaProcesos.setRowCount(0);
        tablaDisco.setRowCount(0);
        double maxUtil = -1.0;
        double minUtil = 101.0;
        
        // Actualizar datos de Computadores y Gráfico
        for (int i = 0; i < cantidadComputadores; i++) {
            var c = computadores.get(i);
            
            double utilizacion = c.getCiclosObservados() == 0 ? 0.0 : ((double) c.getCiclosActivos() / c.getCiclosObservados()) * 100.0;
            
            activosGlobal += c.getCiclosActivos();
            observadosGlobal += c.getCiclosObservados();
            
            int listos = 0, bloqueados = 0, semaforos = 0, suspensiones = 0, reactivaciones = 0;
            
            for (int p = 0; p < view.processes.size(); p++) {
                var fila = view.processes.get(p);
                if (fila.computerId() != c.getId()) continue;
                listos += fila.process().readyWaitTicks();
                bloqueados += fila.process().blockedWaitTicks();
                semaforos += fila.process().semaphoreWaitTicks();
                suspensiones += fila.process().suspensions();
                reactivaciones += fila.process().reactivations();
            }
            
            tablaEquipos.addRow(new Object[]{
                c.getName(), String.format("%.1f", utilizacion), c.getProcesosCompletados(),
                String.format("%.4f", c.getCiclosObservados() == 0 ? 0.0 : (double) c.getProcesosCompletados() / c.getCiclosObservados()),
                String.format("%.1f", c.getProcesosCompletados() == 0 ? 0.0 : (double) c.getTotalTiempoRespuesta() / c.getProcesosCompletados()),
                c.getDeadlinesCumplidos() + " de " + c.getProcesosFinalizados(), 
                listos, bloqueados, semaforos, suspensiones, reactivaciones
            });
            
            if (utilizacion > maxUtil) maxUtil = utilizacion;
            if (utilizacion < minUtil) minUtil = utilizacion;
            
            finalizadosGlobal += c.getProcesosFinalizados();
            procesosCompletadosGlobal += c.getProcesosCompletados();
            deadlinesCumplidosGlobal += c.getDeadlinesCumplidos();
            tiempoRespuestaTotalGlobal += c.getTotalTiempoRespuesta();
        }

        lastSample = cicloActual;
        
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
            
            tablaProcesos.addRow(new Object[]{
                p.id(), p.name(), fila.computerName(), fila.state(),
                p.readyWaitTicks(), p.blockedWaitTicks(), p.semaphoreWaitTicks(),
                p.suspensions(), p.reactivations()
            });
            if (p.type() == com.avilaos.core.model.ProcessType.PRODUCER
                    || p.type() == com.avilaos.core.model.ProcessType.CONSUMER) {
                participantes++;
                esperaSemaforos += p.semaphoreWaitTicks();
            }
        }
        
        String promedioSemaforos = participantes == 0 ? "—" : String.format("%.1f", (double) esperaSemaforos / participantes);
        lblTiempos.setText(String.format("Respuesta promedio (completados): %.1f ticks   ·   Bloqueo en semáforos promedio: %s ticks/proceso   ·   Brecha de CPU: %.1f puntos",
                tiempoRespuestaPromedio, promedioSemaforos, equidad));

        // Actualizar datos del Disco
        var d = view.disk;
        if (d != null) {
            int totalAtendidas = d.completedRequestsCount();
            lblDisco.setText(String.format("DISCO   ·   Desplazamiento total: %d bloques   ·   Espera prom. cola E/S: %.2f ticks   ·   Atendidas: %d (FIFO: %d | SSTF: %d | SCAN: %d | C-SCAN: %d)   ·   Activa: %s",
                    d.totalHeadMovement(), d.averageWaitTime(), totalAtendidas,
                    d.fifoServed(), d.sstfServed(), d.scanServed(), d.cscanServed(), d.activePolicy()));

            DiskSchedulingPolicyType activa = d.activePolicy();

            Object[][] politicas = {
                {"FIFO", d.fifoServed(), d.fifoMovement(), d.fifoAvgWait(), activa == DiskSchedulingPolicyType.FIFO},
                {"SSTF", d.sstfServed(), d.sstfMovement(), d.sstfAvgWait(), activa == DiskSchedulingPolicyType.SSTF},
                {"SCAN", d.scanServed(), d.scanMovement(), d.scanAvgWait(), activa == DiskSchedulingPolicyType.SCAN},
                {"C-SCAN", d.cscanServed(), d.cscanMovement(), d.cscanAvgWait(), activa == DiskSchedulingPolicyType.C_SCAN},
                {"LOOK", d.lookServed(), d.lookMovement(), d.lookAvgWait(), activa == DiskSchedulingPolicyType.LOOK},
                {"C-LOOK", d.clookServed(), d.clookMovement(), d.clookAvgWait(), activa == DiskSchedulingPolicyType.C_LOOK}
            };

            for (Object[] pol : politicas) {
                String nombre = (String) pol[0];
                int atendidas = (Integer) pol[1];
                int mov = (Integer) pol[2];
                double esperaProm = (Double) pol[3];
                boolean esActiva = (Boolean) pol[4];
                double pct = totalAtendidas == 0 ? 0.0 : ((double) atendidas / totalAtendidas) * 100.0;
                String promMov = atendidas == 0 ? "—" : String.format("%.2f", (double) mov / atendidas);
                String strEspera = atendidas == 0 ? "—" : String.format("%.2f", esperaProm);

                tablaDisco.addRow(new Object[]{
                    nombre, atendidas, String.format("%.1f%%", pct), mov, promMov, strEspera, esActiva ? "ACTIVA" : "Inactiva"
                });
            }

            String promTotal = totalAtendidas == 0 ? "—" : String.format("%.2f", (double) d.totalHeadMovement() / totalAtendidas);
            String esperaTotal = totalAtendidas == 0 ? "—" : String.format("%.2f", d.averageWaitTime());
            tablaDisco.addRow(new Object[]{
                "TOTAL ACUMULADO", totalAtendidas, "100.0%", d.totalHeadMovement(), promTotal, esperaTotal, activa.name()
            });
        }

        lblDetalle.setText("Métricas de la ejecución actual · compare políticas con cargas iniciales equivalentes");
    }
}
