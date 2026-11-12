package com.avilaos.core.model;

/**
 * Motivos de bloqueo de un proceso en el núcleo de ÁvilaOS.
 * Requisito: D-03, Capa 1 y Capa 2.
 */
public enum BlockReason {
    NONE,
    WAITING_MUTEX,
    WAITING_EMPTY_BUFFER,
    WAITING_FULL_BUFFER,
    NETWORK_LATENCY,
    WAITING_IO
}
