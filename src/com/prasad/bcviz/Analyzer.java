package com.prasad.bcviz;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.Type;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.*;

/**
 * Ties together ClassInfoExtractor + Disassembler + OpcodeInfo + StackSimulator
 * into a single JSON document the frontend can render.
 */
public class Analyzer {

    public static String analyzeToJson(String classFilePath) throws IOException {
        ClassNode cn = ClassInfoExtractor.readClass(classFilePath);

        List<String> fieldsJson = new ArrayList<>();
        for (FieldNode f : cn.fields) {
            Map<String, String> fj = new LinkedHashMap<>();
            fj.put("name", Json.str(f.name));
            fj.put("type", Json.str(Type.getType(f.desc).getClassName()));
            fj.put("access", Json.str(accessString(f.access)));
            fieldsJson.add(Json.obj(fj));
        }

        List<String> methodsJson = new ArrayList<>();
        for (MethodNode m : cn.methods) {
            methodsJson.add(methodToJson(m, cn));
        }

        Map<String, String> root = new LinkedHashMap<>();
        root.put("name", Json.str(cn.name.replace('/', '.')));
        root.put("superName", Json.str(cn.superName != null ? cn.superName.replace('/', '.') : ""));
        root.put("access", Json.str(accessString(cn.access)));
        root.put("interfaces", Json.arr(namesToJsonStrings(cn.interfaces)));
        root.put("fields", Json.arr(fieldsJson));
        root.put("methods", Json.arr(methodsJson));
        return Json.obj(root);
    }

    private static String methodToJson(MethodNode m, ClassNode owner) {
        List<Disassembler.Instr> instrs = Disassembler.disassemble(m);
        List<StackSimulator.Step> steps = StackSimulator.simulate(m, owner);

        List<String> instrJson = new ArrayList<>();
        for (int i = 0; i < instrs.size(); i++) {
            Disassembler.Instr instr = instrs.get(i);
            StackSimulator.Step step = steps.get(i);
            Map<String, String> ij = new LinkedHashMap<>();
            ij.put("index", instr.index >= 0 ? String.valueOf(instr.index) : "null");
            ij.put("line", instr.lineNumber >= 0 ? String.valueOf(instr.lineNumber) : "null");
            ij.put("opcode", Json.str(instr.opcode));
            ij.put("operand", Json.str(instr.operand));
            ij.put("isLabel", instr.opcode.endsWith(":") ? "true" : "false");
            ij.put("description", Json.str(instr.opcode.endsWith(":") ? "" : OpcodeInfo.describe(rawOpcodeOf(instr))));
            ij.put("stack", Json.arr(namesToJsonStrings(step.stackAfter)));
            ij.put("note", Json.str(step.note));
            instrJson.add(Json.obj(ij));
        }

        Map<String, String> mj = new LinkedHashMap<>();
        mj.put("name", Json.str(m.name));
        mj.put("signature", Json.str(ClassInfoExtractor.readableSignature(m)));
        mj.put("access", Json.str(accessString(m.access)));
        mj.put("instructions", Json.arr(instrJson));
        return Json.obj(mj);
    }

    // We don't keep the raw opcode int on Disassembler.Instr, so re-derive it from
    // the mnemonic for description lookup. Simple reverse lookup against ASM's table.
    private static final Map<String, Integer> MNEMONIC_TO_OPCODE = new HashMap<>();
    static {
        for (int op = 0; op < org.objectweb.asm.util.Printer.OPCODES.length; op++) {
            String mnem = org.objectweb.asm.util.Printer.OPCODES[op];
            if (mnem != null && !mnem.isEmpty()) MNEMONIC_TO_OPCODE.put(mnem.toLowerCase(), op);
        }
    }
    private static int rawOpcodeOf(Disassembler.Instr instr) {
        Integer op = MNEMONIC_TO_OPCODE.get(instr.opcode);
        return op != null ? op : -1;
    }

    private static List<String> namesToJsonStrings(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) out.add(Json.str(n));
        return out;
    }

    private static String accessString(int access) {
        StringBuilder sb = new StringBuilder();
        if ((access & Opcodes.ACC_PUBLIC) != 0) sb.append("public ");
        if ((access & Opcodes.ACC_PRIVATE) != 0) sb.append("private ");
        if ((access & Opcodes.ACC_PROTECTED) != 0) sb.append("protected ");
        if ((access & Opcodes.ACC_STATIC) != 0) sb.append("static ");
        if ((access & Opcodes.ACC_FINAL) != 0) sb.append("final ");
        if ((access & Opcodes.ACC_ABSTRACT) != 0) sb.append("abstract ");
        return sb.toString().trim();
    }
}
