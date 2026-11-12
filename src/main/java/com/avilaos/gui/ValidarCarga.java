package com.avilaos.gui;

import com.google.gson.*;
import com.google.gson.stream.*;
import com.avilaos.core.model.*;
import java.io.StringReader;

/** Valida el archivo completo antes de sustituir la simulación. */
final class ValidarCarga {
    private static IllegalArgumentException error(String s) { return new IllegalArgumentException(s); }
    private static int numero(JsonObject o, String k, int min) {
        JsonElement e = o.get(k);
        try {
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw error("Falta número: " + k);
            int n = new java.math.BigDecimal(e.getAsString()).intValueExact();
            if (n < min) throw error("Valor inválido: " + k);
            return n;
        } catch (ArithmeticException | NumberFormatException ex) { throw error("Número inválido: " + k); }
    }
    private static String texto(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw error("Falta texto: " + k);
        String s = e.getAsString();
        if (s.trim().isEmpty() || s.chars().anyMatch(c -> c < 32))
            throw error("Texto inválido: " + k);
        return s;
    }
    private static JsonArray lista(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonArray()) throw error("Falta lista: " + k);
        return o.getAsJsonArray(k);
    }
    private static JsonObject objeto(JsonElement e) {
        if (!e.isJsonObject()) throw error("Se esperaba un objeto");
        return e.getAsJsonObject();
    }
    static String validar(String json) throws Exception {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setStrictness(Strictness.STRICT);
        JsonElement doc = JsonParser.parseReader(reader);
        if (reader.peek() != JsonToken.END_DOCUMENT) throw error("Contenido adicional después del JSON");
        JsonObject root = objeto(doc);
        int currentCycle = numero(root,"currentCycle",0);
        if (currentCycle == Integer.MAX_VALUE) throw error("Ciclo inicial demasiado grande");
        numero(root,"cycleDurationMs",1); numero(root,"networkLatency",0);
        JsonArray cs=lista(root,"computers"), bs=lista(root,"buffers"), ps=lista(root,"processes");
        if (cs.size() == 0) throw error("Falta al menos un computador");
        int[] ram = new int[cs.size()], usada = new int[cs.size()];
        String[] nombres = new String[cs.size()];
        for (int i=0;i<cs.size();i++) {
            JsonObject c=objeto(cs.get(i)); nombres[i]=texto(c,"name").trim();
            for (int j=0;j<i;j++) if(nombres[i].equalsIgnoreCase(nombres[j])) throw error("Nombres de computadores duplicados: " + nombres[i]);
            ram[i]=numero(c,"ramSize",1); numero(c,"quantum",1);
            try { SchedulingPolicyType.valueOf(texto(c,"policy")); } catch(IllegalArgumentException e) { throw error("Política inválida en " + nombres[i]); }
        }
        for (int i=0;i<bs.size();i++) {
            JsonObject b=objeto(bs.get(i)); int id=numero(b,"id",1), host=numero(b,"hostComputerId",1), size=numero(b,"storage",1);
            if(b.has("latency") && !b.get("latency").isJsonNull()) numero(b,"latency",0);
            if(id!=i+1) throw error("Los IDs de buffers deben ser consecutivos desde 1");
            if(host>ram.length) throw error("Anfitrión de buffer inexistente");
            if ((long)usada[host-1]+size>ram[host-1]) throw error("Los buffers exceden la RAM del computador " + host);
            usada[host-1]+=size;
        }
        for (int i=0;i<ps.size();i++) {
            JsonObject p=objeto(ps.get(i)); int id=numero(p,"id",1), host=numero(p,"computerId",1), mem=numero(p,"assignedMemory",0);
            if (id == Integer.MAX_VALUE) throw error("ID de proceso demasiado grande");
            texto(p,"name"); int instrucciones=numero(p,"totalInstructions",1); numero(p,"priority",1); numero(p,"deadline",1);
            if(host>ram.length || mem>ram[host-1]) throw error("RAM o computador inválido para proceso " + id);
            for(int j=0;j<i;j++) if(numero(objeto(ps.get(j)),"id",1)==id) throw error("ID de proceso duplicado: " + id);
            ProcessState state; ProcessType type;
            try { state=ProcessState.valueOf(texto(p,"state")); type=ProcessType.valueOf(texto(p,"type")); BlockReason.valueOf(texto(p,"blockReason")); }
            catch(IllegalArgumentException e) { throw error("Tipo, estado o bloqueo inválido del proceso " + id); }
            int remTime=numero(p,"remainingTime",0); numero(p,"remainingDeadline",0);
            int pc=numero(p,"programCounter",0); int mar=numero(p,"memoryAddressRegister",0);
            numero(p,"ioInterval",0); numero(p,"ioDuration",0); numero(p,"ioCycleCountdown",0);
            if (p.has("ioRequestInstruction") && (!p.get("ioRequestInstruction").isJsonPrimitive()
                    || !p.getAsJsonPrimitive("ioRequestInstruction").isBoolean()))
                throw error("Indicador de instrucción E/S inválido del proceso " + id);
            numero(p,"cyclesPerOperation",0); numero(p,"requiredElements",0); int processed=numero(p,"processedElements",0);
            if(pc==Integer.MAX_VALUE || mar==Integer.MAX_VALUE || processed==Integer.MAX_VALUE
                    || (long)pc+remTime>Integer.MAX_VALUE || (long)mar+remTime>Integer.MAX_VALUE)
                throw error("Contadores del proceso desbordan durante el ciclo: " + id);
            if(state==ProcessState.LISTO || state==ProcessState.EJECUTANDO || state==ProcessState.BLOQUEADO) {
                if(mem==0 || (long)usada[host-1]+mem>ram[host-1]) throw error("Procesos activos exceden la RAM del computador " + host);
                usada[host-1]+=mem;
            }
            if(type==ProcessType.PRODUCER || type==ProcessType.CONSUMER) {
                String target=texto(p,"targetBufferId");
                try { int b=Integer.parseInt(target); if(b<1 || b>bs.size()) throw error("Buffer inexistente"); }
                catch(NumberFormatException e) { throw error("Buffer inválido del proceso " + id); }
                int cycles=numero(p,"cyclesPerOperation",1); int elems=numero(p,"requiredElements",1);
                try {
                    int totalSync = Math.multiplyExact(elems, Math.addExact(cycles, 5));
                    if ((long)pc+totalSync>Integer.MAX_VALUE || (long)mar+totalSync>Integer.MAX_VALUE)
                        throw error("Registros del proceso desbordan durante el ciclo: " + id);
                } catch (ArithmeticException ex) {
                    throw error("Desbordamiento en instrucciones de sincronización del proceso " + id);
                }
            }
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }
}
