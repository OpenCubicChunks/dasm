package io.github.opencubicchunks.dasm;

import io.github.opencubicchunks.dasm.api.provider.ClassProvider;
import io.github.opencubicchunks.dasm.api.redirect.DasmRedirectSet;
import io.github.opencubicchunks.dasm.api.transform.DasmRedirect;
import io.github.opencubicchunks.dasm.api.Ref;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

            List<Object> values = ann.values;
            Type srcClass = Type.getType(getAnnotationDefaultValue(Ref.class, "value", Class.class));
            TransformFrom.ApplicationStage requestedStage = TransformFrom.ApplicationStage.PRE_APPLY;
            for (int i = 0, valuesSize = values.size(); i < valuesSize; i += 2) {
                String name = (String) values.get(i);
                Object value = values.get(i + 1);
                if (name.equals("value")) {
                    srcClass = parseRefAnnotation((AnnotationNode) value);
                } else if (name.equals("stage")) {
                    String[] parts = ((String[]) value);
                    requestedStage = TransformFrom.ApplicationStage.valueOf(parts[1]);
                }
            }
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

                // The name value pairs of this annotation. Each name value pair is stored as two consecutive
                // elements in the list. The name is a String, and the value may be a
                // Byte, Boolean, Character, Short, Integer, Long, Float, Double, String or org.objectweb.asm.Type,
                // or a two elements String array (for enumeration values), an AnnotationNode,
                // or a List of values of one of the preceding types. The list may be null if there is no name value pair.
                List<Object> values = ann.values;
                String targetName = getAnnotationDefaultValue(TransformFrom.class, "value", String.class);
                boolean makeSyntheticAccessor = getAnnotationBoolean(TransformFrom.class, "makeSyntheticAccessor");
                String desc = null;
                TransformFrom.ApplicationStage requestedStage = TransformFrom.ApplicationStage.PRE_APPLY;
                List<RedirectSet> redirectSets = new ArrayList<>();
                Type srcOwner = Type.getType(getAnnotationDefaultValue(Ref.class, "value", Class.class));
                for (int i = 0, valuesSize = values.size(); i < valuesSize; i += 2) {
                    String name = (String) values.get(i);
                    Object value = values.get(i + 1);
                    switch (name) {
                        case "value":
                            targetName = (String) value;
                            break;
                        case "makeSyntheticAccessor":
                            makeSyntheticAccessor = (Boolean) value;
                            break;
                        case "signature":
                            desc = parseMethodDescriptor((AnnotationNode) value);
                            break;
                        case "stage":
                            String[] parts = ((String[]) value);
                            requestedStage = TransformFrom.ApplicationStage.valueOf(parts[1]);
                            break;
                        case "copyFrom":
                            srcOwner = parseRefAnnotation((AnnotationNode) value);
                            break;
                        case "redirectSets":
                            redirectSets.addAll(Stream.of((Type[]) value).flatMap(type -> getRedirectSetsForType(type).stream()).collect(Collectors.toList()));
                            break;
                    }
                }
                if (stage != requestedStage) {
                    continue;
                }

                if (desc == null) {
                    int split = targetName.indexOf('(');
                    desc = targetName.substring(split);
                    targetName = targetName.substring(0, split);
                }
                TargetMethod targetMethod;
                if (srcOwner.equals(Type.getType(Object.class))) {
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

    private Map<String, Object> getAnnotationValues(AnnotationNode annotationNode, Class<?> annotation) {
        Map<String, Object> annotationValues = new HashMap<>();

        // Insert specified arguments in the annotation
        for (int i = 0; i < annotationNode.values.size(); i += 2) {
            String name = (String) annotationNode.values.get(i);
            Object value = annotationNode.values.get(i + 1);

            if (value.getClass().isAnnotation()) {
                AnnotationNode annotationValue = (AnnotationNode) value;
                try {
                    value = getAnnotationValues(annotationValue, Class.forName(annotationValue.desc));
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
            if (declaredMethod.isDefault() && !annotationValues.containsKey(declaredMethod.getName())) {
                if (declaredMethod.getReturnType().isAnnotation()) {
                    Map<String, Object> value = new HashMap<>();
                    addMissingDefaultValues(declaredMethod.getReturnType(), value);
                    annotationValues.put(declaredMethod.getName(), value);
                } else {
                    annotationValues.put(declaredMethod.getName(), declaredMethod.getDefaultValue());
                }
            }
        }
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

            String newName = null;
            Type mappingsOwner = null;
            for (int i = 0, valuesSize = annotation.values.size(); i < valuesSize; i += 2) {
                String name = (String) annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                switch (name) {
                    case "value":
                        newName = (String) value;
                        break;
                    case "mappingsOwner":
                        mappingsOwner = parseRefAnnotation(annotation);
                        break;
                }
            }
            if (newName == null) {
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

            String newName = null;
            for (int i = 0, valuesSize = annotation.values.size(); i < valuesSize; i += 2) {
                String name = (String) annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if (name.equals("value")) {
                    newName = ((String) value);
                }
            }
            if (newName == null) {
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

            Type from = null;
            Type to = null;
            for (int i = 0, valuesSize = annotation.values.size(); i < valuesSize; i += 2) {
                String name = (String) annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                switch (name) {
                    case "from":
                        from = parseRefAnnotation((AnnotationNode) value);
                        break;
                    case "to":
                        to = parseRefAnnotation((AnnotationNode) value);
                        break;
                }
            }
            if (from == null || to == null) {
                throw new IllegalStateException(String.format("Invalid type redirect: %s -> %s", from, to));
            }
            return new TypeRedirect(from.getClassName(), to.getClassName());
        }

        throw new IllegalStateException(String.format("No type redirect on inner class %s", innerClass.name));
    }

    private static Type parseRefAnnotation(AnnotationNode refNode) {
        assert refNode.values.size() == 2 : "Clazz annotation has multiple targeting fields";

        if ((refNode.values.get(0)).equals("value")) {
            return (Type) refNode.values.get(1);
        } else if ((refNode.values.get(0)).equals("string")) {
            return Type.getObjectType((String) refNode.values.get(1));
        }
        return Type.getType(getAnnotationDefaultValue(Ref.class, "value", Class.class));
    }

    private static String parseMethodDescriptor(AnnotationNode ann) {
        if (ann == null) {
            return null;
        }
        List<Object> values = ann.values;

        Type ret = null;
        List<Type> args = null;
        boolean useFromString = false;
        for (int i = 0, valuesSize = values.size(); i < valuesSize; i += 2) {
            String name = (String) values.get(i);
            Object value = values.get(i + 1);
            switch (name) {
                case "ret":
                    ret = (Type) value;
                    break;
                case "args":
                    args = (List<Type>) value;
                    break;
                case "useFromString":
                    useFromString = (Boolean) value;
                    break;
            }
        }
        if (useFromString) {
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

    private String classToDescriptor(Class<?> clazz) {
        return "L" + clazz.getName().replace('.', '/') + ";";
    }
}
