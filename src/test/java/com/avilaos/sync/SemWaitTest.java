package com.avilaos.sync;

import com.avilaos.core.model.*;
import com.avilaos.core.structures.MyQueue;

public class SemWaitTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (Buffer.SemaphoreKind kind : Buffer.SemaphoreKind.values()) {
            Buffer b = new Buffer(null, null, null, 1, 1, 1, new MyQueue<>(), 1);
            var semaphore = switch (kind) {
                case MUTEX -> b.getMutex();
                case VACIOS -> b.getVacios();
                case LLENOS -> b.getLlenos();
            };
            var queue = switch (kind) {
                case MUTEX -> b.getWaitingMutex();
                case VACIOS -> b.getWaitingVacios();
                case LLENOS -> b.getWaitingLlenos();
            };
            var reason = switch (kind) {
                case MUTEX -> BlockReason.WAITING_MUTEX;
                case VACIOS -> BlockReason.WAITING_FULL_BUFFER;
                case LLENOS -> BlockReason.WAITING_EMPTY_BUFFER;
            };
            if (kind == Buffer.SemaphoreKind.LLENOS) semaphore.release();
            ProcessControlBlock first = running();
            check(b.semWait(first, kind), "ADQUIRIR: concede permiso disponible");
            check(semaphore.availablePermits() == 0, "PERMISO: descontado exactamente una vez");
            check(first.getProgramCounter() == 21 && first.getMemoryAddressRegister() == 20,
                    "REGISTROS: avanzan una instrucción");
            check(first.getState() == ProcessState.EJECUTANDO && queue.isEmpty(), "EXITO: no encola");
            check(first.getMode() == ExecutionMode.KERNEL, "MODO: instrucción kernel");

            ProcessControlBlock second = running();
            check(!b.semWait(second, kind), "BLOQUEO: no hay permiso");
            check(second.getState() == ProcessState.BLOQUEADO && second.getBlockReason() == reason,
                    "ESPERA: estado y motivo");
            check(second.getProgramCounter() == 21 && second.getMemoryAddressRegister() == 20,
                    "PC_BLOQUEADO: ya apunta a siguiente instrucción");
            check(queue.size() == 1 && queue.peek() == second, "COLA: conserva identidad del PCB");
            try {
                b.semWait(second, kind);
                throw new AssertionError("REPETICION: debía rechazar wait de bloqueado");
            } catch (IllegalStateException expected) { }
            check(queue.size() == 1 && second.getProgramCounter() == 21, "REPETICION: sin cambios");
            ProcessControlBlock invalid = running();
            try {
                b.semWait(invalid, null);
                throw new AssertionError("ARGUMENTO: debía rechazar semáforo nulo");
            } catch (IllegalArgumentException expected) { }
            check(invalid.getProgramCounter() == 20 && semaphore.availablePermits() == 0,
                    "ERROR: no modifica registros ni permisos");
        }
        System.out.println("PASS SemWaitTest: mutex, vacios, llenos");
    }

    private static ProcessControlBlock running() {
        ProcessControlBlock pcb = new ProcessControlBlock();
        pcb.setState(ProcessState.EJECUTANDO);
        pcb.setProgramCounter(20);
        pcb.setMemoryAddressRegister(30);
        return pcb;
    }
}
