package org.spideruci.analysis.dynamic.profilers;

import org.spideruci.analysis.dynamic.Profiler;
import org.spideruci.analysis.dynamic.api.EmptyProfiler;
import org.spideruci.analysis.statik.instrumentation.Config;
import org.spideruci.analysis.trace.EventType;
import org.spideruci.analysis.trace.InvokeInsnExecEvent;
import org.spideruci.analysis.trace.MethodDecl;
import org.spideruci.analysis.trace.TraceEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.thoughtworks.xstream.*;

public class MethodCallsTracker extends EmptyProfiler {

  public static void init() {
    Config.profiler = new MethodCallsTracker();
    // Profiler.entryClass = "org/apache/commons/cli";
  }

  public static String entryClass = "org/apache/commons/validator";

  LinkedHashMap<String, Integer> lineProfileCounts = new LinkedHashMap<>();;
  HashMap<String, ClassAndMethod> parentMap = new HashMap<>();
  ArrayList<String> instrumentedMethods = new ArrayList<>();
  HashMap<String, String> instructionIdToDesc = new HashMap<>();

  HashMap<String, Long> callToCaller = new HashMap<>();
  HashMap<String, Long> callToCaller_allup = new HashMap<>();

  HashMap<String, ArrayList<MethodArgument>> valueMap = new HashMap<>();

  @Override
  public boolean shouldInstrument(String className) {
    boolean shouldInstrument = className.startsWith(MethodCallsTracker.entryClass);

    if (shouldInstrument) {
      System.out.println(className + " " + shouldInstrument);
    }
    
    return shouldInstrument;
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
  public void profileMethodArgumentValue(final Object value, final int argIndex, final int argCount, final String methodName, String corelString) {
    ArrayList<MethodArgument> values;
    if (valueMap.containsKey(methodName)) {
      values = valueMap.get(methodName);
    } else {
      values = new ArrayList<>();
      valueMap.put(methodName, values);
    }

    XStream stream = new XStream();
    String stringValue = stream.toXML(value);
    var arg = new MethodArgument(stringValue, argIndex, corelString, methodName, argCount);
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
        && operand.startsWith(MethodCallsTracker.entryClass)
        && (
          declaringParent.className.startsWith(MethodCallsTracker.entryClass)
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
  public void emitLogs() {
    Profiler.REAL_OUT.println("END!!!");

    for (String k : callToCaller_allup.keySet()) {
      Profiler.REAL_OUT.println(k);
    }

    Profiler.REAL_OUT.println("-=-=-=-=-=-=-=-=-=-=-=-");

    for (String methodName : valueMap.keySet()) {
      ArrayList<MethodArgument> values = valueMap.get(methodName);
      if (values == null || values.isEmpty()) {
        continue;
      }

      Profiler.REAL_OUT.println("Method: " + methodName);
      for (MethodArgument argValue : values) {
        Profiler.REAL_OUT.println("Argument (" + argValue.corelId() + ") : "+ argValue.index() + "/" + (argValue.argCount() - 1));
        Profiler.REAL_OUT.println(argValue.value().indent(4));
      }

      Profiler.REAL_OUT.println();
    }
  }
}

record MethodArgument(String value, int index, String corelId, String methodName, int argCount) {}