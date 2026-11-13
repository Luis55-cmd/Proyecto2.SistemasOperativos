package com.avilaos.gui;

import com.avilaos.concurrency.GuiCommands;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/** Barrera de pruebas: esperar desde main, nunca desde EDT. */
final class GuiTestDrain {
    static void await() throws Exception {
        if(SwingUtilities.isEventDispatchThread()) throw new AssertionError("No esperar en EDT");
        Semaphore done=new Semaphore(0);
        Exception[] failure={null};
        GuiCommands.submit(() -> true, ok -> done.release(), error -> {failure[0]=error;done.release();});
        if(!done.tryAcquire(5,TimeUnit.SECONDS)) throw new AssertionError("Trabajador no respondió");
        if(failure[0]!=null) throw new AssertionError(failure[0]);
        SwingUtilities.invokeAndWait(() -> {});
    }
}
