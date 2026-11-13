package com.avilaos.core.model;

/** Tamaño del programa expandido: una unidad de RAM por instrucción. */
public final class ProcessSizing {
    private ProcessSizing() { }

    public static int instructions(ProcessType type, int cpuWork, int ioInterval,
                                   int cyclesPerOperation, int elements) {
        if (type == null) throw new IllegalArgumentException("Selecciona un tipo de proceso");
        if (type == ProcessType.PRODUCER || type == ProcessType.CONSUMER) {
            if (cyclesPerOperation < 1 || elements < 1) throw new IllegalArgumentException("Ciclos y elementos deben ser positivos");
            return Math.multiplyExact(elements, Math.addExact(cyclesPerOperation, 5));
        }
        if (cpuWork < 1) throw new IllegalArgumentException("Las instrucciones deben ser positivas");
        if (type == ProcessType.IO_BOUND) {
            if (cpuWork < 2) throw new IllegalArgumentException("Un proceso I/O bound necesita al menos 2 instrucciones para solicitar E/S.");
            if (ioInterval < 1 || ioInterval >= cpuWork) throw new IllegalArgumentException("El intervalo entre operaciones de E/S debe ser menor que el total de instrucciones.");
            return Math.addExact(cpuWork, (cpuWork - 1) / ioInterval);
        }
        return cpuWork;
    }
}
