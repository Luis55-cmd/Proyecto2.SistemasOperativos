package com.avilaos.fs;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Nodo del árbol N-ario del sistema de archivos (directorio o archivo).
 * Los hijos se guardan en una MyLinkedList propia (sin java.util).
 * No es seguro entre hilos por sí mismo: FileSystemTree/FileSystem lo protegen.
 */
public class FSNode {
    private String name;
    private final FsNodeType type;
    private String owner;
    private FilePermission permission;
    private int firstBlock;   // -1 si es directorio o aún no tiene bloques
    private int sizeBlocks;   // 0 para directorios
    private FSNode parent;
    private final MyLinkedList<FSNode> children = new MyLinkedList<>();

    public FSNode(String name, FsNodeType type, String owner, FilePermission permission,
                  int firstBlock, int sizeBlocks) {
        this.name = name;
        this.type = type;
        this.owner = owner;
        this.permission = permission;
        this.firstBlock = type == FsNodeType.DIRECTORY ? -1 : firstBlock;
        this.sizeBlocks = type == FsNodeType.DIRECTORY ? 0 : sizeBlocks;
    }

    public boolean isDirectory() { return type == FsNodeType.DIRECTORY; }
    public boolean isFile() { return type == FsNodeType.FILE; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FsNodeType getType() { return type; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public FilePermission getPermission() { return permission; }
    public void setPermission(FilePermission permission) { this.permission = permission; }
    public int getFirstBlock() { return firstBlock; }
    public void setFirstBlock(int firstBlock) { this.firstBlock = firstBlock; }
    public int getSizeBlocks() { return sizeBlocks; }
    public void setSizeBlocks(int sizeBlocks) { this.sizeBlocks = sizeBlocks; }
    public FSNode getParent() { return parent; }
    public MyLinkedList<FSNode> getChildren() { return children; }

    /** Ruta absoluta del nodo (la raíz es "/"). */
    public String getPath() {
        if (parent == null) return "/";
        String parentPath = parent.getPath();
        return parentPath.equals("/") ? "/" + name : parentPath + "/" + name;
    }

    /** Hijo directo con ese nombre, o null. */
    public FSNode findChild(String childName) {
        for (int i = 0; i < children.size(); i++) {
            FSNode c = children.get(i);
            if (c.name.equals(childName)) return c;
        }
        return null;
    }

    /** Agrega un hijo. Solo los directorios admiten hijos; no se permiten nombres repetidos. */
    public void addChild(FSNode child) {
        if (!isDirectory()) throw new IllegalStateException("Un archivo no puede tener hijos: " + getPath());
        if (child == null || child.parent != null) throw new IllegalArgumentException("Nodo inválido o ya insertado");
        if (findChild(child.name) != null) throw new IllegalArgumentException("Ya existe: " + child.name);
        child.parent = this;
        children.append(child);
    }

    /** Desvincula un hijo directo (no recursivo). */
    public boolean removeChild(FSNode child) {
        boolean removed = children.remove(child);
        if (removed) child.parent = null;
        return removed;
    }

    @Override
    public String toString() { return name.isEmpty() ? "/" : name; } // útil para el JTree
}