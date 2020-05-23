package org.spideruci.analysis.dynamic.profilers;

import org.spideruci.analysis.dynamic.api.EmptyProfiler;
import org.spideruci.analysis.dynamic.api.IProfiler;
import org.spideruci.analysis.trace.EventType;
import org.spideruci.analysis.trace.TraceEvent;
import org.spideruci.analysis.trace.events.props.InsnPropNames;

import java.util.*;

import static org.spideruci.analysis.dynamic.Profiler.REAL_OUT;

public class CoverageTracker extends EmptyProfiler implements IProfiler {
  
  LinkedHashMap<String, Integer> lineProfileCounts = new LinkedHashMap<>();;
  HashMap<Long, ClassAndMethod> parentMap = new HashMap<>();
  HashMap<String, Line> lineMap = new HashMap<>();
  ArrayList<String> instrumentedClasses = new ArrayList<>();
  ArrayList<String> instrumentedMethods = new ArrayList<>();

  @Override
  public String description() {
    return "CoverageTracker";
  }
  
  @Override
  public void startProfiling() {
    REAL_OUT.println("Starting coverage tracker");
  }
  
  @Override
  public void willInstrumentClass(final String className)  {
    instrumentedClasses.add(className);
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
    parentMap.put(id, cnm);
    instrumentedMethods.add(cnm.toString());
  }

  @Override
  public void willInstrumentCode(final TraceEvent e) {
    if (e.getType() != EventType.$line$) {
      return;
    }

    long id = e.getId();
    long declHostId = e.getInsnDeclHostId();

    if (!parentMap.containsKey(declHostId)) {
      return;
    }

    ClassAndMethod declaringParent = parentMap.get(declHostId);
    int lineNumber = e.getInsnLine();

    Line line = new Line(declaringParent, lineNumber);
    lineMap.put(String.valueOf(id), line);
  }

  @Override
  public void profileInsn(final TraceEvent e) {
    if (e.getExecInsnType() != EventType.$line$) {
      return;
    }

    String sourceLineInsnId = e.getExecInsnEventId();

    if (lineProfileCounts.containsKey(sourceLineInsnId)) {
      int count = lineProfileCounts.get(sourceLineInsnId);
      lineProfileCounts.put(sourceLineInsnId, count + 1);
    } else {
      lineProfileCounts.put(sourceLineInsnId, 1);
    }
  }
  
  @Override
  public void endProfiling() {
    REAL_OUT.println("ending coverage tracker");

    REAL_OUT.printf("Classes instrumented: %s\n", this.instrumentedClasses.size());
    REAL_OUT.printf("Methods instrumented: %s\n", this.instrumentedMethods.size());

    ArrayList<Map.Entry<String, Integer>> list = new ArrayList(this.lineProfileCounts.entrySet());
    list.sort(Map.Entry.comparingByValue());

    Collections.reverse(list);

    for (Map.Entry<String, Integer> lineCount : list) {
      String lineId = lineCount.getKey();

      if (lineId == null || lineId.isEmpty()) {
        continue;
      }
      
      int counter = lineCount.getValue(); // lineProfileCounts.get(lineId);

      if (lineMap.containsKey(lineId)) {
        Line l = lineMap.get(lineId);

        if (!l.className.contains("fop")) {
          continue;
        }


        REAL_OUT.println("Count: " + counter);
        REAL_OUT.println("  - " + l.className);
        REAL_OUT.println("  - " + l.methodName);
        REAL_OUT.println("  - Line: " + l.lineNumber);
      } else {
        REAL_OUT.println("LINE: \n  - " + lineId + "\n  - Coverage Counter:" + counter);
      }

    }
  }
}

class ClassAndMethod {
  String className;
  String methodName;

  public ClassAndMethod(String className, String methodName) {
    this.className = className;
    this.methodName = methodName;
  }

  @Override
  public String toString() {
    return className + "." + methodName;
  }
}

class Line {
  String className;
  String methodName;
  int lineNumber;

  public Line(ClassAndMethod classAndMethod, int lineNumber) {
    this.className = classAndMethod.className;
    this.methodName = classAndMethod.methodName;
    this.lineNumber = lineNumber;
  }
}
