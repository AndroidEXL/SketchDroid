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

    // Sketchware's own block colours (see a.a.a.kq)
    private static final int COLOR_VAR = 0xffee7d16;
    private static final int COLOR_LIST = 0xffcc5b22;
    private static final int COLOR_CONTROL = 0xffe1a92a;
    private static final int COLOR_OPERATOR = 0xff5cb722;
    private static final int COLOR_MATH = 0xff23b9a9;
    private static final int COLOR_COMPONENT = 0xff2ca5e2;
    private static final int COLOR_VIEW = 0xff4a6cd4;

    // Placeholder types used while mapping code, see mapExpression
    private static final int TYPE_BOOLEAN = 0;
    private static final int TYPE_NUMBER = 1;
    private static final int TYPE_STRING = 2;
    private static final int TYPE_ANY = 3;

    /**
     * Definitions of the built-in blocks this importer can generate. The {@code spec} of every entry is
     * Sketchware's own specification (taken from the app's {@code block_strings} resources), because the spec
     * decides which widgets the Logic Editor shows for a block's arguments, and {@code a.a.a.Fx} uses the
     * argument order that goes with it.
     */
    private static final class BlockDef {
        final String spec;
        final String type;
        final String typeName;
        final int color;

        BlockDef(String spec, String type, String typeName, int color) {
            this.spec = spec;
            this.type = type;
            this.typeName = typeName;
            this.color = color;
        }
    }

    private static final Map<String, BlockDef> BLOCKS = new LinkedHashMap<>();

    private static void define(String opCode, String spec, String type, int color) {
        define(opCode, spec, type, "", color);
    }

    private static void define(String opCode, String spec, String type, String typeName, int color) {
        BLOCKS.put(opCode, new BlockDef(spec, type, typeName, color));
    }

    static {
        define("addSourceDirectly", SPEC_ADD_SOURCE, " ", COLOR_RAW);
        define("addCustomVariable", SPEC_CUSTOM_VARIABLE, " ", COLOR_CUSTOM);

        define("if", "if %b then", "c", COLOR_CONTROL);
        define("ifElse", "if %b then", "e", COLOR_CONTROL);
        define("repeat", "repeat %d", "c", COLOR_CONTROL);
        define("forever", "forever", "c", COLOR_CONTROL);
        define("break", "stop", "f", COLOR_CONTROL);

        define("setVarInt", "set %m.varInt to %d", " ", COLOR_VAR);
        define("setVarBoolean", "set %m.varBool to %b", " ", COLOR_VAR);
        define("setVarString", "set %m.varStr to %s", " ", COLOR_VAR);
        define("increaseInt", "%m.varInt increase 1", " ", COLOR_VAR);
        define("decreaseInt", "%m.varInt decrease 1", " ", COLOR_VAR);
        define("mapCreateNew", "%m.varMap create new map", " ", COLOR_VAR);
        define("mapPut", "%m.varMap put key %s value %s", " ", COLOR_VAR);
        define("mapClear", "%m.varMap clear", " ", COLOR_VAR);
        define("mapRemoveKey", "%m.varMap remove key %s", " ", COLOR_VAR);
        define("mapGet", "%m.varMap get key %s", "s", COLOR_VAR);
        define("mapContainKey", "%m.varMap contain key %s", "b", COLOR_VAR);
        define("mapSize", "%m.varMap size", "d", COLOR_VAR);
        define("mapIsEmpty", "%m.varMap is empty", "b", COLOR_VAR);

        define("addListInt", "add %d to %m.listInt", " ", COLOR_LIST);
        define("addListStr", "add %s to %m.listStr", " ", COLOR_LIST);
        define("addMapToList", "add %m.varMap to %m.listMap", " ", COLOR_LIST);
        define("insertListInt", "insert %d at %d to %m.listInt", " ", COLOR_LIST);
        define("insertListStr", "insert %s at %d to %m.listStr", " ", COLOR_LIST);
        define("deleteList", "delete at %d of %m.list", " ", COLOR_LIST);
        define("clearList", "clear %m.list", " ", COLOR_LIST);
        define("lengthList", "length of %m.list", "d", COLOR_LIST);
        define("containListStr", "%m.listStr contains %s", "b", COLOR_LIST);
        define("containListInt", "%m.listInt contains %d", "b", COLOR_LIST);
        define("getAtListStr", "get at %d of %m.listStr", "s", COLOR_LIST);
        define("getAtListInt", "get at %d of %m.listInt", "d", COLOR_LIST);
        define("indexListStr", "index %s in %m.listStr", "d", COLOR_LIST);

        define("doToast", "Toast %s", " ", COLOR_COMPONENT);
        define("finishActivity", "Finish Activity", " ", COLOR_COMPONENT);

        define("true", "true", "b", COLOR_OPERATOR);
        define("false", "false", "b", COLOR_OPERATOR);
        define("+", "%d + %d", "d", COLOR_OPERATOR);
        define("-", "%d - %d", "d", COLOR_OPERATOR);
        define("*", "%d * %d", "d", COLOR_OPERATOR);
        define("/", "%d / %d", "d", COLOR_OPERATOR);
        define("%", "%d % %d", "d", COLOR_OPERATOR);
        define(">", "%d > %d", "b", COLOR_OPERATOR);
        define("<", "%d < %d", "b", COLOR_OPERATOR);
        define("=", "%d = %d", "b", COLOR_OPERATOR);
        define("&&", "%b and %b", "b", COLOR_OPERATOR);
        define("||", "%b or %b", "b", COLOR_OPERATOR);
        define("not", "not %b", "b", COLOR_OPERATOR);
        define("stringJoin", "join %s and %s", "s", COLOR_OPERATOR);
        define("stringLength", "length of %s", "d", COLOR_OPERATOR);
        define("stringEquals", "%s equals %s", "b", COLOR_OPERATOR);
        define("stringContains", "%s contains %s", "b", COLOR_OPERATOR);
        define("stringIndex", "index %s of %s", "d", COLOR_OPERATOR);
        define("stringSub", "%s substring %d to %d", "s", COLOR_OPERATOR);
        define("stringReplace", "%s replace all %s with %s", "s", COLOR_OPERATOR);
        define("stringReplaceAll", "%s replace all RegEx %s with %s", "s", COLOR_OPERATOR);
        define("stringReplaceFirst", "%s replace first RegEx %s with %s", "s", COLOR_OPERATOR);
        define("trim", "trim %s", "s", COLOR_OPERATOR);
        define("toUpperCase", "toUpperCase %s", "s", COLOR_OPERATOR);
        define("toLowerCase", "toLowerCase %s", "s", COLOR_OPERATOR);
        define("toNumber", "toNumber %s", "d", COLOR_OPERATOR);
        define("toString", "toString %d without decimal", "s", COLOR_OPERATOR);
        define("toStringWithDecimal", "toString %d with decimal", "s", COLOR_OPERATOR);
        define("random", "pick random %d to %d", "d", COLOR_OPERATOR);

        define("mathAbs", "absolute value of %d", "d", COLOR_MATH);
        define("mathCeil", "ceil %d", "d", COLOR_MATH);
        define("mathFloor", "floor %d", "d", COLOR_MATH);
        define("mathRound", "round %d", "d", COLOR_MATH);
        define("mathSqrt", "square root of %d", "d", COLOR_MATH);
        define("mathPow", "%d to the %d power", "d", COLOR_MATH);
        define("mathMax", "maximum of %d and %d", "d", COLOR_MATH);
        define("mathMin", "minimum of %d and %d", "d", COLOR_MATH);
        define("mathSin", "sin %d", "d", COLOR_MATH);
        define("mathCos", "cos %d", "d", COLOR_MATH);
        define("mathTan", "tan %d", "d", COLOR_MATH);
        define("mathAsin", "arcsin %d", "d", COLOR_MATH);
        define("mathAcos", "arccos %d", "d", COLOR_MATH);
        define("mathAtan", "arctan %d", "d", COLOR_MATH);
        define("mathExp", "exp %d", "d", COLOR_MATH);
        define("mathLog", "ln %d", "d", COLOR_MATH);
        define("mathLog10", "log %d", "d", COLOR_MATH);
        define("mathToRadian", "Degree %d to Radian", "d", COLOR_MATH);
        define("mathToDegree", "Radian %d to Degree", "d", COLOR_MATH);
        define("mathPi", "PI", "d", COLOR_MATH);
        define("mathE", "E", "d", COLOR_MATH);
    }

    /** A block that is about to be written: arguments may be plain text or other (nested) blocks. */
    private static final class Node {
        final String opCode;
        final String spec;
        final String type;
        final String typeName;
        final int color;
        final ArrayList<Object> parameters = new ArrayList<>();
        /** The statement that follows this one in the same stack. */
        Node next;
        /** Statements inside this block ({@code subStack1}). */
        Node body;
        /** Statements of the {@code else} branch ({@code subStack2}). */
        Node body2;

        Node(String opCode, String spec, String type, String typeName, int color, Object... parameters) {
            this.opCode = opCode;
            this.spec = spec;
            this.type = type;
            this.typeName = typeName;
            this.color = color;
            for (Object parameter : parameters) {
                this.parameters.add(parameter);
            }
        }
    }

    private static Node block(String opCode, Object... parameters) {
        BlockDef definition = BLOCKS.get(opCode);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown block: " + opCode);
        }
        return new Node(opCode, definition.spec, definition.type, definition.typeName, definition.color, parameters);
    }

    private static Node raw(String code) {
        return block("addSourceDirectly", code);
    }

    private static Node getVariable(String name, int variableType) {
        String type = switch (variableType) {
            case 0 -> "b";
            case 2 -> "s";
            case 3 -> "m";
            default -> "d";
        };
        return new Node("getVar", name, type, "", COLOR_VAR);
    }

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
        BlockMapper mapper = createBlockMapper(renamedMethods, moreBlocks);
        int nextBlockId = 1;

        for (MoreBlockDef moreBlock : moreBlocks.values()) {
            mapper.clearArguments();
            for (int i = 0; i < moreBlock.parameterNames.length; i++) {
                mapper.addArgument("_" + moreBlock.parameterNames[i], moreBlock.parameterTypes[i]);
            }
            BlockBuilder builder = new BlockBuilder(nextBlockId);
            for (String statement : moreBlock.statements) {
                builder.addStatement(statement, mapper);
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
            mapper.clearArguments();
            registerEventArguments(mapper, eventName);

            BlockBuilder builder = new BlockBuilder(nextBlockId);
            ArrayList<String> statements = events.get(eventName);
            if (statements != null) {
                for (String statement : statements) {
                    builder.addStatement(statement, mapper);
                }
            }
            if (eventName.equals("initializeLogic")) {
                for (String statement : initializeStatements) {
                    builder.addStatement(statement, mapper);
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

        if (mapper.rawStatements > 0) {
            result.warnings.add(mapper.rawStatements
                    + " statement(s) had no matching block and were kept as add source directly");
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
        /** Last block of the top level chain, so that a following statement can be attached to it. */
        private BlockBean lastTopLevelBlock;
        /** Last written block of any chain (used to continue the top level chain). */
        private BlockBean lastWrittenBlock;

        BlockBuilder(int firstId) {
            nextId = firstId;
        }

        int getNextId() {
            return nextId;
        }

        void addRaw(String code) {
            addTree(raw(code));
        }

        /** Writes {@code statement} as real blocks if possible, and as plain code otherwise. */
        void addStatement(String statement, BlockMapper mapper) {
            Node node = mapper.mapStatement(statement, false);
            if (node != null) {
                addTree(node);
                return;
            }
            addRaw(rewriteCalls(statement, mapper.getGeneratedMethods()));
            mapper.rawStatements++;
        }

        void addCustomMember(String code) {
            addTree(block("addCustomVariable", code));
        }

        /** Serializes a block (including everything nested inside it) into the project's block list. */
        void addTree(Node head) {
            int firstId = writeChain(head);
            if (lastTopLevelBlock != null) {
                lastTopLevelBlock.nextBlock = firstId;
            }
            lastTopLevelBlock = lastWrittenBlock;
        }

        /**
         * Writes a stack of blocks. Arguments and sub stacks are written immediately after their parent, so
         * the first written block always stays the first element - which is what the Logic Editor uses as the
         * root block of the logic.
         *
         * @return the id of the first written block
         */
        private int writeChain(Node head) {
            int firstId = -1;
            BlockBean previous = null;
            for (Node node = head; node != null; node = node.next) {
                BlockBean bean = createBean(node);
                blocks.add(bean);
                if (previous == null) {
                    firstId = Integer.parseInt(bean.id);
                } else {
                    previous.nextBlock = Integer.parseInt(bean.id);
                }
                previous = bean;

                if (node.body != null) {
                    bean.subStack1 = writeChain(node.body);
                }
                if (node.body2 != null) {
                    bean.subStack2 = writeChain(node.body2);
                }
                for (int i = 0; i < node.parameters.size(); i++) {
                    Object parameter = node.parameters.get(i);
                    if (parameter instanceof Node value) {
                        bean.parameters.set(i, "@" + writeChain(value));
                    } else if (parameter != null) {
                        bean.parameters.set(i, String.valueOf(parameter));
                    }
                }
            }
            lastWrittenBlock = previous;
            return firstId;
        }

        private BlockBean createBean(Node node) {
            BlockBean bean = new BlockBean();
            bean.id = String.valueOf(nextId++);
            bean.opCode = node.opCode;
            bean.spec = node.spec;
            bean.type = node.type;
            bean.typeName = node.typeName;
            bean.color = node.color;
            bean.nextBlock = -1;
            bean.subStack1 = -1;
            bean.subStack2 = -1;
            bean.parameters = new ArrayList<>(node.parameters.size());
            for (int i = 0; i < node.parameters.size(); i++) {
                bean.parameters.add("");
            }
            return bean;
        }

        ArrayList<BlockBean> build() {
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
        int[] parameterTypes = new int[0];
        String[] parameterNames = new String[0];
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
        int[] parameterTypes = new int[method.params.size()];
        String[] parameterNames = new String[method.params.size()];
        for (int i = 0; i < method.params.size(); i++) {
            ParamDecl parameter = method.params.get(i);
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
            parameterNames[i] = parameter.name;
            parameterTypes[i] = switch (parameterType) {
                case "b" -> TYPE_BOOLEAN;
                case "s" -> TYPE_STRING;
                default -> TYPE_NUMBER;
            };
            if (isReassigned(method.body, parameter.name)) {
                // Sketchware's More Block parameters are final, so reassigning them wouldn't compile
                return null;
            }
        }

        MoreBlockDef definition = new MoreBlockDef();
        definition.moreBlockName = method.name;
        definition.parameterTypes = parameterTypes;
        definition.parameterNames = parameterNames;
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
    // Turning code into real blocks
    // ---------------------------------------------------------------------------------------------

    /** An existing or freshly created More Block that calls can be turned into blocks with. */
    private static final class MoreBlockCall {
        final String name;
        final String spec;
        final String type;
        final String typeName;
        final int[] parameterTypes;
        final int color = 0xff8a55d7;

        MoreBlockCall(String name, String spec, String type, String typeName, int[] parameterTypes) {
            this.name = name;
            this.spec = spec;
            this.type = type;
            this.typeName = typeName;
            this.parameterTypes = parameterTypes;
        }
    }

    /** A method call that was read out of a statement or an expression. */
    private static final class Call {
        /** The expression the method is called on, e.g. {@code list} of {@code list.add(x)}. */
        final String receiver;
        final String name;
        final String arguments;

        Call(String receiver, String name, String arguments) {
            this.receiver = receiver;
            this.name = name;
            this.arguments = arguments;
        }
    }

    /**
     * Maps Java statements and expressions onto the built-in blocks of Sketchware. Anything that has no
     * matching block is reported as unmapped, so the caller can keep it verbatim in an
     * {@code add source directly} block.
     */
    private final class BlockMapper {
        private final Map<String, Integer> variableTypes = new LinkedHashMap<>();
        private final Map<String, Integer> listTypes = new LinkedHashMap<>();
        private final Map<String, MoreBlockCall> moreBlocks = new LinkedHashMap<>();
        private final Map<String, Integer> arguments = new LinkedHashMap<>();
        private final Set<String> generatedMethods = new HashSet<>();

        private int mappedStatements;
        private int rawStatements;

        BlockMapper(Set<String> rewrittenMethods) {
            generatedMethods.addAll(rewrittenMethods);
        }

        /** Registers a variable of the page so that reads/writes of it become Blocks. */
        void addVariable(String name, int type) {
            variableTypes.put(name, type);
        }

        void addList(String name, int type) {
            listTypes.put(name, type);
        }

        Set<String> getGeneratedMethods() {
            return generatedMethods;
        }

        void clearArguments() {
            arguments.clear();
        }

        /**
         * Registers an argument of the event/More Block being converted. Only Sketchware's own generated
         * parameters (they start with an underscore) can be read with a {@code getArg} block, because that
         * block emits {@code "_" + spec} as the code of the argument.
         */
        void addArgument(String name, int type) {
            if (!name.startsWith("_")) {
                return;
            }
            arguments.put(name, type);
        }

        void addMoreBlock(MoreBlockCall moreBlock) {
            moreBlocks.put(moreBlock.name, moreBlock);
        }

        // -----------------------------------------------------------------------------------------
        // Statements
        // -----------------------------------------------------------------------------------------

        /**
         * @return the block for {@code statement}, or {@code null} if there is no block for it
         */
        Node mapStatement(String statement, boolean insideLoop) {
            String text = statement.trim();
            while (text.endsWith(";")) {
                text = text.substring(0, text.length() - 1).trim();
            }
            if (text.isEmpty()) {
                return null;
            }

            Node result = mapIf(text, insideLoop);
            if (result == null) {
                result = mapLoop(text, insideLoop);
            }
            if (result == null && insideLoop && (text.equals("break") || text.startsWith("break "))) {
                result = block("break");
            }
            if (result == null) {
                result = mapSimpleStatement(text);
            }
            if (result == null) {
                return null;
            }
            mappedStatements++;
            return result;
        }

        /**
         * Converts the statements of a block's body into a chain of blocks.
         *
         * @return the first block of the chain, or {@code null} if a statement couldn't be represented
         */
        private Node mapBody(String body, boolean insideLoop) {
            Node first = null;
            Node last = null;
            for (String statement : splitStatements(body)) {
                Node node = mapStatement(statement, insideLoop);
                if (node == null) {
                    node = raw(rewriteCalls(normalizeStatement(statement), generatedMethods));
                    rawStatements++;
                }
                if (first == null) {
                    first = node;
                } else {
                    last.next = node;
                }
                last = node;
            }
            return first;
        }

        private Node mapIf(String text, boolean insideLoop) {
            if (!startsWithWord(text, "if")) {
                return null;
            }
            int open = text.indexOf('(', 2);
            if (open < 0) {
                return null;
            }
            int close = findMatchingParenthesis(text, open);
            if (close < 0) {
                return null;
            }
            String rest = text.substring(close + 1).trim();
            if (!rest.startsWith("{")) {
                return null;
            }
            int bodyEnd = findMatchingBrace(rest, 0);
            if (bodyEnd < 0) {
                return null;
            }

            Object condition = value(text.substring(open + 1, close), TYPE_BOOLEAN);
            if (condition == null) {
                return null;
            }
            Node ifBody = mapBody(rest.substring(1, bodyEnd), insideLoop);

            String after = rest.substring(bodyEnd + 1).trim();
            Node elseBody = null;
            if (after.equals("else")) {
                return null;
            } else if (after.startsWith("else")) {
                String elseText = after.substring(4).trim();
                if (elseText.startsWith("if")) {
                    elseBody = mapIf(elseText, insideLoop);
                    if (elseBody == null) {
                        return null;
                    }
                } else if (elseText.startsWith("{")) {
                    int elseEnd = findMatchingBrace(elseText, 0);
                    if (elseEnd < 0) {
                        return null;
                    }
                    elseBody = mapBody(elseText.substring(1, elseEnd), insideLoop);
                } else {
                    return null;
                }
            } else if (!after.isEmpty()) {
                return null;
            }

            if (elseBody == null) {
                Node node = block("if", condition);
                node.body = ifBody;
                return node;
            }
            Node node = block("ifElse", condition);
            node.body = ifBody;
            node.body2 = elseBody;
            return node;
        }

        private Node mapLoop(String text, boolean insideLoop) {
            if (startsWithWord(text, "while")) {
                int open = text.indexOf('(', 5);
                if (open < 0) {
                    return null;
                }
                int close = findMatchingParenthesis(text, open);
                if (close < 0) {
                    return null;
                }
                if (!text.substring(open + 1, close).trim().equals("true")) {
                    return null;
                }
                String rest = text.substring(close + 1).trim();
                if (!rest.startsWith("{")) {
                    return null;
                }
                int bodyEnd = findMatchingBrace(rest, 0);
                if (bodyEnd < 0) {
                    return null;
                }
                Node node = block("forever");
                node.body = mapBody(rest.substring(1, bodyEnd), true);
                return node;
            }

            if (startsWithWord(text, "for")) {
                int open = text.indexOf('(', 3);
                if (open < 0) {
                    return null;
                }
                int close = findMatchingParenthesis(text, open);
                if (close < 0) {
                    return null;
                }
                String rest = text.substring(close + 1).trim();
                if (!rest.startsWith("{")) {
                    return null;
                }
                int bodyEnd = findMatchingBrace(rest, 0);
                if (bodyEnd < 0) {
                    return null;
                }

                ArrayList<String> parts = splitTopLevel(text.substring(open + 1, close), ';');
                if (parts.size() != 3) {
                    return null;
                }
                // only "for (int i = 0; i < limit; i++)" with an unused counter can be a "repeat" block
                Matcher declaration = Pattern.compile("^\\s*(?:int|double|long|float|short|byte)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*0\\s*$")
                        .matcher(parts.get(0));
                if (!declaration.matches()) {
                    return null;
                }
                String counter = declaration.group(1);
                Matcher condition = Pattern.compile("^\\s*" + Pattern.quote(counter) + "\\s*<\\s*(.+?)\\s*$")
                        .matcher(parts.get(1));
                if (!condition.matches()) {
                    return null;
                }
                String update = parts.get(2).trim().replace(" ", "");
                if (!update.equals(counter + "++") && !update.equals(counter + "+=1")) {
                    return null;
                }
                String body = rest.substring(1, bodyEnd);
                if (containsIdentifier(body, counter)) {
                    return null;
                }
                Object limit = value(condition.group(1), TYPE_NUMBER);
                if (limit == null) {
                    return null;
                }
                Node node = block("repeat", limit);
                node.body = mapBody(body, true);
                return node;
            }
            return null;
        }


        private Node mapSimpleStatement(String text) {
            if (text.equals("finish()")) {
                return block("finishActivity");
            }

            Node toast = mapToast(text);
            if (toast != null) {
                return toast;
            }

            // counter++ / ++counter / counter-- / --counter
            Matcher update = Pattern.compile("^(\\+\\+|--)?\\s*([A-Za-z_$][\\w$]*)\\s*(\\+\\+|--)?$").matcher(text);
            if (update.matches()) {
                String signs = (update.group(1) == null ? "" : update.group(1)) + (update.group(3) == null ? "" : update.group(3));
                String name = update.group(2);
                Integer type = variableTypes.get(name);
                if (type != null && type == TYPE_NUMBER && !signs.isEmpty()) {
                    return block(signs.charAt(0) == '+' ? "increaseInt" : "decreaseInt", name);
                }
                return null;
            }

            // name.add(...), list.remove(...), map.put(...), ...
            Call call = parseCall(text);
            if (call != null) {
                Node node = mapReceiverCall(call);
                if (node != null) {
                    return node;
                }
                if (call.receiver.isEmpty()) {
                    // a call of a void More Block, e.g. "resetCounter();"
                    MoreBlockCall moreBlock = moreBlockFor(call.name);
                    if (moreBlock != null && moreBlock.type.equals(" ")) {
                        node = mapMoreBlock(moreBlock, splitTopLevel(call.arguments, ','));
                        if (node != null) {
                            return node;
                        }
                    }
                }
            }

            int assignment = indexOfAssignment(text);
            if (assignment <= 0) {
                return null;
            }
            String target = text.substring(0, assignment).trim();
            String operator = "";
            char beforeAssignment = text.charAt(assignment - 1);
            if (beforeAssignment == '+' || beforeAssignment == '-' || beforeAssignment == '*'
                    || beforeAssignment == '/' || beforeAssignment == '%') {
                operator = String.valueOf(beforeAssignment);
                target = text.substring(0, assignment - 1).trim();
            }
            return mapAssignment(target, operator, text.substring(assignment + 1).trim());
        }

        /** {@code Toast.makeText(…).show()} and Sketchware's own {@code showMessage(…)}. */
        private Node mapToast(String text) {
            Call call = parseCall(text);
            if (call == null) {
                return null;
            }
            Call target = call;
            if (call.name.equals("show") && call.arguments.isEmpty()) {
                // Toast.makeText(context, "message", Toast.LENGTH_SHORT).show()
                Call inner = parseCall(call.receiver);
                if (inner == null) {
                    return null;
                }
                target = inner;
            }
            boolean makeText = target.name.equals("makeText") && target.receiver.equals("Toast");
            boolean showMessage = target.name.equals("showMessage") && target.receiver.equals("SketchwareUtil");
            if (!makeText && !showMessage) {
                return null;
            }
            ArrayList<String> args = splitTopLevel(target.arguments, ',');
            int messageIndex = makeText ? 1 : (args.size() == 2 ? 1 : 0);
            if (args.size() <= messageIndex) {
                return null;
            }
            Object message = stringValue(args.get(messageIndex));
            return message == null ? null : block("doToast", message);
        }

        /** Turns {@code receiver.add(…)} and friends into the matching list/map blocks. */
        private Node mapReceiverCall(Call call) {
            ArrayList<String> args = splitTopLevel(call.arguments, ',');
            Integer listType = listTypes.get(call.receiver);
            Integer variableType = variableTypes.get(call.receiver);

            if (listType != null) {
                switch (call.name) {
                    case "add": {
                        if (args.size() == 1) {
                            Object element = listElement(args.get(0), listType);
                            return element == null ? null : addElementBlock(listType, element, call.receiver);
                        }
                        if (args.size() == 2) {
                            Object index = value(args.get(0), TYPE_NUMBER);
                            Object element = listElement(args.get(1), listType);
                            if (index == null || element == null) {
                                return null;
                            }
                            if (listType == 1) {
                                return block("insertListInt", element, index, call.receiver);
                            }
                            if (listType == 2) {
                                return block("insertListStr", element, index, call.receiver);
                            }
                            return null;
                        }
                        return null;
                    }
                    case "remove": {
                        if (args.size() != 1) {
                            return null;
                        }
                        Object index = value(args.get(0), TYPE_NUMBER);
                        return index == null ? null : block("deleteList", index, call.receiver);
                    }
                    case "clear": {
                        return args.isEmpty() ? block("clearList", call.receiver) : null;
                    }
                    default:
                        return null;
                }
            }

            if (variableType != null && variableType == 3) {
                switch (call.name) {
                    case "put": {
                        if (args.size() != 2) {
                            return null;
                        }
                        Object key = stringValue(args.get(0));
                        Object mapped = value(args.get(1), TYPE_ANY);
                        return key == null || mapped == null ? null : block("mapPut", call.receiver, key, mapped);
                    }
                    case "remove": {
                        if (args.size() != 1) {
                            return null;
                        }
                        Object key = value(args.get(0), TYPE_STRING);
                        return key == null ? null : block("mapRemoveKey", call.receiver, key);
                    }
                    case "clear": {
                        return args.isEmpty() ? block("mapClear", call.receiver) : null;
                    }
                    default:
                        return null;
                }
            }
            return null;
        }

        /** The element of a list: a number for "List Number" and so on. */
        private Object listElement(String text, int listType) {
            if (listType == 1) {
                return value(text, TYPE_NUMBER);
            }
            if (listType == 2) {
                return stringValue(text);
            }
            Integer type = variableTypes.get(text.trim());
            return type != null && type == 3 ? text.trim() : null;
        }

        private Node addElementBlock(int listType, Object element, String list) {
            if (listType == 1) {
                return block("addListInt", element, list);
            }
            if (listType == 2) {
                return block("addListStr", element, list);
            }
            return block("addMapToList", element, list);
        }

        private Node mapAssignment(String target, String operator, String expression) {
            Integer type = variableTypes.get(target);
            if (type == null) {
                return null;
            }
            switch (type) {
                case TYPE_BOOLEAN: {
                    if (!operator.isEmpty()) {
                        return null;
                    }
                    Object mapped = value(expression, TYPE_BOOLEAN);
                    return mapped == null ? null : block("setVarBoolean", target, mapped);
                }
                case TYPE_NUMBER: {
                    Object mapped;
                    if (operator.isEmpty()) {
                        mapped = value(expression, TYPE_NUMBER);
                    } else if (operator.equals("%")) {
                        return null;
                    } else {
                        Object right = value(expression, TYPE_NUMBER);
                        mapped = right == null ? null : block(operator, getVariable(target, TYPE_NUMBER), right);
                    }
                    return mapped == null ? null : block("setVarInt", target, mapped);
                }
                case TYPE_STRING: {
                    if (operator.isEmpty()) {
                        Object mapped = stringValue(expression);
                        return mapped == null ? null : block("setVarString", target, mapped);
                    }
                    if (!operator.equals("+")) {
                        return null;
                    }
                    Object right = stringValue(expression);
                    if (right == null) {
                        return null;
                    }
                    return block("setVarString", target,
                            block("stringJoin", getVariable(target, TYPE_STRING), right));
                }
                default: {
                    return operator.isEmpty() && isMapCreation(expression) ? block("mapCreateNew", target) : null;
                }
            }
        }


        // -----------------------------------------------------------------------------------------
        // Values
        // -----------------------------------------------------------------------------------------

        /**
         * @return a block or plain text for the argument {@code text}, or {@code null} if the expression
         * can't be used inside a block argument (then the statement stays "add source directly" code)
         */
        Object value(String text, int expected) {
            String expression = text.trim();
            while (expression.endsWith(";")) {
                expression = expression.substring(0, expression.length() - 1).trim();
            }
            if (expression.isEmpty()) {
                return null;
            }
            Object mapped = mapValue(expression, expected);
            if (mapped != null) {
                return mapped;
            }
            return isInlineCode(expression) ? expression : null;
        }

        /**
         * Finds a More Block by the name its code is called with. Sketchware keeps the block under its plain
         * name but generates a method with a leading underscore, so both {@code greet} and {@code _greet} hit.
         */
        private MoreBlockCall moreBlockFor(String name) {
            MoreBlockCall moreBlock = moreBlocks.get(name);
            if (moreBlock == null && name.startsWith("_")) {
                moreBlock = moreBlocks.get(name.substring(1));
            }
            return moreBlock;
        }

        /**
         * A value for a {@code %s} slot. Numbers are wrapped into a "toString" block, because Sketchware's
         * String slots expect a String.
         */
        Object stringValue(String text) {
            if (!isStringExpression(text)) {
                if (isNumberExpression(text)) {
                    Object number = value(text, TYPE_NUMBER);
                    return number == null ? null : block("toString", number);
                }
            }
            Object mapped = value(text, TYPE_STRING);
            return mapped != null ? mapped : value(text, TYPE_ANY);
        }

        private Object mapValue(String expression, int expected) {
            String text = stripOuterParentheses(expression.trim());
            if (text.isEmpty()) {
                return null;
            }

            if (text.equals("true") || text.equals("false")) {
                return accepts(TYPE_BOOLEAN, expected) ? block(text) : null;
            }
            if (isNumericLiteral(text)) {
                return accepts(TYPE_NUMBER, expected) ? numericLiteral(text) : null;
            }
            if (isStringLiteral(text)) {
                return accepts(TYPE_STRING, expected) ? text : null;
            }
            if (isCharLiteral(text)) {
                return accepts(TYPE_STRING, expected) ? text : null;
            }
            if (text.equals("null")) {
                return expected == TYPE_ANY ? text : null;
            }
            if (text.equals("Math.PI")) {
                return accepts(TYPE_NUMBER, expected) ? block("mathPi") : null;
            }
            if (text.equals("Math.E")) {
                return accepts(TYPE_NUMBER, expected) ? block("mathE") : null;
            }

            Integer variableType = variableTypes.get(text);
            if (variableType != null) {
                return accepts(variableType, expected) ? getVariable(text, variableType) : null;
            }
            Integer argumentType = arguments.get(text);
            if (argumentType != null) {
                return accepts(argumentType, expected)
                        ? new Node("getArg", text.substring(1), argumentBlockType(argumentType), "", COLOR_VAR)
                        : null;
            }

            Object operation = mapOperation(text, expected);
            if (operation != null) {
                return operation;
            }
            return mapCall(text, expected);
        }

        private String argumentBlockType(int type) {
            return switch (type) {
                case TYPE_BOOLEAN -> "b";
                case TYPE_NUMBER -> "d";
                case TYPE_STRING -> "s";
                default -> "m";
            };
        }

        private Object mapOperation(String text, int expected) {
            if (text.startsWith("!")) {
                Object inner = value(text.substring(1), TYPE_BOOLEAN);
                return inner == null || !accepts(TYPE_BOOLEAN, expected) ? null : block("not", inner);
            }
            if (text.startsWith("-") && accepts(TYPE_NUMBER, expected)) {
                Object inner = value(text.substring(1), TYPE_NUMBER);
                return inner == null ? null : block("*", "-1", inner);
            }
            Matcher cast = Pattern.compile("^\\((?:int|double|float|long|short|byte)\\)\\s*(.+)$").matcher(text);
            if (cast.matches()) {
                return value(cast.group(1), expected);
            }

            for (String[] level : new String[][]{{"||"}, {"&&"}, {"==", "!=", ">=", "<=", ">", "<"}, {"+", "-"}, {"*", "/", "%"}}) {
                int index = findOperator(text, level);
                if (index < 0) {
                    continue;
                }
                String operator = null;
                for (String candidate : level) {
                    if (text.startsWith(candidate, index)) {
                        operator = candidate;
                        break;
                    }
                }
                if (operator == null) {
                    continue;
                }
                String left = text.substring(0, index).trim();
                String right = text.substring(index + operator.length()).trim();
                Object mapped = mapBinaryOperation(operator, left, right, expected);
                if (mapped != null) {
                    return mapped;
                }
            }
            return null;
        }

        private Object mapBinaryOperation(String operator, String left, String right, int expected) {
            switch (operator) {
                case "&&":
                case "||": {
                    if (!accepts(TYPE_BOOLEAN, expected)) {
                        return null;
                    }
                    Object mappedLeft = value(left, TYPE_BOOLEAN);
                    Object mappedRight = value(right, TYPE_BOOLEAN);
                    if (mappedLeft == null || mappedRight == null) {
                        return null;
                    }
                    return block(operator, mappedLeft, mappedRight);
                }
                case "==":
                case "!=": {
                    if (!accepts(TYPE_BOOLEAN, expected)) {
                        return null;
                    }
                    Object equality = mapEquality(left, right);
                    if (equality == null) {
                        return null;
                    }
                    return operator.equals("==") ? equality : block("not", equality);
                }
                case ">=":
                case "<=": {
                    if (!accepts(TYPE_BOOLEAN, expected)) {
                        return null;
                    }
                    // "a >= b" is Sketchware's "not (a < b)"
                    Object mappedLeft = value(left, TYPE_NUMBER);
                    Object mappedRight = value(right, TYPE_NUMBER);
                    if (mappedLeft == null || mappedRight == null) {
                        return null;
                    }
                    return operator.equals(">=") ? block("not", block("<", mappedLeft, mappedRight))
                            : block("not", block(">", mappedLeft, mappedRight));
                }
                case ">":
                case "<": {
                    if (!accepts(TYPE_BOOLEAN, expected)) {
                        return null;
                    }
                    Object mappedLeft = value(left, TYPE_NUMBER);
                    Object mappedRight = value(right, TYPE_NUMBER);
                    if (mappedLeft == null || mappedRight == null) {
                        return null;
                    }
                    return block(operator, mappedLeft, mappedRight);
                }
                case "+": {
                    if (expected == TYPE_NUMBER) {
                        return numberOperation("+", left, right);
                    }
                    if (expected == TYPE_STRING || isStringExpression(left) || isStringExpression(right)) {
                        Object mappedLeft = stringValue(left);
                        Object mappedRight = stringValue(right);
                        if (mappedLeft == null || mappedRight == null) {
                            return null;
                        }
                        return block("stringJoin", mappedLeft, mappedRight);
                    }
                    return numberOperation("+", left, right);
                }
                case "-":
                case "*":
                case "/":
                case "%": {
                    return accepts(TYPE_NUMBER, expected) ? numberOperation(operator, left, right) : null;
                }
                default:
                    return null;
            }
        }

        private Object numberOperation(String operator, String left, String right) {
            Object mappedLeft = value(left, TYPE_NUMBER);
            Object mappedRight = value(right, TYPE_NUMBER);
            if (mappedLeft == null || mappedRight == null) {
                return null;
            }
            return block(operator, mappedLeft, mappedRight);
        }

        /** {@code a == b} for numbers and Strings. */
        private Object mapEquality(String left, String right) {
            Object mappedLeft = value(left, TYPE_NUMBER);
            Object mappedRight = value(right, TYPE_NUMBER);
            if (mappedLeft != null && mappedRight != null && (isNumberExpression(left) || !isStringExpression(left))) {
                return block("=", mappedLeft, mappedRight);
            }
            mappedLeft = value(left, TYPE_STRING);
            mappedRight = value(right, TYPE_STRING);
            if (mappedLeft == null || mappedRight == null) {
                return null;
            }
            return block("stringEquals", mappedLeft, mappedRight);
        }

        private boolean isNumberExpression(String text) {
            String expression = stripOuterParentheses(text.trim());
            if (isNumericLiteral(expression) || expression.equals("Math.PI") || expression.equals("Math.E")) {
                return true;
            }
            Integer variableType = variableTypes.get(expression);
            if (variableType != null) {
                return variableType == TYPE_NUMBER;
            }
            Matcher call = Pattern.compile("^[A-Za-z_$][\\w$.]*\\s*\\(.*\\)$").matcher(expression);
            if (!call.matches()) {
                return false;
            }
            String name = expression.substring(0, expression.indexOf('(')).trim();
            MoreBlockCall moreBlock = moreBlockFor(name);
            return moreBlock != null && moreBlock.type.equals("d");
        }

        private boolean isStringExpression(String text) {
            String expression = stripOuterParentheses(text.trim());
            if (isStringLiteral(expression)) {
                return true;
            }
            Integer variableType = variableTypes.get(expression);
            if (variableType != null) {
                return variableType == TYPE_STRING;
            }
            Integer argumentType = arguments.get(expression);
            if (argumentType != null) {
                return argumentType == TYPE_STRING;
            }
            if (expression.endsWith(".toString()") || expression.endsWith(".trim()")
                    || expression.endsWith(".toUpperCase()") || expression.endsWith(".toLowerCase()")) {
                return true;
            }
            if (expression.matches("^(String\\.valueOf|Integer\\.toString|Double\\.toString|Long\\.toString)\\s*\\(.*\\)$")) {
                return true;
            }
            Matcher call = Pattern.compile("^[A-Za-z_$][\\w$.]*\\s*\\(.*\\)$").matcher(expression);
            if (!call.matches()) {
                return false;
            }
            String name = expression.substring(0, expression.indexOf('(')).trim();
            MoreBlockCall moreBlock = moreBlockFor(name);
            return moreBlock != null && moreBlock.type.equals("s");
        }

        /** Calls such as {@code Math.max(a, b)}, {@code message.trim()} or {@code list.size()}. */
        private Object mapCall(String text, int expected) {
            Call call = parseCall(text);
            if (call == null) {
                return null;
            }
            ArrayList<String> args = splitTopLevel(call.arguments, ',');
            String receiver = call.receiver;
            String name = call.name;

            if (receiver.isEmpty()) {
                MoreBlockCall moreBlock = moreBlockFor(name);
                return moreBlock == null ? mapStaticCall(receiver, name, args, expected) : mapMoreBlock(moreBlock, args);
            }

            if (!isIdentifier(receiver)) {
                // a call on the result of another call, e.g. map.get(key).toString()
                Object inner = value(receiver, TYPE_ANY);
                return inner == null ? null : mapValueMethod(name, inner, args, expected);
            }

            Integer listType = listTypes.get(receiver);
            if (listType != null) {
                switch (name) {
                    case "size": {
                        return args.isEmpty() && accepts(TYPE_NUMBER, expected) ? block("lengthList", receiver) : null;
                    }
                    case "get": {
                        if (args.size() != 1) {
                            return null;
                        }
                        Object index = value(args.get(0), TYPE_NUMBER);
                        if (index == null) {
                            return null;
                        }
                        if (listType == 1) {
                            return accepts(TYPE_NUMBER, expected) ? block("getAtListInt", index, receiver) : null;
                        }
                        if (listType == 2) {
                            return accepts(TYPE_STRING, expected) ? block("getAtListStr", index, receiver) : null;
                        }
                        return null;
                    }
                    case "indexOf": {
                        if (args.size() != 1 || listType != 2 || !accepts(TYPE_NUMBER, expected)) {
                            return null;
                        }
                        Object element = stringValue(args.get(0));
                        return element == null ? null : block("indexListStr", element, receiver);
                    }
                    case "contains": {
                        if (args.size() != 1 || !accepts(TYPE_BOOLEAN, expected)) {
                            return null;
                        }
                        if (listType == 1) {
                            Object element = value(args.get(0), TYPE_NUMBER);
                            return element == null ? null : block("containListInt", receiver, element);
                        }
                        if (listType == 2) {
                            Object element = stringValue(args.get(0));
                            return element == null ? null : block("containListStr", receiver, element);
                        }
                        return null;
                    }
                    case "isEmpty": {
                        // "list.isEmpty()" is Sketchware's "length of list = 0"
                        return args.isEmpty() && accepts(TYPE_BOOLEAN, expected)
                                ? block("=", block("lengthList", receiver), "0") : null;
                    }
                    default:
                        return null;
                }
            }

            Integer variableType = variableTypes.get(receiver);
            if (variableType != null && variableType == TYPE_STRING) {
                return mapStringMethod(name, getVariable(receiver, TYPE_STRING), args, expected);
            }
            if (variableType != null && variableType == 3) {
                switch (name) {
                    case "get": {
                        if (args.size() != 1) {
                            return null;
                        }
                        Object key = value(args.get(0), TYPE_STRING);
                        return key == null || !accepts(TYPE_STRING, expected) ? null : block("mapGet", receiver, key);
                    }
                    case "size": {
                        return args.isEmpty() && accepts(TYPE_NUMBER, expected) ? block("mapSize", receiver) : null;
                    }
                    case "containsKey": {
                        if (args.size() != 1 || !accepts(TYPE_BOOLEAN, expected)) {
                            return null;
                        }
                        Object key = value(args.get(0), TYPE_STRING);
                        return key == null ? null : block("mapContainKey", receiver, key);
                    }
                    case "isEmpty": {
                        return args.isEmpty() && accepts(TYPE_BOOLEAN, expected) ? block("mapIsEmpty", receiver) : null;
                    }
                    default:
                        return null;
                }
            }

            MoreBlockCall moreBlock = moreBlockFor(receiver);
            if (moreBlock != null) {
                return mapMoreBlock(moreBlock, args);
            }

            return mapStaticCall(receiver, name, args, expected);
        }

        /** Methods of a String: {@code trim()}, {@code substring(…)} and friends. */
        private Object mapValueMethod(String name, Object inner, ArrayList<String> args, int expected) {
            if (name.equals("toString") && args.isEmpty()) {
                if (inner instanceof Node node) {
                    if (node.type.equals("s")) {
                        return node;
                    }
                    if (node.type.equals("d")) {
                        return accepts(TYPE_STRING, expected) ? block("toString", node) : null;
                    }
                    return null;
                }
                return inner;
            }
            return mapStringMethod(name, inner, args, expected);
        }

        private Object mapStringMethod(String name, Object inner, ArrayList<String> args, int expected) {
            switch (name) {
                case "isEmpty": {
                    // "a.isEmpty()" is Sketchware's "a equals \"\""
                    return args.isEmpty() && accepts(TYPE_BOOLEAN, expected)
                            ? block("stringEquals", inner, "\"\"") : null;
                }
                case "length": {
                    return args.isEmpty() && accepts(TYPE_NUMBER, expected) ? block("stringLength", inner) : null;
                }
                case "trim": {
                    return args.isEmpty() && accepts(TYPE_STRING, expected) ? block("trim", inner) : null;
                }
                case "toUpperCase": {
                    return args.isEmpty() && accepts(TYPE_STRING, expected) ? block("toUpperCase", inner) : null;
                }
                case "toLowerCase": {
                    return args.isEmpty() && accepts(TYPE_STRING, expected) ? block("toLowerCase", inner) : null;
                }
                case "equals": {
                    if (args.size() != 1 || !accepts(TYPE_BOOLEAN, expected)) {
                        return null;
                    }
                    Object other = stringValue(args.get(0));
                    return other == null ? null : block("stringEquals", inner, other);
                }
                case "contains": {
                    if (args.size() != 1 || !accepts(TYPE_BOOLEAN, expected)) {
                        return null;
                    }
                    Object other = stringValue(args.get(0));
                    return other == null ? null : block("stringContains", inner, other);
                }
                case "indexOf": {
                    if (args.size() != 1 || !accepts(TYPE_NUMBER, expected)) {
                        return null;
                    }
                    Object other = stringValue(args.get(0));
                    return other == null ? null : block("stringIndex", other, inner);
                }
                case "concat": {
                    if (args.size() != 1 || !accepts(TYPE_STRING, expected)) {
                        return null;
                    }
                    Object other = stringValue(args.get(0));
                    return other == null ? null : block("stringJoin", inner, other);
                }
                case "substring": {
                    if (args.size() != 2 || !accepts(TYPE_STRING, expected)) {
                        return null;
                    }
                    Object from = value(args.get(0), TYPE_NUMBER);
                    Object to = value(args.get(1), TYPE_NUMBER);
                    return from == null || to == null ? null : block("stringSub", inner, from, to);
                }
                case "replace": {
                    return stringReplace("stringReplace", inner, args, expected);
                }
                case "replaceAll": {
                    return stringReplace("stringReplaceAll", inner, args, expected);
                }
                case "replaceFirst": {
                    return stringReplace("stringReplaceFirst", inner, args, expected);
                }
                default:
                    return null;
            }
        }

        private Object stringReplace(String opCode, Object inner, ArrayList<String> args, int expected) {
            if (args.size() != 2 || !accepts(TYPE_STRING, expected)) {
                return null;
            }
            Object from = stringValue(args.get(0));
            Object to = stringValue(args.get(1));
            return from == null || to == null ? null : block(opCode, inner, from, to);
        }

        /** Calls of static helpers: {@code Math…}, {@code Double.parseDouble…}, {@code SketchwareUtil.getRandom…}. */
        private Object mapStaticCall(String owner, String name, ArrayList<String> args, int expected) {
            switch (owner) {
                case "Math": {
                    switch (name) {
                        case "pow":
                        case "max":
                        case "min": {
                            if (args.size() != 2 || !accepts(TYPE_NUMBER, expected)) {
                                return null;
                            }
                            String opCode = switch (name) {
                                case "pow" -> "mathPow";
                                case "max" -> "mathMax";
                                default -> "mathMin";
                            };
                            Object first = value(args.get(0), TYPE_NUMBER);
                            Object second = value(args.get(1), TYPE_NUMBER);
                            return first == null || second == null ? null : block(opCode, first, second);
                        }
                        case "random": {
                            return null;
                        }
                        default: {
                            if (args.size() != 1 || !accepts(TYPE_NUMBER, expected)) {
                                return null;
                            }
                            String opCode = switch (name) {
                                case "abs" -> "mathAbs";
                                case "sqrt" -> "mathSqrt";
                                case "ceil" -> "mathCeil";
                                case "floor" -> "mathFloor";
                                case "round" -> "mathRound";
                                case "sin" -> "mathSin";
                                case "cos" -> "mathCos";
                                case "tan" -> "mathTan";
                                case "asin" -> "mathAsin";
                                case "acos" -> "mathAcos";
                                case "atan" -> "mathAtan";
                                case "exp" -> "mathExp";
                                case "log" -> "mathLog";
                                case "log10" -> "mathLog10";
                                case "toRadians" -> "mathToRadian";
                                case "toDegrees" -> "mathToDegree";
                                default -> null;
                            };
                            if (opCode == null) {
                                return null;
                            }
                            Object argument = value(args.get(0), TYPE_NUMBER);
                            return argument == null ? null : block(opCode, argument);
                        }
                    }
                }
                case "String": {
                    if (name.equals("valueOf") && args.size() == 1 && accepts(TYPE_STRING, expected)) {
                        Object argument = value(args.get(0), TYPE_ANY);
                        if (argument == null) {
                            return null;
                        }
                        if (argument instanceof Node node && node.type.equals("s")) {
                            return node;
                        }
                        Object number = value(args.get(0), TYPE_NUMBER);
                        return number == null ? argument : block("toString", number);
                    }
                    return null;
                }
                case "Double":
                case "Float":
                case "Integer":
                case "Long":
                case "Short":
                case "Byte": {
                    if ((name.equals("parseDouble") || name.equals("parseFloat") || name.equals("parseInt")
                            || name.equals("parseLong") || name.equals("valueOf") || name.equals("toString"))
                            && args.size() == 1) {
                        if (name.equals("toString")) {
                            if (!accepts(TYPE_STRING, expected)) {
                                return null;
                            }
                            Object number = value(args.get(0), TYPE_NUMBER);
                            return number == null ? null : block("toString", number);
                        }
                        if (!accepts(TYPE_NUMBER, expected)) {
                            return null;
                        }
                        Object text = stringValue(args.get(0));
                        return text == null ? null : block("toNumber", text);
                    }
                    return null;
                }
                case "SketchwareUtil": {
                    if (name.equals("getRandom") && args.size() == 2 && accepts(TYPE_NUMBER, expected)) {
                        Object from = value(args.get(0), TYPE_NUMBER);
                        Object to = value(args.get(1), TYPE_NUMBER);
                        return from == null || to == null ? null : block("random", from, to);
                    }
                    return null;
                }
                default:
                    return null;
            }
        }

        /** A call of one of the converted More Blocks. */
        private Node mapMoreBlock(MoreBlockCall moreBlock, ArrayList<String> args) {
            if (args.size() != moreBlock.parameterTypes.length) {
                return null;
            }
            Node node = new Node("definedFunc", moreBlock.spec, moreBlock.type, moreBlock.typeName, moreBlock.color);
            for (int i = 0; i < args.size(); i++) {
                Object mapped = moreBlock.parameterTypes[i] == TYPE_STRING
                        ? stringValue(args.get(i))
                        : value(args.get(i), moreBlock.parameterTypes[i]);
                if (mapped == null) {
                    return null;
                }
                node.parameters.add(mapped);
            }
            return node;
        }


        // -----------------------------------------------------------------------------------------
        // Small helpers
        // -----------------------------------------------------------------------------------------

        private static Call parseCall(String text) {
            String code = text.trim();
            if (!code.endsWith(")")) {
                return null;
            }
            int open = findOpenParenthesis(code, code.length() - 1);
            if (open < 0) {
                return null;
            }
            int nameStart = open;
            while (nameStart > 0 && isIdentifierPart(code.charAt(nameStart - 1))) {
                nameStart--;
            }
            if (nameStart == open) {
                return null;
            }
            String name = code.substring(nameStart, open);
            String receiver = "";
            if (nameStart > 1 && code.charAt(nameStart - 1) == '.') {
                receiver = code.substring(0, nameStart - 1).trim();
            } else if (nameStart != 0) {
                return null;
            }
            return new Call(receiver, name, code.substring(open + 1, code.length() - 1));
        }

        private static int findOpenParenthesis(String text, int closeIndex) {
            ArrayList<Integer> openParentheses = new ArrayList<>();
            for (int i = 0; i <= closeIndex && i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' || c == '\'') {
                    i = skipLiteral(text, i);
                    continue;
                }
                if (c == '(') {
                    openParentheses.add(i);
                } else if (c == ')') {
                    if (openParentheses.isEmpty()) {
                        return -1;
                    }
                    int open = openParentheses.remove(openParentheses.size() - 1);
                    if (i == closeIndex) {
                        return open;
                    }
                }
            }
            return -1;
        }

        /** @return the index of the closing quote of the literal starting at {@code quoteIndex} */
        private static int skipLiteral(String text, int quoteIndex) {
            char quote = text.charAt(quoteIndex);
            for (int i = quoteIndex + 1; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    return i;
                }
            }
            return text.length() - 1;
        }

        /** @return the index of a top level {@code =} of an assignment, or -1 */
        private static int indexOfAssignment(String text) {
            int depth = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' || c == '\'') {
                    i = skipLiteral(text, i);
                    continue;
                }
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                } else if (c == '=' && depth == 0) {
                    char next = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
                    char previous = i > 0 ? text.charAt(i - 1) : ' ';
                    if (next == '=' || previous == '=' || previous == '!' || previous == '<' || previous == '>') {
                        return -1;
                    }
                    return i;
                }
            }
            return -1;
        }

        /** @return the index of the last top level operator of {@code operators}, or -1 */
        private static int findOperator(String text, String[] operators) {
            int depth = 0;
            int found = -1;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' || c == '\'') {
                    i = skipLiteral(text, i);
                    continue;
                }
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                    continue;
                }
                if (c == ')' || c == ']' || c == '}') {
                    depth--;
                    continue;
                }
                if (depth != 0) {
                    continue;
                }
                for (String operator : operators) {
                    if (!text.startsWith(operator, i)) {
                        continue;
                    }
                    char operatorChar = operator.charAt(0);
                    int after = i + operator.length();
                    char next = after < text.length() ? text.charAt(after) : ' ';
                    char previous = i > 0 ? text.charAt(i - 1) : ' ';
                    if ((operatorChar == '+' || operatorChar == '-') && isUnary(text, i)) {
                        continue;
                    }
                    if (operatorChar == '<' || operatorChar == '>') {
                        // "a <= b" must not be read as a "<" followed by "= b", and "a << b" is no comparison
                        if (next == operatorChar || (next == '=' && operator.length() == 1)) {
                            continue;
                        }
                        if (previous == operatorChar || previous == '=' || previous == '!') {
                            continue;
                        }
                    }
                    found = i;
                }
            }
            return found;
        }

        private static boolean isUnary(String text, int index) {
            for (int i = index - 1; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == ' ' || c == '\t') {
                    continue;
                }
                return "(,=+-*/%<>!&|?:[^".indexOf(c) >= 0;
            }
            return true;
        }

        private static boolean isMapCreation(String text) {
            return text.trim().matches("^new\\s+(java\\.util\\.)?HashMap\\s*(<[^>]*>)?\\s*\\(\\s*\\)$");
        }

        private static String stripOuterParentheses(String text) {
            String result = text;
            while (result.startsWith("(") && result.endsWith(")")) {
                int close = findMatchingParenthesis(result, 0);
                if (close != result.length() - 1) {
                    break;
                }
                result = result.substring(1, result.length() - 1).trim();
            }
            return result;
        }

        private static boolean isNumericLiteral(String text) {
            return text.matches("-?\\d+(\\.\\d+)?[dDfFlL]?");
        }

        private static String numericLiteral(String text) {
            String result = text.trim();
            char last = result.charAt(result.length() - 1);
            if (Character.isLetter(last)) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        }

        private static boolean isStringLiteral(String text) {
            return STRING_OR_CHAR.matcher(text).matches() && text.startsWith("\"");
        }

        private static boolean isCharLiteral(String text) {
            return STRING_OR_CHAR.matcher(text).matches() && text.startsWith("'");
        }

        private static boolean isIdentifier(String text) {
            if (text.isEmpty() || !isIdentifierPart(text.charAt(0))) {
                return false;
            }
            for (int i = 1; i < text.length(); i++) {
                if (!isIdentifierPart(text.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isIdentifierPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '$';
        }

        private static boolean startsWithWord(String text, String word) {
            return text.startsWith(word)
                    && (text.length() == word.length() || !isIdentifierPart(text.charAt(word.length())));
        }

        private static boolean containsIdentifier(String text, String name) {
            return Pattern.compile("(?<![\\w$])" + Pattern.quote(name) + "(?![\\w$])").matcher(text).find();
        }

        private static boolean accepts(int valueType, int expected) {
            return expected == TYPE_ANY || valueType == TYPE_ANY || valueType == expected;
        }
    }

    /**
     * The Logic Editor stores block arguments in single line text fields and reads a leading
     * {@code @} as a nested block, so an argument has to be single line code without a leading {@code @}.
     */
    static boolean isInlineCode(String text) {
        return !text.isEmpty() && !text.startsWith("@") && !text.contains("{") && !text.contains("}")
                && !text.contains(";") && !text.contains("\n") && !text.contains("\r");
    }

    /**
     * Collects everything the mapper needs to know: the page's variables and lists, its More Blocks
     * (including the ones that were just created) and the More Block calls that may be rewritten.
     */
    private BlockMapper createBlockMapper(Set<String> generatedMethods, Map<String, MoreBlockDef> createdMoreBlocks) {
        BlockMapper mapper = new BlockMapper(generatedMethods);

        ArrayList<Pair<Integer, String>> variables = data.k(javaName);
        if (variables != null) {
            for (Pair<Integer, String> variable : variables) {
                mapper.addVariable(variable.second, variable.first);
            }
        }
        ArrayList<Pair<Integer, String>> lists = data.j(javaName);
        if (lists != null) {
            for (Pair<Integer, String> list : lists) {
                mapper.addList(list.second, list.first);
            }
        }

        ArrayList<Pair<String, String>> existingMoreBlocks = data.i(javaName);
        if (existingMoreBlocks != null) {
            for (Pair<String, String> moreBlock : existingMoreBlocks) {
                String spec = moreBlock.second;
                mapper.addMoreBlock(moreBlockCall(spec, parameterTypesOf(spec)));
            }
        }
        for (MoreBlockDef moreBlock : createdMoreBlocks.values()) {
            mapper.addMoreBlock(moreBlockCall(moreBlock.spec, moreBlock.parameterTypes));
        }
        return mapper;
    }

    /**
     * Builds the call of a More Block. A call uses the name without Sketchware's {@code [type]} suffix,
     * because {@code a.a.a.Fx} takes everything before the first space as the method name.
     */
    private static MoreBlockCall moreBlockCall(String spec, int[] parameterTypes) {
        String definition = spec.replaceAll("\\[.*?]", "");
        int space = definition.indexOf(' ');
        String name = space < 0 ? definition : definition.substring(0, space);
        String callSpec = space < 0 ? name : name + definition.substring(space);

        String type = ReturnMoreblockManager.getMoreblockChar(spec);
        String typeName = "";
        if (type.contains("|")) {
            String[] split = type.split("\\|");
            type = split[0];
            typeName = split.length > 1 ? split[1] : "";
        }
        return new MoreBlockCall(name, callSpec, type, typeName, parameterTypes);
    }

    /** The parameter types a More Block's spec declares, e.g. {@code greet[String] %s.name %d.times}. */
    private static int[] parameterTypesOf(String spec) {
        String definition = spec.replaceAll("\\[.*?]", "");
        ArrayList<Integer> types = new ArrayList<>();
        Matcher matcher = Pattern.compile("%([a-zA-Z])").matcher(definition);
        while (matcher.find()) {
            switch (matcher.group(1)) {
                case "b" -> types.add(TYPE_BOOLEAN);
                case "d" -> types.add(TYPE_NUMBER);
                case "s" -> types.add(TYPE_STRING);
                default -> types.add(TYPE_ANY);
            }
        }
        int[] result = new int[types.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = types.get(i);
        }
        return result;
    }

    /** Registers the arguments Sketchware generates for an event, so that reading them becomes a block. */
    private static void registerEventArguments(BlockMapper mapper, String eventName) {
        for (EventSpec spec : EVENTS.values()) {
            if (!spec.eventName.equals(eventName)) {
                continue;
            }
            for (int i = 0; i < spec.paramNames.length && i < spec.paramTypes.length; i++) {
                int type = valueTypeOf(spec.paramTypes[i]);
                if (type >= 0) {
                    mapper.addArgument(spec.paramNames[i], type);
                }
            }
        }
    }

    private static int valueTypeOf(String javaType) {
        return switch (javaType) {
            case "boolean" -> TYPE_BOOLEAN;
            case "int", "double", "float", "long", "short", "byte" -> TYPE_NUMBER;
            case "String" -> TYPE_STRING;
            default -> -1;
        };
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
        int genericDepth = 0;
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
            } else if (c == '{' || c == '(' || c == '[') {
                depth++;
                current.append(c);
            } else if (c == '}' || c == ')' || c == ']') {
                depth--;
                current.append(c);
            } else if (c == '<' && opensGenericArguments(text, i)) {
                // "Map<String, Object>" nests, "count < limit" does not
                depth++;
                genericDepth++;
                current.append(c);
            } else if (c == '>' && genericDepth > 0) {
                depth--;
                genericDepth--;
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

    /**
     * Tells an opening angle bracket of a generic type apart from the smaller-than operator, so that
     * {@code for (int i = 0; i < 5; i++)} can be split on its semicolons.
     */
    private static boolean opensGenericArguments(String text, int index) {
        for (int i = index + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '>') {
                return true;
            }
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.' || c == ',' || c == '?'
                    || c == '[' || c == ']' || c == '<' || Character.isWhitespace(c)) {
                continue;
            }
            return false;
        }
        return false;
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
