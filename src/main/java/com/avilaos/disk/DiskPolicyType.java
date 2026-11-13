package com.avilaos.disk;

/** Políticas de planificación de disco disponibles (patrón Strategy). */
public enum DiskPolicyType {
    FIFO,
    SSTF,
    SCAN,
    CSCAN
}