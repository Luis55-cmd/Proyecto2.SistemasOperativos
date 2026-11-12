package com.avilaos.core.model;

import com.avilaos.core.model.ProcessControlBlock.BufferPermit;

public class BufferPermitsTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void rejected(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("RECHAZO: transición inválida aceptada");
    }
    public static void main(String[] args) {
        for (var slot : new BufferPermit[]{BufferPermit.VACIOS, BufferPermit.LLENOS}) {
            var pcb = new ProcessControlBlock();
            var output = slot == BufferPermit.VACIOS ? BufferPermit.LLENOS : BufferPermit.VACIOS;
            pcb.grantBufferPermit(7, slot);
            check(pcb.getPermitBufferId() == 7 && pcb.getReservedBufferSlot() == slot,
                    "RESERVA: buffer y permiso adquiridos");
            rejected(() -> pcb.grantBufferPermit(7, slot));
            rejected(() -> pcb.grantBufferPermit(8, BufferPermit.MUTEX));
            rejected(() -> pcb.recordBufferOperation(7));
            pcb.grantBufferPermit(7, BufferPermit.MUTEX);
            pcb.setState(ProcessState.LISTO); // desalojo Round Robin
            check(pcb.holdsBufferMutex() && pcb.getReservedBufferSlot() == slot,
                    "RR: perder CPU no pierde permisos");
            pcb.recordBufferOperation(7);
            check(pcb.getReservedBufferSlot() == null && pcb.getPendingBufferSignal() == output,
                    "TRANSFORMACION: reserva consumida y signal opuesto pendiente");
            rejected(() -> pcb.recordBufferOperation(7));
            rejected(() -> pcb.recordBufferSignal(7, slot));
            pcb.recordBufferSignal(7, BufferPermit.MUTEX);
            check(!pcb.holdsBufferMutex() && pcb.getPermitBufferId() == 7,
                    "MUTEX: liberado pero conserva notificación");
            rejected(() -> pcb.recordBufferSignal(7, BufferPermit.MUTEX));
            pcb.recordBufferSignal(7, output);
            check(pcb.getPermitBufferId() == -1 && pcb.getPendingBufferSignal() == null,
                    "FINAL: ningún permiso pendiente");
            rejected(() -> pcb.recordBufferSignal(7, output));
            check(pcb.getProgramCounter() == 0, "PC: contabilidad no ejecuta instrucciones");
            pcb.grantBufferPermit(8, slot);
        }
        System.out.println("PASS BufferPermitsTest");
    }
}
