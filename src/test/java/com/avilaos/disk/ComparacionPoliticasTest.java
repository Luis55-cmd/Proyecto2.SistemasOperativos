package com.avilaos.disk;

import com.avilaos.core.model.IOOperationType;

/** Compara el mismo escenario del anexo para las cuatro políticas obligatorias. */
public class ComparacionPoliticasTest {
    public static void main(String[] args) {
        int[] solicitudes = {82, 17, 43, 140, 24, 16, 190};
        DiskSchedulingPolicyType[] politicas = {DiskSchedulingPolicyType.FIFO, DiskSchedulingPolicyType.SSTF,
                DiskSchedulingPolicyType.SCAN, DiskSchedulingPolicyType.C_SCAN};
        int[] movimientosEsperados = {518, 208, 332, 192};
        int[] esperasEsperadas = {80, 54, 79, 64};
        for (int i = 0; i < politicas.length; i++) {
            var politica = politicas[i];
            var disco = new SimulatedDisk(200, 0, 50, 30);
            disco.setSchedulingPolicy(politica);
            for (int bloque : solicitudes)
                disco.enqueueRequest(null, IOOperationType.READ, "/" + bloque, bloque, 1, 0);
            int ciclo = 1;
            while (disco.getCompletedRequestsCount() < solicitudes.length && ciclo < 1000) disco.tick(ciclo++);
            if (disco.getCompletedRequestsCount() != solicitudes.length
                    || disco.getTotalHeadMovement() != movimientosEsperados[i]
                    || disco.getTotalWaitTime(politica) != esperasEsperadas[i]
                    || disco.getServicedByPolicy(politica) != solicitudes.length)
                throw new AssertionError("Resultados inesperados para " + politica);
            System.out.printf("%s: desplazamiento=%d, espera media=%.3f, atendidas=%d%n",
                    politica, disco.getTotalHeadMovement(), disco.getAverageWaitTime(politica), disco.getCompletedRequestsCount());
        }
    }
}
