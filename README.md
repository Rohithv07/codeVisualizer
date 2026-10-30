# ⚡ CodeTrace - Step-by-Step DSA Execution Visualizer

[![Java](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.9%2B-blue.svg)](https://www.python.org/)
[![C++](https://img.shields.io/badge/C%2B%2B-17-blue.svg)](https://isocpp.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

> **Visualize what your code is *actually* doing, step-by-step.**  
> Designed specifically for competitive programmers (Codeforces, LeetCode, CodeChef) who hit `Wrong Answer` and need to inspect variable states, array pointers, trees, and graphs without manual print debugging.

---

## 🌟 Why CodeTrace?

When solving DSA problems, logic bugs often hide in subtle index off-by-one errors, faulty pointer movements, or corrupted recurrence relations. 

**CodeTrace takes your raw source code and standard input (`stdin`), executes it deterministically, and records a complete time-travel trace:**
* Current executing line number
* Local variable states and mutations
* Dynamic pointer arrows above arrays (`left ↓`, `right ↓`, `mid ↓`)
* 2D dynamic programming grid matrices
* Linked lists with node chains and pointer badges (`head`, `curr`, `prev`)
* Binary trees rendered in hierarchical view
* Directed graphs with live visited-vertex highlights
* Incremental program output (`stdout`) at each step

---

## 🏗️ Architecture & System Design

```mermaid
flowchart TD
    subgraph Browser ["Web Client (Browser - :8080)"]
        UI["Interactive Visualizer UI"]
        Editor["CodeMirror Editor (Line Highlight)"]
        Stepper["Timeline Controls (Play / Step / Scrub)"]
        Visualizers["DSA Inspectors (Array / Grid / Tree / Graph)"]
    end

    subgraph Backend ["Spring Boot Java Backend (:8080)"]
        API["VisualizerController (POST /api/trace)"]
        Service["VisualizerService (Router & Normalizer)"]
        Model["Immutable Java 17 Records (TraceResponse, TraceStep)"]
    end

    subgraph Runners ["Language Tracing Engines"]
        JavaR["JavaTraceRunner\n(Java Debug Interface - com.sun.jdi)"]
        CppR["CppTraceRunner\n(Clang++ -g & LLDB Python API)"]
        PyR["PythonTraceRunner\n(sys.settrace & AST Inspector)"]
    end

    UI -->|"POST /api/trace (Code, Stdin, Language)"| API
    API --> Service
    Service --> JavaR
    Service --> CppR
    Service --> PyR
    
    JavaR -->|"JDI StepEvents"| Model
    CppR -->|"LLDB SBFrame Variables"| Model
    PyR -->|"Trace Frames"| Model
    
    Model -->|"Canonical Timeline JSON"| API
    API -->|"Timeline Steps"| UI
    UI --> Editor
    UI --> Stepper
    UI --> Visualizers
```

---

## 🚀 Key Features

### 1. Multi-Language Support
* **Java (JDK 17)**: Uses Java's native **Java Debug Interface (`com.sun.jdi`)** to step through bytecode line-by-line and inspect JVM stack frames, arrays, primitives, and object references without modifying the source code.
* **C++ (C++17)**: Compiles with `clang++ -g -O0` and orchestrates **LLDB** via its Python API to extract `std::vector`, raw arrays, pointers, and primitives.
* **Python (3.9+)**: Leverages `sys.settrace()` to capture local scopes, lists, dicts, sets, and custom classes.

### 2. Dual Platform Support: Codeforces & LeetCode
CodeTrace automatically detects the style of your code and adapts:
* **Competitive Programming Mode (Codeforces, AtCoder, CSES)**:
  * Full programs containing standard `main()` methods.
  * Reads from standard input (`cin`, `Scanner`, `sys.stdin`) and outputs to `stdout`.
* **LeetCode Mode (Function-Only `class Solution`)**:
  * You can paste pure `class Solution` code with **no `main()` method**.
  * Works across **Java, C++, and Python**.
  * **Linked Lists & Trees Supported Out of the Box**:
    * **Linked Lists**: Accepts `head = [1, 2, 3, 4, 5]`
    * **Binary Trees**: Accepts `root = [3, 9, 20, null, null, 15, 7]`
    * **N-ary Trees**: Accepts `root = [1, null, 3, 2, 4, null, 5, 6]`
    * Auxiliary node definitions (`class ListNode`, `class TreeNode`, `struct ListNode`, `struct TreeNode`) can be **kept, commented out (like on LeetCode/GFG), or omitted completely** — CodeTrace automatically provides or adapts them across all 3 languages!
  * Accepts LeetCode test case formats directly in the input box:
    ```text
    nums = [2, 7, 11, 15], target = 9
    ```
    or for linked lists:
    ```text
    head = [1, 2, 3, 4, 5]
    ```
    or for trees:
    ```text
    root = [1, null, 3, 2, 4, null, 5, 6]
    ```
    or line-by-line:
    ```text
    [2, 7, 11, 15]
    9
    ```
  * CodeTrace automatically synthesizes a driver `main()` method behind the scenes, instantiates your class, injects parsed parameters, and steps through your algorithm line by line.

### 3. Specialized DSA Inspectors
* **Array & Two-Pointer Visualizer**: Automatically detects 1D arrays and points integer variables (like `i`, `j`, `left`, `right`, `low`, `high`, `mid`, `ans`) directly at their respective indices.
* **2D Grid & Matrix Visualizer**: Renders 2D arrays or DP matrices into an interactive table.
* **Linked List Visualizer**: Detects objects with `.next` pointers and displays linked node chains (`[val: 1 | next] → [val: 2 | next] → null`) with pointer badges (`head`, `curr`, `prev`).
* **Tree Visualizer (Binary & N-ary)**: Automatically renders interactive hierarchical tree structures for both binary trees (`left`, `right`) and N-ary trees (`children`).
* **Graph & Adjacency List Visualizer**: Visualizes graphs from dictionaries or 2D vectors, highlighting visited nodes in real time.

### 3. Built-in Safeguards
* **Step Limit Protection**: Halts execution if steps exceed a configurable ceiling (e.g., 1,000 steps), protecting against infinite loops.
* **Deadlock-Free Streaming**: Piped subprocesses stream stdout directly to temporary files, preventing OS pipe buffer deadlocks on large traces.

---

## 📂 Project Structure

```text
codeVisualizer/
├── backend/
│   ├── pom.xml                               # Maven build configuration (Java 17, Spring Boot 3)
│   └── src/
│       ├── main/
│       │   ├── java/com/visualizer/
│       │   │   ├── VisualizerApplication.java # Spring Boot Main
│       │   │   ├── controller/               # REST API (POST /api/trace, GET /api/health)
│       │   │   ├── model/                    # Java 17 Records (DTOs)
│       │   │   │   ├── TraceRequest.java
│       │   │   │   ├── TraceResponse.java
│       │   │   │   ├── TraceStep.java
│       │   │   │   ├── StackFrame.java
│       │   │   │   └── VariableValue.java
│       │   │   ├── runner/                   # Tracing runners
│       │   │   │   ├── LanguageRunner.java   # Common runner interface
│       │   │   │   ├── JavaTraceRunner.java  # JDI native debugger
│       │   │   │   ├── PythonTraceRunner.java
│       │   │   │   └── CppTraceRunner.java   # LLDB C++ tracer
│       │   │   └── service/                  # Visualizer service logic
│       │   └── resources/
│       │       ├── application.properties
│       │       └── static/index.html         # Interactive Single-Page Visualizer UI
│       └── test/java/com/visualizer/runner/
│           └── RunnerTest.java               # Automated test suites for Java, Python, C++
├── runners/
│   ├── python/
│   │   └── tracer.py                         # Standalone Python sys.settrace engine
│   └── cpp/
│       └── tracer.py                         # Standalone C++ LLDB debugger engine
├── mvnw                                      # Maven wrapper script
├── .gitignore
└── README.md
```

---

## ⚡ Quickstart

### Prerequisites
* **Java 17+** (JDK 17 LTS recommended)
* **Python 3.9+**
* **Clang / g++** (with LLDB installed, standard on macOS via Command Line Tools)

### 1. Clone & Run the Application

```bash
git clone git@github.com:Rohithv07/codeVisualizer.git
cd codeVisualizer

# Package the application
./mvnw -f backend/pom.xml clean package -DskipTests

# Start the Spring Boot server
java -jar backend/target/backend-1.0.0-SNAPSHOT.jar
```

Or run directly with Maven:
```bash
./mvnw -f backend/pom.xml spring-boot:run
```

### 2. Open the Visualizer
Open your browser and navigate to:
```
http://localhost:8080
```

1. Select a pre-loaded template (e.g. **Python: Graph BFS**, **Java: Two Pointers**, **C++: Binary Search**).
2. Enter your custom test case in the **Standard Input (stdin)** box.
3. Click **"Visualize Execution"**.
4. Use the **Play / Pause** buttons or **Arrow Keys (`←` / `→`)** to step through execution!

---

## 🧪 Running Automated Tests

Run the test suite covering Java JDI, Python, and C++ tracing:

```bash
./mvnw -f backend/pom.xml test
```

---

## 🔌 API Reference

### Trace Execution
`POST /api/trace`

#### Request Body
```json
{
  "language": "python",
  "code": "n = int(input())\narr = [1, 2, 3]\nprint(sum(arr))",
  "input": "3",
  "maxSteps": 500
}
```

#### Response
```json
{
  "status": "SUCCESS",
  "language": "python",
  "totalSteps": 12,
  "stdout": "6\n",
  "steps": [
    {
      "step": 1,
      "line": 1,
      "event": "line",
      "function": "<module>",
      "callStack": [{ "function": "<module>", "line": 1 }],
      "variables": {
        "n": { "type": "int", "value": 3, "repr": "3" }
      },
      "stdoutDelta": "",
      "totalStdout": ""
    }
  ]
}
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

