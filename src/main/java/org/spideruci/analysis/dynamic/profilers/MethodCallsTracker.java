package org.spideruci.analysis.dynamic.profilers;

import org.spideruci.analysis.dynamic.Profiler;
import org.spideruci.analysis.dynamic.api.EmptyProfiler;
import org.spideruci.analysis.statik.instrumentation.Config;
import org.spideruci.analysis.trace.EventType;
import org.spideruci.analysis.trace.InvokeInsnExecEvent;
import org.spideruci.analysis.trace.MethodDecl;
import org.spideruci.analysis.trace.TraceEvent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.thoughtworks.xstream.*;

public class MethodCallsTracker extends EmptyProfiler {

  public static void init() {
    Config.profiler = new MethodCallsTracker();
    // Profiler.entryClass = "org/apache/commons/cli";
  }

  private static String instrumentationScope = "";

  LinkedHashMap<String, Integer> lineProfileCounts = new LinkedHashMap<>();;
  HashMap<String, ClassAndMethod> parentMap = new HashMap<>();
  ArrayList<String> instrumentedMethods = new ArrayList<>();
  HashMap<String, String> instructionIdToDesc = new HashMap<>();

  HashMap<String, Long> callToCaller = new HashMap<>();
  HashMap<String, Long> callToCaller_allup = new HashMap<>();

  HashMap<String, HashMap<String, ArrayList<MethodArgument>>> valueMap = new HashMap<>();

  @Override
  public boolean shouldInstrument(String className) {
    boolean shouldInstrument = className.startsWith(MethodCallsTracker.instrumentationScope);

    if (shouldInstrument) {
      System.out.println(className + " " + shouldInstrument);
    }
    
    return shouldInstrument;
  }

  @Override
  public void setInstrumentationScope(String scope) {
    MethodCallsTracker.instrumentationScope = scope;
  }

  @Override
  public String getLogConfig() {
    return "exi";
  }

  @Override
  public boolean canUseFrames() {
    return true;
  }

  @Override
  public String description() {
    return "MethodCallsTracker";
  }

  @Override
  public void willInstrumentMethod(final MethodDecl e)  {
    if (e.getType() != EventType.$$method$$) {
      return;
    }

    String methodName = e.getDeclName();
    String className = e.getDeclOwner();
    long id = e.getId();

    ClassAndMethod cnm = new ClassAndMethod(className, methodName);
    parentMap.put(String.valueOf(id), cnm);
    instrumentedMethods.add(cnm.toString());
  }

  @Override
  public void profileMethodArgumentValue(final Object value, final int argIndex, final int argCount, final String methodName, final boolean isStatic, String corelString) {
    if (methodName.contains("Test.")) {
      // poor-person's check for Test Case classes
      return;
    }

    if (!valueMap.containsKey(methodName)) {
      valueMap.put(methodName, new HashMap<>());
    }

    HashMap<String, ArrayList<MethodArgument>> methodArgumentSets = valueMap.get(methodName);
    if (methodArgumentSets == null) { return; }

    ArrayList<MethodArgument> values;
    if (methodArgumentSets.containsKey(corelString)) {
      values = methodArgumentSets.get(corelString);
    } else {
      values = new ArrayList<>();
      methodArgumentSets.put(corelString, values);
    }

    XStream stream = new XStream();
    String stringValue = stream.toXML(value);
    var arg = new MethodArgument(stringValue, argIndex, corelString, methodName, isStatic, argCount);
    values.add(arg);
    // Profiler.REAL_OUT.println(stream.toXML(value)); 
  }

  @Override
  public void willInstrumentCode(final TraceEvent e) {
    if (e.getType() == EventType.$$$ || e.getType() == EventType.$$method$$) {
      return;
    }

    if (e.getType() != EventType.$invoke$) {
      return;
    }

    long id = e.getId();

    String declHostId = String.valueOf(e.getInsnDeclHostId());

    if (!parentMap.containsKey(declHostId)) {
      return;
    }

    ClassAndMethod declaringParent = parentMap.get(declHostId);

    String operand = e.getInsnOperand1() + "." + e.getInsnOperand2() + e.getInsnOperand3();
    String desc = declaringParent.toString() + " --> " + operand;

    if (operand.contains("<init>") 
        && operand.startsWith(MethodCallsTracker.instrumentationScope)
        && (
          declaringParent.className.startsWith(MethodCallsTracker.instrumentationScope)
          && !declaringParent.className.toLowerCase().contains("test")
        )
        && !declaringParent.methodName.contains("init>")
        && !e.getInsnOperand1().contains("Exception")) {
      instructionIdToDesc.put(String.valueOf(id), desc);
    }
  }

  @Override
  public void profileMethodInvoke(final InvokeInsnExecEvent e) {
    String insnId = e.insnEventId; 
    String invokeDesc = instructionIdToDesc.get(insnId);

    if (invokeDesc == null) {
      // invokeDesc = "null";
      return;
    }

    if (callToCaller.containsKey(invokeDesc)) {
      long count = callToCaller.get(invokeDesc);
      callToCaller.put(invokeDesc, count + 1);
    } else {
      callToCaller.put(invokeDesc, 1L);
    }
  }

  @Override
  public void endProfiling(String desc) {
    for (String k : callToCaller.keySet()) {
      // System.out.println(k + " ... " + callToCaller.get(k));

      if (callToCaller_allup.containsKey(k)) {
        long count = callToCaller_allup.get(k);
        callToCaller_allup.put(k, count + callToCaller.get(k));
      } else {
        callToCaller_allup.put(k, callToCaller.get(k));
      }

    }

    callToCaller.clear();
  }

  @Override
  public void emitLogs(final String traceName, final String logPath) {
    Profiler.REAL_OUT.println("END!!!");
    System.out.println("END!!!");

    for (String k : callToCaller_allup.keySet()) {
      Profiler.REAL_OUT.println(k);
    }

    Profiler.REAL_OUT.println("-=-=-=-=-=-=-=-=-=-=-=-");

    Path argLogDirPath = createArgLogDirectory(logPath);
    System.out.println(argLogDirPath);

    Profiler.REAL_OUT.println(argLogDirPath != null ? argLogDirPath.toFile().getAbsolutePath() : "null");

    for (String methodName : valueMap.keySet()) {
      HashMap<String, ArrayList<MethodArgument>> argumentSets = valueMap.get(methodName);
      Profiler.REAL_OUT.println("Method: " + methodName + " // " + argumentSets.size());
      for (String corelIds : argumentSets.keySet()) {
        try {
          Path argLogPath = argLogDirPath.resolve(corelIds + ".log");
          PrintStream outPrintStream = new PrintStream(argLogPath.toFile());

          ArrayList<MethodArgument> values = argumentSets.get(corelIds);
          if (values == null) {
            outPrintStream.println("arg values list is null");
            continue;
          }

          if (values.isEmpty()) {
            outPrintStream.println("arg values list is empty");
            continue;
          }

          var firstArgValue = values.get(0);

          outPrintStream.println(firstArgValue.methodName());
          outPrintStream.println("isStatic:" + (firstArgValue.methodIsStatic() ? 0 : 1));
          outPrintStream.println(firstArgValue.argCount());
          outPrintStream.println(firstArgValue.corelId());

          for (MethodArgument argValue : values) {
            outPrintStream.println("Argument (" + argValue.corelId() + ") : "+ argValue.index() + "/" + (argValue.argCount() - 1));
            outPrintStream.println(argValue.value());
          }

          outPrintStream.flush();
          outPrintStream.close();
        } catch (FileNotFoundException | NullPointerException e) {
          e.printStackTrace();
        }
      }
    }

    valueMap.clear();
  }

  private Path createArgLogDirectory(final String logPath) {
    String logDirPathString = logPath.substring(0, logPath.length() - ".trc".length());
    Path logDirPath = Path.of(logDirPathString);
    System.out.println("[debug]" + logPath);
    System.out.println("[debug]" + logDirPath);

    try {
      return Files.createDirectories(logDirPath);
    } catch (IOException e) {
      Profiler.REAL_OUT.println("[emitLogs failure]: " + e.getMessage());
      return null;
    }
  }
}

record MethodArgument(String value, int index, String corelId, String methodName, boolean methodIsStatic, int argCount) {}