package nachos.threads;

import java.util.Comparator;
import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		waitQueue = new PriorityQueue<Waiter>(10, new Comparator<Waiter>(){
			public int compare(Waiter a, Waiter b){
				return a.time < b.time ? -1 : 1;
			}
		});
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		KThread.yield();
		boolean intStatus = Machine.interrupt().disable();
		while(!waitQueue.isEmpty() && waitQueue.peek().time <= Machine.timer().getTime()){
			waitQueue.poll().thread.ready();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x
	 *            the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable();
		waitQueue.add(new Waiter(Machine.timer().getTime() + x, KThread.currentThread()));
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
	}
	

	private PriorityQueue<Waiter> waitQueue;
	
	protected class Waiter{
		public Long time;
		public KThread thread;
		public Waiter(Long a, KThread b){
			time=a;
			thread=b;
		}
	}
}

