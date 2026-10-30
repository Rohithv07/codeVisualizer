package com.visualizer.runner;

import com.visualizer.model.TraceRequest;
import com.visualizer.model.TraceResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RunnerTest {

    @Test
    public void testPythonTracing() throws Exception {
        PythonTraceRunner runner = new PythonTraceRunner();
        String code = "n = int(input())\n" +
                "arr = list(map(int, input().split()))\n" +
                "total = sum(arr)\n" +
                "print('Total:', total)\n";
        String input = "3\n10 20 30\n";

        TraceRequest request = new TraceRequest("python", code, input, 100);
        TraceResponse response = runner.run(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        assertTrue(response.totalSteps() > 0);
        assertTrue(response.stdout().contains("Total: 60"));
        assertNotNull(response.steps());
    }

    @Test
    public void testJavaArrayTwoPointers() throws Exception {
        JavaTraceRunner runner = new JavaTraceRunner();
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
        TraceResponse response = runner.run(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        assertTrue(response.totalSteps() > 20);
        assertTrue(response.stdout().contains("5 4 3 2 1"));

        // Check that at step 15+, variables contain arr, left, right
        boolean foundArray = response.steps().stream()
                .anyMatch(s -> s.variables().containsKey("arr") &&
                               s.variables().get("arr").type().equals("array"));
        assertTrue(foundArray, "Array variable was not captured in trace steps");
    }

    @Test
    public void testCppArrayTwoPointers() throws Exception {
        CppTraceRunner runner = new CppTraceRunner();
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
        TraceResponse response = runner.run(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status(), "C++ runner error: " + response.error());
        assertTrue(response.totalSteps() > 10);
        assertTrue(response.stdout().contains("50 40 30 20 10"));
    }
}
