package nachos.threads;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		isMsgSet=false;
		lock = new Lock();
		condListener = new Condition(lock);
		condSpeaker = new Condition(lock);
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Do not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word
	 *            the integer to transfer.
	 */
	public void speak(int word) {
		lock.acquire();
		while (isMsgSet) condSpeaker.sleep();
		msg = word;
		isMsgSet = true;
		condListener.wake();
		condSpeaker.sleep();
		//this will be woken when there's a listener and then return
		lock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		lock.acquire();
		while (!isMsgSet) condListener.sleep();
		isMsgSet = false;
		int ret = msg;
		condSpeaker.wakeAll();
		lock.release();
		return ret;
	}
	
	private static int msg;
	private static boolean isMsgSet;
	private static Condition condListener, condSpeaker;
	private static Lock lock;
}
