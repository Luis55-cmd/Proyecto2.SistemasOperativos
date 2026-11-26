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
    public enum Destino { MENU, COMPUTADOR, BUFFER, DISCO, ARCHIVOS, PROCESO, SIMULACION, METRICAS }
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
    private final JButton controlReloj = accion("", false);
    private final JButton avanceReloj = accion("+", false);
    private final JLabel cicloLateral = texto("Ciclo 0", 11, new Color(225, 220, 201), true);
    private boolean controlRelojPendiente;
    
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
    private final JLabel discoMetricas = texto(" ", 14, GRIS, false);
    private final JSpinner spinBloques = new JSpinner(new SpinnerNumberModel(200, 100, 500, 10));
    private final JSpinner spinSwap = new JSpinner(new SpinnerNumberModel(50, 10, 200, 10));
    private final JLabel lblPreviewCapacidadDisco = texto("Datos: 150 · Swap: 50 · Total físico: 200", 12, ACENTO, false);
    private final JLabel lblEstadoCapacidadDisco = texto("Configurable antes de iniciar la simulación (modo Administrador).", 12, GRIS, false);
    private JButton btnAplicarTamanoDisco;
    private final DefaultTableModel tablaColaDisco = new DefaultTableModel(
            new String[]{"Estado", "ID", "Proceso", "Operación", "Ruta / Destino", "Bloque", "Bloques"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private boolean consultaDiscoPendiente = false;
    private final JTree arbolArchivos = new JTree();
    private final JTabbedPane vistasArchivos = new JTabbedPane();
    private final DefaultTableModel tablaAsignacion = new DefaultTableModel(
            new String[]{"Ruta", "Bloques", "Primer bloque", "Dueño", "Proceso creador"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private boolean actualizandoArbolArchivos;
    private final JLabel[] detallesArchivo = new JLabel[7];
    private final JTextArea estadoSolicitudArchivo = crearEstadoSolicitudArchivo();
    private JButton botonRenombrarArchivo;
    private com.avilaos.core.model.ProcessControlBlock solicitudArchivo;
    private static JTextArea crearEstadoSolicitudArchivo() {
        JTextArea estado = new JTextArea("Selecciona una acción para crear un elemento.");
        estado.setEditable(false);
        estado.setFocusable(false);
        estado.setOpaque(false);
        estado.setLineWrap(true);
        estado.setWrapStyleWord(true);
        estado.setFont(texto("", 13, GRIS, false).getFont());
        estado.setForeground(GRIS);
        estado.setRows(3);
        estado.setMinimumSize(new Dimension(0, estado.getPreferredSize().height));
        estado.setMaximumSize(new Dimension(Integer.MAX_VALUE, estado.getPreferredSize().height));
        estado.setAlignmentX(LEFT_ALIGNMENT);
        return estado;
    }
    private boolean consultaArchivosPendiente;
    private String firmaArbol = "";
    private final JToggleButton[] botonesPoliticaDisco = new JToggleButton[4];
    private final com.avilaos.disk.DiskSchedulingPolicyType[] politicasDisco = {
        com.avilaos.disk.DiskSchedulingPolicyType.FIFO, com.avilaos.disk.DiskSchedulingPolicyType.SSTF,
        com.avilaos.disk.DiskSchedulingPolicyType.SCAN, com.avilaos.disk.DiskSchedulingPolicyType.C_SCAN
    };
    private boolean[] bloquesOcupados = new boolean[0];
    private boolean[] bloquesReservados = new boolean[0];
    private String[] detallesBloques = new String[0];
    private int cabezalMapa = 0;
    private int limiteDatosMapa = 0;
    private JDialog ventanaMapaAmpliado;
    private Runnable refrescarMapaAmpliado;
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
                int reservados = 0;
                for (int j = inicio; j < fin; j++) if (bloquesOcupados[j]) ocupados++;
                for (int j = inicio; j < fin; j++) if (bloquesReservados[j]) reservados++;
                int y = 8 + i * alto / filas;
                int siguiente = 8 + (i + 1) * alto / filas;
                g.setColor(reservados > 0 ? new Color(216, 178, 100) : ocupados == 0 ? Color.WHITE : ocupados == fin - inicio
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
            int ancho = Math.min(150, Math.max(20, getWidth() - 110));
            if (total == 0 || e.getX() < 40 || e.getX() >= 40 + ancho || e.getY() < 8 || e.getY() >= 8 + alto) return null;
            int filas = Math.min(total, Math.max(1, alto / 5));
            int fila = Math.min(filas - 1, (((e.getY() - 8 + 1) * filas) - 1) / alto);
            int inicio = (int) ((long) fila * total / filas);
            int fin = (int) ((long) (fila + 1) * total / filas);
            int ocupados = 0;
            int reservados = 0;
            for (int i = inicio; i < fin; i++) if (bloquesOcupados[i]) ocupados++;
            for (int i = inicio; i < fin; i++) if (bloquesReservados[i]) reservados++;
            StringBuilder resultado = new StringBuilder("<html>");
            resultado.append(inicio == fin - 1 ? "Bloque " + inicio : "Bloques " + inicio + "–" + (fin - 1));
            resultado.append(": ").append(fin - inicio - ocupados).append(" libres, ")
                    .append(ocupados - reservados).append(" ocupados, ").append(reservados).append(" reservados");
            for (int i = inicio; i < fin; i++) resultado.append("<br>").append(escaparHtml(detallesBloques[i]));
            return resultado.append("</html>").toString();
        }
    };

    private static String escaparHtml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

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
        String[] titulos = {"01   Inicio", "02   Computador", "", "03   Disco", "04   Archivos", "05   Procesos", "06   Simulación", "07   Métricas"}; // [P2] sin Buffers
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
        lateral.add(controlesRelojLateral());
        lateral.add(Box.createVerticalStrut(14));
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
        pantallas.add(archivos(), Destino.ARCHIVOS.name());
        
// [P2-LEGACY]         pantallas.add(buffer(), Destino.BUFFER.name());
        
        editorProceso = new EditorProceso(() -> mostrarPanel(Destino.COMPUTADOR));
        pantallas.add(editorProceso, Destino.PROCESO.name());
        pantallas.add(new VistaSimulacionAlternativa(controller, () -> mostrarPanel(Destino.ARCHIVOS)), Destino.SIMULACION.name());
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
            actualizarControlesReloj();
            metricas.actualizarMetricas();
            actualizarListaComputadores();
            if (destinoActual == Destino.DISCO || (ventanaMapaAmpliado != null && ventanaMapaAmpliado.isVisible())) {
                actualizarVistaDisco();
            }
            if (destinoActual == Destino.ARCHIVOS) actualizarVistaArchivos();
// [P2-LEGACY]             actualizarListaBuffers();
// [P2-LEGACY]             actualizarOpcionesComputadoresBuffer();
        });

        actualizarControlesReloj();
        mostrarPanel(Destino.MENU);
    }

    private JPanel controlesRelojLateral() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(180, 70));
        JLabel titulo = texto("SIMULACIÓN", 10, new Color(192, 181, 148), true);
        titulo.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(titulo);
        panel.add(Box.createVerticalStrut(5));
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        controlReloj.setPreferredSize(new Dimension(48, 34));
        controlReloj.setBorder(BorderFactory.createLineBorder(BORDE));
        avanceReloj.setPreferredSize(new Dimension(48, 34));
        avanceReloj.setBorder(BorderFactory.createLineBorder(BORDE));
        avanceReloj.setMargin(new Insets(0, 0, 0, 0));
        avanceReloj.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        avanceReloj.setToolTipText("Avanzar un ciclo con la simulación pausada");
        avanceReloj.getAccessibleContext().setAccessibleName("Avanzar un ciclo");
        controlReloj.addActionListener(e -> {
            SimulationSnapshot estado = controller.snapshot();
            enviarControlReloj(estado.running && !estado.paused
                    ? controller::pauseSimulation : controller::startSimulation);
        });
        avanceReloj.addActionListener(e -> enviarControlReloj(controller::stepSimulation));
        fila.add(controlReloj);
        fila.add(avanceReloj);
        fila.add(cicloLateral);
        panel.add(fila);
        return panel;
    }

    private void enviarControlReloj(Runnable accion) {
        controlRelojPendiente = true;
        actualizarControlesReloj();
        GuiRequest.model(controller, () -> { accion.run(); return true; }, resultado -> {
            controlRelojPendiente = false;
            actualizarControlesReloj();
        }, error -> {
            controlRelojPendiente = false;
            actualizarControlesReloj();
            JOptionPane.showMessageDialog(this, error.getMessage(), "Simulación", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void actualizarControlesReloj() {
        SimulationSnapshot estado = controller.snapshot();
        boolean activo = estado.running && !estado.paused;
        boolean hayComputador = estado.computers.size() > 0;
        controlReloj.setBackground(activo ? new Color(63, 123, 72) : new Color(159, 64, 58));
        controlReloj.setIcon(iconoEncendido());
        controlReloj.setDisabledIcon(iconoEncendido());
        controlReloj.getAccessibleContext().setAccessibleName(activo ? "Simulación encendida" : "Simulación apagada");
        controlReloj.setToolTipText(!hayComputador ? "Crea un computador primero"
                : activo ? "Pausar simulación" : "Iniciar o reanudar simulación");
        controlReloj.setEnabled(!controlRelojPendiente && (hayComputador || activo));
        avanceReloj.setEnabled(!controlRelojPendiente && hayComputador && !activo);
        cicloLateral.setText("Ciclo " + estado.cycle);
    }

    private static Icon iconoEncendido() {
        return new Icon() {
            @Override public int getIconWidth() { return 20; }
            @Override public int getIconHeight() { return 20; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D dibujo = (Graphics2D) g.create();
                try {
                    dibujo.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    dibujo.setColor(Color.WHITE);
                    dibujo.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    dibujo.drawArc(x + 3, y + 4, 14, 14, 45, -270);
                    dibujo.drawLine(x + 10, y + 1, x + 10, y + 10);
                } finally { dibujo.dispose(); }
            }
        };
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
        if (destino == Destino.ARCHIVOS) actualizarVistaArchivos();
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
        Destino[] destinos = {Destino.COMPUTADOR, Destino.DISCO, Destino.ARCHIVOS, Destino.PROCESO, Destino.SIMULACION};
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
                guardar.addActionListener(e -> guardarSistemaArchivos(guardar));
            } else {
                cargar.addActionListener(e -> cargarProcesosConfiguracion(cargar));
                guardar.setEnabled(false);
            }
            botones.add(cargar);
            botones.add(guardar);
            tarjeta.add(botones);
            tarjeta.add(Box.createVerticalStrut(8));
            tarjeta.add(i == 0 ? estadoCargaArchivos : estadoCargaProcesos);
            opciones.add(tarjeta);
        }
        panel.add(opciones);
        panel.add(Box.createVerticalStrut(10));
        panel.add(texto("El sistema de archivos puede guardarse en JSON.", 12, GRIS, false));
        return panel;
    }

    private void guardarSistemaArchivos(JButton boton) {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            archivo = new java.io.File(archivo.getParentFile(), archivo.getName() + ".json");
        }
        if (archivo.exists() && JOptionPane.showConfirmDialog(this,
                "El archivo ya existe. ¿Deseas reemplazarlo?", "Guardar sistema de archivos",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        aplicarGuardadoSistemaArchivos(archivo, boton);
    }

    void aplicarGuardadoSistemaArchivos(java.io.File archivo, JButton boton) {
        boton.setEnabled(false);
        GuiRequest.model(controller, () -> {
            var disco = controller.getDisk();
            if (disco.getActiveRequest() != null || disco.getQueueSize() != 0) {
                throw new IllegalStateException("Espera a que terminen las solicitudes de disco antes de guardar.");
            }
            controller.saveFileSystemJson(archivo);
            return true;
        }, resultado -> {
            boton.setEnabled(true);
            estadoCargaArchivos.setText("Sistema de archivos guardado correctamente.");
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
    
    

    /** Vista de jerarquía, detalles y tabla de asignación. */
    private JPanel archivos() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(FONDO);
        panel.setBorder(new EmptyBorder(22, 28, 22, 28));
        JPanel encabezado = new JPanel();
        encabezado.setOpaque(false);
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        encabezado.add(texto("ARCHIVOS", 11, ACENTO, true));
        encabezado.add(Box.createVerticalStrut(5));
        encabezado.add(texto("Tus archivos", 28, TINTA, true));
        encabezado.add(Box.createVerticalStrut(5));
        encabezado.add(texto("Explora la estructura y solicita operaciones sobre tus archivos.", 13, GRIS, false));
        panel.add(encabezado, BorderLayout.NORTH);

        JPanel jerarquia = tarjeta();
        jerarquia.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        jerarquia.add(texto("JERARQUÍA", 11, ACENTO, true));
        jerarquia.add(Box.createVerticalStrut(8));
        jerarquia.add(texto("Sistema de archivos", 20, TINTA, true));
        jerarquia.add(Box.createVerticalStrut(12));
        arbolArchivos.setModel(null);
        arbolArchivos.setBackground(PAPEL);
        arbolArchivos.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        arbolArchivos.addTreeSelectionListener(e -> {
            mostrarDetallesArchivo();
            if (!actualizandoArbolArchivos && arbolArchivos.getLastSelectedPathComponent() != null)
                vistasArchivos.setSelectedIndex(0);
        });
        arbolArchivos.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (vistasArchivos.getTabCount() > 0) vistasArchivos.setSelectedIndex(0);
            }
        });
        JScrollPane scrollArbol = new JScrollPane(arbolArchivos);
        scrollArbol.setAlignmentX(LEFT_ALIGNMENT);
        scrollArbol.setPreferredSize(new Dimension(260, 340));
        scrollArbol.setBorder(BorderFactory.createLineBorder(BORDE));
        jerarquia.add(scrollArbol);

        JPanel detalles = tarjeta();
        detalles.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        detalles.add(texto("DETALLES", 11, ACENTO, true));
        detalles.add(Box.createVerticalStrut(8));
        detalles.add(texto("Selecciona un elemento", 20, TINTA, true));
        detalles.add(Box.createVerticalStrut(8));
        detalles.add(texto("Sus propiedades aparecerán aquí.", 13, GRIS, false));
        detalles.add(Box.createVerticalStrut(16));
        String[] campos = {"Nombre", "Ruta", "Tipo", "Dueño", "Permisos", "Tamaño en bloques", "Primer bloque"};
        for (int i = 0; i < campos.length; i++) {
            detallesArchivo[i] = texto(campos[i] + "  ·  —", 13, GRIS, false);
            detalles.add(detallesArchivo[i]);
            detalles.add(Box.createVerticalStrut(6));
        }
        detalles.add(Box.createVerticalStrut(12));
        detalles.add(texto("ACCIONES", 11, ACENTO, true));
        detalles.add(Box.createVerticalStrut(8));
        JPanel acciones = new JPanel(new GridLayout(3, 2, 6, 6));
        acciones.setOpaque(false);
        acciones.setAlignmentX(LEFT_ALIGNMENT);
        acciones.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        for (String nombre : new String[]{"Crear archivo", "Crear directorio", "Leer", "Renombrar", "Eliminar"}) {
            JButton boton = accion(nombre, false);
            if (nombre.equals("Crear archivo") || nombre.equals("Crear directorio")) {
                boolean archivo = nombre.equals("Crear archivo");
                boton.setToolTipText("Solicitar creación mediante un proceso de E/S");
                boton.addActionListener(e -> mostrarFormularioCreacionArchivo(archivo));
            } else if (nombre.equals("Leer")) {
                boton.setToolTipText("Solicitar lectura del archivo seleccionado");
                boton.addActionListener(e -> solicitarLecturaArchivo());
            } else if (nombre.equals("Renombrar")) {
                botonRenombrarArchivo = boton;
                boton.setEnabled(controller.getMode() == com.avilaos.core.model.UserMode.ADMIN);
                boton.setToolTipText("Solicitar cambio de nombre del elemento seleccionado");
                boton.addActionListener(e -> solicitarRenombrado());
            } else if (nombre.equals("Eliminar")) {
                boton.setToolTipText("Solicitar eliminación del elemento seleccionado");
                boton.addActionListener(e -> solicitarEliminacion());
            } else {
                boton.setEnabled(false);
                boton.setToolTipText("Disponible cuando se conecten las operaciones");
            }
            acciones.add(boton);
        }
        detalles.add(acciones);
        detalles.add(Box.createVerticalStrut(12));
        detalles.add(texto("ESTADO DE LA SOLICITUD", 11, ACENTO, true));
        detalles.add(Box.createVerticalStrut(6));
        detalles.add(estadoSolicitudArchivo);

        JPanel reflexion = new JPanel(new BorderLayout(16, 0));
        reflexion.setBackground(PAPEL);
        reflexion.setAlignmentX(LEFT_ALIGNMENT);
        reflexion.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDE), new EmptyBorder(12, 24, 12, 24)));
        reflexion.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        JPanel cita = new JPanel();
        cita.setOpaque(false);
        cita.setLayout(new BoxLayout(cita, BoxLayout.Y_AXIS));
        cita.add(texto("SOBRE EL ÁRBOL", 11, ACENTO, true));
        cita.add(Box.createVerticalStrut(8));
        cita.add(texto("“Aunque sean miles sus partes,", 16, TINTA, true));
        cita.add(texto("todas forman un solo árbol.”", 16, TINTA, true));
        cita.add(Box.createVerticalStrut(6));
        cita.add(texto("Juan Amos Comenio · Didáctica magna 18 · adaptación libre", 12, GRIS, false));
        reflexion.add(cita, BorderLayout.CENTER);
        java.net.URL imagenComenio = getClass().getResource("/imagenes/comenio.png");
        if (imagenComenio != null) {
            Image retrato = new ImageIcon(imagenComenio).getImage().getScaledInstance(88, 90, Image.SCALE_SMOOTH);
            JLabel etiquetaRetrato = new JLabel(new ImageIcon(retrato));
            etiquetaRetrato.setToolTipText("Retrato de Juan Amos Comenio");
            reflexion.add(etiquetaRetrato, BorderLayout.EAST);
        }
        JPanel columnaArbol = new JPanel(new BorderLayout(0, 12));
        columnaArbol.setOpaque(false);
        columnaArbol.add(jerarquia, BorderLayout.CENTER);
        columnaArbol.add(reflexion, BorderLayout.SOUTH);
        JScrollPane scrollDetalles = new JScrollPane(detalles);
        scrollDetalles.setBorder(null);
        scrollDetalles.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollDetalles.getVerticalScrollBar().setUnitIncrement(20);
        JTable tabla = new JTable(tablaAsignacion);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.setFillsViewportHeight(true);
        tabla.setRowHeight(28);
        tabla.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        tabla.setBackground(PAPEL);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] anchos = {155, 75, 110, 115, 180};
        for (int i = 0; i < anchos.length; i++) tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        JScrollPane scrollTabla = new JScrollPane(tabla);
        scrollTabla.setBorder(BorderFactory.createLineBorder(BORDE));
        JPanel panelTabla = new JPanel(new BorderLayout(0, 12));
        panelTabla.setBackground(PAPEL);
        panelTabla.setBorder(new EmptyBorder(20, 20, 20, 20));
        JPanel encabezadoTabla = new JPanel();
        encabezadoTabla.setOpaque(false);
        encabezadoTabla.setLayout(new BoxLayout(encabezadoTabla, BoxLayout.Y_AXIS));
        encabezadoTabla.add(texto("ASIGNACIÓN", 11, ACENTO, true));
        encabezadoTabla.add(Box.createVerticalStrut(8));
        encabezadoTabla.add(texto("Tabla de archivos", 20, TINTA, true));
        encabezadoTabla.add(Box.createVerticalStrut(6));
        encabezadoTabla.add(texto("Archivos de todos los directorios.", 12, GRIS, false));
        panelTabla.add(encabezadoTabla, BorderLayout.NORTH);
        panelTabla.add(scrollTabla, BorderLayout.CENTER);
        vistasArchivos.addTab("Detalles y acciones", scrollDetalles);
        vistasArchivos.addTab("Tabla de archivos", panelTabla);
        vistasArchivos.setBackground(PAPEL);
        vistasArchivos.setForeground(TINTA);
        JPanel contenido = new JPanel(new GridLayout(1, 2, 20, 0));
        contenido.setOpaque(false);
        contenido.add(columnaArbol);
        contenido.add(vistasArchivos);
        panel.add(contenido, BorderLayout.CENTER);
        return panel;
    }

    private static final class ModeloArbolArchivos implements javax.swing.tree.TreeModel {
        private final com.avilaos.filesystem.FSNode raiz;
        ModeloArbolArchivos(com.avilaos.filesystem.FSNode raiz) { this.raiz = raiz; }
        public Object getRoot() { return raiz; }
        public Object getChild(Object parent, int index) { return ((com.avilaos.filesystem.FSNode) parent).getChildren().get(index); }
        public int getChildCount(Object parent) { return ((com.avilaos.filesystem.FSNode) parent).getChildren().size(); }
        public boolean isLeaf(Object node) { return !((com.avilaos.filesystem.FSNode) node).isDirectory(); }
        public void valueForPathChanged(javax.swing.tree.TreePath path, Object value) { }
        public int getIndexOfChild(Object parent, Object child) {
            var hijos = ((com.avilaos.filesystem.FSNode) parent).getChildren();
            for (int i = 0; i < hijos.size(); i++) if (hijos.get(i) == child) return i;
            return -1;
        }
        public void addTreeModelListener(javax.swing.event.TreeModelListener listener) { }
        public void removeTreeModelListener(javax.swing.event.TreeModelListener listener) { }
    }

    private String firmaNodo(com.avilaos.filesystem.FSNode nodo) {
        StringBuilder firma = new StringBuilder();
        firma.append(nodo.getPath()).append('|').append(nodo.getType()).append('|').append(nodo.getOwner())
                .append('|').append(nodo.getPermission()).append('|').append(nodo.getSizeBlocks()).append('|').append(nodo.getFirstBlock())
                .append('|').append(nodo.getCreatorProcess()).append('\n');
        for (int i = 0; i < nodo.getChildren().size(); i++) firma.append(firmaNodo(nodo.getChildren().get(i)));
        return firma.toString();
    }

    private void actualizarVistaArchivos() {
        if (botonRenombrarArchivo != null) {
            boolean admin = controller.getMode() == com.avilaos.core.model.UserMode.ADMIN;
            botonRenombrarArchivo.setEnabled(admin);
            botonRenombrarArchivo.setToolTipText(admin ? "Solicitar cambio de nombre" : "Solo el administrador puede renombrar");
        }
        if (consultaArchivosPendiente) return;
        consultaArchivosPendiente = true;
        var solicitud = solicitudArchivo;
        GuiRequest.model(controller, () -> {
            var fs = controller.getFileSystemManager();
            String estado = null;
            if (solicitud != null) {
                if (solicitud.getIoOperation() == com.avilaos.core.model.IOOperationType.CREATE) {
                    if (solicitud.isCreationCompleted())
                        estado = "Creación completada: " + solicitud.getTargetPath();
                    else if (solicitud.isTerminated()) estado = "Creación fallida; revisa el log.";
                    else if (solicitud.isCreationIssued()) estado = "Esperando disco · bloques reservados.";
                    else estado = "Proceso " + solicitud.getId() + " · esperando CPU.";
                } else if (solicitud.getIoOperation() == com.avilaos.core.model.IOOperationType.DELETE) {
                    if (solicitud.isTerminated())
                        estado = solicitud.isFileOperationCompleted() ? "Eliminación completada: " + solicitud.getTargetPath()
                                : "Eliminación no completada; revisa el log.";
                    else if (solicitud.getState() == com.avilaos.core.model.ProcessState.BLOQUEADO)
                        estado = "Esperando atención del disco para eliminar " + solicitud.getTargetPath();
                    else estado = "Proceso " + solicitud.getId() + " · esperando CPU para eliminación.";
                } else if (solicitud.getIoOperation() == com.avilaos.core.model.IOOperationType.UPDATE) {
                    if (solicitud.isTerminated())
                        estado = solicitud.isFileOperationCompleted() ? "Renombrado completado: " + solicitud.getTargetPath() + " → " + solicitud.getNewFileName()
                                : "Renombrado no completado; revisa el log.";
                    else if (solicitud.getState() == com.avilaos.core.model.ProcessState.BLOQUEADO)
                        estado = "Esperando atención del disco para renombrar " + solicitud.getTargetPath();
                    else estado = "Proceso " + solicitud.getId() + " · esperando CPU para renombrado.";
                } else if (solicitud.getIoOperation() == com.avilaos.core.model.IOOperationType.READ) {
                    if (solicitud.isTerminated())
                        estado = solicitud.isFileOperationCompleted() ? "Lectura completada: " + solicitud.getTargetPath()
                                : "Lectura no completada; revisa el log.";
                    else if (solicitud.getState() == com.avilaos.core.model.ProcessState.BLOQUEADO)
                        estado = "Esperando atención del disco para leer " + solicitud.getTargetPath();
                    else estado = "Proceso " + solicitud.getId() + " · esperando CPU para lectura.";
                }
            }
            return new Object[]{fs.snapshotTree(), estado};
        }, datos -> {
            consultaArchivosPendiente = false;
            var raiz = (com.avilaos.filesystem.FSNode) datos[0];
            if (solicitud == solicitudArchivo && datos[1] != null) estadoSolicitudArchivo.setText((String) datos[1]);
            String nuevaFirma = firmaNodo(raiz);
            if (!nuevaFirma.equals(firmaArbol)) {
                Object anterior = arbolArchivos.getLastSelectedPathComponent();
                String ruta = anterior instanceof com.avilaos.filesystem.FSNode n ? n.getPath() : null;
                firmaArbol = nuevaFirma;
                actualizarTablaAsignacion(raiz);
                actualizandoArbolArchivos = true;
                try {
                arbolArchivos.setModel(new ModeloArbolArchivos(raiz));
                for (int r = 0; r < arbolArchivos.getRowCount(); r++) {
                    arbolArchivos.expandRow(r);
                }
                if (ruta != null) {
                    javax.swing.tree.TreePath seleccion = new javax.swing.tree.TreePath(raiz);
                    com.avilaos.filesystem.FSNode actual = raiz;
                    String[] partes = ruta.split("/");
                    for (String parte : partes) {
                        if (parte.isEmpty()) continue;
                        actual = actual.findChild(parte);
                        if (actual == null) { seleccion = null; break; }
                        seleccion = seleccion.pathByAddingChild(actual);
                    }
                    if (seleccion != null) arbolArchivos.setSelectionPath(seleccion);
                }
                } finally { actualizandoArbolArchivos = false; }
                mostrarDetallesArchivo();
            }
        }, error -> {
            consultaArchivosPendiente = false;
            controller.logEvent("No se pudo consultar el árbol: " + error.getMessage());
        });
    }

    private void actualizarTablaAsignacion(com.avilaos.filesystem.FSNode raiz) {
        tablaAsignacion.setRowCount(0);
        agregarArchivosATabla(raiz);
    }

    private void agregarArchivosATabla(com.avilaos.filesystem.FSNode nodo) {
        if (nodo.isFile()) {
            tablaAsignacion.addRow(new Object[]{nodo.getPath(), nodo.getSizeBlocks(), nodo.getFirstBlock(), nodo.getOwner(), nodo.getCreatorProcess()});
        }
        for (int i = 0; i < nodo.getChildren().size(); i++) agregarArchivosATabla(nodo.getChildren().get(i));
    }

    private void mostrarDetallesArchivo() {
        Object selected = arbolArchivos.getLastSelectedPathComponent();
        var nodo = selected instanceof com.avilaos.filesystem.FSNode n ? n : null;
        String[] nombres = {"Nombre", "Ruta", "Tipo", "Dueño", "Permisos", "Tamaño en bloques", "Primer bloque"};
        String[] valores = nodo == null ? new String[]{"—", "—", "—", "—", "—", "—", "—"} : new String[]{
            nodo.getName().isEmpty() ? "/" : nodo.getName(), nodo.getPath(), nodo.isDirectory() ? "Directorio" : "Archivo",
            nodo.getOwner(), nodo.getPermission().toString(), Integer.toString(nodo.getSizeBlocks()),
            nodo.getFirstBlock() < 0 ? "—" : Integer.toString(nodo.getFirstBlock())};
        for (int i = 0; i < nombres.length; i++) if (detallesArchivo[i] != null) detallesArchivo[i].setText(nombres[i] + "  ·  " + valores[i]);
    }

    private String[] obtenerDirectoriosDisponibles() {
        var fs = controller.getFileSystemManager();
        if (fs == null) return new String[]{"/"};
        var nodos = fs.getAllNodesPreOrder();
        com.avilaos.core.structures.MyLinkedList<String> dirs = new com.avilaos.core.structures.MyLinkedList<>();
        dirs.append("/");
        for (int i = 0; i < nodos.size(); i++) {
            var n = nodos.get(i);
            if (n.isDirectory() && !"/".equals(n.getPath())) {
                dirs.append(n.getPath());
            }
        }
        String[] resultado = new String[dirs.size()];
        for (int i = 0; i < dirs.size(); i++) resultado[i] = dirs.get(i);
        return resultado;
    }

    private void solicitarEliminacion() {
        Object selected = arbolArchivos.getLastSelectedPathComponent();
        var nodo = selected instanceof com.avilaos.filesystem.FSNode n ? n : null;
        if (nodo == null || "/".equals(nodo.getPath())) {
            JOptionPane.showMessageDialog(this, "Selecciona un archivo o directorio distinto de la raíz.", "Eliminar", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String ruta = nodo.getPath();
        String mensaje = "¿Eliminar " + ruta + "?" + (nodo.isDirectory()
                ? "\nSe eliminarán de forma recursiva todos sus archivos y subdirectorios, liberando todos sus bloques en el disco."
                : "\nSe liberarán sus bloques en el disco.");
        if (JOptionPane.showConfirmDialog(this, mensaje, "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        estadoSolicitudArchivo.setText("Enviando solicitud de eliminación…");
        GuiRequest.model(controller, () -> controller.requestDelete(ruta), p -> {
            solicitudArchivo = p;
            estadoSolicitudArchivo.setText("Proceso " + p.getId() + " creado · " + (controller.isRunning() ? "eliminación pendiente de CPU y disco." : "inicia la simulación para que el disco atienda la eliminación."));
            actualizarVistaArchivos();
        }, error -> {
            estadoSolicitudArchivo.setText("Eliminación rechazada: " + error.getMessage());
            JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo eliminar", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void solicitarRenombrado() {
        Object selected = arbolArchivos.getLastSelectedPathComponent();
        var nodo = selected instanceof com.avilaos.filesystem.FSNode n ? n : null;
        if (nodo == null || "/".equals(nodo.getPath())) {
            JOptionPane.showMessageDialog(this, "Selecciona un archivo o directorio distinto de la raíz.", "Renombrar", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String ruta = nodo.getPath();
        String nombre = JOptionPane.showInputDialog(this, "Nuevo nombre para " + ruta + ":", nodo.getName());
        if (nombre == null) return;
        estadoSolicitudArchivo.setText("Enviando solicitud de renombrado…");
        GuiRequest.model(controller, () -> controller.requestRename(ruta, nombre), p -> {
            solicitudArchivo = p;
            estadoSolicitudArchivo.setText("Proceso " + p.getId() + " creado · " + (controller.isRunning() ? "renombrado pendiente de CPU y disco." : "inicia la simulación para que el disco atienda el renombrado."));
            actualizarVistaArchivos();
        }, error -> {
            estadoSolicitudArchivo.setText("Renombrado rechazado: " + error.getMessage());
            JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo renombrar", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void solicitarLecturaArchivo() {
        Object selected = arbolArchivos.getLastSelectedPathComponent();
        var nodo = selected instanceof com.avilaos.filesystem.FSNode n ? n : null;
        if (nodo == null || !nodo.isFile()) {
            JOptionPane.showMessageDialog(this, "Selecciona un archivo para leer.", "Leer archivo", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String ruta = nodo.getPath();
        estadoSolicitudArchivo.setText("Enviando solicitud de lectura…");
        GuiRequest.model(controller, () -> controller.requestRead(ruta), p -> {
            solicitudArchivo = p;
            estadoSolicitudArchivo.setText("Proceso " + p.getId() + " creado · " + (controller.isRunning() ? "lectura pendiente de CPU y disco." : "inicia la simulación para que el disco atienda la lectura."));
            actualizarVistaArchivos();
        }, error -> {
            estadoSolicitudArchivo.setText("Lectura rechazada: " + error.getMessage());
            JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo solicitar la lectura", JOptionPane.ERROR_MESSAGE);
        });
    }

    /** Solicita un proceso; el elemento aparece cuando termina la E/S. */
    private void mostrarFormularioCreacionArchivo(boolean archivo) {
        JPanel formulario = new JPanel();
        formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS));
        formulario.setBackground(PAPEL);
        formulario.setBorder(new EmptyBorder(16, 16, 16, 16));
        formulario.add(texto("SOLICITAR CREACIÓN", 11, ACENTO, true));
        formulario.add(Box.createVerticalStrut(12));
        Object selected = arbolArchivos.getLastSelectedPathComponent();
        var nodo = selected instanceof com.avilaos.filesystem.FSNode n ? n : null;
        String directorioInicial = nodo == null ? "/" : nodo.isDirectory() ? nodo.getPath() : (nodo.getParent() != null ? nodo.getParent().getPath() : "/");

        String[] dirsDisponibles = obtenerDirectoriosDisponibles();
        JComboBox<String> comboDestino = new JComboBox<>(dirsDisponibles);
        comboDestino.setEditable(true);
        comboDestino.setSelectedItem(directorioInicial);

        JTextField campoNombre = new JTextField();
        campoNombre.setToolTipText("Escribe el nombre (ej: video.mp4) o la ruta completa (ej: /usuarios/video.mp4)");

        JTextField dueno = new JTextField(controller.getAccessControl().getActiveUsername());
        dueno.setEditable(controller.getMode() == com.avilaos.core.model.UserMode.ADMIN);
        JSpinner bloques = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
        JComboBox<String> permiso = new JComboBox<>(new String[]{"Público", "Privado"});

        formulario.add(campo("Directorio destino (o selecciona de la lista)", comboDestino));
        formulario.add(Box.createVerticalStrut(10));
        formulario.add(campo(archivo ? "Nombre o ruta completa del archivo (ej: video.mp4 o /usuarios/video.mp4)"
                : "Nombre o ruta completa del directorio (ej: musica o /usuarios/musica)", campoNombre));
        if (archivo) {
            formulario.add(Box.createVerticalStrut(10));
            formulario.add(campo("Tamaño en bloques", bloques));
        }
        formulario.add(Box.createVerticalStrut(10));
        formulario.add(campo("Dueño del elemento (lo asigna ADMIN)", dueno));
        formulario.add(Box.createVerticalStrut(10));
        formulario.add(campo("Permiso", permiso));
        formulario.add(Box.createVerticalStrut(14));
        formulario.add(texto("<html>El nombre del proceso se genera automáticamente.<br>El elemento aparecerá al terminar la E/S en disco.</html>", 12, GRIS, false));

        int respuesta = JOptionPane.showConfirmDialog(this, formulario,
                archivo ? "Crear archivo" : "Crear directorio", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (respuesta != JOptionPane.OK_OPTION) return;

        String input = campoNombre.getText().trim();
        if (input.isEmpty() || input.equals(".") || input.equals("..")) {
            JOptionPane.showMessageDialog(this, "Indica un nombre o ruta válida.", "Datos inválidos", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String base = comboDestino.getSelectedItem() != null ? comboDestino.getSelectedItem().toString().trim() : "/";
        if (base.isEmpty() || !base.startsWith("/")) base = "/";

        String ruta;
        if (input.startsWith("/")) {
            ruta = input;
        } else if (input.contains("/")) {
            while (base.length() > 1 && base.endsWith("/")) base = base.substring(0, base.length() - 1);
            ruta = (base.equals("/") ? "" : base) + "/" + input;
        } else {
            while (base.length() > 1 && base.endsWith("/")) base = base.substring(0, base.length() - 1);
            ruta = (base.equals("/") ? "" : base) + "/" + input;
        }

        ruta = ruta.replaceAll("/+", "/");
        if (ruta.length() > 1 && ruta.endsWith("/")) {
            ruta = ruta.substring(0, ruta.length() - 1);
        }

        int lastSlash = ruta.lastIndexOf('/');
        String dirPadre = lastSlash == 0 ? "/" : ruta.substring(0, lastSlash);
        String nombreFinal = ruta.substring(lastSlash + 1);

        if (nombreFinal.isEmpty() || nombreFinal.equals(".") || nombreFinal.equals("..")) {
            JOptionPane.showMessageDialog(this, "El nombre del elemento no es válido.", "Datos inválidos", JOptionPane.ERROR_MESSAGE);
            return;
        }

        var nodoPadre = controller.getFileSystemManager().findNode(dirPadre);
        if (nodoPadre == null || !nodoPadre.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "El directorio destino '" + dirPadre + "' no existe en el sistema de archivos.\n"
                    + "Crea primero el directorio o selecciónalo del menú desplegable.",
                    "Directorio no encontrado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final String rutaFinal = ruta;
        final int cantidad;
        try {
            cantidad = archivo ? EditorProceso.numeroVisible(bloques) : 1;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Indica un tamaño entero entre 1 y 500 bloques.",
                    "Tamaño inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String propietario = dueno.getText().trim();
        var tipo = archivo ? com.avilaos.filesystem.FsNodeType.FILE : com.avilaos.filesystem.FsNodeType.DIRECTORY;
        var acceso = permiso.getSelectedIndex() == 0 ? com.avilaos.filesystem.FilePermission.PUBLIC : com.avilaos.filesystem.FilePermission.PRIVATE;
        estadoSolicitudArchivo.setText("Enviando solicitud…");
        GuiRequest.model(controller, () -> controller.requestCreation(rutaFinal, tipo, cantidad, propietario, acceso), p -> {
            solicitudArchivo = p;
            estadoSolicitudArchivo.setText("Proceso " + p.getId() + " creado · " + (controller.isRunning() ? "pendiente de CPU y disco." : "inicia la simulación para que el disco atienda la creación."));
            actualizarVistaArchivos();
        }, error -> {
            estadoSolicitudArchivo.setText("Solicitud rechazada.");
            JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo crear el proceso", JOptionPane.ERROR_MESSAGE);
        });
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
        configuracion.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
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
        configuracion.add(Box.createVerticalStrut(6));
        configuracion.add(discoMetricas);
        configuracion.add(Box.createVerticalStrut(12));
        
        // --- NUEVA SECCIÓN: CONFIGURACIÓN DE TAMAÑO ---
        configuracion.add(texto("CONFIGURACIÓN DE CAPACIDAD", 11, ACENTO, true));
        configuracion.add(Box.createVerticalStrut(6));
        
        JPanel panelTamanos = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panelTamanos.setOpaque(false);
        panelTamanos.setAlignmentX(LEFT_ALIGNMENT);
        
        panelTamanos.add(texto("Total disco:", 12, TINTA, false));
        panelTamanos.add(spinBloques);
        
        panelTamanos.add(texto("Swap:", 12, TINTA, false));
        panelTamanos.add(spinSwap);
        
        btnAplicarTamanoDisco = new JButton("Aplicar");
        btnAplicarTamanoDisco.setToolTipText("Redimensionar capacidad conservando archivos existentes (requiere ADMIN antes de iniciar la simulación)");
        btnAplicarTamanoDisco.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        btnAplicarTamanoDisco.addActionListener(e -> {
            final int total;
            final int swap;
            try {
                total = EditorProceso.numeroVisible(spinBloques);
                swap = EditorProceso.numeroVisible(spinSwap);
                spinBloques.setValue(total);
                spinSwap.setValue(swap);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "Indica valores enteros válidos (Total: 100–500, Swap: 10–200).",
                        "Capacidad inválida", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (total < 100 || total > 500) {
                JOptionPane.showMessageDialog(this, "El tamaño total del disco debe estar entre 100 y 500 bloques.",
                        "Capacidad inválida", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (swap < 10 || swap > 200) {
                JOptionPane.showMessageDialog(this, "El área de swap debe tener entre 10 y 200 bloques.",
                        "Swap inválido", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (swap >= total) {
                JOptionPane.showMessageDialog(this, "El área de swap (" + swap + ") debe ser menor que el total del disco (" + total + ").",
                        "Swap inválido", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int dataBlocks = total - swap;
            if (dataBlocks < 20) {
                JOptionPane.showMessageDialog(this, "El disco debe tener al menos 20 bloques para datos (quedarían " + dataBlocks + ").",
                        "Datos insuficientes", JOptionPane.ERROR_MESSAGE);
                return;
            }
            cambiarCapacidadDisco(dataBlocks, swap);
        });
        panelTamanos.add(btnAplicarTamanoDisco);
        configuracion.add(panelTamanos);
        configuracion.add(Box.createVerticalStrut(4));
        configuracion.add(lblPreviewCapacidadDisco);
        configuracion.add(Box.createVerticalStrut(4));
        configuracion.add(lblEstadoCapacidadDisco);
        configuracion.add(Box.createVerticalStrut(12));

        Runnable actualizarPreview = () -> {
            try {
                int tot = EditorProceso.numeroVisible(spinBloques);
                int sw = EditorProceso.numeroVisible(spinSwap);
                int dat = tot - sw;
                lblPreviewCapacidadDisco.setText("Datos: " + dat + " · Swap: " + sw + " · Total físico: " + tot);
            } catch (Exception ignored) { }
        };
        spinBloques.addChangeListener(e -> actualizarPreview.run());
        spinSwap.addChangeListener(e -> actualizarPreview.run());
        JPanel panelVelocidad = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panelVelocidad.setOpaque(false);
        panelVelocidad.setAlignmentX(LEFT_ALIGNMENT);
        panelVelocidad.add(texto("Velocidad del cabezal (bloques/ciclo):", 12, TINTA, false));
        JSpinner velocidad = new JSpinner(new SpinnerNumberModel(10, 1, 500, 1));
        panelVelocidad.add(velocidad);
        JButton aplicarVelocidad = new JButton("Aplicar velocidad");
        aplicarVelocidad.addActionListener(e -> {
            final int valor;
            try {
                valor = EditorProceso.numeroVisible(velocidad);
                velocidad.setValue(valor);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "Indica una velocidad entera entre 1 y 500.", "Velocidad inválida", JOptionPane.ERROR_MESSAGE);
                return;
            }
            GuiRequest.model(controller, () -> { controller.setDiskHeadSpeed(valor); return true; },
                    ok -> actualizarVistaDisco(), error -> {
                        String motivo = error.getMessage() == null ? error.toString() : error.getMessage();
                        controller.logEvent("No se pudo cambiar la velocidad: " + motivo);
                        JOptionPane.showMessageDialog(this, motivo, "No se pudo cambiar la velocidad", JOptionPane.ERROR_MESSAGE);
                    });
        });
        panelVelocidad.add(aplicarVelocidad);
        configuracion.add(panelVelocidad);
        configuracion.add(Box.createVerticalStrut(12));
        // ----------------------------------------------

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
        columnaIzquierda.add(Box.createVerticalStrut(16));

        JPanel tarjetaColaDisco = tarjeta();
        tarjetaColaDisco.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        tarjetaColaDisco.add(texto("COLA ÚNICA DE E/S DEL DISCO", 11, ACENTO, true));
        tarjetaColaDisco.add(Box.createVerticalStrut(6));
        tarjetaColaDisco.add(texto("Solicitud en atención y solicitudes en espera", 17, TINTA, true));
        tarjetaColaDisco.add(Box.createVerticalStrut(4));
        tarjetaColaDisco.add(texto("Ordenadas en tiempo real según la política activa.", 12, GRIS, false));
        tarjetaColaDisco.add(Box.createVerticalStrut(8));
        JTable tColaDisco = new JTable(tablaColaDisco);
        tColaDisco.setRowHeight(26);
        tColaDisco.setBackground(PAPEL);
        tColaDisco.setForeground(TINTA);
        tColaDisco.setFillsViewportHeight(true);
        JScrollPane scrollColaDisco = new JScrollPane(tColaDisco);
        scrollColaDisco.setAlignmentX(LEFT_ALIGNMENT);
        scrollColaDisco.setBorder(BorderFactory.createLineBorder(BORDE));
        scrollColaDisco.setPreferredSize(new Dimension(480, 125));
        scrollColaDisco.setMaximumSize(new Dimension(Integer.MAX_VALUE, 145));
        tarjetaColaDisco.add(scrollColaDisco);
        columnaIzquierda.add(tarjetaColaDisco);
        columnaIzquierda.add(Box.createVerticalStrut(16));
        
        JPanel mapa = tarjeta();
        mapa.setMinimumSize(new Dimension(0, 0));
        mapa.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        mapa.add(texto("OCUPACIÓN", 11, ACENTO, true));
        mapa.add(Box.createVerticalStrut(8));
        mapa.add(texto("Mapa del disco", 20, TINTA, true));
        mapa.add(Box.createVerticalStrut(6));
        mapa.add(texto("Blanco: libre · Oscuro: ocupado", 12, GRIS, false));
        mapa.add(texto("Dorado: incluye reservas pendientes", 12, GRIS, false));
        mapa.add(texto("Verde claro: rango mixto", 12, GRIS, false));
        mapa.add(texto("Rayado: área de swap", 12, GRIS, false));
        mapa.add(texto("Haz clic en una sección para inspeccionarla.", 12, GRIS, false));
        mapa.add(Box.createVerticalStrut(6));
        JButton btnAmpliarMapa = accion("Ampliar mapa ⤢", true);
        btnAmpliarMapa.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        btnAmpliarMapa.setBorder(new EmptyBorder(4, 10, 4, 10));
        btnAmpliarMapa.setFocusPainted(false);
        btnAmpliarMapa.setAlignmentX(LEFT_ALIGNMENT);
        btnAmpliarMapa.setPreferredSize(new Dimension(128, 25));
        btnAmpliarMapa.setMaximumSize(new Dimension(128, 25));
        btnAmpliarMapa.setToolTipText("Abrir el mapa vertical del disco en una ventana grande interactiva");
        btnAmpliarMapa.addActionListener(e -> abrirVentanaMapaAmpliado());
        mapa.add(btnAmpliarMapa);
        mapa.add(Box.createVerticalStrut(8));
        mapaDisco.setBackground(PAPEL);
        mapaDisco.setAlignmentX(LEFT_ALIGNMENT);
        mapaDisco.setPreferredSize(new Dimension(360, 380));
        mapaDisco.setMaximumSize(new Dimension(Integer.MAX_VALUE, 380));
        JPopupMenu detalleBloquePopup = new JPopupMenu();
        JLabel detalleBloque = new JLabel();
        detalleBloque.setBorder(new EmptyBorder(10, 12, 10, 12));
        detalleBloquePopup.add(detalleBloque);
        mapaDisco.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                String detalle = mapaDisco.getToolTipText(e);
                if (detalle == null) return;
                detalleBloque.setText(detalle);
                detalleBloquePopup.show(mapaDisco, e.getX(), e.getY());
            }
        });
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
boolean[] reservados = new boolean[ocupados.length];
String[] detalles = new String[ocupados.length];
for (int i = 0; i < ocupados.length; i++) {
    var bloque = disco.getBlock(i);
    ocupados[i] = bloque.isOccupied();
    reservados[i] = bloque.isReserved();
    String zona = bloque.isSwapBlock() ? "swap" : "datos";
    if (!ocupados[i]) {
        detalles[i] = i + " · " + zona + " · libre";
    } else {
        String titular = bloque.getOccupiedBy() == null ? "sin identificador" : bloque.getOccupiedBy();
        String estado = reservados[i] ? "reservado para " : bloque.isSwapBlock() ? "swap · " : "ocupado por ";
        String proceso = bloque.getOwnerProcessId() >= 0 ? " · proceso ID " + bloque.getOwnerProcessId() : "";
        String siguiente = bloque.getNextBlock() >= 0 ? " · siguiente " + bloque.getNextBlock() : " · fin de cadena";
        detalles[i] = i + " · " + estado + titular + proceso + siguiente;
    }
}
    String[] resumen = new String[]{
    controller.isFileSystemJsonLoaded() ? "Sistema de archivos cargado desde JSON" : "Disco inicial del simulador; no se ha cargado un JSON",
    "Bloques de datos: " + datos + " · Swap: " + swap + " · Total físico: " + disco.getTotalPhysicalBlocks(),
    "Datos libres: " + disco.getFreeDataBlocks() + " / " + datos + " · Swap libre: " + disco.getFreeSwapBlocks() + " / " + swap,
    "Cabezal: bloque " + disco.getHeadPosition() + " · Dirección: " + (disco.getHeadDirection() == com.avilaos.disk.HeadDirection.UP ? "creciente ↑" : "decreciente ↓"),
    "Política: " + disco.getPolicyType() + " · Velocidad: " + disco.getHeadSpeedPerTick() + " posiciones por ciclo",
    "Métricas: Desplazamiento cabezal " + disco.getTotalHeadMovement() + " bloques · Espera prom. " + String.format("%.2f", disco.getAverageWaitTimeCycles()) + " ticks · Atendidas: " + disco.getCompletedRequestsCount() + " (" + disco.getDetallePorPolitica() + ")"
};

    var activa = disco.getActiveRequest();
    var cola = disco.getQueueSnapshot();
    int totalSol = (activa != null ? 1 : 0) + (cola != null ? cola.size() : 0);
    Object[][] filasCola = new Object[totalSol][7];
    int idxSol = 0;
    if (activa != null) {
        String proc = activa.getPcb() != null ? activa.getPcb().getName() + " (ID " + activa.getPcb().getId() + ")" : "Sistema";
        filasCola[idxSol++] = new Object[]{"En atención", activa.getId(), proc, activa.getOperation(),
                activa.getPath() != null ? activa.getPath() : "—", activa.getTargetBlock(), activa.getBlockCount()};
    }
    if (cola != null) {
        for (int i = 0; i < cola.size(); i++) {
            var r = cola.get(i);
            if (r == null) continue;
            String proc = r.getPcb() != null ? r.getPcb().getName() + " (ID " + r.getPcb().getId() + ")" : "Sistema";
            filasCola[idxSol++] = new Object[]{"En cola #" + (i + 1), r.getId(), proc, r.getOperation(),
                    r.getPath() != null ? r.getPath() : "—", r.getTargetBlock(), r.getBlockCount()};
        }
    }

return new Object[]{resumen, ocupados, disco.getHeadPosition(), datos, disco.getPolicyType(), reservados, detalles, filasCola, swap};

}, datos -> {

consultaDiscoPendiente = false;

String[] resumen = (String[]) datos[0];

bloquesOcupados = (boolean[]) datos[1];
bloquesReservados = (boolean[]) datos[5];
detallesBloques = (String[]) datos[6];
Object[][] filasCola = (Object[][]) datos[7];
int datosActuales = (Integer) datos[3];
int swapActual = (Integer) datos[8];
int totalFisico = datosActuales + swapActual;
if (!((JSpinner.DefaultEditor) spinBloques.getEditor()).getTextField().hasFocus()
        && totalFisico >= 100 && totalFisico <= 500) {
    spinBloques.setValue(totalFisico);
}
if (!((JSpinner.DefaultEditor) spinSwap.getEditor()).getTextField().hasFocus()
        && swapActual >= 10 && swapActual <= 200) {
    spinSwap.setValue(swapActual);
}
lblPreviewCapacidadDisco.setText("Datos: " + datosActuales + " · Swap: " + swapActual + " · Total físico: " + totalFisico);

boolean puedeConfigurar = !controller.isRunning() && controller.getAccessControl().isAdmin();
spinBloques.setEnabled(puedeConfigurar);
spinSwap.setEnabled(puedeConfigurar);
if (btnAplicarTamanoDisco != null) btnAplicarTamanoDisco.setEnabled(puedeConfigurar);

if (controller.isRunning()) {
    lblEstadoCapacidadDisco.setText("Cambio de capacidad deshabilitado durante la ejecución.");
} else if (!controller.getAccessControl().isAdmin()) {
    lblEstadoCapacidadDisco.setText("Solo el Administrador puede reconfigurar la capacidad del disco.");
} else {
    lblEstadoCapacidadDisco.setText("Configura la capacidad antes de iniciar la simulación.");
}
tablaColaDisco.setRowCount(0);
for (int i = 0; i < filasCola.length; i++) {
    if (filasCola[i][0] != null) tablaColaDisco.addRow(filasCola[i]);
}

cabezalMapa = (Integer) datos[2];

limiteDatosMapa = (Integer) datos[3];

discoOrigen.setText(resumen[0]);

discoCapacidad.setText(resumen[1]);

discoOcupacion.setText(resumen[2]);

discoCabezal.setText(resumen[3]);

discoPolitica.setText(resumen[4]);

discoMetricas.setText(resumen[5]);

for (int i = 0; i < botonesPoliticaDisco.length; i++) {

JToggleButton boton = botonesPoliticaDisco[i];

if (boton == null) continue;

boolean seleccionada = politicasDisco[i] == datos[4];

boton.setSelected(seleccionada);

boton.setBackground(seleccionada ? ACENTO : PAPEL);

boton.setForeground(seleccionada ? Color.WHITE : TINTA);

}

mapaDisco.repaint();
if (refrescarMapaAmpliado != null && ventanaMapaAmpliado != null && ventanaMapaAmpliado.isVisible()) {
    refrescarMapaAmpliado.run();
}

}, error -> {

consultaDiscoPendiente = false;

discoOrigen.setText("No se pudo consultar el disco: " + error.getMessage());

});

}

    private void abrirVentanaMapaAmpliado() {
        if (ventanaMapaAmpliado != null) {
            ventanaMapaAmpliado.setVisible(true);
            ventanaMapaAmpliado.toFront();
            if (refrescarMapaAmpliado != null) refrescarMapaAmpliado.run();
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        ventanaMapaAmpliado = new JDialog(owner, "Mapa del Disco Ampliado · ÁvilaOS", Dialog.ModalityType.MODELESS);
        ventanaMapaAmpliado.setSize(980, 720);
        ventanaMapaAmpliado.setMinimumSize(new Dimension(780, 520));
        ventanaMapaAmpliado.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(16, 14));
        root.setBackground(FONDO);
        root.setBorder(new EmptyBorder(18, 22, 18, 22));

        // --- Cabecera con estado en vivo y controles de zoom / cabezal ---
        JPanel cabecera = tarjeta();
        cabecera.setLayout(new BorderLayout(12, 8));
        JPanel infoSuperior = new JPanel();
        infoSuperior.setOpaque(false);
        infoSuperior.setLayout(new BoxLayout(infoSuperior, BoxLayout.Y_AXIS));
        JLabel lblTitulo = texto("MAPA VERTICAL AMPLIADO DEL DISCO", 12, ACENTO, true);
        JLabel lblResumenCabezal = texto("Cabezal: bloque " + cabezalMapa, 16, TINTA, true);
        JLabel lblResumenOcupacion = texto(discoOcupacion.getText(), 13, GRIS, false);
        infoSuperior.add(lblTitulo);
        infoSuperior.add(Box.createVerticalStrut(4));
        infoSuperior.add(lblResumenCabezal);
        infoSuperior.add(Box.createVerticalStrut(2));
        infoSuperior.add(lblResumenOcupacion);

        JPanel controlesZoom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controlesZoom.setOpaque(false);
        controlesZoom.add(texto("Agrupación:", 12, GRIS, false));
        JComboBox<String> comboAgrupacion = new JComboBox<>(new String[]{
            "Automática (~80 franjas grandes)",
            "4 bloques por franja",
            "2 bloques por franja",
            "1 bloque por franja (Detalle 1:1)"
        });
        JButton btnIrCabezal = accion("Centrar en Cabezal", false);
        controlesZoom.add(comboAgrupacion);
        controlesZoom.add(btnIrCabezal);

        cabecera.add(infoSuperior, BorderLayout.WEST);
        cabecera.add(controlesZoom, BorderLayout.EAST);
        root.add(cabecera, BorderLayout.NORTH);

        // --- Panel derecho de inspección fija de la franja seleccionada ---
        JPanel panelInspeccion = tarjeta();
        panelInspeccion.setLayout(new BorderLayout(0, 10));
        panelInspeccion.setPreferredSize(new Dimension(380, 0));
        JPanel encInspeccion = new JPanel();
        encInspeccion.setOpaque(false);
        encInspeccion.setLayout(new BoxLayout(encInspeccion, BoxLayout.Y_AXIS));
        encInspeccion.add(texto("INSPECCIÓN DE FRANJA", 11, ACENTO, true));
        encInspeccion.add(Box.createVerticalStrut(4));
        JLabel lblRangoSeleccionado = texto("Haz clic en cualquier franja", 17, TINTA, true);
        JLabel lblConteoRango = texto("Selecciona una barra a la izquierda para ver cada bloque.", 12, GRIS, false);
        encInspeccion.add(lblRangoSeleccionado);
        encInspeccion.add(Box.createVerticalStrut(2));
        encInspeccion.add(lblConteoRango);
        panelInspeccion.add(encInspeccion, BorderLayout.NORTH);

        DefaultTableModel modeloDetalleFranja = new DefaultTableModel(
                new String[]{"Bloque", "Zona", "Detalle / Propietario / Cadena"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tablaDetalleFranja = new JTable(modeloDetalleFranja);
        tablaDetalleFranja.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tablaDetalleFranja.setFillsViewportHeight(true);
        tablaDetalleFranja.setRowHeight(26);
        tablaDetalleFranja.setBackground(PAPEL);
        tablaDetalleFranja.setForeground(TINTA);
        tablaDetalleFranja.getColumnModel().getColumn(0).setPreferredWidth(58);
        tablaDetalleFranja.getColumnModel().getColumn(1).setPreferredWidth(62);
        tablaDetalleFranja.getColumnModel().getColumn(2).setPreferredWidth(420);
        JScrollPane scrollTablaDetalle = new JScrollPane(
                tablaDetalleFranja,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollTablaDetalle.setBorder(BorderFactory.createLineBorder(BORDE));
        panelInspeccion.add(scrollTablaDetalle, BorderLayout.CENTER);

        // --- Lienzo vertical ampliado con franjas grandes ---
        final int ALTO_FRANJA = 24;
        final int[] franjaHover = {-1};
        final int[] franjaSeleccionada = {0};

        class LienzoMapaGrande extends JPanel {
            int calcularFilas() {
                int total = bloquesOcupados.length;
                if (total == 0) return 1;
                int modo = comboAgrupacion.getSelectedIndex();
                return switch (modo) {
                    case 1 -> Math.max(1, (total + 3) / 4);
                    case 2 -> Math.max(1, (total + 1) / 2);
                    case 3 -> total;
                    default -> Math.min(total, 80);
                };
            }

            void actualizarTablaInspeccion() {
                int total = bloquesOcupados.length;
                if (total == 0) return;
                int filas = calcularFilas();
                int fila = Math.max(0, Math.min(filas - 1, franjaSeleccionada[0]));
                int inicio = (int) ((long) fila * total / filas);
                int fin = Math.max(inicio + 1, (int) ((long) (fila + 1) * total / filas));
                int ocupados = 0;
                int reservados = 0;
                for (int i = inicio; i < fin; i++) {
                    if (bloquesOcupados[i]) ocupados++;
                    if (bloquesReservados[i]) reservados++;
                }
                lblRangoSeleccionado.setText(inicio == fin - 1
                        ? "Bloque " + inicio
                        : "Bloques " + inicio + " – " + (fin - 1));
                lblConteoRango.setText((fin - inicio - ocupados) + " libres · "
                        + (ocupados - reservados) + " ocupados · " + reservados + " reservados");
                modeloDetalleFranja.setRowCount(0);
                FontMetrics fm = tablaDetalleFranja.getFontMetrics(tablaDetalleFranja.getFont());
                int maxAnchoDetalle = 360;
                for (int i = inicio; i < fin && i < detallesBloques.length; i++) {
                    String zona = i >= limiteDatosMapa ? "Swap" : "Datos";
                    String det = detallesBloques[i];
                    int sep = det.indexOf(" · ");
                    String resumenBloque = sep >= 0 ? det.substring(sep + 3) : det;
                    if (fm != null) {
                        maxAnchoDetalle = Math.max(maxAnchoDetalle, fm.stringWidth(resumenBloque) + 28);
                    }
                    modeloDetalleFranja.addRow(new Object[]{i, zona, resumenBloque});
                }
                tablaDetalleFranja.getColumnModel().getColumn(2).setPreferredWidth(maxAnchoDetalle);
            }

            @Override
            public Dimension getPreferredSize() {
                int filas = calcularFilas();
                return new Dimension(500, Math.max(400, 24 + filas * ALTO_FRANJA));
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int total = bloquesOcupados.length;
                if (total == 0) return;

                int filas = calcularFilas();
                int x = 86;
                int ancho = Math.max(220, getWidth() - 250);

                for (int i = 0; i < filas; i++) {
                    int inicio = (int) ((long) i * total / filas);
                    int fin = Math.max(inicio + 1, (int) ((long) (i + 1) * total / filas));
                    int ocupados = 0;
                    int reservados = 0;
                    for (int j = inicio; j < fin; j++) {
                        if (bloquesOcupados[j]) ocupados++;
                        if (bloquesReservados[j]) reservados++;
                    }
                    int y = 12 + i * ALTO_FRANJA;
                    int h = ALTO_FRANJA - 2;

                    // Color base de la franja
                    Color relleno = reservados > 0
                            ? new Color(216, 178, 100)
                            : ocupados == 0
                                    ? Color.WHITE
                                    : ocupados == (fin - inicio)
                                            ? new Color(38, 90, 83)
                                            : new Color(155, 190, 172);
                    g2.setColor(relleno);
                    g2.fillRect(x, y, ancho, h);

                    // Sombreado diagonal si pertenece al área de Swap
                    if (fin > limiteDatosMapa) {
                        Graphics sombreado = g2.create(x, y, ancho, h);
                        sombreado.setColor(new Color(156, 142, 107, 150));
                        for (int linea = -h; linea < ancho + h; linea += 8) {
                            sombreado.drawLine(linea, h, linea + h, 0);
                        }
                        sombreado.dispose();
                    }

                    // Etiqueta del rango a la izquierda de cada franja
                    g2.setColor(TINTA);
                    g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
                    String etqRango = inicio == fin - 1
                            ? String.format("[%03d]", inicio)
                            : String.format("%03d–%03d", inicio, fin - 1);
                    g2.drawString(etqRango, 12, y + 15);

                    // Texto de resumen dentro de la barra
                    String resumenInterno;
                    if (inicio >= limiteDatosMapa) {
                        resumenInterno = ocupados == 0 ? "SWAP libre" : "SWAP (" + ocupados + "/" + (fin - inicio) + " ocup.)";
                    } else if (ocupados == 0) {
                        resumenInterno = "Libre";
                    } else if (fin - inicio == 1 && inicio < detallesBloques.length) {
                        resumenInterno = detallesBloques[inicio];
                    } else {
                        resumenInterno = ocupados + "/" + (fin - inicio) + " bloques ocupados";
                    }
                    g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                    g2.setColor(ocupados == (fin - inicio) && reservados == 0 ? Color.WHITE : TINTA);
                    g2.drawString(resumenInterno, x + 10, y + 15);

                    // Borde normal, hover o seleccionada
                    boolean tieneCabezal = (cabezalMapa >= inicio && cabezalMapa < fin);
                    if (tieneCabezal) {
                        g2.setColor(new Color(195, 38, 34));
                        g2.setStroke(new BasicStroke(2.6f));
                        g2.drawRect(x - 2, y - 1, ancho + 4, h + 2);
                        g2.setStroke(new BasicStroke(1f));
                        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                        g2.drawString("◄ CABEZAL (" + cabezalMapa + ")", x + ancho + 10, y + 16);
                    } else if (i == franjaSeleccionada[0]) {
                        g2.setColor(ACENTO);
                        g2.setStroke(new BasicStroke(2.2f));
                        g2.drawRect(x - 1, y, ancho + 2, h);
                        g2.setStroke(new BasicStroke(1f));
                    } else if (i == franjaHover[0]) {
                        g2.setColor(new Color(100, 130, 85));
                        g2.setStroke(new BasicStroke(1.6f));
                        g2.drawRect(x, y, ancho, h);
                        g2.setStroke(new BasicStroke(1f));
                    } else {
                        g2.setColor(BORDE);
                        g2.drawRect(x, y, ancho, h);
                    }
                }
            }
        }

        LienzoMapaGrande lienzo = new LienzoMapaGrande();
        lienzo.setBackground(PAPEL);
        JScrollPane scrollLienzo = new JScrollPane(lienzo);
        scrollLienzo.setBorder(BorderFactory.createLineBorder(BORDE));
        scrollLienzo.getVerticalScrollBar().setUnitIncrement(ALTO_FRANJA * 2);

        java.awt.event.MouseAdapter raton = new java.awt.event.MouseAdapter() {
            private int filaEnY(int y) {
                int filas = lienzo.calcularFilas();
                int idx = (y - 12) / ALTO_FRANJA;
                if (idx < 0 || idx >= filas) return -1;
                return idx;
            }
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                int f = filaEnY(e.getY());
                if (f != franjaHover[0]) {
                    franjaHover[0] = f;
                    lienzo.repaint();
                }
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                franjaHover[0] = -1;
                lienzo.repaint();
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                int f = filaEnY(e.getY());
                if (f >= 0) {
                    franjaSeleccionada[0] = f;
                    lienzo.actualizarTablaInspeccion();
                    lienzo.repaint();
                }
            }
        };
        lienzo.addMouseListener(raton);
        lienzo.addMouseMotionListener(raton);

        Runnable centrarCabezal = () -> {
            int total = Math.max(1, bloquesOcupados.length);
            int filas = lienzo.calcularFilas();
            int filaCabezal = Math.min(filas - 1, Math.max(0, (int) ((long) cabezalMapa * filas / total)));
            franjaSeleccionada[0] = filaCabezal;
            lienzo.actualizarTablaInspeccion();
            int yObjetivo = Math.max(0, 12 + filaCabezal * ALTO_FRANJA - scrollLienzo.getViewport().getHeight() / 2);
            scrollLienzo.getVerticalScrollBar().setValue(yObjetivo);
            lienzo.repaint();
        };

        comboAgrupacion.addActionListener(e -> {
            lienzo.revalidate();
            centrarCabezal.run();
        });
        btnIrCabezal.addActionListener(e -> centrarCabezal.run());

        refrescarMapaAmpliado = () -> {
            lblResumenCabezal.setText(discoCabezal.getText().trim().isEmpty()
                    ? "Cabezal: bloque " + cabezalMapa
                    : discoCabezal.getText());
            lblResumenOcupacion.setText(discoOcupacion.getText() + "   |   " + discoCapacidad.getText());
            lienzo.revalidate();
            lienzo.actualizarTablaInspeccion();
            lienzo.repaint();
        };

        JSplitPane divisor = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollLienzo, panelInspeccion);
        divisor.setOpaque(false);
        divisor.setBorder(null);
        divisor.setContinuousLayout(true);
        divisor.setOneTouchExpandable(true);
        divisor.setDividerSize(8);
        divisor.setResizeWeight(0.54);
        root.add(divisor, BorderLayout.CENTER);

        ventanaMapaAmpliado.setContentPane(root);
        ventanaMapaAmpliado.setVisible(true);
        SwingUtilities.invokeLater(centrarCabezal);
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
    
    private void cambiarCapacidadDisco(int nuevosBloques, int nuevoSwap) {
        GuiRequest.model(controller, () -> {
            controller.logEvent("Solicitud de cambio de capacidad: " + nuevosBloques + " bloques, " + nuevoSwap + " swap");
            controller.reconfigurarDisco(nuevosBloques, nuevoSwap, true);
            return true;
        }, ok -> {
            actualizarVistaDisco();
            actualizarVistaArchivos();
            JOptionPane.showMessageDialog(this, "Capacidad del disco actualizada conservando los datos.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
        }, error -> {
            String motivo = error.getMessage() == null ? error.toString() : error.getMessage();
            controller.logEvent("Error al cambiar capacidad: " + motivo);
            JOptionPane.showMessageDialog(this, motivo, "No se pudo cambiar la capacidad", JOptionPane.ERROR_MESSAGE);
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
