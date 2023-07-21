package andy;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.apache.commons.io.IOUtils;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.*;

public class Main {
    private static Random random1;
    private static Random random2;
    private static int mode;
    private static int intensity;

    public static void main(String[] args) throws Exception {
        String inPath = args[0];
        String outPath = args[1];
        random1 = new Random(Long.parseLong(args[2]));
        random2 = new Random(Long.parseLong(args[3]));
        mode = Integer.parseInt(args[4]);
        intensity = Integer.parseInt(args[5]);

        Map<String, byte[]> modifiedClasses = new HashMap<>();
        Map<String, byte[]> resources = new HashMap<>();

        JarFile jarFile = new JarFile(inPath);
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();

            InputStream entryInputStream = jarFile.getInputStream(jarEntry);
            byte[] entryBytes = IOUtils.toByteArray(entryInputStream);
            entryInputStream.close();

            if (jarEntry.getName().endsWith(".class")) {
                ClassReader classReader = new ClassReader(entryBytes);
                ClassNode classNode = new ClassNode();
                classReader.accept(classNode, 0);

                corrupt(classNode, args);

                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                classNode.accept(classWriter);
                modifiedClasses.put(jarEntry.getName(), classWriter.toByteArray());
            } else {
                resources.put(jarEntry.getName(), entryBytes);
            }
        }


        JarOutputStream corruptedJarOutputStream = new JarOutputStream(new FileOutputStream(outPath));
            for (Map.Entry<String, byte[]> entry : modifiedClasses.entrySet()) {
                JarEntry modifiedJarEntry = new JarEntry(entry.getKey());
                corruptedJarOutputStream.putNextEntry(modifiedJarEntry);
                corruptedJarOutputStream.write(entry.getValue());
                corruptedJarOutputStream.closeEntry();
            }

            for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                JarEntry resourceJarEntry = new JarEntry(entry.getKey());
                corruptedJarOutputStream.putNextEntry(resourceJarEntry);
                corruptedJarOutputStream.write(entry.getValue());
                corruptedJarOutputStream.closeEntry();
            }
        corruptedJarOutputStream.close();
    }

    private static AbstractInsnNode vector(AbstractInsnNode insn, int limiter, int value) {
        // TODO: make this way more versatile
        if (insn.getOpcode() == limiter)
            return new InsnNode(value);
        else
            return insn;
    }
    private static InsnList mathAfterMath(AbstractInsnNode insn, float max, float min, String math) {
        InsnList insnList = new InsnList();

        switch (insn.getOpcode()) {
            case Opcodes.FADD:
            case Opcodes.FSUB:
            case Opcodes.FMUL:
            case Opcodes.FDIV:
                float f = random2.nextFloat();

                if (f > 0)  // Maximum value
                    insnList.add(new LdcInsnNode(f % max));
                else        // Minimum value
                    insnList.add(new LdcInsnNode(f % min));

                if (math.equals("random")) {
                    List<Integer> fMath = new ArrayList<>();
                    fMath.add(Opcodes.FADD);
                    fMath.add(Opcodes.FSUB);
                    fMath.add(Opcodes.FMUL);
                    fMath.add(Opcodes.FDIV);
                    insnList.add(new InsnNode(fMath.get(random2.nextInt(4))));
                } else
                    insnList.add(new InsnNode(Integer.parseInt(math))); // The float math instruction will be sent here
                break;
            case Opcodes.DADD:
            case Opcodes.DSUB:
            case Opcodes.DMUL:
            case Opcodes.DDIV:
                double d = random2.nextDouble();

                if (d > 0)  // Maximum value
                    insnList.add(new LdcInsnNode(d % (double)max));
                else        // Minimum value
                    insnList.add(new LdcInsnNode(d % (double)min));

                if (math.equals("random")) {
                    List<Integer> dMath = new ArrayList<>();
                    dMath.add(Opcodes.DADD);
                    dMath.add(Opcodes.DSUB);
                    dMath.add(Opcodes.DMUL);
                    dMath.add(Opcodes.DDIV);
                    insnList.add(new InsnNode(dMath.get(random2.nextInt(4))));
                } else
                    insnList.add(new InsnNode(Integer.parseInt(math) + 1)); // Adding 1 makes it the equivalent double math instruction
                break;
        }

        return insnList;
    }

    private static void corrupt(ClassNode clazz, String[] args) {
        clazz.methods.forEach(methodNode -> {
            final InsnList insnList = new InsnList();

            methodNode.instructions.forEach(insnNode -> {
                if (random1.nextInt(intensity) != 0) {
                    insnList.add(insnNode);
                    return;
                }
                switch (mode) {
                    case 0: {
                        insnList.add(vector(insnNode, Integer.parseInt(args[6]), Integer.parseInt(args[7])));
                        break;
                    }
                    case 1: {
                        insnList.add(insnNode);
                        insnList.add(mathAfterMath(insnNode, Float.parseFloat(args[6]), Float.parseFloat(args[7]), args[8]));
                        break;
                    }
                    case 2: {
                        int opcode = insnNode.getOpcode();

                        switch (opcode) {
                            case Opcodes.FADD:
                            case Opcodes.DADD:
                                insnList.add(new InsnNode(insnNode.getOpcode() + 4));
                                break;
                            case Opcodes.DSUB:
                            case Opcodes.FSUB:
                                insnList.add(new InsnNode(insnNode.getOpcode() - 4));
                                break;
                            default:
                                insnList.add(insnNode);
                                break;
                        }

                        break;
                    }
                    case 3: {
                        List<Integer> f = new ArrayList<>();
                        f.add(Opcodes.FADD);
                        f.add(Opcodes.FSUB);
                        f.add(Opcodes.FMUL);
                        f.add(Opcodes.FDIV);
                        List<Integer> d = new ArrayList<>();
                        d.add(Opcodes.DADD);
                        d.add(Opcodes.DSUB);
                        d.add(Opcodes.DMUL);
                        d.add(Opcodes.DDIV);

                        if (f.contains(insnNode.getOpcode()))
                            insnList.add(new InsnNode(f.get(random2.nextInt(f.size()))));
                        else if (d.contains(insnNode.getOpcode()))
                            insnList.add(new InsnNode(d.get(random2.nextInt(d.size()))));
                        else
                            insnList.add(insnNode);

                        break;
                    }
                    case 4: {
                        int opcode = insnNode.getOpcode();


                        if (insnNode.getOpcode() == Opcodes.LDC) {
                            LdcInsnNode ldcInsnNode = (LdcInsnNode) insnNode;
                            if (ldcInsnNode.cst instanceof Float) {
                                float value = (float) ldcInsnNode.cst;
                                float divergence = Float.parseFloat(args[6]) / 100;
                                insnList.add(new LdcInsnNode(value + (random2.nextFloat() % (value * divergence * 2) - value * divergence)));
                                //the above equation makes the new value be between value - divergence% of value and value + divergence% of value
                                //i.e. if value = 100 and divergence = 0.1, the new value will be between 90 and 109.̅9
                            }
                            else if (ldcInsnNode.cst instanceof Double) {
                                double cst = (double) ldcInsnNode.cst;
                                double divergence = Double.parseDouble(args[6]) / 100;
                                insnList.add(new LdcInsnNode(cst + (random2.nextDouble() % (cst * divergence * 2) - cst * divergence)));
                            }
                            else {
                                insnList.add(insnNode);
                            }
                        }
                        else if (opcode == Opcodes.FCONST_1 || opcode == Opcodes.FCONST_2) {
                            float value = opcode - Opcodes.FCONST_0; // 1, 2
                            float divergence = Float.parseFloat(args[6]) / 100;
                            insnList.add(new LdcInsnNode(value + (random2.nextFloat() % (value * divergence * 2) - value * divergence)));
                        }
                        else if (opcode == Opcodes.DCONST_1) {
                            double value = opcode - Opcodes.DCONST_0; // 1
                            double divergence = Double.parseDouble(args[6]) / 100;
                            insnList.add(new LdcInsnNode(value + (random2.nextDouble() % (value * divergence * 2) - value * divergence)));
                        }
                        else { //don't bother with FCONST_0 and DCONST_0, the equation will always result in 0
                            insnList.add(insnNode);
                        }

                        break;
                    }
                    case 5: {
                        if (insnNode.getOpcode() == Opcodes.INVOKESTATIC) {
                            MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                            if (!methodInsnNode.owner.equals("java/lang/Math")) {
                                insnList.add(insnNode);
                                break;
                            }
                            if (methodInsnNode.name.equals("sin")) {
                                methodInsnNode.name = "cos";
                            } else if (methodInsnNode.name.equals("cos")) {
                                methodInsnNode.name = "sin";
                            }
                        }
                        insnList.add(insnNode);

                        break;
                    }
                    default:
                        insnList.add(insnNode);
                        break;
                }
            });

            methodNode.instructions = insnList;
        });
    }
}
