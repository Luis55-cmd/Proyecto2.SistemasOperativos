package com.avilaos.fs;

import com.avilaos.core.structures.MyLinkedList;

/**
 * Árbol N-ario del sistema de archivos con búsqueda por ruta, inserción y
 * eliminación recursiva. Estructura pura (sin hilos): la envoltura
 * FileSystem añade el Semaphore.
 */
public class FileSystemTree {
    private final FSNode root;

    public FileSystemTree() {
        this.root = new FSNode("", FsNodeType.DIRECTORY, "admin", FilePermission.PUBLIC, -1, 0);
    }

    public FSNode getRoot() { return root; }

    /** Busca por ruta absoluta (ej. "/sistema/config.sys"). Devuelve null si no existe. */
    public FSNode find(String path) {
        if (path == null || !path.startsWith("/")) return null;
        FSNode current = root;
        int i = 1, n = path.length();
        while (i < n) {
            int j = path.indexOf('/', i);
            if (j < 0) j = n;
            String part = path.substring(i, j);
            if (!part.isEmpty()) {
                if (!current.isDirectory()) return null;
                current = current.findChild(part);
                if (current == null) return null;
            }
            i = j + 1;
        }
        return current;
    }

    /** Inserta un nodo existente bajo el directorio indicado. */
    public void insert(String parentPath, FSNode node) {
        FSNode parent = find(parentPath);
        if (parent == null) throw new IllegalArgumentException("No existe el directorio: " + parentPath);
        parent.addChild(node);
    }

    /** Crea e inserta un nodo a partir de su ruta completa; el padre debe existir. */
    public FSNode insertAtPath(String fullPath, FsNodeType type, String owner, FilePermission permission,
                               int firstBlock, int sizeBlocks) {
        if (fullPath == null || !fullPath.startsWith("/") || fullPath.length() < 2)
            throw new IllegalArgumentException("Ruta inválida: " + fullPath);
        String clean = fullPath.endsWith("/") ? fullPath.substring(0, fullPath.length() - 1) : fullPath;
        int cut = clean.lastIndexOf('/');
        String parentPath = cut == 0 ? "/" : clean.substring(0, cut);
        String name = clean.substring(cut + 1);
        FSNode node = new FSNode(name, type, owner, permission, firstBlock, sizeBlocks);
        insert(parentPath, node);
        return node;
    }

    /**
     * Elimina el nodo y todo su subárbol. Devuelve los ARCHIVOS eliminados para que
     * quien llama libere sus bloques en el disco. La raíz no se puede eliminar.
     */
    public MyLinkedList<FSNode> remove(String path) {
        FSNode node = find(path);
        if (node == null) throw new IllegalArgumentException("No existe: " + path);
        if (node == root) throw new IllegalArgumentException("No se puede eliminar la raíz");
        MyLinkedList<FSNode> files = new MyLinkedList<>();
        collectFiles(node, files);
        node.getParent().removeChild(node);
        return files;
    }

    /** Cambia el nombre de un nodo (no de la raíz) evitando duplicados. */
    public void rename(String path, String newName) {
        FSNode node = find(path);
        if (node == null || node == root) throw new IllegalArgumentException("No se puede renombrar: " + path);
        if (newName == null || newName.isEmpty() || newName.indexOf('/') >= 0)
            throw new IllegalArgumentException("Nombre inválido");
        FSNode sibling = node.getParent().findChild(newName);
        if (sibling != null && sibling != node) throw new IllegalArgumentException("Ya existe: " + newName);
        node.setName(newName);
    }

    /** Todos los archivos del subárbol (recursivo, en profundidad). */
    public MyLinkedList<FSNode> collectFiles(FSNode from) {
        MyLinkedList<FSNode> out = new MyLinkedList<>();
        collectFiles(from, out);
        return out;
    }

    private void collectFiles(FSNode node, MyLinkedList<FSNode> out) {
        if (node.isFile()) { out.append(node); return; }
        for (int i = 0; i < node.getChildren().size(); i++) collectFiles(node.getChildren().get(i), out);
    }

    /** Cantidad total de nodos (sin contar la raíz). */
    public int countNodes() { return count(root) - 1; }

    private int count(FSNode node) {
        int total = 1;
        for (int i = 0; i < node.getChildren().size(); i++) total += count(node.getChildren().get(i));
        return total;
    }
}