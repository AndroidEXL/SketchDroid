package mod.jbk.editor.manage;

import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.eC;
import a.a.a.jC;
import a.a.a.uq;
import mod.hey.studios.moreblock.ReturnMoreblockManager;

/**
 * Converts plain Java code into Sketchware logic of a screen.
 *
 * <p>What it produces:
 * <ul>
 *     <li>class fields ({@code int}, {@code double}, {@code boolean}, {@code String}, {@code Map} and the matching
 *     {@code ArrayList} types) become Sketchware <b>Variables</b> / <b>Lists</b>;</li>
 *     <li>every convertible method becomes a <b>More Block</b> (with its body as Blocks) and all call sites are
 *     rewritten to the generated {@code _methodName(…)} form;</li>
 *     <li>lifecycle/override methods that Sketchware knows (onCreate, onResume, onSaveInstanceState, …) become
 *     <b>Events</b>;</li>
 *     <li>everything that has no direct block equivalent is preserved verbatim in
 *     {@code add source directly} <b>Blocks</b>, and class members that can't be expressed as variables
 *     (other types, {@code final} constants, constructors, inner classes, …) are kept as
 *     {@code Custom Variable} blocks, which Sketchware writes into the generated class body.</li>
 * </ul>
 */
public class CodeImporter {

    private static final String SPEC_ADD_SOURCE = "add source directly %s.inputOnly";
    private static final String SPEC_CUSTOM_VARIABLE = "Custom Variable Block: add variable %s";
    private static final int COLOR_RAW = 0xff5cb722;
    private static final int COLOR_CUSTOM = 0xff493f5a;

    private static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList(
            "public", "private", "protected", "static", "final", "abstract", "synchronized", "native",
            "strictfp", "transient", "volatile", "default"));

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern STRING_OR_CHAR = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern ANNOTATION = Pattern.compile("@\\w+(\\s*\\([^)]*\\))?");

    private static final Map<String, EventSpec> EVENTS = new LinkedHashMap<>();

    static {
        EVENTS.put("initializeLogic", new EventSpec("initializeLogic", new String[0], new String[0]));
        EVENTS.put("onCreate", new EventSpec("initializeLogic", new String[]{"Bundle"}, new String[0]));
        EVENTS.put("onActivityResult", new EventSpec("onActivityResult", new String[]{"int", "int", "Intent"},
                new String[]{"_requestCode", "_resultCode", "_data"}));
        EVENTS.put("onBackPressed", new EventSpec("onBackPressed", new String[0], new String[0]));
        EVENTS.put("onPostCreate", new EventSpec("onPostCreate", new String[]{"Bundle"}, new String[]{"_savedInstanceState"}));
        EVENTS.put("onStart", new EventSpec("onStart", new String[0], new String[0]));
        EVENTS.put("onResume", new EventSpec("onResume", new String[0], new String[0]));
        EVENTS.put("onPause", new EventSpec("onPause", new String[0], new String[0]));
        EVENTS.put("onStop", new EventSpec("onStop", new String[0], new String[0]));
        EVENTS.put("onDestroy", new EventSpec("onDestroy", new String[0], new String[0]));
        EVENTS.put("onSaveInstanceState", new EventSpec("onSaveInstanceState", new String[]{"Bundle"}, new String[]{"outState"}));
        EVENTS.put("onRestoreInstanceState", new EventSpec("onRestoreInstanceState", new String[]{"Bundle"}, new String[]{"savedInstanceState"}));
        EVENTS.put("onCreateOptionsMenu", new EventSpec("onCreateOptionsMenu", new String[]{"Menu"}, new String[]{"menu"}));
        EVENTS.put("onOptionsItemSelected", new EventSpec("onOptionsItemSelected", new String[]{"MenuItem"}, new String[]{"item"}));
        EVENTS.put("onCreateContextMenu", new EventSpec("onCreateContextMenu",
                new String[]{"ContextMenu", "View", "ContextMenu.ContextMenuInfo"}, new String[]{"menu", "view", "menuInfo"}));
        EVENTS.put("onContextItemSelected", new EventSpec("onContextItemSelected", new String[]{"MenuItem"}, new String[]{"item"}));
    }

    private final String scId;
    private final String javaName;
    private final eC data;

    private final Result result = new Result();
    private final ArrayList<Pair<Integer, String>> addedVariables = new ArrayList<>();
    private final ArrayList<Pair<Integer, String>> addedLists = new ArrayList<>();

    /**
     * What was created, so that the caller can tell the user about it.
     */
    public static class Result {
        public int blocks;
        public int variables;
        public int lists;
        public int moreBlocks;
        public int events;
        public int customMembers;
        public final ArrayList<String> warnings = new ArrayList<>();
    }

    public CodeImporter(String scId, ProjectFileBean projectFile) {
        this.scId = scId;
        javaName = projectFile.getJavaName();
        data = jC.a(scId);
    }

    /**
     * Parses {@code source} and saves everything it could convert into the project.
     *
     * @throws IllegalArgumentException if the code couldn't be parsed at all.
     */
    public Result importCode(String source) {
        String code = stripComments(source);
        if (code.trim().isEmpty()) {
            throw new IllegalArgumentException("the code is empty");
        }

        ArrayList<String> members = splitMembers(extractClassBody(code));
        if (members.isEmpty()) {
            throw new IllegalArgumentException("no code could be read");
        }

        ArrayList<String> initializeStatements = new ArrayList<>();
        ArrayList<String> customMembers = new ArrayList<>();
        LinkedHashMap<String, MoreBlockDef> moreBlocks = new LinkedHashMap<>();
        LinkedHashMap<String, ArrayList<String>> events = new LinkedHashMap<>();

        for (String member : members) {
            MethodDecl method = parseMethod(member);
            if (method != null) {
                EventSpec eventSpec = method.constructor ? null : matchEvent(method);
                if (eventSpec != null) {
                    ArrayList<String> statements = new ArrayList<>();
                    for (String statement : splitStatements(method.body)) {
                        if (isGeneratedBySketchware(statement)) {
                            continue;
                        }
                        statements.add(renameParameters(statement, method.params, eventSpec.paramNames));
                    }
                    events.computeIfAbsent(eventSpec.eventName, key -> new ArrayList<>()).addAll(statements);
                    continue;
                }

                MoreBlockDef moreBlock = method.constructor ? null : tryCreateMoreBlock(method);
                if (moreBlock != null) {
                    moreBlocks.put(method.name, moreBlock);
                    continue;
                }

                customMembers.add(method.raw);
                result.warnings.add("Kept as custom code: " + method.name + "(…)");
                continue;
            }

            FieldDecl field = parseField(member);
            if (field != null && !field.isFinal && !field.hasMultipleDeclarators) {
                int listType = listTypeOf(field.type);
                if (listType > 0) {
                    addList(listType, field.name);
                    if (field.initializer != null && !isCollectionInitializer(field.initializer)) {
                        initializeStatements.add(field.name + " = " + field.initializer + ";");
                    }
                    continue;
                }

                int variableType = variableTypeOf(field.type);
                if (variableType >= 0) {
                    addVariable(variableType, field.name);
                    if (field.initializer != null && !isCollectionInitializer(field.initializer)
                            && !isDefaultInitializer(variableType, field.initializer)) {
                        initializeStatements.add(field.name + " = " + field.initializer + ";");
                    }
                    continue;
                }
            }

            customMembers.add(member);
        }

        Set<String> renamedMethods = moreBlocks.keySet();
        int nextBlockId = 1;

        for (MoreBlockDef moreBlock : moreBlocks.values()) {
            BlockBuilder builder = new BlockBuilder(nextBlockId);
            for (String statement : moreBlock.statements) {
                builder.addRaw(rewriteCalls(statement, renamedMethods));
            }
            ArrayList<BlockBean> blocks = builder.build();

            data.a(javaName, moreBlock.nameWithType, moreBlock.spec);
            data.a(javaName, moreBlock.nameWithType + "_moreBlock", blocks);
            result.moreBlocks++;
            result.blocks += blocks.size();
            nextBlockId = builder.getNextId();
        }

        ArrayList<String> eventNames = new ArrayList<>(events.keySet());
        if ((!initializeStatements.isEmpty() || !customMembers.isEmpty()) && !events.containsKey("initializeLogic")) {
            eventNames.add("initializeLogic");
        }

        for (String eventName : eventNames) {
            BlockBuilder builder = new BlockBuilder(nextBlockId);
            ArrayList<String> statements = events.get(eventName);
            if (statements != null) {
                for (String statement : statements) {
                    builder.addRaw(rewriteCalls(statement, renamedMethods));
                }
            }
            if (eventName.equals("initializeLogic")) {
                for (String statement : initializeStatements) {
                    builder.addRaw(rewriteCalls(statement, renamedMethods));
                }
                for (String member : customMembers) {
                    builder.addCustomMember(member);
                    result.customMembers++;
                }
            }

            ArrayList<BlockBean> blocks = builder.build();
            if (blocks.isEmpty()) {
                continue;
            }

            ensureActivityEvent(eventName);
            data.a(javaName, eventName + "_" + eventName, mergeWithExistingBlocks(eventName, blocks));
            result.events++;
            result.blocks += blocks.size();
            nextBlockId = builder.getNextId();
        }

        if (result.moreBlocks == 0 && result.events == 0 && result.blocks == 0) {
            throw new IllegalArgumentException("no code could be converted");
        }

        if (result.customMembers > 0) {
            result.warnings.add(result.customMembers + " class member(s) were kept as custom code (Custom Variable blocks)");
        }

        data.k();
        return result;
    }

    // ---------------------------------------------------------------------------------------------
    // Blocks
    // ---------------------------------------------------------------------------------------------

    private static class BlockBuilder {
        private final ArrayList<BlockBean> blocks = new ArrayList<>();
        private int nextId;

        BlockBuilder(int firstId) {
            nextId = firstId;
        }

        int getNextId() {
            return nextId;
        }

        void addRaw(String code) {
            add("addSourceDirectly", SPEC_ADD_SOURCE, " ", "", COLOR_RAW, code);
        }

        void addCustomMember(String code) {
            add("addCustomVariable", SPEC_CUSTOM_VARIABLE, " ", "", COLOR_CUSTOM, code);
        }

        private void add(String opCode, String spec, String type, String typeName, int color, String... parameters) {
            BlockBean block = new BlockBean();
            block.id = String.valueOf(nextId++);
            block.opCode = opCode;
            block.spec = spec;
            block.type = type;
            block.typeName = typeName;
            block.color = color;
            block.parameters = new ArrayList<>(Arrays.asList(parameters));
            block.nextBlock = -1;
            block.subStack1 = -1;
            block.subStack2 = -1;
            blocks.add(block);
        }

        ArrayList<BlockBean> build() {
            for (int i = 0; i < blocks.size() - 1; i++) {
                blocks.get(i).nextBlock = Integer.parseInt(blocks.get(i + 1).id);
            }
            return blocks;
        }
    }

    /**
     * Keeps already existing Blocks of {@code eventName} and appends {@code added} to them.
     */
    private static int remapId(int id, Map<Integer, Integer> remappedIds) {
        if (id < 0) {
            return id;
        }
        Integer mapped = remappedIds.get(id);
        return mapped == null ? -1 : mapped;
    }

    private ArrayList<BlockBean> mergeWithExistingBlocks(String eventName, ArrayList<BlockBean> added) {
        ArrayList<BlockBean> existing = data.a(javaName, eventName + "_" + eventName);
        if (existing == null || existing.isEmpty()) {
            return added;
        }

        int newId = 1;
        for (BlockBean block : existing) {
            try {
                newId = Math.max(newId, Integer.parseInt(block.id) + 1);
            } catch (NumberFormatException ignored) {
            }
        }
        Map<Integer, Integer> remappedIds = new LinkedHashMap<>();
        for (BlockBean block : added) {
            int oldId = Integer.parseInt(block.id);
            remappedIds.put(oldId, newId);
            block.id = String.valueOf(newId++);
        }
        for (BlockBean block : added) {
            block.nextBlock = remapId(block.nextBlock, remappedIds);
            block.subStack1 = remapId(block.subStack1, remappedIds);
            block.subStack2 = remapId(block.subStack2, remappedIds);
            for (int i = 0; i < block.parameters.size(); i++) {
                String parameter = block.parameters.get(i);
                if (parameter != null && parameter.startsWith("@")) {
                    try {
                        Integer mapped = remappedIds.get(Integer.parseInt(parameter.substring(1)));
                        block.parameters.set(i, mapped == null ? "" : "@" + mapped);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        for (BlockBean block : existing) {
            if (block.nextBlock < 0) {
                block.nextBlock = Integer.parseInt(added.get(0).id);
                break;
            }
        }

        existing.addAll(added);
        return existing;
    }

    private void ensureActivityEvent(String eventName) {
        ArrayList<EventBean> events = data.g(javaName);
        if (events != null) {
            for (EventBean event : events) {
                if (event.eventType == EventBean.EVENT_TYPE_ACTIVITY && eventName.equals(event.eventName)) {
                    return;
                }
            }
        }
        data.a(javaName, new EventBean(EventBean.EVENT_TYPE_ACTIVITY, 0, eventName, eventName));
    }

    // ---------------------------------------------------------------------------------------------
    // Variables and lists
    // ---------------------------------------------------------------------------------------------

    private void addVariable(int type, String name) {
        if (isVariableAdded(type, name) || hasVariable(type, name)) {
            return;
        }
        addedVariables.add(new Pair<>(type, name));
        data.c(javaName, type, name);
        result.variables++;
    }

    private void addList(int type, String name) {
        if (isListAdded(type, name) || hasList(type, name)) {
            return;
        }
        addedLists.add(new Pair<>(type, name));
        data.b(javaName, type, name);
        result.lists++;
    }

    private boolean isVariableAdded(int type, String name) {
        for (Pair<Integer, String> variable : addedVariables) {
            if (variable.first == type && variable.second.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isListAdded(int type, String name) {
        for (Pair<Integer, String> list : addedLists) {
            if (list.first == type && list.second.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVariable(int type, String name) {
        ArrayList<Pair<Integer, String>> variables = data.k(javaName);
        if (variables == null) {
            return false;
        }
        for (Pair<Integer, String> variable : variables) {
            if (variable.first == type && variable.second.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasList(int type, String name) {
        ArrayList<Pair<Integer, String>> lists = data.j(javaName);
        if (lists == null) {
            return false;
        }
        for (Pair<Integer, String> list : lists) {
            if (list.first == type && list.second.equals(name)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // More Blocks
    // ---------------------------------------------------------------------------------------------

    private static class MoreBlockDef {
        String moreBlockName;
        String nameWithType;
        String spec;
        ArrayList<String> statements = new ArrayList<>();
    }

    private MoreBlockDef tryCreateMoreBlock(MethodDecl method) {
        if (!isValidName(method.name) || !isMoreBlockNameFree(method.name)) {
            return null;
        }
        if (method.name.length() > 2 && method.name.startsWith("on") && Character.isUpperCase(method.name.charAt(2))) {
            // Android callbacks / listeners that Sketchware generates itself
            return null;
        }

        String type = moreBlockTypeOf(method.returnType);
        if (type == null) {
            return null;
        }

        StringBuilder spec = new StringBuilder(method.name);
        ArrayList<String> parameterSpecs = new ArrayList<>();
        for (ParamDecl parameter : method.params) {
            String parameterType = switch (parameter.type) {
                case "boolean" -> "b";
                case "int", "double", "float", "long", "short", "byte" -> "d";
                case "String" -> "s";
                default -> null;
            };
            if (parameterType == null) {
                return null;
            }
            parameterSpecs.add("%" + parameterType + "." + parameter.name);
            if (isReassigned(method.body, parameter.name)) {
                // Sketchware's More Block parameters are final, so reassigning them wouldn't compile
                return null;
            }
        }

        MoreBlockDef definition = new MoreBlockDef();
        definition.moreBlockName = method.name;
        definition.nameWithType = ReturnMoreblockManager.injectMbType(method.name, method.name, type);
        if (!parameterSpecs.isEmpty()) {
            spec.append(" ").append(String.join(" ", parameterSpecs));
        }
        definition.spec = ReturnMoreblockManager.injectMbType(spec.toString(), method.name, type);

        for (String statement : splitStatements(method.body)) {
            definition.statements.add(renameParameters(statement, method.params, null));
        }
        return definition;
    }

    private boolean isMoreBlockNameFree(String name) {
        ArrayList<Pair<String, String>> moreBlocks = data.i(javaName);
        if (moreBlocks != null) {
            for (Pair<String, String> moreBlock : moreBlocks) {
                if (name.equals(ReturnMoreblockManager.getMbName(moreBlock.first))) {
                    return false;
                }
            }
        }
        return !Arrays.asList(uq.b).contains(name) && !Arrays.asList(uq.a()).contains(name);
    }

    // ---------------------------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------------------------

    private static final class ParamDecl {
        final String type;
        final String name;
        final String originalName;

        ParamDecl(String type, String name, String originalName) {
            this.type = type;
            this.name = name;
            this.originalName = originalName;
        }
    }

    private static final class MethodDecl {
        String name = "";
        String returnType = "";
        ArrayList<ParamDecl> params = new ArrayList<>();
        String body = "";
        String raw = "";
        boolean constructor;
    }

    private static final class FieldDecl {
        String type = "";
        String name = "";
        String initializer;
        boolean isFinal;
        boolean hasMultipleDeclarators;
    }

    private static final class EventSpec {
        final String eventName;
        final String[] paramTypes;
        final String[] paramNames;

        EventSpec(String eventName, String[] paramTypes, String[] paramNames) {
            this.eventName = eventName;
            this.paramTypes = paramTypes;
            this.paramNames = paramNames;
        }
    }

    /**
     * @return the code without comments, keeping string and character literals untouched
     */
    static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
                out.append('\n');
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                i = Math.min(i + 2, source.length());
                out.append(' ');
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(c);
                i++;
                while (i < source.length()) {
                    char literalChar = source.charAt(i);
                    out.append(literalChar);
                    if (literalChar == '\\' && i + 1 < source.length()) {
                        out.append(source.charAt(++i));
                    } else if (literalChar == quote) {
                        break;
                    }
                    i++;
                }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * @return everything inside the class' body braces, or the whole input if it isn't a class
     */
    static String extractClassBody(String code) {
        Matcher matcher = Pattern.compile("\\b(?:class|interface|enum)\\s+\\w+").matcher(code);
        if (!matcher.find()) {
            return code;
        }

        int open = code.indexOf('{', matcher.end());
        if (open < 0) {
            return code;
        }
        int close = findMatchingBrace(code, open);
        if (close < 0) {
            throw new IllegalArgumentException("unbalanced braces");
        }
        return code.substring(open + 1, close);
    }

    static int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString || inChar) {
                if (c == '\\') {
                    i++;
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * @return the class' members (fields, methods, initializer blocks, …) in source order
     */
    static ArrayList<String> splitMembers(String body) {
        ArrayList<String> members = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            current.append(c);
            if (inString || inChar) {
                if (c == '\\') {
                    if (i + 1 < body.length()) {
                        current.append(body.charAt(++i));
                    }
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '{' || c == '(' || c == '[') {
                depth++;
            } else if (c == '}' || c == ')' || c == ']') {
                depth--;
            }

            if (depth <= 0 && (c == ';' || c == '}')) {
                addMember(members, current.toString());
                current.setLength(0);
                depth = 0;
            }
        }
        addMember(members, current.toString());
        return members;
    }

    private static void addMember(List<String> members, String member) {
        String trimmed = member.trim();
        if (!trimmed.isEmpty() && !trimmed.equals(";")) {
            members.add(trimmed);
        }
    }

    static MethodDecl parseMethod(String member) {
        if (!member.endsWith("}")) {
            return null;
        }
        int open = member.indexOf('{');
        if (open < 0) {
            return null;
        }
        int close = findMatchingBrace(member, open);
        if (close <= open) {
            return null;
        }

        String signature = ANNOTATION.matcher(member.substring(0, open)).replaceAll(" ").trim();
        int parenthesis = signature.indexOf('(');
        if (parenthesis < 0) {
            return null;
        }
        int endParenthesis = findMatchingParenthesis(signature, parenthesis);
        if (endParenthesis < 0) {
            return null;
        }

        String head = signature.substring(0, parenthesis).replaceAll("\\s+", " ").trim();
        String[] words = head.split(" ");
        if (words.length < 2) {
            return null;
        }

        MethodDecl method = new MethodDecl();
        method.raw = member;
        method.name = words[words.length - 1];
        StringBuilder returnType = new StringBuilder();
        for (int i = words.length - 2; i >= 0; i--) {
            if (MODIFIERS.contains(words[i])) {
                break;
            }
            if (returnType.length() > 0) {
                returnType.insert(0, " ");
            }
            returnType.insert(0, words[i]);
        }
        method.returnType = returnType.toString().trim();
        if (method.returnType.isEmpty()) {
            method.constructor = true;
        }

        String parameters = signature.substring(parenthesis + 1, endParenthesis).trim();
        if (!parameters.isEmpty()) {
            for (String parameter : splitTopLevel(parameters, ',')) {
                ParamDecl declaration = parseParameter(parameter);
                if (declaration == null) {
                    return null;
                }
                method.params.add(declaration);
            }
        }

        method.body = member.substring(open + 1, close);
        return method;
    }

    private static ParamDecl parseParameter(String parameter) {
        String declaration = ANNOTATION.matcher(parameter).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (declaration.isEmpty() || declaration.contains("...")) {
            return null;
        }
        for (String modifier : MODIFIERS) {
            declaration = declaration.replaceAll("\\b" + modifier + "\\b", " ").replaceAll("\\s+", " ").trim();
        }

        int lastSpace = declaration.lastIndexOf(' ');
        if (lastSpace < 0) {
            return null;
        }
        String name = declaration.substring(lastSpace + 1).trim();
        String type = declaration.substring(0, lastSpace).trim();
        if (!IDENTIFIER.matcher(name).matches() || type.isEmpty()) {
            return null;
        }
        return new ParamDecl(type, name, name);
    }

    static FieldDecl parseField(String member) {
        if (!member.endsWith(";")) {
            return null;
        }
        String declaration = ANNOTATION.matcher(member.substring(0, member.length() - 1)).replaceAll(" ").trim();

        FieldDecl field = new FieldDecl();
        field.isFinal = Pattern.compile("\\bfinal\\b").matcher(declaration).find();

        int equals = declaration.indexOf('=');
        String left = equals < 0 ? declaration : declaration.substring(0, equals);
        if (left.contains("(") || left.contains(")")) {
            // the declarator is a method call, e.g. an anonymous class
            return null;
        }
        if (equals >= 0) {
            field.initializer = declaration.substring(equals + 1).trim();
        }
        if (splitTopLevel(left, ',').size() > 1) {
            field.hasMultipleDeclarators = true;
            return field;
        }

        String[] words = left.replaceAll("\\s+", " ").trim().split(" ");
        if (words.length < 2) {
            return null;
        }
        field.name = words[words.length - 1];
        StringBuilder type = new StringBuilder();
        for (int i = words.length - 2; i >= 0; i--) {
            if (MODIFIERS.contains(words[i])) {
                break;
            }
            if (type.length() > 0) {
                type.insert(0, " ");
            }
            type.insert(0, words[i]);
        }
        field.type = type.toString().trim();
        if (!IDENTIFIER.matcher(field.name).matches() || field.type.isEmpty()) {
            return null;
        }
        return field;
    }

    /**
     * @return the method body's statements; {@code else}/{@code catch}/{@code finally} branches stay attached
     * to the statement they belong to
     */
    static ArrayList<String> splitStatements(String body) {
        ArrayList<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            current.append(c);
            if (inString || inChar) {
                if (c == '\\') {
                    if (i + 1 < body.length()) {
                        current.append(body.charAt(++i));
                    }
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '{' || c == '(' || c == '[') {
                depth++;
            } else if (c == '}' || c == ')' || c == ']') {
                depth--;
            }

            if (depth <= 0 && (c == ';' || c == '}')) {
                addStatement(statements, current.toString());
                current.setLength(0);
                depth = 0;
            }
        }
        addStatement(statements, current.toString());

        for (int i = statements.size() - 1; i > 0; i--) {
            String statement = statements.get(i).trim();
            if (statement.startsWith("else") || statement.startsWith("catch") || statement.startsWith("finally")) {
                statements.set(i - 1, statements.get(i - 1) + " " + statement);
                statements.remove(i);
            }
        }
        return statements;
    }

    private static void addStatement(List<String> statements, String statement) {
        String trimmed = statement.trim();
        if (trimmed.isEmpty() || trimmed.equals(";")) {
            return;
        }
        statements.add(normalizeStatement(trimmed));
    }

    /**
     * The Logic Editor keeps block arguments in single-line text fields, so statements are stored with
     * their line breaks turned into spaces. Text blocks (which need their line breaks) are left untouched.
     */
    private static String normalizeStatement(String statement) {
        if (statement.contains("\"\"\"")) {
            return statement;
        }
        return statement.replaceAll("\\s+", " ");
    }

    static int findMatchingParenthesis(String text, int openIndex) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString || inChar) {
                if (c == '\\') {
                    i++;
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static ArrayList<String> splitTopLevel(String text, char separator) {
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString || inChar) {
                current.append(c);
                if (c == '\\') {
                    if (i + 1 < text.length()) {
                        current.append(text.charAt(++i));
                    }
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                current.append(c);
            } else if (c == '\'') {
                inChar = true;
                current.append(c);
            } else if (c == '{' || c == '(' || c == '[' || c == '<') {
                depth++;
                current.append(c);
            } else if (c == '}' || c == ')' || c == ']' || c == '>') {
                depth--;
                current.append(c);
            } else if (c == separator && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().trim().isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    // ---------------------------------------------------------------------------------------------
    // Types
    // ---------------------------------------------------------------------------------------------

    /**
     * @return Sketchware's variable type (0 boolean, 1 number, 2 String, 3 Map), or -1 if there is none
     */
    static int variableTypeOf(String type) {
        return switch (type.replace(" ", "")) {
            case "boolean", "Boolean" -> 0;
            case "int", "double", "float", "long", "short", "byte", "Integer", "Double", "Float", "Long",
                 "Short", "Byte", "Number" -> 1;
            case "String", "CharSequence" -> 2;
            case "HashMap", "Map", "LinkedHashMap", "HashMap<String,Object>", "Map<String,Object>",
                 "LinkedHashMap<String,Object>" -> 3;
            default -> -1;
        };
    }

    /**
     * @return Sketchware's list type (1 List Number, 2 List String, 3 List Map), or -1 if there is none
     */
    static int listTypeOf(String type) {
        return switch (type.replace(" ", "")) {
            case "ArrayList<Integer>", "ArrayList<Double>", "ArrayList<Float>", "ArrayList<Long>",
                 "ArrayList<Number>", "List<Integer>", "List<Double>" -> 1;
            case "ArrayList<String>", "List<String>", "LinkedList<String>" -> 2;
            case "ArrayList<HashMap<String,Object>>", "List<HashMap<String,Object>>",
                 "ArrayList<Map<String,Object>>" -> 3;
            default -> -1;
        };
    }

    /**
     * @return the More Block's return type, or null if Sketchware can't express it
     */
    static String moreBlockTypeOf(String returnType) {
        return switch (returnType.replace(" ", "")) {
            case "void" -> " ";
            case "boolean", "Boolean" -> "b";
            case "int", "double", "float", "long", "short", "byte", "Integer", "Double", "Float", "Long" -> "d";
            case "String", "CharSequence" -> "s";
            case "HashMap", "Map", "HashMap<String,Object>", "Map<String,Object>" -> "a|Map";
            case "ArrayList<String>", "List<String>" -> "l|List String";
            case "ArrayList<HashMap<String,Object>>", "List<HashMap<String,Object>>" -> "l|List Map";
            default -> null;
        };
    }

    /**
     * Sketchware declares Maps and Lists with {@code = new HashMap<>()} / {@code = new ArrayList<>()},
     * so an initializer like that doesn't need to be added to {@code initializeLogic} again.
     */
    private static boolean isDefaultInitializer(int variableType, String initializer) {
        return switch (variableType) {
            case 0 -> initializer.equals("false");
            case 1 -> initializer.equals("0") || initializer.equals("0.0");
            case 2 -> initializer.equals("\"\"");
            default -> false;
        };
    }

    private static boolean isCollectionInitializer(String initializer) {
        return initializer.matches("new\\s+(ArrayList|HashMap|LinkedHashMap|List|Map)\\s*(<[^>]*>)?\\s*\\(.*");
    }

    private static boolean isGeneratedBySketchware(String statement) {
        String trimmed = statement.trim();
        return trimmed.startsWith("super.") || trimmed.matches("setContentView\\s*\\(.*");
    }

    private static boolean isValidName(String name) {
        return IDENTIFIER.matcher(name).matches();
    }

    private static boolean isReassigned(String body, String name) {
        String withoutStrings = STRING_OR_CHAR.matcher(body).replaceAll("\"\"");
        return Pattern.compile("(?<![\\w$.])" + Pattern.quote(name) + "\\s*(=(?!=)|\\+\\+|--|\\+=|-=|\\*=|/=|%=)")
                .matcher(withoutStrings).find();
    }

    private static EventSpec matchEvent(MethodDecl method) {
        EventSpec spec = EVENTS.get(method.name);
        if (spec == null || method.params.size() != spec.paramTypes.length) {
            return null;
        }
        for (int i = 0; i < spec.paramTypes.length; i++) {
            String expected = spec.paramTypes[i];
            String actual = method.params.get(i).type;
            int lastDot = actual.lastIndexOf('.');
            if (lastDot >= 0) {
                actual = actual.substring(lastDot + 1);
            }
            if (!expected.equals(actual)) {
                return null;
            }
        }
        return spec;
    }

    /**
     * Renames the code's parameters to the names Sketchware generates for the event / More Block.
     */
    private static String renameParameters(String statement, ArrayList<ParamDecl> params, String[] paramNames) {
        String renamed = statement;
        for (int i = 0; i < params.size(); i++) {
            String target;
            if (paramNames == null) {
                target = "_" + params.get(i).name;
            } else if (i < paramNames.length) {
                target = paramNames[i];
            } else {
                continue;
            }
            renamed = renameIdentifierOutsideStrings(renamed, params.get(i).name, target);
        }
        return renamed;
    }

    /**
     * Rewrites calls to methods that became More Blocks, so that they call the generated {@code _name(…)} method.
     */
    private static String rewriteCalls(String statement, Set<String> moreBlockMethods) {
        String rewritten = statement;
        for (String method : moreBlockMethods) {
            rewritten = replaceOutsideStrings(rewritten,
                    Pattern.compile("(?<![\\w$.])" + Pattern.quote(method) + "(?=\\s*\\()"),
                    "_" + method);
        }
        return rewritten;
    }

    private static String renameIdentifierOutsideStrings(String text, String from, String to) {
        return replaceOutsideStrings(text,
                Pattern.compile("(?<![\\w$.])" + Pattern.quote(from) + "(?![\\w$])"), to);
    }

    private static String replaceOutsideStrings(String text, Pattern pattern, String replacement) {
        StringBuilder result = new StringBuilder(text.length());
        Matcher literals = STRING_OR_CHAR.matcher(text);
        int lastEnd = 0;
        while (literals.find()) {
            result.append(pattern.matcher(text.substring(lastEnd, literals.start())).replaceAll(replacement));
            result.append(literals.group());
            lastEnd = literals.end();
        }
        result.append(pattern.matcher(text.substring(lastEnd)).replaceAll(replacement));
        return result.toString();
    }
}
