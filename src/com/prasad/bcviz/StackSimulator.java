package com.prasad.bcviz;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Step 4: Walks a method's instructions in order and maintains a SYMBOLIC
 * operand stack - not just a slot count, but readable expressions like
 * "this", "2", "(a + b)", "this.add(2, 3)" - so the output reads like an
 * annotated trace of what the method actually computes.
 *
 * This is deliberately NOT a full data-flow analysis (no branching into
 * multiple possible states at a jump, no merging at labels). It simulates
 * ONE linear pass through the instruction list in source order, which is
 * exactly what happens for straight-line code and gives an honest, readable
 * trace for loops/branches too (their bodies are still linear between jumps -
 * we just don't fork into "what if the branch is/isn't taken" as two paths).
 */
public class StackSimulator {

    public static class Step {
        public final Disassembler.Instr instr;
        public final List<String> stackAfter;
        public final String note; // optional human explanation of what just happened

        Step(Disassembler.Instr instr, List<String> stackAfter, String note) {
            this.instr = instr;
            this.stackAfter = stackAfter;
            this.note = note;
        }
    }

    private static final Map<Integer, String> BIN_OP = new HashMap<>();
    static {
        BIN_OP.put(Opcodes.IADD, "+"); BIN_OP.put(Opcodes.LADD, "+"); BIN_OP.put(Opcodes.FADD, "+"); BIN_OP.put(Opcodes.DADD, "+");
        BIN_OP.put(Opcodes.ISUB, "-"); BIN_OP.put(Opcodes.LSUB, "-"); BIN_OP.put(Opcodes.FSUB, "-"); BIN_OP.put(Opcodes.DSUB, "-");
        BIN_OP.put(Opcodes.IMUL, "*"); BIN_OP.put(Opcodes.LMUL, "*"); BIN_OP.put(Opcodes.FMUL, "*"); BIN_OP.put(Opcodes.DMUL, "*");
        BIN_OP.put(Opcodes.IDIV, "/"); BIN_OP.put(Opcodes.LDIV, "/"); BIN_OP.put(Opcodes.FDIV, "/"); BIN_OP.put(Opcodes.DDIV, "/");
        BIN_OP.put(Opcodes.IREM, "%"); BIN_OP.put(Opcodes.LREM, "%");
        BIN_OP.put(Opcodes.IAND, "&"); BIN_OP.put(Opcodes.IOR, "|"); BIN_OP.put(Opcodes.IXOR, "^");
        BIN_OP.put(Opcodes.ISHL, "<<"); BIN_OP.put(Opcodes.ISHR, ">>"); BIN_OP.put(Opcodes.IUSHR, ">>>");
    }

    /** locals[0] = "this" for instance methods; params named by position for readability. */
    private static Map<Integer, String> initialLocals(MethodNode method, org.objectweb.asm.tree.ClassNode owner) {
        Map<Integer, String> locals = new HashMap<>();
        int slot = 0;
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        if (!isStatic) {
            locals.put(slot, "this");
            slot++;
        }
        Type[] argTypes = Type.getMethodType(method.desc).getArgumentTypes();
        for (int i = 0; i < argTypes.length; i++) {
            locals.put(slot, "arg" + i);
            slot += (argTypes[i].equals(Type.LONG_TYPE) || argTypes[i].equals(Type.DOUBLE_TYPE)) ? 2 : 1;
        }
        return locals;
    }

    public static List<Step> simulate(MethodNode method, org.objectweb.asm.tree.ClassNode owner) {
        List<Step> steps = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>(); // top = head
        Map<Integer, String> locals = initialLocals(method, owner);
        List<Disassembler.Instr> instrs = Disassembler.disassemble(method);

        // We need the raw AbstractInsnNode alongside each printed Instr to know
        // exact opcode/operand types, so walk both in lockstep. Disassembler
        // includes LabelNode markers in its output list (but not LineNumber/Frame
        // nodes), so we must do the same here to stay aligned index-for-index.
        int i = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof LineNumberNode || insn instanceof FrameNode) continue;
            Disassembler.Instr printed = instrs.get(i++);
            if (insn instanceof LabelNode) {
                steps.add(new Step(printed, new ArrayList<>(stack), "")); // label: no stack change
                continue;
            }
            String note = apply(insn, stack, locals);
            steps.add(new Step(printed, new ArrayList<>(stack), note));
        }
        return steps;
    }

    /** Mutates `stack`/`locals` per the instruction's real semantics; returns a short human note. */
    private static String apply(AbstractInsnNode insn, Deque<String> stack, Map<Integer, String> locals) {
        int op = insn.getOpcode();

        // --- constants ---
        if (op == Opcodes.ACONST_NULL) { stack.push("null"); return "push null"; }
        if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) { int v = op - Opcodes.ICONST_0; stack.push(String.valueOf(v)); return "push " + v; }
        if (op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1) { stack.push(String.valueOf(op - Opcodes.LCONST_0) + "L"); return "push long const"; }
        if (insn instanceof IntInsnNode) { int v = ((IntInsnNode) insn).operand; stack.push(String.valueOf(v)); return "push " + v; }
        if (insn instanceof LdcInsnNode) { Object c = ((LdcInsnNode) insn).cst; String s = (c instanceof String) ? "\"" + c + "\"" : String.valueOf(c); stack.push(s); return "push constant " + s; }

        // --- loads / stores ---
        if (insn instanceof VarInsnNode) {
            VarInsnNode v = (VarInsnNode) insn;
            boolean isStore = op == Opcodes.ISTORE || op == Opcodes.LSTORE || op == Opcodes.FSTORE || op == Opcodes.DSTORE || op == Opcodes.ASTORE;
            if (isStore) {
                String val = stack.isEmpty() ? "?" : stack.pop();
                locals.put(v.var, val);
                return "var" + v.var + " = " + val;
            } else {
                String val = locals.getOrDefault(v.var, "var" + v.var);
                stack.push(val);
                return "push " + val;
            }
        }
        if (insn instanceof IincInsnNode) {
            IincInsnNode ii = (IincInsnNode) insn;
            String cur = locals.getOrDefault(ii.var, "var" + ii.var);
            String updated = "(" + cur + (ii.incr >= 0 ? " + " : " - ") + Math.abs(ii.incr) + ")";
            locals.put(ii.var, updated);
            return "var" + ii.var + " += " + ii.incr;
        }

        // --- binary arithmetic / bitwise ---
        if (BIN_OP.containsKey(op)) {
            String b = stack.isEmpty() ? "?" : stack.pop();
            String a = stack.isEmpty() ? "?" : stack.pop();
            String expr = "(" + a + " " + BIN_OP.get(op) + " " + b + ")";
            stack.push(expr);
            return "compute " + expr;
        }
        if (op == Opcodes.INEG || op == Opcodes.LNEG || op == Opcodes.FNEG || op == Opcodes.DNEG) {
            String a = stack.isEmpty() ? "?" : stack.pop();
            String expr = "(-" + a + ")";
            stack.push(expr);
            return "negate";
        }

        // --- stack manipulation ---
        if (op == Opcodes.POP) { if (!stack.isEmpty()) stack.pop(); return "discard top"; }
        if (op == Opcodes.POP2) { if (!stack.isEmpty()) stack.pop(); if (!stack.isEmpty()) stack.pop(); return "discard top 2"; }
        if (op == Opcodes.DUP) { if (!stack.isEmpty()) stack.push(stack.peek()); return "duplicate top"; }
        if (op == Opcodes.SWAP) {
            if (stack.size() >= 2) { String a = stack.pop(), b = stack.pop(); stack.push(a); stack.push(b); }
            return "swap top 2";
        }

        // --- fields ---
        if (insn instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode) insn;
            switch (op) {
                case Opcodes.GETSTATIC: stack.push(shortName(f.owner) + "." + f.name); return "push static field";
                case Opcodes.PUTSTATIC: { String val = stack.isEmpty() ? "?" : stack.pop(); return shortName(f.owner) + "." + f.name + " = " + val; }
                case Opcodes.GETFIELD: { String obj = stack.isEmpty() ? "?" : stack.pop(); stack.push(obj + "." + f.name); return "push field " + f.name; }
                case Opcodes.PUTFIELD: {
                    String val = stack.isEmpty() ? "?" : stack.pop();
                    String obj = stack.isEmpty() ? "?" : stack.pop();
                    return obj + "." + f.name + " = " + val;
                }
            }
        }

        // --- method calls ---
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode m = (MethodInsnNode) insn;
            Type methodType = Type.getMethodType(m.desc);
            int argCount = methodType.getArgumentTypes().length;
            LinkedList<String> args = new LinkedList<>();
            for (int k = 0; k < argCount; k++) args.addFirst(stack.isEmpty() ? "?" : stack.pop());
            String receiver = (op == Opcodes.INVOKESTATIC) ? shortName(m.owner) : (stack.isEmpty() ? "?" : stack.pop());
            String call = receiver + "." + m.name + "(" + String.join(", ", args) + ")";
            boolean hasReturn = !methodType.getReturnType().equals(Type.VOID_TYPE);
            if (hasReturn) { stack.push(call); return "call -> push result of " + call; }
            return "call " + call + " (void)";
        }

        // --- new object ---
        if (insn instanceof TypeInsnNode && op == Opcodes.NEW) {
            TypeInsnNode t = (TypeInsnNode) insn;
            stack.push("new " + shortName(t.desc));
            return "allocate " + shortName(t.desc);
        }

        // --- branches / returns: pop operands per table, no symbolic push ---
        OpcodeInfo.StackEffect eff = StackEffectCalculator.effectOf(insn);
        List<String> popped = new ArrayList<>();
        for (int k = 0; k < eff.pop && !stack.isEmpty(); k++) popped.add(stack.pop());
        for (int k = 0; k < eff.push; k++) stack.push("?"); // fallback for anything not explicitly modeled above

        if (isReturnOp(op)) {
            return popped.isEmpty() ? "return" : "return " + popped.get(0);
        }
        if (isConditionalJump(op)) {
            JumpInsnNode j = (JumpInsnNode) insn;
            return "if (" + String.join(" , ", popped) + ") jump";
        }
        if (op == Opcodes.GOTO) return "jump";
        if (!popped.isEmpty()) return OpcodeInfo.describe(op);
        return OpcodeInfo.describe(op);
    }

    private static boolean isReturnOp(int op) {
        return op == Opcodes.IRETURN || op == Opcodes.LRETURN || op == Opcodes.FRETURN
                || op == Opcodes.DRETURN || op == Opcodes.ARETURN || op == Opcodes.RETURN;
    }

    private static boolean isConditionalJump(int op) {
        return op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL;
    }

    private static String shortName(String internalName) {
        String s = internalName.replace('/', '.');
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    // Quick manual test
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "samples/Sample.class";
        String methodFilter = args.length > 1 ? args[1] : null;
        org.objectweb.asm.tree.ClassNode cn = ClassInfoExtractor.readClass(path);
        for (MethodNode m : cn.methods) {
            if (methodFilter != null && !m.name.equals(methodFilter)) continue;
            System.out.println("=== " + ClassInfoExtractor.readableSignature(m) + " ===");
            for (Step s : simulate(m, cn)) {
                System.out.printf("  [%2s] %-15s stack=%-35s // %s%n",
                        s.instr.index >= 0 ? s.instr.index : "",
                        s.instr.opcode,
                        s.stackAfter,
                        s.note);
            }
            System.out.println();
        }
    }
}
