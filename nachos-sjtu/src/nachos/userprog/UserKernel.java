package nachos.userprog;

import java.util.ArrayList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.vm.VMPage;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());
		int pn = Machine.processor().getNumPhysPages();
		phyTable = new VMPage [pn];
		phyTableLock = new Lock();
		
		for(int i=0;i<pn;i++){
			phyTable[i]=null;
		}
		fdLock = new Lock();
		fileDescriptors.add(UserKernel.console.openForReading());
		fileDescriptors.add(UserKernel.console.openForWriting());
		pidCountLock = new Lock();
		processLock = new Lock();
		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
//		super.selfTest();
//		System.out.println("Testing the console device. Typed characters");
//		System.out.println("will be echoed until q is typed.");
//
//		char c;
//
//		do {
//			c = (char) console.readByte(true);
//			console.writeByte(c);
//		} while (c != 'q');
//
//		System.out.println("");
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		pidMain = process.pid;
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;
	//public static ArrayList<Boolean> phyTable;
	public static VMPage [] phyTable;
	public static Lock phyTableLock;
	public static int pidMain;
	public static int pidCount = 1;
	public static Lock pidCountLock;
	public static ArrayList<OpenFile> fileDescriptors=new ArrayList<OpenFile>();
	public static Lock fdLock;
	public static Lock processLock;
	public static int processCnt;
}
