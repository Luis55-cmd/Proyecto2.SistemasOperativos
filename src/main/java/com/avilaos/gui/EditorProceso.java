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
            ProcessType.CPU_BOUND, ProcessType.FILE_IO });
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
    private final JComboBox<IOOperationType> comboOperacion = new JComboBox<>(new IOOperationType[] { IOOperationType.READ });
    private final JComboBox<String> archivosLectura = new JComboBox<>();
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

    private static final class PaginaProcesos extends JPanel implements javax.swing.Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle rect, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle rect, int orientation, int direction) { return Math.max(20, rect.height - 20); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    public EditorProceso(Runnable irAComputadores) {
        super(new BorderLayout());
        JPanel pagina = new PaginaProcesos();
        pagina.setLayout(new BoxLayout(pagina, BoxLayout.Y_AXIS));
        pagina.setBackground(FONDO);
        pagina.setBorder(javax.swing.BorderFactory.createEmptyBorder(24, 32, 28, 32));
        JPanel encabezado = new JPanel(new BorderLayout(20, 0));
        encabezado.setOpaque(false);
        encabezado.setAlignmentX(LEFT_ALIGNMENT);
        encabezado.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
        JPanel titulos = new JPanel();
        titulos.setOpaque(false);
        titulos.setLayout(new BoxLayout(titulos, BoxLayout.Y_AXIS));
        titulos.add(texto("PROCESOS", 11, ACENTO, true));
        titulos.add(Box.createVerticalStrut(8));
        titulos.add(texto("Crear proceso", 28, TINTA, true));
        titulos.add(Box.createVerticalStrut(6));
        titulos.add(texto("Configura su trabajo y sus recursos.", 13, GRIS, false));
        encabezado.add(titulos, BorderLayout.CENTER);
        JPanel reflexion = new JPanel();
        reflexion.setLayout(new BoxLayout(reflexion, BoxLayout.Y_AXIS));
        reflexion.setBackground(PAPEL);
        reflexion.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(BORDE),
                javax.swing.BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        reflexion.add(texto("SOBRE LAS ACCIONES", 10, ACENTO, true));
        reflexion.add(Box.createVerticalStrut(5));
        reflexion.add(texto("<html>“En cada acción, considera lo que<br>la precede y lo que la sigue.”</html>", 14, TINTA, true));
        reflexion.add(Box.createVerticalStrut(4));
        reflexion.add(texto("Epicteto · Enquiridión 29 · traducción libre", 11, GRIS, false));
        JPanel tarjetaCita = new JPanel(new BorderLayout(8, 0));
        tarjetaCita.setBackground(PAPEL);
        tarjetaCita.setBorder(javax.swing.BorderFactory.createLineBorder(BORDE));
        reflexion.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 14, 10, 0));
        tarjetaCita.add(reflexion, BorderLayout.CENTER);
        var imagenEpicteto = getClass().getResource("/imagenes/epicteto.png");
        if (imagenEpicteto != null) {
            var retrato = new javax.swing.ImageIcon(new javax.swing.ImageIcon(imagenEpicteto).getImage()
                    .getScaledInstance(80, 80, java.awt.Image.SCALE_SMOOTH));
            JLabel imagen = new JLabel(retrato);
            imagen.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 0, 4, 10));
            imagen.setToolTipText("Ilustración de un filósofo");
            tarjetaCita.add(imagen, BorderLayout.EAST);
        }
        encabezado.add(tarjetaCita, BorderLayout.EAST);
        pagina.add(encabezado);
        pagina.add(Box.createVerticalStrut(18));
        JPanel formulario = tarjeta();
        formulario.setMaximumSize(new Dimension(Integer.MAX_VALUE, 460));
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
        formulario.add(Box.createVerticalStrut(16));

        panelInstrucciones.setOpaque(false);
        panelInstrucciones.setAlignmentX(LEFT_ALIGNMENT);
        panelInstrucciones.setMaximumSize(new Dimension(600, 62));
        panelInstrucciones.add(campo("Instrucciones de trabajo CPU", spinnerInstrucciones));
        panelInstrucciones.add(new JLabel());
        formulario.add(panelInstrucciones);
        // ── Panel FILE_IO ──
        panelFileIO.setOpaque(false);
        panelFileIO.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 0, 0, 0));
        panelFileIO.setAlignmentX(LEFT_ALIGNMENT);
        panelFileIO.setMaximumSize(new Dimension(900, 150));
        panelFileIO.setLayout(new BoxLayout(panelFileIO, BoxLayout.Y_AXIS));

        JPanel fila1FileIO = new JPanel(new GridLayout(1, 3, 24, 0));
        fila1FileIO.setOpaque(false);
        fila1FileIO.setMaximumSize(new Dimension(900, 62));
        archivosLectura.setName("archivosLectura");
        fila1FileIO.add(campo("Archivo que leerá el proceso", archivosLectura));
        JButton actualizarArchivos = accion("Actualizar archivos", false);
        actualizarArchivos.addActionListener(e -> actualizarArchivosLectura());
        fila1FileIO.add(actualizarArchivos);
        panelFileIO.add(fila1FileIO);
        panelFileIO.add(Box.createVerticalStrut(10));

        formulario.add(panelFileIO);
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
        formulario.add(Box.createVerticalStrut(12));

        crearComputador.addActionListener(e -> irAComputadores.run());
        formulario.add(crearComputador);
        botonCrear.addActionListener(e -> generarPCB());
        formulario.add(botonCrear);
        formulario.add(Box.createVerticalStrut(10));
        mensaje.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        formulario.add(mensaje);
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
        lista.setPreferredSize(new Dimension(380, 200));
        lista.getViewport().setBackground(PAPEL);
        JPanel tarjetaLista = tarjeta();
        tarjetaLista.setMaximumSize(new Dimension(Integer.MAX_VALUE, 310));
        tarjetaLista.add(texto("Procesos creados", 20, TINTA, true));
        tarjetaLista.add(Box.createVerticalStrut(12));
        lista.setPreferredSize(new Dimension(100, 180));
        lista.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        tarjetaLista.add(lista);
        pagina.add(formulario);
        pagina.add(Box.createVerticalStrut(16));
        pagina.add(tarjetaLista);
        JScrollPane scroll = new JScrollPane(pagina);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll);

        comboTipoProceso.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> lista, Object valor, int indice, boolean seleccionado, boolean foco) {
                String etiqueta = valor == ProcessType.CPU_BOUND ? "CPU-bound" : valor == ProcessType.FILE_IO ? "E/S-bound · lectura de archivo" : String.valueOf(valor);
                return super.getListCellRendererComponent(lista, etiqueta, indice, seleccionado, foco);
            }
        });
        comboTipoProceso.addActionListener(e -> { actualizarCampos(); actualizarMemoria(); if (comboTipoProceso.getSelectedItem() == ProcessType.FILE_IO) actualizarArchivosLectura(); });
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
                int bloques = op == IOOperationType.CREATE ? numeroVisible(spinnerTamanoBloques) : ProcessSizing.fileReadInstructions();
                memoriaCalculada.setText(Integer.toString(bloques));
                detalleCalculo.setText("Lectura: 1 instrucción de solicitud + 1 de finalización · 2 unidades de RAM; la espera depende del disco");
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

    private void actualizarArchivosLectura() {
        var controller = SimulationController.getInstance();
        String anterior = (String) archivosLectura.getSelectedItem();
        GuiRequest.model(controller, () -> {
            var archivos = controller.getFileSystemManager().getAllFiles();
            String[] rutas = new String[archivos.size()];
            for (int i = 0; i < archivos.size(); i++) rutas[i] = archivos.get(i).getPath();
            return rutas;
        }, rutas -> {
            archivosLectura.removeAllItems();
            for (String ruta : rutas) archivosLectura.addItem(ruta);
            if (anterior != null) archivosLectura.setSelectedItem(anterior);
            if (archivosLectura.getSelectedIndex() < 0 && rutas.length > 0) archivosLectura.setSelectedIndex(0);
            if (rutas.length == 0) mensaje.setText("No hay archivos disponibles. Carga o crea uno en Archivos.");
        }, error -> mensaje.setText("No se pudieron consultar los archivos: " + error.getMessage()));
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
        botonCrear.setVisible(hayComputadores);

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
            procesosVisibles.addRow(new Object[]{p.id(), p.name(), fila.computerName(), p.type() == ProcessType.FILE_IO ? "E/S-bound" : p.type() == ProcessType.CPU_BOUND ? "CPU-bound" : p.type().toString(), fila.state(),
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
            String ruta = (String) archivosLectura.getSelectedItem();
            String nuevoNombre = "";
            int bloques = op == IOOperationType.CREATE ? (Integer) spinnerTamanoBloques.getValue() : 0;

            if (ruta == null) { mensaje.setText("Selecciona un archivo existente para leer."); return; }
            if (op == IOOperationType.CREATE && bloques < 1) { mensaje.setText("El tamaño debe ser al menos 1 bloque para CREATE."); return; }
            if (op == IOOperationType.UPDATE && nuevoNombre.isEmpty()) { mensaje.setText("Especifica el nuevo nombre para la operación UPDATE."); return; }

            int prioridad = (Integer) spinnerPrioridad.getValue();
            int deadline = (Integer) spinnerDeadline.getValue();
            final int computadorFinal = computador;
            final int bloquesFinal = bloques;

            SimulationController controller = SimulationController.getInstance();
            botonCrear.setEnabled(false);
            mensaje.setText("Creando proceso E/S-bound…");
            GuiRequest.model(controller, () -> {
                var nodo = controller.getFileSystemManager().findNode(ruta);
                if (nodo == null || !nodo.isFile()) throw new IllegalArgumentException("El archivo ya no existe. Actualiza la lista.");
                var actor = controller.getAccessControl().getActiveUsername();
                var permiso = new com.avilaos.filesystem.AccessControlManager(controller.getMode(), actor)
                        .checkOperation(controller.getFileSystemManager().getTree(), IOOperationType.READ, ruta, nodo.getType());
                if (!permiso.allowed()) throw new IllegalArgumentException(permiso.message());
                var pcb = controller.createConfigFileIOProcess(nombre, computadorFinal,
                        IOOperationType.READ, ruta, 0, actor, ExecutionMode.USER, nuevoNombre, prioridad, deadline);
                if (pcb == null) return "No se pudo crear el proceso de E/S; revisa los parámetros y el log.";
                return "Proceso E/S-bound '" + nombre + "' creado: " + pcb.getState() + " · op=" + op + " · ruta=" + ruta;
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
