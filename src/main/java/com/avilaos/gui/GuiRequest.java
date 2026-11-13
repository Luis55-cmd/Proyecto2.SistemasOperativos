package com.avilaos.gui;

import com.avilaos.concurrency.GuiCommands;
import com.avilaos.concurrency.StateAccess;
import com.avilaos.simulation.SimulationController;

/** Entradas capturadas en EDT; resultado inmutable para EDT. */
final class GuiRequest {
    private GuiRequest() { }
    static <T> void model(SimulationController controller, GuiCommands.Work<T> work,
                         GuiCommands.Result<T> success, GuiCommands.Result<Exception> failure) {
        long session=controller.session();
        GuiCommands.submit(() -> {
            StateAccess.enter();
            try { controller.requireSession(session); return work.run(); }
            finally { StateAccess.exit(); }
        }, value -> { if(controller.session()==session) success.accept(value); else failure.accept(new IllegalStateException("La sesión cambió; solicitud anterior descartada")); }, failure);
    }
    static void control(SimulationController controller, Runnable work) {
        model(controller, () -> { work.run(); return true; }, result -> {},
                error -> controller.logEvent("Solicitud rechazada: " + error.getMessage()));
    }
}
