package com.avilaos.disk;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Política opcional C-LOOK — Issue #8.
 * Igual que C-SCAN, pero al atender la solicitud más alta salta directamente
 * a la solicitud con el bloque más bajo pendiente sin ir hasta el extremo físico.
 */
public class CLOOKDiskPolicy implements DiskSchedulingPolicy {

    @Override
    public IORequest selectNext(MyLinkedList<IORequest> pending, int head, int totalBlocks) {
        if (pending == null || pending.isEmpty()) {
            return null;
        }
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

    @Override
    public DiskSchedulingPolicyType getPolicyType() {
        return DiskSchedulingPolicyType.C_LOOK;
    }

    @Override
    public void reset() {
    }
}

