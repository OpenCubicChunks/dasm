package io.github.opencubicchunks.dasm;

import java.util.*;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class RedirectsParser {
    private static final String DEFAULT_SETS_NAME = "defaultSets";
    private static final String TARGETS_NAME = "targets";
    private static final String TARGET_METHODS_NAME = "targetMethods";
    private static final String USE_SETS_NAME = "useSets";
    private static final String TYPE_REDIRECTS_NAME = "typeRedirects";
    private static final String METHOD_REDIRECTS_NAME = "methodRedirects";
    private static final String FIELD_REDIRECTS_NAME = "fieldRedirects";

    private static final String MAKE_SYNTHETIC_ACCESSOR_NAME = "makeSyntheticAccessor";
    private static final String SHOULD_CLONE_NAME = "shouldClone";
    private static final String MAPPINGS_OWNER_NAME = "mappingsOwner";
    private static final String DST_NAME = "newName";

    public List<RedirectSet> parseRedirectSet(JsonObject json) throws RedirectsParseException {
        List<RedirectSet> redirectSets = new ArrayList<>();
        for (Map.Entry<String, JsonElement> classRedirectObject : json.entrySet()) {
            String redirectSetName = throwOnLengthZero(classRedirectObject.getKey(), () -> "Redirect Set node has empty name");

            JsonElement redirectElement = classRedirectObject.getValue();
            if (!redirectElement.isJsonObject()) {
                throw new RedirectsParseException(String.format("Invalid redirect set node %s", redirectElement));
            }
            JsonObject redirectJson = redirectElement.getAsJsonObject();

            RedirectSet redirectSet = new RedirectSet(redirectSetName);

            if (!redirectJson.has(TYPE_REDIRECTS_NAME) || !redirectJson.get(TYPE_REDIRECTS_NAME).isJsonObject()) {
                throw new RedirectsParseException(String.format("Redirect set has no \"%s\" object", TYPE_REDIRECTS_NAME));
            }
            Set<Map.Entry<String, JsonElement>> typeRedirects = redirectJson.get(TYPE_REDIRECTS_NAME).getAsJsonObject().entrySet();
            parseTypeRedirects(redirectSet, typeRedirects);

            if (!redirectJson.has(FIELD_REDIRECTS_NAME) || !redirectJson.get(FIELD_REDIRECTS_NAME).isJsonObject()) {
                throw new RedirectsParseException(String.format("Redirect set has no \"%s\" object", FIELD_REDIRECTS_NAME));
            }
            Set<Map.Entry<String, JsonElement>> fieldRedirects = redirectJson.get(FIELD_REDIRECTS_NAME).getAsJsonObject().entrySet();
            parseFieldRedirects(redirectSet, fieldRedirects);

            if (!redirectJson.has(METHOD_REDIRECTS_NAME) || !redirectJson.get(METHOD_REDIRECTS_NAME).isJsonObject()) {
                throw new RedirectsParseException(String.format("Redirect set has no \"%s\" object", METHOD_REDIRECTS_NAME));
            }
            Set<Map.Entry<String, JsonElement>> methodRedirects = redirectJson.get(METHOD_REDIRECTS_NAME).getAsJsonObject().entrySet();
            parseMethodRedirects(redirectSet, methodRedirects);

            redirectSets.add(redirectSet);
        }

        return redirectSets;
    }

    public List<ClassTarget> parseClassTargets(JsonObject json) throws RedirectsParseException {
        List<ClassTarget> classTargets = new ArrayList<>();

        List<String> defaultSets = new ArrayList<>();
        if (json.has(DEFAULT_SETS_NAME)) {
            JsonElement sets = json.get(DEFAULT_SETS_NAME);
            if (!sets.isJsonArray()) {
                throw new RedirectsParseException(String.format("Targets has invalid %s: %s", DEFAULT_SETS_NAME, sets));
            }

            JsonArray setArray = sets.getAsJsonArray();
            for (JsonElement setElement : setArray) {
                if (!setElement.isJsonPrimitive() || !setElement.getAsJsonPrimitive().isString()) {
                    throw new RedirectsParseException(String.format("%s has invalid value %s", DEFAULT_SETS_NAME, setElement));
                }
                defaultSets.add(setElement.getAsJsonPrimitive().getAsString());
            }
        }

        if (!json.has(TARGETS_NAME) || !json.get(TARGETS_NAME).isJsonObject()) {
            throw new RedirectsParseException(String.format("Could not find or invalid %s tag in %s", TARGETS_NAME, json));
        }
        JsonObject targetsJson = json.get(TARGETS_NAME).getAsJsonObject();

        for (Map.Entry<String, JsonElement> classRedirectObject : targetsJson.entrySet()) {
            String classTargetName = throwOnLengthZero(classRedirectObject.getKey(), () -> "Class target node has empty name");

            JsonElement classTargetElement = classRedirectObject.getValue();
            if (!classTargetElement.isJsonObject()) {
                throw new RedirectsParseException(String.format("Invalid Class target node %s", classTargetElement));
            }
            JsonObject classTargetNode = classTargetElement.getAsJsonObject();

            ClassTarget classTarget = new ClassTarget(classTargetName);

            if (!classTargetNode.has(USE_SETS_NAME)) { // if there are no specified sets, use the default ones
                for (String set : defaultSets) {
                    classTarget.addUsesSet(set);
                }
            } else { // sets are specified, find them
                JsonElement usesSetNameElement = classTargetNode.get(USE_SETS_NAME);
                if (usesSetNameElement.isJsonPrimitive() && usesSetNameElement.getAsJsonPrimitive().isString()) {
                    //single set
                    classTarget.addUsesSet(throwOnLengthZero(usesSetNameElement.getAsString(), () -> "Specified Class set name has zero length"));
                } else if (usesSetNameElement.isJsonArray()) {
                    //multiple sets
                    JsonArray usesSetNames = usesSetNameElement.getAsJsonArray();
                    for (JsonElement usesSetName : usesSetNames) {
                        classTarget.addUsesSet(usesSetName.getAsString());
                    }
                }
            }

            // it's possible the class target to target the whole class, and so has no target methods
            if (classTargetNode.has(TARGET_METHODS_NAME) && classTargetNode.get(TARGET_METHODS_NAME).isJsonObject()) {
                Set<Map.Entry<String, JsonElement>> targetMethodsNode = classTargetNode.get(TARGET_METHODS_NAME).getAsJsonObject().entrySet();
                parseTargetMethods(classTarget, targetMethodsNode);
            } else {
                classTarget.targetWholeClass();
            }

            classTargets.add(classTarget);
        }

        return classTargets;
    }

    private void parseTargetMethods(ClassTarget output, Set<Map.Entry<String, JsonElement>> methodRedirects) throws RedirectsParseException {
        for (Map.Entry<String, JsonElement> methodRedirect : methodRedirects) {
            Transformer.ClassMethod method = parseTargetMethodSignature(output.getClassName(), methodRedirect.getKey());

            JsonElement value = methodRedirect.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String dstMethodName = throwOnLengthZero(value.getAsString(), () -> String.format("Target method has zero length value for key %s", methodRedirect));
                output.addTarget(new ClassTarget.TargetMethod(
                        method,
                        dstMethodName,
                        true,
                        false)
                );
            } else if (value.isJsonObject()) { // target method might want a synthetic accessor
                JsonObject targetMethodValue = value.getAsJsonObject();

                if (!targetMethodValue.has(DST_NAME)) {
                    throw new RedirectsParseException(String.format("Target method value does not contain a value for \"%s\". %s", DST_NAME, targetMethodValue));
                }
                JsonElement newNameNode = targetMethodValue.get(DST_NAME);
                if (!newNameNode.isJsonPrimitive() || !newNameNode.getAsJsonPrimitive().isString()) {
                    throw new RedirectsParseException(String.format("Target method value does not contain a valid \"%s\". %s", DST_NAME, newNameNode));
                }

                String dstMethodName = throwOnLengthZero(newNameNode.getAsString(), () -> String.format("Target method has zero length value for key %s", methodRedirect));

                boolean makeSyntheticAccessor = getMakeSyntheticAccessorIfPresent(targetMethodValue);
                boolean shouldClone = getShouldCloneIfPresent(targetMethodValue);
                String mappingsOwner = getMappingsOwnerIfPresent(targetMethodValue);

                output.addTarget(new ClassTarget.TargetMethod(
                        new Transformer.ClassMethod(method.owner, method.method, mappingsOwner == null ? method.owner : getTypeFromName(mappingsOwner)),
                        dstMethodName,
                        shouldClone,
                        makeSyntheticAccessor)
                );
            } else {
                throw new RedirectsParseException(String.format("Could not parse Target method %s", methodRedirect));
            }
        }
    }

    private void parseTypeRedirects(RedirectSet output, Set<Map.Entry<String, JsonElement>> typeRedirects) throws RedirectsParseException {
        for (Map.Entry<String, JsonElement> typeRedirect : typeRedirects) {
            JsonElement value = typeRedirect.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new RedirectsParseException(String.format("Type redirect value has invalid structure: %s", value));
            }
            output.addRedirect(new RedirectSet.TypeRedirect(
                    throwOnLengthZero(typeRedirect.getKey(), () -> String.format("Type redirect has zero length string key: %s", typeRedirect)),
                    throwOnLengthZero(value.getAsString(), () -> String.format("Type redirect has zero length string value: %s", typeRedirect))
            ));
        }
    }

    private void parseFieldRedirects(RedirectSet output, Set<Map.Entry<String, JsonElement>> fieldRedirects) throws RedirectsParseException {
        for (Map.Entry<String, JsonElement> fieldRedirect : fieldRedirects) {
            Transformer.ClassField field = parseFieldSignature(fieldRedirect.getKey());

            JsonElement value = fieldRedirect.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                output.addRedirect(new RedirectSet.FieldRedirect(
                        field,
                        throwOnLengthZero(value.getAsString(), () -> String.format("Field redirect has zero length string value: %s", fieldRedirect))
                ));
            } else if (value.isJsonObject()) { // field redirect might contain a mappings owner
                JsonObject fieldRedirectValue = value.getAsJsonObject();

                if (!fieldRedirectValue.has(DST_NAME)) {
                    throw new RedirectsParseException(String.format("Field redirect value does not contain a value for \"%s\". %s", DST_NAME, fieldRedirectValue));
                }
                JsonElement newNameNode = fieldRedirectValue.get(DST_NAME);
                if (!newNameNode.isJsonPrimitive() || !newNameNode.getAsJsonPrimitive().isString()) {
                    throw new RedirectsParseException(String.format("Field redirect value does not contain a valid \"%s\". %s", DST_NAME, newNameNode));
                }

                String dstFieldName = throwOnLengthZero(newNameNode.getAsString(), () -> String.format("Field redirect has zero length value for key %s", fieldRedirect));
                String mappingsOwner = getMappingsOwnerIfPresent(fieldRedirectValue); //TODO: support if required at some point

                output.addRedirect(new RedirectSet.FieldRedirect(field, dstFieldName));
            } else {
                throw new RedirectsParseException(String.format("Field redirect value has invalid structure: %s", value));
            }
        }
    }

    private void parseMethodRedirects(RedirectSet output, Set<Map.Entry<String, JsonElement>> methodRedirects) throws RedirectsParseException {
        for (Map.Entry<String, JsonElement> methodRedirect : methodRedirects) {
            Transformer.ClassMethod method = parseMethodSignature(methodRedirect.getKey());

            JsonElement value = methodRedirect.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String dstMethodName = throwOnLengthZero(value.getAsString(), () -> String.format("Method redirect has zero length value for key %s", methodRedirect));
                output.addRedirect(new RedirectSet.MethodRedirect(method, dstMethodName));
            } else if (value.isJsonObject()) { // method redirect might contain a mappings owner
                JsonObject methodRedirectValue = value.getAsJsonObject();

                if (!methodRedirectValue.has(DST_NAME)) {
                    throw new RedirectsParseException(String.format("Method redirect value does not contain a value for \"%s\". %s", DST_NAME, methodRedirectValue));
                }
                JsonElement newNameNode = methodRedirectValue.get(DST_NAME);
                if (!newNameNode.isJsonPrimitive() || !newNameNode.getAsJsonPrimitive().isString()) {
                    throw new RedirectsParseException(String.format("Method redirect value does not contain a valid \"%s\". %s", DST_NAME, newNameNode));
                }

                String dstMethodName = throwOnLengthZero(newNameNode.getAsString(), () -> String.format("Method redirect has zero length value for key %s", methodRedirect));
                String mappingsOwner = getMappingsOwnerIfPresent(methodRedirectValue);

                output.addRedirect(new RedirectSet.MethodRedirect(
                        new Transformer.ClassMethod(
                                method.owner,
                                method.method,
                                mappingsOwner == null ? method.owner : getTypeFromName(mappingsOwner)
                        ),
                        dstMethodName
                ));
            } else {
                throw new RedirectsParseException(String.format("Could not parse Method redirect %s", methodRedirect));
            }
        }
    }

    private boolean getMakeSyntheticAccessorIfPresent(JsonObject redirectElement) throws RedirectsParseException {
        return getBooleanIfPresent(redirectElement, MAKE_SYNTHETIC_ACCESSOR_NAME, false);
    }

    private boolean getShouldCloneIfPresent(JsonObject redirectElement) throws RedirectsParseException {
        return getBooleanIfPresent(redirectElement, SHOULD_CLONE_NAME, true);
    }

    private boolean getBooleanIfPresent(JsonObject object, String name, boolean defaultValue) throws RedirectsParseException {
        boolean shouldClone = defaultValue;
        if (object.has(name)) { // synthetic accessor is optional
            JsonElement syntheticAccessorNode = object.get(name);
            if (!syntheticAccessorNode.isJsonPrimitive() || !syntheticAccessorNode.getAsJsonPrimitive().isBoolean()) {
                throw new RedirectsParseException(String.format("Redirect value does not contain a valid \"%s\". %s", name, syntheticAccessorNode));
            }
            shouldClone = syntheticAccessorNode.getAsBoolean();
        }
        return shouldClone;
    }

    private String getMappingsOwnerIfPresent(JsonObject targetMethodValue)
            throws RedirectsParseException {
        String mappingsOwner = null;
        if (targetMethodValue.has(MAPPINGS_OWNER_NAME)) { // mappings owner is optional
            JsonElement mappingsOwnerNode = targetMethodValue.get(MAPPINGS_OWNER_NAME);
            if (!mappingsOwnerNode.isJsonPrimitive() || !mappingsOwnerNode.getAsJsonPrimitive().isString()) {
                throw new RedirectsParseException(String.format("Redirect value does not contain a valid \"%s\". %s", MAPPINGS_OWNER_NAME, mappingsOwnerNode));
            }
            mappingsOwner = throwOnLengthZero(mappingsOwnerNode.getAsString(),
                    () -> String.format("Field redirect has zero length value for %s: %s", MAPPINGS_OWNER_NAME, mappingsOwnerNode));
        }
        return mappingsOwner;
    }

    /**
     * Accepts signature like {@code owner#type ident}
     * <p>eg: {@code net.minecraft.world.level.chunk.LevelChunkSection sections}</p>
     */
    public static Transformer.ClassMethod parseTargetMethodSignature(String owner, String in) throws RedirectsParseException {
        String s = throwOnLengthZero(in, () -> "Signature has zero length");

        try {
            Method method = Method.getMethod(s);

            return new Transformer.ClassMethod(getTypeFromName(owner), method);
        } catch (IllegalArgumentException e) {
            throw new RedirectsParseException(String.format("Could not parse signature %s", s), e);
        }
    }

    /**
     * Accepts signature like {@code owner#type ident}
     * <p>eg: {@code net.minecraft.world.level.chunk.LevelChunk | net.minecraft.world.level.chunk.LevelChunkSection sections}</p>
     */
    public static Transformer.ClassMethod parseMethodSignature(String in) throws RedirectsParseException {
        String s = throwOnLengthZero(in, () -> "Signature has zero length");

        try {
            String[] split = requireLengthN(s.split(" ?\\| ?"), 2, () -> String.format("Invalid signature, \"%s\" didnt have a `|`. Expected signature like: OWNER | RETURNTYPE IDENT", in));

            Method method = Method.getMethod(split[1]);

            return new Transformer.ClassMethod(getTypeFromName(split[0]), method);
        } catch (IllegalArgumentException e) {
            throw new RedirectsParseException(String.format("Could not parse signature %s", s), e);
        }
    }

    /**
     * Accepts signature like {@code owner#type ident}
     * <p>eg: {@code net.minecraft.world.level.chunk.LevelChunk | net.minecraft.world.level.chunk.LevelChunkSection sections}</p>
     */
    public static Transformer.ClassField parseFieldSignature(String in) throws RedirectsParseException {
        String s = throwOnLengthZero(in, () -> "Signature has zero length");

        try {
            String[] split = requireLengthN(s.split(" ?\\| ?"), 2, () -> String.format("Invalid signature, \"%s\" didnt have a `|`. Expected signature like: OWNER | RETURNTYPE IDENT", in));

            String[] typeIdentSplit = split[1].split(" ");

            return new Transformer.ClassField(getTypeFromName(split[0]), typeIdentSplit[1], getTypeFromName(typeIdentSplit[0]));
        } catch (IllegalArgumentException e) {
            throw new RedirectsParseException(String.format("Could not parse signature %s", s), e);
        }
    }

    private static String throwOnLengthZero(String string, Supplier<String> message) throws RedirectsParseException {
        if (string.length() < 1) {
            throw new RedirectsParseException(message.get());
        }
        return string;
    }

    private static <T> T[] requireLengthN(T[] array, int requiredLength, Supplier<String> message) throws RedirectsParseException {
        if (array.length != requiredLength) {
            throw new RedirectsParseException(message.get());
        }
        return array;
    }

    /**
     * @param typeName Expected without generics in format:<p>
     *     {@code the.package.name.Class}<p>
     *     {@code int}<p>
     *     {@code int[]}<p>
     *     {@code the.package.name.Class[]}<p>
     */
    private static Type getTypeFromName(String typeName) {
        return Method.getMethod(typeName + " x()").getReturnType();
    }

    public static class ClassTarget {
        private final String className;
        private final List<String> usesSets = new ArrayList<>();
        private final List<TargetMethod> targetMethods = new ArrayList<>();
        private boolean wholeClass = false;

        public ClassTarget(String className) {
            this.className = className;
        }

        public void addUsesSet(String usesSet) {
            this.usesSets.add(usesSet);
        }

        public void addTarget(TargetMethod targetMethod) {
            if (wholeClass) {
                throw new IllegalStateException("Cannot add target methods when targeting whole class!");
            }
            this.targetMethods.add(targetMethod);
        }

        /**
         * Specifies targeting a whole class, without cloning methods. Allows in-place redirects for the whole class
         */
        public void targetWholeClass() {
            if (!targetMethods.isEmpty()) {
                throw new IllegalStateException("Cannot add target whole class when method targets are specified!");
            }
            wholeClass = true;
        }

        public String getClassName() {
            return className;
        }

        public List<String> getSets() {
            return Collections.unmodifiableList(usesSets);
        }

        public boolean isWholeClass() {
            return wholeClass;
        }

        public List<TargetMethod> getTargetMethods() {
            return Collections.unmodifiableList(targetMethods);
        }

        public static final class TargetMethod {
            private final Transformer.ClassMethod method;
            private final String dstMethodName;
            private final boolean shouldClone;
            private final boolean makeSyntheticAccessor;

            public TargetMethod(Transformer.ClassMethod method, String dstMethodName, boolean shouldClone, boolean makeSyntheticAccessor) {
                this.method = method;
                this.dstMethodName = dstMethodName;
                this.shouldClone = shouldClone;
                this.makeSyntheticAccessor = makeSyntheticAccessor;
            }

            public String dstMethodName() {
                return dstMethodName;
            }

            public boolean shouldClone() {
                return shouldClone;
            }

            public boolean makeSyntheticAccessor() {
                return makeSyntheticAccessor;
            }

            public Transformer.ClassMethod method() {
                return this.method;
            }

            @Override
            public String toString() {
                return "TargetMethod{" +
                        "method=" + method +
                        ", dstMethodName='" + dstMethodName + '\'' +
                        ", shouldClone=" + shouldClone +
                        ", makeSyntheticAccessor=" + makeSyntheticAccessor +
                        '}';
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                TargetMethod that = (TargetMethod) o;
                return shouldClone == that.shouldClone && makeSyntheticAccessor == that.makeSyntheticAccessor && Objects.equals(method, that.method) && Objects.equals(dstMethodName, that.dstMethodName);
            }

            @Override
            public int hashCode() {
                return Objects.hash(method, dstMethodName, shouldClone, makeSyntheticAccessor);
            }
        }
    }

    public static class RedirectSet {
        private final String name;
        private final List<TypeRedirect> typeRedirects = new ArrayList<>();
        private final List<FieldRedirect> fieldRedirects = new ArrayList<>();
        private final List<MethodRedirect> methodRedirects = new ArrayList<>();

        public RedirectSet(String name) {
            this.name = name;
        }

        public void addRedirect(TypeRedirect redirect) {
            this.typeRedirects.add(redirect);
        }

        public void addRedirect(FieldRedirect redirect) {
            this.fieldRedirects.add(redirect);
        }

        public void addRedirect(MethodRedirect redirect) {
            this.methodRedirects.add(redirect);
        }

        public String getName() {
            return name;
        }

        @NotNull public List<TypeRedirect> getTypeRedirects() {
            return Collections.unmodifiableList(this.typeRedirects);
        }

        @NotNull public List<FieldRedirect> getFieldRedirects() {
            return Collections.unmodifiableList(this.fieldRedirects);
        }

        @NotNull public List<MethodRedirect> getMethodRedirects() {
            return Collections.unmodifiableList(methodRedirects);
        }

        public static final class TypeRedirect {
            private final String srcClassName;
            private final String dstClassName;

            public TypeRedirect(String srcClassName, String dstClassName) {
                this.srcClassName = srcClassName;
                this.dstClassName = dstClassName;
            }

            public String srcClassName() {
                return srcClassName;
            }

            public String dstClassName() {
                return dstClassName;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                TypeRedirect that = (TypeRedirect) obj;
                return Objects.equals(this.srcClassName, that.srcClassName) &&
                        Objects.equals(this.dstClassName, that.dstClassName);
            }

            @Override
            public int hashCode() {
                return Objects.hash(srcClassName, dstClassName);
            }

            @Override
            public String toString() {
                return "TypeRedirect[" +
                        "srcClassName=" + srcClassName + ", " +
                        "dstClassName=" + dstClassName + ']';
            }
        }

        public static final class FieldRedirect {
            private final Transformer.ClassField field;
            private final String dstFieldName;

            public FieldRedirect(Transformer.ClassField field, String dstFieldName) {
                this.field = field;
                this.dstFieldName = dstFieldName;
            }

            public Transformer.ClassField field() {
                return field;
            }

            public String dstFieldName() {
                return dstFieldName;
            }

            @Override
            public String toString() {
                return "FieldRedirect{" +
                        "field=" + field +
                        ", dstFieldName='" + dstFieldName + '\'' +
                        '}';
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                FieldRedirect that = (FieldRedirect) o;
                return Objects.equals(field, that.field) && Objects.equals(dstFieldName, that.dstFieldName);
            }

            @Override
            public int hashCode() {
                return Objects.hash(field, dstFieldName);
            }
        }

        public static final class MethodRedirect {
            private final Transformer.ClassMethod method;
            private final String dstMethodName;

            public MethodRedirect(Transformer.ClassMethod method, String dstMethodName) {
                this.method = method;
                this.dstMethodName = dstMethodName;
            }

            public Transformer.ClassMethod method() {
                return method;
            }

            public String dstMethodName() {
                return dstMethodName;
            }

            @Override
            public String toString() {
                return "MethodRedirect{" +
                        "method=" + method +
                        ", dstMethodName='" + dstMethodName + '\'' +
                        '}';
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                MethodRedirect that = (MethodRedirect) o;
                return Objects.equals(method, that.method) && Objects.equals(dstMethodName, that.dstMethodName);
            }

            @Override
            public int hashCode() {
                return Objects.hash(method, dstMethodName);
            }
        }
    }
}
