package com.avilaos.fs;

import com.avilaos.disk.SimulatedDisk;
import com.avilaos.filesystem.FilePermission;
import com.avilaos.filesystem.FileSystemManager;
import com.avilaos.filesystem.FSNode;
import com.avilaos.filesystem.FsNodeType;
import com.avilaos.io.FileSystemJsonSerializer;

public class DirectoryHierarchyAndRecursiveDeleteTest {

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError("FALLO: " + msg);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Iniciando DirectoryHierarchyAndRecursiveDeleteTest...");

        // 1. Crear disco de 100 bloques (80 datos + 20 swap)
        SimulatedDisk disk = new SimulatedDisk(80, 20, 10, 10);
        FileSystemManager fs = new FileSystemManager(disk);
        int freeInitial = disk.getFreeDataBlocks(); // 80 - 1 (raíz en 0) = 79

        check(freeInitial == 79, "Bloques libres iniciales deben ser 79");

        // 2. Crear estructura jerárquica: /usuarios (directorio)
        FSNode dirUsuarios = fs.createDirectory("/usuarios", "admin", FilePermission.PUBLIC, "SYSTEM");
        check(dirUsuarios != null, "/usuarios creado");
        check("/usuarios".equals(dirUsuarios.getPath()), "Ruta de directorio es /usuarios");
        check(disk.getFreeDataBlocks() == 78, "Directorio ocupa 1 bloque (78 libres)");

        // 3. Crear archivo /usuarios/video.mp4 (13 bloques como en el ejemplo del usuario)
        FSNode fileVideo = fs.createFile("/usuarios/video.mp4", 13, "admin", FilePermission.PRIVATE, "Proc_1", 1);
        check(fileVideo != null, "video.mp4 creado");
        check("/usuarios/video.mp4".equals(fileVideo.getPath()), 
                "El path del archivo debe contener su directorio: /usuarios/video.mp4 (obtenido: " + fileVideo.getPath() + ")");
        check(fileVideo.getParent() == dirUsuarios, "El padre de video.mp4 debe ser /usuarios");
        check(dirUsuarios.getChildren().size() == 1, "/usuarios debe tener 1 hijo");
        check(disk.getFreeDataBlocks() == 78 - 13, "video.mp4 ocupa 13 bloques (65 libres)");

        // 4. Crear subdirectorio /usuarios/fotos (1 bloque) y archivo /usuarios/fotos/foto1.jpg (4 bloques)
        FSNode dirFotos = fs.createDirectory("/usuarios/fotos", "admin", FilePermission.PUBLIC, "SYSTEM");
        check("/usuarios/fotos".equals(dirFotos.getPath()), "Ruta subdirectorio: /usuarios/fotos");
        check(dirFotos.getParent() == dirUsuarios, "Padre de fotos es /usuarios");

        FSNode fileFoto = fs.createFile("/usuarios/fotos/foto1.jpg", 4, "admin", FilePermission.PUBLIC, "Proc_2", 2);
        check("/usuarios/fotos/foto1.jpg".equals(fileFoto.getPath()), 
                "Ruta anidada profunda: /usuarios/fotos/foto1.jpg");
        check(fileFoto.getParent() == dirFotos, "Padre de foto1.jpg es fotos");
        check(disk.getFreeDataBlocks() == 65 - 1 - 4, "60 bloques libres");

        // 5. Crear archivo fuera de /usuarios en la raíz: /otro.txt (2 bloques)
        FSNode fileOtro = fs.createFile("/otro.txt", 2, "admin", FilePermission.PUBLIC, "Proc_3", 3);
        check("/otro.txt".equals(fileOtro.getPath()), "Ruta de archivo en raíz: /otro.txt");
        check(disk.getFreeDataBlocks() == 58, "58 bloques libres");

        // 6. Serializar a JSON y verificar que los paths exportados incluyan los directorios
        String json = FileSystemJsonSerializer.toJsonString(disk, fs);
        check(json.contains("\"path\": \"/usuarios\""), "JSON debe contener /usuarios");
        check(json.contains("\"path\": \"/usuarios/video.mp4\""), 
                "JSON debe contener /usuarios/video.mp4 con su directorio");
        check(json.contains("\"path\": \"/usuarios/fotos\""), "JSON debe contener /usuarios/fotos");
        check(json.contains("\"path\": \"/usuarios/fotos/foto1.jpg\""), 
                "JSON debe contener /usuarios/fotos/foto1.jpg con su jerarquía completa");
        check(json.contains("\"path\": \"/otro.txt\""), "JSON debe contener /otro.txt");

        // 7. Recargar desde el JSON exportado
        FileSystemJsonSerializer.LoadedFileSystem reloaded = FileSystemJsonSerializer.loadFromString(json, 20);
        FSNode reloadedVideo = reloaded.fileSystemManager().findNode("/usuarios/video.mp4");
        check(reloadedVideo != null, "video.mp4 debe encontrarse en el árbol recargado");
        check("/usuarios/video.mp4".equals(reloadedVideo.getPath()), 
                "Ruta recargada debe ser /usuarios/video.mp4");
        check(reloadedVideo.getSizeBlocks() == 13, "video.mp4 debe conservar 13 bloques");

        FSNode reloadedFoto = reloaded.fileSystemManager().findNode("/usuarios/fotos/foto1.jpg");
        check(reloadedFoto != null, "foto1.jpg debe encontrarse en el árbol recargado");
        check("/usuarios/fotos/foto1.jpg".equals(reloadedFoto.getPath()), "Ruta recargada de foto1.jpg");

        // 8. ELIMINAR EL DIRECTORIO /usuarios: debe borrar recursivamente /usuarios, /usuarios/video.mp4, /usuarios/fotos y /usuarios/fotos/foto1.jpg
        // Total de bloques a liberar: 1 (usuarios) + 13 (video.mp4) + 1 (fotos) + 4 (foto1.jpg) = 19 bloques!
        int freed = fs.deleteNode("/usuarios");
        check(freed == 19, "Al eliminar /usuarios se deben liberar 19 bloques (liberados: " + freed + ")");
        check(disk.getFreeDataBlocks() == 58 + 19, "Los bloques deben retornar al disco (77 libres)");

        // 9. Verificar que /usuarios y todo su contenido fueron removidos del árbol
        check(fs.findNode("/usuarios") == null, "/usuarios no debe existir");
        check(fs.findNode("/usuarios/video.mp4") == null, "/usuarios/video.mp4 no debe existir");
        check(fs.findNode("/usuarios/fotos") == null, "/usuarios/fotos no debe existir");
        check(fs.findNode("/usuarios/fotos/foto1.jpg") == null, "/usuarios/fotos/foto1.jpg no debe existir");

        // 10. Verificar que el archivo fuera del directorio (/otro.txt) NO fue afectado
        check(fs.findNode("/otro.txt") != null, "/otro.txt debe seguir existiendo intacto");
        check(fs.findNode("/otro.txt").getSizeBlocks() == 2, "/otro.txt conserva sus 2 bloques");

        System.out.println("PASS: DirectoryHierarchyAndRecursiveDeleteTest completado con éxito.");
    }
}
