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

        TraceRequest request = new TraceRequest("python", code, input, 100);
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
                "    }\n" +
                "    for (int i = 0; i < n; i++) cout << arr[i] << \" \";\n" +
                "    cout << endl;\n" +
                "    return 0;\n" +
                "}";
        String input = "5\n10 20 30 40 50\n";

        TraceRequest request = new TraceRequest("cpp", code, input, 100);
        TraceResponse response = visualizerService.trace(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "C++ runner error: " + response.error());
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

        TraceRequest request = new TraceRequest("python", code, input, 100);
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

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "LeetCode C++ failed: " + response.error());
        assertTrue(response.totalSteps() > 5);
    }
}
