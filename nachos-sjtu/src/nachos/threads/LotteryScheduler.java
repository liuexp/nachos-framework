package nachos.threads;

import nachos.machine.Lib;

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
		return new LotteryQueue(transferPriority);
	}
	
	public static final int priorityMaximum = Integer.MAX_VALUE;
	public static final int priorityMinimum = 1;
	
	public static void updatePriority(ThreadState cur) {
		//Lib.assertTrue(Machine.interrupt().disabled());
		while(cur != null){
			int p = cur.priority;
			for (PriorityQueue t  : cur.owningQueue){
				if (!t.transferPriority || t.waitQueue.isEmpty())continue;
				for (ThreadState s : t.waitQueue){
					p += s.effectivePriority;
				}
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
	
	protected class LotteryQueue extends PriorityQueue{
		public LotteryQueue(boolean transferPriority){
			super(transferPriority);
		}
		public ThreadState pickNextThread(){
			int cnt = 1;
			for (ThreadState t:waitQueue) cnt += t.effectivePriority;
			int ticket = Lib.random(cnt);
			cnt = 1;
			for (ThreadState t:waitQueue){
				cnt+=t.effectivePriority;
				if(cnt>=ticket) {
					//System.out.println("picked "+ t.thread.toString());
					return t;
					
				}
			}
			return null;
		}
		
	}
}
