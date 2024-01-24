package io.github.opencubicchunks.dasm;

import io.github.opencubicchunks.dasm.api.provider.ClassProvider;
import io.github.opencubicchunks.dasm.api.redirect.DasmRedirectSet;
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

    private static <T> T getAnnotationDefaultValue(Class<?> clazz, String field, Class<T> defaultValueType) {
        try {
            Object defaultValue = clazz.getDeclaredMethod(field).getDefaultValue();
            if (defaultValue != null && !defaultValueType.isInstance(defaultValue)) { // Same check as in Class.cast
                throw new ClassCastException(String.format("Cannot cast object of type %s to %s. In class %s#%s", defaultValue.getClass(), defaultValueType, clazz, field));
            }
            return (T) defaultValue;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean getAnnotationBoolean(Class<?> clazz, String field) {
        try {
            Object defaultValue = clazz.getDeclaredMethod(field).getDefaultValue();
            if (defaultValue != null && !(defaultValue instanceof Boolean)) { // Same check as in Class.cast
                throw new ClassCastException(String.format("Cannot cast object of type %s to Boolean. In class %s#%s", defaultValue.getClass(), clazz, field));
            }
            return defaultValue != null && (boolean) defaultValue;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
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

                String targetName = (String) values.get("value");
                boolean makeSyntheticAccessor = (boolean) values.get("makeSyntheticAccessor");
                @SuppressWarnings("unchecked") String desc = parseMethodDescriptor((Map<String, Object>) values.get("signature"));
                TransformFrom.ApplicationStage requestedStage = (TransformFrom.ApplicationStage) values.get("stage");
                @SuppressWarnings("unchecked") List<RedirectSet> redirectSets = ((List<Type>) values.get("redirectSets")).stream()
                        .flatMap(type -> getRedirectSetsForType(type).stream())
                        .collect(Collectors.toList());

                @SuppressWarnings("unchecked") Type srcOwner = parseRefAnnotation((Map<String, Object>) values.get("copyFrom"));

                if (stage != requestedStage) {
                    continue;
                }

                if (desc == null) {
                    int split = targetName.indexOf('(');
                    desc = targetName.substring(split);
                    targetName = targetName.substring(0, split);
                }
                TargetMethod targetMethod;
                if (srcOwner == null) {
                    targetMethod = new TargetMethod(
                            new ClassMethod(Type.getObjectType(targetClass.name), new org.objectweb.asm.commons.Method(targetName, desc)),
                            methodPrefix + method.name, // Name is modified here to prevent mixin from overwriting it. We remove this prefix in postApply.
                            true,
                            makeSyntheticAccessor,
                            redirectSets
                    );
                } else {
                    targetMethod = new TargetMethod(
                            srcOwner,
                            new ClassMethod(Type.getObjectType(targetClass.name), new org.objectweb.asm.commons.Method(targetName, desc)),
                            methodPrefix + method.name, // Name is modified here to prevent mixin from overwriting it. We remove this prefix in postApply.
                            true,
                            makeSyntheticAccessor,
                            redirectSets
                    );
                }
                if (classTarget.targetMethods().stream().anyMatch(t -> t.method().method.equals(targetMethod.method().method))) {
                    throw new RuntimeException(String.format("Trying to add duplicate TargetMethod to %s:\n\t\t\t\t%s | %s", classTarget.getClassName(), targetMethod.method().owner,
                            targetMethod.method().method));
                }
                classTarget.addTarget(targetMethod);
            }
        }
    }

    private List<RedirectSet> getRedirectSetsForType(Type type) {
        return this.redirectSetsByType.computeIfAbsent(type, t -> {
            List<RedirectSet> redirectSets = new ArrayList<>();

            ClassNode classNode = classNodeForType(type);
            if ((classNode.access & ACC_INTERFACE) == 0) {
                throw new IllegalStateException("Non-interface type is a redirect set");
            }

            AnnotationNode annotationNode = getAnnotationIfPresent(classNode, DasmRedirectSet.class);
            if (annotationNode == null) {
                throw new IllegalStateException(String.format("Class %s is used as a redirect set but not marked with %s", type.getClassName(), DasmRedirectSet.class.getSimpleName()));
            }

            // First add inherited redirect sets
            for (String interface_ : classNode.interfaces) {
                redirectSets.addAll(getRedirectSetsForType(Type.getObjectType(interface_)));
            }

            // Then add this set
            RedirectSet thisRedirectSet = new RedirectSet(type.getClassName());
            redirectSets.add(thisRedirectSet);

            // Discover type/field/method redirects in innerclass
            for (InnerClassNode innerClass : classNode.innerClasses) {
                ClassNode innerClassNode = classNodeForType(Type.getObjectType(innerClass.name));
                thisRedirectSet.addRedirect(parseTypeRedirect(innerClassNode));

                for (FieldNode field : innerClassNode.fields) {
                    thisRedirectSet.addRedirect(parseFieldRedirect(
                            field,
                            Type.getType("L" + innerClassNode.name + ";"),
                            null)
                    );
                }

                for (MethodNode method : innerClassNode.methods) {
                    // skip checking the invisible default constructor if it doesn't have annotations
                    if (method.name.equals("<init>") && method.desc.equals("()V") && method.invisibleAnnotations == null) {
                        continue;
                    }

                    thisRedirectSet.addRedirect(parseMethodRedirect(
                            method,
                            Type.getType("L" + innerClassNode.name + ";"),
                            null,
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


    @Nullable private AnnotationNode getAnnotationIfPresent(ClassNode classNode, Class<?> annotation) {
        if (classNode.invisibleAnnotations == null) {
            return null;
        }
        for (AnnotationNode annotationNode : classNode.invisibleAnnotations) {
            if (annotationNode.desc.equals(classToDescriptor(annotation))) {
                return annotationNode;
            }
        }
        return null;
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

    private static String parseMethodDescriptor(Map<String, Object> ann) {
        if (ann == null) {
            return null;
        }

        Type ret = (Type) ann.get("ret");
        @SuppressWarnings("unchecked") List<Type> args = (List<Type>) ann.get("args");
        boolean fromString = (boolean) ann.get("fromString");

        if (fromString) {
            return null;
        }
        return Type.getMethodDescriptor(ret, args.toArray(new Type[0]));
    }

    private ClassNode classNodeForType(Type type) {
        ClassNode dst = new ClassNode(ASM9);
        final ClassReader classReader = new ClassReader(this.classProvider.classBytes(type.getClassName()));
        classReader.accept(dst, 0);
        return dst;
    }

    private static String classToDescriptor(Class<?> clazz) {
        return "L" + clazz.getName().replace('.', '/') + ";";
    }

    private static String classDescriptorToClassName(String descriptor) {
        return Type.getType(descriptor).getClassName();
    }
}
