package com.prasad.bcviz;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

import java.util.HashMap;
import java.util.Map;

/**
 * Step 2: Disassembles a single method's bytecode into a list of readable
 * instruction lines: index, opcode mnemonic, and decoded operand(s).
 *
 * ASM represents each instruction as a subclass of AbstractInsnNode depending
 * on what kind of operand it carries (a variable slot, a constant, a jump
 * target, a method/field reference, etc). We switch on the concrete type
 * rather than the raw opcode, which is how ASM itself recommends walking
 * an InsnList.
 */
public class Disassembler {

    /** One decoded instruction, ready to print or serialize to JSON later. */
    public static class Instr {
        public final int index;       // position within the method
        public final String opcode;   // mnemonic, e.g. "iload_1"
        public final String operand;  // decoded operand text, or "" if none
        public final int lineNumber;  // -1 if unknown

        Instr(int index, String opcode, String operand, int lineNumber) {
            this.index = index;
            this.opcode = opcode;
            this.operand = operand;
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            String loc = lineNumber >= 0 ? "L" + lineNumber : "  ";
            String op = operand.isEmpty() ? "" : " " + operand;
            String idx = index >= 0 ? String.valueOf(index) : "";
            return String.format("  [%3s] %-4s %-15s%s", idx, loc, opcode, op);
        }
    }

    public static java.util.List<Instr> disassemble(MethodNode method) {
        java.util.List<Instr> result = new java.util.ArrayList<>();
        Map<Label, String> labelNames = new HashMap<>();
        int labelCounter = 0;

        // First pass: assign readable names (L0, L1, ...) to every label so
        // jump targets print as "-> L2" instead of a raw object hash.
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof LabelNode) {
                Label l = ((LabelNode) insn).getLabel();
                labelNames.put(l, "L" + labelCounter++);
            }
        }

        int index = 0;
        int currentLine = -1;

        for (AbstractInsnNode insn : method.instructions) {

            if (insn instanceof LineNumberNode) {
                currentLine = ((LineNumberNode) insn).line;
                continue; // metadata only, not a real instruction
            }
            if (insn instanceof LabelNode) {
                Label l = ((LabelNode) insn).getLabel();
                result.add(new Instr(-1, labelNames.get(l) + ":", "", currentLine));
                continue;
            }
            if (insn instanceof FrameNode) {
                continue; // stack-map frame metadata, not something a human reads as an "instruction"
            }

            String mnemonic = opcodeName(insn.getOpcode());
            String operand = decodeOperand(insn, labelNames);

            result.add(new Instr(index, mnemonic, operand, currentLine));
            index++;
        }
        return result;
    }

    /** Uses ASM's own opcode-name table so mnemonics always match the JVM spec. */
    private static String opcodeName(int opcode) {
        if (opcode < 0 || opcode >= Printer.OPCODES.length) return "?";
        return Printer.OPCODES[opcode].toLowerCase();
    }

    private static String decodeOperand(AbstractInsnNode insn, Map<Label, String> labelNames) {
        switch (insn.getType()) {
            case AbstractInsnNode.VAR_INSN: {
                VarInsnNode v = (VarInsnNode) insn;
                return "var " + v.var; // local variable slot index
            }
            case AbstractInsnNode.IINC_INSN: {
                IincInsnNode i = (IincInsnNode) insn;
                return "var " + i.var + " += " + i.incr;
            }
            case AbstractInsnNode.INT_INSN: {
                IntInsnNode i = (IntInsnNode) insn;
                return String.valueOf(i.operand); // e.g. bipush 42
            }
            case AbstractInsnNode.LDC_INSN: {
                LdcInsnNode l = (LdcInsnNode) insn;
                return "\"" + l.cst + "\""; // constant pushed by ldc
            }
            case AbstractInsnNode.JUMP_INSN: {
                JumpInsnNode j = (JumpInsnNode) insn;
                Label target = j.label.getLabel();
                return "-> " + labelNames.get(target);
            }
            case AbstractInsnNode.FIELD_INSN: {
                FieldInsnNode f = (FieldInsnNode) insn;
                return f.owner.replace('/', '.') + "." + f.name + " : " + f.desc;
            }
            case AbstractInsnNode.METHOD_INSN: {
                MethodInsnNode m = (MethodInsnNode) insn;
                return m.owner.replace('/', '.') + "." + m.name + m.desc;
            }
            case AbstractInsnNode.TYPE_INSN: {
                TypeInsnNode t = (TypeInsnNode) insn;
                return t.desc.replace('/', '.');
            }
            case AbstractInsnNode.MULTIANEWARRAY_INSN: {
                MultiANewArrayInsnNode m = (MultiANewArrayInsnNode) insn;
                return m.desc + " dims=" + m.dims;
            }
            default:
                return ""; // zero-operand instructions like iadd, dup, return
        }
    }

    // Quick manual test for this step
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "samples/Sample.class";
        String methodFilter = args.length > 1 ? args[1] : null;

        org.objectweb.asm.tree.ClassNode cn = ClassInfoExtractor.readClass(path);
        for (MethodNode m : cn.methods) {
            if (methodFilter != null && !m.name.equals(methodFilter)) continue;
            System.out.println("=== " + ClassInfoExtractor.readableSignature(m) + " ===");
            for (Instr instr : disassemble(m)) {
                System.out.println(instr);
            }
            System.out.println();
        }
    }
}
