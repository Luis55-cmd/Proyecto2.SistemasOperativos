package com.avilaos.memory;

import com.avilaos.simulation.SimulationController;
import com.avilaos.core.model.SchedulingPolicyType;

public class BufferMemoryTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var c = SimulationController.getInstance();
        c.reset();
        var pc = c.createComputer("RAM", 32, SchedulingPolicyType.FCFS);
        var buffer = c.createBuffer(pc.getId(), 2, 9);
        check(buffer != null && buffer.getStorage() == 2 && buffer.getSizeInMemory() == 2, "CAPACIDAD: conserva dos elementos");
        check(pc.getMemoryManager().getUsedMemory() == 2, "RAM: reserva capacidad 1 a 1");
        int ocupadas = 0;
        for (int celda : pc.getRam().getSpace()) if (celda != 0) ocupadas++;
        check(ocupadas == 2, "FISICA: dos celdas");
        var rechazado = c.createBuffer(pc.getId(), 31, 1);
        check(rechazado == null && c.getBuffers().size() == 1, "RECHAZO: tamaño no cabe");
        check(pc.getMemoryManager().getUsedMemory() == 2, "ERROR: sin reserva parcial");
        var grande = c.createBuffer(pc.getId(), 3, 100);
        check(grande != null && pc.getMemoryManager().getUsedMemory() == 5, "INDEPENDENCIA: ignorar RAM legacy independiente");
        System.out.println("PASS BufferMemoryTest");
    }
}
