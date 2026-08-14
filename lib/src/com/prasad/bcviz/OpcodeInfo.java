package com.prasad.bcviz;

import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

/**
 * Step 3: For every JVM opcode, provides:
 *   1. A plain-English description (for the UI to show on hover/selection)
 *   2. Its static stack effect: how many values it pops and pushes
 *
 * Stack effect numbers come directly from the JVM Specification, chapter 6
 * ("The Java Virtual Machine Instruction Set"). Most instructions have a
 * FIXED stack effect we can hardcode. A handful are variable (invoke*,
 * multianewarray) because they depend on the method descriptor / dimension
 * count - those are handled specially rather than hardcoded, since the
 * "pop count" depends on the specific instruction's operand, not just its
 * opcode.
 */
public class OpcodeInfo {

    /** pop = values removed from stack top, push = values added after. */
    public static class StackEffect {
        public final int pop;
        public final int push;
        public StackEffect(int pop, int push) { this.pop = pop; this.push = push; }
    }

    public static class Entry {
        public final String description;
        public final StackEffect fixedEffect; // null if variable (computed per-instruction instead)
        Entry(String description, StackEffect fixedEffect) {
            this.description = description;
            this.fixedEffect = fixedEffect;
        }
    }

    private static final Map<Integer, Entry> TABLE = new HashMap<>();

    private static void def(int opcode, String desc, int pop, int push) {
        TABLE.put(opcode, new Entry(desc, new StackEffect(pop, push)));
    }

    private static void defVariable(int opcode, String desc) {
        TABLE.put(opcode, new Entry(desc, null));
    }

    static {
        // --- Constants ---
        def(Opcodes.NOP, "Do nothing", 0, 0);
        def(Opcodes.ACONST_NULL, "Push null reference", 0, 1);
        def(Opcodes.ICONST_M1, "Push int constant -1", 0, 1);
        def(Opcodes.ICONST_0, "Push int constant 0", 0, 1);
        def(Opcodes.ICONST_1, "Push int constant 1", 0, 1);
        def(Opcodes.ICONST_2, "Push int constant 2", 0, 1);
        def(Opcodes.ICONST_3, "Push int constant 3", 0, 1);
        def(Opcodes.ICONST_4, "Push int constant 4", 0, 1);
        def(Opcodes.ICONST_5, "Push int constant 5", 0, 1);
        def(Opcodes.LCONST_0, "Push long constant 0", 0, 2);
        def(Opcodes.LCONST_1, "Push long constant 1", 0, 2);
        def(Opcodes.FCONST_0, "Push float constant 0.0", 0, 1);
        def(Opcodes.FCONST_1, "Push float constant 1.0", 0, 1);
        def(Opcodes.FCONST_2, "Push float constant 2.0", 0, 1);
        def(Opcodes.DCONST_0, "Push double constant 0.0", 0, 2);
        def(Opcodes.DCONST_1, "Push double constant 1.0", 0, 2);
        def(Opcodes.BIPUSH, "Push byte-sized int constant (sign-extended)", 0, 1);
        def(Opcodes.SIPUSH, "Push short-sized int constant (sign-extended)", 0, 1);
        defVariable(Opcodes.LDC, "Push constant from constant pool (int/float/String/Class)");

        // --- Loads (local variable -> stack) ---
        def(Opcodes.ILOAD, "Push int from local variable", 0, 1);
        def(Opcodes.LLOAD, "Push long from local variable", 0, 2);
        def(Opcodes.FLOAD, "Push float from local variable", 0, 1);
        def(Opcodes.DLOAD, "Push double from local variable", 0, 2);
        def(Opcodes.ALOAD, "Push object reference from local variable", 0, 1);
        def(Opcodes.IALOAD, "Push int from array: arrayref, index -> value", 2, 1);
        def(Opcodes.LALOAD, "Push long from array", 2, 2);
        def(Opcodes.FALOAD, "Push float from array", 2, 1);
        def(Opcodes.DALOAD, "Push double from array", 2, 2);
        def(Opcodes.AALOAD, "Push object reference from array", 2, 1);
        def(Opcodes.BALOAD, "Push byte/boolean from array", 2, 1);
        def(Opcodes.CALOAD, "Push char from array", 2, 1);
        def(Opcodes.SALOAD, "Push short from array", 2, 1);

        // --- Stores (stack -> local variable) ---
        def(Opcodes.ISTORE, "Pop int, store into local variable", 1, 0);
        def(Opcodes.LSTORE, "Pop long, store into local variable", 2, 0);
        def(Opcodes.FSTORE, "Pop float, store into local variable", 1, 0);
        def(Opcodes.DSTORE, "Pop double, store into local variable", 2, 0);
        def(Opcodes.ASTORE, "Pop object reference, store into local variable", 1, 0);
        def(Opcodes.IASTORE, "Store int into array: arrayref, index, value ->", 3, 0);
        def(Opcodes.LASTORE, "Store long into array", 4, 0);
        def(Opcodes.FASTORE, "Store float into array", 3, 0);
        def(Opcodes.DASTORE, "Store double into array", 4, 0);
        def(Opcodes.AASTORE, "Store object reference into array", 3, 0);
        def(Opcodes.BASTORE, "Store byte/boolean into array", 3, 0);
        def(Opcodes.CASTORE, "Store char into array", 3, 0);
        def(Opcodes.SASTORE, "Store short into array", 3, 0);

        // --- Stack manipulation ---
        def(Opcodes.POP, "Discard top stack value", 1, 0);
        def(Opcodes.POP2, "Discard top 2 stack values (or 1 wide value)", 2, 0);
        def(Opcodes.DUP, "Duplicate top stack value", 1, 2);
        def(Opcodes.DUP_X1, "Duplicate top value, insert 2 down", 2, 3);
        def(Opcodes.DUP_X2, "Duplicate top value, insert 3 down", 3, 4);
        def(Opcodes.DUP2, "Duplicate top 2 values", 2, 4);
        def(Opcodes.DUP2_X1, "Duplicate top 2 values, insert 3 down", 3, 5);
        def(Opcodes.DUP2_X2, "Duplicate top 2 values, insert 4 down", 4, 6);
        def(Opcodes.SWAP, "Swap top two stack values", 2, 2);

        // --- Arithmetic ---
        def(Opcodes.IADD, "Pop 2 ints, push their sum", 2, 1);
        def(Opcodes.LADD, "Pop 2 longs, push their sum", 4, 2);
        def(Opcodes.FADD, "Pop 2 floats, push their sum", 2, 1);
        def(Opcodes.DADD, "Pop 2 doubles, push their sum", 4, 2);
        def(Opcodes.ISUB, "Pop 2 ints, push their difference", 2, 1);
        def(Opcodes.LSUB, "Pop 2 longs, push their difference", 4, 2);
        def(Opcodes.FSUB, "Pop 2 floats, push their difference", 2, 1);
        def(Opcodes.DSUB, "Pop 2 doubles, push their difference", 4, 2);
        def(Opcodes.IMUL, "Pop 2 ints, push their product", 2, 1);
        def(Opcodes.LMUL, "Pop 2 longs, push their product", 4, 2);
        def(Opcodes.FMUL, "Pop 2 floats, push their product", 2, 1);
        def(Opcodes.DMUL, "Pop 2 doubles, push their product", 4, 2);
        def(Opcodes.IDIV, "Pop 2 ints, push their quotient", 2, 1);
        def(Opcodes.LDIV, "Pop 2 longs, push their quotient", 4, 2);
        def(Opcodes.FDIV, "Pop 2 floats, push their quotient", 2, 1);
        def(Opcodes.DDIV, "Pop 2 doubles, push their quotient", 4, 2);
        def(Opcodes.IREM, "Pop 2 ints, push remainder (modulo)", 2, 1);
        def(Opcodes.LREM, "Pop 2 longs, push remainder", 4, 2);
        def(Opcodes.FREM, "Pop 2 floats, push remainder", 2, 1);
        def(Opcodes.DREM, "Pop 2 doubles, push remainder", 4, 2);
        def(Opcodes.INEG, "Negate top int", 1, 1);
        def(Opcodes.LNEG, "Negate top long", 2, 2);
        def(Opcodes.FNEG, "Negate top float", 1, 1);
        def(Opcodes.DNEG, "Negate top double", 2, 2);
        def(Opcodes.ISHL, "Shift int left", 2, 1);
        def(Opcodes.LSHL, "Shift long left", 3, 2);
        def(Opcodes.ISHR, "Shift int right (arithmetic)", 2, 1);
        def(Opcodes.LSHR, "Shift long right (arithmetic)", 3, 2);
        def(Opcodes.IUSHR, "Shift int right (logical)", 2, 1);
        def(Opcodes.LUSHR, "Shift long right (logical)", 3, 2);
        def(Opcodes.IAND, "Bitwise AND on ints", 2, 1);
        def(Opcodes.LAND, "Bitwise AND on longs", 4, 2);
        def(Opcodes.IOR, "Bitwise OR on ints", 2, 1);
        def(Opcodes.LOR, "Bitwise OR on longs", 4, 2);
        def(Opcodes.IXOR, "Bitwise XOR on ints", 2, 1);
        def(Opcodes.LXOR, "Bitwise XOR on longs", 4, 2);
        def(Opcodes.IINC, "Increment local int variable by constant, in place", 0, 0);

        // --- Conversions ---
        def(Opcodes.I2L, "Convert int to long", 1, 2);
        def(Opcodes.I2F, "Convert int to float", 1, 1);
        def(Opcodes.I2D, "Convert int to double", 1, 2);
        def(Opcodes.L2I, "Convert long to int", 2, 1);
        def(Opcodes.L2F, "Convert long to float", 2, 1);
        def(Opcodes.L2D, "Convert long to double", 2, 2);
        def(Opcodes.F2I, "Convert float to int", 1, 1);
        def(Opcodes.F2L, "Convert float to long", 1, 2);
        def(Opcodes.F2D, "Convert float to double", 1, 2);
        def(Opcodes.D2I, "Convert double to int", 2, 1);
        def(Opcodes.D2L, "Convert double to long", 2, 2);
        def(Opcodes.D2F, "Convert double to float", 2, 1);
        def(Opcodes.I2B, "Convert int to byte", 1, 1);
        def(Opcodes.I2C, "Convert int to char", 1, 1);
        def(Opcodes.I2S, "Convert int to short", 1, 1);

        // --- Comparisons ---
        def(Opcodes.LCMP, "Compare 2 longs, push -1/0/1", 4, 1);
        def(Opcodes.FCMPL, "Compare 2 floats (NaN -> -1), push -1/0/1", 2, 1);
        def(Opcodes.FCMPG, "Compare 2 floats (NaN -> 1), push -1/0/1", 2, 1);
        def(Opcodes.DCMPL, "Compare 2 doubles (NaN -> -1), push -1/0/1", 4, 1);
        def(Opcodes.DCMPG, "Compare 2 doubles (NaN -> 1), push -1/0/1", 4, 1);

        // --- Conditional branches (all pop, none push - they only redirect control flow) ---
        def(Opcodes.IFEQ, "Pop int, jump if == 0", 1, 0);
        def(Opcodes.IFNE, "Pop int, jump if != 0", 1, 0);
        def(Opcodes.IFLT, "Pop int, jump if < 0", 1, 0);
        def(Opcodes.IFGE, "Pop int, jump if >= 0", 1, 0);
        def(Opcodes.IFGT, "Pop int, jump if > 0", 1, 0);
        def(Opcodes.IFLE, "Pop int, jump if <= 0", 1, 0);
        def(Opcodes.IF_ICMPEQ, "Pop 2 ints, jump if equal", 2, 0);
        def(Opcodes.IF_ICMPNE, "Pop 2 ints, jump if not equal", 2, 0);
        def(Opcodes.IF_ICMPLT, "Pop 2 ints, jump if first < second", 2, 0);
        def(Opcodes.IF_ICMPGE, "Pop 2 ints, jump if first >= second", 2, 0);
        def(Opcodes.IF_ICMPGT, "Pop 2 ints, jump if first > second", 2, 0);
        def(Opcodes.IF_ICMPLE, "Pop 2 ints, jump if first <= second", 2, 0);
        def(Opcodes.IF_ACMPEQ, "Pop 2 references, jump if same object", 2, 0);
        def(Opcodes.IF_ACMPNE, "Pop 2 references, jump if different objects", 2, 0);
        def(Opcodes.GOTO, "Unconditional jump", 0, 0);
        def(Opcodes.IFNULL, "Pop reference, jump if null", 1, 0);
        def(Opcodes.IFNONNULL, "Pop reference, jump if not null", 1, 0);

        // --- Returns ---
        def(Opcodes.IRETURN, "Return int from method", 1, 0);
        def(Opcodes.LRETURN, "Return long from method", 2, 0);
        def(Opcodes.FRETURN, "Return float from method", 1, 0);
        def(Opcodes.DRETURN, "Return double from method", 2, 0);
        def(Opcodes.ARETURN, "Return object reference from method", 1, 0);
        def(Opcodes.RETURN, "Return void from method", 0, 0);

        // --- Objects / fields / arrays ---
        defVariable(Opcodes.GETSTATIC, "Push value of static field");
        defVariable(Opcodes.PUTSTATIC, "Pop value, store into static field");
        defVariable(Opcodes.GETFIELD, "Pop objectref, push value of instance field");
        defVariable(Opcodes.PUTFIELD, "Pop value and objectref, store into instance field");
        defVariable(Opcodes.INVOKEVIRTUAL, "Invoke instance method (virtual dispatch)");
        defVariable(Opcodes.INVOKESPECIAL, "Invoke constructor/private/super method");
        defVariable(Opcodes.INVOKESTATIC, "Invoke static method");
        defVariable(Opcodes.INVOKEINTERFACE, "Invoke interface method");
        defVariable(Opcodes.INVOKEDYNAMIC, "Invoke dynamically-computed call site (lambdas, string concat)");
        def(Opcodes.NEW, "Allocate new object (uninitialized)", 0, 1);
        def(Opcodes.NEWARRAY, "Allocate new primitive array", 1, 1);
        def(Opcodes.ANEWARRAY, "Allocate new object reference array", 1, 1);
        def(Opcodes.ARRAYLENGTH, "Pop arrayref, push its length", 1, 1);
        def(Opcodes.ATHROW, "Pop exception object, throw it", 1, 0);
        def(Opcodes.CHECKCAST, "Verify top of stack is instance of type (or throw)", 1, 1);
        def(Opcodes.INSTANCEOF, "Pop objectref, push 1/0 for type check", 1, 1);
        def(Opcodes.MONITORENTER, "Pop objectref, acquire its monitor lock", 1, 0);
        def(Opcodes.MONITOREXIT, "Pop objectref, release its monitor lock", 1, 0);
        defVariable(Opcodes.MULTIANEWARRAY, "Allocate new multi-dimensional array");
    }

    public static Entry lookup(int opcode) {
        return TABLE.get(opcode);
    }

    public static String describe(int opcode) {
        Entry e = TABLE.get(opcode);
        return e != null ? e.description : "(no description available)";
    }
}
