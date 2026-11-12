package com.avilaos.gui;
import com.avilaos.simulation.SimulationSnapshot;
import com.avilaos.simulation.SimulationSnapshot.Node;
import com.avilaos.concurrency.GuiCommands;
import com.avilaos.concurrency.StateAccess;

import com.avilaos.hardware.Computer;
import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.ProcessState;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.simulation.SimulationController;
import com.avilaos.sync.Buffer;
import java.awt.*;
import java.io.File;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultCaret;
import static com.avilaos.gui.PanelPrincipal.*;

/** Monitor de lectura interactivo. Se actualiza vía Observer desde el SimulationController. */
public final class MonitorSimulacion extends JPanel {
    private final SimulationController controller;
    private SimulationSnapshot view;
    private final JTabbedPane pestanas = new JTabbedPane(JTabbedPane.BOTTOM, JTabbedPane.SCROLL_TAB_LAYOUT) {
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            Component sel = getSelectedComponent();
            if (sel != null) {
                Dimension selPref = sel.getPreferredSize();
                Insets insets = getInsets();
                int tabHeaderHeight = 38;
                d.height = selPref.height + insets.top + insets.bottom + tabHeaderHeight;
            }
            return d;
        }
    };
    private final JPanel tarjetas = new JPanel(new GridLayout(0, 2, 16, 16));
    private final JLabel reloj = texto("Reloj global · ciclo 0", 20, ACENTO, true);
    private final JLabel estado = texto("", 13, GRIS, false);
    private final JPanel vacio = tarjeta();
    private int[] ids = new int[0];
    private VistaComputador[] resumenes = new VistaComputador[0];
    private VistaComputador[] detalles = new VistaComputador[0];
    private PanelMetricas panelMetricas;
    private final JButton iniciar = accion("Iniciar", true);
    private final JButton pausar = accion("Pausar", false);
    private final JButton paso = accion("+1 tick", false);
    private final JButton aplicar = accion("Aplicar", false);
    private final JComboBox<Integer> intervalo = new JComboBox<>(new Integer[]{100, 250, 500, 1000});
    private final JLabel intervaloAplicado = texto("", 12, GRIS, false);
    
    // --- Tablas independientes para cada cola del Kernel ---
    private final JLabel tituloColas = texto("DETALLE DE COLAS DEL KERNEL (Comp. seleccionado)", 12, TINTA, true);

    private final DefaultTableModel modeloNuevos = new DefaultTableModel(
            new Object[]{"ID", "Nombre", "Tipo", "Prioridad", "Memoria (RAM)", "Instrucciones", "Deadline"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable tablaNuevos = new JTable(modeloNuevos);
    private final JScrollPane scrollNuevos = new JScrollPane(tablaNuevos);
    private final JLabel tituloNuevos = texto("COLA DE NUEVOS (NEW) · 0 procesos", 12, TINTA, true);

    private final DefaultTableModel modeloListos = new DefaultTableModel(
            new Object[]{"ID", "Nombre", "Tipo", "Prioridad", "T. Restante", "Deadline Rest.", "PC", "MAR", "Memoria (RAM)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable tablaListos = new JTable(modeloListos);
    private final JScrollPane scrollListos = new JScrollPane(tablaListos);
    private final JLabel tituloListos = texto("COLA DE LISTOS (READY) · 0 procesos", 12, TINTA, true);

    private final DefaultTableModel modeloBloqueados = new DefaultTableModel(
            new Object[]{"ID", "Nombre", "Tipo", "Motivo de Bloqueo", "Buffer", "Prioridad", "T. Restante", "Deadline Rest.", "Memoria (RAM)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable tablaBloqueados = new JTable(modeloBloqueados);
    private final JScrollPane scrollBloqueados = new JScrollPane(tablaBloqueados);
    private final JLabel tituloBloqueados = texto("COLA DE BLOQUEADOS (BLOCKED) · 0 procesos", 12, TINTA, true);

    private final DefaultTableModel modeloTerminados = new DefaultTableModel(
            new Object[]{"ID", "Nombre", "Tipo", "Prioridad", "Instrucciones Totales", "Memoria Liberada"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable tablaTerminados = new JTable(modeloTerminados);
    private final JScrollPane scrollTerminados = new JScrollPane(tablaTerminados);
    private final JLabel tituloTerminados = texto("COLA DE TERMINADOS (TERMINATED) · 0 procesos", 12, TINTA, true);

    private final JPanel panelDetalleColas = new JPanel();

    // --- Componentes para visualizar los Buffers Globales ---
    private final DefaultTableModel modeloBuffers = new DefaultTableModel(
            new Object[]{"Buffer", "Host", "Latencia (ticks)", "Elementos", "Capacidad", "Mutex", "Llenos", "Vac\u00edos", "Procesos Bloqueados"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable tablaBuffers = new JTable(modeloBuffers);
    private final JScrollPane scrollBuffers = new JScrollPane(tablaBuffers);
    private final JPanel panelDetalleBuffers = new JPanel(new BorderLayout(0, 8));
    private final JComboBox<String> comboBuffersLatencia = new JComboBox<>();
    private final JSpinner spinnerNuevaLatencia = new JSpinner(new SpinnerNumberModel(2, 0, 1000, 1));
    private final JButton btnActualizarLatencia = accion("Actualizar Latencia", false);
    private boolean actualizandoComboBuffers = false;

    private final JTextArea areaLog = new JTextArea();
    private final JScrollPane scrollLog = new JScrollPane(areaLog);
    private final JPanel panelLog = new JPanel(new BorderLayout(0, 8));

    public MonitorSimulacion(SimulationController controller, Runnable irAComputadores) {
        super(new BorderLayout(0, 0));
        this.controller = controller;
        this.view=controller.snapshot();
        setBackground(FONDO);
        
        JPanel contenedorPrincipal = new ScrollablePanel(new BorderLayout(0, 20));
        contenedorPrincipal.setBackground(FONDO);
        contenedorPrincipal.setBorder(BorderFactory.createEmptyBorder(28, 28, 24, 28));
        
        JPanel cabecera = new JPanel();
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        cabecera.setOpaque(false);
        cabecera.add(texto("SIMULACIÓN", 11, ACENTO, true));
        cabecera.add(Box.createVerticalStrut(8));
        cabecera.add(texto("Observa tu sistema", 28, TINTA, true));
        cabecera.add(Box.createVerticalStrut(12));
        cabecera.add(crearPanelPersistencia()); // Panel de Guardar/Cargar integrado
        cabecera.add(Box.createVerticalStrut(12));
        cabecera.add(reloj);
        cabecera.add(Box.createVerticalStrut(8));
        cabecera.add(estado);
        cabecera.add(Box.createVerticalStrut(12));
        cabecera.add(controlesReloj());
        cabecera.add(Box.createVerticalStrut(6));
        cabecera.add(intervaloAplicado);
        cabecera.add(Box.createVerticalStrut(16));
        
        // --- Configuración gráfica de las tablas de Colas ---
        panelDetalleColas.setLayout(new BoxLayout(panelDetalleColas, BoxLayout.Y_AXIS));
        panelDetalleColas.setOpaque(false);
        panelDetalleColas.setAlignmentX(LEFT_ALIGNMENT);
        
        tituloColas.setAlignmentX(LEFT_ALIGNMENT);
        panelDetalleColas.add(tituloColas);
        panelDetalleColas.add(Box.createVerticalStrut(10));
        
        panelDetalleColas.add(crearSeccionCola(tituloNuevos, scrollNuevos, tablaNuevos));
        panelDetalleColas.add(Box.createVerticalStrut(12));
        panelDetalleColas.add(crearSeccionCola(tituloListos, scrollListos, tablaListos));
        panelDetalleColas.add(Box.createVerticalStrut(12));
        panelDetalleColas.add(crearSeccionCola(tituloBloqueados, scrollBloqueados, tablaBloqueados));
        panelDetalleColas.add(Box.createVerticalStrut(12));
        panelDetalleColas.add(crearSeccionCola(tituloTerminados, scrollTerminados, tablaTerminados));
        panelDetalleColas.setVisible(false);
        
        // --- Configuración gráfica de la tabla de Buffers ---
        tablaBuffers.setFillsViewportHeight(true);
        tablaBuffers.getTableHeader().setReorderingAllowed(false);
        tablaBuffers.setRowHeight(24);
        scrollBuffers.setPreferredSize(new Dimension(Integer.MAX_VALUE, 120));
        scrollBuffers.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        
        panelDetalleBuffers.setOpaque(false);
        panelDetalleBuffers.setAlignmentX(LEFT_ALIGNMENT);
        JLabel tituloBuffers = texto("ESTADO DE LOS BUFFERS GLOBALES", 12, TINTA, true);
        panelDetalleBuffers.add(tituloBuffers, BorderLayout.NORTH);
        panelDetalleBuffers.add(scrollBuffers, BorderLayout.CENTER);
        JPanel panelModificarLatencia = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        panelModificarLatencia.setOpaque(false);
        panelModificarLatencia.setAlignmentX(LEFT_ALIGNMENT);
        panelModificarLatencia.add(texto("Modificar latencia en simulación:", 12, TINTA, true));
        comboBuffersLatencia.setPreferredSize(new Dimension(190, 28));
        panelModificarLatencia.add(comboBuffersLatencia);
        panelModificarLatencia.add(texto("Latencia (ticks):", 12, GRIS, false));
        spinnerNuevaLatencia.setPreferredSize(new Dimension(75, 28));
        ((javax.swing.text.DefaultFormatter) ((JSpinner.DefaultEditor) spinnerNuevaLatencia.getEditor())
                .getTextField().getFormatter()).setCommitsOnValidEdit(true);
        panelModificarLatencia.add(spinnerNuevaLatencia);
        btnActualizarLatencia.setPreferredSize(new Dimension(150, 28));
        btnActualizarLatencia.setEnabled(false);
        panelModificarLatencia.add(btnActualizarLatencia);

        comboBuffersLatencia.addActionListener(e -> {
            if (!actualizandoComboBuffers) {
                String sel = (String) comboBuffersLatencia.getSelectedItem();
                if (sel != null && view != null) {
                    for (int i = 0; i < view.buffers.size(); i++) {
                        var b = view.buffers.get(i);
                        if (sel.startsWith("Buffer " + b.getId() + " ")) {
                            spinnerNuevaLatencia.setValue(b.getLatency());
                            break;
                        }
                    }
                }
            }
        });

        tablaBuffers.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && view != null) {
                int row = tablaBuffers.getSelectedRow();
                if (row >= 0 && row < view.buffers.size()) {
                    var b = view.buffers.get(row);
                    for (int i = 0; i < comboBuffersLatencia.getItemCount(); i++) {
                        String item = comboBuffersLatencia.getItemAt(i);
                        if (item.startsWith("Buffer " + b.getId() + " ")) {
                            comboBuffersLatencia.setSelectedIndex(i);
                            spinnerNuevaLatencia.setValue(b.getLatency());
                            break;
                        }
                    }
                }
            }
        });

        btnActualizarLatencia.addActionListener(e -> {
            String sel = (String) comboBuffersLatencia.getSelectedItem();
            if (sel != null && sel.startsWith("Buffer ")) {
                try {
                    spinnerNuevaLatencia.commitEdit();
                    int endIdx = sel.indexOf(" (");
                    if (endIdx == -1) endIdx = sel.length();
                    int bufId = Integer.parseInt(sel.substring("Buffer ".length(), endIdx).trim());
                    int nuevaLat = (Integer) spinnerNuevaLatencia.getValue();
                    GuiRequest.control(controller, () -> {
                        controller.setBufferLatency(bufId, nuevaLat);
                    });
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        panelDetalleBuffers.add(panelModificarLatencia, BorderLayout.SOUTH);

        cabecera.add(panelDetalleColas);
        cabecera.add(Box.createVerticalStrut(16));
        cabecera.add(panelDetalleBuffers);
        contenedorPrincipal.add(cabecera, BorderLayout.NORTH);
        
        tarjetas.setOpaque(false);
        vacio.add(texto("Todavía no hay computadores", 20, TINTA, true));
        vacio.add(Box.createVerticalStrut(16));
        JButton crear = accion("Crear un computador →", true);
        crear.addActionListener(e -> irAComputadores.run());
        vacio.add(crear);
        
        JPanel resumen = new JPanel(new BorderLayout(0, 16));
        resumen.setOpaque(false);
        JPanel contenido = new JPanel(new BorderLayout());
        contenido.setOpaque(false);
        contenido.add(tarjetas, BorderLayout.NORTH);
        
        resumen.add(contenido, BorderLayout.CENTER);
        resumen.add(texto("CPU: estado actual · Utilización histórica y métricas: pendientes", 12, GRIS, false), BorderLayout.SOUTH);
        
        pestanas.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                tabAreaInsets = new Insets(10, 0, 0, 0);
                contentBorderInsets = new Insets(0, 0, 10, 0);
                tabInsets = new Insets(9, 16, 9, 16);
            }
        });
        pestanas.setBackground(PAPEL);
        pestanas.setForeground(TINTA);
        pestanas.addTab("Resumen", resumen);
        
        contenedorPrincipal.add(pestanas, BorderLayout.CENTER);
        pestanas.addChangeListener(e -> actualizarDetalleColasGlobal());
        
        areaLog.setEditable(false);
        areaLog.setBackground(Color.WHITE);
        areaLog.setForeground(TINTA);
        areaLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        areaLog.setMargin(new Insets(5, 5, 5, 5));
        
        DefaultCaret caret = (DefaultCaret) areaLog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        
        scrollLog.setPreferredSize(new Dimension(Integer.MAX_VALUE, 140));
        scrollLog.setBorder(BorderFactory.createLineBorder(BORDE));
        scrollLog.getViewport().setBackground(Color.WHITE);
        
        panelLog.setOpaque(false);
        JLabel tituloLog = texto("LOG DE EVENTOS DEL SISTEMA", 12, TINTA, true);
        panelLog.add(tituloLog, BorderLayout.NORTH);
        panelLog.add(scrollLog, BorderLayout.CENTER);
        
        contenedorPrincipal.add(panelLog, BorderLayout.SOUTH);

        JScrollPane scrollGlobal = new JScrollPane(contenedorPrincipal);
        scrollGlobal.setBorder(null);
        scrollGlobal.getVerticalScrollBar().setUnitIncrement(16);
        scrollGlobal.getViewport().setBackground(FONDO);
        scrollGlobal.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        
        add(scrollGlobal, BorderLayout.CENTER);
        
        this.controller.addLogListener(mensaje -> {
            areaLog.append(mensaje + "\n");
        });

        this.controller.addGUIListener(() -> {
            if (isShowing()) actualizarVista();
        });
        this.panelMetricas = new PanelMetricas(controller);
        pestanas.addTab("Métricas", panelMetricas);
    }

    private JPanel crearSeccionCola(JLabel titulo, JScrollPane scroll, JTable tabla) {
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.setRowHeight(24);
        scroll.setPreferredSize(new Dimension(Integer.MAX_VALUE, 110));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.add(titulo, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private static String formatoTipo(ProcessType type) {
        if (type == null) return "-";
        return switch (type) {
            case CPU_BOUND -> "CPU";
            case IO_BOUND -> "E/S";
            case PRODUCER -> "Productor";
            case CONSUMER -> "Consumidor";
        };
    }

    private static String formatoMotivoBloqueo(BlockReason reason) {
        if (reason == null) return "-";
        return switch (reason) {
            case WAITING_MUTEX -> "Esperando Mutex";
            case WAITING_EMPTY_BUFFER -> "Buffer Vacío";
            case WAITING_FULL_BUFFER -> "Buffer Lleno";
            case NETWORK_LATENCY -> "Latencia Red";
            case WAITING_IO -> "Espera E/S";
            case NONE -> "Ninguno";
        };
    }
    
    private JPanel crearPanelPersistencia() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JButton btnGuardar = accion("Guardar configuración", false);
        JButton btnCargar = accion("Cargar configuración", false);

        btnGuardar.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
            
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File archivo = chooser.getSelectedFile();
                if (!archivo.getName().toLowerCase().endsWith(".json")) {
                    archivo = new File(archivo.getAbsolutePath() + ".json");
                }

                File destino = archivo;
                btnGuardar.setEnabled(false);
                GuiCommands.submit(() -> guardarEstado(destino), ok -> { btnGuardar.setEnabled(true); if (!ok) JOptionPane.showMessageDialog(this, "No se pudo guardar la configuración. Consulta el log.", "Error al guardar", JOptionPane.ERROR_MESSAGE); },
                        error -> { btnGuardar.setEnabled(true); controller.logEvent(error.getMessage()); });
            }
        });
        btnCargar.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON (*.json)", "json"));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File archivo = chooser.getSelectedFile();

                btnCargar.setEnabled(false);
                GuiCommands.submit(() -> cargarEstado(archivo), ok -> {
                    btnCargar.setEnabled(true);
                    if (!ok) {
                        JOptionPane.showMessageDialog(this,
                                "No se pudo cargar el archivo. Consulta el log para conocer el error.",
                                "Error de carga", JOptionPane.ERROR_MESSAGE);
                    }
                }, error -> {
                    btnCargar.setEnabled(true);
                    controller.logEvent(error.getMessage());
                    JOptionPane.showMessageDialog(this, "No se pudo cargar el archivo: " + error.getMessage(),
                            "Error de carga", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
        panel.add(btnGuardar);
        panel.add(btnCargar);
        JButton btnLog = accion("Exportar log", false);
        btnLog.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("avilaos-ciclo-" + controller.getCurrentCycle() + ".log"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File destino = chooser.getSelectedFile();
                String texto = areaLog.getText();
                GuiCommands.submit(() -> {
                    java.nio.file.Files.writeString(destino.toPath(), texto, java.nio.charset.StandardCharsets.UTF_8);
                    return destino.getName();
                }, nombre -> controller.logEvent("Log exportado: " + nombre),
                        error -> controller.logEvent("Error al exportar log: " + error.getMessage()));
            }
        });
        panel.add(btnLog);
        JButton btnReiniciar = accion("Reiniciar todo", false);
        btnReiniciar.addActionListener(e -> {
            btnReiniciar.setEnabled(false);
            areaLog.setText("");
            GuiCommands.submit(() -> {
                controller.reset();
                return true;
            }, ok -> {
                btnReiniciar.setEnabled(true);
                actualizarVista();
            }, error -> {
                btnReiniciar.setEnabled(true);
                controller.logEvent("Error al reiniciar: " + error.getMessage());
            });
        });
        panel.add(btnReiniciar);
        return panel;
    }

    /** Delegación conservada para las pruebas del monitor anterior. */
    boolean guardarEstado(File archivo) {
        return new ConfiguracionSimulacion(controller).guardarEstado(archivo);
    }

    boolean cargarEstado(File archivo) {
        return new ConfiguracionSimulacion(controller).cargarEstado(archivo);
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel(LayoutManager layout) { super(layout); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(visibleRect.height / 2, 1); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    void limpiarLog() { areaLog.setText(""); }

    public void agregarLog(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            areaLog.append(mensaje + "\n");
        });
    }

    private JPanel controlesReloj() {
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controles.setOpaque(false);
        controles.setAlignmentX(LEFT_ALIGNMENT);
        intervalo.setSelectedItem(controller.getCycleDurationMs());
        intervalo.setPreferredSize(new Dimension(90, 36));
        iniciar.addActionListener(e -> { GuiRequest.control(controller, controller::startSimulation); });
        pausar.addActionListener(e -> { GuiRequest.control(controller, controller::pauseSimulation); });
        paso.addActionListener(e -> { GuiRequest.control(controller, controller::stepSimulation); });
        aplicar.addActionListener(e -> {
            Integer ms = (Integer) intervalo.getSelectedItem();
            if (ms != null) { GuiRequest.control(controller, () -> controller.setCycleDurationMs(ms)); }
        });
        
        intervalo.addActionListener(e -> actualizarControlesReloj());
        controles.add(iniciar); controles.add(pausar); controles.add(paso);
        controles.add(texto("Intervalo (ms)", 12, GRIS, false));
        controles.add(intervalo); controles.add(aplicar);
        controles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return controles;
    }

    private void actualizarControlesReloj() {
        boolean activo = controller.isRunning() && !controller.isPaused();
        iniciar.setText(controller.isRunning() && controller.isPaused() ? "Reanudar" : "Iniciar");
        iniciar.setEnabled(!activo);
        pausar.setEnabled(activo);
        paso.setEnabled(!activo);
        Integer seleccionado = (Integer) intervalo.getSelectedItem();
        int ms = controller.getCycleDurationMs();
        boolean pendiente = seleccionado != null && seleccionado != ms;
        aplicar.setEnabled(pendiente);
        intervaloAplicado.setText("Aplicado: " + ms + " ms/tick" + (pendiente ? " · Cambio pendiente" : ""));
    }

    public void actualizarVista() {
        long previousSession=view.session;
        view=controller.snapshot();
        int ciclo = view.cycle;
        String marcha = !controller.isRunning() ? "Detenida" : controller.isPaused() ? "Pausada" : "En ejecución";
        actualizarControlesReloj();
        {
            var computadores = view.computers;
            int cantidad = computadores.size();
            boolean cambio = cantidad != ids.length || previousSession != view.session;
            if (!cambio) {
                for (int i = 0; i < cantidad; i++) {
                    if (ids[i] != computadores.get(i).getId()) { cambio = true; break; }
                }
            }
            if (cambio || cantidad == 0 && tarjetas.getComponentCount() == 0) reconstruirPestanas();
            
            for (int i = 0; i < cantidad; i++) {
                Node c = computadores.get(i);
                resumenes[i].actualizar(c);
                detalles[i].actualizar(c);
                pestanas.setTitleAt(i + 1, c.getName());

            }
            
            reloj.setText("Reloj global · ciclo " + ciclo);
            estado.setText(marcha + "  ·  " + cantidad + " computadores");
            actualizarDetalleColasGlobal();
            actualizarDetalleBuffersGlobal();

            if (panelMetricas != null) {
                panelMetricas.actualizarMetricas();
            }
        }
    }

    private void actualizarDetalleBuffersGlobal() {
        modeloBuffers.setRowCount(0);
        var buffers = view.buffers;
        for (int i = 0; i < buffers.size(); i++) {
            var b = buffers.get(i);
            String nombre = "Buffer " + b.getId();
            int elementos = b.getDatos().size();
            int capacidad = b.getStorage();
            int hostId = b.getIdComp();
            
            String hostNombre = "Host " + hostId;
            if (view.computers != null) {
                for (int cIdx = 0; cIdx < view.computers.size(); cIdx++) {
                    var comp = view.computers.get(cIdx);
                    if (comp.getId() == hostId) {
                        hostNombre = comp.getName() + " (" + hostId + ")";
                        break;
                    }
                }
            }
            
            int m = b.getMutex().availablePermits();
            int l = b.getLlenos().availablePermits();
            int v = b.getVacios().availablePermits();
            String bloqueados = b.getBlockedProcessesAsString();
            int latencia = b.getLatency();
            modeloBuffers.addRow(new Object[]{nombre, hostNombre, latencia + " ticks", elementos, capacidad, m, l, v, bloqueados});
        }

        // Actualizar combo de buffers para modificar latencia en simulaciÃ³n
        actualizandoComboBuffers = true;
        String seleccionPrevia = (String) comboBuffersLatencia.getSelectedItem();
        comboBuffersLatencia.removeAllItems();
        int indiceSeleccion = -1;
        for (int i = 0; i < buffers.size(); i++) {
            var b = buffers.get(i);
            int hostId = b.getIdComp();
            String hostNombre = "Host " + hostId;
            if (view.computers != null) {
                for (int cIdx = 0; cIdx < view.computers.size(); cIdx++) {
                    var comp = view.computers.get(cIdx);
                    if (comp.getId() == hostId) {
                        hostNombre = comp.getName() + " (" + hostId + ")";
                        break;
                    }
                }
            }
            String item = "Buffer " + b.getId() + " (" + hostNombre + ")";
            comboBuffersLatencia.addItem(item);
            if (seleccionPrevia != null && seleccionPrevia.startsWith("Buffer " + b.getId() + " ")) {
                indiceSeleccion = i;
            }
        }
        if (indiceSeleccion >= 0) {
            comboBuffersLatencia.setSelectedIndex(indiceSeleccion);
        } else if (buffers.size() > 0) {
            comboBuffersLatencia.setSelectedIndex(0);
        }
        btnActualizarLatencia.setEnabled(buffers.size() > 0);
        actualizandoComboBuffers = false;

        String actual = (String) comboBuffersLatencia.getSelectedItem();
        if (actual != null) {
            for (int i = 0; i < buffers.size(); i++) {
                var b = buffers.get(i);
                if (actual.startsWith("Buffer " + b.getId() + " ")) {
                    if (!spinnerNuevaLatencia.hasFocus()) {
                        spinnerNuevaLatencia.setValue(b.getLatency());
                    }
                    break;
                }
            }
        }
    }
    
    private void actualizarDetalleColasGlobal() {
        int seleccion = pestanas.getSelectedIndex();
        var computadores = view.computers;
        int cantidad = computadores.size();
        
        if (cantidad == 0 || seleccion == 0 || pestanas.getSelectedComponent() == panelMetricas) {
            panelDetalleColas.setVisible(false);
            if (cantidad > 0) {
                poblarTablasColas(0);
            }
            pestanas.revalidate();
            pestanas.repaint();
            Container parent = pestanas.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
            return;
        }
        
        panelDetalleColas.setVisible(true);
        int compIndex = seleccion - 1;
        if (compIndex < cantidad) {
            poblarTablasColas(compIndex);
        }
        pestanas.revalidate();
        pestanas.repaint();
        Container parent = pestanas.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private void poblarTablasColas(int compIndex) {
        var computadores = view.computers;
        if (compIndex >= computadores.size()) return;
        Node c = computadores.get(compIndex);
        var kernel = c.getKernel();
        
        tituloColas.setText("DETALLE DE COLAS DEL KERNEL " + c.getName() + " (ID " + c.getId() + ")");
        
        // 1. Nuevos
        modeloNuevos.setRowCount(0);
        var nuevos = kernel.getNewQueue().getProcesses();
        for (int i = 0; i < nuevos.size(); i++) {
            var p = nuevos.get(i);
            modeloNuevos.addRow(new Object[]{
                p.getId(),
                p.getName() != null ? p.getName() : "P" + p.getId(),
                formatoTipo(p.getType()),
                p.getPriority(),
                p.getAssignedMemory(),
                p.getTotalInstructions(),
                p.getDeadline()
            });
        }
        tituloNuevos.setText("COLA DE NUEVOS (NEW) - " + nuevos.size() + " procesos");
        
        // 2. Listos
        modeloListos.setRowCount(0);
        var listos = kernel.getReadyQueue().getProcesses();
        for (int i = 0; i < listos.size(); i++) {
            var p = listos.get(i);
            modeloListos.addRow(new Object[]{
                p.getId(),
                p.getName() != null ? p.getName() : "P" + p.getId(),
                formatoTipo(p.getType()),
                p.getPriority(),
                p.getRemainingTime(),
                p.getRemainingDeadline(),
                p.getProgramCounter(),
                p.getProgramCounter() == 0 ? "—" : p.getMemoryAddressRegister(),
                p.getAssignedMemory() 
            });
        }
        tituloListos.setText("COLA DE LISTOS (READY) - " + listos.size() + " procesos");

        // 3. Bloqueados
        modeloBloqueados.setRowCount(0);
        var bloqueados = kernel.getBlockedQueue().getProcesses();
        for (int i = 0; i < bloqueados.size(); i++) {
            var p = bloqueados.get(i);
            String buf = p.getTargetBufferId() != null 
                ? (p.getTargetBufferId().startsWith("Buffer") ? p.getTargetBufferId() : "Buffer " + p.getTargetBufferId()) 
                : "-";
            modeloBloqueados.addRow(new Object[]{
                p.getId(),
                p.getName() != null ? p.getName() : "P" + p.getId(),
                formatoTipo(p.getType()),
                formatoMotivoBloqueo(p.getBlockReason()),
                buf,
                p.getPriority(),
                p.getRemainingTime(),
                p.getRemainingDeadline(),
                p.getAssignedMemory()
            });
        }
        tituloBloqueados.setText("COLA DE BLOQUEADOS (BLOCKED) - " + bloqueados.size() + " procesos");

        // 4. Terminados
        modeloTerminados.setRowCount(0);
        var terminados = kernel.getEndedQueue().getProcesses();
        for (int i = 0; i < terminados.size(); i++) {
            var p = terminados.get(i);
            modeloTerminados.addRow(new Object[]{
                p.getId(),
                p.getName() != null ? p.getName() : "P" + p.getId(),
                formatoTipo(p.getType()),
                p.getPriority(),
                p.getTotalInstructions(),
                p.getAssignedMemory()
            });
        }
        tituloTerminados.setText("COLA DE TERMINADOS (TERMINATED) - " + terminados.size() + " procesos");
    }

    private void reconstruirPestanas() {
        int seleccion = pestanas.getSelectedIndex();
        boolean viendoMetricas = pestanas.getSelectedComponent() == panelMetricas;
        int seleccionado = seleccion > 0 && seleccion <= ids.length ? ids[seleccion - 1] : -1;
        while (pestanas.getTabCount() > 1) pestanas.removeTabAt(1);
        tarjetas.removeAll();
        
        var computadores = view.computers;
        int cantidad = computadores.size();
        ids = new int[cantidad];
        resumenes = new VistaComputador[cantidad];
        detalles = new VistaComputador[cantidad];
        
        if (cantidad == 0) tarjetas.add(vacio);
        for (int i = 0; i < cantidad; i++) {
            Node c = computadores.get(i);
            ids[i] = c.getId();
            resumenes[i] = new VistaComputador(c, false, view.session);
            detalles[i] = new VistaComputador(c, true, view.session);
            int indice = i + 1;
            JButton abrir = accion("Ver detalle", false);
            abrir.addActionListener(e -> pestanas.setSelectedIndex(indice));
            resumenes[i].add(Box.createVerticalStrut(14));
            resumenes[i].add(abrir);
            tarjetas.add(resumenes[i]);
            
            detalles[i].setPreferredSize(new Dimension(540, 240));
            JPanel wrapDetalle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wrapDetalle.setOpaque(false);
            wrapDetalle.add(detalles[i]);
            
            pestanas.addTab(c.getName(), wrapDetalle);
            
            if (ids[i] == seleccionado) pestanas.setSelectedIndex(indice);
        }
        if (panelMetricas != null) {
            pestanas.addTab("Métricas", panelMetricas);
            if (viendoMetricas) pestanas.setSelectedComponent(panelMetricas);
        }
        tarjetas.revalidate(); 
        tarjetas.repaint();
    }

    private static final class VistaComputador extends JPanel {
        private final int computerId;
        private final long session;
        private final JLabel nombre = texto("", 20, TINTA, true);
        private final JComboBox<SchedulingPolicyType> comboPolitica = new JComboBox<>(SchedulingPolicyType.values());
        private final JLabel labelQuantum = texto("Quantum: ", 13, GRIS, false);
        private final JSpinner spinnerQuantum = new JSpinner(new SpinnerNumberModel(3, 1, 99, 1));
        private final JLabel cpu = texto("", 14, TINTA, true);
        private final JLabel proceso = texto("", 13, GRIS, false);
        private final JLabel registros = texto("", 13, GRIS, false);
        private final JLabel colas = texto("", 13, TINTA, false);
        private final JLabel memoria = texto("", 13, GRIS, false);
        private final boolean detalle;
        private boolean actualizandoVista = false;

        VistaComputador(Node c, boolean detalle, long session) {
            this.computerId = c.getId();
            this.session=session;
            this.detalle = detalle;
            
            // Usamos BorderLayout para empaquetar el contenido arriba sin deformaciones
            setLayout(new BorderLayout());
            setBackground(PAPEL);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDE),
                    BorderFactory.createEmptyBorder(22, 22, 22, 22)));

            // Panel interno donde colocamos verticalmente todos los elementos
            JPanel contenido = new JPanel();
            contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
            contenido.setOpaque(false);
            contenido.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel panelPolitica = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            panelPolitica.setOpaque(false);
            panelPolitica.setAlignmentX(Component.LEFT_ALIGNMENT);
            panelPolitica.add(texto("Política: ", 13, GRIS, false));
            
            comboPolitica.setSelectedItem(c.getPolitica());
            comboPolitica.setPreferredSize(new Dimension(160, 26));
            comboPolitica.addActionListener(e -> {
                SchedulingPolicyType seleccion = (SchedulingPolicyType) comboPolitica.getSelectedItem();
                if (!actualizandoVista && seleccion != null) {
                    var ctrl=SimulationController.getInstance();
                    GuiRequest.control(ctrl, () -> { ctrl.requireSession(session); ctrl.setComputerPolicy(computerId,seleccion); });
                    boolean esRR = (seleccion == SchedulingPolicyType.ROUND_ROBIN);
                    labelQuantum.setVisible(esRR);
                    spinnerQuantum.setVisible(esRR);
                    panelPolitica.revalidate();
                    panelPolitica.repaint();
                }
            });
            panelPolitica.add(comboPolitica);

            spinnerQuantum.setValue(c.getQuantum());
            spinnerQuantum.setPreferredSize(new Dimension(55, 26));
            ((javax.swing.text.DefaultFormatter) ((JSpinner.DefaultEditor) spinnerQuantum.getEditor())
                    .getTextField().getFormatter()).setCommitsOnValidEdit(true);
            boolean esRRInicial = (c.getPolitica() == SchedulingPolicyType.ROUND_ROBIN);
            labelQuantum.setVisible(esRRInicial);
            spinnerQuantum.setVisible(esRRInicial);

            spinnerQuantum.addChangeListener(e -> {
                if (!actualizandoVista) {
                    int val = (Integer) spinnerQuantum.getValue();
                    var ctrl=SimulationController.getInstance();
                    GuiRequest.control(ctrl, () -> { ctrl.requireSession(session); ctrl.setComputerQuantum(computerId,val); });
                }
            });

            panelPolitica.add(Box.createHorizontalStrut(10));
            panelPolitica.add(labelQuantum);
            panelPolitica.add(spinnerQuantum);

            nombre.setAlignmentX(Component.LEFT_ALIGNMENT);
            cpu.setAlignmentX(Component.LEFT_ALIGNMENT);
            proceso.setAlignmentX(Component.LEFT_ALIGNMENT);
            registros.setAlignmentX(Component.LEFT_ALIGNMENT);
            memoria.setAlignmentX(Component.LEFT_ALIGNMENT);
            colas.setAlignmentX(Component.LEFT_ALIGNMENT);

            contenido.add(nombre); 
            contenido.add(Box.createVerticalStrut(8)); 
            contenido.add(panelPolitica);
            contenido.add(Box.createVerticalStrut(16)); 
            contenido.add(cpu);
            contenido.add(Box.createVerticalStrut(6)); 
            contenido.add(proceso);
            if (false && detalle) { 
                contenido.add(Box.createVerticalStrut(6)); 
                contenido.add(registros); 
            }
            contenido.add(Box.createVerticalStrut(16));
            contenido.add(memoria);
            contenido.add(Box.createVerticalStrut(16)); 
            contenido.add(colas);

            // Al colocarlo en NORTH, se mantendrá empaquetado arriba sin importar el alto del frame
            add(contenido, BorderLayout.NORTH);
        }
    

        // Obliga al panel a mantener su altura compacta según el contenido y no expandirse en la ventana
        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }
    

        void actualizar(Node c) {
        actualizandoVista = true;
        try {
            nombre.setText(c.getName() + " · ID " + c.getId());
            
            if (comboPolitica.getSelectedItem() != c.getPolitica()) {
                comboPolitica.setSelectedItem(c.getPolitica());
            }

            boolean esRR = (c.getPolitica() == SchedulingPolicyType.ROUND_ROBIN);
            labelQuantum.setVisible(esRR);
            spinnerQuantum.setVisible(esRR);
            if ((Integer) spinnerQuantum.getValue() != c.getQuantum()) {
                spinnerQuantum.setValue(c.getQuantum());
            }

            var p = c.getCpu().getPCB();
            cpu.setText(p == null ? "CPU libre · KERNEL" : "CPU ocupada · " + c.getCpu().getMode());
            
            if (p == null) {
                proceso.setText("Sin proceso");
            } else {
                String nombreSeguro = p.name().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                String tipo = switch (p.type()) {
                    case CPU_BOUND -> "CPU";
                    case IO_BOUND -> "E/S";
                    case PRODUCER -> "Productor";
                    case CONSUMER -> "Consumidor";
                };
                proceso.setText("<html>" + nombreSeguro + " · ID " + p.id() + " · " + tipo
                        + "<br>PC: " + c.getCpu().getPC() + " · MAR: " + (p.getProgramCounter() == 0 ? "—" : p.getMemoryAddressRegister()) + " · Prioridad: " + p.getPriority()
                        + "<br>Tiempo restante: " + p.remainingTime() + " · Deadline restante: "
                        + p.getRemainingDeadline() + "</html>");
            }
            
            if (false && detalle) registros.setText(p == null 
                    ? "PC: —  ·  MAR: —  ·  Prioridad: —  ·  Deadline: —"
                    : "PC: " + c.getCpu().getPC() 
                    + "  ·  MAR: " + (p.getProgramCounter() == 0 ? "—" : p.getMemoryAddressRegister())
                    + "  ·  Prioridad: " + p.getPriority()
                    + "  ·  Deadline restante: " + p.getRemainingDeadline());
            
            int total = c.getMemoryManager().getTotalMemory();
            int usada = c.getMemoryManager().getUsedMemory();
            memoria.setText("RAM " + usada + " / " + total);
            
            var kernel = c.getKernel();
            colas.setText("Nuevos: " + kernel.getNewQueue().size() + "  ·  Listos: " + kernel.getReadyQueue().size()
                    + "  ·  Bloqueados: " + kernel.getBlockedQueue().size() + "  ·  Terminados: " + kernel.getEndedQueue().size());
        } finally {
            actualizandoVista = false;
        }
    }}
}
