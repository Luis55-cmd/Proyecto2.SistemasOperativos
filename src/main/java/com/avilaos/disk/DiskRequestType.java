package com.avilaos.disk;

/** Origen de una solicitud al disco. */
public enum DiskRequestType {
    FILE_OP,   // operación CRUD de un proceso de E/S
    SWAP_OUT,  // suspensión: RAM -> área de Swap
    SWAP_IN    // reactivación: área de Swap -> RAM
}