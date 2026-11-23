package com.avilaos.simulation;

import com.avilaos.filesystem.FilePermission;
import java.nio.file.Files;

/** Guarda y carga un archivo JSON real; no prueba la interfaz gráfica. */
public class PersistenciaArchivoTest {
    public static void main(String[] args) throws Exception {
        var c = SimulationController.getInstance();
        c.reset();
        var archivo = Files.createTempFile("avila-fs-", ".json");
        try {
            var fs = c.getFileSystemManager();
            fs.createDirectory("/usuario", "ana", FilePermission.PRIVATE, "prueba");
            var original = fs.createFile("/usuario/reporte", 3, "ana", FilePermission.PRIVATE, "prueba", 1);
            int inicio = original.getFirstBlock();
            var cadena = c.getDisk().getChainBlocks(inicio);
            int[] bloques = new int[cadena.size()];
            for (int i = 0; i < bloques.length; i++) bloques[i] = cadena.get(i);
            c.getDisk().setHeadPosition(42);
            c.saveFileSystemJson(archivo.toFile());
            c.loadFileSystemJson(archivo.toFile());
            var restaurado = c.getFileSystemManager().findNode("/usuario/reporte");
            if (restaurado == null || !"ana".equals(restaurado.getOwner())
                    || restaurado.getPermission() != FilePermission.PRIVATE || restaurado.getSizeBlocks() != 3
                    || restaurado.getFirstBlock() != inicio || c.getDisk().getHeadPosition() != 42)
                throw new AssertionError("No se restauraron los metadatos y el cabezal");
            var cadenaRestaurada = c.getDisk().getChainBlocks(inicio);
            if (cadenaRestaurada.size() != bloques.length) throw new AssertionError("Tamaño de cadena");
            for (int i = 0; i < bloques.length; i++)
                if (cadenaRestaurada.get(i) != bloques[i]) throw new AssertionError("Cadena distinta");
            System.out.println("PASS: guardar y cargar JSON real con cadena, permisos, dueño y cabezal en datos");
        } finally {
            Files.deleteIfExists(archivo);
            c.reset();
        }
    }
}
