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
import com.avilaos.core.model.UserMode;

/** Vista de simulación y sus controles. */
public final class VistaSimulacionAlternativa extends JPanel {
    private final SimulationController controller;
    private final JLabel reloj = texto("Ciclo 0 · Detenida", 18, TINTA, true);
    private final RelojAnimado relojAnimado = new RelojAnimado();
    private final JComboBox<Integer> intervalo = new JComboBox<>(new Integer[]{100, 250, 500, 1000});
    private final JTabbedPane vistas = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JButton botonModoUsuario = accion("Sesión: ADMIN · admin", false);
    private final JPanel panelCPU = new JPanel(new BorderLayout());
    private final DefaultTableModel modeloColaDiscoResumen = tabla("Estado", "ID Solicitud", "Proceso", "Operación", "Ruta / Destino", "Bloque destino", "Bloques", "Ciclo llegada");
    private final JTable tablaColaDiscoResumen = new JTable(modeloColaDiscoResumen);
    private final JScrollPane scrollColaDiscoResumen = new JScrollPane(tablaColaDiscoResumen);
    private final JLabel lblEstadoDiscoResumen = texto("Cabezal: bloque 0 · Política: FIFO", 13, GRIS, false);
    private final JLabel ocupacionDisco = texto(" ", 13, GRIS, false);
    private final JLabel estadoColaDisco = texto("No hay solicitudes de E/S pendientes.", 12, GRIS, false);
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
    private final Runnable irAArchivos;
    private long session;
    private boolean actualizando;

    public VistaSimulacionAlternativa(SimulationController controller) {
        this(controller, () -> {});
    }

    public VistaSimulacionAlternativa(SimulationController controller, Runnable irAArchivos) {
        super(new BorderLayout(0, 12));
        this.controller = controller;
        this.irAArchivos = irAArchivos;
        this.view = controller.snapshot();
        this.session = view.session;
        intervalo.setSelectedItem(view.interval);
        setName("simulacionAlternativa");
        setBackground(FONDO);
        setBorder(BorderFactory.createEmptyBorder(22, 28, 20, 28));
        add(cabecera(), BorderLayout.NORTH);
        construirResumen();
        panelCPU.setBackground(FONDO);
        vistas.addTab("CPU y procesos", panelCPU);
        vistas.addTab("Disco y E/S", desplazable(resumen));
        vistas.addTab("Eventos", panelEventos());
        add(vistas, BorderLayout.CENTER);
        add(reflexion(), BorderLayout.SOUTH);
        controller.addGUIListener(this::actualizar);
        controller.addLogListener(texto -> {
            long sesionActual = controller.session();
            if (session != sesionActual) { eventos.setText(""); session = sesionActual; }
            eventos.append(texto + "\n");
            eventos.setCaretPosition(eventos.getDocument().getLength());
        });
        reconstruir();
        actualizar();
    }

    private JPanel reflexion() {
        JPanel tarjeta = new JPanel(new BorderLayout(16, 0));
        tarjeta.setBackground(PAPEL);
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        JPanel contenido = new JPanel();
        contenido.setOpaque(false);
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.add(texto("SOBRE EL CAMBIO", 11, ACENTO, true));
        contenido.add(Box.createVerticalStrut(4));
        contenido.add(texto("“Observa constantemente que todo ocurre mediante el cambio.”", 15, TINTA, true));
        contenido.add(Box.createVerticalStrut(4));
        contenido.add(texto("Marco Aurelio · Meditaciones IV.36 · traducción libre", 12, GRIS, false));
        tarjeta.add(contenido, BorderLayout.CENTER);
        java.net.URL imagen = getClass().getResource("/imagenes/marco-aurelio.png");
        if (imagen != null) {
            Image retrato = new ImageIcon(imagen).getImage().getScaledInstance(64, 65, Image.SCALE_SMOOTH);
            JLabel retratoLabel = new JLabel(new ImageIcon(retrato));
            retratoLabel.setToolTipText("Retrato de Marco Aurelio");
            tarjeta.add(retratoLabel, BorderLayout.EAST);
        }
        return tarjeta;
    }

    private JPanel cabecera() {
        JPanel principal = columna();
        principal.add(texto("SIMULACIÓN", 11, ACENTO, true));
        principal.add(Box.createVerticalStrut(4));
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        fila.add(reloj);
        fila.add(texto("Intervalo (ms)", 12, GRIS, false));
        fila.add(intervalo);
        JButton aplicar = accion("Aplicar", false);
        aplicar.addActionListener(e -> {
            Integer ms = (Integer) intervalo.getSelectedItem();
            if (ms != null) GuiRequest.control(controller, () -> controller.setCycleDurationMs(ms));
        });
        fila.add(aplicar);
        botonModoUsuario.addActionListener(e -> mostrarSesion());
        fila.add(botonModoUsuario);
        JButton reiniciar = accion("Reiniciar", false);
        reiniciar.addActionListener(e -> {
            int respuesta = JOptionPane.showConfirmDialog(this,
                    "Se reiniciará la simulación y se perderán los cambios no guardados. ¿Deseas continuar?",
                    "Reiniciar todo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (respuesta != JOptionPane.YES_OPTION) return;
            reiniciar.setEnabled(false);
            GuiCommands.submit(() -> { controller.reset(); return true; }, ok -> {
                reiniciar.setEnabled(true);
                actualizar();
            }, error -> {
                reiniciar.setEnabled(true);
                controller.logEvent("Error al reiniciar: " + error.getMessage());
                JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo reiniciar", JOptionPane.ERROR_MESSAGE);
            });
        });
        fila.add(reiniciar);
        principal.add(fila);
        relojAnimado.setPreferredSize(new Dimension(72, 72));
        JPanel cabecera = new JPanel(new BorderLayout(12, 0));
        cabecera.setOpaque(false);
        cabecera.add(principal, BorderLayout.CENTER);
        cabecera.add(relojAnimado, BorderLayout.EAST);
        return cabecera;
    }

    private void mostrarSesion() {
        JComboBox<UserMode> modo = new JComboBox<>(UserMode.values());
        modo.setSelectedItem(controller.getMode());
        JTextField nombre = new JTextField(controller.getAccessControl().getActiveUsername(), 16);
        nombre.setEditable(modo.getSelectedItem() == UserMode.USER);
        modo.addActionListener(e -> {
            boolean esUser = modo.getSelectedItem() == UserMode.USER;
            nombre.setEditable(esUser);
            nombre.setText(esUser ? "" : "admin");
        });
        JPanel formulario = new JPanel(new GridLayout(0, 2, 8, 8));
        formulario.add(new JLabel("Modo")); formulario.add(modo);
        formulario.add(new JLabel("Usuario")); formulario.add(nombre);
        Object[] opciones = {"Aplicar", "Cancelar"};
        while (JOptionPane.showOptionDialog(this, formulario, "Cambiar sesión",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, opciones, opciones[0]) == JOptionPane.OK_OPTION) {
            UserMode elegido = (UserMode) modo.getSelectedItem();
            String actor = nombre.getText().trim();
            if (elegido == UserMode.USER && actor.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Indica un nombre de usuario.", "Sesión", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            botonModoUsuario.setEnabled(false);
            GuiRequest.model(controller, () -> { controller.setUserSession(elegido, actor); return true; }, ok -> {
                botonModoUsuario.setText("Sesión: " + elegido + " · " + (elegido == UserMode.ADMIN ? "admin" : actor));
                botonModoUsuario.setEnabled(true);
            }, error -> {
                botonModoUsuario.setEnabled(true);
                JOptionPane.showMessageDialog(this, error.getMessage(), "Sesión", JOptionPane.ERROR_MESSAGE);
            });
            break;
        }
    }

    // ── Cargar / Guardar sistema de archivos JSON ─────────────────────────────

    private void guardarSistemaArchivos(JButton boton) {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
            archivo = new File(archivo.getParentFile(), archivo.getName() + ".json");
        if (archivo.exists() && JOptionPane.showConfirmDialog(this,
                "El archivo ya existe. ¿Deseas reemplazarlo?", "Guardar sistema de archivos",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        final File destino = archivo;
        boton.setEnabled(false);
        GuiRequest.model(controller, () -> {
            var disco = controller.getDisk();
            if (disco.getActiveRequest() != null || disco.getQueueSize() != 0)
                throw new IllegalStateException("Espera a que terminen las solicitudes de disco antes de guardar.");
            controller.saveFileSystemJson(destino);
            return true;
        }, ok -> {
            boton.setEnabled(true);
            controller.logEvent("Sistema de archivos guardado: " + destino.getName());
        }, error -> {
            boton.setEnabled(true);
            String motivo = error.getMessage() == null ? error.toString() : error.getMessage();
            controller.logEvent("Error al guardar sistema de archivos: " + motivo);
            JOptionPane.showMessageDialog(this, motivo, "No se pudo guardar", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void cargarSistemaArchivos(JButton boton) {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File archivo = selector.getSelectedFile();
        boton.setEnabled(false);
        GuiRequest.model(controller,
                () -> controller.isFileSystemJsonLoaded() || !controller.getFileSystemManager().getAllNodesPreOrder().isEmpty(),
                hayDatos -> {
                    if (hayDatos) {
                        int confirmacion = JOptionPane.showConfirmDialog(this,
                                "La carga reemplazará el disco y los archivos actuales. ¿Deseas continuar?",
                                "Cargar sistema de archivos", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (confirmacion != JOptionPane.YES_OPTION) { boton.setEnabled(true); return; }
                    }
                    aplicarCargaSistemaArchivos(archivo, boton);
                }, error -> {
                    boton.setEnabled(true);
                    controller.logEvent("Error al comprobar el sistema de archivos: " + error.getMessage());
                    JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo cargar", JOptionPane.ERROR_MESSAGE);
                });
    }

    private void aplicarCargaSistemaArchivos(File archivo, JButton boton) {
        GuiRequest.model(controller, () -> {
            controller.logEvent("Intento de carga del sistema de archivos: " + archivo.getName());
            if (controller.isRunning())
                throw new IllegalStateException("Detén y reinicia la simulación antes de reemplazar el disco.");
            controller.loadFileSystemJson(archivo);
            return true;
        }, ok -> {
            boton.setEnabled(true);
            controller.logEvent("Sistema de archivos cargado correctamente.");
            actualizar();
        }, error -> {
            boton.setEnabled(true);
            String motivo = error.getMessage() == null ? error.toString() : error.getMessage();
            controller.logEvent("Error al cargar sistema de archivos: " + motivo);
            JOptionPane.showMessageDialog(this, motivo, "No se pudo cargar", JOptionPane.ERROR_MESSAGE);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

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
        resumen.setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 8));
        resumen.add(encabezado("Disco y E/S", "Solicitud activa y solicitudes pendientes según la política de disco"));
        lblEstadoDiscoResumen.setAlignmentX(LEFT_ALIGNMENT);
        resumen.add(lblEstadoDiscoResumen);
        ocupacionDisco.setAlignmentX(LEFT_ALIGNMENT);
        resumen.add(ocupacionDisco);
        resumen.add(Box.createVerticalStrut(12));
        resumen.add(texto("Cola única de E/S del disco", 15, TINTA, true));
        resumen.add(texto("Incluye la solicitud en atención y todas las solicitudes en espera.", 12, GRIS, false));
        resumen.add(Box.createVerticalStrut(6));
        tablaColaDiscoResumen.setRowHeight(28);
        tablaColaDiscoResumen.setBackground(PAPEL);
        tablaColaDiscoResumen.setForeground(TINTA);
        scrollColaDiscoResumen.setColumnHeaderView(tablaColaDiscoResumen.getTableHeader());
        scrollColaDiscoResumen.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        desplazarVistaDesdeTabla(scrollColaDiscoResumen);
        scrollColaDiscoResumen.setPreferredSize(new Dimension(800, 56));
        scrollColaDiscoResumen.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        scrollColaDiscoResumen.setAlignmentX(LEFT_ALIGNMENT);
        resumen.add(scrollColaDiscoResumen);
        estadoColaDisco.setAlignmentX(LEFT_ALIGNMENT);
        resumen.add(estadoColaDisco);
        resumen.add(Box.createVerticalStrut(12));
        JButton verArchivos = accion("Ver archivos", false);
        verArchivos.addActionListener(e -> irAArchivos.run());
        resumen.add(verArchivos);
    }

    private JPanel panelEventos() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(FONDO);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 8, 8, 8));
        JPanel cabecera = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        cabecera.setOpaque(false);
        cabecera.add(texto("Eventos de la simulación", 16, TINTA, true));
        JButton exportar = accion("Exportar log", false);
        exportar.addActionListener(e -> exportarLog());
        cabecera.add(exportar);
        panel.add(cabecera, BorderLayout.NORTH);
        eventos.setEditable(false);
        eventos.setLineWrap(true);
        eventos.setWrapStyleWord(true);
        eventos.setBackground(PAPEL);
        panel.add(new JScrollPane(eventos), BorderLayout.CENTER);
        return panel;
    }

    private void actualizar() {
        SimulationSnapshot anterior = view;
        view = controller.snapshot();
        if (session != view.session) { eventos.setText(""); session = view.session; }
        reloj.setText("Ciclo " + view.cycle + " · " + (!view.running ? "Detenida" : view.paused ? "Pausada" : "Activa"));
        relojAnimado.setActivo(view.running && !view.paused);
        if (anterior.interval != view.interval || anterior.session != view.session) intervalo.setSelectedItem(view.interval);
        if (anterior.session != view.session) botonModoUsuario.setText("Sesión: ADMIN · admin");
        boolean cambio = ids.length != view.computers.size() || anterior.session != view.session;
        if (!cambio) for (int i=0; i<ids.length; i++) if (ids[i] != view.computers.get(i).id()) { cambio=true; break; }
        if (cambio) reconstruir();
        for (int i = 0; i < detalles.length; i++) detalles[i].actualizar(view.computers.get(i));
        actualizarTablaColaDisco(modeloColaDiscoResumen, scrollColaDiscoResumen);
        if (view.disk != null) {
            String dir = view.disk.activeHeadDirection() == com.avilaos.disk.HeadDirection.UP ? "creciente ↑" : "decreciente ↓";
            lblEstadoDiscoResumen.setText("Cabezal: bloque " + view.disk.activeHeadPosition() + " (" + dir + ")  ·  Política: "
                    + view.disk.activePolicy() + "  ·  En cola: " + view.disk.queueSize() + "  ·  Atendidas: " + view.disk.completedRequestsCount());
            ocupacionDisco.setText("Datos: " + (view.bloquesDatos - view.bloquesDatosLibres) + "/" + view.bloquesDatos
                    + " bloques ocupados  ·  Swap: " + (view.bloquesSwap - view.bloquesSwapLibres) + "/" + view.bloquesSwap + " bloques ocupados");
        }
        resumen.revalidate();
        resumen.repaint();
// [P2-LEGACY]         actualizarBuffers();
    }

    private void actualizarTablaColaDisco(DefaultTableModel modelo, JScrollPane scroll) {
        modelo.setRowCount(0);
        if (view != null && view.diskQueue != null) {
            for (int i = 0; i < view.diskQueue.size(); i++) {
                var r = view.diskQueue.get(i);
                modelo.addRow(new Object[]{r.status(), r.id(), r.processName(), r.operation(), r.path(), r.targetBlock(), r.blockCount(), r.arrivalCycle()});
            }
        }
        int filas = (view != null && view.diskQueue != null) ? view.diskQueue.size() : 0;
        estadoColaDisco.setVisible(filas == 0);
        int altura = 28 + Math.max(1, filas) * 28;
        scroll.setPreferredSize(new Dimension(800, altura));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, altura));
    }

    private void reconstruir() {
        panelCPU.removeAll();
        ids = new int[view.computers.size()];
        detalles = new DetalleComputador[ids.length];
        if (ids.length == 0) {
            JPanel vacio = columna();
            vacio.setBorder(BorderFactory.createEmptyBorder(18, 8, 8, 8));
            vacio.add(texto("Crea o carga un computador para ver la CPU y sus procesos.", 14, GRIS, false));
            panelCPU.add(vacio, BorderLayout.NORTH);
        } else {
            Node c = view.computers.get(0);
            ids[0] = c.id();
            detalles[0] = new DetalleComputador(c.id());
            panelCPU.add(desplazable(detalles[0]), BorderLayout.CENTER);
        }
        panelCPU.revalidate();
        panelCPU.repaint();
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
        private final DefaultTableModel[] modelos = new DefaultTableModel[6];
        private final JScrollPane[] tablasColas = new JScrollPane[6];
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
            String[] nombres={"Nuevos", "Listos", "Bloqueados", "Listos / Suspendidos (Swap)", "Bloqueados / Suspendidos (Swap)", "Terminados"};
            for(int i=0;i<6;i++) {
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
            ram.setText("RAM: " + c.memoryManager().usedMemory() + " usada · " + (c.memoryManager().totalMemory()-c.memoryManager().usedMemory()) + " libre · " + c.memoryManager().totalMemory() + " total  |  Swap: " + c.suspensionCount() + " suspensiones · " + c.reactivationCount() + " reactivaciones");
            politica.setSelectedItem(c.politica()); if (!((JSpinner.DefaultEditor) quantum.getEditor()).getTextField().hasFocus()) quantum.setValue(c.quantum());
            quantum.setEnabled(c.politica()==SchedulingPolicyType.ROUND_ROBIN);
            var k=c.kernel(); var colas=new SimulationSnapshot.QueueView[]{k.newQueue(),k.readyQueue(),k.blockedQueue(),k.readySuspendedQueue(),k.blockedSuspendedQueue(),k.endedQueue()};
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
