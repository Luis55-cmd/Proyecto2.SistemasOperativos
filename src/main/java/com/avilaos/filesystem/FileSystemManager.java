package com.avilaos.filesystem;

import com.avilaos.core.structures.MyLinkedList;
import com.avilaos.disk.DiskBlock;
import com.avilaos.disk.SimulatedDisk;
import java.util.concurrent.Semaphore;

/**
 * Gestor del Sistema de Archivos Jerárquico acoplado al Disco Simulado (Issue #9).
 * <ul>
 *   <li>Protege toda operación sobre el árbol con {@link Semaphore}.</li>
 *   <li>Vincula la creación y eliminación de archivos/directorios con la asignación
 *       encadenada de bloques en {@link SimulatedDisk}.</li>
 *   <li>Garantiza atomicidad: si no hay bloques suficientes, no modifica ni el árbol ni el disco.</li>
 * </ul>
 */
public class FileSystemManager {

    private final FileSystemTree tree;
    private SimulatedDisk disk;
    private final Semaphore fsMutex = new Semaphore(1, true);

    public FileSystemManager(SimulatedDisk disk) {
        this.tree = new FileSystemTree();
        this.disk = disk;
        if (disk == null || disk.allocateChainedBlocksFrom(0, "/", 1, -1) != 0)
            throw new IllegalStateException("No se pudo asignar el bloque de la raíz.");
        tree.getRoot().setFirstBlock(0);
        tree.getRoot().setSizeBlocks(1);
    }

    public void setDisk(SimulatedDisk disk) {
        fsMutex.acquireUninterruptibly();
        try {
            this.disk = disk;
        } finally {
            fsMutex.release();
        }
    }

    public SimulatedDisk getDisk() {
        return disk;
    }

    public FileSystemTree getTree() {
        return tree;
    }

    /** Copia coherente para visualización, sin compartir nodos vivos con Swing. */
    public FSNode snapshotTree() {
        fsMutex.acquireUninterruptibly();
        try { return copyNode(tree.getRoot()); }
        finally { fsMutex.release(); }
    }

    private FSNode copyNode(FSNode original) {
        FSNode copy = new FSNode(original.getName(), original.getType(), original.getOwner(),
                original.getPermission(), original.getFirstBlock(), original.getSizeBlocks(),
                original.getCreatorProcess(), original.getColorHex());
        for (int i = 0; i < original.getChildren().size(); i++) {
            copy.addChild(copyNode(original.getChildren().get(i)));
        }
        return copy;
    }

    /**
     * Crea un directorio en {@code fullPath}.
     */
    public FSNode createDirectory(String fullPath, String owner, FilePermission permission, String creatorProcess) {
        return createDirectoryAtBlock(fullPath, owner, permission, creatorProcess, -1);
    }

    public FSNode createDirectoryAtBlock(String fullPath, String owner, FilePermission permission, String creatorProcess, int block) {
        fsMutex.acquireUninterruptibly();
        try {
            validateNewPathUnlocked(fullPath);
            int allocated = block < 0 ? disk.allocateChainedBlocks(fullPath, 1, -1)
                    : disk.allocateChainedBlocksFrom(block, fullPath, 1, -1);
            if (allocated < 0) throw new IllegalStateException("No hay bloque disponible para el directorio: " + fullPath);
            try {
                FSNode node = tree.insertAtPath(fullPath, FsNodeType.DIRECTORY, owner, permission, allocated, 1);
                node.setCreatorProcess(creatorProcess != null ? creatorProcess : "SYSTEM");
                return node;
            } catch (RuntimeException ex) {
                disk.freeChainedBlocks(allocated);
                throw ex;
            }
        } finally {
            fsMutex.release();
        }
    }

    public int reserveCreation(com.avilaos.core.model.ProcessControlBlock p) {
        fsMutex.acquireUninterruptibly();
        try {
            validateNewPathUnlocked(p.getTargetPath());
            var decision = new AccessControlManager(p.getRequesterMode(), p.getRequesterName())
                    .checkOperation(tree, com.avilaos.core.model.IOOperationType.CREATE, p.getTargetPath(), p.getCreationType());
            if (!decision.allowed()) throw new IllegalArgumentException(decision.message());
            int start = disk.reserveChainedBlocks(p.getTargetPath(), p.getFileSizeBlocks(), p.getId());
            if (start < 0) throw new IllegalStateException("No hay bloques suficientes para " + p.getTargetPath());
            return start;
        } finally { fsMutex.release(); }
    }

    public void completeCreation(com.avilaos.core.model.ProcessControlBlock p) {
        fsMutex.acquireUninterruptibly();
        try {
            validateNewPathUnlocked(p.getTargetPath());
            var decision = new AccessControlManager(p.getRequesterMode(), p.getRequesterName())
                    .checkOperation(tree, com.avilaos.core.model.IOOperationType.CREATE, p.getTargetPath(), p.getCreationType());
            if (!decision.allowed()) throw new IllegalArgumentException(decision.message());
            if (!disk.finishReservation(p.getReservedStart(), p.getId(), true)) throw new IllegalStateException("Reserva inválida");
            try {
                FSNode node = tree.insertAtPath(p.getTargetPath(), p.getCreationType(), p.getOwner(),
                        p.getCreationPermission(), p.getReservedStart(), p.getFileSizeBlocks());
                node.setCreatorProcess(p.getName());
            } catch (RuntimeException ex) {
                disk.freeChainedBlocks(p.getReservedStart());
                throw ex;
            }
            p.setReservedStart(-1);
            p.setCreationCompleted(true);
        } finally { fsMutex.release(); }
    }

    /**
     * Crea un archivo en {@code fullPath} reservando {@code sizeBlocks} bloques encadenados
     * en el disco simulado. Si no hay bloques libres suficientes o la ruta es inválida,
     * lanza excepción sin alterar ni el árbol ni el disco.
     */
    public FSNode createFile(String fullPath, int sizeBlocks, String owner,
                             FilePermission permission, String creatorProcess, int ownerProcessId) {
        if (sizeBlocks <= 0) {
            throw new IllegalArgumentException("El tamaño del archivo en bloques debe ser mayor que 0");
        }
        fsMutex.acquireUninterruptibly();
        try {
            if (disk == null) {
                throw new IllegalStateException("No hay disco asociado al sistema de archivos");
            }
            validateNewPathUnlocked(fullPath);
            if (!disk.hasAvailableDataBlocks(sizeBlocks)) {
                throw new IllegalStateException("Espacio insuficiente en el disco para crear " + fullPath
                        + " (requiere " + sizeBlocks + " bloques, libres: " + disk.getFreeDataBlocks() + ")");
            }
            int startBlock = disk.allocateChainedBlocks(fullPath, sizeBlocks, ownerProcessId);
            if (startBlock < 0) {
                throw new IllegalStateException("No se pudieron asignar bloques en el disco para " + fullPath);
            }
            try {
                FSNode node = tree.insertAtPath(fullPath, FsNodeType.FILE, owner, permission, startBlock, sizeBlocks);
                node.setCreatorProcess(creatorProcess != null ? creatorProcess : "SYSTEM");
                return node;
            } catch (RuntimeException ex) {
                // Revertir asignación de disco si falló la inserción en el árbol
                disk.freeChainedBlocks(startBlock);
                throw ex;
            }
        } finally {
            fsMutex.release();
        }
    }

    /**
     * Crea un archivo comenzando en un {@code startBlock} específico (usado al cargar el JSON inicial).
     */
    public FSNode createFileAtStartBlock(String fullPath, int startBlock, int sizeBlocks,
                                         String owner, FilePermission permission, String creatorProcess) {
        if (sizeBlocks <= 0) {
            throw new IllegalArgumentException("El tamaño del archivo en bloques debe ser mayor que 0");
        }
        fsMutex.acquireUninterruptibly();
        try {
            if (disk == null) {
                throw new IllegalStateException("No hay disco asociado al sistema de archivos");
            }
            validateNewPathUnlocked(fullPath);
            int assignedStart = disk.allocateChainedBlocksFrom(startBlock, fullPath, sizeBlocks, -1);
            if (assignedStart < 0) {
                throw new IllegalStateException("No se pudo asignar la cadena de bloques desde el bloque "
                        + startBlock + " para " + fullPath);
            }
            try {
                FSNode node = tree.insertAtPath(fullPath, FsNodeType.FILE, owner, permission, assignedStart, sizeBlocks);
                node.setCreatorProcess(creatorProcess != null ? creatorProcess : "SYSTEM");
                return node;
            } catch (RuntimeException ex) {
                disk.freeChainedBlocks(assignedStart);
                throw ex;
            }
        } finally {
            fsMutex.release();
        }
    }

    /**
     * Lee un archivo o directorio existente por ruta y verifica que sus bloques sean válidos.
     */
    public FSNode readNode(String path) {
        fsMutex.acquireUninterruptibly();
        try {
            FSNode node = tree.find(path);
            if (node == null) {
                throw new IllegalArgumentException("No existe la ruta: " + path);
            }
            return node;
        } finally {
            fsMutex.release();
        }
    }

    public FSNode findNode(String path) {
        fsMutex.acquireUninterruptibly();
        try {
            return tree.find(path);
        } finally {
            fsMutex.release();
        }
    }

    /**
     * Actualiza (renombra) un archivo o directorio y sincroniza la ruta en los bloques ocupados del disco.
     */
    public FSNode renameNode(String path, String newName) {
        fsMutex.acquireUninterruptibly();
        try {
            tree.rename(path, newName);
            FSNode node = tree.find(parentOf(path).equals("/") ? "/" + newName : parentOf(path) + "/" + newName);
            if (node != null && disk != null) {
                syncOccupiedByRecursiveUnlocked(node);
            }
            return node;
        } finally {
            fsMutex.release();
        }
    }

    /** Para UPDATE sin nombre: genera un nombre libre en el mismo directorio. */
    public FSNode renameNodeAutomatically(String path) {
        fsMutex.acquireUninterruptibly();
        try {
            FSNode node = tree.find(path);
            if (node == null || node.getParent() == null) {
                throw new IllegalArgumentException("No se puede renombrar la ruta: " + path);
            }
            String nombre = node.getName();
            int punto = node.getType() == FsNodeType.FILE ? nombre.lastIndexOf('.') : -1;
            String base = punto > 0 ? nombre.substring(0, punto) : nombre;
            String extension = punto > 0 ? nombre.substring(punto) : "";
            String candidato = base + "_actualizado" + extension;
            int numero = 2;
            while (node.getParent().findChild(candidato) != null) {
                candidato = base + "_actualizado_" + numero++ + extension;
            }
            tree.rename(path, candidato);
            if (disk != null) syncOccupiedByRecursiveUnlocked(node);
            return node;
        } finally {
            fsMutex.release();
        }
    }

    /**
     * Elimina un archivo (liberando su cadena de bloques en el SD) o un directorio de forma
     * recursiva (eliminando todos sus subdirectorios y archivos y liberando todos sus bloques en el SD).
     *
     * @return Cantidad total de bloques liberados en el disco.
     */
    public int deleteNode(String path) {
        fsMutex.acquireUninterruptibly();
        try {
            MyLinkedList<FSNode> removedFiles = tree.remove(path);
            int totalFreedBlocks = 0;
            if (disk != null) {
                for (int i = 0; i < removedFiles.size(); i++) {
                    FSNode file = removedFiles.get(i);
                    if (file.getFirstBlock() >= 0) {
                        totalFreedBlocks += disk.freeChainedBlocks(file.getFirstBlock());
                    }
                }
            }
            return totalFreedBlocks;
        } finally {
            fsMutex.release();
        }
    }

    /**
     * Devuelve una copia de todos los archivos actuales en el árbol (para la tabla de asignación).
     */
    public MyLinkedList<FSNode> getAllFiles() {
        fsMutex.acquireUninterruptibly();
        try {
            return tree.collectFiles(tree.getRoot());
        } finally {
            fsMutex.release();
        }
    }

    /**
     * Devuelve todos los nodos (directorios y archivos, excepto la raíz) en preorden (para exportar a JSON).
     */
    public MyLinkedList<FSNode> getAllNodesPreOrder() {
        fsMutex.acquireUninterruptibly();
        try {
            MyLinkedList<FSNode> out = new MyLinkedList<>();
            collectAllPreOrderUnlocked(tree.getRoot(), out);
            return out;
        } finally {
            fsMutex.release();
        }
    }

    public void reset() {
        fsMutex.acquireUninterruptibly();
        try {
            MyLinkedList<FSNode> rootChildren = tree.getRoot().getChildren();
            while (!rootChildren.isEmpty()) {
                FSNode child = rootChildren.get(0);
                MyLinkedList<FSNode> removed = tree.remove(child.getPath());
                if (disk != null) {
                    for (int i = 0; i < removed.size(); i++) {
                        if (removed.get(i).getFirstBlock() >= 0) disk.freeChainedBlocks(removed.get(i).getFirstBlock());
                    }
                }
            }
            FSNode.resetColorPalette();
        } finally {
            fsMutex.release();
        }
    }

    private void collectAllPreOrderUnlocked(FSNode current, MyLinkedList<FSNode> out) {
        MyLinkedList<FSNode> children = current.getChildren();
        for (int i = 0; i < children.size(); i++) {
            FSNode child = children.get(i);
            out.append(child);
            if (child.isDirectory()) {
                collectAllPreOrderUnlocked(child, out);
            }
        }
    }

    private void syncOccupiedByRecursiveUnlocked(FSNode node) {
        if (node.getFirstBlock() >= 0) {
            String newPath = node.getPath();
            MyLinkedList<Integer> chain = disk.getChainBlocks(node.getFirstBlock());
            for (int i = 0; i < chain.size(); i++) {
                DiskBlock b = disk.getBlock(chain.get(i));
                if (b != null && b.isOccupied()) {
                    b.setOccupiedBy(newPath);
                }
            }
        }
        MyLinkedList<FSNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            syncOccupiedByRecursiveUnlocked(children.get(i));
        }
    }

    private void validateNewPathUnlocked(String fullPath) {
        if (fullPath == null || !fullPath.startsWith("/") || fullPath.length() < 2) {
            throw new IllegalArgumentException("Ruta inválida: " + fullPath);
        }
        String clean = fullPath.endsWith("/") ? fullPath.substring(0, fullPath.length() - 1) : fullPath;
        int cut = clean.lastIndexOf('/');
        String parentPath = cut == 0 ? "/" : clean.substring(0, cut);
        String name = clean.substring(cut + 1);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nombre vacío en ruta: " + fullPath);
        }
        FSNode parent = tree.find(parentPath);
        if (parent == null || !parent.isDirectory()) {
            throw new IllegalArgumentException("No existe el directorio padre: " + parentPath);
        }
        if (parent.findChild(name) != null) {
            throw new IllegalArgumentException("Ya existe un elemento con ese nombre: " + fullPath);
        }
    }

    public static String parentOf(String fullPath) {
        if (fullPath == null || !fullPath.startsWith("/") || fullPath.length() < 2) {
            return "/";
        }
        String clean = fullPath.endsWith("/") ? fullPath.substring(0, fullPath.length() - 1) : fullPath;
        int cut = clean.lastIndexOf('/');
        return cut <= 0 ? "/" : clean.substring(0, cut);
    }
}

