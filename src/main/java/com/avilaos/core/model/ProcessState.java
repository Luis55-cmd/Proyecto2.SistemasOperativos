package com.avilaos.core.model;

/**
 * Modelo de 7 estados de ÁvilaOS (Proyecto 2).
 * NUEVO -> LISTO <-> EJECUTANDO -> TERMINADO; BLOQUEADO por E/S de disco;
 * SUSPENDIDO_LISTO y SUSPENDIDO_BLOQUEADO: procesos enviados al área de Swap.
 */
public enum ProcessState {
    NUEVO,
    LISTO,
    EJECUTANDO,
    BLOQUEADO,
    TERMINADO, // [P2] antes SALIENTE
    SUSPENDIDO_LISTO,
    SUSPENDIDO_BLOQUEADO,
}