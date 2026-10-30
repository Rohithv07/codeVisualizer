#!/usr/bin/env python3
"""
C++ Execution Tracer for DSA / Competitive Programming using LLDB
Compiles C++ source with debug symbols, steps through execution, and captures variable states.
"""

import sys
import os
import json
import tempfile
import subprocess
from typing import Any, Dict, List, Optional

# Add LLDB Python library path on macOS
LLDB_PATHS = [
    "/Library/Developer/CommandLineTools/Library/PrivateFrameworks/LLDB.framework/Resources/Python",
    "/Applications/Xcode.app/Contents/SharedFrameworks/LLDB.framework/Resources/Python"
]
for p in LLDB_PATHS:
    if os.path.exists(p) and p not in sys.path:
        sys.path.append(p)

try:
    import lldb
except ImportError:
    lldb = None

MAX_STEPS_DEFAULT = 1000
MAX_COLLECTION_SIZE = 50

def serialize_lldb_value(val: Any, depth: int = 0) -> Dict[str, Any]:
    if depth > 8 or not val or not val.IsValid():
        return {"type": "unknown", "repr": "?"}

    type_name = val.GetTypeName() or "unknown"
    val_str = val.GetValue()
    summary = val.GetSummary()

    # Primitives
    if type_name in ("int", "short", "long", "long long", "unsigned int", "unsigned long"):
        try:
            return {"type": "int", "value": int(val_str), "repr": val_str}
        except (ValueError, TypeError):
            return {"type": "int", "value": val_str, "repr": str(val_str)}

    if type_name in ("float", "double"):
        try:
            return {"type": "float", "value": float(val_str), "repr": val_str}
        except (ValueError, TypeError):
            return {"type": "float", "value": val_str, "repr": str(val_str)}

    if type_name in ("bool", "_Bool"):
        b_val = val_str == "true" or val_str == "1"
        return {"type": "bool", "value": b_val, "repr": "true" if b_val else "false"}

    if type_name == "char":
        return {"type": "char", "value": val_str, "repr": val_str or "'\\0'"}

    # String (std::string)
    if "string" in type_name.lower():
        clean_str = summary.strip('"') if summary else (val_str or "")
        return {"type": "string", "value": clean_str, "repr": f'"{clean_str}"'}

    # Arrays and std::vector
    is_vector = "vector" in type_name
    is_array = "[" in type_name and "]" in type_name
    if is_vector or is_array:
        num_children = val.GetNumChildren()
        limit = min(num_children, MAX_COLLECTION_SIZE)
        elements = []
        is_matrix = False
        for j in range(limit):
            child = val.GetChildAtIndex(j)
            if child and child.IsValid():
                child_type = child.GetTypeName() or ""
                if "vector" in child_type or "[" in child_type:
                    is_matrix = True
                elements.append(serialize_lldb_value(child, depth + 1))

        return {
            "type": "array",
            "length": num_children,
            "isMatrix": is_matrix,
            "values": elements,
            "repr": f"{'vector' if is_vector else 'array'}[{num_children}]"
        }

    # Pointers / Structs / Objects (e.g. ListNode*, TreeNode*)
    if val.TypeIsPointerType():
        if val_str in ("0x0", "0x00000000", "0", None):
            return {"type": "null", "value": None, "repr": "nullptr"}
        # Dereference pointer to inspect fields
        deref = val.Dereference()
        if deref and deref.IsValid():
            fields = {}
            for k in range(deref.GetNumChildren()):
                f = deref.GetChildAtIndex(k)
                if f and f.IsValid() and f.GetName():
                    fields[f.GetName()] = serialize_lldb_value(f, depth + 1)
            return {
                "type": "object",
                "className": type_name.replace("*", "").strip(),
                "id": val_str,
                "fields": fields,
                "repr": f"{type_name}@{val_str}"
            }

    # Custom struct/object
    num_fields = val.GetNumChildren()
    if num_fields > 0 and num_fields < 20:
        fields = {}
        for k in range(num_fields):
            f = val.GetChildAtIndex(k)
            if f and f.IsValid() and f.GetName():
                fields[f.GetName()] = serialize_lldb_value(f, depth + 1)
        return {
            "type": "object",
            "className": type_name,
            "fields": fields,
            "repr": summary or type_name
        }

    return {"type": type_name, "value": val_str or summary, "repr": val_str or summary or "?"}

def trace_cpp(code: str, stdin_data: str, max_steps: int = MAX_STEPS_DEFAULT) -> Dict[str, Any]:
    if lldb is None:
        return {
            "status": "SERVER_ERROR",
            "language": "cpp",
            "error": "LLDB Python module is not available on this system.",
            "totalSteps": 0,
            "stdout": "",
            "steps": []
        }

    with tempfile.TemporaryDirectory(prefix="dsa_cpp_") as temp_dir:
        src_file = os.path.join(temp_dir, "solution.cpp")
        bin_file = os.path.join(temp_dir, "solution")
        stdin_file = os.path.join(temp_dir, "stdin.txt")
        stdout_file = os.path.join(temp_dir, "stdout.txt")

        with open(src_file, "w") as f:
            f.write(code)
        with open(stdin_file, "w") as f:
            f.write(stdin_data)

        # Step 1: Compile with debug symbols (-g) and optimization off (-O0)
        compile_cmd = [
            "clang++", "-g", "-O0", "-std=c++17", "-fno-inline",
            src_file, "-o", bin_file
        ]
        compile_res = subprocess.run(compile_cmd, capture_output=True, text=True)
        if compile_res.returncode != 0:
            return {
                "status": "COMPILATION_ERROR",
                "language": "cpp",
                "error": compile_res.stderr or compile_res.stdout,
                "totalSteps": 0,
                "stdout": "",
                "steps": []
            }

        # Step 2: Initialize LLDB Target
        debugger = lldb.SBDebugger.Create()
        debugger.SetAsync(False)
        target = debugger.CreateTarget(bin_file)
        if not target.IsValid():
            return {
                "status": "RUNTIME_ERROR",
                "language": "cpp",
                "error": "Failed to create LLDB target for compiled binary.",
                "totalSteps": 0,
                "stdout": "",
                "steps": []
            }

        # Breakpoint at main
        bp = target.BreakpointCreateByName("main")
        if not bp.IsValid():
            return {
                "status": "RUNTIME_ERROR",
                "language": "cpp",
                "error": "Failed to set breakpoint at main().",
                "totalSteps": 0,
                "stdout": "",
                "steps": []
            }

        launch_error = lldb.SBError()
        launch_info = lldb.SBLaunchInfo([])
        # fd 0 = stdin, fd 1 = stdout
        launch_info.AddOpenFileAction(0, stdin_file, True, False)
        launch_info.AddOpenFileAction(1, stdout_file, False, True)

        process = target.Launch(launch_info, launch_error)
        if not launch_error.Success():
            return {
                "status": "RUNTIME_ERROR",
                "language": "cpp",
                "error": f"Failed to launch process: {launch_error.GetCString()}",
                "totalSteps": 0,
                "stdout": "",
                "steps": []
            }

        thread = process.GetSelectedThread()
        steps = []
        step_count = 0
        status = "SUCCESS"
        error_msg = None

        while process.GetState() == lldb.eStateStopped:
            frame = thread.GetSelectedFrame()
            line_entry = frame.GetLineEntry()
            file_spec = line_entry.GetFileSpec()
            file_name = file_spec.GetFilename()
            line_num = line_entry.GetLine()

            if file_name and file_name.lower() == "solution.cpp":
                step_count += 1
                if step_count > max_steps:
                    status = "STEP_LIMIT_EXCEEDED"
                    error_msg = f"Execution reached maximum allowed steps ({max_steps})"
                    break

                # Extract local variables
                variables = {}
                var_list = frame.GetVariables(True, True, False, True)
                for i in range(var_list.GetSize()):
                    v = var_list.GetValueAtIndex(i)
                    if v and v.IsValid() and v.GetName():
                        variables[v.GetName()] = serialize_lldb_value(v)

                # Call stack
                call_stack = []
                for f_idx in range(thread.GetNumFrames()):
                    f = thread.GetFrameAtIndex(f_idx)
                    f_line = f.GetLineEntry().GetLine()
                    f_name = f.GetFunctionName() or "unknown"
                    f_fn = f.GetLineEntry().GetFileSpec().GetFilename()
                    if f_fn and f_fn.lower() == "solution.cpp":
                        call_stack.append({"function": f_name, "line": f_line})
                call_stack.reverse()

                # Read current stdout
                total_out = ""
                if os.path.exists(stdout_file):
                    with open(stdout_file, "r") as out_f:
                        total_out = out_f.read()

                steps.append({
                    "step": step_count,
                    "line": line_num,
                    "event": "line",
                    "function": frame.GetFunctionName() or "main",
                    "callStack": call_stack,
                    "variables": variables,
                    "stdoutDelta": "",
                    "totalStdout": total_out
                })

            thread.StepOver()

        # Final cleanup
        if process.IsValid():
            process.Kill()

        total_stdout = ""
        if os.path.exists(stdout_file):
            with open(stdout_file, "r") as out_f:
                total_stdout = out_f.read()

        return {
            "status": status,
            "language": "cpp",
            "error": error_msg,
            "totalSteps": len(steps),
            "stdout": total_stdout,
            "steps": steps
        }

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--cli":
        payload = json.loads(sys.stdin.read())
        code_input = payload.get("code", "")
        stdin_input = payload.get("input", "")
        max_steps = payload.get("maxSteps", MAX_STEPS_DEFAULT)
        result = trace_cpp(code_input, stdin_input, max_steps)
        print(json.dumps(result))
