package io.github.opencubicchunks.dasm;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.logging.Logger;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

public class Transformer {
    private static final Logger LOGGER = Logger.getLogger(Transformer.class.getName());

    private final MappingsProvider mappingsProvider;
    private final boolean globalLogSelfRedirects;

    /**
     * @param mappingsProvider The mappings provider to use
     * @param globalLogSelfRedirects A global toggle for self redirects logging
     */
    public Transformer(MappingsProvider mappingsProvider, boolean globalLogSelfRedirects) {
        this.mappingsProvider = mappingsProvider;
        this.globalLogSelfRedirects = globalLogSelfRedirects;
    }

    public void transformClass(ClassNode targetClass, RedirectsParser.ClassTarget target, List<RedirectsParser.RedirectSet> redirectSets) {
        Map<Type, Type> typeRedirects = new HashMap<>();
        Map<ClassField, String> fieldRedirects = new HashMap<>();
        Map<ClassMethod, String> methodRedirects = new HashMap<>();

        for (RedirectsParser.RedirectSet redirectSet : redirectSets) {
            for (RedirectsParser.RedirectSet.TypeRedirect typeRedirect : redirectSet.getTypeRedirects()) {
                typeRedirects.put(
                        getObjectType(mappingsProvider.mapClassName(typeRedirect.srcClassName())),
                        getObjectType(mappingsProvider.mapClassName(typeRedirect.dstClassName()))
                );
            }

            for (RedirectsParser.RedirectSet.FieldRedirect fieldRedirect : redirectSet.getFieldRedirects()) {
                fieldRedirects.put(fieldRedirect.field(), fieldRedirect.dstFieldName());
            }

            for (RedirectsParser.RedirectSet.MethodRedirect methodRedirect : redirectSet.getMethodRedirects()) {
                methodRedirects.put(methodRedirect.method(), methodRedirect.dstMethodName());
            }
        }

        if (target.isWholeClass()) {
            applyWholeClassRedirects(targetClass, methodRedirects, fieldRedirects, typeRedirects, target.debugSelfRedirects());
        } else {
            target.getTargetMethods().forEach(targetMethod -> {
                String newName = targetMethod.dstMethodName();

                MethodNode method;
                if (targetMethod.shouldClone()) {
                    method = cloneAndApplyRedirects(targetClass, targetMethod.method(), newName,
                            methodRedirects,
                            fieldRedirects,
                            typeRedirects, target.debugSelfRedirects());
                } else {
                    method = applyRedirects(targetClass, targetMethod.method(), newName,
                            methodRedirects,
                            fieldRedirects,
                            typeRedirects,
                            target.debugSelfRedirects());
                }
                if (targetMethod.makeSyntheticAccessor()) {
                    makeStaticSyntheticAccessor(targetClass, method);
                }
            });
        }
    }

    private static void makeStaticSyntheticAccessor(ClassNode node, MethodNode newMethod) {
        Type[] params = Type.getArgumentTypes(newMethod.desc);
        Type[] newParams = new Type[params.length + 1];
        System.arraycopy(params, 0, newParams, 1, params.length);
        newParams[0] = getObjectType(node.name);

        Type returnType = Type.getReturnType(newMethod.desc);
        MethodNode newNode = new MethodNode(newMethod.access | ACC_STATIC, newMethod.name,
                Type.getMethodDescriptor(returnType, newParams), null, null);

        int j = 0;
        for (Type param : newParams) {
            newNode.instructions.add(new VarInsnNode(param.getOpcode(ILOAD), j));
            j += param.getSize();
        }
        newNode.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, newMethod.name, newMethod.desc, false));
        newNode.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));
        node.methods.add(newNode);
    }

    private MethodNode cloneAndApplyRedirects(ClassNode node, ClassMethod existingMethodIn, String newName,
                                              Map<ClassMethod, String> methodRedirectsIn, Map<ClassField, String> fieldRedirectsIn, Map<Type, Type> typeRedirectsIn,
                                              boolean debugLogging) {
        LOGGER.info("Transforming " + node.name + ": Cloning method " + existingMethodIn.method.getName() + " " + existingMethodIn.method.getDescriptor() + " "
                + "into " + newName + " and applying remapping");
        Method existingMethod = remapMethod(existingMethodIn).method;

        MethodNode originalMethod = node.methods.stream()
                .filter(x -> existingMethod.getName().equals(x.name) && existingMethod.getDescriptor().equals(x.desc))
                .findAny().orElseThrow(() -> new IllegalStateException("Target method " + existingMethod + " not found"));

        Map<Handle, String> redirectedLambdas = cloneAndApplyLambdaRedirects(node, originalMethod, methodRedirectsIn, fieldRedirectsIn, typeRedirectsIn, debugLogging);

        Map<ClassMethod, String> methodRedirects = new HashMap<>(methodRedirectsIn);
        for (Handle handle : redirectedLambdas.keySet()) {
            methodRedirects.put(
                    new ClassMethod(Type.getObjectType(handle.getOwner()), new Method(handle.getName(), handle.getDesc())),
                    redirectedLambdas.get(handle)
            );
        }

        Remapper remapper = new RedirectingRemapper(node, methodRedirects, fieldRedirectsIn, typeRedirectsIn, debugLogging);

        String desc = originalMethod.desc;
        Type[] params = Type.getArgumentTypes(desc);
        Type ret = Type.getReturnType(desc);
        for (int i = 0; i < params.length; i++) {
            if (params[i].getSort() == Type.OBJECT) {
                params[i] = getObjectType(remapper.map(params[i].getInternalName()));
            }
        }
        if (ret.getSort() == Type.OBJECT) {
            ret = getObjectType(remapper.map(ret.getInternalName()));
        }
        String mappedDesc = Type.getMethodDescriptor(ret, params);

        MethodNode existingOutput = removeExistingMethod(node, newName, mappedDesc);
        MethodNode output;
        if (existingOutput != null) {
            // Remove stub annotations, they may be added by stirrin if this is a stub
            if (existingOutput.visibleAnnotations.removeIf(annotationNode -> annotationNode.desc.equals("Lio/github/opencubicchunks/stirrin/StirrinStub;"))) {
                // this is a stub? Instructions should be overwritten.
                existingOutput.instructions = new InsnList();
                LOGGER.info("Overwriting stub method " + newName + " " + mappedDesc);
            } else {
                LOGGER.info("Copying code into existing method " + newName + " " + mappedDesc);
            }

            output = existingOutput;
        } else {
            output = new MethodNode(originalMethod.access, newName, mappedDesc, null, originalMethod.exceptions.toArray(new String[0]));
        }

        MethodVisitor mv = new MethodVisitor(ASM7, output) {
            @Override public void visitLineNumber(int line, Label start) {
                super.visitLineNumber(line, start);
            }
        };
        MethodRemapper methodRemapper = new MethodRemapper(mv, remapper);

        originalMethod.accept(methodRemapper);
        output.name = newName;
        // remove protected and private, add public
        output.access &= ~(ACC_PROTECTED | ACC_PRIVATE);
        output.access |= ACC_PUBLIC;
        node.methods.add(output);

        return output;
    }

    private MethodNode applyRedirects(ClassNode node, ClassMethod existingMethodIn, String newName,
                                      Map<ClassMethod, String> methodRedirectsIn, Map<ClassField, String> fieldRedirectsIn, Map<Type, Type> typeRedirectsIn,
                                      boolean debugLogging) {
        LOGGER.info("Transforming " + node.name + ": Cloning method " + existingMethodIn.method.getName() + " " + existingMethodIn.method.getDescriptor() + " "
                + "into " + newName + " and applying remapping");
        Method existingMethod = remapMethod(existingMethodIn).method;

        MethodNode originalMethod = node.methods.stream()
                .filter(x -> existingMethod.getName().equals(x.name) && existingMethod.getDescriptor().equals(x.desc))
                .findAny().orElseThrow(() -> new IllegalStateException("Target method " + existingMethod + " not found"));

        Map<Handle, String> redirectedLambdas = cloneAndApplyLambdaRedirects(node, originalMethod, methodRedirectsIn, fieldRedirectsIn, typeRedirectsIn, debugLogging);

        Map<ClassMethod, String> methodRedirects = new HashMap<>(methodRedirectsIn);
        for (Handle handle : redirectedLambdas.keySet()) {
            methodRedirects.put(
                    new ClassMethod(Type.getObjectType(handle.getOwner()), new Method(handle.getName(), handle.getDesc())),
                    redirectedLambdas.get(handle)
            );
        }

        Remapper remapper = new RedirectingRemapper(node, methodRedirects, fieldRedirectsIn, typeRedirectsIn, debugLogging);

        String desc = originalMethod.desc;
        Type[] params = Type.getArgumentTypes(desc);
        Type ret = Type.getReturnType(desc);
        for (int i = 0; i < params.length; i++) {
            if (params[i].getSort() == Type.OBJECT) {
                params[i] = getObjectType(remapper.map(params[i].getInternalName()));
            }
        }
        if (ret.getSort() == Type.OBJECT) {
            ret = getObjectType(remapper.map(ret.getInternalName()));
        }
        String mappedDesc = Type.getMethodDescriptor(ret, params);

        MethodNode output = new MethodNode(originalMethod.access, newName, mappedDesc, null, originalMethod.exceptions.toArray(new String[0]));

        MethodVisitor mv = new MethodVisitor(ASM7, output) {
            @Override public void visitLineNumber(int line, Label start) {
                super.visitLineNumber(line, start);
            }
        };
        MethodRemapper methodRemapper = new MethodRemapper(mv, remapper);
        originalMethod.accept(methodRemapper);
        output.name = newName;
        // remove protected and private, add public
        output.access &= ~(ACC_PROTECTED | ACC_PRIVATE);
        output.access |= ACC_PUBLIC;

        node.methods.remove(originalMethod);
        node.methods.add(output);

        return output;
    }


    private void applyWholeClassRedirects(ClassNode node,
                                          Map<ClassMethod, String> methodRedirectsIn,
                                          Map<ClassField, String> fieldRedirectsIn,
                                          Map<Type, Type> typeRedirectsIn, boolean debugLogging) {

        LOGGER.info("Transforming " + node.name + ": Transforming whole class");

        Remapper remapper = new RedirectingRemapper(node, methodRedirectsIn, fieldRedirectsIn, typeRedirectsIn, debugLogging);

        ClassNode oldNode = new ClassNode(ASM9);
        node.accept(oldNode);

        node.access = 0;
        node.name = null;
        node.signature = null;
        node.superName = null;
        node.interfaces.clear();
        node.sourceFile = null;
        node.sourceDebug = null;
        node.module = null;
        node.outerClass = null;
        node.outerMethod = null;
        node.outerMethodDesc = null;
        node.visibleAnnotations = null;
        node.invisibleAnnotations = null;
        node.visibleTypeAnnotations = null;
        node.invisibleTypeAnnotations = null;
        node.attrs = null;
        node.innerClasses.clear();
        node.nestHostClass = null;
        node.nestMembers = null;
        node.permittedSubclasses = null;
        node.recordComponents = null;
        node.fields.clear();
        node.methods.clear();

        ClassRemapper classRemapper = new ClassRemapper(node, remapper);
        oldNode.accept(classRemapper);
    }

    private static MethodNode removeExistingMethod(ClassNode node, String name, String desc) {
        MethodNode methodNode = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findAny().orElse(null);
        if (methodNode != null)
            node.methods.remove(methodNode);
        return methodNode;
    }

    private ClassField remapField(ClassField clField) {
        Type mappedType = remapType(clField.owner);
        String mappedName = this.mappingsProvider.mapFieldName(clField.owner.getClassName(), clField.name, clField.desc.getDescriptor());
        Type mappedDesc = remapDescType(clField.desc);
        return new ClassField(mappedType, mappedName, mappedDesc);
    }

    @NotNull private ClassMethod remapMethod(ClassMethod clMethod) {
        Type[] params = Type.getArgumentTypes(clMethod.method.getDescriptor());
        Type returnType = Type.getReturnType(clMethod.method.getDescriptor());

        Type mappedType = remapType(clMethod.owner);
        String mappedName = this.mappingsProvider.mapMethodName(clMethod.mappingOwner.getClassName(), clMethod.method.getName(), clMethod.method.getDescriptor());
        Type[] mappedParams = new Type[params.length];
        for (int i = 0; i < params.length; i++) {
            mappedParams[i] = remapDescType(params[i]);
        }
        Type mappedReturnType = remapDescType(returnType);
        return new ClassMethod(mappedType, new Method(mappedName, mappedReturnType, mappedParams));
    }

    private Type remapDescType(Type t) {
        if (t.getSort() == ARRAY) {
            int dimCount = t.getDimensions();
            StringBuilder prefix = new StringBuilder(dimCount);
            for (int i = 0; i < dimCount; i++) {
                prefix.append('[');
            }
            return Type.getType(prefix + remapDescType(t.getElementType()).getDescriptor());
        }
        if (t.getSort() != OBJECT) {
            return t;
        }
        String unmapped = t.getClassName();
        if (unmapped.endsWith(";")) {
            unmapped = unmapped.substring(1, unmapped.length() - 1);
        }
        String mapped = this.mappingsProvider.mapClassName(unmapped);
        String mappedDesc = 'L' + mapped.replace('.', '/') + ';';
        return Type.getType(mappedDesc);
    }

    private Type remapType(Type t) {
        String unmapped = t.getClassName();
        String mapped = this.mappingsProvider.mapClassName(unmapped);
        return Type.getObjectType(mapped.replace('.', '/'));
    }

    private Map<Handle, String> cloneAndApplyLambdaRedirects(ClassNode node, MethodNode method, Map<ClassMethod, String> methodRedirectsIn,
                                                             Map<ClassField, String> fieldRedirectsIn, Map<Type, Type> typeRedirectsIn,
                                                             boolean debugLogging) {
        Map<Handle, String> lambdaRedirects = new HashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == INVOKEDYNAMIC) {
                InvokeDynamicInsnNode invoke = (InvokeDynamicInsnNode) instruction;
                String bootstrapMethodName = invoke.bsm.getName();
                String bootstrapMethodOwner = invoke.bsm.getOwner();
                if (bootstrapMethodName.equals("metafactory") && bootstrapMethodOwner.equals("java/lang/invoke/LambdaMetafactory")) {
                    for (Object bsmArg : invoke.bsmArgs) {
                        if (bsmArg instanceof Handle) {
                            Handle handle = (Handle) bsmArg;
                            String owner = handle.getOwner();
                            if (owner.equals(node.name)) {
                                String newName = "dasm$redirect$" + handle.getName();
                                lambdaRedirects.put(handle, newName);
                                cloneAndApplyRedirects(node, new ClassMethod(Type.getObjectType(handle.getOwner()),
                                                new Method(handle.getName(), handle.getDesc())),
                                        newName, methodRedirectsIn, fieldRedirectsIn, typeRedirectsIn, debugLogging);
                            }
                        }
                    }
                }
            }
        }
        return lambdaRedirects;
    }

    private class RedirectingRemapper extends Remapper {

        private final Set<String> defaultKnownClasses;
        private final Map<String, String> methodRedirects;
        private final Map<String, String> fieldRedirects;
        private final Map<String, String> typeRedirects;
        private final boolean debugLogging;

        public RedirectingRemapper(ClassNode node,
                                   Map<ClassMethod, String> methodRedirectsIn,
                                   Map<ClassField, String> fieldRedirectsIn,
                                   Map<Type, Type> typeRedirectsIn, boolean debugLogging) {

            this.debugLogging = debugLogging;
            this.defaultKnownClasses = Sets.newHashSet(
                    Type.getType(Object.class).getInternalName(),
                    Type.getType(String.class).getInternalName(),
                    node.name
            );

            this.methodRedirects = new HashMap<>();
            for (ClassMethod classMethodUnmapped : methodRedirectsIn.keySet()) {
                ClassMethod classMethod = remapMethod(classMethodUnmapped);
                methodRedirects.put(
                        classMethod.owner.getInternalName() + "." + classMethod.method.getName() + classMethod.method.getDescriptor(),
                        methodRedirectsIn.get(classMethodUnmapped)
                );
            }

            this.fieldRedirects = new HashMap<>();
            for (ClassField classFieldUnmapped : fieldRedirectsIn.keySet()) {
                ClassField classField = remapField(classFieldUnmapped);
                fieldRedirects.put(
                        classField.owner.getInternalName() + "." + classField.name,
                        fieldRedirectsIn.get(classFieldUnmapped)
                );
            }

            this.typeRedirects = new HashMap<>();
            for (Type type : typeRedirectsIn.keySet()) {
                typeRedirects.put(remapType(type).getInternalName(), remapType(typeRedirectsIn.get(type)).getInternalName());
            }

            methodRedirects.forEach((old, n) -> LOGGER.info("Method mapping: " + old + " -> " + n));
            fieldRedirects.forEach((old, n) -> LOGGER.info("Field mapping: " + old + " -> " + n));
            typeRedirects.forEach((old, n) -> LOGGER.info("Type mapping: " + old + " -> " + n));

        }

        @Override
        public String mapMethodName(final String owner, final String name, final String descriptor) {
            if (name.equals("<init>")) {
                return name;
            }
            String key = owner + '.' + name + descriptor;
            String mappedName = methodRedirects.get(key);
            if (mappedName == null) {
                if (globalLogSelfRedirects || this.debugLogging) {
                    LOGGER.info("NOTE: handling METHOD redirect to self: " + key);
                }
                methodRedirects.put(key, name);
                return name;
            }
            return mappedName;
        }

        @Override
        public String mapInvokeDynamicMethodName(final String name, final String descriptor) {
            if (globalLogSelfRedirects || this.debugLogging) {
                LOGGER.info("NOTE: remapping invokedynamic to self: " + name + "." + descriptor);
            }
            return name;
        }

        @Override
        public String mapFieldName(final String owner, final String name, final String descriptor) {
            String key = owner + '.' + name;
            String mapped = fieldRedirects.get(key);
            if (mapped == null) {
                if (globalLogSelfRedirects || this.debugLogging) {
                    LOGGER.info("NOTE: handling FIELD redirect to self: " + key);
                }
                fieldRedirects.put(key, name);
                return name;
            }
            return mapped;
        }

        @Override
        public String map(final String key) {
            String mapped = typeRedirects.get(key);
            if (mapped == null && defaultKnownClasses.contains(key)) {
                mapped = key;
            }
            if (mapped == null) {
                if (globalLogSelfRedirects || this.debugLogging) {
                    LOGGER.info("NOTE: handling CLASS redirect to self: " + key);
                }
                typeRedirects.put(key, key);
                return key;
            }
            return mapped;
        }
    }

    public static final class ClassMethod {
        public final Type owner;
        public final Method method;
        public final Type mappingOwner;

        public ClassMethod(Type owner, Method method) {
            this.owner = owner;
            this.method = method;
            this.mappingOwner = owner;
        }

        // mapping owner because mappings owner may not be the same as in the call site
        public ClassMethod(Type owner, Method method, Type mappingOwner) {
            this.owner = owner;
            this.method = method;
            this.mappingOwner = mappingOwner;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassMethod that = (ClassMethod) o;
            return owner.equals(that.owner) && method.equals(that.method) && mappingOwner.equals(that.mappingOwner);
        }

        @Override public int hashCode() {
            return Objects.hash(owner, method, mappingOwner);
        }

        @Override public String toString() {
            return "ClassMethod{" +
                    "owner=" + owner +
                    ", method=" + method +
                    ", mappingOwner=" + mappingOwner +
                    '}';
        }
    }

    public static final class ClassField {
        public final Type owner;
        public final String name;
        public final Type desc;

        public ClassField(String owner, String name, String desc) {
            this.owner = getObjectType(owner);
            this.name = name;
            this.desc = getType(desc);
        }

        public ClassField(Type owner, String name, Type desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassField that = (ClassField) o;
            return owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
        }

        @Override public int hashCode() {
            return Objects.hash(owner, name, desc);
        }

        @Override public String toString() {
            return "ClassField{" +
                    "owner=" + owner +
                    ", name='" + name + '\'' +
                    ", desc=" + desc +
                    '}';
        }
    }
}