package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
    return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = Integer.MAX_VALUE;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    
    /**CORRECTION: changed from protected to private, same change in PriorityScheduler.java*/
    private ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }
    
    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue implements Comparable<PriorityQueue> {
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    //long time = Machine.timer().getTime();
	    time++;
	    getThreadState(thread).waitForAccess(this, time);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    if (transferPriority) {
	    	getThreadState(thread).acquire(this);
		    owner = thread;
	    }
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    
	    ThreadState next = pickNextThread();
	    if (owner!=null) {
	    	getThreadState(owner).release(this);
	    	owner=null;
	    }
	    if (next!=null) {
	    	waitBy.remove(next);
	    	next.init();
	    	updateDP();
	    	acquire(next.getThread());
	    	return next.getThread();
	    }
	    return null;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    if (waitBy.isEmpty()) return null;
	    
	    int ticket = 0;
		for (ThreadState thread : waitBy)
			ticket += thread.getEffectivePriority();
		ticket = (int) Math.floor(Math.random() * ticket);
		for (ThreadState thread : waitBy) {
			ticket -= thread.getEffectivePriority();
			if (ticket < 0) return thread;
		}
		return waitBy.first();
	}
	
	public int getDP() {
		return DP;
	}
	
	public int compareTo(PriorityQueue queue) {
		if (DP > queue.DP) return -1;
		if (DP < queue.DP) return 1;
		if (ID > queue.ID) return 1;
		if (ID < queue.ID) return -1;
		return 0;
	}
	
	public void waitByRemove(KThread thread) {
		waitBy.remove(getThreadState(thread));
	}

	public void updateEP(KThread thread) {
		waitBy.add(getThreadState(thread));
		updateDP();
	}

	protected void updateDP() {
		int tmpDP = 0;

		if (waitBy.isEmpty() || (!transferPriority))
			tmpDP = 0;
		else
			for (ThreadState thread : waitBy)
				tmpDP += thread.getEffectivePriority();

		if (tmpDP == DP)
			return;

		DP = tmpDP;
		if (owner != null) {
			getThreadState(owner).acquiresRemove(this);
			getThreadState(owner).updateDP(this);
		}
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
	protected int DP = 0;
	protected TreeSet<ThreadState> waitBy = new TreeSet<ThreadState>();
	protected KThread owner = null;
	protected int ID = numOfQueue++;
	protected long time = 0;
    }
    
    protected int numOfQueue=0;

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState implements Comparable<ThreadState>{
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    
	    setPriority(priorityDefault);
	}
	
	public KThread getThread() {
		return thread;
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    return EP;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    this.priority = priority;
	    
	    updateEP();
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue, long time) {
	    this.time = time;
	    waitFor = waitQueue;
	    waitQueue.updateEP(thread);
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    acquires.add(waitQueue);
	    updateEP();
	}
	public void release(PriorityQueue waitQueue) {
		acquires.remove(waitQueue);
		updateEP();
	}
	
	public int compareTo(ThreadState s) {
		if (EP>s.EP) return -1;
		if (EP<s.EP) return 1;
		if (time>s.time) return 1;
		if (time<s.time) return -1;
		return 0;
	}
	
	public void acquiresRemove(PriorityQueue waitQueue) {
		acquires.remove(waitQueue);
	}
	
	public void updateDP(PriorityQueue waitQueue) {
		acquires.add(waitQueue);
		updateEP();
	}
	
	public int max(int x, int y) {
		return x>y ? x : y;
	}
	
	protected void updateEP() {
		int tmpEP = priority;
		if (!acquires.isEmpty())
			for (PriorityQueue q : acquires)
				tmpEP += q.getDP();

		if (tmpEP == EP)
			return;

		EP = tmpEP;
		if (waitFor != null) {
			waitFor.waitByRemove(thread);
			waitFor.updateEP(thread);
		}
	}
	public void init() {
		waitFor = null;
	}

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority = priorityDefault;
	protected int EP = priorityDefault;
	protected long time = 0;
	protected PriorityQueue waitFor = null;
	protected TreeSet<PriorityQueue> acquires = new TreeSet<PriorityQueue>();
    }
}
