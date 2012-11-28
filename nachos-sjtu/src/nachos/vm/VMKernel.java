package nachos.vm;

import java.util.HashMap;

import nachos.userprog.UserKernel;
import nachos.machine.TranslationEntry;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}
	

	
	public static VMKernel getKernel() {
		return (VMKernel) kernel;
	}
	
	private static final char dbgVM = 'v';
	public HashMap<VMPage, TranslationEntry> ipTable = new HashMap<VMPage, TranslationEntry> ();
	
	
}
