package com.avilaos.fs;

import com.avilaos.filesystem.FsNodeType;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FileSystemManager;
import com.avilaos.filesystem.AccessControlManager;
import com.avilaos.filesystem.FSNode;
import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.UserMode;
import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.disk.DiskSchedulingPolicyType;
import com.avilaos.disk.SimulatedDisk;
import com.avilaos.io.FileSystemJsonSerializer;
import com.avilaos.io.ProcessConfigJsonSerializer;
import com.avilaos.simulation.SimulationController;

/**
 * Pruebas unitarias e integradas del Módulo 2 (Issues #9, #10 y #11 — T14 a T26 y T39 a T44):
 * - Issue #9 (feat/filesystem): CRUD jerárquico acoplado a asignación encadenada en SD y Semaphore.
 * - Issue #10 (feat/user-modes): Validación de permisos en modo ADMIN vs USER sin detener la simulación.
 * - Issue #11 (feat/json-persistence): Carga/guardado de los 2 JSON obligatorios y bloqueo de inicio del reloj.
 */
public class Module2FileSystemAndPersistenceTest {

    private static final String SAMPLE_FS_JSON = """
        {
          "diskConfig": {
            "totalBlocks": 200,
            "initialHead": 50
          },
          "fileSystem": [
            {
              "path": "/sistema",
              "type": "DIRECTORY",
              "owner": "admin",
              "permissions": "public"
            },
            {
              "path": "/usuarios",
              "type": "DIRECTORY",
              "owner": "admin",
              "permissions": "public"
            },
            {
              "path": "/sistema/boot.bin",
              "type": "FILE",
              "startBlock": 11,
              "blocks": 1,
              "owner": "admin",
              "permissions": "public"
            },
            {
              "path": "/usuarios/notas.txt",
              "type": "FILE",
              "startBlock": 34,
              "blocks": 1,
              "owner": "user1",
              "permissions": "private"
            },
            {
              "path": "/sistema/script.py",
              "type": "FILE",
              "startBlock": 62,
              "blocks": 1,
              "owner": "admin",
              "permissions": "public"
            },
            {
              "path": "/sistema/core.dll",
              "type": "FILE",
              "startBlock": 64,
              "blocks": 1,
              "owner": "admin",
              "permissions": "public"
            },
            {
              "path": "/sistema/config.sys",
              "type": "FILE",
              "startBlock": 95,
              "blocks": 1,
              "owner": "admin",
              "permissions": "public"
            },
            {
              "path": "/usuarios/img1.png",
              "type": "FILE",
              "startBlock": 119,
              "blocks": 1,
              "owner": "user1",
              "permissions": "private"
            },
            {
              "path": "/usuarios/data.csv",
              "type": "FILE",
              "startBlock": 123,
              "blocks": 1,
              "owner": "user2",
              "permissions": "public"
            },
            {
              "path": "/usuarios/video.mp4",
              "type": "FILE",
              "startBlock": 180,
              "blocks": 1,
              "owner": "user1",
              "permissions": "public"
            }
          ]
        }
        """;

    private static final String SAMPLE_PROCESSES_JSON = """
        {
          "globalConfig": {
            "cycleDurationMs": 300,
            "swapSizeBlocks": 100,
            "initialDiskPolicy": "FIFO"
          },
          "computer": {
            "ramCapacity": 1024,
            "policy": "FCFS",
            "quantum": 0
          },
          "processes": [
            {
              "name": "Proc_95",
              "memory": 16,
              "deadline": 500,
              "instructions": 2,
              "targetPath": "/sistema/config.sys",
              "operation": "READ",
              "fileSizeBlocks": 0
            },
            {
              "name": "Proc_180",
              "memory": 16,
              "deadline": 500,
              "instructions": 2,
              "targetPath": "/usuarios/video.mp4",
              "operation": "READ",
              "fileSizeBlocks": 0
            }
          ]
        }
        """;

    public static void main(String[] args) {
        testIssue9CrudAndChainedDiskAllocation();
        testIssue10AdminAndUserModesAndPermissions();
        testIssue11DualJsonLoadSaveAndClockGate();
        System.out.println("TODAS LAS PRUEBAS DEL MODULO 2 (ISSUES #9, #10 Y #11) PASARON.");
    }

    private static void testIssue9CrudAndChainedDiskAllocation() {
        SimulatedDisk disk = new SimulatedDisk(50, 20, 10, 10);
        FileSystemManager fs = new FileSystemManager(disk);

        // Crear directorios y archivos
        fs.createDirectory("/sistema", "admin", FilePermission.PUBLIC, "Init");
        fs.createDirectory("/sistema/sub", "admin", FilePermission.PUBLIC, "Init");

        FSNode f1 = fs.createFile("/sistema/kernel.bin", 4, "admin", FilePermission.PUBLIC, "Proc_1", 1);
        check(f1.getFirstBlock() == 0, "kernel.bin empieza en bloque 0");
        check(f1.getSizeBlocks() == 4, "tamaño 4 bloques");
        check("Proc_1".equals(f1.getCreatorProcess()), "proceso creador registrado");
        check(f1.getColorHex() != null && f1.getColorHex().startsWith("#"), "color identificador asignado");
        check(disk.getFreeDataBlocks() == 46, "quedan 46 bloques libres");

        FSNode f2 = fs.createFile("/sistema/sub/driver.sys", 3, "admin", FilePermission.PRIVATE, "Proc_2", 2);
        check(f2.getFirstBlock() == 4, "driver.sys empieza en bloque 4");
        check(disk.getFreeDataBlocks() == 43, "quedan 43 bloques libres");

        // T18: Rechazar creación sin espacio suficiente sin alterar árbol ni disco
        expectFail(() -> fs.createFile("/sistema/huge.iso", 100, "admin", FilePermission.PUBLIC, "Proc_3", 3),
                "debe rechazar archivo cuando no hay bloques libres suficientes");
        check(fs.findNode("/sistema/huge.iso") == null, "árbol intacto tras rechazo por espacio");
        check(disk.getFreeDataBlocks() == 43, "disco intacto tras rechazo por espacio");

        // T19 & T20: Leer y renombrar archivo actualizando ruta en los bloques del disco
        FSNode readF1 = fs.readNode("/sistema/kernel.bin");
        check(readF1 == f1, "readNode encuentra kernel.bin");
        fs.renameNode("/sistema/kernel.bin", "vmlinuz.bin");
        check(fs.findNode("/sistema/vmlinuz.bin") != null, "archivo renombrado en árbol");
        check("/sistema/vmlinuz.bin".equals(disk.getBlock(0).getOccupiedBy()), "occupiedBy actualizado en disco");

        // T21: Eliminar archivo individual y liberar sus 4 bloques
        int freed1 = fs.deleteNode("/sistema/vmlinuz.bin");
        check(freed1 == 4, "se liberaron los 4 bloques de vmlinuz.bin");
        check(disk.getFreeDataBlocks() == 47, "47 bloques libres tras borrar vmlinuz.bin");

        // T22: Eliminar directorio anidado recursivamente y liberar los bloques de sus archivos hijos
        int freedDir = fs.deleteNode("/sistema");
        check(freedDir == 3, "se liberaron los 3 bloques de /sistema/sub/driver.sys");
        check(disk.getFreeDataBlocks() == 50, "todos los 50 bloques de datos quedaron libres");
        check(fs.findNode("/sistema") == null, "directorio /sistema eliminado");
    }

    private static void testIssue10AdminAndUserModesAndPermissions() {
        SimulatedDisk disk = new SimulatedDisk(100, 50, 0, 10);
        FileSystemManager fs = new FileSystemManager(disk);
        fs.createDirectory("/sistema", "admin", FilePermission.PUBLIC, "SYS");
        fs.createDirectory("/home_user1", "user1", FilePermission.PRIVATE, "SYS");
        fs.createDirectory("/home_user2", "user2", FilePermission.PRIVATE, "SYS");

        fs.createFile("/sistema/config.sys", 2, "admin", FilePermission.PUBLIC, "SYS", 0);
        fs.createFile("/home_user1/secreto1.txt", 1, "user1", FilePermission.PRIVATE, "P1", 1);
        fs.createFile("/home_user2/secreto2.txt", 1, "user2", FilePermission.PRIVATE, "P2", 2);
        fs.createFile("/home_user2/publico2.txt", 1, "user2", FilePermission.PUBLIC, "P2", 2);

        AccessControlManager ac = new AccessControlManager();

        // T23: En modo ADMIN todo está permitido
        ac.setSession(UserMode.ADMIN, "admin");
        check(ac.checkOperation(fs.getTree(), IOOperationType.READ, "/home_user2/secreto2.txt", FsNodeType.FILE).allowed(),
                "ADMIN puede leer archivo privado de user2");
        check(ac.checkOperation(fs.getTree(), IOOperationType.DELETE, "/sistema/config.sys", FsNodeType.FILE).allowed(),
                "ADMIN puede eliminar archivo del sistema");
        check(ac.canChangePolicy().allowed(), "ADMIN puede cambiar políticas");

        // T24 & T25 & T26: En modo USER (user1)
        ac.setSession(UserMode.USER, "user1");
        check(ac.checkOperation(fs.getTree(), IOOperationType.READ, "/home_user1/secreto1.txt", FsNodeType.FILE).allowed(),
                "user1 puede leer su propio archivo privado");
        check(ac.checkOperation(fs.getTree(), IOOperationType.READ, "/home_user2/publico2.txt", FsNodeType.FILE).allowed(),
                "user1 puede leer archivo público de user2");

        AccessControlManager.AccessDecision denyPrivate =
                ac.checkOperation(fs.getTree(), IOOperationType.READ, "/home_user2/secreto2.txt", FsNodeType.FILE);
        check(!denyPrivate.allowed() && denyPrivate.message().contains("privado"),
                "user1 NO puede leer archivo privado de user2 y recibe motivo claro");

        check(ac.checkOperation(fs.getTree(), IOOperationType.CREATE, "/home_user1/nuevo.txt", FsNodeType.FILE).allowed(),
                "user1 puede crear archivo dentro de su propio directorio /home_user1");

        AccessControlManager.AccessDecision denyCreateForeign =
                ac.checkOperation(fs.getTree(), IOOperationType.CREATE, "/sistema/hack.bin", FsNodeType.FILE);
        check(!denyCreateForeign.allowed(),
                "user1 NO puede crear archivo en directorio /sistema de admin");

        AccessControlManager.AccessDecision denyDeleteSys =
                ac.checkOperation(fs.getTree(), IOOperationType.DELETE, "/sistema/config.sys", FsNodeType.FILE);
        check(!denyDeleteSys.allowed(),
                "user1 NO puede eliminar archivos del sistema");

        AccessControlManager.AccessDecision denyModifyOther =
                ac.checkOperation(fs.getTree(), IOOperationType.UPDATE, "/home_user2/publico2.txt", FsNodeType.FILE);
        check(!denyModifyOther.allowed(),
                "user1 NO puede modificar archivos de user2 aunque sean públicos");

        check(!ac.canChangePolicy().allowed(), "user1 NO puede cambiar políticas de planificación");
    }

    private static void testIssue11DualJsonLoadSaveAndClockGate() {
        SimulationController controller = SimulationController.getInstance();
        controller.reset();
        controller.setDualJsonGateActive(true);

        // T42: Sin cargar ambos JSON, el inicio del reloj está bloqueado
        check(!controller.isReadyToStartClock(), "reloj bloqueado al inicio");
        expectFail(controller::startSimulation, "startSimulation bloqueado sin JSONs");
        expectFail(controller::stepSimulation, "stepSimulation bloqueado sin JSONs");

        // Cargar JSON #1 (Sistema de Archivos)
        controller.loadFileSystemJson(SAMPLE_FS_JSON);
        check(controller.isFileSystemJsonLoaded() && !controller.isProcessesJsonLoaded(),
                "solo JSON #1 cargado");
        expectFail(controller::startSimulation, "startSimulation sigue bloqueado si falta JSON #2");

        // Verificar que los 8 archivos y 2 directorios del JSON oficial se cargaron en bloques exactos
        check(controller.getDisk().getTotalBlocks() == 200, "totalBlocks = 200");
        check(controller.getDisk().getHeadPosition() == 50, "initialHead = 50");
        FSNode cfgNode = controller.getFileSystemManager().findNode("/sistema/config.sys");
        check(cfgNode != null && cfgNode.getFirstBlock() == 95, "/sistema/config.sys en bloque 95");
        check(controller.getDisk().getBlock(95).isOccupied(), "bloque 95 ocupado en disco");

        // Cargar JSON #2 (Procesos y Configuración)
        controller.loadProcessesConfigJson(SAMPLE_PROCESSES_JSON);
        check(controller.isReadyToStartClock(), "ambos JSON cargados: listo para iniciar");
        check(controller.getDisk().getSwapSizeBlocks() == 100, "swapSizeBlocks = 100");
        check(controller.getDisk().getPolicyType() == DiskSchedulingPolicyType.FIFO, "initialDiskPolicy = FIFO");
        check(controller.getAllProcesses().size() == 2, "2 procesos cargados");

        // T43: Modificar el sistema de archivos (crear archivo de 3 bloques), exportar a JSON y recargar
        controller.getFileSystemManager().createFile(
                "/usuarios/reporte.pdf", 3, "user1", FilePermission.PUBLIC, "Proc_Test", 99);
        String exportedJson = controller.exportFileSystemJson();
        FileSystemJsonSerializer.LoadedFileSystem reloaded =
                FileSystemJsonSerializer.loadFromString(exportedJson, 100);
        FSNode reloadedPdf = reloaded.fileSystemManager().findNode("/usuarios/reporte.pdf");
        check(reloadedPdf != null && reloadedPdf.getSizeBlocks() == 3,
                "archivo multibloque exportado y recargado con sus 3 bloques");
        MyLinkedList<Integer> chain = reloaded.disk().getChainBlocks(reloadedPdf.getFirstBlock());
        check(chain.size() == 3, "cadena de 3 bloques restaurada tras exportar/importar JSON");

        // T44: Recargar JSON de procesos preservando el archivo creado en el disco
        controller.loadProcessesConfigJson(SAMPLE_PROCESSES_JSON);
        check(controller.getFileSystemManager().findNode("/usuarios/reporte.pdf") != null,
                "recargar procesos preserva el estado modificado del sistema de archivos en el disco");

        // Limpiar el estado para no afectar otras pruebas
        controller.reset();
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError("Fallo: " + msg);
        }
    }

    private static void expectFail(Runnable r, String msg) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            return;
        }
        throw new AssertionError("Debía lanzar excepción: " + msg);
    }
}

