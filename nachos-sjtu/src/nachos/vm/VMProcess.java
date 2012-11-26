package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.userprog.UserProcess;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		Processor p = Machine.processor();
		for(int i=0;i<p.getTLBSize();i++){
			myTLB[i] = p.readTLBEntry(i);
			p.writeTLBEntry(i, new TranslationEntry());
		}
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Processor p = Machine.processor();
		for(int i=0;i<p.getTLBSize();i++){
			if(myTLB[i]==null)continue;
			p.writeTLBEntry(i, myTLB[i]);
		}
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		//delay loading executables, just setup specific entries, note that changes should be written to swap rather than executables
		return super.loadSections();
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			int vaddr = processor.readRegister(Processor.regBadVAddr);
			int vpn = Processor.pageFromAddress(vaddr);
			int idx = Lib.random(processor.getTLBSize());
			for (int i=0;i<processor.getTLBSize();i++){
				if(!processor.readTLBEntry(i).valid){
					idx = i;
					break;
				}
			}
			TranslationEntry oldEntry = processor.readTLBEntry(idx);
			if(oldEntry.valid){
				pageTable[oldEntry.vpn].dirty |= oldEntry.dirty;
				pageTable[oldEntry.vpn].used|= oldEntry.used;
			}
			//FIXME: use a single global pageTable
			if(vpn<0 || vpn >= pageTable.length)handleException(Processor.exceptionBusError);
			processor.writeTLBEntry(idx, pageTable[vpn]);
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
	private TranslationEntry [] myTLB = new TranslationEntry[Machine.processor().getTLBSize()];
}
