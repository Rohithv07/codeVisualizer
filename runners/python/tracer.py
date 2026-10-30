#!/usr/bin/env python3
"""
Python Execution Tracer for DSA / Competitive Programming
Traces execution step-by-step, recording line numbers, variable values, call stack, and stdout.
"""

import sys
import io
import json
import types
from typing import Any, Dict, List, Optional

MAX_STEPS_DEFAULT = 1000
MAX_COLLECTION_SIZE = 50
MAX_STRING_LEN = 100
MAX_OBJECT_DEPTH = 3

class StepLimitExceeded(Exception):
    pass

class VariableSerializer:
    @classmethod
    def serialize(cls, val: Any, depth: int = 0, visited: Optional[set] = None) -> Dict[str, Any]:
        if visited is None:
            visited = set()

        if depth > MAX_OBJECT_DEPTH:
            return {"type": "truncated", "repr": str(val)[:50]}

        # Primitives
        if val is None:
            return {"type": "null", "value": None, "repr": "None"}
        if isinstance(val, bool):
            return {"type": "bool", "value": val, "repr": str(val)}
        if isinstance(val, (int, float)):
            return {"type": type(val).__name__, "value": val, "repr": str(val)}
        if isinstance(val, str):
            truncated = val if len(val) <= MAX_STRING_LEN else val[:MAX_STRING_LEN] + "..."
            return {"type": "str", "value": truncated, "repr": repr(truncated)}

        val_id = id(val)
        if val_id in visited:
            return {"type": "ref", "id": hex(val_id), "repr": f"ref({hex(val_id)})"}
        visited.add(val_id)

        # Lists & Tuples (1D, 2D arrays)
        if isinstance(val, (list, tuple)):
            is_tuple = isinstance(val, tuple)
            elements = []
            for i, item in enumerate(val):
                if i >= MAX_COLLECTION_SIZE:
                    elements.append({"type": "truncated", "repr": f"... +{len(val) - MAX_COLLECTION_SIZE} more"})
                    break
                elements.append(cls.serialize(item, depth + 1, visited.copy()))
            
            # Helper: check if it's a 2D matrix of numbers
            is_matrix = len(val) > 0 and all(isinstance(row, (list, tuple)) for row in val[:5])

            return {
                "type": "tuple" if is_tuple else "list",
                "length": len(val),
                "isMatrix": is_matrix,
                "values": elements,
                "repr": str(val) if len(str(val)) < 80 else f"{type(val).__name__}[{len(val)}]"
            }

        # Sets
        if isinstance(val, (set, frozenset)):
            items = [cls.serialize(x, depth + 1, visited.copy()) for i, x in enumerate(val) if i < MAX_COLLECTION_SIZE]
            return {
                "type": "set",
                "length": len(val),
                "values": items,
                "repr": str(val)
            }

        # Dictionaries
        if isinstance(val, dict):
            items = {}
            for i, (k, v) in enumerate(val.items()):
                if i >= MAX_COLLECTION_SIZE:
                    items["..."] = {"type": "truncated", "repr": f"+{len(val) - MAX_COLLECTION_SIZE} more"}
                    break
                items[str(k)] = cls.serialize(v, depth + 1, visited.copy())
            return {
                "type": "dict",
                "length": len(val),
                "entries": items,
                "fields": items,
                "repr": str(val) if len(str(val)) < 80 else f"dict[{len(val)}]"
            }

        # Custom DSA Objects (ListNode, TreeNode, GraphNode, etc.)
        if hasattr(val, "__dict__"):
            fields = {}
            for k, v in val.__dict__.items():
                if not k.startswith("_"):
                    fields[k] = cls.serialize(v, depth + 1, visited.copy())
            return {
                "type": "object",
                "className": val.__class__.__name__,
                "id": hex(val_id),
                "fields": fields,
                "repr": f"<{val.__class__.__name__}@{hex(val_id)}>"
            }

        # Fallback for functions, modules, etc.
        return {
            "type": type(val).__name__,
            "value": str(val),
            "repr": str(val)
        }

class ExecutionTracer:
    def __init__(self, target_filename: str, max_steps: int = MAX_STEPS_DEFAULT):
        self.target_filename = target_filename
        self.max_steps = max_steps
        self.steps: List[Dict[str, Any]] = []
        self.step_count = 0
        self.last_stdout_pos = 0
        self.stdout_capture = io.StringIO()

    def get_call_stack(self, frame: types.FrameType) -> List[Dict[str, Any]]:
        stack = []
        curr = frame
        while curr:
            if curr.f_code.co_filename == self.target_filename:
                stack.append({
                    "function": curr.f_code.co_name,
                    "line": curr.f_lineno
                })
            curr = curr.f_back
        return list(reversed(stack))

    def trace_callback(self, frame: types.FrameType, event: str, arg: Any):
        # Only trace code from target file
        if frame.f_code.co_filename != self.target_filename:
            return self.trace_callback

        if event in ("line", "return", "exception"):
            self.step_count += 1
            if self.step_count > self.max_steps:
                raise StepLimitExceeded(f"Execution reached maximum allowed steps ({self.max_steps})")

            # Capture stdout delta
            current_stdout = self.stdout_capture.getvalue()
            stdout_delta = current_stdout[self.last_stdout_pos:]
            self.last_stdout_pos = len(current_stdout)

            # Filter variables to exclude dunder/internal and imports
            variables: Dict[str, Any] = {}
            for var_name, var_val in frame.f_locals.items():
                if var_name.startswith("__") and var_name.endswith("__"):
                    continue
                if isinstance(var_val, types.ModuleType):
                    continue
                variables[var_name] = VariableSerializer.serialize(var_val)

            step_data = {
                "step": self.step_count,
                "line": frame.f_lineno,
                "event": event,
                "function": frame.f_code.co_name,
                "callStack": self.get_call_stack(frame),
                "variables": variables,
                "stdoutDelta": stdout_delta,
                "totalStdout": current_stdout
            }
            self.steps.append(step_data)

        return self.trace_callback

def run_trace(code: str, stdin_data: str, max_steps: int = MAX_STEPS_DEFAULT) -> Dict[str, Any]:
    target_filename = "<solution>"
    tracer = ExecutionTracer(target_filename=target_filename, max_steps=max_steps)

    # Prepare I/O
    orig_stdin = sys.stdin
    orig_stdout = sys.stdout
    sys.stdin = io.StringIO(stdin_data)
    sys.stdout = tracer.stdout_capture

    status = "SUCCESS"
    error_message: Optional[str] = None

    try:
        compiled_code = compile(code, target_filename, "exec")
        sys.settrace(tracer.trace_callback)
        global_scope = {"__name__": "__main__"}
        exec(compiled_code, global_scope)
    except StepLimitExceeded as e:
        status = "STEP_LIMIT_EXCEEDED"
        error_message = str(e)
    except Exception as e:
        status = "RUNTIME_ERROR"
        error_message = f"{type(e).__name__}: {str(e)}"
    finally:
        sys.settrace(None)
        sys.stdin = orig_stdin
        sys.stdout = orig_stdout

    return {
        "status": status,
        "language": "python",
        "error": error_message,
        "totalSteps": len(tracer.steps),
        "stdout": tracer.stdout_capture.getvalue(),
        "steps": tracer.steps
    }

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--cli":
        # Read payload from stdin: JSON { "code": "...", "input": "...", "maxSteps": 1000 }
        payload = json.loads(sys.stdin.read())
        code_input = payload.get("code", "")
        stdin_input = payload.get("input", "")
        max_steps = payload.get("maxSteps", MAX_STEPS_DEFAULT)
        result = run_trace(code_input, stdin_input, max_steps)
        print(json.dumps(result))

