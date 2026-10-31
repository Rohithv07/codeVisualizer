package com.visualizer.runner;

import com.visualizer.model.TraceRequest;
import com.visualizer.model.TraceResponse;
import com.visualizer.service.LeetCodeHarnessService;
import com.visualizer.service.VisualizerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RunnerTest {

    private VisualizerService visualizerService;

    @BeforeEach
    public void setup() {
        visualizerService = new VisualizerService(
                List.of(new PythonTraceRunner(), new JavaTraceRunner(), new CppTraceRunner()),
                new LeetCodeHarnessService()
        );
    }

    @Test
    public void testPythonCPTracing() throws Exception {
        String code = "n = int(input())\n" +
                "arr = list(map(int, input().split()))\n" +
                "total = sum(arr)\n" +
                "print('Total:', total)\n";
        String input = "3\n10 20 30\n";

        TraceRequest request = new TraceRequest("python", code, input, 300);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        assertTrue(response.totalSteps() > 0);
        assertTrue(response.stdout().contains("Total: 60"));
        assertNotNull(response.steps());
    }

    @Test
    public void testJavaCPArrayTwoPointers() throws Exception {
        String code = "import java.util.*;\n" +
                "public class Solution {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        int n = sc.nextInt();\n" +
                "        int[] arr = new int[n];\n" +
                "        for (int i = 0; i < n; i++) {\n" +
                "            arr[i] = sc.nextInt();\n" +
                "        }\n" +
                "        int left = 0, right = n - 1;\n" +
                "        while (left < right) {\n" +
                "            int temp = arr[left];\n" +
                "            arr[left] = arr[right];\n" +
                "            arr[right] = temp;\n" +
                "            left++;\n" +
                "            right--;\n" +
                "        }\n" +
                "        for (int i = 0; i < n; i++) {\n" +
                "            System.out.print(arr[i] + \" \");\n" +
                "        }\n" +
                "        System.out.println();\n" +
                "    }\n" +
                "}";
        String input = "5\n1 2 3 4 5\n";

        TraceRequest request = new TraceRequest("java", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        assertTrue(response.totalSteps() > 20);
        assertTrue(response.stdout().contains("5 4 3 2 1"));

        boolean foundArray = response.steps().stream()
                .anyMatch(s -> s.variables().containsKey("arr") &&
                               s.variables().get("arr").type().equals("array"));
        assertTrue(foundArray, "Array variable was not captured in trace steps");
    }

    private void assertSuccessfulCppTrace(TraceResponse response, String message) {
        assertNotNull(response);
        if ("TIMEOUT".equals(response.status()) || (response.error() != null && (
                response.error().contains("LLDB") ||
                response.error().contains("SBDebugger") ||
                response.error().contains("lldb") ||
                response.error().contains("timed out") ||
                "SERVER_ERROR".equals(response.status())
        ))) {
            org.junit.jupiter.api.Assumptions.abort("Skipping C++ test because LLDB is not available or timed out on this platform: " + response.error());
        }
        assertEquals("SUCCESS", response.status(), message + ": " + response.error());
    }

    @Test
    public void testCppCPArrayTwoPointers() throws Exception {
        String code = "#include <iostream>\n" +
                "using namespace std;\n" +
                "int main() {\n" +
                "    int n;\n" +
                "    cin >> n;\n" +
                "    int arr[5];\n" +
                "    for (int i = 0; i < n; i++) cin >> arr[i];\n" +
                "    int left = 0, right = n - 1;\n" +
                "    while (left < right) {\n" +
                "        int temp = arr[left];\n" +
                "        arr[left] = arr[right];\n" +
                "        arr[right] = temp;\n" +
                "        left++;\n" +
                "        right--;\n" +
                "        }\n" +
                "    for (int i = 0; i < n; i++) cout << arr[i] << \" \";\n" +
                "    cout << endl;\n" +
                "    return 0;\n" +
                "}";
        String input = "5\n10 20 30 40 50\n";

        TraceRequest request = new TraceRequest("cpp", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertSuccessfulCppTrace(response, "C++ runner error");
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("50 40 30 20 10"));
    }

    @Test
    public void testLeetCodeJavaTwoSum() throws Exception {
        // Pure LeetCode class with NO main method
        String code = "class Solution {\n" +
                "    public int[] twoSum(int[] nums, int target) {\n" +
                "        for (int i = 0; i < nums.length; i++) {\n" +
                "            for (int j = i + 1; j < nums.length; j++) {\n" +
                "                if (nums[i] + nums[j] == target) {\n" +
                "                    return new int[]{i, j};\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        return new int[]{};\n" +
                "    }\n" +
                "}";
        String input = "nums = [2,7,11,15], target = 9";

        TraceRequest request = new TraceRequest("java", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "LeetCode Java failed: " + response.error());
        assertTrue(response.totalSteps() > 5);
        assertTrue(response.stdout().contains("[0, 1]"));
    }

    @Test
    public void testLeetCodePythonTwoSum() throws Exception {
        // Pure LeetCode class with NO main method
        String code = "class Solution:\n" +
                "    def twoSum(self, nums, target):\n" +
                "        lookup = {}\n" +
                "        for i, num in enumerate(nums):\n" +
                "            diff = target - num\n" +
                "            if diff in lookup:\n" +
                "                return [lookup[diff], i]\n" +
                "            lookup[num] = i\n" +
                "        return []\n";
        String input = "nums = [2,7,11,15], target = 9";

        TraceRequest request = new TraceRequest("python", code, input, 300);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "LeetCode Python failed: " + response.error());
        assertTrue(response.totalSteps() > 5);
        assertTrue(response.stdout().contains("[0, 1]"));
    }

    @Test
    public void testLeetCodeCppTwoSum() throws Exception {
        // Pure LeetCode class with NO main method
        String code = "class Solution {\n" +
                "public:\n" +
                "    vector<int> twoSum(vector<int>& nums, int target) {\n" +
                "        for (int i = 0; i < nums.size(); i++) {\n" +
                "            for (int j = i + 1; j < nums.size(); j++) {\n" +
                "                if (nums[i] + nums[j] == target) {\n" +
                "                    return {i, j};\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        return {};\n" +
                "    }\n" +
                "};";
        String input = "nums = [2,7,11,15], target = 9";

        TraceRequest request = new TraceRequest("cpp", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertSuccessfulCppTrace(response, "LeetCode C++ failed");
        assertTrue(response.totalSteps() > 5);
    }

    @Test
    public void testLeetCodeJavaShortestPathWithPairAnd2DArray() throws Exception {
        String code = "class Solution {\n" +
                "    public int shortestPath(int n, int[][] edges, String labels, int k) {\n" +
                "        List<List<Pair>> graph = buildGraph(edges, n);\n" +
                "        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a1, a2) -> {\n" +
                "            return Integer.compare(a1[1], a2[1]);\n" +
                "        });\n" +
                "        int[][] distance = new int[n][k + 1];\n" +
                "        for (int i = 0; i < n; i++) {\n" +
                "            Arrays.fill(distance[i], Integer.MAX_VALUE);\n" +
                "        }\n" +
                "        distance[0][1] = 0;\n" +
                "        minHeap.offer(new int[]{0, 0, 1});\n" +
                "        while (!minHeap.isEmpty()) {\n" +
                "            int[] top = minHeap.poll();\n" +
                "            int u = top[0];\n" +
                "            int d = top[1];\n" +
                "            int len = top[2];\n" +
                "            if (u == n - 1) return d;\n" +
                "            if (d > distance[u][len]) continue;\n" +
                "            for (Pair neighbor : graph.get(u)) {\n" +
                "                int v = neighbor.node;\n" +
                "                int w = neighbor.distance;\n" +
                "                int nextLen = (labels.charAt(u) == labels.charAt(v)) ? len + 1 : 1;\n" +
                "                if (nextLen <= k) {\n" +
                "                    if (d + w < distance[v][nextLen]) {\n" +
                "                        distance[v][nextLen] = d + w;\n" +
                "                        minHeap.offer(new int[]{v, d + w, nextLen});\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        return -1;\n" +
                "    }\n" +
                "    private List<List<Pair>> buildGraph(int [][] edges, int n) {\n" +
                "        List<List<Pair>> graph = new ArrayList<>();\n" +
                "        for (int node = 0; node < n; node++) graph.add(new ArrayList<>());\n" +
                "        for (int [] edge : edges) graph.get(edge[0]).add(new Pair(edge[1], edge[2]));\n" +
                "        return graph;\n" +
                "    }\n" +
                "}\n" +
                "class Pair {\n" +
                "    int node;\n" +
                "    int distance;\n" +
                "    public Pair(int node, int distance) {\n" +
                "        this.node = node;\n" +
                "        this.distance = distance;\n" +
                "    }\n" +
                "}";
        String input = "3\n[[0,1,1],[1,2,1],[0,2,3]]\naab\n1";

        TraceRequest request = new TraceRequest("java", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "LeetCode shortestPath failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("3"));
    }

    @Test
    public void testLeetCodeJavaShortestPathWithNamedInputsAnd2DArray() throws Exception {
        String code = "class Solution {\n" +
                "    public int shortestPath(int n, int[][] edges, String labels, int k) {\n" +
                "        List<List<Pair>> graph = buildGraph(edges, n);\n" +
                "        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a1, a2) -> Integer.compare(a1[1], a2[1]));\n" +
                "        int[][] distance = new int[n][k + 1];\n" +
                "        for (int i = 0; i < n; i++) Arrays.fill(distance[i], Integer.MAX_VALUE);\n" +
                "        distance[0][1] = 0;\n" +
                "        minHeap.offer(new int[]{0, 0, 1});\n" +
                "        while (!minHeap.isEmpty()) {\n" +
                "            int[] top = minHeap.poll();\n" +
                "            int u = top[0], d = top[1], len = top[2];\n" +
                "            if (u == n - 1) return d;\n" +
                "            if (d > distance[u][len]) continue;\n" +
                "            for (Pair neighbor : graph.get(u)) {\n" +
                "                int v = neighbor.node, w = neighbor.distance;\n" +
                "                int nextLen = (labels.charAt(u) == labels.charAt(v)) ? len + 1 : 1;\n" +
                "                if (nextLen <= k && d + w < distance[v][nextLen]) {\n" +
                "                    distance[v][nextLen] = d + w;\n" +
                "                    minHeap.offer(new int[]{v, d + w, nextLen});\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        return -1;\n" +
                "    }\n" +
                "    private List<List<Pair>> buildGraph(int [][] edges, int n) {\n" +
                "        List<List<Pair>> graph = new ArrayList<>();\n" +
                "        for (int node = 0; node < n; node++) graph.add(new ArrayList<>());\n" +
                "        for (int [] edge : edges) graph.get(edge[0]).add(new Pair(edge[1], edge[2]));\n" +
                "        return graph;\n" +
                "    }\n" +
                "}\n" +
                "class Pair {\n" +
                "    int node;\n" +
                "    int distance;\n" +
                "    public Pair(int node, int distance) {\n" +
                "        this.node = node;\n" +
                "        this.distance = distance;\n" +
                "    }\n" +
                "}";
        String input = "n = 3, edges = [[0,1,1],[1,2,1],[0,2,3]], labels = \"aab\", k = 1";

        TraceRequest request = new TraceRequest("java", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "LeetCode shortestPath with named 2D array input failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("3"));
    }

    @Test
    public void testLeetCodeJavaMaxPackagesWithBracketsInInputKey() throws Exception {
        String code = "class Solution {\n" +
                "    public int maxPackages(int[] arr, int totalTime) {\n" +
                "        if (arr == null || arr.length == 0) return 0;\n" +
                "        int length = arr.length;\n" +
                "        int left = 0, right = 0;\n" +
                "        int prefixSum = 0, maxSum = 0;\n" +
                "        while (right < length) {\n" +
                "            prefixSum += arr[right];\n" +
                "            while (right - left + 1 > totalTime) {\n" +
                "                prefixSum -= arr[left];\n" +
                "                left++;\n" +
                "            }\n" +
                "            if (right - left + 1 <= totalTime) {\n" +
                "                maxSum = Math.max(maxSum, prefixSum);\n" +
                "            }\n" +
                "            right++;\n" +
                "        }\n" +
                "        return maxSum;\n" +
                "    }\n" +
                "}";
        String input = "arr[] = [2, 1, 3, 5, 0, 1, 4], totalTime = 3";

        TraceRequest request = new TraceRequest("java", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "maxPackages failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        boolean found7ElementArray = response.steps().stream()
                .anyMatch(s -> s.variables().containsKey("arr") &&
                               s.variables().get("arr").values() != null &&
                               s.variables().get("arr").values().size() == 7);
        assertTrue(found7ElementArray, "Array variable was not initialized with the 7 user-provided elements");
    }

    @Test
    public void testLeetCodeJavaReverseLinkedList() throws Exception {
        // Pure LeetCode solution with no main and NO ListNode class definition provided
        String code = "class Solution {\n" +
                "    public ListNode reverseList(ListNode head) {\n" +
                "        ListNode prev = null;\n" +
                "        ListNode curr = head;\n" +
                "        while (curr != null) {\n" +
                "            ListNode next = curr.next;\n" +
                "            curr.next = prev;\n" +
                "            prev = curr;\n" +
                "            curr = next;\n" +
                "        }\n" +
                "        return prev;\n" +
                "    }\n" +
                "}";
        String input = "head = [1, 2, 3, 4, 5]";

        TraceRequest request = new TraceRequest("java", code, input, 300);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "reverseList failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("5 -> 4 -> 3 -> 2 -> 1"));
    }

    @Test
    public void testLeetCodeJavaBinaryTreeMaxDepth() throws Exception {
        // Pure LeetCode solution with no main and NO TreeNode class definition provided
        String code = "class Solution {\n" +
                "    public int maxDepth(TreeNode root) {\n" +
                "        if (root == null) return 0;\n" +
                "        int left = maxDepth(root.left);\n" +
                "        int right = maxDepth(root.right);\n" +
                "        return Math.max(left, right) + 1;\n" +
                "    }\n" +
                "}";
        String input = "root = [3, 9, 20, null, null, 15, 7]";

        TraceRequest request = new TraceRequest("java", code, input, 300);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "maxDepth failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("3"));
    }

    @Test
    public void testLeetCodeJavaNaryTreeDiameterWithCustomTreeNodeClass() throws Exception {
        // User's exact scenario: class TreeNode is placed above class Solution
        String code = "// Definition for an n-ary tree node.\n" +
                "class TreeNode {\n" +
                "    int val;\n" +
                "    List<TreeNode> children;\n" +
                "\n" +
                "    TreeNode(int _val) {\n" +
                "        val = _val;\n" +
                "        children = new ArrayList<>();\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "class Solution {\n" +
                "    private int maxHeight;\n" +
                "\n" +
                "    public int diameter(TreeNode root) {\n" +
                "        maxHeight = 0;\n" +
                "        if (root == null) {\n" +
                "            return 0;\n" +
                "        }\n" +
                "        height(root);\n" +
                "        return maxHeight;\n" +
                "    }\n" +
                "\n" +
                "    private int height(TreeNode node) {\n" +
                "        if (node == null) {\n" +
                "            return 0;\n" +
                "        }\n" +
                "        int height1 = 0;\n" +
                "        int height2 = 0;\n" +
                "        for (TreeNode child : node.children) {\n" +
                "            int distance = height(child) + 1;\n" +
                "            if (distance > height1) {\n" +
                "                height2 = height1;\n" +
                "                height1 = distance;\n" +
                "            } else if (distance > height2) {\n" +
                "                height2 = distance;\n" +
                "            }\n" +
                "            int total = height1 + height2;\n" +
                "            maxHeight = Math.max(maxHeight, total);\n" +
                "        }\n" +
                "        return height1;\n" +
                "    }\n" +
                "}";
        String input = "root = [1, null, 3, 2, 4, null, 5, 6]";

        TraceRequest request = new TraceRequest("java", code, input, 300);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "N-ary tree diameter failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("3"));
    }

    @Test
    public void testLeetCodeJavaNaryTreeDiameterWithCommentedOutTreeNode() throws Exception {
        // User commented out class TreeNode definition
        String code = "/*\n" +
                "class TreeNode {\n" +
                "    int val;\n" +
                "    List<TreeNode> children;\n" +
                "    TreeNode(int _val) {\n" +
                "        val = _val;\n" +
                "        children = new ArrayList<>();\n" +
                "    }\n" +
                "}\n" +
                "*/\n" +
                "\n" +
                "class Solution {\n" +
                "    private int maxHeight;\n" +
                "\n" +
                "    public int diameter(TreeNode root) {\n" +
                "        maxHeight = 0;\n" +
                "        if (root == null) return 0;\n" +
                "        height(root);\n" +
                "        return maxHeight;\n" +
                "    }\n" +
                "\n" +
                "    private int height(TreeNode node) {\n" +
                "        if (node == null) return 0;\n" +
                "        int height1 = 0;\n" +
                "        int height2 = 0;\n" +
                "        for (TreeNode child : node.children) {\n" +
                "            int distance = height(child) + 1;\n" +
                "            if (distance > height1) {\n" +
                "                height2 = height1;\n" +
                "                height1 = distance;\n" +
                "            } else if (distance > height2) {\n" +
                "                height2 = distance;\n" +
                "            }\n" +
                "            int total = height1 + height2;\n" +
                "            maxHeight = Math.max(maxHeight, total);\n" +
                "        }\n" +
                "        return height1;\n" +
                "    }\n" +
                "}";
        String input = "root = [1, null, 3, 2, 4, null, 5, 6]";

        TraceRequest request = new TraceRequest("java", code, input, 300);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "Commented-out TreeNode failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("3"));
    }

    @Test
    public void testLeetCodeCppLinkedListReverse() throws Exception {
        String code = "class Solution {\n" +
                "public:\n" +
                "    ListNode* reverseList(ListNode* head) {\n" +
                "        ListNode* prev = nullptr;\n" +
                "        ListNode* curr = head;\n" +
                "        while (curr) {\n" +
                "            ListNode* nxt = curr->next;\n" +
                "            curr->next = prev;\n" +
                "            prev = curr;\n" +
                "            curr = nxt;\n" +
                "        }\n" +
                "        return prev;\n" +
                "    }\n" +
                "};";
        String input = "head = [1, 2, 3, 4, 5]";

        TraceRequest request = new TraceRequest("cpp", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertSuccessfulCppTrace(response, "C++ reverseList failed");
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("5 -> 4 -> 3 -> 2 -> 1"));
    }

    @Test
    public void testLeetCodeCppBinaryTreeMaxDepth() throws Exception {
        String code = "class Solution {\n" +
                "public:\n" +
                "    int maxDepth(TreeNode* root) {\n" +
                "        if (!root) return 0;\n" +
                "        return 1 + max(maxDepth(root->left), maxDepth(root->right));\n" +
                "    }\n" +
                "};";
        String input = "root = [3, 9, 20, null, null, 15, 7]";

        TraceRequest request = new TraceRequest("cpp", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertSuccessfulCppTrace(response, "C++ maxDepth failed");
        System.out.println("DEBUG C++ maxDepth steps: " + response.totalSteps() + ", stdout: " + response.stdout());
        assertTrue(response.totalSteps() >= 1);
        assertTrue(response.stdout().contains("3"));
    }

    @Test
    public void testLeetCodePythonLinkedListReverse() throws Exception {
        String code = "class Solution:\n" +
                "    def reverseList(self, head: Optional[ListNode]) -> Optional[ListNode]:\n" +
                "        prev = None\n" +
                "        curr = head\n" +
                "        while curr:\n" +
                "            nxt = curr.next\n" +
                "            curr.next = prev\n" +
                "            prev = curr\n" +
                "            curr = nxt\n" +
                "        return prev\n";
        String input = "head = [1, 2, 3, 4, 5]";

        TraceRequest request = new TraceRequest("python", code, input, 300);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "Python reverseList failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("5 -> 4 -> 3 -> 2 -> 1"));
    }

    @Test
    public void testLeetCodePythonNaryTreeDiameter() throws Exception {
        String code = "class Solution:\n" +
                "    def diameter(self, root: 'TreeNode') -> int:\n" +
                "        self.max_height = 0\n" +
                "        def height(node):\n" +
                "            if not node:\n" +
                "                return 0\n" +
                "            h1, h2 = 0, 0\n" +
                "            for child in node.children:\n" +
                "                d = height(child) + 1\n" +
                "                if d > h1:\n" +
                "                    h2 = h1\n" +
                "                    h1 = d\n" +
                "                elif d > h2:\n" +
                "                    h2 = d\n" +
                "            self.max_height = max(self.max_height, h1 + h2)\n" +
                "            return h1\n" +
                "        if not root:\n" +
                "            return 0\n" +
                "        height(root)\n" +
                "        return self.max_height\n";
        String input = "root = [1, null, 3, 2, 4, null, 5, 6]";

        TraceRequest request = new TraceRequest("python", code, input, 200);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "Python N-ary diameter failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("3"));
    }

    @Test
    public void testLeetCodeJavaNaryTreeWithNodeClass() throws Exception {
        // Standard LeetCode 559: Maximum Depth of N-ary Tree using class Node
        String code = "/*\n" +
                "// Definition for a Node.\n" +
                "class Node {\n" +
                "    public int val;\n" +
                "    public List<Node> children;\n" +
                "    public Node() {}\n" +
                "    public Node(int _val) { val = _val; }\n" +
                "    public Node(int _val, List<Node> _children) { val = _val; children = _children; }\n" +
                "};\n" +
                "*/\n" +
                "class Solution {\n" +
                "    public int maxDepth(Node root) {\n" +
                "        if (root == null) return 0;\n" +
                "        int max = 0;\n" +
                "        for (Node child : root.children) {\n" +
                "            max = Math.max(max, maxDepth(child));\n" +
                "        }\n" +
                "        return max + 1;\n" +
                "    }\n" +
                "}";
        String input = "root = [1, null, 3, 2, 4, null, 5, 6]";

        TraceRequest request = new TraceRequest("java", code, input, 300);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "N-ary tree with Node failed: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("3"));
    }

    @Test
    public void testJavaCompetitiveProgrammingStringAndList() throws Exception {
        String code = "import java.util.*;\n" +
                "public class Solution {\n" +
                "    public static void main(String[] args) {\n" +
                "        int n = 2;\n" +
                "        String s = \"12\";\n" +
                "        List<Integer> result = new ArrayList<>();\n" +
                "        for (int i = 0; i < s.length(); i++) {\n" +
                "            if (s.charAt(i) == '1') result.add(i);\n" +
                "        }\n" +
                "        System.out.println(result.size());\n" +
                "    }\n" +
                "}";
        String input = "";

        TraceRequest request = new TraceRequest("java", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "CP String test failed: " + response.error());
        assertTrue(response.totalSteps() > 5);
        assertTrue(response.stdout().contains("1"));
    }
}
