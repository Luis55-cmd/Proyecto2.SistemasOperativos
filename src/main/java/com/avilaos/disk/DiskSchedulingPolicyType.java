package com.avilaos.disk;

/** Políticas de planificación de disco disponibles (patrón Strategy). */
public enum DiskSchedulingPolicyType {
    FIFO,
    SSTF,
    SCAN,
    C_SCAN,
    LOOK,   // opcional
    C_LOOK  // opcional
}