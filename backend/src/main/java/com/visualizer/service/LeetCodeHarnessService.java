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

    public static String stripComments(String code) {
        if (code == null) return "";
        return code.replaceAll("(?s)/\\*.*?\\*/", " ")
                   .replaceAll("//.*", " ");
    }

    public static boolean containsWord(String text, String word) {
        if (text == null) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    public boolean hasMainMethod(String code) {
        if (code == null) return false;
        String clean = stripComments(code);
        return MAIN_PATTERN.matcher(clean).find();
    }

    public boolean isLeetCodeStyle(String code) {
        if (code == null) return false;
        String clean = stripComments(code);
        return !hasMainMethod(clean) && (clean.contains("class Solution") || clean.contains("class solution") || clean.contains("struct Solution"));
    }

    /**
     * Augments Java LeetCode code by generating a driver main() method inside Solution
     */
    public String augmentJava(String code, String input) {
        if (!isLeetCodeStyle(code)) {
            return code;
        }

        String cleanCode = stripComments(code);
        String solutionBody = extractClassBody(cleanCode, "Solution");

        // Method signature regex inside Solution
        // e.g. public int shortestPath(int n, int[][] edges, String labels, int k)
        // e.g. public int diameter(TreeNode root)
        Pattern methodPattern = Pattern.compile(
                "public\\s+([A-Za-z0-9_<>\\[\\]]+)\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)"
        );
        Matcher matcher = methodPattern.matcher(solutionBody);

        if (!matcher.find()) {
            return code;
        }

        String returnType = matcher.group(1);
        String methodName = matcher.group(2);
        String paramsStr = matcher.group(3).trim();

        List<ParamDef> params = parseParams(paramsStr);
        ParsedInput parsed = parseInput(input);

        boolean hasListNode = cleanCode.contains("class ListNode") || cleanCode.contains("struct ListNode");
        boolean hasTreeNode = cleanCode.contains("class TreeNode") || cleanCode.contains("struct TreeNode");
        boolean hasNode = cleanCode.contains("class Node") || cleanCode.contains("struct Node");
        boolean usesNode = containsWord(paramsStr, "Node") || containsWord(returnType, "Node");
        boolean isNaryTree = cleanCode.contains("children") || usesNode;
        boolean userTreeNodeHasLeft = hasTreeNode && userTreeNodeHasLeft(cleanCode);

        StringBuilder mainMethod = new StringBuilder();
        mainMethod.append("\n    // Auto-generated driver for LeetCode by CodeTrace\n");
        mainMethod.append("    public static void main(String[] args) {\n");
        mainMethod.append("        Solution __sol = new Solution();\n");

        List<String> argNames = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            ParamDef p = params.get(i);
            String rawVal = parsed.getValueFor(p.name, i);
            String initExpr = generateJavaInitExpr(p.type, rawVal, isNaryTree);
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
            if (containsWord(cleanCode, "ListNode")) {
                mainMethod.append("        } else if (__result instanceof ListNode __ln) {\n");
                mainMethod.append("            StringBuilder __sb = new StringBuilder();\n");
                mainMethod.append("            while (__ln != null) {\n");
                mainMethod.append("                __sb.append(__ln.val);\n");
                mainMethod.append("                if (__ln.next != null) __sb.append(\" -> \");\n");
                mainMethod.append("                __ln = __ln.next;\n");
                mainMethod.append("            }\n");
                mainMethod.append("            System.out.println(__sb.toString());\n");
            }
            if (containsWord(cleanCode, "TreeNode")) {
                mainMethod.append("        } else if (__result instanceof TreeNode __tn) {\n");
                mainMethod.append("            System.out.println(\"TreeNode(val=\" + __tn.val + \")\");\n");
            }
            if (usesNode || containsWord(cleanCode, "Node")) {
                mainMethod.append("        } else if (__result instanceof Node __nd) {\n");
                mainMethod.append("            System.out.println(\"Node(val=\" + __nd.val + \")\");\n");
            }
            mainMethod.append("        } else {\n");
            mainMethod.append("            System.out.println(__result);\n");
            mainMethod.append("        }\n");
        }
        mainMethod.append("    }\n\n");

        // Helper static builder for ListNode
        if (containsWord(cleanCode, "ListNode")) {
            mainMethod.append("    public static ListNode __buildList(int[] vals) {\n");
            mainMethod.append("        if (vals == null || vals.length == 0) return null;\n");
            mainMethod.append("        ListNode dummy = new ListNode(0);\n");
            mainMethod.append("        ListNode curr = dummy;\n");
            mainMethod.append("        for (int v : vals) {\n");
            mainMethod.append("            curr.next = new ListNode(v);\n");
            mainMethod.append("            curr = curr.next;\n");
            mainMethod.append("        }\n");
            mainMethod.append("        return dummy.next;\n");
            mainMethod.append("    }\n\n");
        }

        // Helper static builder for Node (N-ary tree)
        if (usesNode || containsWord(cleanCode, "Node")) {
            mainMethod.append("    public static Node __buildNaryNode(Integer[] vals) {\n");
            mainMethod.append("        if (vals == null || vals.length == 0 || vals[0] == null) return null;\n");
            mainMethod.append("        Node root = new Node(vals[0]);\n");
            mainMethod.append("        Queue<Node> queue = new LinkedList<>();\n");
            mainMethod.append("        queue.offer(root);\n");
            mainMethod.append("        int i = 1;\n");
            mainMethod.append("        if (i < vals.length && vals[i] == null) {\n");
            mainMethod.append("            i++;\n");
            mainMethod.append("        }\n");
            mainMethod.append("        while (!queue.isEmpty() && i < vals.length) {\n");
            mainMethod.append("            Node parent = queue.poll();\n");
            mainMethod.append("            if (parent.children == null) parent.children = new ArrayList<>();\n");
            mainMethod.append("            while (i < vals.length && vals[i] != null) {\n");
            mainMethod.append("                Node child = new Node(vals[i]);\n");
            mainMethod.append("                if (child.children == null) child.children = new ArrayList<>();\n");
            mainMethod.append("                parent.children.add(child);\n");
            mainMethod.append("                queue.offer(child);\n");
            mainMethod.append("                i++;\n");
            mainMethod.append("            }\n");
            mainMethod.append("            i++;\n");
            mainMethod.append("        }\n");
            mainMethod.append("        return root;\n");
            mainMethod.append("    }\n\n");
        }

        // Helper static builders for TreeNode
        if (containsWord(cleanCode, "TreeNode")) {
            boolean shouldGenerateBinaryBuilder = !hasTreeNode || userTreeNodeHasLeft;
            boolean shouldGenerateNaryBuilder = !hasTreeNode || isNaryTree;

            if (shouldGenerateBinaryBuilder) {
                mainMethod.append("    public static TreeNode __buildTree(Integer[] vals) {\n");
                mainMethod.append("        if (vals == null || vals.length == 0 || vals[0] == null) return null;\n");
                mainMethod.append("        TreeNode root = new TreeNode(vals[0]);\n");
                mainMethod.append("        Queue<TreeNode> queue = new LinkedList<>();\n");
                mainMethod.append("        queue.offer(root);\n");
                mainMethod.append("        int i = 1;\n");
                mainMethod.append("        while (!queue.isEmpty() && i < vals.length) {\n");
                mainMethod.append("            TreeNode curr = queue.poll();\n");
                mainMethod.append("            if (i < vals.length && vals[i] != null) {\n");
                mainMethod.append("                curr.left = new TreeNode(vals[i]);\n");
                mainMethod.append("                queue.offer(curr.left);\n");
                mainMethod.append("            }\n");
                mainMethod.append("            i++;\n");
                mainMethod.append("            if (i < vals.length && vals[i] != null) {\n");
                mainMethod.append("                curr.right = new TreeNode(vals[i]);\n");
                mainMethod.append("                queue.offer(curr.right);\n");
                mainMethod.append("            }\n");
                mainMethod.append("            i++;\n");
                mainMethod.append("        }\n");
                mainMethod.append("        return root;\n");
                mainMethod.append("    }\n\n");
            }

            if (shouldGenerateNaryBuilder) {
                mainMethod.append("    public static TreeNode __buildNaryTree(Integer[] vals) {\n");
                mainMethod.append("        if (vals == null || vals.length == 0 || vals[0] == null) return null;\n");
                mainMethod.append("        TreeNode root = new TreeNode(vals[0]);\n");
                mainMethod.append("        Queue<TreeNode> queue = new LinkedList<>();\n");
                mainMethod.append("        queue.offer(root);\n");
                mainMethod.append("        int i = 1;\n");
                mainMethod.append("        if (i < vals.length && vals[i] == null) {\n");
                mainMethod.append("            i++;\n");
                mainMethod.append("        }\n");
                mainMethod.append("        while (!queue.isEmpty() && i < vals.length) {\n");
                mainMethod.append("            TreeNode parent = queue.poll();\n");
                mainMethod.append("            if (parent.children == null) parent.children = new ArrayList<>();\n");
                mainMethod.append("            while (i < vals.length && vals[i] != null) {\n");
                mainMethod.append("                TreeNode child = new TreeNode(vals[i]);\n");
                mainMethod.append("                if (child.children == null) child.children = new ArrayList<>();\n");
                mainMethod.append("                parent.children.add(child);\n");
                mainMethod.append("                queue.offer(child);\n");
                mainMethod.append("                i++;\n");
                mainMethod.append("            }\n");
                mainMethod.append("            i++;\n");
                mainMethod.append("        }\n");
                mainMethod.append("        return root;\n");
                mainMethod.append("    }\n\n");
            }
        }

        // Append standard LeetCode ListNode / TreeNode definitions if not already defined in cleanCode
        StringBuilder definitions = new StringBuilder();
        if (containsWord(cleanCode, "ListNode") && !hasListNode) {
            definitions.append("\nclass ListNode {\n")
                    .append("    public int val;\n")
                    .append("    public ListNode next;\n")
                    .append("    public ListNode() {}\n")
                    .append("    public ListNode(int val) { this.val = val; }\n")
                    .append("    public ListNode(int val, ListNode next) { this.val = val; this.next = next; }\n")
                    .append("}\n");
        }
        if (containsWord(cleanCode, "TreeNode") && !hasTreeNode) {
            definitions.append("\nclass TreeNode {\n")
                    .append("    public int val;\n")
                    .append("    public TreeNode left;\n")
                    .append("    public TreeNode right;\n")
                    .append("    public List<TreeNode> children = new ArrayList<>();\n")
                    .append("    public TreeNode() {}\n")
                    .append("    public TreeNode(int val) { this.val = val; }\n")
                    .append("    public TreeNode(int val, List<TreeNode> children) { this.val = val; this.children = children; }\n")
                    .append("    public TreeNode(int val, TreeNode left, TreeNode right) {\n")
                    .append("        this.val = val;\n")
                    .append("        this.left = left;\n")
                    .append("        this.right = right;\n")
                    .append("    }\n")
                    .append("}\n");
        }
        if ((usesNode || containsWord(cleanCode, "Node")) && !hasNode) {
            definitions.append("\nclass Node {\n")
                    .append("    public int val;\n")
                    .append("    public List<Node> children = new ArrayList<>();\n")
                    .append("    public Node() {}\n")
                    .append("    public Node(int _val) { val = _val; }\n")
                    .append("    public Node(int _val, List<Node> _children) { val = _val; children = _children; }\n")
                    .append("}\n");
        }

        // Insert specifically before the closing brace of class Solution
        int solutionClosingBrace = findClassClosingBrace(code, "Solution");
        if (solutionClosingBrace != -1) {
            String prefix = "import java.util.*; import java.io.*; ";
            return prefix + code.substring(0, solutionClosingBrace) + mainMethod + code.substring(solutionClosingBrace) + definitions;
        }

        return code + definitions;
    }

    /**
     * Augments C++ LeetCode code by generating a driver main() method
     */
    public String augmentCpp(String code, String input) {
        if (!isLeetCodeStyle(code)) {
            return code;
        }

        String cleanCode = stripComments(code);
        String solutionBody = extractClassBody(cleanCode, "Solution");

        Pattern methodPattern = Pattern.compile(
                "([A-Za-z0-9_<:>\\*]+)\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)\\s*\\{"
        );
        Matcher matcher = methodPattern.matcher(solutionBody);

        if (!matcher.find()) {
            return code;
        }

        String returnType = matcher.group(1).trim();
        String methodName = matcher.group(2).trim();
        String paramsStr = matcher.group(3).trim();

        List<ParamDef> params = parseParams(paramsStr);
        ParsedInput parsed = parseInput(input);

        boolean hasListNode = cleanCode.contains("struct ListNode") || cleanCode.contains("class ListNode");
        boolean hasTreeNode = cleanCode.contains("struct TreeNode") || cleanCode.contains("class TreeNode");
        boolean hasNode = cleanCode.contains("struct Node") || cleanCode.contains("class Node");
        boolean usesNode = paramsStr.matches(".*\\bNode\\b.*") || returnType.matches(".*\\bNode\\b.*");
        boolean isNaryTree = cleanCode.contains("children") || usesNode;

        StringBuilder helpers = new StringBuilder();

        // Injected definitions for C++
        if (containsWord(cleanCode, "ListNode") && !hasListNode) {
            helpers.append("struct ListNode {\n")
                    .append("    int val;\n")
                    .append("    ListNode *next;\n")
                    .append("    ListNode() : val(0), next(nullptr) {}\n")
                    .append("    ListNode(int x) : val(x), next(nullptr) {}\n")
                    .append("    ListNode(int x, ListNode *next) : val(x), next(next) {}\n")
                    .append("};\n\n");
        }
        if (containsWord(cleanCode, "TreeNode") && !hasTreeNode) {
            helpers.append("struct TreeNode {\n")
                    .append("    int val;\n")
                    .append("    TreeNode *left;\n")
                    .append("    TreeNode *right;\n")
                    .append("    vector<TreeNode*> children;\n")
                    .append("    TreeNode() : val(0), left(nullptr), right(nullptr) {}\n")
                    .append("    TreeNode(int x) : val(x), left(nullptr), right(nullptr) {}\n")
                    .append("    TreeNode(int x, TreeNode *left, TreeNode *right) : val(x), left(left), right(right) {}\n")
                    .append("    TreeNode(int x, vector<TreeNode*> _children) : val(x), left(nullptr), right(nullptr), children(_children) {}\n")
                    .append("};\n\n");
        }
        if ((usesNode || containsWord(cleanCode, "Node")) && !hasNode) {
            helpers.append("class Node {\n")
                    .append("public:\n")
                    .append("    int val;\n")
                    .append("    vector<Node*> children;\n")
                    .append("    Node() : val(0) {}\n")
                    .append("    Node(int _val) : val(_val) {}\n")
                    .append("    Node(int _val, vector<Node*> _children) : val(_val), children(_children) {}\n")
                    .append("};\n\n");
        }

        // Builders for C++
        if (containsWord(cleanCode, "ListNode")) {
            helpers.append("ListNode* __buildList(const vector<int>& vals) {\n")
                    .append("    if (vals.empty()) return nullptr;\n")
                    .append("    ListNode dummy(0);\n")
                    .append("    ListNode* curr = &dummy;\n")
                    .append("    for (int v : vals) {\n")
                    .append("        curr->next = new ListNode(v);\n")
                    .append("        curr = curr->next;\n")
                    .append("    }\n")
                    .append("    return dummy.next;\n")
                    .append("}\n\n");
        }
        if (containsWord(cleanCode, "TreeNode")) {
            helpers.append("TreeNode* __buildTree(const vector<string>& vals) {\n")
                    .append("    if (vals.empty() || vals[0] == \"null\" || vals[0] == \"None\") return nullptr;\n")
                    .append("    TreeNode* root = new TreeNode(stoi(vals[0]));\n")
                    .append("    queue<TreeNode*> q;\n")
                    .append("    q.push(root);\n")
                    .append("    size_t i = 1;\n")
                    .append("    while (!q.empty() && i < vals.size()) {\n")
                    .append("        TreeNode* curr = q.front();\n")
                    .append("        q.pop();\n")
                    .append("        if (i < vals.size() && vals[i] != \"null\" && vals[i] != \"None\") {\n")
                    .append("            curr->left = new TreeNode(stoi(vals[i]));\n")
                    .append("            q.push(curr->left);\n")
                    .append("        }\n")
                    .append("        i++;\n")
                    .append("        if (i < vals.size() && vals[i] != \"null\" && vals[i] != \"None\") {\n")
                    .append("            curr->right = new TreeNode(stoi(vals[i]));\n")
                    .append("            q.push(curr->right);\n")
                    .append("        }\n")
                    .append("        i++;\n")
                    .append("    }\n")
                    .append("    return root;\n")
                    .append("}\n\n");

            helpers.append("TreeNode* __buildNaryTree(const vector<string>& vals) {\n")
                    .append("    if (vals.empty() || vals[0] == \"null\" || vals[0] == \"None\") return nullptr;\n")
                    .append("    TreeNode* root = new TreeNode(stoi(vals[0]));\n")
                    .append("    queue<TreeNode*> q;\n")
                    .append("    q.push(root);\n")
                    .append("    size_t i = 1;\n")
                    .append("    if (i < vals.size() && (vals[i] == \"null\" || vals[i] == \"None\")) i++;\n")
                    .append("    while (!q.empty() && i < vals.size()) {\n")
                    .append("        TreeNode* parent = q.front();\n")
                    .append("        q.pop();\n")
                    .append("        while (i < vals.size() && vals[i] != \"null\" && vals[i] != \"None\") {\n")
                    .append("            TreeNode* child = new TreeNode(stoi(vals[i]));\n")
                    .append("            parent->children.push_back(child);\n")
                    .append("            q.push(child);\n")
                    .append("            i++;\n")
                    .append("        }\n")
                    .append("        i++;\n")
                    .append("    }\n")
                    .append("    return root;\n")
                    .append("}\n\n");
        }

        if (usesNode || containsWord(cleanCode, "Node")) {
            helpers.append("Node* __buildNaryNode(const vector<string>& vals) {\n")
                    .append("    if (vals.empty() || vals[0] == \"null\" || vals[0] == \"None\") return nullptr;\n")
                    .append("    Node* root = new Node(stoi(vals[0]));\n")
                    .append("    queue<Node*> q;\n")
                    .append("    q.push(root);\n")
                    .append("    size_t i = 1;\n")
                    .append("    if (i < vals.size() && (vals[i] == \"null\" || vals[i] == \"None\")) i++;\n")
                    .append("    while (!q.empty() && i < vals.size()) {\n")
                    .append("        Node* parent = q.front();\n")
                    .append("        q.pop();\n")
                    .append("        while (i < vals.size() && vals[i] != \"null\" && vals[i] != \"None\") {\n")
                    .append("            Node* child = new Node(stoi(vals[i]));\n")
                    .append("            parent->children.push_back(child);\n")
                    .append("            q.push(child);\n")
                    .append("            i++;\n")
                    .append("        }\n")
                    .append("        i++;\n")
                    .append("    }\n")
                    .append("    return root;\n")
                    .append("}\n\n");
        }

        StringBuilder mainFunc = new StringBuilder();
        mainFunc.append("\n// Auto-generated driver for LeetCode by CodeTrace\n");
        mainFunc.append("int main() {\n");
        mainFunc.append("    Solution __sol;\n");

        List<String> argNames = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            ParamDef p = params.get(i);
            String rawVal = parsed.getValueFor(p.name, i);
            String initExpr = generateCppInitExpr(p.type, rawVal, isNaryTree);
            String cleanType = p.type.replace("&", "").trim();
            mainFunc.append("    ").append(cleanType).append(" ").append(p.name)
                    .append(" = ").append(initExpr).append(";\n");
            argNames.add(p.name);
        }

        if ("void".equalsIgnoreCase(returnType)) {
            mainFunc.append("    __sol.").append(methodName).append("(")
                    .append(String.join(", ", argNames)).append(");\n");
            mainFunc.append("    cout << \"Completed\" << endl;\n");
        } else if (containsWord(returnType, "ListNode")) {
            mainFunc.append("    ListNode* __result = __sol.").append(methodName).append("(")
                    .append(String.join(", ", argNames)).append(");\n");
            mainFunc.append("    ListNode* __curr = __result;\n");
            mainFunc.append("    while (__curr) {\n");
            mainFunc.append("        cout << __curr->val;\n");
            mainFunc.append("        if (__curr->next) cout << \" -> \";\n");
            mainFunc.append("        __curr = __curr->next;\n");
            mainFunc.append("    }\n");
            mainFunc.append("    cout << endl;\n");
        } else if (returnType.contains("vector")) {
            mainFunc.append("    auto __result = __sol.").append(methodName).append("(")
                    .append(String.join(", ", argNames)).append(");\n");
            mainFunc.append("    cout << \"[\";\n");
            mainFunc.append("    for (size_t i = 0; i < __result.size(); i++) {\n");
            mainFunc.append("        cout << __result[i] << (i + 1 < __result.size() ? \", \" : \"\");\n");
            mainFunc.append("    }\n");
            mainFunc.append("    cout << \"]\" << endl;\n");
        } else if (containsWord(returnType, "TreeNode")) {
            mainFunc.append("    TreeNode* __result = __sol.").append(methodName).append("(")
                    .append(String.join(", ", argNames)).append(");\n");
            mainFunc.append("    if (__result) cout << \"TreeNode(val=\" << __result->val << \")\" << endl;\n");
            mainFunc.append("    else cout << \"null\" << endl;\n");
        } else if (containsWord(returnType, "Node")) {
            mainFunc.append("    Node* __result = __sol.").append(methodName).append("(")
                    .append(String.join(", ", argNames)).append(");\n");
            mainFunc.append("    if (__result) cout << \"Node(val=\" << __result->val << \")\" << endl;\n");
            mainFunc.append("    else cout << \"null\" << endl;\n");
        } else {
            mainFunc.append("    auto __result = __sol.").append(methodName).append("(")
                    .append(String.join(", ", argNames)).append(");\n");
            mainFunc.append("    cout << \"Result: \" << __result << endl;\n");
        }
        mainFunc.append("    return 0;\n");
        mainFunc.append("}\n");

        String prefix = "#include <iostream>\n#include <vector>\n#include <string>\n#include <unordered_map>\n#include <queue>\n#include <algorithm>\nusing namespace std;\n#line 1 \"solution.cpp\"\n";
        return prefix + helpers + code + "\n" + mainFunc;
    }

    /**
     * Augments Python LeetCode code by appending an execution harness
     */
    public String augmentPython(String code, String input) {
        if (!isLeetCodeStyle(code)) {
            return code;
        }

        String cleanCode = stripComments(code);
        boolean isNaryTree = cleanCode.contains("children");

        ParsedInput parsed = parseInput(input);
        List<String> rawInputs = parsed.getPositional();
        String rawInputsJson = "[ " + String.join(", ", rawInputs.stream().map(s -> {
            if (s.startsWith("[") || s.startsWith("{") || s.matches("-?\\d+(\\.\\d+)?") || s.equals("true") || s.equals("false") || s.equals("null")) {
                return s;
            }
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }).toList()) + " ]";

        StringBuilder header = new StringBuilder();
        header.append("from typing import *\n");
        header.append("import inspect, ast\n");
        header.append("null = None\n");
        header.append("true = True\n");
        header.append("false = False\n\n");

        header.append("if 'ListNode' not in locals():\n");
        header.append("    class ListNode:\n");
        header.append("        def __init__(self, val=0, next=None):\n");
        header.append("            self.val = val\n");
        header.append("            self.next = next\n\n");

        header.append("if 'TreeNode' not in locals():\n");
        header.append("    class TreeNode:\n");
        header.append("        def __init__(self, val=0, left=None, right=None, children=None):\n");
        header.append("            self.val = val\n");
        header.append("            self.left = left\n");
        header.append("            self.right = right\n");
        header.append("            self.children = children if children is not None else []\n\n");

        header.append("if 'Node' not in locals():\n");
        header.append("    class Node:\n");
        header.append("        def __init__(self, val=None, children=None):\n");
        header.append("            self.val = val\n");
        header.append("            self.children = children if children is not None else []\n\n");

        StringBuilder harness = new StringBuilder();
        harness.append("\n\n# Auto-generated driver for LeetCode by CodeTrace\n");
        harness.append("def __build_list(vals):\n");
        harness.append("    if not vals:\n");
        harness.append("        return None\n");
        harness.append("    dummy = ListNode(0)\n");
        harness.append("    curr = dummy\n");
        harness.append("    for v in vals:\n");
        harness.append("        curr.next = ListNode(v)\n");
        harness.append("        curr = curr.next\n");
        harness.append("    return dummy.next\n\n");

        harness.append("def __build_tree(vals):\n");
        harness.append("    if not vals or vals[0] is None:\n");
        harness.append("        return None\n");
        harness.append("    from collections import deque\n");
        harness.append("    root = TreeNode(vals[0])\n");
        harness.append("    queue = deque([root])\n");
        harness.append("    i = 1\n");
        harness.append("    while queue and i < len(vals):\n");
        harness.append("        curr = queue.popleft()\n");
        harness.append("        if i < len(vals) and vals[i] is not None:\n");
        harness.append("            curr.left = TreeNode(vals[i])\n");
        harness.append("            queue.append(curr.left)\n");
        harness.append("        i += 1\n");
        harness.append("        if i < len(vals) and vals[i] is not None:\n");
        harness.append("            curr.right = TreeNode(vals[i])\n");
        harness.append("            queue.append(curr.right)\n");
        harness.append("        i += 1\n");
        harness.append("    return root\n\n");

        harness.append("def __build_nary_tree(vals):\n");
        harness.append("    if not vals or vals[0] is None:\n");
        harness.append("        return None\n");
        harness.append("    from collections import deque\n");
        harness.append("    node_cls = Node if 'Node' in globals() or 'Node' in locals() else TreeNode\n");
        harness.append("    root = node_cls(vals[0])\n");
        harness.append("    queue = deque([root])\n");
        harness.append("    i = 1\n");
        harness.append("    if i < len(vals) and vals[i] is None:\n");
        harness.append("        i += 1\n");
        harness.append("    while queue and i < len(vals):\n");
        harness.append("        parent = queue.popleft()\n");
        harness.append("        if getattr(parent, 'children', None) is None:\n");
        harness.append("            parent.children = []\n");
        harness.append("        while i < len(vals) and vals[i] is not None:\n");
        harness.append("            child = node_cls(vals[i])\n");
        harness.append("            if getattr(child, 'children', None) is None:\n");
        harness.append("                child.children = []\n");
        harness.append("            parent.children.append(child)\n");
        harness.append("            queue.append(child)\n");
        harness.append("            i += 1\n");
        harness.append("        i += 1\n");
        harness.append("    return root\n\n");

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
        harness.append("                val = __args_data[i]\n");
        harness.append("                p_name = p.name.lower()\n");
        harness.append("                ann = str(p.annotation).lower() if p.annotation != inspect.Parameter.empty else ''\n");
        harness.append("                if ('head' in p_name or 'listnode' in ann) and isinstance(val, list):\n");
        harness.append("                    __call_args.append(__build_list(val))\n");
        harness.append("                elif ('root' in p_name or 'treenode' in ann or 'node' in ann) and isinstance(val, list):\n");
        harness.append("                    if ").append(isNaryTree ? "True" : "False").append(" or 'node' in ann:\n");
        harness.append("                        __call_args.append(__build_nary_tree(val))\n");
        harness.append("                    else:\n");
        harness.append("                        __call_args.append(__build_tree(val))\n");
        harness.append("                else:\n");
        harness.append("                    __call_args.append(val)\n");
        harness.append("            else:\n");
        harness.append("                __call_args.append(0)\n");
        harness.append("        __res = __target(*__call_args)\n");
        harness.append("        if isinstance(__res, ListNode):\n");
        harness.append("            __p = __res\n");
        harness.append("            __vals = []\n");
        harness.append("            while __p:\n");
        harness.append("                __vals.append(str(__p.val))\n");
        harness.append("                __p = __p.next\n");
        harness.append("            print(' -> '.join(__vals))\n");
        harness.append("        else:\n");
        harness.append("            print('Result:', __res)\n");

        return header + code + harness;
    }

    private String extractClassBody(String code, String className) {
        Pattern pattern = Pattern.compile("\\bclass\\s+" + className + "\\b");
        Matcher matcher = pattern.matcher(code);
        if (!matcher.find()) return code;
        int classStart = matcher.start();
        int openBraceIdx = code.indexOf('{', classStart);
        if (openBraceIdx == -1) return code;
        int closeBraceIdx = findClassClosingBrace(code, className);
        if (closeBraceIdx > openBraceIdx) {
            return code.substring(openBraceIdx + 1, closeBraceIdx);
        }
        return code;
    }

    private boolean userTreeNodeHasLeft(String cleanCode) {
        Pattern tnPattern = Pattern.compile("\\b(?:class|struct)\\s+TreeNode\\b[^{]*\\{([\\s\\S]*?)\\}");
        Matcher m = tnPattern.matcher(cleanCode);
        if (m.find()) {
            String body = m.group(1);
            return body.contains("left");
        }
        return false;
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
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;

        for (int i = openBraceIdx + 1; i < code.length(); i++) {
            char c = code.charAt(i);
            char prev = i > 0 ? code.charAt(i - 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (prev == '*' && c == '/') inBlockComment = false;
                continue;
            }
            if (inString) {
                if (c == '"' && prev != '\\') inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\'' && prev != '\\') inChar = false;
                continue;
            }

            if (c == '/' && i + 1 < code.length()) {
                char next = code.charAt(i + 1);
                if (next == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                } else if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '{') {
                braceCount++;
            } else if (c == '}') {
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
                while (name.startsWith("*") || name.startsWith("&")) {
                    type = type + name.charAt(0);
                    name = name.substring(1).trim();
                }
                list.add(new ParamDef(type, name));
            }
        }
        return list;
    }

    public static class ParsedInput {
        private final Map<String, String> named = new LinkedHashMap<>();
        private final List<String> positional = new ArrayList<>();

        public void put(String key, String value) {
            if (key != null && !key.trim().isEmpty()) {
                String normalizedKey = key.trim()
                        .replaceAll("^(?:int|long|double|float|char|boolean|String|vector<[^>]+>|\\[\\]|\\*)+\\s*", "")
                        .replaceAll("[\\[\\]]", "")
                        .trim()
                        .toLowerCase();
                named.put(normalizedKey, value.trim());
            }
            positional.add(value.trim());
        }

        public String getValueFor(String paramName, int index) {
            if (paramName != null) {
                String cleanName = paramName.replaceAll("[\\[\\]]", "").trim().toLowerCase();
                if (named.containsKey(cleanName)) {
                    return named.get(cleanName);
                }
            }
            if (index < positional.size()) {
                return positional.get(index);
            }
            return null;
        }

        public List<String> getPositional() {
            return positional;
        }
    }

    private ParsedInput parseInput(String input) {
        ParsedInput parsed = new ParsedInput();
        if (input == null || input.trim().isEmpty()) {
            return parsed;
        }

        if (input.contains("=")) {
            Map<String, String> kvs = parseKeyValues(input);
            if (!kvs.isEmpty()) {
                for (Map.Entry<String, String> entry : kvs.entrySet()) {
                    parsed.put(entry.getKey(), entry.getValue());
                }
                return parsed;
            }
        }

        // Line-by-line fallback
        String[] lines = input.split("\n");
        for (String line : lines) {
            String l = line.trim();
            if (!l.isEmpty()) {
                parsed.put(null, l);
            }
        }
        return parsed;
    }

    private Map<String, String> parseKeyValues(String text) {
        Map<String, String> kvs = new LinkedHashMap<>();
        int i = 0;
        int n = text.length();

        while (i < n) {
            int eqIdx = text.indexOf('=', i);
            if (eqIdx == -1) {
                break;
            }

            // Key is backwards from eqIdx to previous comma, newline, or start
            int keyStart = eqIdx - 1;
            while (keyStart >= i && text.charAt(keyStart) != ',' && text.charAt(keyStart) != '\n') {
                keyStart--;
            }
            String key = text.substring(keyStart + 1, eqIdx).trim();

            // Value starts after eqIdx
            int valStart = eqIdx + 1;
            while (valStart < n && (text.charAt(valStart) == ' ' || text.charAt(valStart) == '\t')) {
                valStart++;
            }

            if (valStart >= n) {
                break;
            }

            char firstChar = text.charAt(valStart);
            int valEnd = valStart;

            if (firstChar == '[') {
                int bracketDepth = 0;
                while (valEnd < n) {
                    char c = text.charAt(valEnd);
                    if (c == '[') bracketDepth++;
                    else if (c == ']') {
                        bracketDepth--;
                        if (bracketDepth == 0) {
                            valEnd++;
                            break;
                        }
                    }
                    valEnd++;
                }
            } else if (firstChar == '{') {
                int braceDepth = 0;
                while (valEnd < n) {
                    char c = text.charAt(valEnd);
                    if (c == '{') braceDepth++;
                    else if (c == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            valEnd++;
                            break;
                        }
                    }
                    valEnd++;
                }
            } else if (firstChar == '"') {
                valEnd++;
                while (valEnd < n) {
                    char c = text.charAt(valEnd);
                    char prev = text.charAt(valEnd - 1);
                    if (c == '"' && prev != '\\') {
                        valEnd++;
                        break;
                    }
                    valEnd++;
                }
            } else {
                // Scalar: read until ',' or '\n'
                while (valEnd < n && text.charAt(valEnd) != ',' && text.charAt(valEnd) != '\n') {
                    valEnd++;
                }
            }

            String val = text.substring(valStart, valEnd).trim();
            if (!key.isEmpty()) {
                kvs.put(key, val);
            }

            i = valEnd;
            while (i < n && (text.charAt(i) == ',' || text.charAt(i) == '\n' || text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
                i++;
            }
        }

        return kvs;
    }

    private String generateJavaInitExpr(String type, String raw, boolean isNaryTree) {
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
                return inner.isEmpty() ? "new int[]{}" : "new int[]{" + inner + "}";
            }
            return "new int[]{}";
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

        // LeetCode ListNode
        if (cleanType.equals("ListNode")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                return inner.isEmpty() ? "null" : "__buildList(new int[]{" + inner + "})";
            }
            return "null";
        }

        // LeetCode TreeNode
        if (cleanType.equals("TreeNode")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                if (inner.isEmpty()) return "null";
                String formatted = inner.replaceAll("(?i)\\bnone\\b", "null").replaceAll("(?i)\\bNULL\\b", "null");
                String builderName = isNaryTree ? "__buildNaryTree" : "__buildTree";
                return builderName + "(new Integer[]{" + formatted + "})";
            }
            return "null";
        }

        // LeetCode Node (N-ary tree)
        if (cleanType.equals("Node")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                if (inner.isEmpty()) return "null";
                String formatted = inner.replaceAll("(?i)\\bnone\\b", "null").replaceAll("(?i)\\bNULL\\b", "null");
                return "__buildNaryNode(new Integer[]{" + formatted + "})";
            }
            return "null";
        }

        return "null";
    }

    private String generateCppInitExpr(String type, String raw, boolean isNaryTree) {
        String cleanType = type.replace("&", "").trim();

        if (cleanType.contains("ListNode*") || cleanType.contains("ListNode *")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                return inner.isEmpty() ? "nullptr" : "__buildList(vector<int>{" + inner + "})";
            }
            return "nullptr";
        }

        if (cleanType.contains("TreeNode*") || cleanType.contains("TreeNode *")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                if (inner.isEmpty()) return "nullptr";
                String[] parts = inner.split(",");
                List<String> quoted = new ArrayList<>();
                for (String p : parts) {
                    String t = p.trim();
                    if (t.equalsIgnoreCase("null") || t.equalsIgnoreCase("none")) {
                        quoted.add("\"null\"");
                    } else {
                        quoted.add("\"" + t + "\"");
                    }
                }
                String builder = isNaryTree ? "__buildNaryTree" : "__buildTree";
                return builder + "(vector<string>{" + String.join(", ", quoted) + "})";
            }
            return "nullptr";
        }

        if (cleanType.contains("Node*") || cleanType.contains("Node *")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                if (inner.isEmpty()) return "nullptr";
                String[] parts = inner.split(",");
                List<String> quoted = new ArrayList<>();
                for (String p : parts) {
                    String t = p.trim();
                    if (t.equalsIgnoreCase("null") || t.equalsIgnoreCase("none")) {
                        quoted.add("\"null\"");
                    } else {
                        quoted.add("\"" + t + "\"");
                    }
                }
                return "__buildNaryNode(vector<string>{" + String.join(", ", quoted) + "})";
            }
            return "nullptr";
        }

        if (cleanType.contains("vector<vector<int>>")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                return raw.replace('[', '{').replace(']', '}');
            }
            return "{}";
        }

        if (cleanType.contains("vector<int>")) {
            if (raw != null && raw.startsWith("[") && raw.endsWith("]")) {
                String inner = raw.substring(1, raw.length() - 1).trim();
                return inner.isEmpty() ? "{}" : "{" + inner + "}";
            }
            return "{}";
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
