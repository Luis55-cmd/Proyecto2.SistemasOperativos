package com.avilaos.core.model;

/**
 * Tipos de procesos admitidos en el simulador ÁvilaOS.
 * Requisito: RF §1 y RF §2.
 */
public enum ProcessState {
    NUEVO,
    LISTO,
    EJECUTANDO,
    BLOQUEADO,
    SALIENTE,
    SUSPENDIDO_LISTO,
    SUSPENDIDO_BLOQUEADO,
}