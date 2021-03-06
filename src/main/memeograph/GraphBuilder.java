package memeograph;

import com.sun.jdi.*;
import com.sun.jdi.Value;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;

public class GraphBuilder {

    private VirtualMachine vm;
    private Graph currentgraph;

    private EventSet event_set = null;
    private Iterator<Event> event_iter = null;

    public GraphBuilder(VirtualMachine vm)
    {
       this.vm = vm;

       //We need this catch the loading of classes
       ClassPrepareRequest classEvent = vm.eventRequestManager().createClassPrepareRequest();
       classEvent.addClassExclusionFilter("java.*");
       classEvent.addClassExclusionFilter("javax.*");
       classEvent.addClassExclusionFilter("sun.*");
       classEvent.enable();

        try {
            event_set = vm.eventQueue().remove();
            event_iter = event_set.iterator();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
       step();
    }

    /*
     * Clear the system of any memory of a graph, then interrogate the
     * system for its current state
     */
    private void interrogate(){
        currentgraph = new Graph();
        //Go through all of the threads
        for (ThreadReference thread : vm.allThreads()) {
            if (thread.threadGroup().name().equals("system")) {
                continue;
            }
            try {
                ThreadHeader header = new ThreadHeader(thread);
                currentgraph.getSuperNode().addThread(header);
                StackObject prev = null;

                for (int depth = thread.frameCount() - 1; depth > 0; depth--) {
                    StackObject so = exploreStackFrame(thread.frame(depth), depth);
                    //System.out.println("StackObject: " + so);
                    if (prev != null) {
                        prev.setNextFrame(so);
                    } else {
                        header.setFrame(so);
                    }
                    prev = so;
                }
            } catch (IncompatibleThreadStateException ex) {
                ex.printStackTrace();
            }
        }

        //System.out.println("Super Node! is: " + supernode);
    }

    private StackObject exploreStackFrame(StackFrame frame, int depth) throws IncompatibleThreadStateException{
            StackObject tree = getStackFrame(frame, depth);
            ObjectReference thisor = frame.thisObject();
            if (thisor != null) {
                tree.addHeapObject(currentgraph.getHeapObject(thisor));
            }
            try {
                List<LocalVariable> locals = frame.visibleVariables();
                LocalVariable[] localvars = locals.toArray(new LocalVariable[] {});
                Arrays.sort(localvars);
                for (LocalVariable var : localvars) {
                        Value val = frame.getValue(var);
                        if (val != null && val.type() != null)
                                tree.addHeapObject(currentgraph.getHeapObject(val));
                }
            } catch (AbsentInformationException ex) {
                return tree;
            }
            return tree;
    }

    /**
    * The String representation of a Stack Frame. NOTE: This needs to be unique
    * for every object. Otherwise building the graph will probably go wrong.
    */
    private String StackFrame2String(int depth, ThreadReference t) throws IncompatibleThreadStateException{
        if (t == null) {
            throw new NullPointerException("Thread Reference cannot be null.");
        }
        int count = t.frameCount() - depth - 1;
        try {
            return "Thread(" + t.name() + ") StackFrame(" + count + ") " + t.frame(depth).location().method().name();
        } catch (IncompatibleThreadStateException itse) {
            return "Thread(" + t.name() + ") StackFrame(" + count + ")";
        }
    }

    private StackObject getStackFrame(StackFrame f, int depth) throws IncompatibleThreadStateException{
        if (!currentgraph.getStackMap().containsKey(f)){
                currentgraph.getStackMap().put(f, new StackObject(StackFrame2String(depth, f.thread())));
        }
        return currentgraph.getStackMap().get(f);
    }

    public Graph currentGraph(){
        return currentgraph;
    }

    public Graph step(){
        try {
            while(event_iter.hasNext()){
                Event event = event_iter.next();
                if (event instanceof VMStartEvent){
                }else if (event instanceof ModificationWatchpointEvent) {
                    interrogate();
                    return currentGraph();
                }else if (event instanceof ClassPrepareEvent){
                    ClassPrepareEvent cpe = (ClassPrepareEvent) event;
                    for (Field field : cpe.referenceType().allFields()) {
                        if (field.name().equals("memeopoint")) {
                            ModificationWatchpointRequest mwr = vm.eventRequestManager().createModificationWatchpointRequest(field);
                            mwr.enable();
                        }
                    }
                }else{
                    System.err.println("Caught the wrong watchpoint" + event.getClass().getName());
                }
            }
            //Looks like we need to try again
            event_set.resume();
            event_set = vm.eventQueue().remove();
            event_iter = event_set.iterator();
            return step();
        }catch(InterruptedException ei){
            System.err.println("Couldn't wait for remove()");
            ei.printStackTrace();
        }
        //This should NEVER happen
        throw new RuntimeException("Couldn't step");
    }
}


