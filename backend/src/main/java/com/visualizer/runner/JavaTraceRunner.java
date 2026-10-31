package com.visualizer.runner;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.Field;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.LongValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ShortValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import com.visualizer.model.StackFrame;
import com.visualizer.model.TraceRequest;
import com.visualizer.model.TraceResponse;
import com.visualizer.model.TraceStep;
import com.visualizer.model.VariableValue;
import org.springframework.stereotype.Component;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JavaTraceRunner implements LanguageRunner {

    private static final int MAX_COLLECTION_SIZE = 50;

    @Override
    public boolean supports(String language) {
        return "java".equalsIgnoreCase(language);
    }

    @Override
    public TraceResponse run(TraceRequest request) throws Exception {
        String code = request.code();
        if (code == null || code.trim().isEmpty()) {
            return TraceResponse.error("EMPTY_CODE", "java", "No code provided.");
        }

        String className = extractClassName(code);
        if (className == null) {
            className = "Main";
            code = "public class Main {\n" + code + "\n}";
        }

        // Ensure any auxiliary classes/records/interfaces in the file are not declared public (Java requires public class to match file name)
        code = code.replaceAll("\\bpublic\\s+(class|record|interface|enum)\\s+(?!" + Pattern.quote(className) + "\\b)([A-Za-z0-9_]+)", "$1 $2");

        Path tempDir = Files.createTempDirectory("dsa_java_");
        Path sourceFile = tempDir.resolve(className + ".java");

        try {
            Files.writeString(sourceFile, code, StandardCharsets.UTF_8);

            // Step 1: Compile with debug symbols (-g)
            ByteArrayOutputStream compileErrStream = new ByteArrayOutputStream();
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int compileResult;
            if (compiler != null) {
                compileResult = compiler.run(null, null, compileErrStream, "-g", "-d", tempDir.toString(), sourceFile.toString());
            } else {
                // Fallback to javac CLI
                Process javacProc = new ProcessBuilder("javac", "-g", "-d", tempDir.toString(), sourceFile.toString())
                        .redirectErrorStream(true)
                        .start();
                compileResult = javacProc.waitFor();
                compileErrStream.write(javacProc.getInputStream().readAllBytes());
            }

            if (compileResult != 0) {
                String errorOutput = compileErrStream.toString(StandardCharsets.UTF_8);
                return TraceResponse.error("COMPILATION_ERROR", "java", errorOutput);
            }

            // Step 2: Launch JDI Debugger Session
            return executeWithJdi(className, tempDir, request.input(), request.maxSteps());

        } finally {
            deleteDirectory(tempDir.toFile());
        }
    }

    private TraceResponse executeWithJdi(String className, Path classpathDir, String stdinData, int maxSteps) {
        List<TraceStep> steps = new ArrayList<>();
        StringBuilder totalStdout = new StringBuilder();
        StringBuilder totalStderr = new StringBuilder();
        int lastStdoutPos = 0;
        String status = "SUCCESS";
        String errorMessage = null;

        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("options").setValue("-Dfile.encoding=UTF-8 -cp " + classpathDir.toAbsolutePath());
        arguments.get("main").setValue(className);
        arguments.get("suspend").setValue("true");

        VirtualMachine vm = null;
        Process vmProcess = null;
        Thread stdoutReader = null;
        Thread stderrReader = null;

        try {
            vm = connector.launch(arguments);
            vmProcess = vm.process();

            // Feed stdin
            if (stdinData != null && !stdinData.isEmpty()) {
                OutputStream os = vmProcess.getOutputStream();
                os.write(stdinData.getBytes(StandardCharsets.UTF_8));
                if (!stdinData.endsWith("\n")) {
                    os.write("\n".getBytes(StandardCharsets.UTF_8));
                }
                os.flush();
                try {
                    os.close();
                } catch (IOException ignored) {}
            } else {
                try {
                    vmProcess.getOutputStream().close();
                } catch (IOException ignored) {}
            }

            // Capture stdout
            InputStream is = vmProcess.getInputStream();
            stdoutReader = new Thread(() -> {
                try {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        synchronized (totalStdout) {
                            totalStdout.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                        }
                    }
                } catch (IOException ignored) {}
            });
            stdoutReader.setDaemon(true);
            stdoutReader.start();

            // Capture stderr
            InputStream es = vmProcess.getErrorStream();
            stderrReader = new Thread(() -> {
                try {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = es.read(buffer)) != -1) {
                        synchronized (totalStderr) {
                            totalStderr.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                        }
                    }
                } catch (IOException ignored) {}
            });
            stderrReader.setDaemon(true);
            stderrReader.start();

            // Setup ClassPrepareRequest
            EventRequestManager erm = vm.eventRequestManager();
            ClassPrepareRequest classReq = erm.createClassPrepareRequest();
            classReq.addClassExclusionFilter("java.*");
            classReq.addClassExclusionFilter("javax.*");
            classReq.addClassExclusionFilter("sun.*");
            classReq.addClassExclusionFilter("com.sun.*");
            classReq.addClassExclusionFilter("jdk.*");
            classReq.enable();

            EventQueue queue = vm.eventQueue();
            boolean running = true;
            int stepCount = 0;

            vm.resume();

            while (running) {
                if (vmProcess != null && !vmProcess.isAlive()) {
                    running = false;
                    break;
                }

                EventSet eventSet = queue.remove(2000); // 2 second event timeout
                if (eventSet == null) {
                    if (vmProcess != null && !vmProcess.isAlive()) {
                        running = false;
                        break;
                    }
                    status = "TIMEOUT";
                    errorMessage = "Execution timed out waiting for debug events.";
                    break;
                }

                for (Event event : eventSet) {
                    if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                        running = false;
                        break;
                    }

                    if (event instanceof ClassPrepareEvent classPrepareEvent) {
                        // In JDI, only one StepRequest is allowed per thread.
                        // Remove any existing step request on this thread first to avoid DuplicateRequestException.
                        List<StepRequest> existing = new ArrayList<>(erm.stepRequests());
                        for (StepRequest sr : existing) {
                            if (sr.thread().equals(classPrepareEvent.thread())) {
                                erm.deleteEventRequest(sr);
                            }
                        }
                        StepRequest stepReq = erm.createStepRequest(classPrepareEvent.thread(), StepRequest.STEP_LINE, StepRequest.STEP_INTO);
                        stepReq.addClassExclusionFilter("java.*");
                        stepReq.addClassExclusionFilter("javax.*");
                        stepReq.addClassExclusionFilter("sun.*");
                        stepReq.addClassExclusionFilter("com.sun.*");
                        stepReq.addClassExclusionFilter("jdk.*");
                        stepReq.enable();
                    } else if (event instanceof StepEvent stepEvent) {
                        stepCount++;
                        if (stepCount > maxSteps) {
                            status = "STEP_LIMIT_EXCEEDED";
                            errorMessage = "Execution reached maximum allowed steps (" + maxSteps + ")";
                            running = false;
                            break;
                        }

                        Location loc = stepEvent.location();
                        int line = loc.lineNumber();
                        String function = loc.method().name();

                        // Call stack
                        List<com.visualizer.model.StackFrame> callStack = new ArrayList<>();
                        try {
                            for (com.sun.jdi.StackFrame sf : stepEvent.thread().frames()) {
                                String declName = sf.location().declaringType().name();
                                if (!declName.startsWith("java.") && !declName.startsWith("javax.") && !declName.startsWith("sun.") && !declName.startsWith("jdk.")) {
                                    callStack.add(new com.visualizer.model.StackFrame(sf.location().method().name(), sf.location().lineNumber()));
                                }
                            }
                        } catch (IncompatibleThreadStateException ignored) {}
                        Collections.reverse(callStack);

                        // Variables
                        Map<String, VariableValue> variables = new LinkedHashMap<>();
                        try {
                            com.sun.jdi.StackFrame currentFrame = stepEvent.thread().frame(0);
                            List<LocalVariable> visibleVars = currentFrame.visibleVariables();
                            Map<LocalVariable, Value> values = currentFrame.getValues(visibleVars);

                            for (Map.Entry<LocalVariable, Value> entry : values.entrySet()) {
                                String varName = entry.getKey().name();
                                if ("args".equals(varName)) continue; // skip String[] args in main
                                variables.put(varName, serializeJdiValue(entry.getValue(), 0));
                            }
                        } catch (Exception ignored) {}

                        // Stdout
                        String currentOut;
                        synchronized (totalStdout) {
                            currentOut = totalStdout.toString();
                        }
                        String delta = currentOut.substring(lastStdoutPos);
                        lastStdoutPos = currentOut.length();

                        TraceStep traceStep = new TraceStep(
                                stepCount,
                                line,
                                "line",
                                function,
                                callStack,
                                variables,
                                delta,
                                currentOut
                        );
                        steps.add(traceStep);
                    }
                }

                if (running) {
                    eventSet.resume();
                } else {
                    try {
                        eventSet.resume();
                    } catch (Exception ignored) {}
                }
            }

        } catch (VMDisconnectedException e) {
            // Normal program termination
        } catch (VMStartException e) {
            status = "RUNTIME_ERROR";
            String procErr = "";
            try {
                if (e.process() != null) {
                    byte[] errBytes = e.process().getErrorStream().readAllBytes();
                    if (errBytes.length > 0) {
                        procErr = new String(errBytes, StandardCharsets.UTF_8).trim();
                    }
                }
            } catch (Exception ignored) {}
            if (!procErr.isEmpty()) {
                errorMessage = procErr;
            } else {
                errorMessage = "Failed to launch Java debuggee process: " + e.getMessage();
            }
        } catch (IllegalConnectorArgumentsException e) {
            status = "RUNTIME_ERROR";
            errorMessage = "JDI connector argument error: " + e.getMessage();
        } catch (Exception e) {
            status = "RUNTIME_ERROR";
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        } finally {
            if (vm != null) {
                try {
                    vm.exit(0);
                } catch (Exception ignored) {}
            }
            if (vmProcess != null) {
                vmProcess.destroyForcibly();
            }
        }

        String stderrStr = totalStderr.toString().trim();
        if (errorMessage == null && !stderrStr.isEmpty()) {
            if (stderrStr.contains("Exception") || stderrStr.contains("Error") || steps.isEmpty()) {
                status = "RUNTIME_ERROR";
                errorMessage = stderrStr;
            }
        }

        return new TraceResponse(
                status,
                "java",
                errorMessage,
                steps.size(),
                totalStdout.toString(),
                steps
        );
    }

    private static final int MAX_OBJECT_DEPTH = 8;

    private VariableValue serializeJdiValue(Value val, int depth) {
        if (depth > MAX_OBJECT_DEPTH) {
            return new VariableValue("truncated", null, "...");
        }

        if (val == null) {
            return new VariableValue("null", null, "null");
        }

        if (val instanceof PrimitiveValue prim) {
            if (prim instanceof BooleanValue b) {
                return new VariableValue("boolean", b.value(), String.valueOf(b.value()));
            } else if (prim instanceof IntegerValue i) {
                return new VariableValue("int", i.value(), String.valueOf(i.value()));
            } else if (prim instanceof LongValue l) {
                return new VariableValue("long", l.value(), String.valueOf(l.value()));
            } else if (prim instanceof DoubleValue d) {
                return new VariableValue("double", d.value(), String.valueOf(d.value()));
            } else if (prim instanceof FloatValue f) {
                return new VariableValue("float", f.value(), String.valueOf(f.value()));
            } else if (prim instanceof CharValue c) {
                return new VariableValue("char", String.valueOf(c.value()), "'" + c.value() + "'");
            } else if (prim instanceof ByteValue b) {
                return new VariableValue("byte", b.value(), String.valueOf(b.value()));
            } else if (prim instanceof ShortValue s) {
                return new VariableValue("short", s.value(), String.valueOf(s.value()));
            }
        }

        if (val instanceof StringReference strRef) {
            String str = strRef.value();
            String truncated = str.length() > 100 ? str.substring(0, 100) + "..." : str;
            return new VariableValue("String", truncated, "\"" + truncated + "\"");
        }

        if (val instanceof ArrayReference arrRef) {
            int len = arrRef.length();
            int limit = Math.min(len, MAX_COLLECTION_SIZE);
            List<Value> rawValues = arrRef.getValues(0, limit);
            List<VariableValue> elements = new ArrayList<>();
            boolean isMatrix = false;

            for (Value elem : rawValues) {
                if (elem instanceof ArrayReference) {
                    isMatrix = true;
                }
                elements.add(serializeJdiValue(elem, depth + 1));
            }

            return VariableValue.array(len, isMatrix, elements);
        }

        if (val instanceof ObjectReference objRef) {
            ReferenceType refType = objRef.referenceType();

            // Handle java.util.ArrayList directly as array for clean visualizer rendering
            if (refType.name().equals("java.util.ArrayList")) {
                try {
                    Field sizeField = refType.fieldByName("size");
                    Field dataField = refType.fieldByName("elementData");
                    if (sizeField != null && dataField != null) {
                        Value sizeVal = objRef.getValue(sizeField);
                        Value dataVal = objRef.getValue(dataField);
                        if (sizeVal instanceof PrimitiveValue pv && dataVal instanceof ArrayReference dataArr) {
                            int size = pv.intValue();
                            int limit = Math.min(size, MAX_COLLECTION_SIZE);
                            List<Value> rawValues = dataArr.getValues(0, limit);
                            List<VariableValue> elements = new ArrayList<>();
                            for (Value elem : rawValues) {
                                elements.add(serializeJdiValue(elem, depth + 1));
                            }
                            return VariableValue.array(size, false, elements);
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Custom object (e.g. ListNode, TreeNode, Node)
            Map<String, VariableValue> fields = new LinkedHashMap<>();
            try {
                for (Field field : refType.visibleFields()) {
                    if (field.isStatic()) continue;
                    Value fVal = objRef.getValue(field);
                    fields.put(field.name(), serializeJdiValue(fVal, depth + 1));
                }
            } catch (Exception ignored) {}

            return VariableValue.object(refType.name(), String.valueOf(objRef.uniqueID()), fields);
        }

        return new VariableValue("unknown", null, val.toString());
    }

    private String extractClassName(String code) {
        String clean = stripComments(code);
        // Priority 1: Solution (standard LeetCode class where driver main is injected)
        if (Pattern.compile("\\bclass\\s+Solution\\b").matcher(clean).find()) {
            return "Solution";
        }
        // Priority 2: public class X
        Matcher pcm = Pattern.compile("public\\s+class\\s+([A-Za-z0-9_]+)").matcher(clean);
        if (pcm.find()) {
            return pcm.group(1);
        }
        // Priority 3: Main (standard CP class)
        if (Pattern.compile("\\bclass\\s+Main\\b").matcher(clean).find()) {
            return "Main";
        }
        // Priority 4: First class definition
        Matcher cm = Pattern.compile("\\bclass\\s+([A-Za-z0-9_]+)").matcher(clean);
        if (cm.find()) {
            return cm.group(1);
        }
        return null;
    }

    private String stripComments(String code) {
        if (code == null) return "";
        return code.replaceAll("(?s)/\\*.*?\\*/", " ")
                   .replaceAll("//.*", " ");
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
