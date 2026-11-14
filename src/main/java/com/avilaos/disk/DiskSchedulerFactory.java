package com.avilaos.disk;

/**
 * Fábrica de políticas de planificación de disco a partir de {@link DiskSchedulingPolicyType}.
 */
public final class DiskSchedulerFactory {

    private DiskSchedulerFactory() {
    }

    public static DiskSchedulingPolicy create(DiskSchedulingPolicyType type) {
        if (type == null) {
            return new FIFODiskPolicy();
        }
        return switch (type) {
            case FIFO -> new FIFODiskPolicy();
            case SSTF -> new SSTFDiskPolicy();
            case SCAN -> new SCANDiskPolicy(HeadDirection.UP);
            case C_SCAN -> new CSCANDiskPolicy();
            case LOOK -> new LOOKDiskPolicy();
            case C_LOOK -> new CLOOKDiskPolicy();
        };
    }
}

