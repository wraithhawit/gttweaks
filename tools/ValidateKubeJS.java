import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Static checker for KubeJS scripts against ProbeJS type definitions.
 *
 * <p>KubeJS reports "0 errors" for scripts whose lambdas have not run yet, so a
 * clean startup log says nothing about code inside .pattern(), .definition() or
 * any other deferred callback. Those fail only once something triggers them,
 * which for a multiblock means restarting and building the structure in world.
 * This turns that loop into a grep.
 *
 * <p>Two checks, both aimed at failures that actually happened while porting
 * All the Mods 9's GregTech scripts to 8.0:
 *
 * <pre>
 *   UNKNOWN    a method name that exists nowhere in the generated types.
 *              Catches renames: aisle -> slice, blastTemp -> blast.
 *
 *   AMBIGUOUS  a static call to a method with several overloads, written
 *              without the explicit-signature form. Rhino picks by argument
 *              shape and can pick wrong: blast(Function) resolved to
 *              blast(int), and autoAbilities(GTRecipeType[]) resolved to the
 *              varargs element form and stringified the array. Both crashed at
 *              runtime, one of them only when a multiblock was assembled.
 * </pre>
 *
 * <p>Usage: {@code java ValidateKubeJS.java <.probe dir> <scripts dir>...}
 * Exits 1 if any UNKNOWN was found, so it can gate a port.
 */
public class ValidateKubeJS {

    /**
     * Declared members per simple class name, ProbeJS $-prefix stripped. The value is the set of
     * first-parameter <em>categories</em> across that method's overloads, not a plain count.
     *
     * <p>Overload count alone is a bad signal: {@code abilities(PartAbility)} and
     * {@code abilities(PartAbility...)} are two overloads but pose no hazard, because any argument
     * that fits one fits the other. What actually bit us was overloads whose first parameters are
     * different <em>kinds</em> of thing, so a JS value plausibly matches more than one and Rhino
     * chooses by shape: {@code blast(int)} vs {@code blast(UnaryOperator)}, and
     * {@code autoAbilities(GTRecipeType[])} vs {@code autoAbilities(boolean, boolean, boolean)}.
     * Arrays collapse to their element's category so varargs pairs stay quiet.
     */
    static final Map<String, Map<String, Set<String>>> CLASS_METHODS = new HashMap<>();

    /** Every method name known anywhere, for the cheap "does this exist at all" check. */
    static final Set<String> ALL_METHODS = new HashSet<>();

    /**
     * class -> method -> parameter count -> first-parameter categories at that count.
     *
     * <p>Argument count usually settles what overload count alone cannot. GregTech's
     * {@code autoAbilities} has an object-first form and a boolean-first form, so it looks
     * ambiguous, but the object form takes 1 or 7 arguments and the boolean form takes exactly 3 —
     * a call with 7 arguments can only mean one of them. Flagging it anyway trains you to ignore
     * the warnings, so only report when the call's own arity still leaves a genuine choice.
     */
    static final Map<String, Map<String, Map<Integer, Set<String>>>> CLASS_ARITY = new HashMap<>();

    /**
     * class -> method -> parameter counts whose first parameter is an array type.
     *
     * <p>This is the shape behind the nastiest failure of the port. Rhino, handed a single Java
     * array for a varargs parameter, may treat it as one <em>element</em> rather than as the array
     * itself; GregTech's type wrapper then tries to build a ResourceLocation out of it and dies
     * with "Could not create ID from gtceu:[Lcom...GTRecipeType;". It survives script load and only
     * throws when the enclosing lambda runs, which for a multiblock means in-world assembly.
     *
     * <p>A JS array literal is fine — Rhino converts it correctly — so only a non-literal single
     * argument is worth reporting.
     */
    static final Map<String, Map<String, Set<Integer>>> ARRAY_FIRST_PARAM = new HashMap<>();

    static final Pattern TYPE_DECL = Pattern.compile("export (?:class|interface) \\$([A-Za-z0-9_$]+)");
    static final Pattern MEMBER = Pattern.compile(
            "^\\s+(?:static\\s+)?(?:readonly\\s+)?([a-zA-Z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*:\\s*([^;]+)");

    /**
     * Method names whose declared return type is an array.
     *
     * <p>Passing one of these straight into a varargs parameter is the actual hazard —
     * {@code autoAbilities(definition.getRecipeTypes())} — whereas passing an ordinary single value
     * such as {@code blocks("gtceu:casing")} is entirely normal. Without this distinction the check
     * fires on almost every predicate in the file and is worse than useless.
     */
    static final Set<String> RETURNS_ARRAY = new HashSet<>();

    /**
     * Declaration tallies per method name: index 0 total, index 1 array-returning.
     *
     * <p>Names are the only handle available without real type inference, so a name only counts as
     * array-valued when <em>every</em> declaration of it returns an array. Otherwise one obscure
     * {@code get()} returning an array anywhere in 14,000 types marks every {@code .get()} in the
     * codebase as suspicious, which is how the first version of this check produced 54 false
     * positives and zero useful ones.
     */
    static final Map<String, int[]> RETURN_TALLY = new HashMap<>();
    static final Pattern CONSTANT = Pattern.compile("^\\s+static\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:");

    static final Pattern STATIC_CALL = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\.([a-zA-Z_][A-Za-z0-9_]*)\\s*\\(");
    /**
     * Any {@code Capitalised.member} use, call or not, so deleted constant holders are caught —
     * {@code GuiTextures.PROGRESS_BAR_ARROW} has no parentheses for the call patterns to match.
     * A leading {@code $} is captured rather than skipped, because scripts conventionally name
     * {@code Java.loadClass} results {@code $Foo}; keeping it means those resolve against the
     * script's own declarations instead of being reported as missing bindings.
     */
    static final Pattern NAMESPACE_USE = Pattern.compile("(?<![\\w$])(\\$?[A-Z][A-Za-z0-9_]*)\\.[a-zA-Z_]");
    static final Pattern CHAIN_CALL = Pattern.compile("\\.([a-zA-Z_][A-Za-z0-9_]*)\\s*\\(");
    static final Pattern EXPLICIT_SIG = Pattern.compile("\\[\\s*[\"']([a-zA-Z_][A-Za-z0-9_]*)\\(");
    static final Pattern LOCAL_DEF = Pattern.compile(
            "(?:function\\s+([a-zA-Z_][A-Za-z0-9_]*)|(?:const|let|var)\\s+([a-zA-Z_$][A-Za-z0-9_$]*))");

    /**
     * Global bindings as ProbeJS writes them. Two shapes: plain bindings appear as
     * {@code let Predicates: typeof $Predicates;} in the per-side bindings files, while event
     * groups appear as {@code namespace GTCEuStartupEvents {}}. Missing the second shape reports
     * every event group as an unknown binding.
     */
    static final Pattern BINDING_DECL = Pattern.compile(
            "^\\s*(?:(?:let|const|var)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*typeof"
                    + "|(?:export\\s+)?namespace\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{)");

    /** Global bindings KubeJS actually exposes, harvested from ProbeJS rather than assumed. */
    static final Set<String> BINDINGS = new HashSet<>();

    /** Capitalised identifiers that are legitimately used without being KubeJS bindings. */
    static final Set<String> NON_BINDING_GLOBALS = new HashSet<>(Arrays.asList(
            "Java", "Math", "JSON", "Object", "Array", "String", "Number", "Boolean", "Date",
            "Map", "Set", "Error", "Promise", "RegExp", "Symbol", "Infinity", "NaN"));

    /** JS/Rhino builtins and KubeJS globals that will never appear in the generated types. */
    static final Set<String> IGNORE = new HashSet<>(Arrays.asList(
            "forEach", "map", "filter", "push", "pop", "slice", "splice", "join", "split",
            "indexOf", "includes", "concat", "reduce", "sort", "reverse", "keys", "values",
            "toString", "valueOf", "charAt", "substring", "replace", "trim", "parseInt",
            "parseFloat", "isNaN", "log", "info", "warn", "error", "of", "loadClass",
            "registry", "create", "require", "startsWith", "endsWith", "toUpperCase",
            "toLowerCase", "padStart", "padEnd", "repeat", "apply", "call", "bind",
            "hasOwnProperty", "freeze", "assign", "entries", "from", "isArray", "stringify",
            "parse", "max", "min", "floor", "ceil", "round", "abs", "pow", "random", "sqrt",
            "get", "set", "add", "remove", "has", "size", "clear"));

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ValidateKubeJS <.probe dir> <scripts dir>...");
            System.exit(2);
        }
        loadTypes(Path.of(args[0]));
        RETURN_TALLY.forEach((name, tally) -> {
            if (tally[1] == tally[0]) {
                RETURNS_ARRAY.add(name);
            }
        });
        System.out.printf("indexed %d types, %d distinct method names, %d array-valued%n%n",
                CLASS_METHODS.size(), ALL_METHODS.size(), RETURNS_ARRAY.size());

        int unknown = 0;
        int ambiguous = 0;
        int files = 0;
        // KubeJS runs every script of a type in one shared scope, so a `const` declared at top
        // level in two files is a redeclaration error that takes BOTH files down — and it only
        // shows up once more than one of them is present, which makes it easy to introduce by
        // copying a helper into several files at once.
        Map<String, List<String>> topLevelConsts = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            for (Path js : scripts(Path.of(args[i]))) {
                files++;
                int[] counts = checkScript(js);
                unknown += counts[0];
                ambiguous += counts[1];
                for (String name : topLevelDeclarations(js)) {
                    topLevelConsts.computeIfAbsent(name, k -> new ArrayList<>()).add(js.getFileName().toString());
                }
            }
        }
        for (Map.Entry<String, List<String>> e : topLevelConsts.entrySet()) {
            if (e.getValue().size() > 1) {
                System.out.printf("REDECLARE %s declared at top level in %d files: %s%n",
                        e.getKey(), e.getValue().size(), String.join(", ", e.getValue()));
                unknown++;
            }
        }
        System.out.printf("%nchecked %d files: %d UNKNOWN, %d AMBIGUOUS%n", files, unknown, ambiguous);
        if (unknown > 0) {
            System.exit(1);
        }
    }

    /** Top-level (column-zero) const/let declarations, which share one scope across all scripts. */
    static List<String> topLevelDeclarations(Path js) throws IOException {
        Pattern decl = Pattern.compile("^(?:const|let)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
        List<String> names = new ArrayList<>();
        for (String line : Files.readAllLines(js, StandardCharsets.UTF_8)) {
            Matcher m = decl.matcher(line);
            if (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    static List<Path> scripts(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".js")).sorted().collect(Collectors.toList());
        }
    }

    static void loadTypes(Path probe) throws IOException {
        List<Path> types;
        try (Stream<Path> s = Files.walk(probe)) {
            types = s.filter(f -> f.getFileName().toString().endsWith(".d.ts")).collect(Collectors.toList());
        }
        for (Path p : types) {
            String current = null;
            List<String> lines;
            try {
                lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            for (String line : lines) {
                Matcher t = TYPE_DECL.matcher(line);
                if (t.find()) {
                    current = t.group(1);
                    continue;
                }
                Matcher m = MEMBER.matcher(line);
                if (m.find()) {
                    String name = m.group(1);
                    if (name.equals("constructor")) {
                        continue;
                    }
                    ALL_METHODS.add(name);
                    int[] tally = RETURN_TALLY.computeIfAbsent(name, k -> new int[2]);
                    tally[0]++;
                    if (m.group(3) != null && m.group(3).trim().endsWith("[]")) {
                        tally[1]++;
                    }
                    if (current != null) {
                        String category = firstParamCategory(m.group(2));
                        CLASS_METHODS.computeIfAbsent(current, k -> new HashMap<>())
                                .computeIfAbsent(name, k -> new HashSet<>())
                                .add(category);
                        int arity = paramCount(m.group(2));
                        CLASS_ARITY.computeIfAbsent(current, k -> new HashMap<>())
                                .computeIfAbsent(name, k -> new HashMap<>())
                                .computeIfAbsent(arity, k -> new HashSet<>())
                                .add(category);
                        if (firstParamIsArray(m.group(2))) {
                            ARRAY_FIRST_PARAM.computeIfAbsent(current, k -> new HashMap<>())
                                    .computeIfAbsent(name, k -> new HashSet<>())
                                    .add(arity);
                        }
                    }
                    continue;
                }
                Matcher c = CONSTANT.matcher(line);
                if (c.find() && current != null) {
                    // Constants are not calls, but record them so Foo.BAR is never flagged.
                    CLASS_METHODS.computeIfAbsent(current, k -> new HashMap<>())
                            .computeIfAbsent(c.group(1), k -> new HashSet<>());
                }
                Matcher b = BINDING_DECL.matcher(line);
                if (b.find()) {
                    BINDINGS.add(b.group(1) != null ? b.group(1) : b.group(2));
                }
            }
        }
    }

    /**
     * Classifies an overload by the kind of its first parameter, which is what Rhino discriminates
     * on in practice. Arrays report their element's category so that a {@code (T)} / {@code (T...)}
     * pair — the commonest and most harmless overload shape — collapses to a single category.
     */
    static String firstParamCategory(String params) {
        String p = params.trim();
        if (p.isEmpty()) {
            return "none";
        }
        int colon = p.indexOf(':');
        String type = (colon >= 0 ? p.substring(colon + 1) : p);
        int comma = type.indexOf(',');
        if (comma >= 0) {
            type = type.substring(0, comma);
        }
        type = type.trim();
        while (type.endsWith("[]")) {
            type = type.substring(0, type.length() - 2).trim();
        }
        String lower = type.toLowerCase();
        if (lower.equals("number") || lower.equals("boolean") || lower.equals("bigint")) {
            return "primitive";
        }
        if (lower.equals("string")) {
            return "string";
        }
        if (type.contains("=>") || lower.contains("function") || lower.contains("operator")
                || lower.contains("consumer") || lower.contains("supplier") || lower.contains("predicate_")) {
            return "function";
        }
        return "object";
    }

    /** True when the first declared parameter is an array (which includes varargs in ProbeJS output). */
    static boolean firstParamIsArray(String params) {
        String p = params.trim();
        if (p.isEmpty()) {
            return false;
        }
        int colon = p.indexOf(':');
        String type = (colon >= 0 ? p.substring(colon + 1) : p);
        int comma = type.indexOf(',');
        if (comma >= 0) {
            type = type.substring(0, comma);
        }
        return type.trim().endsWith("[]");
    }

    /**
     * Text of a call's first argument, or null if the call does not close on this line.
     *
     * <p>Deliberately the first argument rather than the sole argument: the hazard is a Java array
     * reaching an array parameter, and that is just as broken in
     * {@code autoAbilities(getRecipeTypes(), true, true, ...)} as in the one-argument form. An
     * earlier version only inspected single-argument calls and therefore passed the seven-argument
     * rewrite, which then crashed in exactly the same way.
     */
    static String firstArgument(String line, int openParen) {
        int depth = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = openParen; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
                if (depth == 1) {
                    continue;
                }
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return sb.toString().trim();
                }
            } else if (c == ',' && depth == 1) {
                return sb.toString().trim();
            }
            if (depth >= 1) {
                sb.append(c);
            }
        }
        return null;
    }

    /** Declared parameter count for a ProbeJS signature body such as {@code arg0: X, arg1: Y}. */
    static int paramCount(String params) {
        String p = params.trim();
        if (p.isEmpty()) {
            return 0;
        }
        int count = 1;
        int depth = 0;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '<' || c == '(' || c == '[') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Argument count at a call site, given the offset of its opening parenthesis. Returns -1 when
     * the call does not close on the same line, in which case the caller falls back to the
     * arity-blind check rather than guessing.
     */
    static int argCount(String line, int openParen) {
        int depth = 0;
        int count = 0;
        boolean any = false;
        for (int i = openParen; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return any ? count + 1 : 0;
                }
            } else if (c == ',' && depth == 1) {
                count++;
                any = true;
            } else if (!Character.isWhitespace(c) && depth == 1) {
                any = true;
            }
        }
        return -1;
    }

    static int[] checkScript(Path js) throws IOException {
        List<String> lines = Files.readAllLines(js, StandardCharsets.UTF_8);

        // Names the script defines itself, so local helpers are never reported.
        Set<String> local = new HashSet<>();
        for (String l : lines) {
            Matcher d = LOCAL_DEF.matcher(l);
            while (d.find()) {
                if (d.group(1) != null) {
                    local.add(d.group(1));
                }
                if (d.group(2) != null) {
                    local.add(d.group(2));
                }
            }
        }

        List<String> found = new ArrayList<>();
        // One report per missing binding per file; otherwise a deleted class used 25 times
        // produces 25 identical lines and buries everything else.
        Set<String> reportedBindings = new HashSet<>();
        int unknown = 0;
        int ambiguous = 0;
        boolean inBlockComment = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String stripped = line.trim();
            if (inBlockComment) {
                if (stripped.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (stripped.startsWith("/*")) {
                if (!stripped.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }
            if (stripped.startsWith("//")) {
                continue;
            }

            // Calls already written in explicit-signature form are settled; skip them.
            Set<String> explicit = new HashSet<>();
            Matcher e = EXPLICIT_SIG.matcher(line);
            while (e.find()) {
                explicit.add(e.group(1));
            }

            // A capitalised identifier used as a namespace that KubeJS does not expose at all.
            // This is what catches classes deleted between versions — GuiTextures, FillDirection,
            // MultiblockShapeInfo — which the call checks below miss, because a bare constant
            // reference like GuiTextures.PROGRESS_BAR_ARROW has no parentheses to match on.
            Matcher ns = NAMESPACE_USE.matcher(line);
            while (ns.find()) {
                String id = ns.group(1);
                if (local.contains(id) || BINDINGS.contains(id) || NON_BINDING_GLOBALS.contains(id)
                        || CLASS_METHODS.containsKey(id) || reportedBindings.contains(id)) {
                    continue;
                }
                reportedBindings.add(id);
                found.add(String.format("UNKNOWN   %s:%d  '%s' is not a KubeJS binding in this pack",
                        js.getFileName(), i + 1, id));
                unknown++;
            }

            Matcher sc = STATIC_CALL.matcher(line);
            while (sc.find()) {
                String cls = sc.group(1);
                String method = sc.group(2);
                if (local.contains(cls) || IGNORE.contains(method) || explicit.contains(method)) {
                    continue;
                }
                Map<String, Set<String>> members = CLASS_METHODS.get(cls);
                if (members == null) {
                    continue; // not a binding we indexed; the chain check still covers the name
                }
                Set<String> categories = members.get(method);
                if (categories == null) {
                    found.add(String.format("UNKNOWN   %s:%d  %s.%s() is not declared on %s",
                            js.getFileName(), i + 1, cls, method, cls));
                    unknown++;
                } else {
                    int open = line.indexOf('(', sc.end() - 1);
                    // A single non-literal argument handed to an array/varargs parameter.
                    Set<Integer> arrayArities = ARRAY_FIRST_PARAM.getOrDefault(cls, Map.of())
                            .getOrDefault(method, Set.of());
                    String first = firstArgument(line, open);
                    boolean firstIsArrayValued = false;
                    if (first != null) {
                        Matcher call = Pattern.compile("\\.([a-zA-Z_][A-Za-z0-9_]*)\\s*\\(\\s*\\)\\s*$").matcher(first);
                        firstIsArrayValued = call.find() && RETURNS_ARRAY.contains(call.group(1));
                    }
                    if (firstIsArrayValued && !arrayArities.isEmpty()) {
                        found.add(String.format(
                                "JAVA-ARR  %s:%d  %s.%s(%s ...) hands a Java array to an array parameter; "
                                        + "GregTech's type wrapper only unwraps elements, so copy it into a JS "
                                        + "array first.",
                                js.getFileName(), i + 1, cls, method,
                                first.length() > 40 ? first.substring(0, 40) + "..." : first));
                        ambiguous++;
                    }
                }
                if (categories != null && categories.size() > 1) {
                    // The call's own argument count may already pick a single overload.
                    int args = argCount(line, line.indexOf('(', sc.end() - 1));
                    Set<String> atArity = null;
                    Map<Integer, Set<String>> byArity = CLASS_ARITY.getOrDefault(cls, Map.of()).get(method);
                    if (args >= 0 && byArity != null) {
                        atArity = byArity.get(args);
                    }
                    if (atArity == null || atArity.size() > 1) {
                        found.add(String.format(
                                "AMBIGUOUS %s:%d  %s.%s() overloads take %s as first arg; Rhino picks by shape, "
                                        + "prefer %s['%s(<paramTypes>)'](...)",
                                js.getFileName(), i + 1, cls, method,
                                new java.util.TreeSet<>(categories), cls, method));
                        ambiguous++;
                    }
                }
            }

            Matcher cc = CHAIN_CALL.matcher(line);
            while (cc.find()) {
                String method = cc.group(1);
                if (IGNORE.contains(method) || local.contains(method) || explicit.contains(method)) {
                    continue;
                }
                if (!ALL_METHODS.contains(method)) {
                    found.add(String.format("UNKNOWN   %s:%d  .%s() exists in no indexed type",
                            js.getFileName(), i + 1, method));
                    unknown++;
                }
            }
        }

        if (!found.isEmpty()) {
            found.forEach(System.out::println);
        }
        return new int[] { unknown, ambiguous };
    }
}
