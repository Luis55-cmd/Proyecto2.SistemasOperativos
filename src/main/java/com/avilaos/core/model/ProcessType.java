package com.avilaos.core.model;

/**
 * Tipos de procesos admitidos en ÁvilaOS.
 * PRODUCER/CONSUMER son legado del Proyecto 1 (sin uso en el P2).
 */
public enum ProcessType {
    CPU_BOUND,
    IO_BOUND,
    PRODUCER, // [P2-LEGACY]
    CONSUMER, // [P2-LEGACY]
    FILE_IO   // [P2] proceso de E/S que opera sobre un archivo (CRUD)
}