package com.visualizer.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LeetCodeHarnessService {

    private static final Pattern MAIN_PATTERN = Pattern.compile(
            "\\b(public\\s+static\\s+void\\s+main|int\\s+main|void\\s+main|def\\s+main|if\\s+__name__\\s*==\\s*['\"]__main__['\"])\\b"
    );

    public boolean hasMainMethod(String code) {
        if (code == null) return false;
        return MAIN_PATTERN.matcher(code).find();
    }

    public boolean isLeetCodeStyle(String code) {
        if (code == null) return false;
        return !hasMainMethod(code) && (code.contains("class Solution") || code.contains("class solution"));
    }

    /**
     * Augments Java LeetCode code by generating a driver main() method inside Solution
     */
    public String augmentJava(String code, String input) {
        if (!isLeetCodeStyle(code)) {
            return code;
        }

        // Method signature regex inside Solution
        // e.g. public int shortestPath(int n, int[][] edges, String labels, int k)
        Pattern methodPattern = Pattern.compile(
                "public\\s+([A-Za-z0-9_<>\\[\\]]+)\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)"
        );
        Matcher matcher = methodPattern.matcher(code);

        if (!matcher.find()) {
            return code;
        }

        String returnType = matcher.group(1);
        String methodName = matcher.group(2);
        String paramsStr = matcher.group(3).trim();

        List<ParamDef> params = parseParams(paramsStr);
        List<String> rawInputs = parseInputValues(input);

        StringBuilder mainMethod = new StringBuilder();
        mainMethod.append("\n    // Auto-generated driver for LeetCode by CodeTrace\n");
        mainMethod.append("    public static void main(String[] args) {\n");
        mainMethod.append("        Solution __sol = new Solution();\n");

        List<String> argNames = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            ParamDef p = params.get(i);
            String rawVal = i < rawInputs.size() ? rawInputs.get(i) : null;
            String initExpr = generateJavaInitExpr(p.type, rawVal);
            mainMethod.append("        ").append(p.type).append(" ").append(p.name)
                    .append(" = ").append(initExpr).append(";\n");
            argNames.add(p.name);
        }

        if ("void".equalsIgnoreCase(returnType)) {
            mainMethod.append("        __sol.").append(methodName).append("(")
                    .append(String.join(", ", argNames)).append(");\n");
            mainMethod.append("        System.out.println(\"Completed\");\n");
        } else {
            mainMethod.append("        Object __result = __sol.").append(methodName).append("(")
                    .append(String.join(", ", argNames)).append(");\n");
            mainMethod.append("        if (__result instanceof int[] __arr) {\n");
            mainMethod.append("            System.out.println(Arrays.toString(__arr));\n");
            mainMethod.append("        } else if (__result instanceof Object[] __oa) {\n");
            mainMethod.append("            System.out.println(Arrays.deepToString(__oa));\n");
            mainMethod.append("        } else {\n");
            mainMethod.append("            System.out.println(__result);\n");
            mainMethod.append("        }\n");
        }
        mainMethod.append("    }\n");

        // Insert specifically before the closing brace of class Solution
        int solutionClosingBrace = findClassClosingBrace(code, "Solution");
        if (solutionClosingBrace != -1) {
            String prefix = "import java.util.*; import java.io.*; ";
            return prefix + code.substring(0, solutionClosingBrace) + mainMethod + code.substring(solutionClosingBrace);
        }

        return code;
    }

    /**
     * Augments C++ LeetCode code by generating a driver main() method
     */
    public String augmentCpp(String code, String input) {
        if (!isLeetCodeStyle(code)) {
            return code;
        }

        Pattern methodPattern = Pattern.compile(
                "([A-Za-z0-9_<:>]+)\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)\\s*\\{"
        );
        Matcher matcher = methodPattern.matcher(code);

        if (!matcher.find()) {
            return code;
        }

        String returnType = matcher.group(1);
        String methodName = matcher.group(2);
        String paramsStr = matcher.group(3).trim();

        List<ParamDef> params = parseParams(paramsStr);
        List<String> rawInputs = parseInputValues(input);

        StringBuilder mainFunc = new StringBuilder();
        mainFunc.append("\n// Auto-generated driver for LeetCode by CodeTrace\n");
        mainFunc.append("int main() {\n");
        mainFunc.append("    Solution __sol;\n");

        List<String> argNames = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            ParamDef p = params.get(i);
            String rawVal = i < rawInputs.size() ? rawInputs.get(i) : null;
            String initExpr = generateCppInitExpr(p.type, rawVal);
            String cleanType = p.type.replace("&", "").trim();
            mainFunc.append("    ").append(cleanType).append(" ").append(p.name)
                    .append(" = ").append(initExpr).append(";\n");
            argNames.add(p.name);
        }

        mainFunc.append("    auto __result = __sol.").append(methodName).append("(")
                .append(String.join(", ", argNames)).append(");\n");
        mainFunc.append("    cout << \"Result computed\" << endl;\n");
        mainFunc.append("    return 0;\n");
        mainFunc.append("}\n");

        String prefix = "#include <iostream>\n#include <vector>\n#include <string>\n#include <unordered_map>\n#include <queue>\n#include <algorithm>\nusing namespace std;\n#line 1 \"solution.cpp\"\n";
        return prefix + code + "\n" + mainFunc;
    }

    /**
     * Augments Python LeetCode code by appending an execution harness
     */
    public String augmentPython(String code, String input) {
        if (!isLeetCodeStyle(code)) {
            return code;
        }

        List<String> rawInputs = parseInputValues(input);
        String rawInputsJson = "[ " + String.join(", ", rawInputs.stream().map(s -> {
            if (s.startsWith("[") || s.startsWith("{") || s.matches("-?\\d+(\\.\\d+)?") || s.equals("true") || s.equals("false") || s.equals("null")) {
                return s;
            }
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }).toList()) + " ]";

        StringBuilder harness = new StringBuilder();
        harness.append("\n\n# Auto-generated driver for LeetCode by CodeTrace\n");
        harness.append("import inspect, ast\n");
        harness.append("if 'Solution' in locals():\n");
        harness.append("    __sol = Solution()\n");
        harness.append("    __methods = [m for m, f in inspect.getmembers(__sol, predicate=inspect.ismethod) if not m.startswith('_')]\n");
        harness.append("    if __methods:\n");
        harness.append("        __target = getattr(__sol, __methods[0])\n");
        harness.append("        __params = [p for p in inspect.signature(__target).parameters.values() if p.name != 'self']\n");
        harness.append("        __args_data = ").append(rawInputsJson).append("\n");
        harness.append("        __call_args = []\n");
        harness.append("        for i, p in enumerate(__params):\n");
        harness.append("            if i < len(__args_data):\n");
        harness.append("                __call_args.append(__args_data[i])\n");
        harness.append("            else:\n");
        harness.append("                __call_args.append(0)\n");
        harness.append("        __res = __target(*__call_args)\n");
        harness.append("        print('Result:', __res)\n");

        return code + harness;
    }

    private int findClassClosingBrace(String code, String className) {
        Pattern pattern = Pattern.compile("\\bclass\\s+" + className + "\\b");
        Matcher matcher = pattern.matcher(code);
        if (!matcher.find()) {
            return code.lastIndexOf('}');
        }
        int classStart = matcher.start();
        int openBraceIdx = code.indexOf('{', classStart);
        if (openBraceIdx == -1) return code.lastIndexOf('}');

        int braceCount = 1;
        for (int i = openBraceIdx + 1; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return i;
                }
            }
        }
        return code.lastIndexOf('}');
    }

    private List<ParamDef> parseParams(String paramsStr) {
        List<ParamDef> list = new ArrayList<>();
        if (paramsStr.isEmpty()) return list;

        String[] parts = paramsStr.split(",");
        for (String part : parts) {
            String p = part.trim();
            int lastSpace = p.lastIndexOf(' ');
            if (lastSpace != -1) {
                String type = p.substring(0, lastSpace).trim();
                String name = p.substring(lastSpace + 1).trim();
                list.add(new ParamDef(type, name));
            }
        }
        return list;
    }

    private List<String> parseInputValues(String input) {
        List<String> values = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) {
            return values;
        }

        // Format 1: key = val, key2 = val2
        if (input.contains("=")) {
            Pattern kvPattern = Pattern.compile("[A-Za-z0-9_]+\\s*=\\s*(\\[[\\s\\S]*?\\]|\"[^\"]*\"|[^,\\n]+)");
            Matcher m = kvPattern.matcher(input);
            while (m.find()) {
                values.add(m.group(1).trim());
            }
            if (!values.isEmpty()) return values;
        }

        // Format 2: line by line
        String[] lines = input.split("\n");
        for (String line : lines) {
            String l = line.trim();
            if (!l.isEmpty()) {
                values.add(l);
            }
        }
        return values;
    }

    private String generateJavaInitExpr(String type, String raw) {
        String cleanType = type.trim();

        // 2D int array: int[][]
        if (cleanType.equals("int[][]")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String converted = raw.replace('[', '{').replace(']', '}');
                return "new int[][]" + converted;
            }
            return "new int[][]{}";
        }

        // 1D int array: int[]
        if (cleanType.equals("int[]")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                return "new int[]{" + inner + "}";
            }
            return "new int[]{2, 7, 11, 15}";
        }

        if (cleanType.equals("int")) {
            if (raw != null && raw.matches("-?\\d+")) return raw;
            return "0";
        }

        if (cleanType.equals("double") || cleanType.equals("float")) {
            if (raw != null && raw.matches("-?\\d+(\\.\\d+)?")) return raw;
            return "0.0";
        }

        if (cleanType.equals("boolean")) {
            if (raw != null) return String.valueOf(Boolean.parseBoolean(raw));
            return "false";
        }

        if (cleanType.equals("String")) {
            if (raw != null) {
                String s = raw.trim();
                return s.startsWith("\"") && s.endsWith("\"") ? s : "\"" + s.replace("\"", "\\\"") + "\"";
            }
            return "\"\"";
        }

        if (cleanType.startsWith("List<")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                return "new ArrayList<>(List.of(" + inner + "))";
            }
            return "new ArrayList<>()";
        }

        return "null";
    }

    private String generateCppInitExpr(String type, String raw) {
        String cleanType = type.replace("&", "").trim();

        if (cleanType.contains("vector<vector<int>>")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                return raw.replace('[', '{').replace(']', '}');
            }
            return "{}";
        }

        if (cleanType.contains("vector<int>")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                return "{" + inner + "}";
            }
            return "{2, 7, 11, 15}";
        }

        if (cleanType.equals("int")) {
            if (raw != null && raw.matches("-?\\d+")) return raw;
            return "0";
        }

        if (cleanType.equals("string")) {
            if (raw != null) {
                String s = raw.trim();
                return s.startsWith("\"") && s.endsWith("\"") ? s : "\"" + s.replace("\"", "\\\"") + "\"";
            }
            return "\"\"";
        }

        return "{}";
    }

    public record ParamDef(String type, String name) {}
}
