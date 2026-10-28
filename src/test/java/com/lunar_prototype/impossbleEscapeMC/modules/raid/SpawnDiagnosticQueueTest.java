package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

public final class SpawnDiagnosticQueueTest {
    public static void main(String[] args) {
        var tasks=IntStream.range(0,10).boxed().toList();
        var queue=new SpawnDiagnosticQueue<>(tasks,8);
        int[] steps=new int[10];
        var finished=new HashSet<Integer>();
        // 最初の1組が重くても、後続の簡単な9組は全て先に完了できる。
        for(int op=0;op<100;op++) {
            Integer task=queue.next();
            check(task!=null,"slow task still pending");
            steps[task]++;
            if(task!=0&&steps[task]>=3){queue.complete(task);finished.add(task);}
        }
        check(finished.size()==9&&!finished.contains(0),"one slow route cannot block all later easy routes");
        check(queue.remaining().equals(List.of(0)),"timeout only marks unfinished route, never completed ones");
        queue.complete(0);check(queue.next()==null&&queue.remaining().isEmpty(),"all complete");

        var bounded=new SpawnDiagnosticQueue<>(tasks,8);
        for(int op=0;op<32;op++)check(bounded.next()<8,"only eight unfinished searches admitted");
        check(new HashSet<>(bounded.remaining()).size()==10,"queued and active routes retained exactly once");
        bounded.complete(0);
        boolean admitted=false;
        for(int op=0;op<8;op++)if(bounded.next()==8)admitted=true;
        check(admitted,"finishing a search admits next waiting pair");
        check(new SpawnDiagnosticQueue<Integer>(List.of(),8).next()==null,"zero pairs terminate");
        System.out.println("SpawnDiagnosticQueueTest OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
