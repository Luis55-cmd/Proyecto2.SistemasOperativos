package com.avilaos.filesystem;

import com.avilaos.core.model.IOOperationType;
import com.avilaos.core.model.UserMode;

/**
 * Control de modos de usuario (ADMIN vs USER) y validación de permisos sobre el
 * sistema de archivos y la configuración del sistema (Issue #10).
 * Nunca detiene la simulación: devuelve un {@link AccessDecision} con el estado
 * permitido/rechazado y el mensaje explicativo para la interfaz y el log.
 */
public class AccessControlManager {

    public record AccessDecision(boolean allowed, String message) {
        public static AccessDecision ok(String msg) {
            return new AccessDecision(true, msg);
        }

        public static AccessDecision deny(String reason) {
            return new AccessDecision(false, reason);
        }
    }

    private UserMode activeMode = UserMode.ADMIN;
    private String activeUsername = "admin";

    public AccessControlManager() {
    }

    public AccessControlManager(UserMode activeMode, String activeUsername) {
        setSession(activeMode, activeUsername);
    }

    public synchronized void setSession(UserMode mode, String username) {
        this.activeMode = mode != null ? mode : UserMode.ADMIN;
        if (this.activeMode == UserMode.ADMIN) {
            this.activeUsername = (username != null && !username.trim().isEmpty()) ? username.trim() : "admin";
        } else {
            this.activeUsername = (username != null && !username.trim().isEmpty()) ? username.trim() : "user1";
        }
    }

    public synchronized void setActiveMode(UserMode mode) {
        this.activeMode = mode != null ? mode : UserMode.ADMIN;
        if (this.activeMode == UserMode.ADMIN && (activeUsername == null || activeUsername.startsWith("user"))) {
            this.activeUsername = "admin";
        } else if (this.activeMode == UserMode.USER && "admin".equalsIgnoreCase(activeUsername)) {
            this.activeUsername = "user1";
        }
    }

    public synchronized void setActiveUsername(String username) {
        if (username != null && !username.trim().isEmpty()) {
            this.activeUsername = username.trim();
        }
    }

    public synchronized UserMode getActiveMode() {
        return activeMode;
    }

    public synchronized String getActiveUsername() {
        return activeUsername;
    }

    public synchronized boolean isAdmin() {
        return activeMode == UserMode.ADMIN;
    }

    /**
     * Verifica si el modo actual permite cambiar políticas de planificación (CPU/Disco).
     */
    public synchronized AccessDecision canChangePolicy() {
        if (activeMode == UserMode.ADMIN) {
            return AccessDecision.ok("Cambio de política permitido (ADMIN).");
        }
        return AccessDecision.deny("Operación rechazada (modo USER): solo el administrador puede cambiar las políticas de planificación.");
    }

    /**
     * Valida una operación CRUD sobre el árbol de archivos antes de crear o ejecutar el proceso de E/S.
     *
     * @param tree         Árbol del sistema de archivos.
     * @param operation    Operación solicitada (CREATE, READ, UPDATE, DELETE).
     * @param targetPath   Ruta objetivo.
     * @param targetType   Tipo de nodo para CREATE (FILE o DIRECTORY); puede ser null en READ/UPDATE/DELETE.
     */
    public synchronized AccessDecision checkOperation(FileSystemTree tree, IOOperationType operation,
                                                      String targetPath, FsNodeType targetType) {
        if (tree == null) {
            return AccessDecision.deny("Sistema de archivos no inicializado.");
        }
        if (operation == null) {
            return AccessDecision.deny("Operación de E/S no especificada.");
        }
        if (operation == IOOperationType.SUSPEND || operation == IOOperationType.REACTIVATE) {
            return AccessDecision.ok("Operación interna de swap del kernel.");
        }
        if (targetPath == null || !targetPath.startsWith("/")) {
            return AccessDecision.deny("Ruta inválida: " + targetPath);
        }

        // En modo ADMIN se permiten todas las operaciones CRUD
        if (activeMode == UserMode.ADMIN) {
            return AccessDecision.ok("Operación " + operation + " permitida en modo ADMIN sobre " + targetPath);
        }

        // Modo USER: validaciones por operación
        return switch (operation) {
            case CREATE -> checkCreateAsUser(tree, targetPath, targetType != null ? targetType : FsNodeType.FILE);
            case READ -> checkReadAsUser(tree, targetPath);
            case UPDATE -> checkModifyAsUser(tree, targetPath, "modificar/renombrar");
            case DELETE -> checkModifyAsUser(tree, targetPath, "eliminar");
            default -> AccessDecision.deny("Operación no soportada: " + operation);
        };
    }

    private AccessDecision checkCreateAsUser(FileSystemTree tree, String fullPath, FsNodeType targetType) {
        if (targetType == FsNodeType.DIRECTORY) {
            return AccessDecision.deny("Operación rechazada (modo USER): solo el administrador puede crear directorios (" + fullPath + ").");
        }
        String parentPath = FileSystemManager.parentOf(fullPath);
        FSNode parent = tree.find(parentPath);
        if (parent == null || !parent.isDirectory()) {
            return AccessDecision.deny("Operación rechazada: no existe el directorio destino '" + parentPath + "'.");
        }
        if (!isOwner(parent)) {
            return AccessDecision.deny("Operación rechazada (modo USER): el usuario '" + activeUsername
                    + "' solo puede crear archivos dentro de sus propios directorios (el dueño de '"
                    + parentPath + "' es '" + parent.getOwner() + "').");
        }
        return AccessDecision.ok("Creación de archivo permitida en directorio propio '" + parentPath + "'.");
    }

    private AccessDecision checkReadAsUser(FileSystemTree tree, String targetPath) {
        FSNode node = tree.find(targetPath);
        if (node == null) {
            return AccessDecision.deny("Operación rechazada: no existe la ruta '" + targetPath + "'.");
        }
        if (node.isDirectory()) {
            return AccessDecision.ok("Lectura de estructura de directorio permitida.");
        }
        if (isOwner(node) || node.getPermission() == FilePermission.PUBLIC) {
            return AccessDecision.ok("Lectura permitida sobre '" + targetPath + "'.");
        }
        return AccessDecision.deny("Operación rechazada (modo USER): el archivo '" + targetPath
                + "' es privado y pertenece a '" + node.getOwner() + "' (usuario activo: '" + activeUsername + "').");
    }

    private AccessDecision checkModifyAsUser(FileSystemTree tree, String targetPath, String verb) {
        if ("/".equals(targetPath)) {
            return AccessDecision.deny("Operación rechazada: no se puede " + verb + " el directorio raíz '/'.");
        }
        FSNode node = tree.find(targetPath);
        if (node == null) {
            return AccessDecision.deny("Operación rechazada: no existe la ruta '" + targetPath + "'.");
        }
        if ("admin".equalsIgnoreCase(node.getOwner())) {
            return AccessDecision.deny("Operación rechazada (modo USER): no se permite " + verb
                    + " archivos o directorios del sistema ('" + targetPath + "').");
        }
        if (!isOwner(node)) {
            return AccessDecision.deny("Operación rechazada (modo USER): no se permite " + verb
                    + " archivos o directorios de otros usuarios ('" + targetPath
                    + "' pertenece a '" + node.getOwner() + "', usuario activo: '" + activeUsername + "').");
        }
        return AccessDecision.ok("Operación de " + verb + " permitida sobre recurso propio '" + targetPath + "'.");
    }

    private boolean isOwner(FSNode node) {
        return node != null && node.getOwner() != null && node.getOwner().equalsIgnoreCase(activeUsername);
    }
}

