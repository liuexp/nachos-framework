package nachos.threads;

import nachos.machine.Lib;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

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
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
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
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from
	 *            waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		// implement me
		return null;
	}
	public static void updatePriority(ThreadState cur) {
		//Lib.assertTrue(Machine.interrupt().disabled());
		while(cur != null){
			int p = cur.priority;
			for (PriorityQueue t  : cur.owningQueue){
				if (!t.transferPriority || t.waitQueue.isEmpty())continue;
				int tmp = t.waitQueue.first().effectivePriority;
				p = tmp > p ? tmp : p;
			}
			if(p == cur.effectivePriority)return;
			else if(cur.waitingFor == null){
				cur.effectivePriority = p;
				return;
			}else {
				boolean tmp = cur.waitingFor.waitQueue.remove(cur);
				Lib.assertTrue(tmp);
				cur.effectivePriority=p;
				cur.waitingFor.waitQueue.add(cur);
				if(cur.waitingFor.transferPriority)
					cur = cur.waitingFor.owner;
				else return;
			}
		}
	}
}
