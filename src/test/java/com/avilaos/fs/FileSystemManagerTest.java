package com.avilaos.fs;

import com.avilaos.disk.SimulatedDisk;

/**
 * Pruebas unitarias del Issue #9 (rama feat/filesystem — T14 a T22 del BACKLOG):
 * - Metadatos de FSNode: nombre, tamaño en bloques, dueño, permiso, startBlock, proceso creador y color.
 * - Crear directorios y archivos vinculados con la asignación encadenada en SimulatedDisk.
 * - Rechazo sin alterar árbol ni disco cuando no hay bloques libres suficientes.
 * - Leer y Actualizar (renombrar archivos y directorios sincronizando bloques en el SD).
 * - Eliminar archivo y eliminar directorio de forma recursiva liberando todos sus bloques en el SD.
 * - Protección concurrente con Semaphore.
 */
public class FileSystemManagerTest {

    public static void main(String[] args) throws InterruptedException {
        testCreateFilesAndDirectoriesWithChainedBlocks();
        testRejectCreationWhenInsufficientDiskSpace();
        testReadAndRenameSyncsDiskBlocks();
        testDeleteFileAndRecursiveDirectoryFreesDiskBlocks();
        testSemaphoreConcurrency();
        System.out.println("TODAS LAS PRUEBAS DE FILESYSTEM MANAGER (ISSUE #9) PASARON.");
    }

    private static void testCreateFilesAndDirectoriesWithChainedBlocks() {
        SimulatedDisk disk = new SimulatedDisk(50, 20, 10, 10);
        FileSystemManager fs = new FileSystemManager(disk);

        FSNode sysDir = fs.createDirectory("/sistema", "admin", FilePermission.PUBLIC, "Init");
        check(sysDir.isDirectory() && "/sistema".equals(sysDir.getPath()), "directorio /sistema creado");

        FSNode file = fs.createFile("/sistema/kernel.bin", 4, "admin", FilePermission.PUBLIC, "Proc_1", 1);
        check(file.isFile(), "es archivo");
        check(file.getFirstBlock() == 0, "startBlock = 0");
        check(file.getSizeBlocks() == 4, "sizeBlocks = 4");
        check("admin".equals(file.getOwner()), "owner = admin");
        check(file.getPermission() == FilePermission.PUBLIC, "permission = PUBLIC");
        check("Proc_1".equals(file.getCreatorProcess()), "creatorProcess = Proc_1");
        check(file.getColorHex() != null && file.getColorHex().startsWith("#"), "color identificador asignado");
        check(disk.getFreeDataBlocks() == 46, "46 bloques libres en SD");
        check(disk.getChainBlocks(file.getFirstBlock()).size() == 4, "cadena de 4 bloques enlazados en SD");
    }

    private static void testRejectCreationWhenInsufficientDiskSpace() {
        SimulatedDisk disk = new SimulatedDisk(10, 5, 0, 5);
        FileSystemManager fs = new FileSystemManager(disk);
        fs.createDirectory("/datos", "admin", FilePermission.PUBLIC, "Init");
        fs.createFile("/datos/a.bin", 6, "admin", FilePermission.PUBLIC, "P1", 1);
        check(disk.getFreeDataBlocks() == 4, "quedan 4 bloques libres");

        boolean failed = false;
        try {
            fs.createFile("/datos/grande.bin", 8, "admin", FilePermission.PUBLIC, "P2", 2);
        } catch (IllegalStateException ex) {
            failed = true;
        }
        check(failed, "debe rechazar archivo cuando faltan bloques libres");
        check(fs.findNode("/datos/grande.bin") == null, "árbol sin cambios tras fallo");
        check(disk.getFreeDataBlocks() == 4, "disco sin asignación parcial tras fallo");
    }

    private static void testReadAndRenameSyncsDiskBlocks() {
        SimulatedDisk disk = new SimulatedDisk(30, 10, 0, 5);
        FileSystemManager fs = new FileSystemManager(disk);
        fs.createDirectory("/docs", "user1", FilePermission.PRIVATE, "P1");
        FSNode n = fs.createFile("/docs/nota.txt", 2, "user1", FilePermission.PRIVATE, "P1", 1);

        check(fs.readNode("/docs/nota.txt") == n, "readNode devuelve el archivo");
        fs.renameNode("/docs/nota.txt", "final.txt");
        check(fs.findNode("/docs/nota.txt") == null, "nombre anterior ya no existe");
        check(fs.findNode("/docs/final.txt") == n, "nuevo nombre encontrado");
        check("/docs/final.txt".equals(disk.getBlock(n.getFirstBlock()).getOccupiedBy()),
                "occupiedBy actualizado en bloques del disco tras renombrar archivo");

        fs.renameNode("/docs", "documentos");
        check(fs.findNode("/documentos/final.txt") == n, "renombrar directorio actualiza ruta de hijos");
        check("/documentos/final.txt".equals(disk.getBlock(n.getFirstBlock()).getOccupiedBy()),
                "occupiedBy actualizado en disco tras renombrar directorio padre");
    }

    private static void testDeleteFileAndRecursiveDirectoryFreesDiskBlocks() {
        SimulatedDisk disk = new SimulatedDisk(40, 10, 0, 5);
        FileSystemManager fs = new FileSystemManager(disk);
        fs.createDirectory("/raiz", "admin", FilePermission.PUBLIC, "SYS");
        fs.createDirectory("/raiz/sub1", "admin", FilePermission.PUBLIC, "SYS");
        fs.createDirectory("/raiz/sub1/sub2", "admin", FilePermission.PUBLIC, "SYS");

        fs.createFile("/raiz/f1.bin", 3, "admin", FilePermission.PUBLIC, "P1", 1);
        fs.createFile("/raiz/sub1/f2.bin", 4, "admin", FilePermission.PUBLIC, "P2", 2);
        fs.createFile("/raiz/sub1/sub2/f3.bin", 5, "admin", FilePermission.PUBLIC, "P3", 3);
        check(disk.getFreeDataBlocks() == 40 - 12, "12 bloques ocupados en total");

        // Eliminar un archivo individual (3 bloques)
        int freedFile = fs.deleteNode("/raiz/f1.bin");
        check(freedFile == 3 && disk.getFreeDataBlocks() == 40 - 9, "3 bloques liberados al borrar f1.bin");

        // Eliminar directorio /raiz recursivamente (debe borrar sub1, sub2, f2.bin y f3.bin y liberar 9 bloques)
        int freedDir = fs.deleteNode("/raiz");
        check(freedDir == 9, "9 bloques liberados al eliminar /raiz recursivamente");
        check(disk.getFreeDataBlocks() == 40, "los 40 bloques del disco volvieron a quedar libres");
        check(fs.findNode("/raiz") == null, "/raiz eliminado del árbol");
    }

    private static void testSemaphoreConcurrency() throws InterruptedException {
        SimulatedDisk disk = new SimulatedDisk(100, 20, 0, 10);
        FileSystemManager fs = new FileSystemManager(disk);
        fs.createDirectory("/concurrente", "admin", FilePermission.PUBLIC, "SYS");

        Thread[] threads = new Thread[6];
        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                fs.createFile("/concurrente/f_" + idx + ".dat", 3, "admin", FilePermission.PUBLIC, "T" + idx, idx);
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        check(fs.getAllFiles().size() == 6, "6 archivos creados concurrentemente");
        check(disk.getFreeDataBlocks() == 100 - 18, "18 bloques ocupados de forma consistente");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError("Fallo: " + msg);
        }
    }
}

