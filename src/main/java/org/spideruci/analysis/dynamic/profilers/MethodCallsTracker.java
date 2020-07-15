package org.spideruci.analysis.dynamic.profilers;

import org.spideruci.analysis.dynamic.Profiler;
import org.spideruci.analysis.dynamic.api.EmptyProfiler;
import org.spideruci.analysis.trace.EventType;
import org.spideruci.analysis.trace.TraceEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class MethodCallsTracker extends EmptyProfiler {

  LinkedHashMap<String, Integer> lineProfileCounts = new LinkedHashMap<>();;
  HashMap<String, ClassAndMethod> parentMap = new HashMap<>();
  ArrayList<String> instrumentedMethods = new ArrayList<>();
  HashMap<String, String> instructionIdToDesc = new HashMap<>();

  HashMap<String, Long> callToCaller = new HashMap<>();

  @Override
  public String description() {
    return "MethodCallsTracker";
  }

  @Override
  public void willInstrumentMethod(final TraceEvent e)  {
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

    instructionIdToDesc.put(String.valueOf(id), desc);
  }

  @Override
  public void profileMethodInvoke(final TraceEvent e) {
    if (e.getType() != EventType.$$$) {
      return;
    }

    if (e.getExecInsnType() != EventType.$invoke$) {
      return;
    }

    String insnId = e.getExecInsnEventId();
    String invokeDesc = instructionIdToDesc.get(insnId);

    if (invokeDesc == null) {
      invokeDesc = "null";
    }

    if (callToCaller.containsKey(invokeDesc)) {
      long count = callToCaller.get(invokeDesc);
      callToCaller.put(invokeDesc, count + 1);
    } else {
      callToCaller.put(invokeDesc, 1L);
    }
  }

  @Override
  public void endProfiling() {
    for (String k : callToCaller.keySet()) {
      System.out.println(k + " ... " + callToCaller.get(k));
    }

    callToCaller.clear();
  }


}
