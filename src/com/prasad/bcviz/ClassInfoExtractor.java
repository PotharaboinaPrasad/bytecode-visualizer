package com.prasad.bcviz;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Step 1: Reads a .class file and extracts high-level structural metadata:
 * class name, superclass, interfaces, access flags, fields, and method signatures.
 *
 * This uses ASM's "tree API" (ClassNode) rather than the streaming visitor API,
 * because a tree gives us random access to the whole class structure at once -
 * which we'll need later for the stack simulator and CFG builder.
 */
public class ClassInfoExtractor {

    public static ClassNode readClass(String path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            ClassReader reader = new ClassReader(in);
            ClassNode classNode = new ClassNode();
            // EXPAND_FRAMES: makes stack map frames easier to work with later
            // (important once we get to the stack simulator step)
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);
            return classNode;
        }
    }

    public static void printSummary(ClassNode cn) {
        System.out.println("=== CLASS ===");
        System.out.println("Name:        " + cn.name.replace('/', '.'));
        System.out.println("Super:       " + (cn.superName != null ? cn.superName.replace('/', '.') : "none"));
        System.out.println("Interfaces:  " + (cn.interfaces.isEmpty() ? "none" : cn.interfaces));
        System.out.println("Access:      " + accessFlagsToString(cn.access));
        System.out.println("Java ver:    " + decodeVersion(cn.version));

        System.out.println("\n=== FIELDS (" + cn.fields.size() + ") ===");
        for (FieldNode f : cn.fields) {
            System.out.printf("  %-20s %-15s %s%n",
                    f.name,
                    Type.getType(f.desc).getClassName(),
                    accessFlagsToString(f.access));
        }

        System.out.println("\n=== METHODS (" + cn.methods.size() + ") ===");
        for (MethodNode m : cn.methods) {
            System.out.printf("  %s %s%n", readableSignature(m), accessFlagsToString(m.access));
            System.out.printf("      instructions: %d%n", m.instructions.size());
        }
    }

    /** Turns "(ILjava/lang/String;)Z" style descriptors into "boolean methodName(int, String)" */
    public static String readableSignature(MethodNode m) {
        Type methodType = Type.getMethodType(m.desc);
        StringBuilder sb = new StringBuilder();
        sb.append(methodType.getReturnType().getClassName()).append(" ");
        sb.append(m.name).append("(");
        Type[] args = methodType.getArgumentTypes();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(args[i].getClassName());
        }
        sb.append(")");
        return sb.toString();
    }

    private static String decodeVersion(int version) {
        int major = version & 0xFFFF;
        // Java 1.1 = 45, each version after = major - 44
        int javaVersion = major - 44;
        return "Java " + javaVersion + " (classfile major=" + major + ")";
    }

    private static String accessFlagsToString(int access) {
        StringBuilder sb = new StringBuilder();
        if ((access & Opcodes.ACC_PUBLIC) != 0) sb.append("public ");
        if ((access & Opcodes.ACC_PRIVATE) != 0) sb.append("private ");
        if ((access & Opcodes.ACC_PROTECTED) != 0) sb.append("protected ");
        if ((access & Opcodes.ACC_STATIC) != 0) sb.append("static ");
        if ((access & Opcodes.ACC_FINAL) != 0) sb.append("final ");
        if ((access & Opcodes.ACC_ABSTRACT) != 0) sb.append("abstract ");
        if ((access & Opcodes.ACC_INTERFACE) != 0) sb.append("interface ");
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) sb.append("synthetic ");
        return sb.toString().trim();
    }

    // Quick manual test for this step - we'll replace this with a proper CLI/web layer later
    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "samples/Sample.class";
        ClassNode cn = readClass(path);
        printSummary(cn);
    }
}
