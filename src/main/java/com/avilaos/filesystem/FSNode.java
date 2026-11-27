package com.avilaos.filesystem;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Nodo del árbol N-ario del sistema de archivos (directorio o archivo) — Issues #3 y #9.
 * Almacena: nombre, tipo, tamaño en bloques, dueño (owner), permisos (PUBLIC/PRIVATE),
 * primer bloque (firstBlock / startBlock), proceso creador y color identificador.
 * Los hijos se guardan en una {@link MyLinkedList} propia (cero java.util).
 */
public class FSNode {
    private static final String[] PALETTE = {
        "#3B82F6", "#10B981", "#F59E0B", "#EF4444",
        "#8B5CF6", "#EC4899", "#14B8A6", "#F97316",
        "#06B6D4", "#84CC16", "#6366F1", "#D946EF"
    };
    private static int nextColorIndex = 0;

    private String name;
    private final FsNodeType type;
    private String owner;
    private FilePermission permission;
    private int firstBlock;         // -1 si es directorio o sin bloques asignados
    private int sizeBlocks;         // 0 para directorios
    private String creatorProcess;  // nombre/ID del proceso creador ("SYSTEM" si viene del JSON)
    private String colorHex;        // color identificador para la tabla de asignación y el disco
    private FSNode parent;
    private final MyLinkedList<FSNode> children = new MyLinkedList<>();

    public FSNode(String name, FsNodeType type, String owner, FilePermission permission,
                  int firstBlock, int sizeBlocks, String creatorProcess, String colorHex) {
        this.name = name;
        this.type = type;
        this.owner = (owner != null && !owner.isEmpty()) ? owner : "admin";
        this.permission = permission != null ? permission : FilePermission.PUBLIC;
        this.firstBlock = firstBlock;
        this.sizeBlocks = Math.max(0, sizeBlocks);
        this.creatorProcess = (creatorProcess != null && !creatorProcess.isEmpty()) ? creatorProcess : "SYSTEM";
        if (type == FsNodeType.DIRECTORY) {
            this.colorHex = "#64748B";
        } else if (colorHex != null && !colorHex.isEmpty()) {
            this.colorHex = colorHex;
        } else {
            this.colorHex = nextPaletteColor();
        }
    }

    public FSNode(String name, FsNodeType type, String owner, FilePermission permission,
                  int firstBlock, int sizeBlocks) {
        this(name, type, owner, permission, firstBlock, sizeBlocks, "SYSTEM", null);
    }

    private static synchronized String nextPaletteColor() {
        String c = PALETTE[nextColorIndex % PALETTE.length];
        nextColorIndex++;
        return c;
    }

    public static synchronized void resetColorPalette() {
        nextColorIndex = 0;
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
    public String getCreatorProcess() { return creatorProcess; }
    public void setCreatorProcess(String creatorProcess) { this.creatorProcess = creatorProcess; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
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
