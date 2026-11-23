package com.avilaos.simulation;

import com.avilaos.filesystem.FilePermission;
import com.avilaos.io.FileSystemJsonSerializer;
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
            Files.writeString(archivo, "contenido anterior");
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

            int posicionSwap = c.getDisk().getTotalBlocks();
            c.getDisk().setHeadPosition(posicionSwap);
            c.saveFileSystemJson(archivo.toFile());
            c.loadFileSystemJson(archivo.toFile());
            if (c.getDisk().getHeadPosition() != posicionSwap
                    || c.getFileSystemManager().findNode("/usuario/reporte") == null)
                throw new AssertionError("No se restauró el cabezal en swap y el archivo");
            String json = Files.readString(archivo);
            try {
                FileSystemJsonSerializer.loadFromString(json, 0);
                throw new AssertionError("No debe aceptarse una posición fuera del disco físico");
            } catch (IllegalArgumentException expected) {
                if (!expected.getMessage().contains("currentHead")) throw expected;
            }
            String cadenaInvalida = "{\"diskConfig\":{\"totalBlocks\":20,\"initialHead\":0},"
                    + "\"fileSystem\":[{\"path\":\"/archivo\",\"type\":\"FILE\",\"startBlock\":3,"
                    + "\"blocks\":3,\"chain\":[3,4],\"owner\":\"ana\",\"permissions\":\"private\"}]}";
            var discoAnterior = c.getDisk();
            var arbolAnterior = c.getFileSystemManager();
            try {
                c.loadFileSystemJson(cadenaInvalida);
                throw new AssertionError("Una cadena incompleta debe rechazarse");
            } catch (IllegalArgumentException expected) {
                if (!expected.getMessage().contains("chain")) throw expected;
            }
            if (c.getDisk() != discoAnterior || c.getFileSystemManager() != arbolAnterior)
                throw new AssertionError("La carga inválida cambió el sistema anterior");

            var carpeta = Files.createTempDirectory("avila-destino-");
            var destino = Files.createDirectory(carpeta.resolve("destino"));
            var marca = Files.writeString(destino.resolve("conservar.txt"), "original");
            try {
                try {
                    c.saveFileSystemJson(destino.toFile());
                    throw new AssertionError("Guardar sobre un directorio no vacío debe fallar");
                } catch (java.io.IOException esperado) { }
                if (!"original".equals(Files.readString(marca))) throw new AssertionError("Se alteró el destino");
                try (var contenido = Files.list(carpeta)) {
                    if (contenido.count() != 1) throw new AssertionError("Quedó un archivo temporal");
                }
            } finally {
                Files.deleteIfExists(marca);
                Files.deleteIfExists(destino);
                Files.deleteIfExists(carpeta);
            }
            System.out.println("PASS: JSON real, metadatos y cabezal en datos y swap; rechaza swap insuficiente");
        } finally {
            Files.deleteIfExists(archivo);
            c.reset();
        }
    }
}
