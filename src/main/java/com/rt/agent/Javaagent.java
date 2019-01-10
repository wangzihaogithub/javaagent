package com.rt.agent;

import javassist.LogTransformerByInsertCode;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.lang.instrument.Instrumentation;

/**
 * jvm启动参数 -javaagent:D:\wangzihao\workspace\kf-imes-new\plug\javaagent-1.0.jar -Drt.agent.packages=com.kf.imes.controller
 * @author 84215
 */
public class Javaagent {

    public static void premain(String agentArgs, Instrumentation instrumentation){
        System.out.println("begin agentArgs="+agentArgs+", instrumentation="+instrumentation);
        LogTransformerByInsertCode transformer = new LogTransformerByInsertCode();
        instrumentation.addTransformer(transformer);
        System.out.println("end agentArgs="+agentArgs+", instrumentation="+instrumentation);
    }


    public static class TimeCountClassVisitor extends ClassVisitor implements Opcodes {
        private String owner;
        private boolean isInterface;
        private String filedName="JASMCN";
        private int acc=Opcodes.ACC_PUBLIC+Opcodes.ACC_STATIC+Opcodes.ACC_FINAL;
        private boolean isPresent=false;

        private String methodName;

        public TimeCountClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM6, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            owner = name;
            isInterface = (access & ACC_INTERFACE) != 0;
        }
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (name.equals(filedName)){
                isPresent=true;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv=cv.visitMethod(access, name, descriptor, signature, exceptions);

            if (!isInterface && mv != null && !"<init>".equals(name) && !"<clinit>".equals(name)) {
                methodName=name;
                AddTimerMethodAdapter at = new AddTimerMethodAdapter(mv);
                at.aa = new AnalyzerAdapter(owner, access, name, descriptor, at);
                at.lvs = new LocalVariablesSorter(access, descriptor, at.aa);

                return at.lvs;
            }

            return mv;
        }

        @Override
        public void visitEnd() {
            if (!isInterface) {
                FieldVisitor fv = cv.visitField(acc, filedName,
                        "Ljava/lang/String;", null, owner);
                if (fv != null) {
                    fv.visitEnd();
                }
            }
            cv.visitEnd();
        }

        class AddTimerMethodAdapter extends MethodVisitor {
            private int time;
            private int maxStack;
            public LocalVariablesSorter lvs;
            public AnalyzerAdapter aa;

            public AddTimerMethodAdapter(MethodVisitor methodVisitor) {
                super(ASM6, methodVisitor);
            }


            @Override
            public void visitCode() {
                mv.visitCode();
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                time=lvs.newLocal(Type.LONG_TYPE);
                mv.visitVarInsn(LSTORE, time);
                maxStack=4;
            }

            @Override
            public void visitInsn(int opcode) {
                if (((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) && !isPresent) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                    mv.visitVarInsn(LLOAD, time);
                    mv.visitInsn(LSUB);
                    mv.visitVarInsn(LSTORE, time);

                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

                    mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                    mv.visitFieldInsn(GETSTATIC, owner, filedName, "Ljava/lang/String;");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

                    mv.visitLdcInsn("  "+methodName+":");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

                    mv.visitVarInsn(LLOAD, time);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

                    maxStack=Math.max(aa.stack.size()+4,maxStack);
                }
                mv.visitInsn(opcode);
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                super.visitMaxs(Math.max(maxStack,this.maxStack), maxLocals);
            }
        }

    }

}
