package com.avilaos.gui;

import com.avilaos.core.model.ProcessControlBlock;
import com.avilaos.core.model.BlockReason;
import com.avilaos.core.model.ProcessType;
import com.avilaos.core.model.SchedulingPolicyType;
import com.avilaos.simulation.SimulationController;
import java.lang.reflect.Field;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.SwingUtilities;

/** Comprueba que los datos visibles llegan al PCB sin secuencias ficticias. */
public class EditorProcesoFormularioTest {
    private static final SimulationController c = SimulationController.getInstance();
    private static EditorProceso editor;
    private static Object campo(String nombre) {
        try { Field f = EditorProceso.class.getDeclaredField(nombre); f.setAccessible(true); return f.get(editor); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void numero(String nombre, int valor) { ((JSpinner) campo(nombre)).setValue(valor); }
    private static void check(boolean valor, String mensaje) { if (!valor) throw new AssertionError(mensaje); }
    @SuppressWarnings("unchecked")
    private static void enviar(ProcessType tipo, String nombre) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ((JComboBox<ProcessType>) campo("comboTipoProceso")).setSelectedItem(tipo);
            ((JTextField) campo("campoNombre")).setText(nombre);
            numero("spinnerDeadline", 90);
            numero("spinnerInstrucciones", 12);
            numero("spinnerIoInterval", 4);
            numero("spinnerIoDuration", 3);
            numero("spinnerCiclosOperacion", 2);
            numero("spinnerElementos", 3);
            editor.generarPCB();
        });
        GuiTestDrain.await();
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            c.reset();
            var pc = c.createComputer("Equipo", 128, SchedulingPolicyType.FCFS);
            c.createBuffer(pc.getId(), 2);
            editor = new EditorProceso(() -> {});
            var memoria = (JTextField) campo("memoriaCalculada");
            check(!memoria.isEditable() && memoria.getText().equals("10"), "RAM inicial no editable");
            var instrucciones = ((JSpinner.DefaultEditor) ((JSpinner) campo("spinnerInstrucciones")).getEditor()).getTextField();
            instrucciones.setText("24");
            check(memoria.getText().equals("24"), "RAM CPU cambia mientras se escribe");
            instrucciones.setText("");
            check(memoria.getText().equals("—"), "Entrada incompleta no muestra cálculo anterior");
            instrucciones.setText("10");
            ((JComboBox<ProcessType>) campo("comboTipoProceso")).setSelectedItem(ProcessType.IO_BOUND);
            numero("spinnerIoInterval", 3);
            check(memoria.getText().equals("13"), "10 de trabajo más 3 solicitudes de E/S");
            ((JComboBox<ProcessType>) campo("comboTipoProceso")).setSelectedItem(ProcessType.PRODUCER);
            numero("spinnerCiclosOperacion", 3);
            numero("spinnerElementos", 4);
            check(memoria.getText().equals("32"), "RAM de productor en vivo");
            ((JComboBox<ProcessType>) campo("comboTipoProceso")).setSelectedItem(ProcessType.CONSUMER);
            check(memoria.getText().equals("32"), "RAM de consumidor en vivo");
        });
        enviar(ProcessType.CPU_BOUND, "CPU");
        enviar(ProcessType.IO_BOUND, "IO");
        enviar(ProcessType.PRODUCER, "Productor");
        enviar(ProcessType.CONSUMER, "Consumidor");
        check(c.getAllProcesses().size() == 4, "Se esperaban cuatro procesos");
        check(c.getComputers().get(0).getMemoryManager().getUsedMemory() == 70, "RAM real = buffer 2 + 12 + 14 + 21 + 21");
        for (int i = 0; i < 4; i++) {
            ProcessControlBlock p = c.getAllProcesses().get(i);
            int esperado = i == 0 ? 12 : i == 1 ? 14 : 21;
            check(p.getRequiredMemory() == esperado && p.getDeadline() == 90, "Memoria o deadline " + p.getName());
            check(p.getTotalInstructions() == esperado, "Instrucciones " + p.getName());
            if (i == 1) check(p.getIoInterval() == 4 && p.getIoDuration() == 3, "Parámetros E/S");
            if (i >= 2) check("1".equals(p.getTargetBufferId()) && p.getCyclesPerOperation() == 2
                    && p.getRequiredElements() == 3, "Parámetros buffer");
        }
        SwingUtilities.invokeAndWait(() -> {
            var tabla = (DefaultTableModel) campo("procesosVisibles");
            check(tabla.getRowCount() == 4, "Tabla no muestra los cuatro procesos");
            check(tabla.getValueAt(0, 1).equals("CPU") && tabla.getValueAt(0, 5).equals(12), "Fila CPU o memoria");
            check(tabla.getValueAt(1, 9).toString().contains("cada 4"), "Faltan parámetros E/S");
            check(tabla.getValueAt(2, 9).toString().contains("3 elementos"), "Faltan parámetros de productor");
            ((JTextField) campo("campoNombre")).setText("   ");
            editor.generarPCB();
        });
        check(c.getAllProcesses().size() == 4, "Aceptó nombre vacío");
        SwingUtilities.invokeAndWait(() -> {
            c.reset();
            c.createComputer("E/S", 128, SchedulingPolicyType.FCFS);
            editor = new EditorProceso(() -> {});
            ((JComboBox<ProcessType>) campo("comboTipoProceso")).setSelectedItem(ProcessType.IO_BOUND);
            ((JTextField) campo("campoNombre")).setText("Prueba E/S");
            numero("spinnerDeadline", 100);
            numero("spinnerInstrucciones", 1);
            numero("spinnerIoInterval", 1);
            editor.generarPCB();
            check(((JLabel) campo("mensaje")).getText().contains("al menos 2"), "Mensaje de mínimo");
            numero("spinnerInstrucciones", 10);
            numero("spinnerIoInterval", 10);
            editor.generarPCB();
            check(((JLabel) campo("mensaje")).getText().contains("menor"), "Mensaje de igualdad");
            numero("spinnerIoInterval", 15);
            editor.generarPCB();
            check(((JLabel) campo("mensaje")).getText().contains("menor"), "Mensaje de intervalo excesivo");
            check(c.getAllProcesses().size() == 0, "Se creó un I/O bound inválido");
            numero("spinnerIoInterval", 9);
            editor.generarPCB();
        });
        GuiTestDrain.await();
        check(c.getAllProcesses().size() == 1, "No creó el caso de frontera válido");
        ProcessControlBlock io = c.getAllProcesses().get(0);
        for (int i = 0; i < 9; i++) c.stepSimulation();
        check(io.getProgramCounter() == 9 && io.getBlockReason() == BlockReason.NONE,
                "El trabajo CPU debe completarse antes de la instrucción de E/S");
        c.stepSimulation();
        check(io.getProgramCounter() == 10 && io.getBlockReason() == BlockReason.WAITING_IO,
                "El proceso válido no ejecutó la solicitud de E/S");
        System.out.println("PASS: cuatro tipos y validación I/O bound, incluido bloqueo en el límite");
        c.reset();
        System.exit(0);
    }
}
