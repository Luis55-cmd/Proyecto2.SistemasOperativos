package com.avilaos.fs;

import com.avilaos.filesystem.FilePermission;
import com.avilaos.io.FileSystemJsonSerializer;

/** Los padres explícitos conservan sus atributos aunque se declaren después del hijo. */
public class JerarquiaMetadatosTest {
    public static void main(String[] args) {
        String encabezado = "{\"diskConfig\":{\"totalBlocks\":40,\"initialHead\":0},\"fileSystem\":[";
        String hija = "{\"path\":\"/a/b\",\"type\":\"DIRECTORY\",\"startBlock\":5,\"owner\":\"hija\",\"permissions\":\"public\"}";
        String padre = "{\"path\":\"/a\",\"type\":\"DIRECTORY\",\"startBlock\":10,\"owner\":\"ana\",\"permissions\":\"private\"}";
        var fs = FileSystemJsonSerializer.loadFromString(encabezado + hija + "," + padre + "]}", 0).fileSystemManager();
        var a = fs.findNode("/a");
        if (!"ana".equals(a.getOwner()) || a.getPermission() != FilePermission.PRIVATE
                || a.getFirstBlock() != 10 || fs.findNode("/a/b").getFirstBlock() != 5)
            throw new AssertionError("Se perdieron atributos del directorio declarado después");
        try {
            FileSystemJsonSerializer.loadFromString(encabezado + padre + "," + padre + "]}", 0);
            throw new AssertionError("Se aceptaron rutas duplicadas");
        } catch (IllegalArgumentException esperado) {
            if (!esperado.getMessage().contains("duplicada")) throw esperado;
        }
        System.out.println("PASS: orden de directorios conserva metadatos y rechaza duplicados");
    }
}
