package com.prasad.bcviz;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Step 3b: Resolves the "variable" stack effects that OpcodeInfo's static
 * table can't hardcode - because they depend on the specific instruction's
 * operand, not just its opcode.
 *
 * Example: INVOKEVIRTUAL always has opcode 182, but calling
 * "int add(int,int)" pops 3 values (objectref + 2 args) and pushes 1,
 * while calling "void log(String)" pops 2 and pushes 0. Same opcode,
 * different effect - so this has to be computed per-instruction from
 * the method/field descriptor, using ASM's Type class to measure how many
 * stack slots each parameter/return type occupies (longs and doubles take
 * 2 slots, everything else takes 1).
 */
public class StackEffectCalculator {

    public static OpcodeInfo.StackEffect effectOf(AbstractInsnNode insn) {
        OpcodeInfo.Entry fixed = OpcodeInfo.lookup(insn.getOpcode());
        if (fixed != null && fixed.fixedEffect != null) {
            return fixed.fixedEffect; // most instructions: table lookup is enough
        }

        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN:
                return methodEffect((MethodInsnNode) insn);
            case AbstractInsnNode.FIELD_INSN:
                return fieldEffect((FieldInsnNode) insn);
            case AbstractInsnNode.LDC_INSN:
                return ldcEffect((LdcInsnNode) insn);
            case AbstractInsnNode.MULTIANEWARRAY_INSN:
                return new OpcodeInfo.StackEffect(((MultiANewArrayInsnNode) insn).dims, 1);
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
                return invokeDynamicEffect((InvokeDynamicInsnNode) insn);
            default:
                return new OpcodeInfo.StackEffect(0, 0);
        }
    }

    private static OpcodeInfo.StackEffect methodEffect(MethodInsnNode m) {
        Type methodType = Type.getMethodType(m.desc);
        int argSlots = slotsOf(methodType.getArgumentTypes());
        // instance calls (virtual/special/interface) also pop the object
        // reference they're called on; static calls don't.
        int objectRefSlots = (m.getOpcode() == Opcodes.INVOKESTATIC) ? 0 : 1;
        int pop = argSlots + objectRefSlots;
        int push = methodType.getReturnType().equals(Type.VOID_TYPE) ? 0 : slotsOf(methodType.getReturnType());
        return new OpcodeInfo.StackEffect(pop, push);
    }

    private static OpcodeInfo.StackEffect fieldEffect(FieldInsnNode f) {
        int typeSlots = slotsOf(Type.getType(f.desc));
        switch (f.getOpcode()) {
            case Opcodes.GETSTATIC: return new OpcodeInfo.StackEffect(0, typeSlots);
            case Opcodes.PUTSTATIC: return new OpcodeInfo.StackEffect(typeSlots, 0);
            case Opcodes.GETFIELD:  return new OpcodeInfo.StackEffect(1, typeSlots);          // pop objectref, push value
            case Opcodes.PUTFIELD:  return new OpcodeInfo.StackEffect(typeSlots + 1, 0);       // pop objectref + value
            default: return new OpcodeInfo.StackEffect(0, 0);
        }
    }

    private static OpcodeInfo.StackEffect ldcEffect(LdcInsnNode l) {
        // ldc2_w (implicit for long/double constants) pushes 2 slots; everything else pushes 1
        boolean wide = (l.cst instanceof Long) || (l.cst instanceof Double);
        return new OpcodeInfo.StackEffect(0, wide ? 2 : 1);
    }

    private static OpcodeInfo.StackEffect invokeDynamicEffect(InvokeDynamicInsnNode i) {
        Type methodType = Type.getMethodType(i.desc);
        int pop = slotsOf(methodType.getArgumentTypes()); // captured values, no objectref (it's a static-like call site)
        int push = methodType.getReturnType().equals(Type.VOID_TYPE) ? 0 : slotsOf(methodType.getReturnType());
        return new OpcodeInfo.StackEffect(pop, push);
    }

    /** long and double each occupy 2 stack slots; everything else occupies 1. */
    private static int slotsOf(Type t) {
        return (t.equals(Type.LONG_TYPE) || t.equals(Type.DOUBLE_TYPE)) ? 2 : 1;
    }

    private static int slotsOf(Type[] types) {
        int total = 0;
        for (Type t : types) total += slotsOf(t);
        return total;
    }

    // Quick manual test - verify against a method with field access + a method call
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "samples/Sample.class";
        org.objectweb.asm.tree.ClassNode cn = ClassInfoExtractor.readClass(path);
        for (MethodNode m : cn.methods) {
            System.out.println("=== " + ClassInfoExtractor.readableSignature(m) + " ===");
            for (AbstractInsnNode insn : m.instructions) {
                if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
                OpcodeInfo.StackEffect eff = effectOf(insn);
                String mnemonic = org.objectweb.asm.util.Printer.OPCODES[insn.getOpcode()].toLowerCase();
                System.out.printf("  %-15s pop=%d push=%d  // %s%n",
                        mnemonic, eff.pop, eff.push, OpcodeInfo.describe(insn.getOpcode()));
            }
        }
    }
}
