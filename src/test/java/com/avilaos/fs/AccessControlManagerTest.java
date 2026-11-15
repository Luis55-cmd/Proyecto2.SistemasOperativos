package com.avilaos.fs;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.UserMode;
import com.avilaos.disk.SimulatedDisk;

/**
 * Pruebas unitarias del Issue #10 (rama feat/user-modes — T23 a T26 del BACKLOG):
 * - En modo ADMIN: permite todas las operaciones CRUD, gestión de procesos y cambio de políticas.
 * - En modo USER: permite leer la estructura general y archivos propios o públicos;
 *   permite crear archivos únicamente dentro de sus propios directorios;
 *   prohíbe modificar/eliminar archivos del sistema o de otros usuarios.
 * - Retorna mensajes claros de rechazo sin detener la simulación.
 */
public class AccessControlManagerTest {

    public static void main(String[] args) {
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

        // T23: En modo ADMIN todas las operaciones están permitidas
        ac.setSession(UserMode.ADMIN, "admin");
        check(ac.checkOperation(fs.getTree(), IOOperationType.READ, "/home_user2/secreto2.txt", FsNodeType.FILE).allowed(),
                "ADMIN puede leer archivo privado de user2");
        check(ac.checkOperation(fs.getTree(), IOOperationType.CREATE, "/sistema/nuevo_dir", FsNodeType.DIRECTORY).allowed(),
                "ADMIN puede crear directorios");
        check(ac.checkOperation(fs.getTree(), IOOperationType.UPDATE, "/sistema/config.sys", FsNodeType.FILE).allowed(),
                "ADMIN puede renombrar archivo del sistema");
        check(ac.checkOperation(fs.getTree(), IOOperationType.DELETE, "/sistema/config.sys", FsNodeType.FILE).allowed(),
                "ADMIN puede eliminar archivo del sistema");
        check(ac.canChangePolicy().allowed(), "ADMIN puede cambiar políticas de planificación");

        // T24: En modo USER (user1), lectura de estructura, archivos propios y públicos, y rechazo de privados ajenos
        ac.setSession(UserMode.USER, "user1");
        check(ac.checkOperation(fs.getTree(), IOOperationType.READ, "/sistema", FsNodeType.DIRECTORY).allowed(),
                "user1 puede leer estructura general de directorios");
        check(ac.checkOperation(fs.getTree(), IOOperationType.READ, "/home_user1/secreto1.txt", FsNodeType.FILE).allowed(),
                "user1 puede leer su propio archivo privado");
        check(ac.checkOperation(fs.getTree(), IOOperationType.READ, "/home_user2/publico2.txt", FsNodeType.FILE).allowed(),
                "user1 puede leer archivo público de user2");

        AccessControlManager.AccessDecision denyPrivate =
                ac.checkOperation(fs.getTree(), IOOperationType.READ, "/home_user2/secreto2.txt", FsNodeType.FILE);
        check(!denyPrivate.allowed() && denyPrivate.message().contains("privado"),
                "user1 NO puede leer archivo privado de user2 y recibe motivo claro");

        // T25 & T26: En modo USER, creación solo en directorio propio y rechazo de modificaciones/eliminaciones ajenas o del sistema
        check(ac.checkOperation(fs.getTree(), IOOperationType.CREATE, "/home_user1/apuntes.txt", FsNodeType.FILE).allowed(),
                "user1 puede crear archivo dentro de su propio directorio /home_user1");

        AccessControlManager.AccessDecision denyCreateDir =
                ac.checkOperation(fs.getTree(), IOOperationType.CREATE, "/home_user1/sub", FsNodeType.DIRECTORY);
        check(!denyCreateDir.allowed() && denyCreateDir.message().contains("directorios"),
                "user1 NO puede crear directorios y recibe motivo claro");

        AccessControlManager.AccessDecision denyCreateForeignDir =
                ac.checkOperation(fs.getTree(), IOOperationType.CREATE, "/sistema/hack.bin", FsNodeType.FILE);
        check(!denyCreateForeignDir.allowed() && denyCreateForeignDir.message().contains("propios directorios"),
                "user1 NO puede crear archivo en /sistema y recibe motivo claro");

        AccessControlManager.AccessDecision denyDeleteSys =
                ac.checkOperation(fs.getTree(), IOOperationType.DELETE, "/sistema/config.sys", FsNodeType.FILE);
        check(!denyDeleteSys.allowed() && denyDeleteSys.message().contains("sistema"),
                "user1 NO puede eliminar archivos del sistema");

        AccessControlManager.AccessDecision denyModifyOther =
                ac.checkOperation(fs.getTree(), IOOperationType.UPDATE, "/home_user2/publico2.txt", FsNodeType.FILE);
        check(!denyModifyOther.allowed() && denyModifyOther.message().contains("otros usuarios"),
                "user1 NO puede modificar archivos de user2 aunque sean públicos");

        AccessControlManager.AccessDecision denyPolicy = ac.canChangePolicy();
        check(!denyPolicy.allowed() && denyPolicy.message().contains("administrador"),
                "user1 NO puede cambiar políticas de planificación");

        System.out.println("TODAS LAS PRUEBAS DE ACCESS CONTROL MANAGER (ISSUE #10) PASARON.");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError("Fallo: " + msg);
        }
    }
}

