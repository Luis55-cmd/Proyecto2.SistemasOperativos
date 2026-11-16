package com.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.ParseException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.ProcessSizing;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.ExecutionMode;
import com.avilaos.simulation.SimulationController;
import static com.avilaos.gui.PanelPrincipal.*;

/** Formulario de creación de procesos según los parámetros utilizados por el simulador. */
public class EditorProceso extends JPanel {
    private final JTextField campoNombre = new JTextField();
    private final JComboBox<DestinoComputador> destino = new JComboBox<>();
    private final JComboBox<ProcessType> comboTipoProceso = new JComboBox<>(new ProcessType[] {
            ProcessType.CPU_BOUND, ProcessType.IO_BOUND, ProcessType.FILE_IO });
    private final JSpinner spinnerPrioridad = numero(1, 1, 10);
    private final JSpinner spinnerDeadline = numero(20, 1, 100000);
    private final JTextField memoriaCalculada = new JTextField();
    private final JLabel detalleCalculo = texto(" ", 12, GRIS, false);
    private final JSpinner spinnerInstrucciones = numero(10, 1, 1000000);
    private final JSpinner spinnerIoInterval = numero(3, 1, 10000);
    private final JSpinner spinnerIoDuration = numero(2, 1, 10000);
    private final JComboBox<String> comboBufferId = new JComboBox<>();
    private final JSpinner spinnerCiclosOperacion = numero(1, 1, 1000);
    private final JSpinner spinnerElementos = numero(1, 1, 1000);
    // FILE_IO fields
    private final JComboBox<IOOperationType> comboOperacion = new JComboBox<>(new IOOperationType[] {
            IOOperationType.CREATE, IOOperationType.READ, IOOperationType.UPDATE, IOOperationType.DELETE });
    private final JTextField campoRuta = new JTextField("/");
    private final JSpinner spinnerTamanoBloques = numero(1, 1, 500);
    private final JTextField campoDueno = new JTextField("admin");
    private final JComboBox<ExecutionMode> comboModo = new JComboBox<>(ExecutionMode.values());
    private final JTextField campoNuevoNombre = new JTextField();

    private final JPanel panelInstrucciones = new JPanel(new GridLayout(1, 2, 24, 0));
    private final JPanel panelIO = new JPanel(new GridLayout(1, 2, 24, 0));
    private final JPanel panelSync = new JPanel(new GridLayout(1, 3, 24, 0));
    private final JPanel panelFileIO = new JPanel();
    private final JButton crearComputador = accion("Crea un computador primero →", true);
    private final JButton botonCrear = accion("Crear proceso", true);
    private final JLabel mensaje = texto("Completa los datos para crear un proceso.", 12, GRIS, false);
    private final DefaultTableModel procesosVisibles = new DefaultTableModel(
            new String[]{"ID", "Nombre", "Computador", "Tipo", "Estado", "RAM requerida", "Prioridad", "Deadline", "Instrucciones", "Configuración"}, 0) {
        @Override public boolean isCellEditable(int fila, int columna) { return false; }
    };

    private record DestinoComputador(int id, String nombre) {
        @Override public String toString() { return id == -1 ? nombre : nombre + " · ID " + id; }
    }

    private static JSpinner numero(int valor, int minimo, int maximo) {
        return new JSpinner(new SpinnerNumberModel(valor, minimo, maximo, 1));
    }

    public EditorProceso(Runnable irAComputadores) {
        super(new BorderLayout());
        JPanel pagina = pagina("PROCESOS", "Crear proceso", "Configura los datos del proceso y selecciona su computador.");
        JPanel formulario = tarjeta();
        formulario.setMaximumSize(new Dimension(Integer.MAX_VALUE, 560));
        formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS));
        formulario.add(texto("Propiedades del proceso", 20, TINTA, true));
        formulario.add(Box.createVerticalStrut(16));

        JPanel identidad = new JPanel(new GridLayout(1, 3, 24, 0));
        identidad.setOpaque(false);
        identidad.setAlignmentX(LEFT_ALIGNMENT);
        identidad.setMaximumSize(new Dimension(900, 62));
        identidad.add(campo("Nombre del proceso", campoNombre));
        identidad.add(campo("Computador destino", destino));
        identidad.add(campo("Tipo de proceso", comboTipoProceso));
        formulario.add(identidad);
        formulario.add(Box.createVerticalStrut(20));

        JPanel recursos = new JPanel(new GridLayout(1, 3, 24, 0));
        recursos.setOpaque(false);
        recursos.setAlignmentX(LEFT_ALIGNMENT);
        recursos.setMaximumSize(new Dimension(900, 62));
        recursos.add(campo("Prioridad · 1 baja / 10 alta", spinnerPrioridad));
        recursos.add(campo("Deadline (ticks)", spinnerDeadline));
        memoriaCalculada.setEditable(false);
        memoriaCalculada.setName("memoriaCalculada");
        recursos.add(campo("Memoria calculada · unidades", memoriaCalculada));
        formulario.add(recursos);
        detalleCalculo.setAlignmentX(LEFT_ALIGNMENT);
        formulario.add(Box.createVerticalStrut(6));
        formulario.add(detalleCalculo);
        formulario.add(Box.createVerticalStrut(20));

        panelInstrucciones.setOpaque(false);
        panelInstrucciones.setAlignmentX(LEFT_ALIGNMENT);
        panelInstrucciones.setMaximumSize(new Dimension(600, 62));
        panelInstrucciones.add(campo("Instrucciones de trabajo CPU", spinnerInstrucciones));
        panelInstrucciones.add(new JLabel());
        formulario.add(panelInstrucciones);
        panelIO.setOpaque(false);
        panelIO.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 0, 0, 0));
        panelIO.setAlignmentX(LEFT_ALIGNMENT);
        panelIO.setMaximumSize(new Dimension(600, 74));
        panelIO.add(campo("Ciclos de CPU entre operaciones de E/S", spinnerIoInterval));
        panelIO.add(campo("Duración del bloqueo de E/S (ticks)", spinnerIoDuration));
        formulario.add(panelIO);

        // ── Panel FILE_IO ──
        panelFileIO.setOpaque(false);
        panelFileIO.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 0, 0, 0));
        panelFileIO.setAlignmentX(LEFT_ALIGNMENT);
        panelFileIO.setMaximumSize(new Dimension(900, 150));
        panelFileIO.setLayout(new BoxLayout(panelFileIO, BoxLayout.Y_AXIS));

        JPanel fila1FileIO = new JPanel(new GridLayout(1, 3, 24, 0));
        fila1FileIO.setOpaque(false);
        fila1FileIO.setMaximumSize(new Dimension(900, 62));
        fila1FileIO.add(campo("Operación CRUD", comboOperacion));
        campoRuta.setName("campoRuta");
        fila1FileIO.add(campo("Ruta del archivo (targetPath)", campoRuta));
        fila1FileIO.add(campo("Tamaño en bloques (CREATE)", spinnerTamanoBloques));
        panelFileIO.add(fila1FileIO);
        panelFileIO.add(Box.createVerticalStrut(10));

        JPanel fila2FileIO = new JPanel(new GridLayout(1, 3, 24, 0));
        fila2FileIO.setOpaque(false);
        fila2FileIO.setMaximumSize(new Dimension(900, 62));
        campoDueno.setName("campoDueno");
        fila2FileIO.add(campo("Dueño del proceso", campoDueno));
        fila2FileIO.add(campo("Modo de ejecución", comboModo));
        campoNuevoNombre.setName("campoNuevoNombre");
        fila2FileIO.add(campo("Nuevo nombre (UPDATE)", campoNuevoNombre));
        panelFileIO.add(fila2FileIO);
        formulario.add(panelFileIO);

        formulario.add(Box.createVerticalStrut(24));

        crearComputador.addActionListener(e -> irAComputadores.run());
        formulario.add(crearComputador);
        botonCrear.addActionListener(e -> generarPCB());
        formulario.add(botonCrear);
        formulario.add(Box.createVerticalStrut(10));
        mensaje.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        formulario.add(mensaje);
        pagina.add(formulario);
        pagina.add(Box.createVerticalStrut(20));
        JTable tabla = new JTable(procesosVisibles);
        tabla.setName("procesosCreados");
        tabla.setRowHeight(32);
        tabla.setBackground(PAPEL);
        tabla.setForeground(TINTA);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] anchos = {45, 120, 130, 100, 95, 110, 75, 85, 105, 220};
        for (int i = 0; i < anchos.length; i++) tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        JScrollPane lista = new JScrollPane(tabla);
        lista.setColumnHeaderView(tabla.getTableHeader());
        lista.setAlignmentX(LEFT_ALIGNMENT);
        lista.setPreferredSize(new Dimension(600, 160));
        lista.getViewport().setBackground(PAPEL);
        pagina.add(lista);
        add(pagina);

        comboTipoProceso.addActionListener(e -> { actualizarCampos(); actualizarMemoria(); });
        comboOperacion.addActionListener(e -> actualizarCamposFileIO());
        for (JSpinner spinner : new JSpinner[]{spinnerInstrucciones, spinnerIoInterval, spinnerIoDuration,
                spinnerCiclosOperacion, spinnerElementos, spinnerTamanoBloques}) {
            spinner.addChangeListener(e -> actualizarMemoria());
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { actualizarMemoria(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { actualizarMemoria(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { actualizarMemoria(); }
            });
        }
        actualizarCampos();
        actualizarMemoria();
        SimulationController.getInstance().addGUIListener(this::actualizarListas);
        actualizarListas();
    }

    /** Lee el texto visible para actualizar el cálculo también antes de perder el foco. */
    static int numeroVisible(JSpinner spinner) {
        String text = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText().trim();
        if (!text.matches("-?\\d+") && !text.matches("-?\\d{1,3}([.,\\u00a0]\\d{3})+")) throw new IllegalArgumentException("Valor numérico inválido");
        java.text.NumberFormat formato = java.text.NumberFormat.getIntegerInstance();
        java.text.ParsePosition posicion = new java.text.ParsePosition(0);
        Number numero = formato.parse(text, posicion);
        if (numero == null || posicion.getIndex() != text.length()) throw new IllegalArgumentException();
        long valor = numero.longValue();
        SpinnerNumberModel modelo = (SpinnerNumberModel) spinner.getModel();
        if (valor < ((Number) modelo.getMinimum()).longValue() || valor > ((Number) modelo.getMaximum()).longValue())
            throw new IllegalArgumentException("Valor fuera de rango");
        return (int) valor;
    }

    private void actualizarMemoria() {
        try {
            ProcessType tipo = (ProcessType) comboTipoProceso.getSelectedItem();
            if (tipo == ProcessType.FILE_IO) {
                IOOperationType op = (IOOperationType) comboOperacion.getSelectedItem();
                int bloques = op == IOOperationType.CREATE ? numeroVisible(spinnerTamanoBloques) : 1;
                memoriaCalculada.setText(Integer.toString(bloques));
                detalleCalculo.setText("Total: " + bloques + " bloques · 1 bloque = 1 unidad de RAM");
                return;
            }
            boolean sync = tipo == ProcessType.PRODUCER || tipo == ProcessType.CONSUMER;
            int trabajo = sync ? 1 : numeroVisible(spinnerInstrucciones);
            int intervalo = tipo == ProcessType.IO_BOUND ? numeroVisible(spinnerIoInterval) : 0;
            if (tipo == ProcessType.IO_BOUND) numeroVisible(spinnerIoDuration);
            int ciclos = sync ? numeroVisible(spinnerCiclosOperacion) : 1;
            int elementos = sync ? numeroVisible(spinnerElementos) : 1;
            int total = ProcessSizing.instructions(tipo, trabajo, intervalo, ciclos, elementos);
            memoriaCalculada.setText(Integer.toString(total));
            detalleCalculo.setText("Total: " + total + " instrucciones · 1 instrucción = 1 unidad de RAM");
        } catch (IllegalArgumentException | ArithmeticException error) {
            memoriaCalculada.setText("—");
            detalleCalculo.setText("Completa una configuración válida para calcular la memoria.");
        }
    }

    private void actualizarCampos() {
        ProcessType tipo = (ProcessType) comboTipoProceso.getSelectedItem();
        boolean sincronizado = tipo == ProcessType.PRODUCER || tipo == ProcessType.CONSUMER;
        boolean esFileIO = tipo == ProcessType.FILE_IO;
        panelInstrucciones.setVisible(!sincronizado && !esFileIO);
        panelIO.setVisible(tipo == ProcessType.IO_BOUND);
        panelSync.setVisible(sincronizado);
        panelFileIO.setVisible(esFileIO);
        if (esFileIO) actualizarCamposFileIO();
        revalidate();
        repaint();
    }

    /** Habilita / deshabilita campos específicos según la operación FILE_IO seleccionada. */
    private void actualizarCamposFileIO() {
        IOOperationType op = (IOOperationType) comboOperacion.getSelectedItem();
        spinnerTamanoBloques.setEnabled(op == IOOperationType.CREATE);
        campoNuevoNombre.setEnabled(op == IOOperationType.UPDATE);
        actualizarMemoria();
    }

    /** Actualiza los destinos sin perder la selección ni los datos del formulario. */
    public void actualizarListas() {
        var view = SimulationController.getInstance().snapshot();
        Integer seleccionado = getComputadorDestinoId();
        destino.removeAllItems();
        for (int i = 0; i < view.computers.size(); i++) {
            var c = view.computers.get(i);
            DestinoComputador opcion = new DestinoComputador(c.getId(), c.getName());
            destino.addItem(opcion);
            if (seleccionado != null && seleccionado == c.getId()) destino.setSelectedItem(opcion);
        }
        if (view.computers.size() > 0) {
            DestinoComputador automatico = new DestinoComputador(-1, "⚡ Automático (Sender-Initiated)");
            destino.addItem(automatico);
            if (seleccionado != null && seleccionado == -1) destino.setSelectedItem(automatico);
        }
        boolean hayComputadores = destino.getItemCount() > 0;
        destino.setEnabled(hayComputadores);
        crearComputador.setVisible(!hayComputadores);

        actualizarProcesos(view);
    }

    private void actualizarProcesos(com.avilaos.simulation.SimulationSnapshot view) {
        procesosVisibles.setRowCount(0);
        for (int i = 0; i < view.processes.size(); i++) {
            var fila = view.processes.get(i);
            var p = fila.process();
            String configuracion = switch (p.type()) {
                case IO_BOUND -> "E/S cada " + p.ioInterval() + " · duración " + p.ioDuration();
                case FILE_IO -> {
                    String op = p.ioOperation() != null ? p.ioOperation().name() : "?";
                    String ruta = p.targetPath() != null ? p.targetPath() : "?";
                    String extra = p.ioOperation() == IOOperationType.CREATE ? " · " + p.fileSizeBlocks() + " bloques" : "";
                    yield op + " " + ruta + extra;
                }
                case PRODUCER, CONSUMER -> "Buffer " + p.targetBufferId() + " · " + p.cyclesPerOperation()
                        + " ciclos · " + p.requiredElements() + " elementos";
                default -> "—";
            };
            procesosVisibles.addRow(new Object[]{p.id(), p.name(), fila.computerName(), p.type(), fila.state(),
                    p.assignedMemory(), p.priority(), p.deadline(), p.totalInstructions(), configuracion});
        }
    }

    public Integer getComputadorDestinoId() {
        DestinoComputador seleccionado = (DestinoComputador) destino.getSelectedItem();
        return seleccionado == null ? null : seleccionado.id();
    }

    public void generarPCB() {
        String nombre = campoNombre.getText().trim();
        Integer computador = getComputadorDestinoId();
        ProcessType tipo = (ProcessType) comboTipoProceso.getSelectedItem();
        boolean sincronizado = tipo == ProcessType.PRODUCER || tipo == ProcessType.CONSUMER;
        boolean esFileIO = tipo == ProcessType.FILE_IO;

        try {
            spinnerPrioridad.setValue(numeroVisible(spinnerPrioridad));
            spinnerDeadline.setValue(numeroVisible(spinnerDeadline));
            if (esFileIO) {
                IOOperationType op = (IOOperationType) comboOperacion.getSelectedItem();
                if (op == IOOperationType.CREATE) spinnerTamanoBloques.setValue(numeroVisible(spinnerTamanoBloques));
            } else if (sincronizado) {
                spinnerCiclosOperacion.setValue(numeroVisible(spinnerCiclosOperacion));
                spinnerElementos.setValue(numeroVisible(spinnerElementos));
            } else {
                spinnerInstrucciones.setValue(numeroVisible(spinnerInstrucciones));
                if (tipo == ProcessType.IO_BOUND) {
                    spinnerIoInterval.setValue(numeroVisible(spinnerIoInterval));
                    spinnerIoDuration.setValue(numeroVisible(spinnerIoDuration));
                }
            }
        } catch (IllegalArgumentException error) {
            mensaje.setText("Revisa los valores numéricos del proceso.");
            return;
        }
        if (nombre.isEmpty()) { mensaje.setText("Escribe un nombre para el proceso."); return; }
        if (computador == null) { mensaje.setText("Selecciona un computador destino."); return; }

        if (esFileIO) {
            IOOperationType op = (IOOperationType) comboOperacion.getSelectedItem();
            String ruta = campoRuta.getText().trim();
            String dueno = campoDueno.getText().trim();
            ExecutionMode modo = (ExecutionMode) comboModo.getSelectedItem();
            String nuevoNombre = campoNuevoNombre.getText().trim();
            int bloques = op == IOOperationType.CREATE ? (Integer) spinnerTamanoBloques.getValue() : 0;

            if (!ruta.startsWith("/")) { mensaje.setText("La ruta debe comenzar con '/' (ej: /home/user/archivo.txt)."); return; }
            if (op == IOOperationType.CREATE && bloques < 1) { mensaje.setText("El tamaño debe ser al menos 1 bloque para CREATE."); return; }
            if (op == IOOperationType.UPDATE && nuevoNombre.isEmpty()) { mensaje.setText("Especifica el nuevo nombre para la operación UPDATE."); return; }

            int prioridad = (Integer) spinnerPrioridad.getValue();
            int deadline = (Integer) spinnerDeadline.getValue();
            final int computadorFinal = computador;
            final int bloquesFinal = bloques;
            final String duenoCorrecto = dueno.isEmpty() ? "admin" : dueno;

            SimulationController controller = SimulationController.getInstance();
            botonCrear.setEnabled(false);
            mensaje.setText("Creando proceso FILE_IO…");
            GuiRequest.model(controller, () -> {
                var pcb = controller.createConfigFileIOProcess(nombre, computadorFinal,
                        op, ruta, bloquesFinal, duenoCorrecto, modo, nuevoNombre, prioridad, deadline);
                if (pcb == null) return "No se pudo crear el proceso FILE_IO; revisa los parámetros y el log.";
                return "Proceso FILE_IO '" + nombre + "' creado: " + pcb.getState() + " · op=" + op + " · ruta=" + ruta;
            }, resultado -> { mensaje.setText(resultado); botonCrear.setEnabled(true); },
               error -> { mensaje.setText(error.getMessage()); botonCrear.setEnabled(true); });
            return;
        }

        String buffer = (String) comboBufferId.getSelectedItem();
        if (sincronizado && buffer == null) { mensaje.setText("Crea y selecciona un buffer primero."); return; }

        int prioridad = (Integer) spinnerPrioridad.getValue();
        int deadline = (Integer) spinnerDeadline.getValue();
        int ciclos = (Integer) spinnerCiclosOperacion.getValue();
        int elementos = (Integer) spinnerElementos.getValue();
        int instrucciones = sincronizado ? 1 : (Integer) spinnerInstrucciones.getValue();
        int intervaloIO = tipo == ProcessType.IO_BOUND ? (Integer) spinnerIoInterval.getValue() : 0;
        int duracionIO = tipo == ProcessType.IO_BOUND ? (Integer) spinnerIoDuration.getValue() : 0;
        if (tipo == ProcessType.IO_BOUND && instrucciones < 2) {
            mensaje.setText("Un proceso I/O bound necesita al menos 2 instrucciones para solicitar E/S.");
            return;
        }
        if (tipo == ProcessType.IO_BOUND && intervaloIO >= instrucciones) {
            mensaje.setText("El intervalo entre operaciones de E/S debe ser menor que el total de instrucciones.");
            return;
        }
        SimulationController controller = SimulationController.getInstance();
        botonCrear.setEnabled(false);
        mensaje.setText("Creando proceso…");
        GuiRequest.model(controller, () -> {
            var pcb = controller.createConfiguredProcess(nombre, computador, tipo, instrucciones, prioridad, deadline,
                    intervaloIO, duracionIO, buffer, ciclos, elementos);
            if (pcb == null) return "No se pudo crear el proceso; revisa los parámetros y el log.";
            return "Proceso '" + nombre + "' creado: " + pcb.getState() + " en computador " + pcb.getComputerId();
        }, resultado -> { mensaje.setText(resultado); botonCrear.setEnabled(true); },
           error -> { mensaje.setText(error.getMessage()); botonCrear.setEnabled(true); });
    }
}
