package com.avilaos.core.model;

/**
 * Tipos de procesos admitidos en el simulador ÁvilaOS.
 * Requisito: RF §1 y RF §2.
 */
public enum ProcessType {
    CPU_BOUND,
    IO_BOUND,
    PRODUCER,
    CONSUMER
}
