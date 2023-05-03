package io.github.opencubicchunks.dasm.util;

import io.github.opencubicchunks.dasm.RedirectsParseException;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class ResolutionUtils {
    public static String applyImportsToMethodSignature(String signature, Map<String, String> imports) throws RedirectsParseException {
        int spaceIdx = signature.indexOf(" ");
        int openingBraceIdx = signature.indexOf("(");
        int closingBracketIdx = signature.indexOf(")");
        if (spaceIdx == -1 || openingBraceIdx == -1 || closingBracketIdx == -1) {
            throw new RedirectsParseException(String.format("Illegal method signature \"%s\"", signature));
        }

        String returnType = signature.substring(0, spaceIdx).trim();
        String resolvedReturnType = resolveType(returnType, imports);

        String methodName = signature.substring(spaceIdx + 1, openingBraceIdx);
        String methodArgs = signature.substring(openingBraceIdx + 1, closingBracketIdx);

        StringBuilder sb = new StringBuilder(resolvedReturnType).append(" ")
                .append(methodName).append('(');
        String[] split = methodArgs.split(",");
        if (split.length > 0 && !split[0].equals("")) { // java split returns a 1-size array even for an empty string
            for (int i = 0; i < split.length; i++) {
                String argument = split[i];
                sb.append(resolveType(argument.trim(), imports));

                if (i < split.length - 1) {
                    sb.append(", ");
                }
            }
        }
        sb.append(')');

        return sb.toString();
    }

    public static String resolveType(String typeName, Map<String, String> imports) throws RedirectsParseException {
        int indexOfArray = typeName.indexOf("[");

        if (indexOfArray == -1) { // no array
            String resolved = imports.getOrDefault(typeName, typeName);
            validateType(typeName, resolved);
            return resolved;
        }

        String type = typeName.substring(0, indexOfArray);

        int arrayDepth = StringUtils.countMatches(typeName, "[]");
        String arrayPart = StringUtils.repeat("[]", arrayDepth);

        String resolved = imports.getOrDefault(type, type);
        validateType(type, resolved);
        return resolved + arrayPart;
    }

    public static void validateType(String typeName, String resolvedTypeName) throws RedirectsParseException {
        switch (typeName) {
            case "void":
            case "boolean":
            case "char":
            case "byte":
            case "short":
            case "int":
            case "float":
            case "long":
            case "double":
                return;
        }
        if (resolvedTypeName == null) {
            throw new RedirectsParseException("No import for type " + typeName);
        }
        if (!resolvedTypeName.contains(".")) {
            throw new RedirectsParseException(String.format("Import-resolved class is illegal: \"%s\" -> \"%s\"", typeName, resolvedTypeName));
        }
    }
}
