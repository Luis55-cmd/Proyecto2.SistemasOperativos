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
    public enum Destino { MENU, COMPUTADOR, BUFFER, PROCESO, SIMULACION, METRICAS }
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
    private final EditorProceso editorProceso;
    private final PanelMetricasCopia metricas;
    private final SimulationController controller = SimulationController.getInstance();
    
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
        String[] titulos = {"01   Inicio", "02   Computador", "", "03   Procesos", "04   Simulación", "05   Métricas"}; // [P2] sin Buffers
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
        pantallas.add(inicio(), Destino.MENU.name());
        pantallas.add(computador(), Destino.COMPUTADOR.name());
        
// [P2-LEGACY]         pantallas.add(buffer(), Destino.BUFFER.name());
        
        editorProceso = new EditorProceso(() -> mostrarPanel(Destino.COMPUTADOR));
        pantallas.add(editorProceso, Destino.PROCESO.name());
        pantallas.add(new VistaSimulacionAlternativa(controller), Destino.SIMULACION.name());
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
        for (Destino item : Destino.values()) {
            JButton boton = navegacion[item.ordinal()];
            if (boton == null) continue; // [P2] BUFFER oculto
            boton.setBackground(item == destino ? ACENTO : TINTA);
            boton.setForeground(item == destino ? Color.WHITE : new Color(225, 220, 201));
        }
    }

    public Destino getDestinoActual() { return destinoActual; }

    private JPanel inicio() {
        JPanel panel = pagina("TU ESPACIO DE TRABAJO", "Construye tu simulación", "Configura los computadores y compón los programas que van a ejecutar.");
        JPanel bienvenida = tarjeta();
        bienvenida.add(texto("Un clúster. Muchas posibilidades.", 24, TINTA, true));
        bienvenida.add(Box.createVerticalStrut(12));
        bienvenida.add(texto("Comienza por los recursos; después define el trabajo.", 15, GRIS, false));
        panel.add(bienvenida);
        panel.add(Box.createVerticalStrut(24));
        
        JPanel opciones = new JPanel(new GridLayout(1, 3, 20, 0));
        opciones.setOpaque(false);
        opciones.setAlignmentX(LEFT_ALIGNMENT);
        opciones.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        
        JPanel equipos = tarjeta();
        equipos.add(texto("01 / COMPUTADORES", 11, ACENTO, true));
        equipos.add(Box.createVerticalStrut(14));
        equipos.add(texto("Define recursos", 21, TINTA, true));
        equipos.add(Box.createVerticalStrut(8));
        equipos.add(texto("Memoria y política.", 13, GRIS, false));
        equipos.add(Box.createVerticalStrut(22));
        JButton equipo = accion("Configurar  →", true);
        equipo.addActionListener(e -> mostrarPanel(Destino.COMPUTADOR));
        equipos.add(equipo);
        
// [P2-LEGACY]         JPanel buffers = tarjeta();
// [P2-LEGACY]         buffers.add(texto("02 / BUFFERS", 11, ACENTO, true));
// [P2-LEGACY]         buffers.add(Box.createVerticalStrut(14));
// [P2-LEGACY]         buffers.add(texto("Sincronización", 21, TINTA, true));
// [P2-LEGACY]         buffers.add(Box.createVerticalStrut(8));
// [P2-LEGACY]         buffers.add(texto("Espacios de memoria.", 13, GRIS, false));
// [P2-LEGACY]         buffers.add(Box.createVerticalStrut(22));
// [P2-LEGACY]         JButton btnBuffer = accion("Crear buffer  →", true);
// [P2-LEGACY]         btnBuffer.addActionListener(e -> mostrarPanel(Destino.BUFFER));
// [P2-LEGACY]         buffers.add(btnBuffer);
        
        JPanel programas = tarjeta();
        programas.add(texto("03 / PROCESOS", 11, ACENTO, true));
        programas.add(Box.createVerticalStrut(14));
        programas.add(texto("Diseña programas", 21, TINTA, true));
        programas.add(Box.createVerticalStrut(8));
        programas.add(texto("Bloques de CPU y E/S.", 13, GRIS, false));
        programas.add(Box.createVerticalStrut(22));
        JButton proceso = accion("Borrador  →", true);
        proceso.addActionListener(e -> mostrarPanel(Destino.PROCESO));
        programas.add(proceso);
        
// [P2-LEGACY]         opciones.add(equipos); opciones.add(buffers); opciones.add(programas);
        opciones.add(equipos); opciones.add(programas); // [P2] sin tarjeta de buffers
        panel.add(opciones);
        panel.add(Box.createVerticalStrut(24));
        return panel;
    }

    private JPanel computador() {
        JPanel panel = pagina("COMPUTADORES", "Configura un computador", "Cada computador tendrá su propia CPU, memoria y política.");
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
        panel.add(tarjeta);
        panel.add(Box.createVerticalStrut(20));
        JTable tabla = new JTable(computadoresVisibles);
        tabla.setRowHeight(32);
        tabla.setBackground(PAPEL);
        tabla.setForeground(TINTA);
        JScrollPane lista = new JScrollPane(tabla);
        lista.setColumnHeaderView(tabla.getTableHeader());
        lista.setAlignmentX(LEFT_ALIGNMENT);
        lista.setPreferredSize(new Dimension(600, 160));
        panel.add(lista);
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
        computadoresVisibles.setRowCount(0);
        for (int i = 0; i < view.computers.size(); i++) {
            var c = view.computers.get(i);
            String polStr = (c.getPolitica() == SchedulingPolicyType.ROUND_ROBIN)
                    ? "Round Robin (Q: " + c.getQuantum() + ")"
                    : c.getPolitica().toString();
            computadoresVisibles.addRow(new Object[]{c.getId(), c.getName(),
                    c.getRam().getTotalSize(), polStr});
        }
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
