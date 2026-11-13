package com.avilaos.gui;

import com.avilaos.concurrency.GuiCommands;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.simulation.SimulationController;
import com.avilaos.simulation.SimulationSnapshot;
import com.avilaos.simulation.SimulationSnapshot.Node;
import com.avilaos.simulation.SimulationSnapshot.ProcessView;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import static com.avilaos.gui.PanelPrincipal.*;

/** Vista de simulación y sus controles. */
public final class VistaSimulacionAlternativa extends JPanel {
    private final SimulationController controller;
    private final ConfiguracionSimulacion configuracion;
    private final JLabel reloj = texto("Ciclo 0 · Detenida", 20, TINTA, true);
    private final RelojAnimado relojAnimado = new RelojAnimado();
    private final JButton iniciar = accion("Iniciar", true);
    private final JButton pausar = accion("Pausar", false);
    private final JButton paso = accion("+1 ciclo", false);
    private final JComboBox<Integer> intervalo = new JComboBox<>(new Integer[]{100, 250, 500, 1000});
    private final JTabbedPane vistas = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JPanel resumenEquipos = new JPanel();
// [P2-LEGACY]     private final DefaultTableModel modeloBuffers = tabla("Buffer", "Computador", "Elementos", "Capacidad", "Latencia", "Mutex", "Llenos", "Vacíos");
// [P2-LEGACY]     private final JTable buffers = new JTable(modeloBuffers);
// [P2-LEGACY]     private final JScrollPane scrollBuffers = new JScrollPane(buffers);
// [P2-LEGACY]     private final JTextArea esperaBuffers = new JTextArea(3, 30);
// [P2-LEGACY]     private final JSpinner latencia = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
// [P2-LEGACY]     private final JButton aplicarLatencia = accion("Aplicar latencia", false);
    private final JTextArea eventos = new JTextArea(5, 40);
    private final JPanel resumen = columna();
    private int[] ids = new int[0];
    private DetalleComputador[] detalles = new DetalleComputador[0];
    private SimulationSnapshot view;
    private long session;
    private boolean actualizando;

    public VistaSimulacionAlternativa(SimulationController controller) {
        super(new BorderLayout(0, 12));
        this.controller = controller;
        this.configuracion = new ConfiguracionSimulacion(controller);
        this.view = controller.snapshot();
        this.session = view.session;
        intervalo.setSelectedItem(view.interval);
        setName("simulacionAlternativa");
        setBackground(FONDO);
        setBorder(BorderFactory.createEmptyBorder(22, 28, 20, 28));
        add(cabecera(), BorderLayout.NORTH);
        construirResumen();
        vistas.addTab("Resumen", desplazable(resumen));
        add(vistas, BorderLayout.CENTER);
        add(panelEventos(), BorderLayout.SOUTH);
        controller.addGUIListener(this::actualizar);
        controller.addLogListener(texto -> {
            long sesionActual = controller.session();
            if (session != sesionActual) { eventos.setText(""); session = sesionActual; }
            eventos.append(texto + "\n");
        });
        actualizar();
    }

    private JPanel cabecera() {
        JPanel principal = columna();
        principal.add(texto("SIMULACIÓN", 11, ACENTO, true));
        principal.add(Box.createVerticalStrut(6));
        principal.add(reloj);
        principal.add(Box.createVerticalStrut(10));
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controles.setOpaque(false);
        controles.setAlignmentX(LEFT_ALIGNMENT);
        iniciar.addActionListener(e -> { if (controller.snapshot().computers.size() < 2) { controller.logEvent("Se requieren mínimo 2 computadores creados para iniciar la simulación."); return; } GuiRequest.control(controller, controller::startSimulation); });
        pausar.addActionListener(e -> GuiRequest.control(controller, controller::pauseSimulation));
        paso.addActionListener(e -> { if (controller.snapshot().computers.size() < 2) { controller.logEvent("Se requieren mínimo 2 computadores creados para iniciar la simulación."); return; } GuiRequest.control(controller, controller::stepSimulation); });
        controles.add(iniciar); controles.add(pausar); controles.add(paso);
        controles.add(texto("Intervalo (ms)", 12, GRIS, false));
        controles.add(intervalo);
        JButton aplicar = accion("Aplicar", false);
        aplicar.addActionListener(e -> { Integer ms = (Integer) intervalo.getSelectedItem(); if (ms != null) GuiRequest.control(controller, () -> controller.setCycleDurationMs(ms)); });
        controles.add(aplicar);
        principal.add(controles);
        principal.add(Box.createVerticalStrut(8));
        JPanel archivos = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        archivos.setOpaque(false); archivos.setAlignmentX(LEFT_ALIGNMENT);
        JButton guardar = accion("Guardar configuración", false);
        JButton cargar = accion("Cargar configuración", false);
        JButton exportar = accion("Exportar log", false);
        JButton reiniciar = accion("Reiniciar todo", false);
        guardar.addActionListener(e -> elegirArchivo(true, guardar));
        cargar.addActionListener(e -> elegirArchivo(false, cargar));
        exportar.addActionListener(e -> exportarLog());
        reiniciar.addActionListener(e -> {
            reiniciar.setEnabled(false);
            eventos.setText("");
            GuiCommands.submit(() -> { controller.reset(); return true; }, ok -> {
                reiniciar.setEnabled(true);
                actualizar();
            }, error -> {
                reiniciar.setEnabled(true);
                controller.logEvent("Error al reiniciar: " + error.getMessage());
            });
        });
        archivos.add(guardar); archivos.add(cargar); archivos.add(exportar); archivos.add(reiniciar);
        principal.add(archivos);
        JPanel cabecera = new JPanel(new BorderLayout(12, 0));
        cabecera.setOpaque(false);
        cabecera.add(principal, BorderLayout.CENTER);
        cabecera.add(relojAnimado, BorderLayout.EAST);
        return cabecera;
    }

    private void elegirArchivo(boolean guardar, JButton boton) {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        int respuesta = guardar ? selector.showSaveDialog(this) : selector.showOpenDialog(this);
        if (respuesta != JFileChooser.APPROVE_OPTION) return;
        File elegido = selector.getSelectedFile();
        if (guardar && !elegido.getName().toLowerCase().endsWith(".json")) elegido = new File(elegido.getAbsolutePath() + ".json");
        File archivo = elegido;
        boton.setEnabled(false);
        GuiCommands.submit(() -> guardar ? configuracion.guardarEstado(archivo) : configuracion.cargarEstado(archivo), ok -> {
            boton.setEnabled(true);
            if (!ok) JOptionPane.showMessageDialog(this, "No se pudo completar la operación. Consulta el log.", "Configuración", JOptionPane.ERROR_MESSAGE);
        }, error -> { boton.setEnabled(true); JOptionPane.showMessageDialog(this, error.getMessage(), "Configuración", JOptionPane.ERROR_MESSAGE); });
    }

    private void exportarLog() {
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File("avilaos-ciclo-" + view.cycle + ".log"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File archivo = selector.getSelectedFile();
        String contenido = eventos.getText();
        GuiCommands.submit(() -> { Files.writeString(archivo.toPath(), contenido, StandardCharsets.UTF_8); return true; },
                ok -> controller.logEvent("Log exportado: " + archivo.getName()), error -> controller.logEvent("Error al exportar log: " + error.getMessage()));
    }

    private void construirResumen() {
        resumen.add(encabezado("Computadores", "Estado de CPU y memoria por equipo"));
        resumenEquipos.setLayout(new BoxLayout(resumenEquipos, BoxLayout.Y_AXIS));
        resumenEquipos.setOpaque(false); resumenEquipos.setAlignmentX(LEFT_ALIGNMENT);
        resumen.add(resumenEquipos);
// [P2-LEGACY]         resumen.add(Box.createVerticalStrut(16));
// [P2-LEGACY]         resumen.add(encabezado("Buffers globales", "Ocupación, semáforos y procesos en espera"));
// [P2-LEGACY]         buffers.setRowHeight(30); buffers.setBackground(PAPEL); buffers.setForeground(TINTA);
// [P2-LEGACY]         buffers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
// [P2-LEGACY]         scrollBuffers.setColumnHeaderView(buffers.getTableHeader());
// [P2-LEGACY]         scrollBuffers.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
// [P2-LEGACY]         desplazarVistaDesdeTabla(scrollBuffers);
// [P2-LEGACY]         scrollBuffers.setPreferredSize(new Dimension(800, 90)); scrollBuffers.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
// [P2-LEGACY]         scrollBuffers.setAlignmentX(LEFT_ALIGNMENT); resumen.add(scrollBuffers);
// [P2-LEGACY]         esperaBuffers.setEditable(false); esperaBuffers.setLineWrap(true); esperaBuffers.setWrapStyleWord(true);
// [P2-LEGACY]         esperaBuffers.setBackground(PAPEL); esperaBuffers.setForeground(TINTA);
// [P2-LEGACY]         esperaBuffers.setText("Selecciona un buffer para ver las colas de sus semáforos.");
// [P2-LEGACY]         JScrollPane espera = new JScrollPane(esperaBuffers);
// [P2-LEGACY]         espera.setPreferredSize(new Dimension(800, 70)); espera.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
// [P2-LEGACY]         espera.setAlignmentX(LEFT_ALIGNMENT); resumen.add(Box.createVerticalStrut(8)); resumen.add(espera);
// [P2-LEGACY]         JPanel ajuste = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); ajuste.setOpaque(false); ajuste.setAlignmentX(LEFT_ALIGNMENT);
// [P2-LEGACY]         ajuste.add(texto("Latencia del buffer seleccionado (ticks)", 12, GRIS, false)); ajuste.add(latencia); ajuste.add(aplicarLatencia);
// [P2-LEGACY]         resumen.add(Box.createVerticalStrut(8)); resumen.add(ajuste);
// [P2-LEGACY]         buffers.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !actualizando) mostrarBuffer(); });
// [P2-LEGACY]         aplicarLatencia.addActionListener(e -> {
// [P2-LEGACY]             int fila = buffers.getSelectedRow();
// [P2-LEGACY]             if (fila < 0 || fila >= view.buffers.size()) return;
// [P2-LEGACY]             try { latencia.setValue(EditorProceso.numeroVisible(latencia)); } catch (IllegalArgumentException ex) { esperaBuffers.setText("Escribe una latencia válida."); return; }
// [P2-LEGACY]             int id = view.buffers.get(fila).id(), valor = (Integer) latencia.getValue();
// [P2-LEGACY]             GuiRequest.control(controller, () -> { /* [P2-LEGACY] controller.setBufferLatency(id, valor) */ });
// [P2-LEGACY]         });
    }

    private JPanel panelEventos() {
        JPanel panel = new JPanel(new BorderLayout(0, 4)); panel.setOpaque(false);
        panel.add(texto("Eventos", 14, TINTA, true), BorderLayout.NORTH);
        eventos.setEditable(false); eventos.setLineWrap(true); eventos.setWrapStyleWord(true); eventos.setBackground(PAPEL);
        JScrollPane scroll = new JScrollPane(eventos); scroll.setPreferredSize(new Dimension(400, 105));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void actualizar() {
        SimulationSnapshot anterior = view;
        view = controller.snapshot();
        if (session != view.session) { eventos.setText(""); session = view.session; }
        reloj.setText("Ciclo " + view.cycle + " · " + (!view.running ? "Detenida" : view.paused ? "Pausada" : "En ejecución"));
        boolean activo = view.running && !view.paused;
        relojAnimado.setActivo(activo);
        iniciar.setText(view.running && view.paused ? "Reanudar" : "Iniciar");
        boolean minComputadores = view.computers.size() >= 2;
        iniciar.setEnabled(!activo && minComputadores); pausar.setEnabled(activo); paso.setEnabled(!activo && minComputadores);
        if (anterior.interval != view.interval || anterior.session != view.session) intervalo.setSelectedItem(view.interval);
        boolean cambio = ids.length != view.computers.size() || anterior.session != view.session;
        if (!cambio) for (int i=0; i<ids.length; i++) if (ids[i] != view.computers.get(i).id()) { cambio=true; break; }
        if (cambio) reconstruir();
        resumenEquipos.removeAll();
        for (int i=0; i<view.computers.size(); i++) {
            Node c = view.computers.get(i);
            var p = c.cpu().PCB();
            String cpu = p == null ? "CPU libre" : p.name() + " · PC " + c.cpu().PC()
                    + " · MAR " + (p.programCounter() == 0 ? "—" : p.memoryAddressRegister()) + " · " + c.cpu().mode();
            JPanel card = tarjeta(); card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
            card.add(texto(c.name() + " · " + c.politica(), 15, TINTA, true));
            card.add(texto(cpu + "  |  RAM " + c.memoryManager().usedMemory() + "/" + c.memoryManager().totalMemory(), 12, GRIS, false));
            resumenEquipos.add(card); resumenEquipos.add(Box.createVerticalStrut(8));
            detalles[i].actualizar(c);
        }
// [P2-LEGACY]         if (view.computers.size() < 2) resumenEquipos.add(texto("Crea al menos 2 computadores para comenzar la simulación.", 13, GRIS, false));
        if (view.computers.size() < 1) resumenEquipos.add(texto("Crea el computador para comenzar la simulación.", 13, GRIS, false)); // [P2]
        resumenEquipos.revalidate(); resumenEquipos.repaint();
// [P2-LEGACY]         actualizarBuffers();
    }

    private void reconstruir() {
        int seleccionado = vistas.getSelectedIndex();
        while (vistas.getTabCount() > 1) vistas.removeTabAt(1);
        ids = new int[view.computers.size()]; detalles = new DetalleComputador[ids.length];
        for (int i=0; i<ids.length; i++) {
            Node c = view.computers.get(i); ids[i] = c.id();
            detalles[i] = new DetalleComputador(c.id()); vistas.addTab(c.name(), desplazable(detalles[i]));
        }
        vistas.setSelectedIndex(Math.min(seleccionado, vistas.getTabCount()-1));
    }

// [P2-LEGACY]     private void actualizarBuffers() {
// [P2-LEGACY]         int seleccionado = buffers.getSelectedRow();
// [P2-LEGACY]         int id = seleccionado >= 0 && seleccionado < modeloBuffers.getRowCount() ? (Integer) modeloBuffers.getValueAt(seleccionado, 0) : -1;
// [P2-LEGACY]         actualizando = true;
// [P2-LEGACY]         modeloBuffers.setRowCount(0);
// [P2-LEGACY]         int nuevoIndice = -1;
// [P2-LEGACY]         for (int i=0; i<view.buffers.size(); i++) {
// [P2-LEGACY]             var b = view.buffers.get(i);
// [P2-LEGACY]             String anfitrion = "ID " + b.idComp();
// [P2-LEGACY]             for (int j=0; j<view.computers.size(); j++) if (view.computers.get(j).id() == b.idComp()) anfitrion = view.computers.get(j).name();
// [P2-LEGACY]             modeloBuffers.addRow(new Object[]{b.id(), anfitrion, b.datos().size(), b.storage(), b.latency(), b.mutex().count(), b.llenos().count(), b.vacios().count()});
// [P2-LEGACY]             if (b.id() == id) nuevoIndice = i;
// [P2-LEGACY]         }
// [P2-LEGACY]         int altura = 28 + Math.max(2, view.buffers.size()) * buffers.getRowHeight();
// [P2-LEGACY]         scrollBuffers.setPreferredSize(new Dimension(800, altura));
// [P2-LEGACY]         scrollBuffers.setMaximumSize(new Dimension(Integer.MAX_VALUE, altura));
// [P2-LEGACY]         if (nuevoIndice < 0 && view.buffers.size() > 0) nuevoIndice = 0;
// [P2-LEGACY]         if (nuevoIndice >= 0) buffers.setRowSelectionInterval(nuevoIndice, nuevoIndice);
// [P2-LEGACY]         actualizando = false;
// [P2-LEGACY]         mostrarBuffer();
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY]     private void mostrarBuffer() {
// [P2-LEGACY]         int fila = buffers.getSelectedRow();
// [P2-LEGACY]         aplicarLatencia.setEnabled(fila >= 0 && fila < view.buffers.size());
// [P2-LEGACY]         if (fila < 0 || fila >= view.buffers.size()) { esperaBuffers.setText("No hay buffers creados."); return; }
// [P2-LEGACY]         var b = view.buffers.get(fila);
// [P2-LEGACY]         esperaBuffers.setText("Buffer " + b.id() + " · Contenido: " + b.datos().text()
// [P2-LEGACY]                 + "\nProcesos en espera: " + b.blockedProcessesAsString());
// [P2-LEGACY]         if (!((JSpinner.DefaultEditor) latencia.getEditor()).getTextField().hasFocus()) latencia.setValue(b.latency());
// [P2-LEGACY]     }

    private final class DetalleComputador extends JPanel {
        private final int id;
        private final JLabel cpu = texto("CPU libre", 14, TINTA, true);
        private final JLabel ram = texto("", 13, GRIS, false);
        private final JComboBox<SchedulingPolicyType> politica = new JComboBox<>(SchedulingPolicyType.values());
        private final JSpinner quantum = new JSpinner(new SpinnerNumberModel(3, 1, 99, 1));
        private final DefaultTableModel[] modelos = new DefaultTableModel[4];
        private final JScrollPane[] tablasColas = new JScrollPane[4];
        private boolean actualizandoDetalle;
        DetalleComputador(int id) {
            this.id = id; setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(FONDO);
            setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 8));
            JPanel tarjetaCPU = tarjeta(); tarjetaCPU.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
            tarjetaCPU.add(texto("CPU y planificación", 18, TINTA, true)); tarjetaCPU.add(Box.createVerticalStrut(8));
            tarjetaCPU.add(cpu); tarjetaCPU.add(Box.createVerticalStrut(6)); tarjetaCPU.add(ram);
            JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); controles.setOpaque(false); controles.setAlignmentX(LEFT_ALIGNMENT);
            controles.add(texto("Política", 12, GRIS, false)); controles.add(politica);
            controles.add(texto("Quantum RR", 12, GRIS, false)); controles.add(quantum); tarjetaCPU.add(Box.createVerticalStrut(10)); tarjetaCPU.add(controles);
            politica.addActionListener(e -> { if (!actualizandoDetalle && politica.getSelectedItem() != null) { var seleccion=(SchedulingPolicyType) politica.getSelectedItem(); GuiRequest.control(controller, () -> controller.setComputerPolicy(id, seleccion)); } });
            quantum.addChangeListener(e -> { if (!actualizandoDetalle) { int q=(Integer) quantum.getValue(); GuiRequest.control(controller, () -> controller.setComputerQuantum(id, q)); } });
            add(tarjetaCPU); add(Box.createVerticalStrut(18));
            String[] nombres={"Nuevos", "Listos", "Bloqueados", "Terminados"};
            for(int i=0;i<4;i++) {
                modelos[i]=tabla("ID", "Nombre", "Tipo", "Prioridad", "RAM requerida", "T. Restante", "Deadline", "PC", "Motivo");
                add(texto(nombres[i], 15, TINTA, true)); add(Box.createVerticalStrut(6));
                JTable t=new JTable(modelos[i]); t.setRowHeight(28); t.setBackground(PAPEL); t.setForeground(TINTA);
                JScrollPane scroll=new JScrollPane(t); scroll.setColumnHeaderView(t.getTableHeader()); scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER); desplazarVistaDesdeTabla(scroll);
                scroll.setPreferredSize(new Dimension(800, 56)); scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56)); scroll.setAlignmentX(LEFT_ALIGNMENT);
                tablasColas[i]=scroll; add(scroll); add(Box.createVerticalStrut(14));
            }
        }
        void actualizar(Node c) {
            actualizandoDetalle = true;
            var p=c.cpu().PCB();
            cpu.setText(p==null ? "CPU libre · " + c.cpu().mode() : "Ejecuta " + p.name() + " (ID " + p.id() + ") · PC " + c.cpu().PC() + " · " + c.cpu().mode() + " · Prioridad " + p.priority() + " · Deadline " + p.remainingDeadline());
            ram.setText("RAM: " + c.memoryManager().usedMemory() + " usada · " + (c.memoryManager().totalMemory()-c.memoryManager().usedMemory()) + " libre · " + c.memoryManager().totalMemory() + " total");
            politica.setSelectedItem(c.politica()); if (!((JSpinner.DefaultEditor) quantum.getEditor()).getTextField().hasFocus()) quantum.setValue(c.quantum());
            quantum.setEnabled(c.politica()==SchedulingPolicyType.ROUND_ROBIN);
            var k=c.kernel(); var colas=new SimulationSnapshot.QueueView[]{k.newQueue(),k.readyQueue(),k.blockedQueue(),k.endedQueue()};
            for(int i=0;i<colas.length;i++) { modelos[i].setRowCount(0); for(int j=0;j<colas[i].processes().size();j++) {
                ProcessView pcola=colas[i].processes().get(j);
                modelos[i].addRow(new Object[]{pcola.id(),pcola.name(),pcola.type(),pcola.priority(),pcola.assignedMemory(),pcola.remainingTime(),pcola.remainingDeadline(),pcola.programCounter(),motivo(pcola)});
            }
                int altura = 28 + Math.max(1, colas[i].processes().size()) * 28;
                tablasColas[i].setPreferredSize(new Dimension(800, altura));
                tablasColas[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, altura));
            }
            revalidate();
            actualizandoDetalle = false;
        }
    }

    private static String motivo(ProcessView p) {
        return switch (p.blockReason()) {
            case WAITING_MUTEX -> "Mutex";
            case WAITING_EMPTY_BUFFER -> "Buffer vacío";
            case WAITING_FULL_BUFFER -> "Buffer lleno";
            case NETWORK_LATENCY -> "Red";
            case WAITING_IO -> "E/S";
            case WAITING_DISK -> "Disco"; // [P2]
            case SWAPPING_OUT -> "Swap-out"; // [P2]
            case SWAPPING_IN -> "Swap-in"; // [P2]
            case NONE -> "—";
        };
    }
    private static DefaultTableModel tabla(String... columnas) {
        return new DefaultTableModel(columnas, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } };
    }
    private static JPanel columna() { JPanel p=new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setBackground(FONDO); p.setAlignmentX(LEFT_ALIGNMENT); return p; }
    private static JPanel encabezado(String titulo, String descripcion) {
        JPanel p=columna(); p.add(texto(titulo,18,TINTA,true)); p.add(texto(descripcion,12,GRIS,false)); p.add(Box.createVerticalStrut(8)); return p;
    }
    /** La rueda sobre una tabla mueve el scroll exterior de la vista seleccionada. */
    private static void desplazarVistaDesdeTabla(JScrollPane tabla) {
        tabla.setWheelScrollingEnabled(false);
        java.awt.event.MouseWheelListener mover = evento -> {
            JScrollPane exterior = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tabla.getParent());
            if (exterior == null) return;
            Point punto = SwingUtilities.convertPoint(evento.getComponent(), evento.getPoint(), exterior);
            java.awt.event.MouseWheelEvent reenviado = new java.awt.event.MouseWheelEvent(
                    exterior, evento.getID(), evento.getWhen(), evento.getModifiersEx(),
                    punto.x, punto.y, evento.getXOnScreen(), evento.getYOnScreen(),
                    evento.getClickCount(), evento.isPopupTrigger(), evento.getScrollType(),
                    evento.getScrollAmount(), evento.getWheelRotation(), evento.getPreciseWheelRotation());
            exterior.dispatchEvent(reenviado);
            evento.consume();
        };
        tabla.addMouseWheelListener(mover);
        tabla.getViewport().addMouseWheelListener(mover);
        if (tabla.getViewport().getView() != null) tabla.getViewport().getView().addMouseWheelListener(mover);
        if (tabla.getColumnHeader() != null && tabla.getColumnHeader().getView() != null)
            tabla.getColumnHeader().getView().addMouseWheelListener(mover);
    }

    private static JScrollPane desplazable(JPanel contenido) {
        JScrollPane scroll=new JScrollPane(contenido); scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }
}
