package org.transparent.emerald;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

final class NameFinder extends ClassVisitor {
    String name;

    public NameFinder(int api) {
        super(api);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name,
                                     String descriptor, String signature,
                                     String[] exceptions) {
        if (name.equals("getName") && descriptor
                .equals("()Ljava/lang/String;")) {
            return new Visitor(api, this);
        }
        return super.visitMethod(
                access, name,
                descriptor, signature,
                exceptions);
    }

    private static class Visitor extends MethodVisitor {
        private final NameFinder finder;

        public Visitor(int api, NameFinder finder) {
            super(api);
            this.finder = finder;
        }

        @Override
        public void visitLdcInsn(Object value) {
            finder.name = (String) value;
            super.visitLdcInsn(value);
        }
    }
}