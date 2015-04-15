package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
	Lock lock = null;
	Condition2 wSpeaker = null, wListener = null;
	int as, al;
	int m;
	boolean received;
    public Communicator() {
    	lock = new Lock();
    	wSpeaker = new Condition2(lock);
    	wListener = new Condition2(lock);
    	as = 0;
    	al = 0;
    	received = false;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	lock.acquire();
    	while(as > 0) {
    		wSpeaker.wake();
    		wSpeaker.sleep();
    	}
    	as++;
    	m = word;
    	received = false;
    	while(!received) {
    		wListener.wake();
    		wSpeaker.sleep();
    	}
    	as--;
    	wListener.wake();
    	wSpeaker.wake();
    	lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	lock.acquire();
    	int result;
    	while(al > 0) {
    		wListener.wake();
    		wListener.sleep();
    	}
    	al++;
    	while(as == 0) {
    		wSpeaker.wake();
    		wListener.sleep();
    	}
    	while(received) {
    		wListener.sleep();
    		wSpeaker.wake();
    	}
    	result = m;
    	received = true;
    	wSpeaker.wake();
    	al--;
    	lock.release();
    	return result;
    }
}
