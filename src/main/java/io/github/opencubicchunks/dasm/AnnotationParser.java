package io.github.opencubicchunks.dasm;

import io.github.opencubicchunks.dasm.api.provider.ClassProvider;
import io.github.opencubicchunks.dasm.api.redirect.AddFieldToSets;
import io.github.opencubicchunks.dasm.api.redirect.AddMethodToSets;
import io.github.opencubicchunks.dasm.api.redirect.DasmRedirectSet;
import io.github.opencubicchunks.dasm.api.redirect.PartialRedirect;
import io.github.opencubicchunks.dasm.api.transform.DasmRedirect;
import io.github.opencubicchunks.dasm.api.transform.TransformFrom;
import io.github.opencubicchunks.dasm.api.transform.TransformFromClass;
import io.github.opencubicchunks.dasm.transformer.ClassField;
import io.github.opencubicchunks.dasm.transformer.ClassMethod;
import io.github.opencubicchunks.dasm.transformer.redirect.FieldRedirect;
import io.github.opencubicchunks.dasm.transformer.redirect.MethodRedirect;
import io.github.opencubicchunks.dasm.transformer.redirect.RedirectSet;
import io.github.opencubicchunks.dasm.transformer.redirect.TypeRedirect;
import io.github.opencubicchunks.dasm.transformer.target.TargetClass;
import io.github.opencubicchunks.dasm.transformer.target.TargetMethod;
import io.github.opencubicchunks.dasm.util.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ASM9;

public class AnnotationParser {
    private final ClassProvider classProvider;
    private final Map<Type, List<RedirectSet>> redirectSetsByType = new ConcurrentHashMap<>();

    private final Type defaultSet;

    public AnnotationParser(ClassProvider classProvider, Class<?> defaultRedirectSet) {
        this.classProvider = classProvider;
        this.defaultSet = Type.getType(defaultRedirectSet);
    }

    public void findRedirectSets(String targetClassName, ClassNode targetClass, Set<RedirectSet> redirectSets) {
        if (targetClass.invisibleAnnotations == null) {
            return;
        }
        for (AnnotationNode ann : targetClass.invisibleAnnotations) {
            if (!ann.desc.equals(classToDescriptor(DasmRedirect.class))) {
                continue;
            }
            // The name value pairs of this annotation. Each name value pair is stored as two consecutive
            // elements in the list. The name is a String, and the value may be a
            // Byte, Boolean, Character, Short, Integer, Long, Float, Double, String or org.objectweb.asm.Type,
            // or a two elements String array (for enumeration values), an AnnotationNode,
            // or a List of values of one of the preceding types. The list may be null if there is no name value pair.
            List<Object> values = ann.values;
            if (values == null) {
                redirectSets.addAll(getRedirectSetsForType(this.defaultSet));
                continue;
            }
            List<Type> sets = null;
            for (int i = 0, valuesSize = values.size(); i < valuesSize; i += 2) {
                String name = (String) values.get(i);
                Object value = values.get(i + 1);
                if (name.equals("value")) {
                    sets = (List<Type>) value;
                }
            }
            if (sets == null) {
                redirectSets.addAll(getRedirectSetsForType(this.defaultSet));
                continue;
            }
            for (Type set : sets) {
                List<RedirectSet> redirectSet = getRedirectSetsForType(set);
                if (redirectSet == null) {
                    throw new IllegalArgumentException("No redirect set " + set + ", targetClass=" + targetClassName);
                }
                redirectSets.addAll(redirectSet);
            }
        }
    }

    @Nullable private Type getNewOwner(Type currentClass, Type newOwner) {
        if (currentClass.equals(newOwner)) {
            return null;
        }
        return newOwner;
    }

    public void buildClassTarget(ClassNode targetClass, TargetClass classTarget, TransformFrom.ApplicationStage stage, String methodPrefix) {
        if (targetClass.invisibleAnnotations == null) {
            return;
        }
        for (AnnotationNode ann : targetClass.invisibleAnnotations) {
            if (!ann.desc.equals(classToDescriptor(TransformFromClass.class)) || ann.values == null) {
                continue;
            }

            Map<String, Object> values = getAnnotationValues(ann, TransformFromClass.class);

            @SuppressWarnings("unchecked") Type srcClass = parseRefAnnotation((Map<String, Object>) values.get("value"));
            TransformFrom.ApplicationStage requestedStage = (TransformFrom.ApplicationStage) values.get("stage");

            if (stage != requestedStage) {
                continue;
            }
            classTarget.targetWholeClass(srcClass);
        }

        targetClass.fields.forEach(fieldNode -> {
            AnnotationNode annotation = getAnnotationIfPresent(fieldNode.invisibleAnnotations, AddFieldToSets.class);
            if (annotation == null) {
                return;
            }
            Map<String, Object> values = getAnnotationValues(annotation, AddFieldToSets.class);

            Type owner = (Type) values.get("owner");

            @SuppressWarnings("unchecked") Map<String, Object> field = (Map<String, Object>) values.get("field");
            Type fieldType = (Type) field.get("type");
            String fieldName = (String) field.get("name");

            @SuppressWarnings("unchecked") List<Type> sets = (List<Type>) values.get("sets");

            sets.forEach(set -> {
                RedirectSet directRedirectSetForType = getDirectRedirectSetForType(set);
                if (directRedirectSetForType == null) {
                    throw new IllegalArgumentException(String.format("Couldn't find redirect set for class %s", set.getClassName()));
                }
                directRedirectSetForType.addRedirect(new FieldRedirect(
                        new ClassField(owner, fieldName, Type.getType(fieldType.getDescriptor())),
                        getNewOwner(Type.getType(classNameToDescriptor(classTarget.getClassName())), owner),
                        fieldNode.name
                ));
            });
        });

        targetClass.methods.forEach(methodNode -> {
            AnnotationNode annotation = getAnnotationIfPresent(methodNode.invisibleAnnotations, AddMethodToSets.class);
            if (annotation == null) {
                return;
            }
            Map<String, Object> values = getAnnotationValues(annotation, AddMethodToSets.class);

            @SuppressWarnings("unchecked") Type owner = (Type) values.get("owner");

            @SuppressWarnings("unchecked") Pair<String, String> methodParts = parseMethodSigAnnotation((Map<String, Object>) values.get("method"));

            @SuppressWarnings("unchecked") List<Type> sets = (List<Type>) values.get("sets");

            sets.forEach(set -> {
                RedirectSet directRedirectSetForType = getDirectRedirectSetForType(set);
                if (directRedirectSetForType == null) {
                    throw new IllegalArgumentException(String.format("Couldn't find redirect set for class %s", set.getClassName()));
                }
                directRedirectSetForType.addRedirect(new MethodRedirect(
                        new ClassMethod(
                                owner,
                                new Method(methodParts.first, methodParts.second)
                        ),
                        getNewOwner(Type.getType(classNameToDescriptor(classTarget.getClassName())), owner),
                        methodNode.name,
                        (targetClass.access & ACC_INTERFACE) != 0
                ));
            });
        });



        for (Iterator<MethodNode> iterator = targetClass.methods.iterator(); iterator.hasNext(); ) {
            MethodNode method = iterator.next();
            if (method.invisibleAnnotations == null) {
                continue;
            }

            for (AnnotationNode ann : method.invisibleAnnotations) {
                if (!ann.desc.equals(classToDescriptor(TransformFrom.class))) {
                    continue;
                }
                iterator.remove();

                Map<String, Object> values = getAnnotationValues(ann, TransformFrom.class);

                TransformFrom.ApplicationStage requestedStage = (TransformFrom.ApplicationStage) values.get("stage");
                if (stage != requestedStage) {
                    continue;
                }

                Pair<String, String> methodSig = parseMethodSigAnnotation((Map<String, Object>) values.get("value"));
                boolean makeSyntheticAccessor = (boolean) values.get("makeSyntheticAccessor");

                @SuppressWarnings("unchecked") Type srcOwner = parseRefAnnotation((Map<String, Object>) values.get("copyFrom"));

                @SuppressWarnings("unchecked") List<RedirectSet> usedRedirectSets = ((List<Type>) values.get("useRedirectSets")).stream()
                        .flatMap(setType -> getRedirectSetsForType(setType).stream())
                        .collect(Collectors.toList());

                ClassMethod classMethod = new ClassMethod(
                        Type.getType(classNameToDescriptor(classTarget.getClassName())),
                        new Method(methodSig.first, methodSig.second)
                );
                Type methodOwner = srcOwner != null ? srcOwner : classMethod.owner;
                boolean isDstInterface = (targetClass.access & ACC_INTERFACE) != 0;

                Type newOwner = getNewOwner(Type.getType(classNameToDescriptor(classTarget.getClassName())), methodOwner);
                //noinspection unchecked
                ((List<Type>) values.get("addToRedirectSets")).stream()
                        .flatMap(setType -> getRedirectSetsForType(setType).stream())
                        .forEach(redirectSet -> redirectSet.addRedirect(new MethodRedirect(classMethod, newOwner, method.name, isDstInterface)));

                TargetMethod targetMethod = new TargetMethod(
                        methodOwner,
                        classMethod,
                        methodPrefix + method.name, // Name is modified here to prevent mixin from overwriting it. We remove this prefix in postApply.
                        true,
                        makeSyntheticAccessor,
                        usedRedirectSets
                );

                if (classTarget.targetMethods().stream().anyMatch(t -> t.method().method.equals(targetMethod.method().method))) {
                    throw new RuntimeException(String.format("Trying to add duplicate TargetMethod to %s:\n\t\t\t\t%s | %s", classTarget.getClassName(), targetMethod.method().owner,
                            targetMethod.method().method));
                }
                classTarget.addTarget(targetMethod);
            }
        }
    }

    @Nullable
    private RedirectSet getDirectRedirectSetForType(Type setType) {
        List<RedirectSet> redirectSets = this.redirectSetsByType.get(setType);

        if (redirectSets == null || redirectSets.isEmpty()) {
            return null;
        }

        return redirectSets.get(redirectSets.size() - 1);
    }

    private List<RedirectSet> getRedirectSetsForType(Type setType) {
        return this.redirectSetsByType.computeIfAbsent(setType, t -> {
            List<RedirectSet> redirectSets = new ArrayList<>();

            ClassNode classNode = classNodeForType(setType);
            if ((classNode.access & ACC_INTERFACE) == 0) {
                throw new IllegalStateException("Non-interface type is a redirect set");
            }

            AnnotationNode annotationNode = getAnnotationIfPresent(classNode.invisibleAnnotations, DasmRedirectSet.class);
            if (annotationNode == null) {
                throw new IllegalStateException(String.format("Class %s is used as a redirect set but not marked with %s", setType.getClassName(), DasmRedirectSet.class.getSimpleName()));
            }

            // First add inherited redirect sets
            for (String interface_ : classNode.interfaces) {
                redirectSets.addAll(getRedirectSetsForType(Type.getObjectType(interface_)));
            }

            // Then add this set
            RedirectSet thisRedirectSet = new RedirectSet(setType.getClassName());
            redirectSets.add(thisRedirectSet);

            // Discover type/field/method redirects in innerclass
            for (InnerClassNode innerClass : classNode.innerClasses) {
                ClassNode innerClassNode = classNodeForType(Type.getObjectType(innerClass.name));

                String srcClassName;
                String dstClassName;

                if (isAnnotationIfPresent(innerClassNode.invisibleAnnotations, io.github.opencubicchunks.dasm.api.redirect.TypeRedirect.class)) {
                    TypeRedirect typeRedirect = parseTypeRedirect(innerClassNode);
                    thisRedirectSet.addRedirect(typeRedirect);

                    srcClassName = typeRedirect.srcClassName();
                    dstClassName = typeRedirect.dstClassName();
                } else if (isAnnotationIfPresent(innerClassNode.invisibleAnnotations, PartialRedirect.class)) {
                    Pair<String, String> partialRedirect = parsePartialRedirect(innerClassNode);
                    srcClassName = partialRedirect.first;
                    dstClassName = partialRedirect.second;
                } else {
                    throw new IllegalStateException(String.format("Inner class %s must be either a TypeRedirect or a PartialRedirect", innerClass.name));
                }

                Type srcType = Type.getType(classNameToDescriptor(srcClassName));
                Type newOwner = getNewOwner(srcType, Type.getType(classNameToDescriptor(dstClassName)));
                for (FieldNode field : innerClassNode.fields) {
                    thisRedirectSet.addRedirect(parseFieldRedirect(
                            field,
                            srcType,
                            newOwner
                    ));
                }

                for (MethodNode method : innerClassNode.methods) {
                    // skip checking the invisible default constructor if it doesn't have annotations
                    if (method.name.equals("<init>") && method.desc.equals("()V") && method.invisibleAnnotations == null) {
                        continue;
                    }

                    thisRedirectSet.addRedirect(parseMethodRedirect(
                            method,
                            srcType,
                            newOwner,
                            (innerClassNode.access & ACC_INTERFACE) == 0)
                    );
                }
            }

            return redirectSets;
        });
    }

    private static Map<String, Object> getAnnotationValues(AnnotationNode annotationNode, Class<?> annotation) {
        Map<String, Object> annotationValues = new HashMap<>();

        // Insert specified arguments in the annotation
        for (int i = 0; i < annotationNode.values.size(); i += 2) {
            String name = (String) annotationNode.values.get(i);
            Object value = annotationNode.values.get(i + 1);

            if (value instanceof AnnotationNode) {
                AnnotationNode annotationValue = (AnnotationNode) value;
                try {
                    value = getAnnotationValues(annotationValue, Class.forName(classDescriptorToClassName(annotationValue.desc)));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            annotationValues.put(name, value);
        }

        addMissingDefaultValues(annotation, annotationValues);
        return annotationValues;
    }

    private static void addMissingDefaultValues(Class<?> annotation, Map<String, Object> annotationValues) {
        // Insert default arguments, only if they aren't already present
        for (java.lang.reflect.Method declaredMethod : annotation.getDeclaredMethods()) {
            Object defaultValue = declaredMethod.getDefaultValue();
            if (defaultValue != null && !annotationValues.containsKey(declaredMethod.getName())) {
                if (declaredMethod.getReturnType().isAnnotation()) {
                    Map<String, Object> values = new HashMap<>();
                    for (java.lang.reflect.Method method : defaultValue.getClass().getInterfaces()[0].getDeclaredMethods()) {
                        try {
                            if (method.getReturnType().isAnnotation()) {
                                @SuppressWarnings("unchecked") Map<String, Object> v = (Map<String, Object>) annotationValues.computeIfAbsent(method.getName(), n -> new HashMap<>());
                                addMissingDefaultValues(method.getReturnType(), v);
                            } else {
                                Object v = method.invoke(defaultValue);
                                values.put(method.getName(), matchAsmTypes(v));
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    annotationValues.put(declaredMethod.getName(), values);
                } else {
                    annotationValues.put(declaredMethod.getName(), matchAsmTypes(defaultValue));
                }
            }
        }
    }

    private static Object matchAsmTypes(Object defaultValue) {
        if (defaultValue instanceof Class[]) {
            return Arrays.stream((Class<?>[]) defaultValue)
                    .map(Type::getType)
                    .collect(Collectors.toList());
        }
        if (defaultValue.getClass().isArray()) {
            return Arrays.asList((Object[]) defaultValue);
        }
        if (defaultValue instanceof Class<?>) {
            return Type.getType((Class<?>) defaultValue);
        }
        return defaultValue;
    }


    @Nullable private AnnotationNode getAnnotationIfPresent(List<AnnotationNode> annotations, Class<?> annotation) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode annotationNode : annotations) {
            if (annotationNode.desc.equals(classToDescriptor(annotation))) {
                return annotationNode;
            }
        }
        return null;
    }

    private boolean isAnnotationIfPresent(List<AnnotationNode> annotations, Class<?> annotation) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotationNode : annotations) {
            if (annotationNode.desc.equals(classToDescriptor(annotation))) {
                return true;
            }
        }
        return false;
    }

    private MethodRedirect parseMethodRedirect(MethodNode methodNode, Type owner, @Nullable Type newOwner, boolean isDstInterface) {
        for (AnnotationNode annotation : methodNode.invisibleAnnotations) {
            if (!annotation.desc.equals(classToDescriptor(io.github.opencubicchunks.dasm.api.redirect.MethodRedirect.class))) {
                continue;
            }

            Map<String, Object> values = getAnnotationValues(annotation, io.github.opencubicchunks.dasm.api.redirect.MethodRedirect.class);

            String newName = (String) values.get("value");
            @SuppressWarnings("unchecked") Type mappingsOwner = parseRefAnnotation((Map<String, Object>) values.get("mappingsOwner"));

            if (newName.isEmpty()) {
                throw new IllegalStateException(String.format("Invalid method redirect: %s -> %s", methodNode.name, newName));
            }

            if (mappingsOwner == null) {
                return new MethodRedirect(new ClassMethod(owner, new Method(methodNode.name, methodNode.desc)), newOwner, newName, isDstInterface);
            } else {
                return new MethodRedirect(new ClassMethod(owner, new Method(methodNode.name, methodNode.desc), mappingsOwner), newOwner, newName, isDstInterface);
            }
        }

        throw new IllegalStateException(String.format("No method redirect on field %s", methodNode.name));
    }

    private FieldRedirect parseFieldRedirect(FieldNode fieldNode, Type owner, @Nullable Type newOwner) {
        for (AnnotationNode annotation : fieldNode.invisibleAnnotations) {
            if (!annotation.desc.equals(classToDescriptor(io.github.opencubicchunks.dasm.api.redirect.FieldRedirect.class))) {
                continue;
            }

            Map<String, Object> values = getAnnotationValues(annotation, FieldRedirect.class);

            String newName = (String) values.get("value");

            if (newName.isEmpty()) {
                throw new IllegalStateException(String.format("Invalid field redirect: %s -> %s", fieldNode.name, newName));
            }
            return new FieldRedirect(new ClassField(owner, fieldNode.name, Type.getType(fieldNode.desc)), newOwner, newName);
        }

        throw new IllegalStateException(String.format("No field redirect on field %s", fieldNode.name));
    }

    private TypeRedirect parseTypeRedirect(ClassNode innerClass) {
        for (AnnotationNode annotation : innerClass.invisibleAnnotations) {
            if (!annotation.desc.equals(classToDescriptor(io.github.opencubicchunks.dasm.api.redirect.TypeRedirect.class))) {
                continue;
            }

            Map<String, Object> values = getAnnotationValues(annotation, TypeRedirect.class);

            @SuppressWarnings("unchecked") Type from = parseRefAnnotation((Map<String, Object>) values.get("from"));
            @SuppressWarnings("unchecked") Type to = parseRefAnnotation((Map<String, Object>) values.get("to"));

            if (from == null || to == null) {
                throw new IllegalStateException(String.format("Invalid type redirect: %s -> %s", from, to));
            }
            return new TypeRedirect(from.getClassName(), to.getClassName());
        }

        throw new IllegalStateException(String.format("No type redirect on inner class %s", innerClass.name));
    }

    private Pair<String, String> parsePartialRedirect(ClassNode innerClass) {
        for (AnnotationNode annotation : innerClass.invisibleAnnotations) {
            if (!annotation.desc.equals(classToDescriptor(PartialRedirect.class))) {
                continue;
            }

            Map<String, Object> values = getAnnotationValues(annotation, PartialRedirect.class);

            @SuppressWarnings("unchecked") Type from = parseRefAnnotation((Map<String, Object>) values.get("from"));
            @SuppressWarnings("unchecked") Type to = parseRefAnnotation((Map<String, Object>) values.get("to"));

            if (from == null || to == null) {
                throw new IllegalStateException(String.format("Invalid type redirect: %s -> %s", from, to));
            }
            return new Pair<>(from.getClassName(), to.getClassName());
        }

        throw new IllegalStateException(String.format("No type redirect on inner class %s", innerClass.name));
    }

    @Nullable
    private static Type parseRefAnnotation(Map<String, Object> values) {
        Type type = null;
        if (values.containsKey("value")) {
            type = (Type) values.get("value");
        }
        if (values.containsKey("string")) {
            String string = (String) values.get("string");
            if (!string.isEmpty()) {
                type = Type.getObjectType(string);
            }
        }
        if (type == null) {
            throw new IllegalArgumentException("Ref annotation was given no arguments!");
        }
        if (type.getClassName().equals(Object.class.getName())) {
            return null;
        }
        return type;
    }

    private static Pair<String, String> parseMethodSigAnnotation(Map<String, Object> ann) {
        if (ann == null) {
            return null;
        }

        String value = ((String) ann.get("value"));
        if (!value.isEmpty()) {
            int parametersStart = value.indexOf('(');
            if (parametersStart == -1) { // did not find
                throw new IllegalArgumentException(String.format("MethodSig annotation had invalid value %s", value));
            }
            return new Pair<>(value.substring(0, parametersStart), value.substring(parametersStart));
        }

        Type ret = (Type) ann.get("ret");
        @SuppressWarnings("unchecked") List<Type> args = (List<Type>) ann.get("args");
        String name = (String) ann.get("value");

        return new Pair<>(name, Type.getMethodDescriptor(ret, args.toArray(new Type[0])));
    }

    private ClassNode classNodeForType(Type type) {
        ClassNode dst = new ClassNode(ASM9);
        final ClassReader classReader = new ClassReader(this.classProvider.classBytes(type.getClassName()));
        classReader.accept(dst, 0);
        return dst;
    }

    private static String classToDescriptor(Class<?> clazz) {
        return classNameToDescriptor(clazz.getName());
    }

    private static String classNameToDescriptor(String className) {
        return "L" + className.replace('.', '/') + ";";
    }

    private static String classDescriptorToClassName(String descriptor) {
        return Type.getType(descriptor).getClassName();
    }
}
