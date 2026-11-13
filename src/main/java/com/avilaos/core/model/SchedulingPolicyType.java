package com.avilaos.core.model;

/**
 * Políticas de planificación de CPU soportadas en ÁvilaOS.
 * Requisito: RF §1, RF §3, ARC-18.
 */
public enum SchedulingPolicyType {
    FCFS,
    EDF,
    ROUND_ROBIN,
    PRIORITY_PREEMPTIVE
}
