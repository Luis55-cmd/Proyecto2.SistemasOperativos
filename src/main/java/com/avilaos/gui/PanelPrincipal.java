package com.avilaos.gui;

import java.awt.*;
import com.avilaos.hardware.Computer;
import com.avilaos.simulation.SimulationSnapshot;
import com.avilaos.simulation.SimulationController;
// [P2-LEGACY] import com.avilaos.sync.Buffer;
import com.avilaos.core.model.SchedulingPolicyType;
import javax.swing.table.DefaultTableModel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Vista Swing: conserva formularios; no modifica el kernel ni ejecuta procesos. */
public class PanelPrincipal extends JPanel {
    public enum Destino { MENU, COMPUTADOR, BUFFER, DISCO, PROCESO, SIMULACION, METRICAS }
    static final Color FONDO = new Color(246, 243, 234);
    static final Color TINTA = new Color(38, 58, 43);
    static final Color GRIS = new Color(119, 105, 85);
    static final Color ACENTO = new Color(82, 112, 64);
    static final Color PAPEL = new Color(255, 253, 247);
    static final Color BORDE = new Color(218, 210, 190);
    private final CardLayout distribucion = new CardLayout();
    private final JPanel pantallas = new JPanel(distribucion);
    private final JButton[] navegacion = new JButton[Destino.values().length];
    private Destino destinoActual;
    private final JLabel estadoCargaArchivos = texto(" ", 12, ACENTO, true);
    private Timer avisoCargaTimer;
    private final JLabel estadoCargaProcesos = texto(" ", 12, ACENTO, true);
    private Timer avisoProcesosTimer;
    private final EditorProceso editorProceso;
    private final PanelMetricasCopia metricas;
    private final SimulationController controller = SimulationController.getInstance();
    
    private final CardLayout estadoComputadorLayout = new CardLayout();
    private final JPanel estadoComputadorPanel = new JPanel(estadoComputadorLayout);
    private final JLabel resumenNombre = texto(" ", 20, TINTA, true);
    private final JLabel resumenRam = texto(" ", 15, GRIS, false);
    private final JLabel resumenPolitica = texto(" ", 15, GRIS, false);
    private final JLabel resumenReloj = texto(" ", 15, GRIS, false);
    private final JComboBox<SchedulingPolicyType> politicaGestion = new JComboBox<>(SchedulingPolicyType.values());
    private final JSpinner quantumGestion = new JSpinner(new SpinnerNumberModel(3, 1, 9999, 1));
    private boolean actualizandoGestion = false;
    private final JLabel discoOrigen = texto("Consultando disco…", 12, GRIS, false);
    private final JLabel discoCapacidad = texto(" ", 15, TINTA, true);
    private final JLabel discoOcupacion = texto(" ", 14, GRIS, false);
    private final JLabel discoCabezal = texto(" ", 14, GRIS, false);
    private final JLabel discoPolitica = texto(" ", 14, GRIS, false);
    private boolean consultaDiscoPendiente = false;
    private final JToggleButton[] botonesPoliticaDisco = new JToggleButton[4];
    private final com.avilaos.disk.DiskSchedulingPolicyType[] politicasDisco = {
        com.avilaos.disk.DiskSchedulingPolicyType.FIFO, com.avilaos.disk.DiskSchedulingPolicyType.SSTF,
        com.avilaos.disk.DiskSchedulingPolicyType.SCAN, com.avilaos.disk.DiskSchedulingPolicyType.C_SCAN
    };
    private boolean[] bloquesOcupados = new boolean[0];
    private int cabezalMapa = 0;
    private int limiteDatosMapa = 0;
    private final JPanel mapaDisco = new JPanel() {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int total = bloquesOcupados.length;
            if (total == 0) return;
            int alto = Math.max(1, getHeight() - 16);
            int filas = Math.min(total, Math.max(1, alto / 5));
            int ancho = Math.min(150, Math.max(20, getWidth() - 110));
            int x = 40;
            for (int i = 0; i < filas; i++) {
                int inicio = (int) ((long) i * total / filas);
                int fin = (int) ((long) (i + 1) * total / filas);
                int ocupados = 0;
                for (int j = inicio; j < fin; j++) if (bloquesOcupados[j]) ocupados++;
                int y = 8 + i * alto / filas;
                int siguiente = 8 + (i + 1) * alto / filas;
                g.setColor(ocupados == 0 ? Color.WHITE : ocupados == fin - inicio
                        ? new Color(38, 90, 83) : new Color(155, 190, 172));
                g.fillRect(x, y, ancho, Math.max(1, siguiente - y));
                g.setColor(new Color(218, 210, 190));
                g.drawRect(x, y, ancho, Math.max(1, siguiente - y));
            }
            g.setColor(new Color(35, 35, 35));
            for (int marca = 0; marca <= 4; marca++) {
                int bloque = (int) ((long) marca * (total - 1) / 4);
                int yMarca = 8 + (int) ((long) bloque * alto / total);
                g.drawLine(x - 6, yMarca, x - 1, yMarca);
                String etiqueta = Integer.toString(bloque);
                int anchoTexto = g.getFontMetrics().stringWidth(etiqueta);
                g.drawString(etiqueta, x - 10 - anchoTexto,
                        Math.min(getHeight() - 2, Math.max(12, yMarca + 4)));
            }
            if (limiteDatosMapa < total) {
                int ySwap = 8 + (int) ((long) limiteDatosMapa * alto / total);
                Graphics sombreado = g.create(x, ySwap, ancho, Math.max(0, 8 + alto - ySwap));
                sombreado.setColor(new Color(156, 142, 107));
                for (int linea = -alto; linea < ancho + alto; linea += 9) {
                    sombreado.drawLine(linea, alto, linea + alto, 0);
                }
                sombreado.dispose();
                g.setColor(new Color(119, 105, 85));
                g.drawLine(x, ySwap, x + ancho, ySwap);
            }
            int yCabezal = 8 + (int) ((long) cabezalMapa * alto / total);
            g.setColor(new Color(185, 47, 43));
            g.fillRect(x - 8, yCabezal, ancho + 16, 3);
            g.drawString("Cabezal", x + ancho + 12, Math.max(14, yCabezal));
            if (limiteDatosMapa < total) {
                int ySwap = 8 + (int) ((long) limiteDatosMapa * alto / total);
                g.setColor(new Color(119, 105, 85));
                g.drawString("Swap", x + ancho + 12, Math.min(getHeight() - 5, ySwap + 14));
            }
        }
        @Override public String getToolTipText(java.awt.event.MouseEvent e) {
            int total = bloquesOcupados.length;
            int alto = Math.max(1, getHeight() - 16);
            if (total == 0 || e.getY() < 8 || e.getY() >= 8 + alto) return null;
            int filas = Math.min(total, Math.max(1, alto / 5));
            int fila = Math.min(filas - 1, (e.getY() - 8) * filas / alto);
            int inicio = (int) ((long) fila * total / filas);
            int fin = (int) ((long) (fila + 1) * total / filas);
            int ocupados = 0;
            for (int i = inicio; i < fin; i++) if (bloquesOcupados[i]) ocupados++;
            return "Bloques " + inicio + "–" + (fin - 1) + ": " + ocupados + " ocupados de " + (fin - inicio);
        }
    };

    // Controles de Computador
    private final JTextField nombreComputador = new JTextField();
    private final javax.swing.border.Border bordeNombreNormal = nombreComputador.getBorder();
    private final JLabel errorNombre = texto(" ", 12, new Color(166, 44, 38), false);
    private SchedulingPolicyType politicaSeleccionada = SchedulingPolicyType.FCFS;
    private final JSpinner spinnerQuantum = new JSpinner(new SpinnerNumberModel(3, 1, 99, 1));
    private final JLabel resultadoComputador = texto("Todavía no has creado computadores desde esta vista.", 12, GRIS, false);
    private final DefaultTableModel computadoresVisibles = new DefaultTableModel(
            new String[]{"ID", "Nombre", "RAM", "Política"}, 0) {
        @Override public boolean isCellEditable(int fila, int columna) { return false; }
    };

    // Controles de Buffer
    private record OpcionComputador(int id, String nombre) {
        @Override public String toString() { return nombre + " · ID " + id; }
    }
    // NOTA: El campo ID numérico se deshabilita/elimina visualmente porque el Controller lo autogenera.
// [P2-LEGACY]     private final JComboBox<OpcionComputador> comboComputadorBuffer = new JComboBox<>();
// [P2-LEGACY]     private final JSpinner spinnerAlmacenamiento = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
// [P2-LEGACY]     private final JSpinner spinnerLatenciaBuffer = new JSpinner(new SpinnerNumberModel(2, 0, 1000, 1));
// [P2-LEGACY]     private final JTextField memoriaBuffer = new JTextField("10");
// [P2-LEGACY]     private final JLabel resultadoBuffer = texto("Ningún buffer creado en esta sesión.", 12, GRIS, false);
// [P2-LEGACY]     private final DefaultTableModel buffersVisibles = new DefaultTableModel(
// [P2-LEGACY]             new String[]{"ID Buffer", "Host", "Capacidad (Items)", "Tama\u00f1o en RAM", "Latencia (ticks)"}, 0) {
// [P2-LEGACY]         @Override public boolean isCellEditable(int fila, int columna) { return false; }
// [P2-LEGACY]     };

    public PanelPrincipal() {
        super(new BorderLayout());
        setBackground(FONDO);
        JPanel lateral = new JPanel();
        lateral.setLayout(new BoxLayout(lateral, BoxLayout.Y_AXIS));
        lateral.setBackground(TINTA);
        lateral.setBorder(new EmptyBorder(30, 22, 24, 22));
        lateral.setPreferredSize(new Dimension(220, 0));
        LogoAvila marca = new LogoAvila();
        marca.setAlignmentX(LEFT_ALIGNMENT);
        lateral.add(marca);
        lateral.add(Box.createVerticalStrut(28));
        
        // [P2-LEGACY] String[] titulos = {"01   Inicio", "02   Computadores", "03   Buffers", "04   Procesos", "05   Simulación", "06   Métricas"};
        String[] titulos = {"01   Inicio", "02   Computador", "", "03   Disco", "04   Procesos", "05   Simulación", "06   Métricas"}; // [P2] sin Buffers
        for (Destino destino : Destino.values()) {
            if (destino == Destino.BUFFER) continue; // [P2] pantalla de buffers oculta
            JButton boton = accion(titulos[destino.ordinal()], false);
            boton.setHorizontalAlignment(SwingConstants.LEFT);
            boton.setMaximumSize(new Dimension(180, 46));
            boton.setAlignmentX(LEFT_ALIGNMENT);
            boton.addActionListener(e -> mostrarPanel(destino));
            navegacion[destino.ordinal()] = boton;
            lateral.add(boton);
            lateral.add(Box.createVerticalStrut(8));
        }
        lateral.add(Box.createVerticalGlue());
        JLabel pie = texto("ENTORNO DE DISEÑO", 10, new Color(192, 181, 148), true);
        pie.setAlignmentX(LEFT_ALIGNMENT);
        lateral.add(pie);
        add(lateral, BorderLayout.WEST);
        pantallas.setBackground(FONDO);
        pantallas.add(inicioNuevo(), Destino.MENU.name());
        pantallas.add(computador(), Destino.COMPUTADOR.name());
        JScrollPane paginaDisco = new JScrollPane(disco());
        paginaDisco.setBorder(null);
        paginaDisco.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        paginaDisco.getVerticalScrollBar().setUnitIncrement(20);
        pantallas.add(paginaDisco, Destino.DISCO.name());
        
// [P2-LEGACY]         pantallas.add(buffer(), Destino.BUFFER.name());
        
        editorProceso = new EditorProceso(() -> mostrarPanel(Destino.COMPUTADOR));
        pantallas.add(editorProceso, Destino.PROCESO.name());
        pantallas.add(new VistaSimulacionAlternativa(controller, () -> mostrarPanel(Destino.COMPUTADOR)), Destino.SIMULACION.name());
        metricas = new PanelMetricasCopia(controller);
        JPanel vistaMetricas = new JPanel(new BorderLayout(0, 16));
        vistaMetricas.setBackground(FONDO);
        vistaMetricas.setBorder(new EmptyBorder(24, 24, 24, 24));
        vistaMetricas.add(texto("Métricas del sistema", 26, TINTA, true), BorderLayout.NORTH);
        vistaMetricas.add(metricas, BorderLayout.CENTER);
        pantallas.add(vistaMetricas, Destino.METRICAS.name());
        add(pantallas, BorderLayout.CENTER);
        
        // --- CONEXIÓN AL OBSERVER ---
        // Se actualizan las tablas automáticamente cuando el SimulationController cambia
        controller.addGUIListener(() -> {
            metricas.actualizarMetricas();
            actualizarListaComputadores();
            if (destinoActual == Destino.DISCO) actualizarVistaDisco();
// [P2-LEGACY]             actualizarListaBuffers();
// [P2-LEGACY]             actualizarOpcionesComputadoresBuffer();
        });

        mostrarPanel(Destino.MENU);
    }

    public void mostrarPanel(Destino destino) {
        if (destino == null) throw new IllegalArgumentException("Destino de navegación requerido");
        if (destino == Destino.METRICAS) {
            metricas.actualizarMetricas();
        }
        if (destino == Destino.PROCESO) editorProceso.actualizarListas();
        
// [P2-LEGACY]         if (destino == Destino.BUFFER) actualizarOpcionesComputadoresBuffer();
        
        distribucion.show(pantallas, destino.name());
        destinoActual = destino;
        if (destino == Destino.DISCO) actualizarVistaDisco();
        for (Destino item : Destino.values()) {
            JButton boton = navegacion[item.ordinal()];
            if (boton == null) continue; // [P2] BUFFER oculto
            boton.setBackground(item == destino ? ACENTO : TINTA);
            boton.setForeground(item == destino ? Color.WHITE : new Color(225, 220, 201));
        }
    }

    public Destino getDestinoActual() { return destinoActual; }

    /** Maqueta visual: los botones de persistencia todavía no ejecutan acciones. */
    private JPanel inicioNuevo() {
        JPanel panel = pagina("BIENVENIDO A AVILAOS", "Prepara tu simulación",
                "Crea una configuración nueva, carga archivos o combina ambas opciones.");

        JPanel bienvenida = new JPanel(new BorderLayout(16, 0));
        bienvenida.setBackground(PAPEL);
        bienvenida.setAlignmentX(LEFT_ALIGNMENT);
        bienvenida.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE), new EmptyBorder(12, 24, 12, 24)));
        bienvenida.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
        JPanel cita = new JPanel();
        cita.setOpaque(false);
        cita.setLayout(new BoxLayout(cita, BoxLayout.Y_AXIS));
        cita.add(texto("UNA MEDITACIÓN PARA EMPEZAR", 11, ACENTO, true));
        cita.add(Box.createVerticalStrut(8));
        cita.add(texto("“Nada llega demasiado pronto ni demasiado tarde si llega a su debido tiempo.”", 16, TINTA, true));
        cita.add(Box.createVerticalStrut(6));
        cita.add(texto("Marco Aurelio · Meditaciones IV.23 · traducción libre", 12, GRIS, false));
        bienvenida.add(cita, BorderLayout.CENTER);
        java.net.URL imagen = getClass().getResource("/imagenes/marco-aurelio.png");
        if (imagen != null) {
            Image retrato = new ImageIcon(imagen).getImage().getScaledInstance(90, 92, Image.SCALE_SMOOTH);
            JLabel retratoLabel = new JLabel(new ImageIcon(retrato));
            retratoLabel.setToolTipText("Retrato de Marco Aurelio");
            bienvenida.add(retratoLabel, BorderLayout.EAST);
        }
        panel.add(bienvenida);
        panel.add(Box.createVerticalStrut(22));
        panel.add(texto("TU RECORRIDO", 11, ACENTO, true));
        panel.add(Box.createVerticalStrut(8));
        JPanel recorrido = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        recorrido.setOpaque(false);
        recorrido.setAlignmentX(LEFT_ALIGNMENT);
        recorrido.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        String[] etapas = {"Computador", "Disco", "Archivos", "Procesos", "Simulación"};
        Destino[] destinos = {Destino.COMPUTADOR, Destino.DISCO, null, Destino.PROCESO, Destino.SIMULACION};
        for (int i = 0; i < etapas.length; i++) {
            if (i > 0) {
                JLabel flecha = texto("  →  ", 15, GRIS, false);
                recorrido.add(flecha);
            }
            JButton etapa = new JButton(etapas[i]);
            etapa.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            etapa.setBorder(new EmptyBorder(5, 2, 5, 2));
            etapa.setBorderPainted(false);
            etapa.setContentAreaFilled(false);
            etapa.setFocusPainted(true);
            etapa.setForeground(ACENTO);
            Destino destino = destinos[i];
            if (destino == null) {
                etapa.setEnabled(false);
                etapa.setToolTipText("Vista pendiente de implementar");
            } else {
                etapa.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                etapa.addActionListener(e -> mostrarPanel(destino));
            }
            recorrido.add(etapa);
        }
        panel.add(recorrido);
        panel.add(Box.createVerticalStrut(22));
        panel.add(texto("ARCHIVOS DE LA SIMULACIÓN", 11, ACENTO, true));
        panel.add(Box.createVerticalStrut(10));

        JPanel opciones = new JPanel(new GridLayout(1, 2, 18, 0));
        opciones.setOpaque(false);
        opciones.setAlignmentX(LEFT_ALIGNMENT);
        opciones.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        String[] titulos = {"Sistema de archivos", "Procesos y configuración"};
        String[] descripciones = {"Disco, directorios y archivos.", "Computador, políticas y procesos."};
        for (int i = 0; i < titulos.length; i++) {
            JPanel tarjeta = tarjeta();
            tarjeta.add(texto(titulos[i], 18, TINTA, true));
            tarjeta.add(Box.createVerticalStrut(8));
            tarjeta.add(texto(descripciones[i], 12, GRIS, false));
            tarjeta.add(Box.createVerticalStrut(16));
            JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            botones.setOpaque(false);
            botones.setAlignmentX(LEFT_ALIGNMENT);
            JButton cargar = accion("Cargar JSON", true);
            JButton guardar = accion("Guardar JSON", false);
            if (i == 0) {
                cargar.addActionListener(e -> cargarSistemaArchivos(cargar));
            } else {
                cargar.addActionListener(e -> cargarProcesosConfiguracion(cargar));
            }
            guardar.setEnabled(false);
            botones.add(cargar);
            botones.add(guardar);
            tarjeta.add(botones);
            tarjeta.add(Box.createVerticalStrut(8));
            tarjeta.add(i == 0 ? estadoCargaArchivos : estadoCargaProcesos);
            opciones.add(tarjeta);
        }
        panel.add(opciones);
        panel.add(Box.createVerticalStrut(10));
        panel.add(texto("Carga de JSON disponible. Guardado pendiente de conectar.", 12, GRIS, false));
        return panel;
    }

    private void cargarSistemaArchivos(JButton boton) {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File archivo = selector.getSelectedFile();
        boton.setEnabled(false);
        if (avisoCargaTimer != null) avisoCargaTimer.stop();
        estadoCargaArchivos.setText(" ");
        GuiRequest.model(controller, () -> controller.isFileSystemJsonLoaded()
                || !controller.getFileSystemManager().getAllNodesPreOrder().isEmpty(), hayDatos -> {
            if (hayDatos) {
                int confirmacion = JOptionPane.showConfirmDialog(this,
                        "La carga reemplazará el disco y los archivos actuales. ¿Deseas continuar?",
                        "Cargar sistema de archivos", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirmacion != JOptionPane.YES_OPTION) {
                    boton.setEnabled(true);
                    return;
                }
            }
            aplicarCargaSistemaArchivos(archivo, boton);
        }, error -> {
            boton.setEnabled(true);
            controller.logEvent("Error al comprobar el sistema de archivos: " + error.getMessage());
            JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo cargar", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void aplicarCargaSistemaArchivos(java.io.File archivo, JButton boton) {
        GuiRequest.model(controller, () -> {
            controller.logEvent("Intento de carga del sistema de archivos: " + archivo.getName());
            if (controller.isRunning()) {
                throw new IllegalStateException("Detén y reinicia la simulación antes de reemplazar el disco.");
            }
            controller.loadFileSystemJson(archivo);
            return true;
        }, resultado -> {
            boton.setEnabled(true);
            estadoCargaArchivos.setText("Sistema de archivos cargado correctamente.");
            if (avisoCargaTimer != null) avisoCargaTimer.stop();
            avisoCargaTimer = new Timer(2500, e -> estadoCargaArchivos.setText(" "));
            avisoCargaTimer.setRepeats(false);
            avisoCargaTimer.start();
        }, error -> {
            boton.setEnabled(true);
            String motivo = error.getMessage() == null ? error.toString() : error.getMessage();
            controller.logEvent("Error al cargar sistema de archivos: " + motivo);
            JOptionPane.showMessageDialog(this, motivo, "No se pudo cargar", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void cargarProcesosConfiguracion(JButton boton) {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File archivo = selector.getSelectedFile();
        boton.setEnabled(false);
        if (avisoProcesosTimer != null) avisoProcesosTimer.stop();
        estadoCargaProcesos.setText(" ");
        GuiRequest.model(controller, () -> controller.isProcessesJsonLoaded()
                || !controller.getComputers().isEmpty() || !controller.getAllProcesses().isEmpty(), hayDatos -> {
            if (hayDatos) {
                int confirmacion = JOptionPane.showConfirmDialog(this,
                        "La carga reemplazará el computador y sus procesos, y cambiará ajustes del reloj y disco. ¿Deseas continuar?",
                        "Cargar procesos y configuración", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirmacion != JOptionPane.YES_OPTION) {
                    boton.setEnabled(true);
                    return;
                }
            }
            aplicarCargaProcesosConfiguracion(archivo, boton);
        }, error -> {
            boton.setEnabled(true);
            String motivo = error.getMessage() == null ? error.toString() : error.getMessage();
            controller.logEvent("Error al comprobar la configuración: " + motivo);
            JOptionPane.showMessageDialog(this, motivo, "No se pudo cargar", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void aplicarCargaProcesosConfiguracion(java.io.File archivo, JButton boton) {
        GuiRequest.model(controller, () -> {
            controller.logEvent("Intento de carga de procesos y configuración: " + archivo.getName());
            if (controller.isRunning()) {
                throw new IllegalStateException("Detén y reinicia la simulación antes de reemplazar la configuración.");
            }
            controller.loadProcessesConfigJson(archivo);
            return true;
        }, resultado -> {
            boton.setEnabled(true);
            estadoCargaProcesos.setText("Procesos y configuración cargados correctamente.");
            if (avisoProcesosTimer != null) avisoProcesosTimer.stop();
            avisoProcesosTimer = new Timer(2500, e -> estadoCargaProcesos.setText(" "));
            avisoProcesosTimer.setRepeats(false);
            avisoProcesosTimer.start();
        }, error -> {
            boton.setEnabled(true);
            String motivo = error.getMessage() == null ? error.toString() : error.getMessage();
            controller.logEvent("Error al cargar procesos y configuración: " + motivo);
            JOptionPane.showMessageDialog(this, motivo, "No se pudo cargar", JOptionPane.ERROR_MESSAGE);
        });
    }

    private static final class PanelDiscoAjustable extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(20, r.height - 20); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Vista de consulta; el mapa de bloques aún está pendiente. */
    private JPanel disco() {
        JPanel panel = new PanelDiscoAjustable();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(FONDO);
        panel.setBorder(new EmptyBorder(32, 32, 28, 32));
        panel.add(texto("DISCO", 11, ACENTO, true));
        panel.add(Box.createVerticalStrut(10));
        panel.add(texto("Tu disco", 28, TINTA, true));
        panel.add(Box.createVerticalStrut(10));
        panel.add(texto("Observa el disco de la simulación.", 13, GRIS, false));
        panel.add(Box.createVerticalStrut(28));
        JPanel configuracion = tarjeta();
        configuracion.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        configuracion.add(texto("ESTADO DEL DISCO", 11, ACENTO, true));
        configuracion.add(Box.createVerticalStrut(8));
        configuracion.add(discoOrigen);
        configuracion.add(Box.createVerticalStrut(10));
        configuracion.add(discoCapacidad);
        configuracion.add(Box.createVerticalStrut(6));
        configuracion.add(discoOcupacion);
        configuracion.add(Box.createVerticalStrut(6));
        configuracion.add(discoCabezal);
        configuracion.add(Box.createVerticalStrut(6));
        configuracion.add(discoPolitica);
        configuracion.add(Box.createVerticalStrut(12));
        configuracion.add(texto("POLÍTICA DE DISCO", 11, ACENTO, true));
        configuracion.add(Box.createVerticalStrut(6));
        JPanel selectorDisco = new JPanel(new GridLayout(1, 4, 6, 0));
        selectorDisco.setOpaque(false);
        selectorDisco.setAlignmentX(LEFT_ALIGNMENT);
        selectorDisco.setMaximumSize(new Dimension(440, 36));
        ButtonGroup grupoDisco = new ButtonGroup();
        for (int i = 0; i < politicasDisco.length; i++) {
            var politica = politicasDisco[i];
            JToggleButton boton = new JToggleButton(politica == com.avilaos.disk.DiskSchedulingPolicyType.C_SCAN ? "C-SCAN" : politica.name());
            boton.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
            boton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            boton.setOpaque(true);
            boton.setBorder(BorderFactory.createLineBorder(BORDE));
            boton.addActionListener(e -> cambiarPoliticaDisco(politica));
            botonesPoliticaDisco[i] = boton;
            grupoDisco.add(boton);
            selectorDisco.add(boton);
        }
        configuracion.add(selectorDisco);
        JPanel columnaIzquierda = new JPanel();
        columnaIzquierda.setOpaque(false);
        columnaIzquierda.setMinimumSize(new Dimension(0, 0));
        columnaIzquierda.setLayout(new BoxLayout(columnaIzquierda, BoxLayout.Y_AXIS));
        columnaIzquierda.add(configuracion);
        columnaIzquierda.add(Box.createVerticalStrut(20));
        JPanel mapa = tarjeta();
        mapa.setMinimumSize(new Dimension(0, 0));
        mapa.setMaximumSize(new Dimension(Integer.MAX_VALUE, 520));
        mapa.add(texto("OCUPACIÓN", 11, ACENTO, true));
        mapa.add(Box.createVerticalStrut(8));
        mapa.add(texto("Mapa del disco", 20, TINTA, true));
        mapa.add(Box.createVerticalStrut(6));
        mapa.add(texto("Blanco: libre · Oscuro: ocupado", 12, GRIS, false));
        mapa.add(texto("Verde claro: rango mixto", 12, GRIS, false));
        mapa.add(texto("Rayado: área de swap", 12, GRIS, false));
        mapa.add(Box.createVerticalStrut(8));
        mapaDisco.setBackground(PAPEL);
        mapaDisco.setAlignmentX(LEFT_ALIGNMENT);
        mapaDisco.setPreferredSize(new Dimension(360, 380));
        mapaDisco.setMaximumSize(new Dimension(Integer.MAX_VALUE, 380));
        mapaDisco.setToolTipText("Pasa el cursor por un rango para ver su ocupación");
        mapa.add(mapaDisco);
        JPanel cita = new JPanel(new BorderLayout(16, 0));
        cita.setBackground(PAPEL);
        cita.setAlignmentX(LEFT_ALIGNMENT);
        cita.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE), new EmptyBorder(12, 24, 12, 24)));
        cita.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        JPanel textoCita = new JPanel();
        textoCita.setOpaque(false);
        textoCita.setLayout(new BoxLayout(textoCita, BoxLayout.Y_AXIS));
        textoCita.add(texto("SOBRE EL ESPACIO", 11, ACENTO, true));
        textoCita.add(Box.createVerticalStrut(8));
        textoCita.add(texto("“Es el espacio vacío dentro de la vasija", 16, TINTA, true));
        textoCita.add(texto("lo que la hace útil.”", 16, TINTA, true));
        textoCita.add(Box.createVerticalStrut(6));
        textoCita.add(texto("Lao-Tsé · Tao Te Ching 11 · traducción libre", 12, GRIS, false));
        cita.add(textoCita, BorderLayout.CENTER);
        java.net.URL imagen = getClass().getResource("/imagenes/lao-tse.png");
        if (imagen != null) {
            Image retrato = new ImageIcon(imagen).getImage().getScaledInstance(90, 91, Image.SCALE_SMOOTH);
            JLabel retratoLabel = new JLabel(new ImageIcon(retrato));
            retratoLabel.setToolTipText("Retrato de Lao-Tsé");
            cita.add(retratoLabel, BorderLayout.EAST);
        }
        columnaIzquierda.add(cita);
        JPanel contenido = new JPanel(new GridBagLayout());
        contenido.setOpaque(false);
        contenido.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints posiciones = new GridBagConstraints();
        posiciones.gridy = 0;
        posiciones.weighty = 1;
        posiciones.anchor = GridBagConstraints.NORTH;
        posiciones.fill = GridBagConstraints.BOTH;
        posiciones.gridx = 0;
        posiciones.weightx = 0.62;
        posiciones.insets = new Insets(0, 0, 0, 20);
        contenido.add(columnaIzquierda, posiciones);
        posiciones.gridx = 1;
        posiciones.weightx = 0.38;
        posiciones.insets = new Insets(0, 0, 0, 0);
        contenido.add(mapa, posiciones);
        panel.add(contenido);
        return panel;
    }

    private void actualizarVistaDisco() {
        if (consultaDiscoPendiente) return;
        consultaDiscoPendiente = true;
        GuiRequest.model(controller, () -> {
            var disco = controller.getDisk();
            int datos = disco.getTotalBlocks();
            int swap = disco.getSwapSizeBlocks();
            boolean[] ocupados = new boolean[disco.getTotalPhysicalBlocks()];
            for (int i = 0; i < ocupados.length; i++) ocupados[i] = disco.getBlock(i).isOccupied();
            String[] resumen = new String[]{
                controller.isFileSystemJsonLoaded() ? "Sistema de archivos cargado desde JSON" : "Disco inicial del simulador; no se ha cargado un JSON",
                "Bloques de datos: " + datos + " · Swap: " + swap + " · Total físico: " + disco.getTotalPhysicalBlocks(),
                "Datos libres: " + disco.getFreeDataBlocks() + " / " + datos + " · Swap libre: " + disco.getFreeSwapBlocks() + " / " + swap,
                "Cabezal: bloque " + disco.getHeadPosition() + " · Dirección: " + (disco.getHeadDirection() == com.avilaos.disk.HeadDirection.UP ? "creciente ↑" : "decreciente ↓"),
                "Política: " + disco.getPolicyType() + " · Velocidad: " + disco.getHeadSpeedPerTick() + " posiciones por ciclo"
            };
            return new Object[]{resumen, ocupados, disco.getHeadPosition(), datos, disco.getPolicyType()};
        }, datos -> {
            consultaDiscoPendiente = false;
            String[] resumen = (String[]) datos[0];
            bloquesOcupados = (boolean[]) datos[1];
            cabezalMapa = (Integer) datos[2];
            limiteDatosMapa = (Integer) datos[3];
            discoOrigen.setText(resumen[0]);
            discoCapacidad.setText(resumen[1]);
            discoOcupacion.setText(resumen[2]);
            discoCabezal.setText(resumen[3]);
            discoPolitica.setText(resumen[4]);
            for (int i = 0; i < botonesPoliticaDisco.length; i++) {
                JToggleButton boton = botonesPoliticaDisco[i];
                if (boton == null) continue;
                boolean seleccionada = politicasDisco[i] == datos[4];
                boton.setSelected(seleccionada);
                boton.setBackground(seleccionada ? ACENTO : PAPEL);
                boton.setForeground(seleccionada ? Color.WHITE : TINTA);
            }
            mapaDisco.repaint();
        }, error -> {
            consultaDiscoPendiente = false;
            discoOrigen.setText("No se pudo consultar el disco: " + error.getMessage());
        });
    }

    private void cambiarPoliticaDisco(com.avilaos.disk.DiskSchedulingPolicyType politica) {
        GuiRequest.model(controller, () -> {
            var permiso = controller.getAccessControl().canChangePolicy();
            if (!permiso.allowed()) throw new IllegalStateException(permiso.message());
            var disco = controller.getDisk();
            if (disco.getPolicyType() != politica) {
                controller.logEvent("Solicitud de cambio de política de disco a " + politica);
                disco.setSchedulingPolicy(politica);
            }
            return true;
        }, ok -> actualizarVistaDisco(), error -> {
            String motivo = error.getMessage() == null ? error.toString() : error.getMessage();
            controller.logEvent("Error al cambiar política de disco: " + motivo);
            JOptionPane.showMessageDialog(this, motivo, "No se pudo cambiar la política", JOptionPane.ERROR_MESSAGE);
            actualizarVistaDisco();
        });
    }

    private JPanel computador() {
        JPanel panel = pagina("COMPUTADOR", "Tu computador", "Crea y consulta el único computador de la simulación.");
        JPanel tarjeta = tarjeta();
        tarjeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        tarjeta.add(texto("Recursos del computador", 20, TINTA, true));
        tarjeta.add(Box.createVerticalStrut(16));
        JPanel nombre = campo("Nombre del computador", nombreComputador);
        nombre.setAlignmentX(LEFT_ALIGNMENT);
        nombre.setMaximumSize(new Dimension(400, 62));
        tarjeta.add(nombre);
        errorNombre.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        tarjeta.add(Box.createVerticalStrut(4));
        tarjeta.add(errorNombre);
        tarjeta.add(Box.createVerticalStrut(24));
        JPanel campos = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        campos.setOpaque(false);
        campos.setAlignmentX(LEFT_ALIGNMENT);
        campos.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        JComboBox<Integer> ram = new JComboBox<>(new Integer[]{32, 64, 128, 256, 512, 1024, 2048});
        ram.setUI(new javax.swing.plaf.basic.BasicComboBoxUI());
        ram.setMaximumRowCount(4);
        ram.setSelectedItem(128);
        JPanel memoria = campo("RAM · unidades", ram);
        memoria.setPreferredSize(new Dimension(150, 62));
        campos.add(memoria);
        campos.add(Box.createHorizontalStrut(24));
        JPanel politica = campo("Política inicial", selectorPolitica());
        politica.setPreferredSize(new Dimension(480, 62));
        campos.add(politica);
        campos.add(Box.createHorizontalStrut(24));
        JPanel panelQuantum = campo("Quantum (RR)", spinnerQuantum);
        panelQuantum.setPreferredSize(new Dimension(110, 62));
        spinnerQuantum.setEnabled(politicaSeleccionada == SchedulingPolicyType.ROUND_ROBIN);
        campos.add(panelQuantum);
        tarjeta.add(campos);
        tarjeta.add(Box.createVerticalStrut(22));
        JButton crear = accion("Crear computador", true);
        crear.addActionListener(e -> crearComputadorDesdeFormulario((Integer) ram.getSelectedItem()));
        tarjeta.add(crear);
        tarjeta.add(Box.createVerticalStrut(10));
        resultadoComputador.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        tarjeta.add(resultadoComputador);
        JPanel gestion = tarjeta();
        gestion.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        gestion.add(texto("COMPUTADOR CONFIGURADO", 11, ACENTO, true));
        gestion.add(Box.createVerticalStrut(12));
        gestion.add(resumenNombre);
        gestion.add(Box.createVerticalStrut(12));
        gestion.add(resumenRam);
        gestion.add(Box.createVerticalStrut(6));
        gestion.add(resumenPolitica);
        gestion.add(Box.createVerticalStrut(6));
        gestion.add(resumenReloj);
        gestion.add(Box.createVerticalStrut(18));
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controles.setOpaque(false);
        controles.setAlignmentX(LEFT_ALIGNMENT);
        controles.add(texto("Política de CPU", 12, GRIS, true));
        controles.add(politicaGestion);
        controles.add(texto("Quantum RR", 12, GRIS, true));
        controles.add(quantumGestion);
        politicaGestion.addActionListener(e -> {
            if (actualizandoGestion || controller.snapshot().computers.size() == 0) return;
            int id = controller.snapshot().computers.get(0).getId();
            SchedulingPolicyType nuevaPolitica = (SchedulingPolicyType) politicaGestion.getSelectedItem();
            GuiRequest.model(controller, () -> { controller.setComputerPolicy(id, nuevaPolitica); return true; },
                    ok -> actualizarListaComputadores(), error -> {
                        actualizarListaComputadores();
                        JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo cambiar la política", JOptionPane.ERROR_MESSAGE);
                    });
        });
        quantumGestion.addChangeListener(e -> {
            if (actualizandoGestion || controller.snapshot().computers.size() == 0) return;
            int id = controller.snapshot().computers.get(0).getId();
            int quantum = (Integer) quantumGestion.getValue();
            GuiRequest.model(controller, () -> { controller.setComputerQuantum(id, quantum); return true; },
                    ok -> actualizarListaComputadores(), error -> {
                        actualizarListaComputadores();
                        JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo cambiar el quantum", JOptionPane.ERROR_MESSAGE);
                    });
        });
        gestion.add(controles);
        gestion.add(Box.createVerticalStrut(8));
        JButton eliminar = accion("Eliminar computador", false);
        eliminar.setEnabled(false);
        gestion.add(eliminar);
        gestion.add(Box.createVerticalStrut(8));
        gestion.add(texto("Eliminación pendiente de conectar.", 12, GRIS, false));
        estadoComputadorPanel.setOpaque(false);
        estadoComputadorPanel.setAlignmentX(LEFT_ALIGNMENT);
        estadoComputadorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        estadoComputadorPanel.add(tarjeta, "crear");
        estadoComputadorPanel.add(gestion, "gestionar");
        panel.add(estadoComputadorPanel);
        panel.add(Box.createVerticalStrut(20));
        JPanel reflexion = new JPanel(new BorderLayout(16, 0));
        reflexion.setBackground(PAPEL);
        reflexion.setAlignmentX(LEFT_ALIGNMENT);
        reflexion.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE), new EmptyBorder(12, 24, 12, 24)));
        reflexion.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
        JPanel cita = new JPanel();
        cita.setOpaque(false);
        cita.setLayout(new BoxLayout(cita, BoxLayout.Y_AXIS));
        cita.add(texto("SOBRE LA DISTRIBUCIÓN", 11, ACENTO, true));
        cita.add(Box.createVerticalStrut(8));
        cita.add(texto("“La justicia en la distribución sigue una proporción.”", 16, TINTA, true));
        cita.add(Box.createVerticalStrut(6));
        cita.add(texto("Aristóteles · Ética a Nicómaco V.3 · traducción libre", 12, GRIS, false));
        reflexion.add(cita, BorderLayout.CENTER);
        java.net.URL imagen = getClass().getResource("/imagenes/aristoteles.png");
        if (imagen != null) {
            Image retrato = new ImageIcon(imagen).getImage().getScaledInstance(72, 92, Image.SCALE_SMOOTH);
            JLabel retratoLabel = new JLabel(new ImageIcon(retrato));
            retratoLabel.setToolTipText("Retrato de Aristóteles");
            reflexion.add(retratoLabel, BorderLayout.EAST);
        }
        panel.add(reflexion);
        actualizarListaComputadores();
        return panel;
    }

// [P2-LEGACY]     private JPanel buffer() {
// [P2-LEGACY]         JPanel panel = pagina("BUFFERS", "Gestión de Buffers", "Asigna memoria en un computador específico para el esquema Productor/Consumidor.");
// [P2-LEGACY]         JPanel tarjeta = tarjeta();
// [P2-LEGACY]         tarjeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
// [P2-LEGACY]         tarjeta.add(texto("Propiedades del Buffer", 20, TINTA, true));
// [P2-LEGACY]         tarjeta.add(Box.createVerticalStrut(16));
// [P2-LEGACY]         
// [P2-LEGACY]         JPanel fila1 = new JPanel(new GridLayout(1, 2, 24, 0));
// [P2-LEGACY]         fila1.setOpaque(false);
// [P2-LEGACY]         fila1.setAlignmentX(LEFT_ALIGNMENT);
// [P2-LEGACY]         fila1.setMaximumSize(new Dimension(500, 62));
// [P2-LEGACY]         // Se autogenera el ID en el controlador, por eso se oculta el ID visual
// [P2-LEGACY]         fila1.add(campo("Computador Host", comboComputadorBuffer));
// [P2-LEGACY]         spinnerLatenciaBuffer.setName("latenciaBuffer");
// [P2-LEGACY]         fila1.add(campo("Latencia de Red (ticks)", spinnerLatenciaBuffer));
// [P2-LEGACY]         tarjeta.add(fila1);
// [P2-LEGACY]         tarjeta.add(Box.createVerticalStrut(16));
// [P2-LEGACY]         
// [P2-LEGACY]         JPanel fila2 = new JPanel(new GridLayout(1, 2, 24, 0));
// [P2-LEGACY]         fila2.setOpaque(false);
// [P2-LEGACY]         fila2.setAlignmentX(LEFT_ALIGNMENT);
// [P2-LEGACY]         fila2.setMaximumSize(new Dimension(500, 62));
// [P2-LEGACY]         fila2.add(campo("Capacidad (Elementos)", spinnerAlmacenamiento));
// [P2-LEGACY]         memoriaBuffer.setEditable(false);
// [P2-LEGACY]         memoriaBuffer.setName("ramBuffer");
// [P2-LEGACY]         spinnerAlmacenamiento.setName("capacidadBuffer");
// [P2-LEGACY]         ((javax.swing.text.DefaultFormatter) ((JSpinner.DefaultEditor) spinnerAlmacenamiento.getEditor())
// [P2-LEGACY]                 .getTextField().getFormatter()).setCommitsOnValidEdit(true);
// [P2-LEGACY]         spinnerAlmacenamiento.addChangeListener(e ->
// [P2-LEGACY]                 memoriaBuffer.setText(spinnerAlmacenamiento.getValue().toString()));
// [P2-LEGACY]         Runnable actualizarRamBuffer = () -> {
// [P2-LEGACY]             try { memoriaBuffer.setText(Integer.toString(EditorProceso.numeroVisible(spinnerAlmacenamiento))); }
// [P2-LEGACY]             catch (IllegalArgumentException ex) { memoriaBuffer.setText("—"); }
// [P2-LEGACY]         };
// [P2-LEGACY]         ((JSpinner.DefaultEditor) spinnerAlmacenamiento.getEditor()).getTextField().getDocument()
// [P2-LEGACY]                 .addDocumentListener(new javax.swing.event.DocumentListener() {
// [P2-LEGACY]                     public void insertUpdate(javax.swing.event.DocumentEvent e) { actualizarRamBuffer.run(); }
// [P2-LEGACY]                     public void removeUpdate(javax.swing.event.DocumentEvent e) { actualizarRamBuffer.run(); }
// [P2-LEGACY]                     public void changedUpdate(javax.swing.event.DocumentEvent e) { actualizarRamBuffer.run(); }
// [P2-LEGACY]                 });
// [P2-LEGACY]         fila2.add(campo("Tamaño en RAM", memoriaBuffer));
// [P2-LEGACY]         tarjeta.add(fila2);
// [P2-LEGACY]         tarjeta.add(Box.createVerticalStrut(22));
// [P2-LEGACY]         
// [P2-LEGACY]         JButton crear = accion("Crear y registrar Buffer", true);
// [P2-LEGACY]         crear.addActionListener(e -> crearBufferDesdeFormulario());
// [P2-LEGACY]         tarjeta.add(crear);
// [P2-LEGACY]         tarjeta.add(Box.createVerticalStrut(10));
// [P2-LEGACY]         resultadoBuffer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
// [P2-LEGACY]         tarjeta.add(resultadoBuffer);
// [P2-LEGACY]         
// [P2-LEGACY]         panel.add(tarjeta);
// [P2-LEGACY]         panel.add(Box.createVerticalStrut(20));
// [P2-LEGACY]         
// [P2-LEGACY]         JTable tabla = new JTable(buffersVisibles);
// [P2-LEGACY]         tabla.setRowHeight(32);
// [P2-LEGACY]         tabla.setBackground(PAPEL);
// [P2-LEGACY]         tabla.setForeground(TINTA);
// [P2-LEGACY]         JScrollPane lista = new JScrollPane(tabla);
// [P2-LEGACY]         lista.setColumnHeaderView(tabla.getTableHeader());
// [P2-LEGACY]         lista.setAlignmentX(LEFT_ALIGNMENT);
// [P2-LEGACY]         lista.setPreferredSize(new Dimension(600, 160));
// [P2-LEGACY]         panel.add(lista);
// [P2-LEGACY]         
// [P2-LEGACY]         actualizarListaBuffers();
// [P2-LEGACY]         return panel;
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY]     private void actualizarOpcionesComputadoresBuffer() {
// [P2-LEGACY]         var view = controller.snapshot();
// [P2-LEGACY]         OpcionComputador seleccion = (OpcionComputador) comboComputadorBuffer.getSelectedItem();
// [P2-LEGACY]         comboComputadorBuffer.removeAllItems();
// [P2-LEGACY]         for (int i = 0; i < view.computers.size(); i++) {
// [P2-LEGACY]             var c = view.computers.get(i);
// [P2-LEGACY]             OpcionComputador op = new OpcionComputador(c.getId(), c.getName());
// [P2-LEGACY]             comboComputadorBuffer.addItem(op);
// [P2-LEGACY]             if (seleccion != null && seleccion.id() == c.getId()) {
// [P2-LEGACY]                 comboComputadorBuffer.setSelectedItem(op);
// [P2-LEGACY]             }
// [P2-LEGACY]         }
// [P2-LEGACY]     }
// [P2-LEGACY] 
// [P2-LEGACY]     private void crearBufferDesdeFormulario() {
// [P2-LEGACY]         try {
// [P2-LEGACY]             OpcionComputador compSeleccionado = (OpcionComputador) comboComputadorBuffer.getSelectedItem();
// [P2-LEGACY]             if (compSeleccionado == null) {
// [P2-LEGACY]                 resultadoBuffer.setText("Error: Debes crear y seleccionar un computador primero.");
// [P2-LEGACY]                 return;
// [P2-LEGACY]             }
// [P2-LEGACY]             
// [P2-LEGACY]             int capacidad = EditorProceso.numeroVisible(spinnerAlmacenamiento); spinnerAlmacenamiento.setValue(capacidad);
// [P2-LEGACY]             int latencia = EditorProceso.numeroVisible(spinnerLatenciaBuffer); spinnerLatenciaBuffer.setValue(latencia);
// [P2-LEGACY]             
// [P2-LEGACY]             // Valores ya validados y asignados arriba
// [P2-LEGACY]             // (capacidad y latencia listos)
// [P2-LEGACY]             resultadoBuffer.setText("Creando buffer…");
// [P2-LEGACY]             GuiRequest.model(controller, () -> {
// [P2-LEGACY]                 // [P2-LEGACY] Buffer b=controller.createBuffer(compSeleccionado.id(), capacidad, latencia);
// [P2-LEGACY]                 Object b = null; // [P2] creación de buffers deshabilitada
// [P2-LEGACY]                 // [P2-LEGACY] return b==null ? "Error: RAM insuficiente o anfitrión inválido" : "Buffer ID " + b.getId() + " creado en Host " + compSeleccionado.id();
// [P2-LEGACY]                 return "Buffers deshabilitados en Proyecto 2";
// [P2-LEGACY]             }, resultadoBuffer::setText, error -> resultadoBuffer.setText(error.getMessage()));
// [P2-LEGACY] 
// [P2-LEGACY]             
// [P2-LEGACY]         } catch (Exception ex) {
// [P2-LEGACY]             resultadoBuffer.setText("Error: Capacidad (1-1000) y latencia (0-1000) deben ser enteros válidos.");
// [P2-LEGACY]         }
// [P2-LEGACY]     }
// [P2-LEGACY]     
// [P2-LEGACY]     private void actualizarListaBuffers() {
// [P2-LEGACY]         var view = controller.snapshot();
// [P2-LEGACY]         buffersVisibles.setRowCount(0);
// [P2-LEGACY]         for (int i = 0; i < view.buffers.size(); i++) {
// [P2-LEGACY]             var b = view.buffers.get(i);
// [P2-LEGACY]             String hostInfo = "Host " + b.getIdComp();
// [P2-LEGACY]             if (view.computers != null) {
// [P2-LEGACY]                 for (int c = 0; c < view.computers.size(); c++) {
// [P2-LEGACY]                     var comp = view.computers.get(c);
// [P2-LEGACY]                     if (comp.getId() == b.getIdComp()) {
// [P2-LEGACY]                         hostInfo = comp.getName() + " (" + b.getIdComp() + ")";
// [P2-LEGACY]                         break;
// [P2-LEGACY]                     }
// [P2-LEGACY]                 }
// [P2-LEGACY]             }
// [P2-LEGACY]             buffersVisibles.addRow(new Object[]{
// [P2-LEGACY]                 b.getId(),
// [P2-LEGACY]                 hostInfo,
// [P2-LEGACY]                 b.getStorage(),
// [P2-LEGACY]                 b.getSizeInMemory(),
// [P2-LEGACY]                 b.getLatency()
// [P2-LEGACY]             });
// [P2-LEGACY]         }
// [P2-LEGACY]     }
    
    
    private JPanel selectorPolitica() {
        JPanel opciones = new JPanel(new GridLayout(1, 4, 8, 0));
        opciones.setOpaque(false);
        opciones.setPreferredSize(new Dimension(480, 36));
        ButtonGroup grupo = new ButtonGroup();
        for (String nombre : new String[]{"FCFS", "Round Robin", "EDF", "Prioridades"}) {
            JToggleButton boton = new JToggleButton(nombre);
            boton.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
            boton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            boton.setOpaque(true);
            boton.setBackground(PAPEL);
            boton.setForeground(TINTA);
            boton.setBorder(BorderFactory.createLineBorder(BORDE));
            boton.addItemListener(e -> {
                if (boton.isSelected()) {
                    politicaSeleccionada = switch (nombre) {
                        case "Round Robin" -> SchedulingPolicyType.ROUND_ROBIN;
                        case "EDF" -> SchedulingPolicyType.EDF;
                        case "Prioridades" -> SchedulingPolicyType.PRIORITY_PREEMPTIVE;
                        default -> SchedulingPolicyType.FCFS;
                    };
                    spinnerQuantum.setEnabled(politicaSeleccionada == SchedulingPolicyType.ROUND_ROBIN);
                }
                boton.setBackground(boton.isSelected() ? ACENTO : PAPEL);
                boton.setForeground(boton.isSelected() ? Color.WHITE : TINTA);
            });
            grupo.add(boton);
            opciones.add(boton);
            boton.setSelected("FCFS".equals(nombre));
        }
        return opciones;
    }

    private void crearComputadorDesdeFormulario(Integer ram) {
        nombreComputador.setBorder(bordeNombreNormal);
        errorNombre.setText(" ");
        if (ram == null || nombreComputador.getText().trim().isEmpty()) {
            resultadoComputador.setText("Escribe un nombre y selecciona la RAM.");
            return;
        }
        String name=nombreComputador.getText();
        int quantum;
        try {
            quantum = politicaSeleccionada == SchedulingPolicyType.ROUND_ROBIN ? EditorProceso.numeroVisible(spinnerQuantum) : (Integer) spinnerQuantum.getValue();
            spinnerQuantum.setValue(quantum);
        } catch (IllegalArgumentException ex) {
            resultadoComputador.setText("El quantum debe ser un entero entre 1 y 99.");
            return;
        }
        SchedulingPolicyType policy=politicaSeleccionada;
        resultadoComputador.setText("Creando computador…");
        GuiRequest.model(controller, () -> {
            Computer creado=controller.createComputer(name,ram,policy,quantum);
            return "Creado: " + creado.getName() + " · ID " + creado.getId();
        }, resultadoComputador::setText, error -> {
            nombreComputador.setBorder(BorderFactory.createLineBorder(new Color(166,44,38),2));
            errorNombre.setText(error.getMessage());
            resultadoComputador.setText("No se creó otro computador.");
        });
    }

    private void actualizarListaComputadores() {
        var view = controller.snapshot();
        if (view.computers.size() == 0) {
            estadoComputadorLayout.show(estadoComputadorPanel, "crear");
            return;
        }
        var computador = view.computers.get(0);
        String politica = computador.getPolitica() == SchedulingPolicyType.ROUND_ROBIN
                ? "Round Robin · quantum " + computador.getQuantum()
                : computador.getPolitica().toString();
        resumenNombre.setText(computador.getName() + " · ID " + computador.getId());
        resumenRam.setText("RAM: " + computador.getRam().getTotalSize() + " unidades");
        resumenPolitica.setText("Política de CPU: " + politica);
        resumenReloj.setText("Duración del ciclo global: " + view.interval + " ms");
        actualizandoGestion = true;
        try {
            politicaGestion.setSelectedItem(computador.getPolitica());
            if (!((JSpinner.DefaultEditor) quantumGestion.getEditor()).getTextField().hasFocus()) {
                quantumGestion.setValue(Math.max(1, Math.min(9999, computador.getQuantum())));
            }
            quantumGestion.setEnabled(computador.getPolitica() == SchedulingPolicyType.ROUND_ROBIN);
        } finally {
            actualizandoGestion = false;
        }
        estadoComputadorLayout.show(estadoComputadorPanel, "gestionar");
    }

    // Funciones de utilidad visual
    static JLabel texto(String valor, int tamano, Color color, boolean negrita) {
        JLabel label = new JLabel(valor);
        label.setFont(new Font(Font.SANS_SERIF, negrita ? Font.BOLD : Font.PLAIN, tamano));
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }
    static JButton accion(String titulo, boolean primaria) {
        JButton boton = new JButton(titulo);
        boton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        boton.setBackground(primaria ? ACENTO : PAPEL);
        boton.setForeground(primaria ? Color.WHITE : TINTA);
        boton.setOpaque(true);
        boton.setContentAreaFilled(true);
        boton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primaria ? ACENTO : BORDE),
                new EmptyBorder(12, 16, 12, 16)));
        boton.setAlignmentX(LEFT_ALIGNMENT);
        return boton;
    }
    static JPanel pagina(String etiqueta, String titulo, String descripcion) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(FONDO);
        panel.setBorder(new EmptyBorder(32, 32, 28, 32));
        panel.add(texto(etiqueta, 11, ACENTO, true));
        panel.add(Box.createVerticalStrut(10));
        panel.add(texto(titulo, 28, TINTA, true));
        panel.add(Box.createVerticalStrut(10));
        panel.add(texto(descripcion, 13, GRIS, false));
        panel.add(Box.createVerticalStrut(28));
        return panel;
    }
    static JPanel tarjeta() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PAPEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE), new EmptyBorder(24, 24, 24, 24)));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        return panel;
    }
    static JPanel campo(String etiqueta, JComponent control) {
        JPanel panel = new JPanel(new BorderLayout(0, 9));
        panel.setOpaque(false);
        panel.add(texto(etiqueta, 12, GRIS, true), BorderLayout.NORTH);
        control.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        control.setPreferredSize(new Dimension(180, 36));
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }
}
