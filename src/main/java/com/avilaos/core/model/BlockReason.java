package com.avilaos.core.model;

/**
 * Motivos de bloqueo de un proceso en el núcleo de ÁvilaOS.
 * Los valores de buffer/red son legado del Proyecto 1 (sin uso en el P2).
 */
public enum BlockReason {
    NONE,
    WAITING_MUTEX,          // [P2-LEGACY]
    WAITING_EMPTY_BUFFER,   // [P2-LEGACY]
    WAITING_FULL_BUFFER,    // [P2-LEGACY]
    NETWORK_LATENCY,        // [P2-LEGACY]
    WAITING_IO,
    WAITING_DISK,           // [P2] esperando que el disco atienda su solicitud CRUD
    SWAPPING_OUT,           // [P2] escritura de su imagen al área de Swap
    SWAPPING_IN             // [P2] lectura de su imagen desde el área de Swap
}