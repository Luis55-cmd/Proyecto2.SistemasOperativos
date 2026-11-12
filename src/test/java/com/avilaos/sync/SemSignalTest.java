package com.avilaos.sync;

import com.avilaos.core.model.*;
import com.avilaos.core.structures.MyQueue;

public class SemSignalTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static ProcessControlBlock running() {
        var pcb = new ProcessControlBlock();
        pcb.setState(ProcessState.EJECUTANDO);
        pcb.setProgramCounter(20);
        pcb.setMemoryAddressRegister(30);
        return pcb;
    }
    private static void prepareSignal(ProcessControlBlock pcb, Buffer.SemaphoreKind kind) {
        var permit = ProcessControlBlock.BufferPermit.valueOf(kind.name());
        pcb.grantBufferPermit(1, ProcessControlBlock.BufferPermit.MUTEX);
        if (kind != Buffer.SemaphoreKind.MUTEX) {
            pcb.grantBufferPermit(1, kind == Buffer.SemaphoreKind.LLENOS
                    ? ProcessControlBlock.BufferPermit.VACIOS : ProcessControlBlock.BufferPermit.LLENOS);
            pcb.recordBufferOperation(1);
            pcb.recordBufferSignal(1, ProcessControlBlock.BufferPermit.MUTEX);
        }
    }
    public static void main(String[] args) {
        for (var kind : Buffer.SemaphoreKind.values()) {
            var b = new Buffer(null, null, null, 1, 1, 1, new MyQueue<>(), 1);
            var semaphore = switch (kind) {
                case MUTEX -> b.getMutex();
                case VACIOS -> b.getVacios();
                case LLENOS -> b.getLlenos();
            };
            // Precondición: no queda ningún permiso disponible.
            semaphore.drainPermits();
            var stale = running();
            b.semWait(stale, kind);
            stale.setTerminated(true);
            var first = running();
            var second = running();
            b.semWait(first, kind);
            b.semWait(second, kind);
            var emitter = running();
            prepareSignal(emitter, kind);
            check(b.semSignal(emitter, kind) == first, "DESTINO: descarta terminado y toma primero válido");
            check(kind == Buffer.SemaphoreKind.MUTEX ? first.holdsBufferMutex()
                    : first.getReservedBufferSlot() == ProcessControlBlock.BufferPermit.valueOf(kind.name()),
                    "TITULAR: receptor conserva permiso transferido");
            check(emitter.getPermitBufferId() == -1, "EMISOR: pierde titularidad al señalizar");
            check(first.getState() == ProcessState.LISTO && first.getBlockReason() == BlockReason.NONE,
                    "DESPERTAR: listo y sin espera pendiente");
            check(first.getProgramCounter() == 21 && first.getMemoryAddressRegister() == 20,
                    "RECEPTOR: no ejecuta otra instrucción al despertar");
            check(emitter.getProgramCounter() == 21 && emitter.getMemoryAddressRegister() == 20,
                    "EMISOR: signal consume una instrucción");
            check(emitter.getMode() == ExecutionMode.KERNEL, "MODO: signal en kernel");
            check(semaphore.availablePermits() == 0, "TRANSFERENCIA: no libera permiso al público");
            check(second.getState() == ProcessState.BLOQUEADO, "UNO: solo despierta un PCB");
            var newcomer = running();
            check(!b.semWait(newcomer, kind), "ROBO: recién llegado no puede tomar permiso reservado");
            prepareSignal(emitter, kind);
            check(b.semSignal(emitter, kind) == second, "ORDEN: conserva turno de segundo");
            prepareSignal(emitter, kind);
            check(b.semSignal(emitter, kind) == newcomer, "ORDEN: luego recién llegado");
            prepareSignal(emitter, kind);
            check(b.semSignal(emitter, kind) == null && semaphore.availablePermits() == 1,
                    "LIBERAR: sin esperadores deja un permiso disponible");
            var next = running();
            check(b.semWait(next, kind), "REUTILIZAR: siguiente wait toma permiso liberado");
            emitter.setState(ProcessState.BLOQUEADO);
            int pc = emitter.getProgramCounter();
            try {
                b.semSignal(emitter, kind);
                throw new AssertionError("INVALIDO: emisor bloqueado no puede ejecutar signal");
            } catch (IllegalStateException expected) { }
            check(emitter.getProgramCounter() == pc && semaphore.availablePermits() == 0,
                    "ERROR: sin efectos laterales");
        }
        System.out.println("PASS SemSignalTest: transferencia, orden, no robo, liberación y registros");
    }
}
