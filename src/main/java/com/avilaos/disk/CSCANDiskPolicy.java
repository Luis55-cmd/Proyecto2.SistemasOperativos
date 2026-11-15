package com.avilaos.disk;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Política de planificación de disco C-SCAN (Circular SCAN) — Issue #8.
 * El cabezal barre siempre hacia bloques mayores (↑) hasta llegar al último
 * bloque ({@code totalBlocks - 1}), salta al bloque {@code 0} sin atender en
 * el regreso y continúa barriendo hacia arriba.
 */
public class CSCANDiskPolicy implements DiskSchedulingPolicy {

    private boolean wrappedOnLastSelect;

    @Override
    public IORequest selectNext(MyLinkedList<IORequest> pending, int head, int totalBlocks) {
        wrappedOnLastSelect = false;
        if (pending == null || pending.isEmpty()) {
            return null;
        }

        // 1. Buscar la solicitud más cercana con targetBlock >= head
        IORequest bestUp = null;
        int bestUpDist = Integer.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            IORequest req = pending.get(i);
            int target = req.getTargetBlock();
            if (target >= head) {
                int dist = target - head;
                if (bestUp == null || dist < bestUpDist
                        || (dist == bestUpDist && FIFODiskPolicy.compareArrival(req, bestUp) < 0)) {
                    bestUp = req;
                    bestUpDist = dist;
                }
            }
        }
        if (bestUp != null) {
            return bestUp;
        }

        // 2. No quedan solicitudes >= head: llega a totalBlocks - 1, salta a 0 y toma el menor bloque
        wrappedOnLastSelect = true;
        IORequest lowest = null;
        for (int i = 0; i < pending.size(); i++) {
            IORequest req = pending.get(i);
            if (lowest == null || req.getTargetBlock() < lowest.getTargetBlock()
                    || (req.getTargetBlock() == lowest.getTargetBlock()
                        && FIFODiskPolicy.compareArrival(req, lowest) < 0)) {
                lowest = req;
            }
        }
        return lowest;
    }

    public boolean didWrapOnLastSelect() {
        return wrappedOnLastSelect;
    }

    @Override
    public DiskSchedulingPolicyType getPolicyType() {
        return DiskSchedulingPolicyType.C_SCAN;
    }

    @Override
    public void reset() {
        this.wrappedOnLastSelect = false;
    }
}

