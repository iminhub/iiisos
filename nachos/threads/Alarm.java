package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
    boolean intStatus = Machine.interrupt().disable();
    long time = Machine.timer().getTime();
    TThread tt = waitQueue.pollFirst();
    while(tt != null) {
    	if(tt.wakeTime <= time)
    		tt.getThread().ready();
    	else {
    		waitQueue.add(tt);
    		break;
    	}
    	tt = waitQueue.pollFirst();
    }
	KThread.currentThread().yield();
	Machine.interrupt().restore(intStatus);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	// for now, cheat just to get something working (busy waiting is bad)
    boolean intStatus = Machine.interrupt().disable();
	long wakeTime = Machine.timer().getTime() + x;
	waitQueue.add(new TThread(KThread.currentThread(), wakeTime));
	KThread.sleep();
	Machine.interrupt().restore(intStatus);
    }
    
    private class TThread implements Comparable<TThread> {
    	private KThread thread = null;
    	public long wakeTime;
    	
    	public TThread(KThread thread, long wakeTime) {
    		this.thread = thread;
    		this.wakeTime = wakeTime;
    	}
    	
    	public KThread getThread() {
    		return thread;
    	}
    	
    	public int compareTo(TThread t) {
    		if(wakeTime > t.wakeTime) {
    			return 1;
    		} else if(wakeTime == t.wakeTime) {
    			return thread.compareTo(t.thread);
    		} else
    			return -1;
    	}
    	
    }
    
    private static TreeSet<TThread> waitQueue = new TreeSet<TThread>();
    
}
