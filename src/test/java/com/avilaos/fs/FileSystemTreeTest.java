package com.avilaos.fs;

import com.avilaos.core.structures.MyLinkedList;

/** Pruebas del árbol N-ario del sistema de archivos (ejecutar con main). */
public class FileSystemTreeTest {
    public static void main(String[] args) {
        FileSystemTree t = new FileSystemTree();
        t.insertAtPath("/sistema", FsNodeType.DIRECTORY, "admin", FilePermission.PUBLIC, -1, 0);
        t.insertAtPath("/usuarios", FsNodeType.DIRECTORY, "admin", FilePermission.PUBLIC, -1, 0);
        t.insertAtPath("/sistema/config.sys", FsNodeType.FILE, "admin", FilePermission.PUBLIC, 95, 1);
        t.insertAtPath("/sistema/lib", FsNodeType.DIRECTORY, "admin", FilePermission.PUBLIC, -1, 0);
        t.insertAtPath("/sistema/lib/core.dll", FsNodeType.FILE, "admin", FilePermission.PUBLIC, 64, 2);
        t.insertAtPath("/usuarios/notas.txt", FsNodeType.FILE, "user1", FilePermission.PRIVATE, 34, 1);

        check(t.countNodes() == 6, "conteo inicial");
        FSNode cfg = t.find("/sistema/config.sys");
        check(cfg != null && cfg.getFirstBlock() == 95 && cfg.getOwner().equals("admin"), "busqueda por ruta");
        check("/sistema/config.sys".equals(cfg.getPath()), "getPath");
        check(t.find("/") == t.getRoot(), "raiz");
        check(t.find("/nada/x") == null, "ruta inexistente");
        check(t.find("/sistema/config.sys/x") == null, "hijo de archivo");
        check(t.find("sistema") == null, "ruta relativa invalida");

        expectFail(() -> t.insertAtPath("/sistema/config.sys", FsNodeType.FILE, "a", FilePermission.PUBLIC, 1, 1), "duplicado");
        expectFail(() -> t.insertAtPath("/sistema/config.sys/x", FsNodeType.FILE, "a", FilePermission.PUBLIC, 1, 1), "hijo de archivo");
        expectFail(() -> t.insertAtPath("/noexiste/x", FsNodeType.FILE, "a", FilePermission.PUBLIC, 1, 1), "padre inexistente");
        expectFail(() -> t.remove("/"), "borrar raiz");

        t.rename("/usuarios/notas.txt", "apuntes.txt");
        check(t.find("/usuarios/apuntes.txt") != null && t.find("/usuarios/notas.txt") == null, "rename");

        MyLinkedList<FSNode> freed = t.remove("/sistema");
        check(freed.size() == 2, "archivos liberados por eliminacion recursiva");
        check(t.find("/sistema") == null && t.find("/sistema/lib/core.dll") == null, "subarbol eliminado");
        check(t.countNodes() == 2, "conteo final");
        System.out.println("TODAS LAS PRUEBAS DEL ARBOL N-ARIO PASARON.");
    }

    private static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    private static void expectFail(Runnable r, String msg) {
        try { r.run(); } catch (RuntimeException e) { return; }
        throw new AssertionError("debia fallar: " + msg);
    }
}